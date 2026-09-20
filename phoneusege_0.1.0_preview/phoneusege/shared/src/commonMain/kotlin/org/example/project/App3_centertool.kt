package org.example.project

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// ==================== 任务8-1：AI 工具执行 ====================

enum class ToolCallStatus { Running, Done, Error }

/** 一条工具调用记录（显示在 AI 回复下方的工具气泡） */
data class ToolCallRecord(
    val id: Long,
    val toolName: String,
    val argsText: String,
    val status: ToolCallStatus,
    val resultPreview: String,
    val displayTime: String,
    /** 任务18：所属的 AI 回复气泡 id —— 一次工具调用贴回其触发消息下方，
     *  一个回复里的每次工具调用各占一个气泡 */
    val anchorMessageId: Long? = null
)

/** AI 发起的一次工具调用（从流式文本标记中解析） */
data class ToolCallInvocation(
    val name: String,
    val args: Map<String, String>
)

// ==================== 任务8-2：plan 展示状态 ====================

/** plan 展示状态：加载中（纯黑方块+动画）→ 画线（横线由快到慢画出） */
data class PlanRecord(
    val isLoading: Boolean,
    val steps: List<String>
)

/** 全局 plan 展示状态（plan 工具由 AI 调用，UI 层 collect 展示） */
object PlanStore {
    private val _current = MutableStateFlow<PlanRecord?>(null)
    val current: StateFlow<PlanRecord?> = _current

    /** 任务20：灯泡开场是否已展示过（只在第一次调用 plan 工具时出现） */
    var bulbIntroShown by mutableStateOf(false)

    fun showLoading() {
        _current.value = PlanRecord(isLoading = true, steps = emptyList())
    }

    fun showSteps(steps: List<String>) {
        _current.value = PlanRecord(isLoading = false, steps = steps)
    }

    fun clear() {
        _current.value = null
        // 会话切换后，下一次 plan 调用重新播放灯泡开场
        bulbIntroShown = false
    }
}

// ==================== 任务8-3：AI 工具桥（协议解析 + plan 注册） ====================

object AiToolBridge {
    private var initialized = false

    /** AI 回复中的工具调用标记：[[tool_call:{"name":"...","args":{...}}]] */
    private val TOOL_CALL_RE = Regex("""\[\[tool_call:(\{.*?\})\]\]""", setOf(RegexOption.DOT_MATCHES_ALL))
    private val json = Json { ignoreUnknownKeys = true }

    /** 注册 plan 工具 + 初始化（幂等，App 启动时调用一次） */
    fun ensureInitialized() {
        if (initialized) return
        initialized = true
        McpToolStore.register(
            McpToolDefinition(
                name = "plan",
                description = "Break a complex task into a clear step-by-step plan. The plan is displayed to the user visually (black panel, lines draw one by one). Use when the user asks to plan, or when the task has multiple stages. Do NOT repeat the plan text in your visible reply.",
                parameters = listOf(
                    McpToolParameter(name = "task", description = "The task being planned", required = true),
                    McpToolParameter(
                        name = "steps",
                        description = "One step per line, e.g. '1. xxx\\n2. yyy'",
                        required = true
                    )
                )
            )
        ) { args ->
            val steps = (args["steps"] ?: "")
                .lines()
                .map { it.trim().trimStart('*', '-', '•').trim() }
                .filter { it.isNotBlank() }
            // 加载动画展示一小会儿，再转入画线阶段
            delay(1100)
            // 任务19：plan 已展示过步骤（非加载中）则不再刷新 ——
            // 修复"全部任务完成时 plan 重新加载一次"
            val currentPlan = PlanStore.current.value
            if (currentPlan == null || currentPlan.isLoading) {
                PlanStore.showSteps(steps)
            }
            McpToolResult(isSuccess = true, output = "plan recorded: ${steps.size} steps")
        }
    }

