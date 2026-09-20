package org.example.project

import android.content.Context
import java.io.File

class AndroidFileStorage(private val context: Context) : StorageProvider {

    private val dir: File
        get() = context.filesDir

    override fun save(key: String, value: String) {
        try {
            if (!dir.exists()) dir.mkdirs()
            File(dir, "$key.json").writeText(value, Charsets.UTF_8)
        } catch (_: Exception) {
            // 写入失败，静默处理
        }
    }

    override fun load(key: String): String? {
        return try {
            val file = File(dir, "$key.json")
            if (file.exists()) file.readText(Charsets.UTF_8) else null
        } catch (_: Exception) {
            null
        }
    }
}
