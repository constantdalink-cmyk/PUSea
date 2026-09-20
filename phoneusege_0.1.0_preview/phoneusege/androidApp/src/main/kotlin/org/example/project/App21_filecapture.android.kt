package org.example.project

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

class AndroidImageBitmapDecoder : ImageBitmapDecoder {
    override fun decode(bytes: ByteArray): ImageBitmap? = try {
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    } catch (_: Throwable) { null }
}

class AndroidFileCapturePicker(
    private val activity: ComponentActivity
) : FileCapturePicker {

    private val pending = AtomicReference<((CanvasCapturedFile?) -> Unit)?>(null)
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    private val launcher = activity.registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        val cb = pending.getAndSet(null) ?: return@registerForActivityResult
        if (uri == null) { cb(null); return@registerForActivityResult }
        io.execute {
            try {
                val file = readUri(activity.applicationContext, uri)
                main.post { cb(file) }
            } catch (t: Throwable) {
                main.post {
                    CanvasFileStore.setError(t.message)
                    cb(null)
                }
            }
        }
    }

    override fun launch(onResult: (CanvasCapturedFile?) -> Unit) {
        pending.set(onResult)
        try { launcher.launch(arrayOf("*/*")) }
        catch (t: Throwable) {
            CanvasFileStore.setError(t.message)
            onResult(null)
        }
    }

    override fun importByPath(path: String, onResult: (CanvasCapturedFile?) -> Unit) {
        io.execute {
            try {
                val src = File(path)
                if (!src.exists() || !src.isFile || !src.canRead()) {
                    main.post {
                        CanvasFileStore.setError("unreadable")
                        onResult(null)
                    }
                    return@execute
                }
                val dir = File(activity.applicationContext.filesDir, "canvas_captures")
                if (!dir.exists()) dir.mkdirs()
                val dest = File(dir, src.name) // 直接覆盖，不加 _数字
                src.inputStream().use { i -> dest.outputStream().use { o -> i.copyTo(o) } }
                val out = process(dest)
                main.post { onResult(out) }
            } catch (t: Throwable) {
                main.post {
                    CanvasFileStore.setError(t.message)
                    onResult(null)
                }
            }
        }
    }

    private fun readUri(context: Context, uri: Uri): CanvasCapturedFile {
        var name = "file"
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (c.moveToFirst() && i >= 0) c.getString(i)?.takeIf { it.isNotBlank() }?.let { name = it }
        }
        val dir = File(context.filesDir, "canvas_captures")
        if (!dir.exists()) dir.mkdirs()
        val dest = File(dir, name) // 直接覆盖
        context.contentResolver.openInputStream(uri)?.use { i ->
            dest.outputStream().use { o -> i.copyTo(o) }
        } ?: error("open failed")
        return process(dest)
    }

    private fun process(dest: File): CanvasCapturedFile {
        val size = dest.length()
        val max = 10 * 1024 * 1024
        val bytes = if (size <= max) dest.readBytes() else dest.inputStream().use { inp ->
            val buf = ByteArray(max)
            var n = 0
            while (n < max) {
                val r = inp.read(buf, n, max - n)
                if (r <= 0) break
                n += r
            }
            buf.copyOf(n)
        }

        // 灵活识别：先看是不是图片头，再看能不能当文本，否则二进制拒绝
        var isImage = false
        if (bytes.size >= 3) {
            val b0 = bytes[0].toInt() and 0xFF
            val b1 = bytes[1].toInt() and 0xFF
            val b2 = bytes[2].toInt() and 0xFF
            val b3 = if (bytes.size > 3) bytes[3].toInt() and 0xFF else -1
            isImage =
                (b0 == 0xFF && b1 == 0xD8 && b2 == 0xFF) ||                 // jpg
                (b0 == 0x89 && b1 == 0x50 && b2 == 0x4E && b3 == 0x47) ||   // png
                (b0 == 0x47 && b1 == 0x49 && b2 == 0x46 && b3 == 0x38) ||   // gif
                (b0 == 0x42 && b1 == 0x4D) ||                               // bmp
                (b0 == 0x52 && b1 == 0x49 && b2 == 0x46 && b3 == 0x46)      // webp/riff
        }

        var isText = false
        if (!isImage) {
            val sample = if (bytes.size > 512) bytes.copyOf(512) else bytes
            val nul = sample.count { it == 0.toByte() }
            // 基本可打印/可解码，就当文本；大量 NUL 当二进制
            if (nul <= sample.size / 20) {
                val s = runCatching { String(sample, Charsets.UTF_8) }.getOrNull()
                if (s != null) {
                    val ok = s.count { ch ->
                        ch == '\n' || ch == '\r' || ch == '\t' || ch.code in 32..126 || ch.code > 127
                    }
                    isText = ok >= (s.length * 0.85)
                }
            }
        }

        if (!isImage && !isText) error("binary not allowed")

        val preview = if (isText) {
            val raw = String(bytes, Charsets.UTF_8).replace("\u0000", "")
            if (raw.length > 180) raw.take(180) + "..." else raw
        } else ""

        return CanvasCapturedFile(
            id = CanvasFileStore.nextId(),
            name = dest.name,
            sizeBytes = size,
            sizeLabel = formatCapturedSize(size),
            contentPreview = preview,
            localPath = dest.absolutePath,
            isText = isText,
            isImage = isImage,
            rawBytes = if (isImage) bytes else null
        )
    }
}
