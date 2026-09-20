package org.example.project

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class ConversationMessageRecord(
    val id: Long,
    val role: String,
    val content: String,
    val displayTime: String,
    val replyToId: Long? = null
)

object ContextLimits {
    const val MAX_UI_MESSAGES = 80
    const val MAX_PERSISTED_MESSAGES = 500
    const val MAX_API_MESSAGES = 20
    const val MAX_API_CHARS = 12000
    const val MAX_SINGLE_MESSAGE_CHARS = 4000
    const val MAX_HTTP_RESPONSE_CHARS = 2_000_000
}

/** 任务22：工具调用记录的可序列化形态（跨重启保留工具气泡） */
@Serializable
data class PersistedToolCallRecord(
    val id: Long,
    val toolName: String,
    val argsText: String,
    val status: String,
    val resultPreview: String,
    val displayTime: String,
    val anchorMessageId: Long? = null
)

@Serializable
data class ConversationRecord(
    val schemaVersion: Int = 3,
    val conversationId: String = "conversation_1",
    val title: String = "New conversation",
    val updatedSequence: Long = 0L,
    val messages: List<ConversationMessageRecord> = emptyList(),
    // 任务22：工具气泡/plan 持久化（字段带默认值，老档案 v2 可直接加载）
    val toolCalls: List<PersistedToolCallRecord> = emptyList(),
    val planSteps: List<String> = emptyList(),
    val planAnchorMessageId: Long? = null,
    // 即时聊天标记：true = 本次对话不进存档（只活在内存，重启即焚）。
    // 带默认值 + ignoreUnknownKeys → 老档案无缝加载；标记本身永不落盘
    // （ephemeral=true 的整条记录会被 persistArchive 过滤掉）。
    val ephemeral: Boolean = false
)

data class ConversationCardRecord(
    val conversationId: String,
    val title: String,
    val preview: String,
    val displayTime: String,
    val messageCount: Int
)

/** 任务3：cmd 命令用的会话摘要（含空会话，按最近更新排序） */
data class ConversationSummaryRecord(
    val conversationId: String,
    val title: String,
    val messageCount: Int
)

@Serializable
private data class ConversationArchiveRecord(
    val schemaVersion: Int = 2,
    val currentConversationId: String = "conversation_1",
    val nextConversationNumber: Long = 2L,
    val nextMessageId: Long = 1L,
    val nextUpdateSequence: Long = 1L,
    val conversations: List<ConversationRecord> = emptyList()
)

object ConversationStore {

    private const val LEGACY_STORAGE_KEY = "conversation_current_v1"
    private const val ARCHIVE_STORAGE_KEY = "conversation_archive_v2"

    private val json = Json { ignoreUnknownKeys = true }

    private var storage: StorageProvider = MemoryStorage
    private var initialized = false

    private var conversations = emptyList<ConversationRecord>()
    private var currentId = "conversation_1"
    private var nextConversationNumber = 2L
    private var nextMessageId = 1L
    private var nextUpdateSequence = 1L

    private val _messages =
        MutableStateFlow<List<ConversationMessageRecord>>(emptyList())
    val messages: StateFlow<List<ConversationMessageRecord>> =
        _messages.asStateFlow()

    private val _conversationCards =
        MutableStateFlow<List<ConversationCardRecord>>(emptyList())
    val conversationCards: StateFlow<List<ConversationCardRecord>> =
        _conversationCards.asStateFlow()

    private val _currentConversationId =
        MutableStateFlow("conversation_1")
    val currentConversationId: StateFlow<String> =
        _currentConversationId.asStateFlow()

    private val _isLoaded = MutableStateFlow(false)
    val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()

    fun initialize(provider: StorageProvider) {
        storage = provider
        if (initialized) return
        initialized = true
        loadArchiveOrMigrateLegacy()
    }

