package org.example.project

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class AndroidInterpreterDownloader : InterpreterDownloader {
    override suspend fun downloadText(url: String): String = withContext(Dispatchers.IO) {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            val code = conn.responseCode
            if (code !in 200..299) throw IllegalStateException("HTTP $code")
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}

/**
 * 每个解释器存一个 ci_N.js；meta.txt / bindings.txt 用 Tab 分隔，免 JSON 依赖。
 *
 * 持久化工程要点：
 *   - 原子写入：先写 *.tmp 再 rename 覆盖，避免写一半崩溃损坏正式文件
 *   - 删除清理：save 时删除已不在快照中的 ci_*.js，磁盘不残留垃圾
 *   - 加载容错：文件损坏/缺行时跳过或整体返回 null，绝不崩溃；bindings 过滤失效引用
 *   - 格式兼容：与旧版 Tab 分隔格式完全一致，旧数据可直接读取
 */
class AndroidCodeRiViewStorage(context: Context) : CodeRiViewStorage {
    private val dir = File(context.filesDir, "code_ri_view")
    private val metaFile = File(dir, "meta.txt")
    private val bindingsFile = File(dir, "bindings.txt")

    private fun clean(s: String) = s.replace('\t', ' ').replace("\n", " ")

    override suspend fun load(): CodeRiViewSnapshot? = withContext(Dispatchers.IO) {
        if (!metaFile.exists()) return@withContext null
        try {
            val interpreters = metaFile.readLines().mapNotNull { line ->
                val p = line.split('\t')
                if (p.size < 3) return@mapNotNull null
                val f = File(dir, "${p[0]}.js")
                if (f.exists()) CodeInterpreter(p[0], p[1], p[2], f.readText()) else null
            }
            val validIds = interpreters.map { it.id }.toSet()
            val bindings = bindingsFile.takeIf { it.exists() }?.readLines()
                ?.mapNotNull { line ->
                    val p = line.split('\t')
                    if (p.size >= 2 && p[1] in validIds) p[0] to p[1] else null
                }?.toMap() ?: emptyMap()
            CodeRiViewSnapshot(interpreters, bindings)
        } catch (t: Throwable) {
            // 存储损坏：返回 null，上层从空状态开始，不崩溃
            null
        }
    }

    override suspend fun save(snapshot: CodeRiViewSnapshot) = withContext(Dispatchers.IO) {
        dir.mkdirs()
        // 1) 逐个原子写解释器源码
        snapshot.interpreters.forEach {
            atomicWrite(File(dir, "${it.id}.js"), it.sourceCode)
        }
        // 2) 清理已删除解释器的磁盘文件（killinter 等场景）
        val keep = snapshot.interpreters.map { "${it.id}.js" }.toSet()
        dir.listFiles()?.forEach { f ->
            if (f.isFile && f.name.endsWith(".js") && f.name !in keep) {
                f.delete()
            }
        }
        // 3) meta / bindings 原子写
        atomicWrite(
            metaFile,
            snapshot.interpreters.joinToString("\n") {
                "${it.id}\t${clean(it.name)}\t${clean(it.sourceUrl)}"
            }
        )
        atomicWrite(
            bindingsFile,
            snapshot.bindings.entries.joinToString("\n") { "${it.key}\t${it.value}" }
        )
        Unit
    }

    /** 原子写：先写同目录 .tmp，再 rename 覆盖目标（POSIX 同目录 rename 原子）。 */
    private fun atomicWrite(file: File, content: String) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(content)
        if (!tmp.renameTo(file)) {
            // rename 失败（极端情况）则回退直接写
            file.writeText(content)
            tmp.delete()
        }
    }
}
