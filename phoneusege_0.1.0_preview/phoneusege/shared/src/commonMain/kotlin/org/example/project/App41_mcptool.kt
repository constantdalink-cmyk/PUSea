package org.example.project

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** MCP 工具参数定义 */
data class McpToolParameter(
    val name: String,
    val type: String = "string",
    val description: String = "",
    val required: Boolean = true
)

/** MCP 工具定义（AI 可发现、可调用的工具元信息） */
data class McpToolDefinition(
    val name: String,
    val description: String,
    val parameters: List<McpToolParameter> = emptyList()
)

/** MCP 工具调用结果 */
data class McpToolResult(
    val isSuccess: Boolean,
    val output: String,
    val error: String? = null
)

/** MCP 工具卡片条目（组件管理画布展示：名称/描述/参数/开关状态） */
data class McpToolUiEntry(
    val name: String,
    val description: String,
    val parameters: List<McpToolParameter>,
    val enabled: Boolean
)

/**
 * 任务4：AI 专用 MCP 工具注册表。
 *  - AI 通过 listMcpTools() 发现工具、callMcpTool() 调用工具；
 *  - 只注册 AI 允许使用的工具（anyenone 摘要等）；
 *  - 用户专用命令（copycode 等）一律不注册，AI 无法触达。
 * 权限模型与 CmdStore 的 CommandCaller 双重隔离。
 *
 * 任务23（组件管理画布）：新增 ON/OFF 开关能力 ——
 *  - setEnabled(name, false)：AI 无法发现（listMcpTools / tools JSON / 系统提示全部过滤）、
 *    无法调用（callMcpTool 直接拒绝 "tool disabled"）；
 *  - setEnabled(name, true)：恢复可用，无需重新注册；
 *  - uiState：响应式状态流，组件管理画布 collect 渲染工具卡片。
 */
object McpToolStore {
    private val definitions = mutableMapOf<String, McpToolDefinition>()
    private val handlers = mutableMapOf<String, suspend (Map<String, String>) -> McpToolResult>()
    /** 开关状态：缺失 = 默认开启 */
    private val enabledFlags = mutableMapOf<String, Boolean>()
    /** 调用深度计数（godo 用户自定义 tool 互相调用时防无限递归；单 AI 流场景足够） */
    private var activeDepth = 0

    /** 工具卡片状态流（组件管理画布 collect 渲染，register/unregister/setEnabled 时发布） */
    private val _uiState = MutableStateFlow<List<McpToolUiEntry>>(emptyList())
    val uiState: StateFlow<List<McpToolUiEntry>> = _uiState

    /** 开关或工具变动持久化监听回调 */
    var onFlagsChanged: (() -> Unit)? = null

    private fun publish() {
        _uiState.value = definitions.values
            .map { def ->
                McpToolUiEntry(
                    name = def.name,
                    description = def.description,
                    parameters = def.parameters,
                    enabled = enabledFlags[def.name] ?: true
                )
            }
            .sortedBy { it.name }
        onFlagsChanged?.invoke()
    }

    fun register(
        definition: McpToolDefinition,
        handler: suspend (Map<String, String>) -> McpToolResult
    ) {
        definitions[definition.name] = definition
        handlers[definition.name] = handler
        // 重新注册视为恢复开启（godo 覆盖注册同名前先卸载旧开关状态）
        enabledFlags[definition.name] = true
        publish()
    }

    /** 列出 AI 可见（已开启）的工具 */
    fun listMcpTools(): List<McpToolDefinition> =
        definitions.values.filter { enabledFlags[it.name] ?: true }

    /** 列出全部已注册工具（含关闭的；组件管理画布用） */
    fun listAllMcpTools(): List<McpToolDefinition> = definitions.values.toList()