    private fun loadArchiveOrMigrateLegacy() {
        _isLoaded.value = false

        val loadedArchive = try {
            storage.load(ARCHIVE_STORAGE_KEY)
                ?.takeIf { it.isNotBlank() }
                ?.let { json.decodeFromString<ConversationArchiveRecord>(it) }
        } catch (_: Exception) {
            null
        }

        if (loadedArchive != null) {
            conversations = loadedArchive.conversations
            currentId = loadedArchive.currentConversationId
            nextConversationNumber = loadedArchive.nextConversationNumber
            nextMessageId = loadedArchive.nextMessageId
            nextUpdateSequence = loadedArchive.nextUpdateSequence
            repairLoadedCounters()
            // 任务1：让每个对话档案拥有唯一、稳定的 ID（修复老数据空白/重复 ID）
            repairConversationIds()
        } else {
            migrateLegacyConversation()
            // 任务1：迁移结果同样过一遍 ID 修复
            repairConversationIds()
            persistArchive()
        }

        ensureCurrentConversationExists()
        publishCurrentConversation()
        _isLoaded.value = true
    }

    private fun migrateLegacyConversation() {
        val legacyRecord = try {
            storage.load(LEGACY_STORAGE_KEY)
                ?.takeIf { it.isNotBlank() }
                ?.let { json.decodeFromString<ConversationRecord>(it) }
        } catch (_: Exception) {
            null
        }

        val migratedMessages = legacyRecord?.messages.orEmpty()
        val migrated = ConversationRecord(
            schemaVersion = 2,
            conversationId = "conversation_1",
            title = deriveTitle(migratedMessages),
            updatedSequence = if (migratedMessages.isEmpty()) 0L else 1L,
            messages = migratedMessages
        )

        conversations = listOf(migrated)
        currentId = migrated.conversationId
        nextConversationNumber = 2L
        nextMessageId = (migratedMessages.maxOfOrNull { it.id } ?: 0L) + 1L
        nextUpdateSequence = if (migratedMessages.isEmpty()) 1L else 2L
    }

    private fun repairLoadedCounters() {
        if (conversations.isEmpty()) {
            conversations = listOf(
                ConversationRecord(conversationId = "conversation_1")
            )
        }

        val maximumMessageId =
            conversations.flatMap { it.messages }.maxOfOrNull { it.id } ?: 0L
        nextMessageId = maxOf(nextMessageId, maximumMessageId + 1L)

        val maximumUpdateSequence =
            conversations.maxOfOrNull { it.updatedSequence } ?: 0L
        nextUpdateSequence =
            maxOf(nextUpdateSequence, maximumUpdateSequence + 1L)

        val maximumConversationNumber =
            conversations
                .mapNotNull {
                    it.conversationId
                        .removePrefix("conversation_")
                        .toLongOrNull()
                }
                .maxOrNull() ?: 0L
        nextConversationNumber =
            maxOf(nextConversationNumber, maximumConversationNumber + 1L)
    }

    /**
     * 任务1：让每个对话档案拥有唯一、稳定的 ID。
     * 加载/迁移完成后执行一次 ID 修复，保证：
     *  1) 空白 conversationId → 分配新的 conversation_N；
     *  2) 重复 conversationId → 仅保留 updatedSequence 最新的一条，其余重分配新 ID；
     *  3) currentId 若指向被重分配的会话 → 跟随重映射到新 ID。
     * 发生变动时立即持久化，避免修复结果丢失。
     */
    private fun repairConversationIds() {
        if (conversations.isEmpty()) return

        // 每个原始 ID 只保留 updatedSequence 最新的一条（重复时其余重分配）
        val keepRawIds = conversations
            .filter { it.conversationId.isNotBlank() }
            .groupBy { it.conversationId }
            .mapValues { (_, group) -> group.maxBy { it.updatedSequence } }

        val usedIds = mutableSetOf<String>()
        val remapped = mutableMapOf<String, String>()
        var changed = false

        fun freshId(): String {
            var candidate: String
            do {
                candidate = "conversation_$nextConversationNumber"
                nextConversationNumber += 1L
            } while (candidate in usedIds)
            usedIds.add(candidate)
            return candidate
        }

        val repaired = conversations.map { conv ->
            val raw = conv.conversationId.trim()
            val isKept = raw.isNotEmpty() && keepRawIds[raw] === conv
            if (isKept) {
                usedIds.add(raw)
                conv
            } else {
                val fresh = freshId()
                if (raw.isNotEmpty()) remapped[raw] = fresh
                changed = true
                conv.copy(conversationId = fresh)
            }
        }

        conversations = repaired
        if (changed) {
            currentId = remapped[currentId] ?: currentId
            persistArchive()
        }
    }

