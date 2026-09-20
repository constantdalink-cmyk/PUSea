package org.example.project

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.coroutines.coroutineContext

// ==============================================================================
// Android actual：全部网络 expect 函数（testKeyConnection / requestChat /
// requestChatStream / probeHttpOrigin）
//
// 关键特性（任务8：原生 tool call → [[tool_call]] 标记通道）：
//  - 请求体自动附带 McpToolStore 的工具定义（OpenAI "tools" / Anthropic "tools"）
//  - SSE 解析时累积 delta.tool_calls（OpenAI）与 tool_use 块（Anthropic）
//  - 流结束（finish_reason=="tool_calls" / message_stop）时，把累积的工具调用
//    拼成 [[tool_call:{"name":"...","args":{...}}]] 文本增量发给 onDelta
//  - 前端现有链路（工具气泡齿轮动画 / plan 黑块横线动画）无需任何改动即可触发
// ==============================================================================

// ==================== 基础工具 ====================

private fun isOpenAiProvider(config: SavedKeyConfig): Boolean =
    config.provider.equals("OpenAI", true)

private fun chatUrl(config: SavedKeyConfig): String {
    val base = normalizeBaseUrl(config.baseUrl)
    return if (isOpenAiProvider(config)) {
        if (base.contains("/chat/completions")) base else base + "/chat/completions"
    } else {
        if (base.contains("/messages")) base else base + "/v1/messages"
    }
}

private fun buildHttpConnection(url: String, config: SavedKeyConfig, timeoutMs: Int): HttpURLConnection {
    val conn = URL(url).openConnection() as HttpURLConnection
    conn.requestMethod = "POST"
    conn.connectTimeout = 15000
    conn.readTimeout = timeoutMs
    conn.doOutput = true
    conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
    conn.setRequestProperty("Accept", "text/event-stream")
    if (isOpenAiProvider(config)) {
        conn.setRequestProperty("Authorization", "Bearer ${config.apiKey}")
    } else {
        conn.setRequestProperty("x-api-key", config.apiKey)
        conn.setRequestProperty("anthropic-version", "2023-06-01")
    }
    return conn
}

// ==================== 消息体构建 ====================

private fun base64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

/**
 * OpenAI 消息体构建：用户消息带附件（图片）时，content 升级为 parts 数组：
 * [{"type":"text","text":...}, {"type":"image_url","image_url":{"url":"data:image/png;base64,..."}}]
 * 无附件时保持纯字符串，与旧行为一致。
 */
private fun buildOpenAiMessages(history: List<ChatTurn>): JSONArray {
    val arr = JSONArray()
    history.forEach { turn ->
        val obj = JSONObject().put("role", turn.role)
        val images = turn.attachments.filter { it.mimeType.startsWith("image/") }
        if (images.isEmpty()) {
            obj.put("content", turn.content)
        } else {
            val parts = JSONArray()
            if (turn.content.isNotBlank()) {
                parts.put(JSONObject().put("type", "text").put("text", turn.content))
            }
            images.forEach { att ->
                parts.put(
                    JSONObject()
                        .put("type", "image_url")
                        .put(
                            "image_url",
                            JSONObject().put("url", "data:${att.mimeType};base64,${base64(att.bytes)}")
                        )
                )
            }
            obj.put("content", parts)
        }
        arr.put(obj)
    }
    return arr
}

private fun buildAnthropicMessages(history: List<ChatTurn>): Pair<String?, JSONArray> {
    val systemBlocks = mutableListOf<String>()
    val arr = JSONArray()
    history.forEach { turn ->
        when (turn.role) {
            "system" -> {
                if (turn.content.isNotBlank()) {
                    systemBlocks.add(turn.content)
                }
            }
            "user" -> {
                // 带图片附件 → content 升级为 blocks 数组（text + image base64 块）
                val images = turn.attachments.filter { it.mimeType.startsWith("image/") }
                if (images.isEmpty()) {
                    arr.put(JSONObject().put("role", "user").put("content", turn.content))
                } else {
                    val blocks = JSONArray()
                    if (turn.content.isNotBlank()) {
                        blocks.put(JSONObject().put("type", "text").put("text", turn.content))
                    }
                    images.forEach { att ->
                        blocks.put(
                            JSONObject()
                                .put("type", "image")
                                .put(
                                    "source",
                                    JSONObject()
                                        .put("type", "base64")
                                        .put("media_type", att.mimeType)
                                        .put("data", base64(att.bytes))
                                )
                        )
                    }
                    arr.put(JSONObject().put("role", "user").put("content", blocks))
                }
            }
            "assistant" -> arr.put(JSONObject().put("role", "assistant").put("content", turn.content))
            else -> arr.put(JSONObject().put("role", "user").put("content", turn.content))
        }
    }
    val combinedSystem = if (systemBlocks.isNotEmpty()) {
        systemBlocks.joinToString("\n\n")
    } else {
        null
    }
    return combinedSystem to arr
}

