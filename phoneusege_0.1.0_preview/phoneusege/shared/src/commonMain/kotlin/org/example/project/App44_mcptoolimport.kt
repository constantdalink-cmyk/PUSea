package org.example.project

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 任务7：外部 MCP tool 导入。
 * godo <url> / godo <file> 支持两种格式：
 *  1) godo 原生格式（含【do】【hell】【theusenee】）；
 *  2) 通用 JSON MCP tool 规格（其他生态，如搜索引擎提供的 MCP tool）。
 */

/** 外部 HTTP 请求能力（commonMain 无网络，由平台侧注入，与 InterpreterDownloader 同模式） */
interface HttpRequester {
    suspend fun request(
        method: String,
        url: String,
        queryParams: Map<String, String>,
        body: String?
    ): String
}

/** 外部 MCP tool 规格（JSON，兼容其他生态的简化 MCP 定义） */
@Serializable
data class ExternalMcpToolSpec(
    val name: String = "",
    val description: String = "",
    val parameters: List<ExternalMcpToolParameter> = emptyList(),
    /** HTTP 转发模式：请求这个端点执行 tool（搜索引擎 MCP tool 常见） */
    val endpoint: String? = null,
    val method: String = "GET",
    /** GodoScript 模式：直接用这段逻辑代码执行 */
    val code: String? = null,
    /** 源码模式：下载这个 URL 的内容，再按 godo/JSON 解析（限一层递归） */
    val source: String? = null
)

@Serializable
data class ExternalMcpToolParameter(
    val name: String = "",
    val type: String = "string",
    val description: String = "",
    val required: Boolean = true
)

/** JSON 规格解析（宽松模式，未知字段忽略） */
object McpToolSpecParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parseJson(text: String): ExternalMcpToolSpec? = try {
        json.decodeFromString<ExternalMcpToolSpec>(text)
    } catch (_: Exception) {
        null
    }
}
