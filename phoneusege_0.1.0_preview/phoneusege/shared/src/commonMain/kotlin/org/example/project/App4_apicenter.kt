package org.example.project

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
// ==============================================================================
// 数据模型
// ==============================================================================

@Serializable
data class SavedKeyConfig(
    val id: String,
    val name: String,
    val provider: String,   // "OpenAI" | "Claude"
    val apiKey: String,
    val baseUrl: String,
    val model: String
)

// ==============================================================================
// 工具：Base URL 规范化
// ==============================================================================

fun normalizeBaseUrl(url: String): String {
    return url.trim().trimEnd('/')
}

// ==============================================================================
// 存储接口
// ==============================================================================

interface StorageProvider {
    fun save(key: String, value: String)
    fun load(key: String): String?
}

object MemoryStorage : StorageProvider {
    private val store = mutableMapOf<String, String>()
    override fun save(key: String, value: String) { store[key] = value }
    override fun load(key: String): String? = store[key]
}

// ==============================================================================
// 配置 Store：多选激活 + 真持久化
// ==============================================================================

object KeyConfigStore {
    private var storage: StorageProvider = MemoryStorage
    private val json = Json { ignoreUnknownKeys = true }

    private val _configs = MutableStateFlow<List<SavedKeyConfig>>(emptyList())
    val configs: StateFlow<List<SavedKeyConfig>> = _configs.asStateFlow()

    // 多选激活：点一个亮一个，可同时亮多个
    private val _activeIds = MutableStateFlow<Set<String>>(emptySet())
    val activeIds: StateFlow<Set<String>> = _activeIds.asStateFlow()

    private var nextIdCounter = 1

    fun initialize(provider: StorageProvider) {
        storage = provider
        loadFromStorage()
    }

    private fun loadFromStorage() {
        storage.load("key_configs")?.let { raw ->
            try {
                _configs.value = json.decodeFromString<List<SavedKeyConfig>>(raw)
                nextIdCounter = (_configs.value.maxOfOrNull {
                    it.id.removePrefix("config_").toIntOrNull() ?: 0
                } ?: 0) + 1
            } catch (_: Exception) { }
        }
        storage.load("active_ids")?.let { raw ->
            try {
                val ids = json.decodeFromString<Set<String>>(raw)
                _activeIds.value = ids.filter { id ->
                    _configs.value.any { it.id == id }
                }.toSet()
            } catch (_: Exception) { }
        }
    }

    private fun saveToStorage() {
        try {
            storage.save("key_configs", json.encodeToString(_configs.value))
            storage.save("active_ids", json.encodeToString(_activeIds.value))
        } catch (_: Exception) { }
    }

    fun toggleActive(id: String) {
        _activeIds.value =
            if (id in _activeIds.value) _activeIds.value - id
            else _activeIds.value + id
        saveToStorage()
    }

    fun getActiveConfigs(): List<SavedKeyConfig> =
        _configs.value.filter { it.id in _activeIds.value }

    // ===== 7B：Host Probe 连续失败计数 =====
    private val hostProbeFailureCounts = mutableMapOf<String, Int>()

    fun deactivate(id: String) {
        _activeIds.value = _activeIds.value - id
        hostProbeFailureCounts.remove(id)
        saveToStorage()
    }

    private fun recordHostProbeFailure(id: String) {
        val next = hostProbeFailureCounts.getOrElse(id) { 0 } + 1
        if (next >= 3) {
            deactivate(id)
        } else {
            hostProbeFailureCounts[id] = next
        }
    }

    private fun resetHostProbeFailure(id: String) {
        hostProbeFailureCounts.remove(id)
    }

    private fun originKeyForGrouping(baseUrl: String): String {
        val normalized = normalizeBaseUrl(baseUrl)
        val schemeIdx = normalized.indexOf("://")
        val hostStart = if (schemeIdx >= 0) schemeIdx + 3 else 0
        val slashIdx = normalized.indexOf('/', hostStart)
        return if (slashIdx >= 0) normalized.substring(0, slashIdx) else normalized
    }