    /** 注入到 AI 请求的系统提示：工具调用协议 + 工具清单（正面教学完整格式） */
    fun buildToolSystemPrompt(): String {
        val tools = McpToolStore.buildToolsPrompt()
        return buildString {
            appendLine("You have MCP tools. To call a tool, append ONE COMPLETE block at the very END of your reply:")
            appendLine("[[tool_call:{\"name\":\"toolname\",\"args\":{\"arg\":\"value\"}}]]")
            appendLine("The block MUST be complete: starts with [[tool_call: and ends with ]] , with valid JSON inside. Examples:")
            appendLine("[[tool_call:{\"name\":\"tcopsay\",\"args\":{}}]]")
            appendLine("[[tool_call:{\"name\":\"plan\",\"args\":{\"task\":\"build app\",\"steps\":\"1. design\\n2. code\\n3. test\"}}]]")
            appendLine("The tool executes automatically and its result is returned to you; then continue your reply.")
            appendLine("When the user asks to plan / 拆解 / 计划 / 规划, call 'plan' first (args: task, steps - one step per line, numbered). Do not repeat the plan in your visible reply.")
            appendLine("Available tools:")
            appendLine(tools)
        }
    }

    /**
     * 兜底检测：AI 没输出 [[tool_call]] 标记但回复里含计划模式时，
     * 自动提取步骤供前端展示 plan 动画（不依赖模型格式遵循）。
     * 匹配：plan:/步骤/计划 关键词 + 至少 2 行数字步骤，或纯数字步骤列表。
     */
    fun extractPlanSteps(text: String): List<String>? {
        val t = text.trim()
        if (t.isEmpty()) return null

        // 任务13（重构）：兼容多种步骤行格式，覆盖中文模型的常见表达：
        //  "1. xxx" / "1、xxx" / "1) xxx" / "1）xxx" / "1: xxx" / "1．xxx"
        //  "第一步：xxx" / "第2步 xxx" / "步骤1：xxx" / "Step 1: xxx" / "step2 - xxx"
        //  "① xxx" / "- xxx" / "• xxx"
        val numbered = Regex("""^\s*[（(]?\d+[）)]?\s*[.、)）:：．]\s*(.+)$""")
        val chineseOrdinal = Regex("""^\s*第\s*[0-9一二三四五六七八九十百\d]+\s*[步阶段部分点项]\s*[:：、.\-]?\s*(.+)$""")
        val stepPrefix = Regex("""^\s*(?:步骤|step)\s*\d+\s*[:：、.\-]?\s*(.+)$""", setOf(RegexOption.IGNORE_CASE))
        val circled = Regex("""^\s*[①②③④⑤⑥⑦⑧⑨⑩⑪⑫]\s*[:：、.\-]?\s*(.+)$""")

        fun normalize(line: String): String? {
            val trimmed = line.trim().trimStart('*', '-', '•', ' ').trim()
            return numbered.matchEntire(trimmed)?.groupValues?.get(1)?.trim()
                ?: chineseOrdinal.matchEntire(trimmed)?.groupValues?.get(1)?.trim()
                ?: stepPrefix.matchEntire(trimmed)?.groupValues?.get(1)?.trim()
                ?: circled.matchEntire(trimmed)?.groupValues?.get(1)?.trim()
        }

        // 1. 直接从 [[tool_call 块里提取 plan 的 steps 值（JSON 字符串，含 \n 转义）
        val blockRe = Regex(
            """\[\[tool_call:\s*\{.*?"name"\s*:\s*"plan".*?"steps"\s*:\s*"((?:[^"\\]|\\.)*)""",
            setOf(RegexOption.DOT_MATCHES_ALL)
        )
        blockRe.find(t)?.groupValues?.get(1)?.let { rawSteps ->
            val decoded = rawSteps
                .replace("\\n", "\n")
                .replace("\\r", "")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
            val steps = decoded.lines()
                .mapNotNull { normalize(it) ?: it.trim().takeIf(String::isNotBlank) }
                .filter { it.isNotBlank() }
            if (steps.isNotEmpty()) return steps
        }

        // 2. 全文步骤行（任意格式，≥2 行即视为计划）
        val stepLines = t.lines()
            .map { it.trim() }
            .mapNotNull { normalize(it) }
            .filter { it.isNotBlank() }
        if (stepLines.size >= 2) return stepLines

        // 3. 关键词模式：plan:/步骤/计划/第一步/STEP 等出现时，收集步骤行与列表行
        val hasKeyword = t.contains(Regex("""(?i)(plan:|步骤|计划|规划|方案|安排|第一步|steps?:)"""))
        if (hasKeyword) {
            val lines = t.lines().map { it.trim() }.filter { it.isNotBlank() }
            val steps = lines.mapNotNull { line ->
                normalize(line) ?: if (line.startsWith("- ") || line.startsWith("• ")) {
                    line.trimStart('-', '•', ' ').trim()
                } else null
            }.filter { it.isNotBlank() }
            if (steps.size >= 2) return steps
        }
        return null
    }