    private fun ensureCurrentConversationExists() {
        if (conversations.any { it.conversationId == currentId }) return

        val fallback = conversations.maxByOrNull { it.updatedSequence }
        if (fallback != null) {
            currentId = fallback.conversationId
        } else {
            val empty = ConversationRecord(conversationId = "conversation_1")
            conversations = listOf(empty)
            currentId = empty.conversationId
        }
    }

    private fun publishCurrentConversation() {
        ensureCurrentConversationExists()
        val current = conversations.first { it.conversationId == currentId }
        _messages.value = current.messages
        _currentConversationId.value = currentId
        publishConversationCards()
    }

    private fun publishConversationCards() {
        _conversationCards.value = conversations
            .asSequence()
            // 即时聊天(ephemeral)对话不进存档列表——它根本没存档，列表里也不该出现
            .filter { !it.ephemeral }
            .filter { it.messages.isNotEmpty() }
            .sortedByDescending { it.updatedSequence }
            .map { conversation ->
                val lastMessage = conversation.messages.lastOrNull()
                ConversationCardRecord(
                    conversationId = conversation.conversationId,
                    title = conversation.title,
                    preview = cleanPreview(lastMessage?.content.orEmpty()),
                    displayTime = lastMessage?.displayTime.orEmpty(),
                    messageCount = conversation.messages.count {
                        it.role == "user" || it.role == "assistant"
                    }
                )
            }
            .toList()
    }

    private fun persistArchive() {
        try {
            val clippedConversations = conversations.map { conv ->
                conv.copy(
                    messages = conv.messages.takeLast(
                        ContextLimits.MAX_PERSISTED_MESSAGES
                    )
                )
            }
            conversations = clippedConversations

            // 即时聊天(ephemeral)对话不进存档磁盘数据——它只活在内存，重启即焚。
            // 这里是"不存档"的总闸门：无论哪条写入路径(append/工具气泡/plan/切换/修复)
            // 都过这道筛子，内存里的 conversations 保持完整(即时对话本次会话内照常使用)。
            val persistedConversations =
                clippedConversations.filter { !it.ephemeral }

            // 当前若是即时对话，存档里的 currentId 回退到最近更新的持久会话——
            // 避免重启后指针悬空在已不存在的会话上(ensureCurrentConversationExists 兜底)。
            val persistedCurrentId =
                if (persistedConversations.any { it.conversationId == currentId }) {
                    currentId
                } else {
                    persistedConversations
                        .maxByOrNull { it.updatedSequence }
                        ?.conversationId ?: currentId
                }

            val archive = ConversationArchiveRecord(
                currentConversationId = persistedCurrentId,
                nextConversationNumber = nextConversationNumber,
                nextMessageId = nextMessageId,
                nextUpdateSequence = nextUpdateSequence,
                conversations = persistedConversations
            )
            storage.save(ARCHIVE_STORAGE_KEY, json.encodeToString(archive))
        } catch (_: Exception) {
        }
    }

    fun allocateMessageId(): Long {
        val allocated = nextMessageId
        nextMessageId += 1L
        return allocated
    }

