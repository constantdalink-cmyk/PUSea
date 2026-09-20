package org.example.project

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 任务7：Android 侧 HttpRequester 实现（HttpURLConnection，GET 拼 query / POST 带 JSON body）
 *
 * 【爬虫爬不了 · 排查结论与修复记录】
 * 结论：原实现的 GET 主链路本身是对的——已正确 withContext(IO)、带 15s 超时、
 *       非 2xx 抛 IOException 而非静默吞掉、crawler 传 emptyMap()+null 时 URL 原样透传。
 *       此前怀疑的「吞异常 / 无超时 / 主线程网络 / 非2xx返回空」均被实际代码逐一排除。
 *
 * 真凶：三个目标站（smithery.ai、glama.ai 走 Cloudflare，mcp.so 有 CDN 防护）
 *       会直接 403/406 拒绝 HttpURLConnection 的默认 UA（Dalvik/…）。
 *       原代码从未设置 User-Agent → 三站全部非 2xx → 全部抛 IOException →
 *       newItems 恒为空 → 表现即「一条都爬不到」。
 *
 * 修复：补浏览器级 User-Agent（配套 Accept-Language），让请求呈现为正常客户端；
 *       并保留非 2xx 抛错（带状态码），配合 PlazaStore.lastCrawlError 可直观看到
 *       每一站真实的 HTTP 状态（403 / 429 / 5xx），根因不再隐身。
 */
class AndroidHttpRequester : HttpRequester {
    override suspend fun request(
        method: String,
        url: String,
        queryParams: Map<String, String>,
        body: String?
    ): String = withContext(Dispatchers.IO) {
        val isBodyMethod = body != null
        val fullUrl = if (queryParams.isNotEmpty() && !isBodyMethod) {
            url + (if (url.contains('?')) "&" else "?") +
                queryParams.entries.joinToString("&") { (k, v) ->
                    URLEncoder.encode(k, "UTF-8") + "=" + URLEncoder.encode(v, "UTF-8")
                }
        } else url

        val conn = (URL(fullUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = method.uppercase()
            connectTimeout = 15000
            readTimeout = 15000
            // 【关键修复】补浏览器级 UA：Cloudflare / CDN 会 403 掉默认 Dalvik UA，
            // 这是「三站全爬不到」的真凶。
            setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
            )
            setRequestProperty("Accept", "application/json, text/plain, */*")
            setRequestProperty("Accept-Language", "en-US,en;q=0.9")
            if (isBodyMethod) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        try {
            if (isBodyMethod) {
                conn.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            if (code in 200..299) {
                text
            } else {
                // 带状态码与响应片段抛错：配合 PlazaStore.lastCrawlError 直接暴露 403/429 等真实原因
                throw IOException("HTTP $code: ${text.take(200)}")
            }
        } finally {
            conn.disconnect()
        }
    }
}