    /**
     * 兜底检测：AI 文本提到"调用某工具"意图时，自动展示该工具的气泡。
     * 匹配（用|调用|使用|use|call|execute|run）+ 已知工具名。
     */
    fun extractMentionedToolCalls(text: String): List<ToolCallInvocation> {
        val known = McpToolStore.listMcpTools().map { it.name }
            .filter { it.matches(Regex("""[a-z_][a-z0-9_]*""")) }
        if (known.isEmpty()) return emptyList()
        // 任务13（重构）：兼容中文语态 —— "调用了plan工具"（无空格）、"使用 plan 工具"、
        // "调用一下 plan"、"use the plan tool"。工具名先任意匹配再过滤已知清单，避免误判。
        val intentRe = Regex(
            """(?i)(?<!不)(?:用|调用|使用|use|call|execute|run)(?:了|着|过|一下)?\s*(?:the\s+|tool\s+|工具\s*)?([a-z_][a-z0-9_]*)"""
        )
        return intentRe.findAll(text)
            .map { it.groupValues[1].lowercase() }
            .filter { it in known }
            .map { ToolCallInvocation(it, emptyMap()) }
            .toList()
    }

    /** 从 AI 回复文本中解析全部工具调用（兼容两种：完整文本块 + 标准原生 tool_calls JSON） */
    fun extractToolCalls(text: String): List<ToolCallInvocation> {
        val out = mutableListOf<ToolCallInvocation>()

        // 1. 文本块：[[tool_call:{...}]]（完整，含 ]] 收尾）
        for (m in TOOL_CALL_RE.findAll(text)) {
            try {
                parseToolCallJson(m.groupValues[1])?.let { out.add(it) }
            } catch (_: Exception) {
            }
        }

        // 2. 标准原生 tool_calls JSON（平台层未转换时兜底）：
        //    "tool_calls":[{"function":{"name":"plan","arguments":"{...}"}}]
        if (out.isEmpty()) {
            val tcRe = Regex(
                """"tool_calls"\s*:\s*\[\s*\{(?:\{?[^{}]*)*?"function"\s*:\s*\{\s*"name"\s*:\s*"([^"]+)"\s*,\s*"arguments"\s*:\s*"((?:[^"\\]|\\.)*)"""",
                setOf(RegexOption.DOT_MATCHES_ALL)
            )
            for (m in tcRe.findAll(text)) {
                val name = m.groupValues[1].lowercase()
                val argsRaw = m.groupValues[2].replace("\\\"", "\"").replace("\\n", "\n")
                val args = try {
                    json.parseToJsonElement(argsRaw).jsonObject
                        .mapValues { it.value.jsonPrimitive.contentOrNull ?: it.value.toString() }
                } catch (_: Exception) {
                    emptyMap()
                }
                out.add(ToolCallInvocation(name, args))
            }
            // 3. Anthropic 形态：{"type":"tool_use","name":"...","input":{...}}
            if (out.isEmpty()) {
                val tuRe = Regex(
                    """"type"\s*:\s*"tool_use"\s*,\s*"name"\s*:\s*"([^"]+)"\s*,\s*"input"\s*:\s*(\{.*?\})""",
                    setOf(RegexOption.DOT_MATCHES_ALL)
                )
                for (m in tuRe.findAll(text)) {
                    val name = m.groupValues[1].lowercase()
                    val args = try {
                        json.parseToJsonElement(m.groupValues[2]).jsonObject
                            .mapValues { it.value.jsonPrimitive.contentOrNull ?: it.value.toString() }
                    } catch (_: Exception) {
                        emptyMap()
                    }
                    out.add(ToolCallInvocation(name, args))
                }
            }
            // 4. 裸 JSON 形态（模型没按协议加 [[tool_call: 包裹）：{"name":"plan","args":{...}}
            //    仅接受"工具名在已知 MCP 清单内"的块，避免把普通 JSON 误判成工具调用
            if (out.isEmpty()) {
                val knownNames = McpToolStore.listMcpTools().map { it.name }.toSet()
                val bareRe = Regex(
                    """\{"name"\s*:\s*"([a-z_][a-z0-9_]*)",\s*"args"\s*:\s*(\{.*?\})\}""",
                    setOf(RegexOption.DOT_MATCHES_ALL)
                )
                for (m in bareRe.findAll(text)) {
                    val name = m.groupValues[1].lowercase()
                    if (name !in knownNames) continue
                    val args = try {
                        json.parseToJsonElement(sanitizeJsonString(m.groupValues[2])).jsonObject
                            .mapValues { it.value.jsonPrimitive.contentOrNull ?: it.value.toString() }
                    } catch (_: Exception) {
                        emptyMap()
                    }
                    out.add(ToolCallInvocation(name, args))
                }
            }
            // 5. 单括号变体：tool_call: {...}（无 [[ 前缀）
            if (out.isEmpty()) {
                val scRe = Regex(
                    """tool_call\s*:\s*(\{.*?\})""",
                    setOf(RegexOption.DOT_MATCHES_ALL)
                )
                for (m in scRe.findAll(text)) {
                    try {
                        parseToolCallJson(m.groupValues[1])?.let { out.add(it) }
                    } catch (_: Exception) {
                    }
                }
            }
        }
        return out.distinctBy { it.name }
    }