/** OpenAI 风格 tools 数组（来自 McpToolStore） */
private fun buildOpenAiTools(): JSONArray? {
    val tools = McpToolStore.buildOpenAiToolsJson()
    val arr = try { JSONArray(tools) } catch (_: Exception) { JSONArray() }
    return if (arr.length() > 0) arr else null
}

/** Anthropic 风格 tools 数组 */
private fun buildAnthropicTools(): JSONArray? {
    val defs = McpToolStore.listMcpTools()
    if (defs.isEmpty()) return null
    val arr = JSONArray()
    defs.forEach { def ->
        val props = JSONObject()
        val required = JSONArray()
        def.parameters.forEach { p ->
            props.put(
                p.name,
                JSONObject().put("type", p.type).put("description", p.description)
            )
            if (p.required) required.put(p.name)
        }
        val inputSchema = JSONObject()
            .put("type", "object")
            .put("properties", props)
            .put("required", required)
        arr.put(
            JSONObject()
                .put("name", def.name)
                .put("description", def.description)
                .put("input_schema", inputSchema)
        )
    }
    return arr
}

// ==================== 工具调用 → [[tool_call]] 标记 ====================

/** 累积中的工具调用（arguments 分块到达，需拼接） */
private class PendingToolCall {
    var name: String = ""
    val args = StringBuilder()
}

private fun PendingToolCall.toMarker(): String =
    "[[tool_call:{\"name\":\"${name}\",\"args\":${args.toString().ifBlank { "{}" }}}]]"

private fun buildMarkers(calls: Collection<PendingToolCall>): String =
    calls.joinToString("") { it.toMarker() }

// ==================== OpenAI SSE 解析 ====================

/**
 * 解析一行 OpenAI 流式 data JSON。
 * @return true = 流结束（[DONE] 或 finish_reason 已处理）
 */
private suspend fun handleOpenAiDelta(
    json: JSONObject,
    text: StringBuilder,
    pending: MutableMap<Int, PendingToolCall>,
    onDelta: suspend (String) -> Unit
) {
    val choices = json.optJSONArray("choices") ?: return
    if (choices.length() == 0) return
    val choice = choices.optJSONObject(0) ?: return
    val delta = choice.optJSONObject("delta") ?: return

    // 1. 普通文本增量
    val content = delta.optString("content", "")
    if (content.isNotEmpty()) {
        text.append(content)
        onDelta(content)
    }

    // 2. 工具调用增量（按 index 累积，arguments 分块）
    val tcs = delta.optJSONArray("tool_calls")
    if (tcs != null) {
        for (i in 0 until tcs.length()) {
            val tc = tcs.optJSONObject(i) ?: continue
            val idx = tc.optInt("index", pending.size)
            val fn = tc.optJSONObject("function") ?: continue
            val entry = pending.getOrPut(idx) { PendingToolCall() }
            val name = fn.optString("name", "")
            if (name.isNotEmpty()) entry.name = name
            entry.args.append(fn.optString("arguments", ""))
        }
    }

    // 3. 结束：把累积的工具调用转成标记输出
    val finish = choice.optString("finish_reason", "")
    if (finish == "tool_calls" && pending.isNotEmpty()) {
        val marker = buildMarkers(pending.values)
        pending.clear()
        if (marker.isNotEmpty()) onDelta(marker)
    }
}

// ==================== Anthropic SSE 解析 ====================

/**
 * 解析一行 Anthropic 流式事件（event: 行 + data: 行）。
 * @return true = message_stop（流结束）
 */