    fun append(message: ConversationMessageRecord) {
        val currentIndex =
            conversations.indexOfFirst { it.conversationId == currentId }
        if (currentIndex < 0) return

        if (message.id >= nextMessageId) {
            nextMessageId = message.id + 1L
        }

        val oldConversation = conversations[currentIndex]
        val newMessages = insertMessagePreservingOrder(oldConversation.messages, message)
        val updatedConversation = oldConversation.copy(
            schemaVersion = 2,
            title = deriveTitle(newMessages),
            updatedSequence = nextUpdateSequence++,
            messages = newMessages
        )

        conversations = conversations.toMutableList().apply {
            this[currentIndex] = updatedConversation
        }

        _messages.value = newMessages
        publishConversationCards()
        // 即时对话只更新内存、不落盘(用完即焚)；长久聊天照常写入存档
        if (!updatedConversation.ephemeral) persistArchive()
    }

    /**
     * 记忆画布气泡编辑：改写指定会话某条消息的正文。
     * 标题保持原样不重算；即时对话只改内存不落盘。
     */
    fun updateMessageContent(
        conversationId: String,
        messageId: Long,
        newContent: String
    ): Boolean {
        val index = conversations.indexOfFirst {
            it.conversationId == conversationId
        }
        if (index < 0) return false

        val conv = conversations[index]
        val msgIndex = conv.messages.indexOfFirst { it.id == messageId }
        if (msgIndex < 0) return false

        val updated = conv.messages.toMutableList().apply {
            this[msgIndex] = this[msgIndex].copy(content = newContent)
        }
        conversations = conversations.toMutableList().apply {
            this[index] = conv.copy(
                messages = updated,
                updatedSequence = nextUpdateSequence++
            )
        }

        if (conversationId == currentId) _messages.value = updated
        publishConversationCards()
        if (!conv.ephemeral) persistArchive()
        return true
    }

    /**
     * 插入消息时保持问答顺序：
     * assistant 消息如果有 replyToId，则插入到对应 user 消息之后；
     * 其余情况追加到末尾。
     */
    private fun insertMessagePreservingOrder(
        messages: List<ConversationMessageRecord>,
        incoming: ConversationMessageRecord
    ): List<ConversationMessageRecord> {
        val replyTo = incoming.replyToId
        if (incoming.role != "assistant" || replyTo == null) {
            return messages + incoming
        }
        val userIndex = messages.indexOfFirst { it.id == replyTo }
        if (userIndex < 0) {
            return messages + incoming
        }
        val insertAt = userIndex + 1
        return messages.subList(0, insertAt) + incoming + messages.subList(insertAt, messages.size)
    }

    fun createNewConversation(): String {
        // 即时聊天(第二个竖块拨开)：本次新开的对话不进存档——
        // 标记在此被消耗：只作用于这一次新对话，竖块随之归位(回到长久聊天)；
        // 在即时对话里再新开会话时标记已是 false → 新会话仍然存档。
        val ephemeral = ChatModeStore.consumeEphemeralFlag()

        var newId: String
        do {
            newId = "conversation_$nextConversationNumber"
            nextConversationNumber += 1L
        } while (conversations.any { it.conversationId == newId })

        val newConversation = ConversationRecord(
            conversationId = newId,
            title = "New conversation",
            updatedSequence = nextUpdateSequence++,
            ephemeral = ephemeral
        )

        conversations = conversations + newConversation
        currentId = newId
        _messages.value = emptyList()
        _currentConversationId.value = newId
        publishConversationCards()
        // 即时对话不碰磁盘(跳过本次落盘安全：计数器重启时由 repairLoadedCounters 自愈)
        if (!ephemeral) persistArchive()
        // 任务2：新开会话 = 全新空代码库（该会话名下没有任何工作区数据，什么也没有）
        JsWorkspaceStore.switchConversation(newId)
        return newId
    }

    fun selectConversation(conversationId: String): Boolean {
        val selected = conversations.firstOrNull {
            it.conversationId == conversationId
        } ?: return false

        if (currentId == conversationId) return true

        currentId = conversationId
        _messages.value = selected.messages
        _currentConversationId.value = conversationId
        persistArchive()
        // 任务2：切换会话 = 切换到该会话自己的代码库（各自独立保存）
        JsWorkspaceStore.switchConversation(conversationId)
        return true
    }