    /**
     * 任务13（重构）：修复被截断的工具调用标记并提取。
     * 模型输出 [[tool_call:{...} 但缺 ]] 收尾（常见于流式截断）时，
     * 直接从标记起点截取到文本末尾、掐掉尾部散文，交给 repairJson 补全解析。
     * 修复成功后照常执行工具 → 工具气泡一定出现，不再走"要求 AI 重说"分支。
     */
    fun extractUnclosedToolCalls(text: String): List<ToolCallInvocation> {
        val out = mutableListOf<ToolCallInvocation>()
        var searchFrom = 0
        while (true) {
            val start = text.indexOf("[[tool_call:", searchFrom)
            if (start < 0) break
            val contentStart = start + "[[tool_call:".length
            val content = text.substring(contentStart)
            // 找到最后一个 }（JSON 对象收尾），丢弃其后的散文；无 } 则整段交给修复器
            val lastBrace = content.lastIndexOf('}')
            val candidate = if (lastBrace >= 0) content.substring(0, lastBrace + 1) else content
            try {
                parseToolCallJson(candidate)?.let { out.add(it) }
            } catch (_: Exception) {
            }
            searchFrom = contentStart + 1
        }
        return out.distinctBy { it.name }
    }

    /**
     * 检测工具调用块是否缺 ]] 收尾。
     * 出现 [[tool_call 但没有任何完整块（或完整块之后还有残缺片段）→ 需要 AI 重说。
     */
    fun hasUnclosedToolMarker(text: String): Boolean {
        if (!text.contains("[[tool_call")) return false
        // 找到每个 [[tool_call 起点
        var searchFrom = 0
        var hasUnclosed = false
        while (true) {
            val idx = text.indexOf("[[tool_call", searchFrom)
            if (idx < 0) break
            // 从起点找最近的 ]]；找不到 = 未闭合
            val closeIdx = text.indexOf("]]", idx + "[[tool_call".length)
            if (closeIdx < 0) {
                hasUnclosed = true
                break
            }
            searchFrom = closeIdx + 2
        }
        return hasUnclosed
    }

    /** 解析单个工具调用 JSON 块（容错：真实换行转义 + 截断修复） */
    private fun parseToolCallJson(jsonStr: String): ToolCallInvocation? {
        val cleaned = sanitizeJsonString(jsonStr)
        val obj = try {
            json.parseToJsonElement(cleaned).jsonObject
        } catch (_: Exception) {
            // JSON 非法（截断/缺闭合）→ 修复后再试
            val repaired = repairJson(cleaned)
            try {
                json.parseToJsonElement(repaired).jsonObject
            } catch (_: Exception) {
                return null
            }
        }
        val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: return null
        val args = obj["args"]?.jsonObject
            ?.mapValues { it.value.jsonPrimitive.contentOrNull ?: it.value.toString() }
            ?: emptyMap()
        return ToolCallInvocation(name, args)
    }

