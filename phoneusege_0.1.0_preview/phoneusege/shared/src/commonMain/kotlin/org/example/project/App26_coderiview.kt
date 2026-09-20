package org.example.project

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class CodeInterpreter(
    val id: String,
    val name: String,
    val sourceUrl: String,
    val sourceCode: String
)

data class CodeRiViewSnapshot(
    val interpreters: List<CodeInterpreter>,
    val bindings: Map<String, String> // workspaceFileId -> interpreterId
)

interface CodeRiViewStorage {
    suspend fun load(): CodeRiViewSnapshot?
    suspend fun save(snapshot: CodeRiViewSnapshot)
}

object CodeRiViewStore {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var storage: CodeRiViewStorage? = null
    private var idSeq = 0L

    /** 写盘串行化锁（协程 Mutex）：避免并发 persist/flush 交错写入导致文件损坏。
     *  用 Mutex 而非 synchronized，因为临界区内调用挂起函数 save()。 */
    private val writeLock = Mutex()

    private val _interpreters = MutableStateFlow<List<CodeInterpreter>>(emptyList())
    val interpreters: StateFlow<List<CodeInterpreter>> = _interpreters

    private val _bindings = MutableStateFlow<Map<String, String>>(emptyMap())
    val bindings: StateFlow<Map<String, String>> = _bindings

    fun initialize(storage: CodeRiViewStorage) {
        this.storage = storage
        scope.launch {
            storage.load()?.let { s ->
                _interpreters.value = s.interpreters
                _bindings.value = s.bindings
                idSeq = s.interpreters
                    .mapNotNull { it.id.removePrefix("ci_").toLongOrNull() }
                    .maxOrNull() ?: 0L
                // 重建解释器集成表（App27_interpreterhub.kt）
                InterpreterHubStore.refresh()
            }
        }
    }

    /** 自动管道：下载完成后自动绑定到当前选中的工作区文件，返回被绑定的 fileId。 */
    fun attachDownloaded(name: String, sourceUrl: String, sourceCode: String): String? {
        idSeq++
        val interp = CodeInterpreter("ci_$idSeq", name, sourceUrl, sourceCode)
        _interpreters.value = _interpreters.value + interp
        // 自动集成到解释器集成文件（App27_interpreterhub.kt）
        InterpreterHubStore.integrate(interp)

        val fileId = JsWorkspaceStore.selectedFileId.value
        if (fileId != null) {
            _bindings.value = _bindings.value + (fileId to interp.id)
        }
        persist()
        return fileId
    }

    fun unbind(fileId: String) {
        _bindings.value = _bindings.value - fileId
        persist()
    }

    fun interpreterFor(fileId: String): CodeInterpreter? {
        val iid = _bindings.value[fileId] ?: return null
        return _interpreters.value.firstOrNull { it.id == iid }
    }

    /** 删除解释器（按 id 或名称匹配），同时清理其绑定并重建集成表。 */
    fun removeInterpreter(target: String): Boolean {
        val t = target.trim()
        if (t.isEmpty()) return false
        val list = _interpreters.value
        val match = list.firstOrNull { it.id == t || it.name.equals(t, ignoreCase = true) }
            ?: return false
        _interpreters.value = list.filterNot { it.id == match.id }
        _bindings.value = _bindings.value.filterValues { it != match.id }
        InterpreterHubStore.refresh()
        persist()
        return true
    }

    /** 异步落盘：fire-and-forget，供状态变更后调用。 */
    private fun persist() {
        val s = storage ?: return
        val snap = CodeRiViewSnapshot(_interpreters.value, _bindings.value)
        scope.launch {
            writeLock.withLock { runCatching { s.save(snap) } }
        }
    }

    /** 挂起式落盘：当前状态完整写入后才返回。用于 onPause / 退出等需要确保落盘的时机。 */
    suspend fun flush() {
        val s = storage ?: return
        val snap = CodeRiViewSnapshot(_interpreters.value, _bindings.value)
        writeLock.withLock { runCatching { s.save(snap) } }
    }
}