private suspend fun handleAnthropicEvent(
    eventType: String,
    data: String?,
    text: StringBuilder,
    pending: MutableMap<Int, PendingToolCall>,
    onDelta: suspend (String) -> Unit
): Boolean {
    if (data == null) return false
    if (eventType == "message_stop") {
        if (pending.isNotEmpty()) {
            val marker = buildMarkers(pending.values)
            pending.clear()
            if (marker.isNotEmpty()) onDelta(marker)
        }
        return true
    }
    if (eventType == "content_block_start") {
        val block = try { JSONObject(data) } catch (_: Exception) { return false }
        val idx = block.optInt("index", pending.size)
        val content = block.optJSONObject("content_block") ?: return false
        if (content.optString("type") == "tool_use") {
            val entry = pending.getOrPut(idx) { PendingToolCall() }
            entry.name = content.optString("name", "")
        }
        return false
    }
    if (eventType == "content_block_delta") {
        val block = try { JSONObject(data) } catch (_: Exception) { return false }
        val delta = block.optJSONObject("delta") ?: return false
        val type = delta.optString("type", "")
        if (type == "text_delta") {
            val t = delta.optString("text", "")
            if (t.isNotEmpty()) {
                text.append(t)
                onDelta(t)
            }
        } else if (type == "input_json_delta") {
            val idx = block.optInt("index", pending.size)
            val entry = pending.getOrPut(idx) { PendingToolCall() }
            entry.args.append(delta.optString("partial_json", ""))
        }
        return false
    }
    return false
}

// ==================== 流式请求（核心） ====================

actual suspend fun requestChatStream(
    config: SavedKeyConfig,
    history: List<ChatTurn>,
    onDelta: suspend (String) -> Unit
): ChatStreamResult = withContext(Dispatchers.IO) {
    val conn = try {
        buildHttpConnection(chatUrl(config), config, 90000)
    } catch (t: Throwable) {
        return@withContext ChatStreamResult(
            isSuccess = false,
            errorMessage = t.message?.take(200) ?: "Connection failed"
        )
    }

    try {
        // 请求体
        val body = JSONObject().put("model", config.model)
        if (isOpenAiProvider(config)) {
            body.put("messages", buildOpenAiMessages(history))
            body.put("stream", true)
            buildOpenAiTools()?.let { body.put("tools", it) }
        } else {
            val (system, messages) = buildAnthropicMessages(history)
            if (system != null) body.put("system", system)
            body.put("messages", messages)
            body.put("max_tokens", 4096)
            body.put("stream", true)
            buildAnthropicTools()?.let { body.put("tools", it) }
        }
        conn.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body.toString()) }

        val code = conn.responseCode
        if (code !in 200..299) {
            val errText = conn.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            return@withContext ChatStreamResult(
                isSuccess = false,
                errorMessage = "HTTP $code: ${errText.take(200)}",
                statusCode = code
            )
        }

        val text = StringBuilder()
        val pending = mutableMapOf<Int, PendingToolCall>()

        val reader = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8))
        if (isOpenAiProvider(config)) {
            // OpenAI 兼容：data: {...} 行，直到 data: [DONE]
            while (coroutineContext.isActive) {
                val line = reader.readLine() ?: break
                if (!line.startsWith("data:")) continue
                val payload = line.removePrefix("data:").trim()
                if (payload == "[DONE]") break
                if (payload.isEmpty()) continue
                try {
                    handleOpenAiDelta(JSONObject(payload), text, pending, onDelta)
                } catch (_: Exception) {
                    // 跳过无法解析的块
                }
            }
            // 流被中断但还有未输出的工具调用（少数服务端不发 finish_reason）
            if (pending.isNotEmpty() && coroutineContext.isActive) {
                val marker = buildMarkers(pending.values)
                pending.clear()
                if (marker.isNotEmpty()) onDelta(marker)
            }
        } else {
            // Anthropic：event: X 行 + data: {...} 行
            var eventType = ""
            var finished = false
            while (coroutineContext.isActive) {
                val line = reader.readLine() ?: break
                when {
                    line.startsWith("event:") -> eventType = line.removePrefix("event:").trim()
                    line.startsWith("data:") -> {
                        val data = line.removePrefix("data:").trim()
                        if (handleAnthropicEvent(eventType, data, text, pending, onDelta)) {
                            finished = true
                            break
                        }
                    }
                }
            }
            if (!finished && pending.isNotEmpty() && coroutineContext.isActive) {
                val marker = buildMarkers(pending.values)
                pending.clear()
                if (marker.isNotEmpty()) onDelta(marker)
            }
        }

        ChatStreamResult(
            isSuccess = true,
            fullText = text.toString()
        )
    } catch (t: kotlinx.coroutines.CancellationException) {
        throw t
    } catch (t: Throwable) {
        ChatStreamResult(
            isSuccess = false,
            fullText = "",
            errorMessage = t.message?.take(200) ?: "Stream failed"
        )
    } finally {
        conn.disconnect()
    }
}

// ==================== 非流式请求 ====================