    /** 把 JSON 字符串值里的真实换行/回车转义成 \n / \r（AI 常输出多行文本导致 JSON 非法） */
    private fun sanitizeJsonString(s: String): String {
        val sb = StringBuilder(s.length + 8)
        var inStr = false
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                c == '\\' && i + 1 < s.length -> {
                    sb.append(c).append(s[i + 1])
                    i += 2
                }
                c == '"' -> {
                    inStr = !inStr
                    sb.append(c)
                    i++
                }
                inStr && c == '\n' -> {
                    sb.append("\\n")
                    i++
                }
                inStr && c == '\r' -> {
                    sb.append("\\r")
                    i++
                }
                else -> {
                    sb.append(c)
                    i++
                }
            }
        }
        return sb.toString()
    }

    /** 修复被截断的 JSON（缺闭合括号）：逐级补全 } ] " 直到能解析 */
    private fun repairJson(frag: String): String {
        var candidate = frag
        // 先去掉尾部非 JSON 垃圾
        candidate = candidate.trimEnd().trimEnd(']', '}', ' ', '\n')
        // 逐级尝试补闭合
        repeat(8) {
            try {
                json.parseToJsonElement(candidate)
                return candidate
            } catch (_: Exception) {
            }
            // 从尾部开始补
            val trimmed = candidate.trimEnd()
            val last = trimmed.lastOrNull()
            candidate = when {
                last == '"' -> trimmed + "}" // 字符串后面直接补对象闭合
                last == '{' -> trimmed + "}"
                last == ',' -> trimmed.dropLast(1) + "}"
                last == ':' -> trimmed + "null}"
                else -> trimmed + "}"
            }
        }
        return candidate
    }

    /** 检测回复里是否出现了错误的 [[tool_call 文本标记（缺闭合） */
    fun hasMalformedToolMarker(text: String): Boolean {
        if (TOOL_CALL_RE.containsMatchIn(text)) return false // 完整标记不算错误
        if (!text.contains("[[tool_call") && !text.contains("[[tool_call:")) return false
        // 任务13（重构）：能被截断修复器成功修复的标记不算 malformed ——
        // 截断（缺 ]]）是流式输出的常态，修复后照常执行工具、气泡照常显示，
        // 不应再被判为 malformed 而触发无谓的"重说"重试
        if (extractUnclosedToolCalls(text).isNotEmpty()) return false
        return true
    }

    /** 从回复文本中移除工具调用标记（完整块 + 残留 malformed 片段，不显示给用户） */
    fun removeToolMarkers(text: String): String {
        var t = TOOL_CALL_RE.replace(text, "").trim()
        // 删除残留的 malformed 片段（从 [[tool_call 到文本末尾，通常在回复尾部）
        val idx = t.indexOf("[[tool_call")
        if (idx >= 0) t = t.substring(0, idx).trim()
        return t
    }

    /** 执行工具调用（AI 专用入口） */
    suspend fun executeToolCall(name: String, args: Map<String, String>): McpToolResult =
        McpToolStore.callMcpTool(name, args)
}

// ==================== 任务8-4：工具气泡 UI（纯黑气泡 + 白色齿轮 + AI 调用 tool + 完成 All Done ✓） ====================

@Composable
internal fun ToolCallBubble(record: ToolCallRecord, pixelFont: FontFamily) {
    val isRunning = record.status == ToolCallStatus.Running
    val statusColor = when (record.status) {
        ToolCallStatus.Done -> Color(0xFF3DDC84)
        ToolCallStatus.Error -> Color(0xFFFF6B6B)
        ToolCallStatus.Running -> Color(0xFF9E9E9E)
    }
    Row(
        modifier = Modifier
            // 任务16：tool 框与 AI 消息气泡等长（消息气泡固定 200.dp）
            .width(200.dp)
            // 任务14：边框固定为深灰色（与本应用消息气泡边框同色），
            // 状态只通过齿轮和文字（All Done ✓ / Failed ✗）传达
            .background(Color.Black, RoundedCornerShape(6.dp))
            .border(1.dp, Color(0xFF4A4A4F), RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SpinningGearIcon()
        Spacer(Modifier.width(7.dp))
        Text(
            text = "AI use " + record.toolName,
            color = Color(0xFFE6E6E6),
            fontSize = 10.sp,
            fontFamily = pixelFont
        )
        if (record.argsText.isNotBlank()) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = record.argsText,
                color = Color(0xFF9E9E9E),
                fontSize = 8.sp,
                fontFamily = pixelFont,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        if (!isRunning) {
            // 完成后：All Done 小字 + 勾；失败：Failed ✗
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (record.status == ToolCallStatus.Error) "Failed ✗" else "All Done ✓",
                color = statusColor,
                fontSize = 8.sp,
                fontFamily = pixelFont
            )
        }
    }
}