    /**
     * 同时请求所有当前激活配置，返回最先成功的回复。
     *
     * 流程：
     * 1. 对所有激活配置按 origin 去重做 HEAD 探活
     * 2. 探活不可达才累计 hostProbeFailureCounts
     * 3. 探活可达才发真实 requestChat
     * 4. API 失败不会关闭激活，只作为本次请求失败
     * 5. 第一个 isSuccess=true 且 reply 非空的结果获胜
     */
    suspend fun requestFirstSuccess(history: List<ChatTurn>): ChatReplyResult = coroutineScope {
        val activeConfigs = getActiveConfigs()

        if (activeConfigs.isEmpty()) {
            return@coroutineScope ChatReplyResult(
                isSuccess = false,
                reply = null,
                errorMessage = "没有激活的 Key，请先在 Key 面板点亮至少一个配置。"
            )
        }

        // 1. 按 origin 去重探活
        val groupedByOrigin = activeConfigs.groupBy { originKeyForGrouping(it.baseUrl) }

        val probeJobs = groupedByOrigin.map { (origin, configs) ->
            async {
                val probe = probeHttpOrigin(origin)
                Pair(configs, probe)
            }
        }

        val probeResults = probeJobs.map { it.await() }

        val reachableConfigs = mutableListOf<SavedKeyConfig>()
        val unreachableConfigs = mutableListOf<SavedKeyConfig>()

        probeResults.forEach { (configs, probe) ->
            if (probe.reachable) {
                reachableConfigs.addAll(configs)
                configs.forEach { resetHostProbeFailure(it.id) }
            } else {
                unreachableConfigs.addAll(configs)
            }
        }

        // 只有 HEAD 探活失败才记录 host 失败
        unreachableConfigs.forEach { recordHostProbeFailure(it.id) }

        if (reachableConfigs.isEmpty()) {
            val reason = probeResults
                .mapNotNull { it.second.errorMessage }
                .firstOrNull()
                ?: "所有激活配置的主机均不可达。"

            return@coroutineScope ChatReplyResult(
                isSuccess = false,
                reply = null,
                errorMessage = reason
            )
        }

        // 2. 并发真实请求，最先成功获胜
        val channel = Channel<Pair<SavedKeyConfig, ChatReplyResult>>(Channel.UNLIMITED)

        val jobs = reachableConfigs.map { config ->
            launch {
                val result = try {
                    requestChat(config, history)
                } catch (e: Exception) {
                    ChatReplyResult(
                        isSuccess = false,
                        reply = null,
                        errorMessage = e.message?.take(200) ?: "Request failed"
                    )
                }

                channel.trySend(Pair(config, result))
            }
        }

        var completed = 0
        var winner: ChatReplyResult? = null
        var lastError: String? = null

        while (completed < jobs.size) {
            val (_, result) = channel.receive()
            completed += 1

            if (result.isSuccess && !result.reply.isNullOrBlank()) {
                winner = result
                break
            } else {
                lastError = result.errorMessage
            }
        }

        if (winner != null) {
            // 注意：HttpURLConnection 已经发出去的请求不一定能真正省钱；
            // 这里取消只是避免本地继续等待结果。
            jobs.forEach { job ->
                if (job.isActive) {
                    job.cancel(CancellationException("First successful reply selected"))
                }
            }
            channel.close()

            return@coroutineScope winner
        }

        channel.close()

        return@coroutineScope ChatReplyResult(
            isSuccess = false,
            reply = null,
            errorMessage = lastError ?: "所有激活配置请求均失败。"
        )
    }
    private sealed class StreamRaceEvent {
        data class Delta(
            val configId: String,
            val delta: String
        ) : StreamRaceEvent()

        data class Finished(
            val configId: String,
            val result: ChatStreamResult
        ) : StreamRaceEvent()
    }