actual suspend fun requestChat(
    config: SavedKeyConfig,
    history: List<ChatTurn>
): ChatReplyResult = withContext(Dispatchers.IO) {
    val conn = try {
        buildHttpConnection(chatUrl(config), config, 60000)
    } catch (t: Throwable) {
        return@withContext ChatReplyResult(
            isSuccess = false,
            errorMessage = t.message?.take(200) ?: "Connection failed"
        )
    }
    try {
        val body = JSONObject().put("model", config.model)
        if (isOpenAiProvider(config)) {
            body.put("messages", buildOpenAiMessages(history))
            body.put("stream", false)
            buildOpenAiTools()?.let { body.put("tools", it) }
        } else {
            val (system, messages) = buildAnthropicMessages(history)
            if (system != null) body.put("system", system)
            body.put("messages", messages)
            body.put("max_tokens", 4096)
            body.put("stream", false)
            buildAnthropicTools()?.let { body.put("tools", it) }
        }
        conn.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body.toString()) }

        val code = conn.responseCode
        if (code !in 200..299) {
            val errText = conn.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            return@withContext ChatReplyResult(
                isSuccess = false,
                errorMessage = "HTTP $code: ${errText.take(200)}",
                statusCode = code
            )
        }

        val resp = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val json = try { JSONObject(resp) } catch (_: Exception) {
            return@withContext ChatReplyResult(isSuccess = false, errorMessage = "bad json response")
        }

        if (isOpenAiProvider(config)) {
            val choices = json.optJSONArray("choices") ?: JSONArray()
            val message = choices.optJSONObject(0)?.optJSONObject("message")
                ?: return@withContext ChatReplyResult(isSuccess = false, errorMessage = "no choices")
            val content = message.optString("content", "")
            val tcs = message.optJSONArray("tool_calls")
            val reply = if (tcs != null && tcs.length() > 0) {
                val markers = buildString {
                    for (i in 0 until tcs.length()) {
                        val fn = tcs.optJSONObject(i)?.optJSONObject("function") ?: continue
                        val name = fn.optString("name", "")
                        val args = fn.optString("arguments", "{}")
                        append("[[tool_call:{\"name\":\"$name\",\"args\":$args}]]")
                    }
                }
                content + markers
            } else content
            ChatReplyResult(isSuccess = true, reply = reply, statusCode = code)
        } else {
            val contentBlocks = json.optJSONArray("content") ?: JSONArray()
            val reply = buildString {
                for (i in 0 until contentBlocks.length()) {
                    val block = contentBlocks.optJSONObject(i) ?: continue
                    when (block.optString("type")) {
                        "text" -> append(block.optString("text", ""))
                        "tool_use" -> {
                            val name = block.optString("name", "")
                            val input = block.optJSONObject("input")?.toString() ?: "{}"
                            append("[[tool_call:{\"name\":\"$name\",\"args\":$input}]]")
                        }
                    }
                }
            }
            ChatReplyResult(isSuccess = true, reply = reply, statusCode = code)
        }
    } catch (t: Throwable) {
        ChatReplyResult(
            isSuccess = false,
            errorMessage = t.message?.take(200) ?: "Request failed"
        )
    } finally {
        conn.disconnect()
    }
}

// ==================== 测试与探活 ====================

actual suspend fun testKeyConnection(config: SavedKeyConfig): ConnectionTestResult =
    withContext(Dispatchers.IO) {
        val result = requestChat(
            config,
            listOf(ChatTurn(role = "user", content = "ping"))
        )
        ConnectionTestResult(
            isSuccess = result.isSuccess,
            message = if (result.isSuccess) {
                "OK (${result.statusCode ?: "HTTP"})"
            } else {
                result.errorMessage ?: "failed"
            },
            statusCode = result.statusCode
        )
    }

actual suspend fun probeHttpOrigin(baseUrl: String): HostProbeResult =
    withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val origin = originOnly(baseUrl)
            conn = (URL(origin).openConnection() as HttpURLConnection).apply {
                requestMethod = "HEAD"
                connectTimeout = 5000
                readTimeout = 5000
            }
            val code = conn!!.responseCode
            HostProbeResult(reachable = true, statusCode = code)
        } catch (t: Throwable) {
            HostProbeResult(reachable = false, errorMessage = t.message?.take(200))
        } finally {
            conn?.disconnect()
        }
    }

/** 提取 scheme+host+port（与 KeyConfigStore.originKeyForGrouping 逻辑一致） */
private fun originOnly(baseUrl: String): String {
    val normalized = normalizeBaseUrl(baseUrl)
    val schemeIdx = normalized.indexOf("://")
    val hostStart = if (schemeIdx >= 0) schemeIdx + 3 else 0
    val slashIdx = normalized.indexOf('/', hostStart)
    return if (slashIdx >= 0) normalized.substring(0, slashIdx) else normalized
}