/** 白色设置图标（齿轮）：调用时转一圈后停住 */
@Composable
private fun SpinningGearIcon() {
    val angle = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        angle.animateTo(360f, tween(650, easing = LinearEasing))
    }
    Canvas(
        modifier = Modifier
            .size(13.dp)
            .graphicsLayer { rotationZ = angle.value }
    ) {
        val c = Color.White
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        // 8 个齿
        for (i in 0 until 8) {
            rotate(i * 45f, pivot = Offset(cx, cy)) {
                drawRect(c, topLeft = Offset(cx - w * 0.07f, 0f), size = Size(w * 0.14f, h * 0.30f))
            }
        }
        // 主体圆 + 中心孔
        drawCircle(c, radius = w * 0.32f, center = Offset(cx, cy))
        drawCircle(Color(0xFF111111), radius = w * 0.14f, center = Offset(cx, cy))
    }
}

// ==================== 任务19：灯泡开场（AI 直接调用工具时替代 AI Tool Call 字幕） ====================

/** 手绘风格灯泡（Canvas 绘制）：未亮时暗淡，亮起后发光 + 光线（任务20：缩小） */
@Composable
private fun HandDrawnBulb(lightAlpha: Float) {
    Canvas(modifier = Modifier.size(13.dp)) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h * 0.38f
        val r = w * 0.26f
        val stroke = 1.2.dp.toPx()
        // 任务20：暗态对比加强 —— 未亮时线条很暗（0.25），点亮后逐渐变亮
        val lineColor = Color(0xFFFFE082).copy(alpha = 0.25f + 0.75f * lightAlpha)
        val glowColor = Color(0xFFFFF59D)

        // 亮起后的光晕：随亮度逐渐扩大，点亮过程更明显
        if (lightAlpha > 0.05f) {
            drawCircle(
                color = glowColor.copy(alpha = 0.30f * lightAlpha),
                radius = r * (1.4f + 0.8f * lightAlpha),
                center = Offset(cx, cy)
            )
        }
        // 光线
        if (lightAlpha > 0.25f) {
            val ray = lightAlpha * 0.9f
            drawLine(glowColor.copy(alpha = ray), Offset(cx, cy - r * 1.75f), Offset(cx, cy - r * 1.25f), stroke)
            drawLine(glowColor.copy(alpha = ray), Offset(cx - r * 1.5f, cy - r * 0.8f), Offset(cx - r * 1.05f, cy - r * 0.55f), stroke)
            drawLine(glowColor.copy(alpha = ray), Offset(cx + r * 1.5f, cy - r * 0.8f), Offset(cx + r * 1.05f, cy - r * 0.55f), stroke)
            drawLine(glowColor.copy(alpha = ray), Offset(cx - r * 1.65f, cy + r * 0.15f), Offset(cx - r * 1.25f, cy + r * 0.15f), stroke)
            drawLine(glowColor.copy(alpha = ray), Offset(cx + r * 1.65f, cy + r * 0.15f), Offset(cx + r * 1.25f, cy + r * 0.15f), stroke)
        }
        // 手绘灯泡轮廓：两段错开的圆弧制造手绘感
        drawArc(
            color = lineColor,
            startAngle = 205f, sweepAngle = 130f, useCenter = false,
            topLeft = Offset(cx - r, cy - r), size = Size(r * 2f, r * 2f),
            style = Stroke(stroke)
        )
        drawArc(
            color = lineColor,
            startAngle = -25f, sweepAngle = 125f, useCenter = false,
            topLeft = Offset(cx - r * 0.95f, cy - r * 0.97f), size = Size(r * 1.9f, r * 1.92f),
            style = Stroke(stroke)
        )
        // 灯丝
        drawLine(lineColor, Offset(cx - r * 0.5f, cy + r * 0.3f), Offset(cx, cy + r * 0.55f), stroke)
        drawLine(lineColor, Offset(cx, cy + r * 0.55f), Offset(cx + r * 0.5f, cy + r * 0.3f), stroke)
        // 底座
        drawLine(lineColor, Offset(cx - r * 0.6f, cy + r * 0.68f), Offset(cx + r * 0.6f, cy + r * 0.68f), stroke)
        drawLine(lineColor, Offset(cx - r * 0.4f, cy + r * 0.86f), Offset(cx + r * 0.4f, cy + r * 0.86f), stroke)
    }
}