    /**
     * 归档画布右滑删除：移除指定会话档案（消息/工具气泡/plan 一并移除）。
     * 被删的是当前会话时：回落到最近更新的一条；全部删光则新建空会话，
     * 保证 currentId 永不悬空。
     */
    fun deleteConversation(conversationId: String): Boolean {
        if (conversations.none { it.conversationId == conversationId }) {
            return false
        }

        val wasCurrent = currentId == conversationId

        conversations = conversations.filterNot {
            it.conversationId == conversationId
        }

        if (wasCurrent) {
            val fallback =
                conversations.maxByOrNull { it.updatedSequence }

            if (fallback != null) {
                currentId = fallback.conversationId
                _messages.value = fallback.messages
                _currentConversationId.value =
                    fallback.conversationId
                // 任务2：当前会话被删后，工作区跟随回落到下一个会话
                JsWorkspaceStore.switchConversation(
                    fallback.conversationId
                )
            } else {
                // 全部删光：补一个空会话兜底
                val newId =
                    "conversation_$nextConversationNumber"
                nextConversationNumber += 1L
                val fresh = ConversationRecord(
                    conversationId = newId,
                    updatedSequence = nextUpdateSequence++
                )
                conversations = listOf(fresh)
                currentId = newId
                _messages.value = emptyList()
                _currentConversationId.value = newId
                JsWorkspaceStore.switchConversation(newId)
            }
        }

        publishConversationCards()
        persistArchive()
        return true
    }

    /**
     * 任务3：列出所有会话档案及其 ID（含空会话，按最近更新倒序），供 tcopsay 使用。
     */
    fun listConversationSummaries(): List<ConversationSummaryRecord> =
        conversations
            .sortedByDescending { it.updatedSequence }
            .map {
                ConversationSummaryRecord(
                    conversationId = it.conversationId,
                    title = it.title,
                    messageCount = it.messages.size
                )
            }

    /** 任务3：按会话 ID 查找会话档案（供 anyenone 摘要使用）。 */
    fun findConversation(conversationId: String): ConversationRecord? =
        conversations.firstOrNull { it.conversationId == conversationId }

    // ==================== 任务22：工具气泡 / plan 持久化 ====================
    // 存入当前会话档案；读取时把持久化形态还原为 UI 层的 ToolCallRecord。
    // 老档案没有这些字段 → 默认空，加载无影响。

    /** 保存当前会话的工具气泡记录（含各自锚点），重启后恢复 */
    fun saveToolCallsForCurrent(records: List<ToolCallRecord>) {
        val currentIndex =
            conversations.indexOfFirst { it.conversationId == currentId }
        if (currentIndex < 0) return

        val old = conversations[currentIndex]
        val mapped = records.takeLast(60).map {
            PersistedToolCallRecord(
                id = it.id,
                toolName = it.toolName,
                argsText = it.argsText,
                status = it.status.name,
                resultPreview = it.resultPreview,
                displayTime = it.displayTime,
                anchorMessageId = it.anchorMessageId
            )
        }
        conversations = conversations.toMutableList().apply {
            this[currentIndex] = old.copy(toolCalls = mapped)
        }
        // 即时对话：工具气泡与消息保持一致——只更新内存，不进存档
        if (!old.ephemeral) persistArchive()
    }

    /** 读取当前会话的工具气泡记录（Running 状态恢复为 Done，避免重启后永远转圈） */
    fun loadToolCallsForCurrent(): List<ToolCallRecord> {
        val current =
            conversations.firstOrNull { it.conversationId == currentId }
                ?: return emptyList()
        return current.toolCalls.map {
            val restoredStatus = try {
                ToolCallStatus.valueOf(it.status)
            } catch (_: Exception) {
                ToolCallStatus.Done
            }
            ToolCallRecord(
                id = it.id,
                toolName = it.toolName,
                argsText = it.argsText,
                status = if (restoredStatus == ToolCallStatus.Running) {
                    ToolCallStatus.Done
                } else restoredStatus,
                resultPreview = it.resultPreview,
                displayTime = it.displayTime,
                anchorMessageId = it.anchorMessageId
            )
        }
    }