    /**
     * 阶段 8：流式最快获胜。
     *
     * 判定标准不是"谁先完成"，而是"谁先产出第一个有效 delta"。
     * 获胜后只转发 winner 的后续 delta，其它流尝试取消并忽略。
     */
    suspend fun requestFirstStreamSuccess(
        history: List<ChatTurn>,
        onDelta: suspend (String) -> Unit
    ): ChatStreamResult = coroutineScope {
        val activeConfigs = getActiveConfigs()

        if (activeConfigs.isEmpty()) {
            return@coroutineScope ChatStreamResult(
                isSuccess = false,
                errorMessage = "没有激活的 Key，请先在 Key 面板点亮至少一个配置。"
            )
        }

        // 1. 按 origin 去重探活
        val groupedByOrigin = activeConfigs.groupBy { originKeyForGrouping(it.baseUrl) }

        val probeJobs = groupedByOrigin.map { (origin, configs) ->
            async {
                val probe = probeHttpOrigin(origin)
                Pair(configs, probe)
            }
        }

        val probeResults = probeJobs.map { it.await() }

        val reachableConfigs = mutableListOf<SavedKeyConfig>()
        val unreachableConfigs = mutableListOf<SavedKeyConfig>()

        probeResults.forEach { (configs, probe) ->
            if (probe.reachable) {
                reachableConfigs.addAll(configs)
                configs.forEach { resetHostProbeFailure(it.id) }
            } else {
                unreachableConfigs.addAll(configs)
            }
        }

        // 只有 HEAD 探活失败才记录 host 失败
        unreachableConfigs.forEach { recordHostProbeFailure(it.id) }

        if (reachableConfigs.isEmpty()) {
            val reason = probeResults
                .mapNotNull { it.second.errorMessage }
                .firstOrNull()
                ?: "所有激活配置的主机均不可达。"

            return@coroutineScope ChatStreamResult(
                isSuccess = false,
                errorMessage = reason
            )
        }

        // 2. 并发开启流式请求，由 Channel 集中仲裁 winner
        val channel = Channel<StreamRaceEvent>(Channel.UNLIMITED)

        val jobs = reachableConfigs.associate { config ->
            config.id to launch {
                try {
                    val result = requestChatStream(config, history) { delta ->
                        if (delta.isNotEmpty()) {
                            channel.send(
                                StreamRaceEvent.Delta(
                                    configId = config.id,
                                    delta = delta
                                )
                            )
                        }
                    }

                    channel.trySend(
                        StreamRaceEvent.Finished(
                            configId = config.id,
                            result = result
                        )
                    )
                } catch (_: CancellationException) {
                    // loser 被取消时不再上报，避免污染最终错误。
                } catch (e: Exception) {
                    channel.trySend(
                        StreamRaceEvent.Finished(
                            configId = config.id,
                            result = ChatStreamResult(
                                isSuccess = false,
                                errorMessage = e.message?.take(200)
                                    ?: "Stream request failed"
                            )
                        )
                    )
                }
            }
        }

        var completedBeforeWinner = 0
        var winnerId: String? = null
        var winnerResult: ChatStreamResult? = null
        var lastError: String? = null

        try {
            while (true) {
                val event = channel.receive()

                when (event) {
                    is StreamRaceEvent.Delta -> {
                        val currentWinner = winnerId

                        if (currentWinner == null) {
                            winnerId = event.configId

                            // 注意：HttpURLConnection 请求可能已经发出。
                            // 这里取消只是停止本地继续等待，不承诺服务端立即停止计费。
                            jobs.forEach { (id, job) ->
                                if (id != event.configId && job.isActive) {
                                    job.cancel(
                                        CancellationException(
                                            "First streaming delta selected"
                                        )
                                    )
                                }
                            }

                            onDelta(event.delta)
                        } else if (currentWinner == event.configId) {
                            onDelta(event.delta)
                        }
                    }

                    is StreamRaceEvent.Finished -> {
                        val currentWinner = winnerId

                        if (currentWinner == null) {
                            completedBeforeWinner += 1

                            if (
                                event.result.isSuccess &&
                                event.result.fullText.isNotBlank()
                            ) {
                                // 理论兜底：如果某个流没有提前吐 delta，但结束时有文本，
                                // 仍把它当作本次成功结果。
                                winnerId = event.configId
                                winnerResult = event.result

                                jobs.forEach { (id, job) ->
                                    if (id != event.configId && job.isActive) {
                                        job.cancel(
                                            CancellationException(
                                                "First successful stream selected"
                                            )
                                        )
                                    }
                                }

                                break
                            } else {
                                lastError = event.result.errorMessage
                            }

                            if (completedBeforeWinner >= jobs.size) {
                                break
                            }
                        } else if (currentWinner == event.configId) {
                            winnerResult = event.result
                            break
                        }
                    }
                }
            }
        } finally {
            jobs.values.forEach { job ->
                if (job.isActive) {
                    job.cancel(
                        CancellationException(
                            "Stream race finished"
                        )
                    )
                }
            }
            channel.close()
        }

        val result = winnerResult

        if (result != null) {
            return@coroutineScope result
        }

        return@coroutineScope ChatStreamResult(
            isSuccess = false,
            errorMessage = lastError ?: "所有激活配置请求均失败。"
        )
    }


    fun add(config: SavedKeyConfig) {
        _configs.value = _configs.value + config
        saveToStorage()
    }

    fun update(config: SavedKeyConfig) {
        _configs.value = _configs.value.map { if (it.id == config.id) config else it }
        saveToStorage()
    }

    fun delete(id: String) {
        _configs.value = _configs.value.filterNot { it.id != id }
        _activeIds.value = _activeIds.value - id
        saveToStorage()
    }

    fun createNewConfig(
        name: String, provider: String,
        apiKey: String, baseUrl: String, model: String
    ): SavedKeyConfig {
        val idx = nextIdCounter++   // 全局统一计数，OpenAI/Claude 共用
        return SavedKeyConfig(
            id = "config_$idx",
            name = name.ifBlank { "$provider $idx" },
            provider = provider,
            apiKey = apiKey,
            baseUrl = normalizeBaseUrl(baseUrl),
            model = model
        )
    }
}