/** 任务20（重写）：暗灯泡滑入 → 明显停留 → 亮起（带点亮闪烁）→ 停留 →
 *  时间从灯泡后面向右划出 → 停留 → 淡出。
 *  动画播放期间保持挂载；全部播完才回调 onDone。 */
@Composable
private fun BulbIntro(timeText: String, pixelFont: FontFamily, onDone: () -> Unit) {
    val density = LocalDensity.current
    val slideIn = remember { Animatable(-1f) }
    val lightAlpha = remember { Animatable(0f) }
    // 初始 -1f = 完全藏在灯泡后面（左移 17dp），再向右划出
    val timeSlide = remember { Animatable(-1f) }
    val timeAlpha = remember { Animatable(0f) }
    val rowAlpha = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        // 1. 未亮的暗灯泡从屏幕左边缓缓滑入
        slideIn.animateTo(0f, tween(600, easing = LinearEasing))
        // 2. 暗态明显停留 —— 让用户看清"不亮的灯泡"
        delay(500)
        // 3. 点亮动画：渐亮 → 轻微闪烁 → 全亮（像真实灯泡点亮）
        lightAlpha.animateTo(1f, tween(500))
        lightAlpha.animateTo(0.55f, tween(160))
        lightAlpha.animateTo(1f, tween(260))
        // 4. 亮着停留一会儿
        delay(400)
        // 5. 时间从灯泡后面向右划出
        timeAlpha.animateTo(1f, tween(150))
        timeSlide.animateTo(0f, tween(900, easing = LinearEasing))
        // 6. 停留展示
        delay(1500)
        // 7. 整体淡出，最后才标记"已播放"
        rowAlpha.animateTo(0f, tween(500))
        onDone()
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.graphicsLayer { alpha = rowAlpha.value }
    ) {
        Box(
            modifier = Modifier
                // 灯泡置于时间文字上层，时间从它"后面"划出
                .zIndex(1f)
                .graphicsLayer {
                    translationX = slideIn.value * with(density) { 16.dp.toPx() }
                    alpha = 1f + slideIn.value
                }
        ) {
            HandDrawnBulb(lightAlpha.value)
        }
        Spacer(Modifier.width(4.dp))
        Box(
            modifier = Modifier.graphicsLayer {
                translationX = timeSlide.value * with(density) { 17.dp.toPx() }
                alpha = timeAlpha.value
            }
        ) {
            Text(
                text = timeText,
                color = Color(0xFF9E9E9E),
                fontSize = 8.sp,
                fontFamily = pixelFont
            )
        }
    }
}

// ==================== 任务8-5：plan 气泡 UI（纯黑方块 + 加载 + 横线画出） ====================

@Composable
internal fun PlanBubble(record: PlanRecord, pixelFont: FontFamily) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black, RoundedCornerShape(6.dp))
            .border(1.dp, Color(0xFF78909C), RoundedCornerShape(6.dp))
            .padding(12.dp)
    ) {
        if (record.isLoading) {
            PlanLoadingIndicator(pixelFont)
        } else if (record.steps.isEmpty()) {
            // AI 没返回步骤时兜底显示，避免永远转加载动画
            Text(
                text = "plan: no steps",
                color = Color(0xFF9E9E9E),
                fontSize = 9.sp,
                fontFamily = pixelFont
            )
        } else {
            // 任务16（重构）：plan 新设计 —— 标题居中，任务分行放在横线上
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "PLAN",
                    color = Color(0xFF4FC3F7),
                    fontSize = 9.sp,
                    fontFamily = pixelFont,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(10.dp))
                record.steps.forEachIndexed { i, step ->
                    // 任务20：恢复编号 —— 提取时剥掉的编号在此补回（原始已带编号则不重复加）
                    val numberedStep = if (Regex("""^\s*[（(]?\d+[）)]?\s*[.、)）:：．]\s*""").containsMatchIn(step)) {
                        step
                    } else {
                        "${i + 1}. $step"
                    }
                    // 由快到慢：后面的线开始更晚、画得更慢
                    PlanLine(
                        step = numberedStep,
                        pixelFont = pixelFont,
                        startDelayMs = 350L + i * 380L,
                        drawDurationMs = 220L + i * 260L
                    )
                    Spacer(Modifier.height(9.dp))
                }
            }
        }
    }
}

