package org.example.project

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class CanvasCapturedFile(
    val id: String,
    val name: String,
    val sizeBytes: Long,
    val sizeLabel: String,
    val contentPreview: String,
    val localPath: String,
    val isText: Boolean,
    val isImage: Boolean,
    val rawBytes: ByteArray?
)

interface FileCapturePicker {
    fun launch(onResult: (CanvasCapturedFile?) -> Unit)
    fun importByPath(path: String, onResult: (CanvasCapturedFile?) -> Unit)
}

interface ImageBitmapDecoder {
    fun decode(bytes: ByteArray): ImageBitmap?
}

object ImageBitmapDecodeStore {
    private var decoder: ImageBitmapDecoder? = null
    fun initialize(decoder: ImageBitmapDecoder) { this.decoder = decoder }
    fun decode(bytes: ByteArray): ImageBitmap? = decoder?.decode(bytes)
}

object CanvasFileStore {
    private val _files = MutableStateFlow<List<CanvasCapturedFile>>(emptyList())
    val files: StateFlow<List<CanvasCapturedFile>> = _files

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError

    private var picker: FileCapturePicker? = null
    private var idSeq = 0L

    fun initialize(picker: FileCapturePicker) { this.picker = picker }

    fun capture() {
        val p = picker ?: return
        p.launch { file -> if (file != null) add(file) }
    }

    fun importByPath(path: String) {
        val p = picker ?: return
        val t = path.trim()
        if (t.isEmpty()) return
        p.importByPath(t) { file -> if (file != null) add(file) }
    }

    fun add(file: CanvasCapturedFile) {
        // 同名直接覆盖，不加 _数字
        _files.value = listOf(file) + _files.value.filter { it.name != file.name }
        _lastError.value = null
    }

    fun remove(id: String) { _files.value = _files.value.filter { it.id != id } }
    fun clear() { _files.value = emptyList(); _lastError.value = null }
    fun nextId(): String { idSeq++; return "cf_$idSeq" }
    fun setError(msg: String?) { _lastError.value = msg }

    // ===== 附件全量文本读取钩子 =====
    // 由平台层注入（如 AndroidFileCapturePicker 的 actual 实现）：把网格文件里
    // 的完整文本内容读出来交给请求上下文。未注入时返回 null，调用方会退回
    // contentPreview。这是纯新增钩子，不影响现有构造与调用。
    private var contentReader: (suspend (CanvasCapturedFile) -> String?)? = null
    fun setContentReader(reader: suspend (CanvasCapturedFile) -> String?) {
        contentReader = reader
    }
    suspend fun readFullText(file: CanvasCapturedFile): String? = contentReader?.invoke(file)
}

fun formatCapturedSize(bytes: Long): String = when {
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> "${bytes / 1024L} KB"
    else -> "${bytes / (1024L * 1024L)} MB"
}