    /**
     * 任务23：开关一个工具。
     * @param enabled false = AI 无法发现、无法调用；true = 恢复可用
     * @return 是否找到并更新了该工具
     */
    fun setEnabled(name: String, enabled: Boolean): Boolean {
        val key = definitions.keys.firstOrNull { it == name }
            ?: definitions.keys.firstOrNull { it.equals(name, ignoreCase = true) }
            ?: return false
        if ((enabledFlags[key] ?: true) == enabled) return true
        enabledFlags[key] = enabled
        publish()
        return true
    }

    /** 当前是否开启（未记录默认开启；未注册返回 false） */
    fun isEnabled(name: String): Boolean {
        val key = definitions.keys.firstOrNull { it == name }
            ?: definitions.keys.firstOrNull { it.equals(name, ignoreCase = true) }
            ?: return false
        return enabledFlags[key] ?: true
    }

    /** 注销一个工具（用户自定义 tool 移除时同步调用） */
    fun unregister(name: String) {
        definitions.remove(name)
        handlers.remove(name)
        enabledFlags.remove(name)
        publish()
    }

    /** 导出当前开关状态（持久化用） */
    fun getEnabledFlags(): Map<String, Boolean> = enabledFlags.toMap()

    /** 批量恢复开关状态（启动还原用） */
    fun restoreEnabledFlags(flags: Map<String, Boolean>) {
        enabledFlags.putAll(flags)
        publish()
    }

    /**
     * 生成 OpenAI 风格 function calling 的 tools 定义 JSON（供原生 tool call 接入）。
     * 例：[{"type":"function","function":{"name":"tcopsay","description":"...","parameters":{...}}}]
     * 任务23：只包含已开启（AI 可见）的工具。
     */
    fun buildOpenAiToolsJson(): String {
        val items = listMcpTools().map { def ->
            buildString {
                append("{\"type\":\"function\",\"function\":{\"name\":\"")
                append(def.name)
                append("\",\"description\":\"")
                append(escapeJson(def.description))
                append("\",\"parameters\":{\"type\":\"object\",\"properties\":{")
                append(def.parameters.joinToString(",") { p ->
                    "\"${p.name}\":{\"type\":\"${p.type}\",\"description\":\"${escapeJson(p.description)}\"}"
                })
                append("},\"required\":[")
                append(def.parameters.filter { it.required }.joinToString(",") { "\"${it.name}\"" })
                append("]}}}")
            }
        }
        return "[" + items.joinToString(",") + "]"
    }

    private fun escapeJson(s: String): String =
        s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")

    /** 生成可注入 AI prompt 的工具描述文本（工具发现）；任务23：只包含已开启的工具 */
    fun buildToolsPrompt(): String {
        val enabled = listMcpTools()
        if (enabled.isEmpty()) return "(no mcp tools available)"
        return buildString {
            appendLine("Available MCP tools (call them when needed, one per <call> block):")
            enabled.forEach { def ->
                appendLine("- ${def.name}: ${def.description}")
                if (def.parameters.isNotEmpty()) {
                    val params = def.parameters.joinToString(", ") {
                        "${it.name}${if (it.required) "" else "?"}:${it.type}"
                    }
                    appendLine("    parameters: $params")
                }
            }
        }
    }

    /** 当前调用深度（godo 引擎递归防护用） */
    fun currentDepth(): Int = activeDepth

    /** 调用工具（AI 专用入口；godo 用户自定义 tool 会再次进入，深度递增）；
     *  任务23：已关闭的工具直接拒绝，不执行 handler */
    suspend fun callMcpTool(name: String, args: Map<String, String>): McpToolResult {
        if (definitions.containsKey(name) && !(enabledFlags[name] ?: true)) {
            return McpToolResult(isSuccess = false, output = "", error = "tool disabled: $name")
        }
        val handler = handlers[name]
            ?: return McpToolResult(isSuccess = false, output = "", error = "unknown tool: $name")
        activeDepth++
        return try {
            handler(args)
        } catch (t: Throwable) {
            McpToolResult(isSuccess = false, output = "", error = t.message ?: "tool call failed: $name")
        } finally {
            activeDepth--
        }
    }
}