/** 纯黑方块中间的加载动画（任务15：圆圈加载动画 + planning 闪烁） */
@Composable
private fun PlanLoadingIndicator(pixelFont: FontFamily) {
    val transition = rememberInfiniteTransition(label = "planLoad")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(950, easing = LinearEasing)),
        label = "planAngle"
    )
    val blink by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(420, easing = LinearEasing), RepeatMode.Reverse),
        label = "planBlink"
    )
    // 任务20：圆圈加载回到中央 —— 圆圈在上居中，planning 文字在下居中（天蓝色调）
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Canvas(modifier = Modifier.size(24.dp)) {
            // 任务15：圆圈加载动画 —— 淡色底环 + 旋转圆弧段
            val stroke = 2.5.dp.toPx()
            val radius = (size.minDimension - stroke) / 2f
            val center = Offset(size.width / 2f, size.height / 2f)
            drawCircle(
                color = Color(0xFF4FC3F7).copy(alpha = 0.22f),
                radius = radius,
                center = center,
                style = Stroke(stroke)
            )
            rotate(angle, pivot = center) {
                drawArc(
                    color = Color(0xFF4FC3F7),
                    startAngle = 0f,
                    sweepAngle = 265f,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2f, radius * 2f),
                    style = Stroke(stroke, cap = StrokeCap.Round)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "planning...",
            color = Color(0xFF4FC3F7).copy(alpha = blink),
            fontSize = 9.sp,
            fontFamily = pixelFont
        )
    }
}

/** 单条 plan 任务行（任务16 新设计）：文字居中 + 横线在文字下方从左画出 */
@Composable
private fun PlanLine(
    step: String,
    pixelFont: FontFamily,
    startDelayMs: Long,
    drawDurationMs: Long
) {
    val progress = remember { Animatable(0f) }
    val textAlpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(startDelayMs)
        progress.animateTo(1f, tween(drawDurationMs.toInt(), easing = LinearEasing))
        textAlpha.animateTo(1f, tween(180))
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().graphicsLayer { alpha = textAlpha.value },
            // 任务20：任务文字置于左边（编号 + 任务，左对齐）
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = step,
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 9.sp,
                fontFamily = pixelFont,
                textAlign = TextAlign.Start,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(3.dp))
        Canvas(modifier = Modifier.fillMaxWidth().height(4.dp)) {
            drawLine(
                color = Color(0xFF78909C),
                start = Offset(0f, size.height / 2f),
                end = Offset(size.width * progress.value, size.height / 2f),
                strokeWidth = 2.dp.toPx()
            )
        }
    }
}

// ==================== 任务8-6：工具/plan 气泡列表（叠加在消息流底部） ====================

@Composable
internal fun ToolCallFeed(
    toolCalls: List<ToolCallRecord>,
    planRecord: PlanRecord?,
    pixelFont: FontFamily,
    aiTextEmpty: Boolean = false,
    modifier: Modifier = Modifier,
    introTimeText: String = ""
) {
    if (toolCalls.isEmpty() && planRecord == null) return
    Column(modifier = modifier) {
        // 任务20（修复）：灯泡开场只在"第一次调用 plan 工具"时出现（与回复文本是否为空无关）。
        // 动画播放期间保持挂载（不再用 SideEffect 立即置标志——那会让灯泡只活一帧就被卸载），
        // 全部播完后由 BulbIntro 的 onDone 回调标记已播放
        if (planRecord != null && !PlanStore.bulbIntroShown) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.CenterStart
            ) {
                BulbIntro(
                    timeText = introTimeText,
                    pixelFont = pixelFont,
                    onDone = { PlanStore.bulbIntroShown = true }
                )
            }
            // 任务20：高度占用再收紧
            Spacer(Modifier.height(2.dp))
        }
        planRecord?.let {
            PlanBubble(it, pixelFont)
            Spacer(Modifier.height(6.dp))
        }
        // 任务18（重构）：渲染全部工具调用记录 —— 一次工具调用一个气泡
        // （修复"一个回复里只能有一个气泡"：原来只渲染 lastOrNull 最后一条）
        toolCalls.forEach { tc ->
            ToolCallBubble(tc, pixelFont)
            Spacer(Modifier.height(5.dp))
        }
    }
}