    /** 保存当前会话的 plan（步骤 + 锚点） */
    fun savePlanForCurrent(steps: List<String>, anchorMessageId: Long?) {
        val currentIndex =
            conversations.indexOfFirst { it.conversationId == currentId }
        if (currentIndex < 0) return

        val old = conversations[currentIndex]
        conversations = conversations.toMutableList().apply {
            this[currentIndex] = old.copy(
                planSteps = steps,
                planAnchorMessageId = anchorMessageId
            )
        }
        // 即时对话：plan 与消息保持一致——只更新内存，不进存档
        if (!old.ephemeral) persistArchive()
    }

    /** 读取当前会话的 plan：返回 步骤列表 to 锚点；无 plan 时返回 null */
    fun loadPlanForCurrent(): Pair<List<String>, Long?>? {
        val current =
            conversations.firstOrNull { it.conversationId == currentId }
                ?: return null
        if (current.planSteps.isEmpty()) return null
        return current.planSteps to current.planAnchorMessageId
    }

    fun clearCurrentConversation() {
        val currentIndex =
            conversations.indexOfFirst { it.conversationId == currentId }
        if (currentIndex < 0) return

        val cleared = conversations[currentIndex].copy(
            title = "New conversation",
            updatedSequence = nextUpdateSequence++,
            messages = emptyList(),
            // 任务22：清空会话时一并清掉工具气泡与 plan
            toolCalls = emptyList(),
            planSteps = emptyList(),
            planAnchorMessageId = null
        )

        conversations = conversations.toMutableList().apply {
            this[currentIndex] = cleared
        }

        _messages.value = emptyList()
        publishConversationCards()
        persistArchive()
    }

    fun buildContextWindow(
        maxMessages: Int = ContextLimits.MAX_API_MESSAGES,
        maxCharacters: Int = ContextLimits.MAX_API_CHARS,
        maxSingleMessageChars: Int = ContextLimits.MAX_SINGLE_MESSAGE_CHARS
    ): List<ChatTurn> {
        val all = _messages.value

        fun truncate(text: String): String {
            return if (text.length > maxSingleMessageChars) {
                text.take(maxSingleMessageChars) + "[...truncated]"
            } else {
                text
            }
        }

        val system = all.firstOrNull { it.role == "system" }
        val candidates = all.filter {
            it.role == "user" || it.role == "assistant"
        }

        val selected = mutableListOf<ConversationMessageRecord>()
        var characterCount = 0

        for (message in candidates.asReversed()) {
            if (selected.size >= maxMessages.coerceAtLeast(1)) break

            val clippedLength =
                message.content.length.coerceAtMost(maxSingleMessageChars)

            val wouldExceed =
                characterCount + clippedLength > maxCharacters.coerceAtLeast(1)

            if (wouldExceed && selected.isNotEmpty()) break

            selected += message
            characterCount += clippedLength
        }

        return buildList {
            if (system != null) {
                add(
                    ChatTurn(
                        role = "system",
                        content = truncate(system.content)
                    )
                )
            }

            selected.asReversed().forEach {
                add(
                    ChatTurn(
                        role = it.role,
                        content = truncate(it.content)
                    )
                )
            }
        }
    }

    private fun deriveTitle(
        messages: List<ConversationMessageRecord>
    ): String {
        val firstUserText =
            messages.firstOrNull { it.role == "user" }?.content.orEmpty()

        val cleaned =
            firstUserText.replace(Regex("\\s+"), " ").trim().take(28)

        return cleaned.ifBlank { "New conversation" }
    }

    private fun cleanPreview(content: String): String {
        return content.replace(Regex("\\s+"), " ").trim().take(52)
    }
}
