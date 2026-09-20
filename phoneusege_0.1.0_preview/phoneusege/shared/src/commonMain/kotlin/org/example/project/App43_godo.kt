package org.example.project

/**
 * 任务6（修订）：Godo —— 用户自定义 tool 的接口。
 *
 * 用户在 cmd 输入：
 *   godo：
 *   【do】：tool 名字
 *   【hell】：tool 定义（描述）
 *   【theusenee】：让这个 tool 用起来的逻辑代码（GodoScript）
 *
 * 系统自动解析三个必要字段 → 注册为 AI 可用的 MCP tool。
 * 之后 AI（和 godo 脚本）都可以调用这个用户自制的 tool。
 */

// ==================== 用户自定义 tool ====================

data class GodoToolDefinition(
    val name: String,
    val description: String,
    val code: String
)

/** 内置命令名：用户自定义 tool 不允许重名覆盖 */
val BUILTIN_COMMAND_NAMES: Set<String> = setOf(
    "adcodesee", "sonowinter", "reafile", "killinter", "sick",
    "tcopsay", "anyenone", "copycode", "godo",
    "c", "b", "s", "ba", "c0", "k", "r", "o", "p", "peko", "serg"
)

object UserToolStore {
    private val tools = mutableMapOf<String, GodoToolDefinition>()

    fun get(name: String): GodoToolDefinition? = tools[name.lowercase()]

    fun list(): List<GodoToolDefinition> = tools.values.toList()

    /**
     * 注册用户自定义 tool 并同步导入 McpToolStore（AI 立即可见可调）。
     * @return null = 成功；非 null = 失败原因
     */
    fun register(def: GodoToolDefinition): String? {
        val name = def.name.trim().lowercase()
        if (name.isEmpty()) return "tool name is empty"
        if (name in BUILTIN_COMMAND_NAMES) return "name '$name' conflicts with a built-in command"
        if (def.description.isBlank()) return "tool description is empty"
        if (def.code.isBlank()) return "tool logic code is empty"

        return registerMcpTool(
            name = name,
            description = def.description + " (user-defined godo tool)",
            parameters = listOf(
                    McpToolParameter(
                        name = "input",
                        description = "Optional free-form input; every named argument is also injected as a variable, e.g. \$conversationId",
                        required = false
                    )
            )
        ) { args ->
            try {
                // AI 传参全部注入为 GodoScript 变量（$参数名），特殊 $input 为自由文本
                val vars = args.mapKeys { it.key.lowercase() } + mapOf("input" to (args["input"] ?: ""))
                val lines = GodoEngine.execute(
                    def.code,
                    CommandCaller.Ai,
                    initialVars = vars,
                    depth = McpToolStore.currentDepth()
                )
                McpToolResult(isSuccess = true, output = lines.joinToString("\n"))
            } catch (t: Throwable) {
                McpToolResult(isSuccess = false, output = "", error = t.message ?: "godo tool '$name' failed")
            }
        }
    }

    /**
     * 任务7：直接注册一个 MCP tool（godo 格式 / HTTP 转发 / 外部导入共用底层）。
     * @return null = 成功；非 null = 失败原因
     */
    fun registerMcpTool(
        name: String,
        description: String,
        parameters: List<McpToolParameter> = emptyList(),
        handler: suspend (Map<String, String>) -> McpToolResult
    ): String? {
        val key = name.trim().lowercase()
        if (key.isEmpty()) return "tool name is empty"
        if (key in BUILTIN_COMMAND_NAMES) return "name '$key' conflicts with a built-in command"
        if (description.isBlank()) return "tool description is empty"

        tools[key] = GodoToolDefinition(key, description, "(external)")
        McpToolStore.register(McpToolDefinition(key, description, parameters)) { args -> handler(args) }
        return null
    }

    /** 注销用户自定义 tool（同时从 McpToolStore 移除，AI 不可再调） */
    fun unregister(name: String): Boolean {
        val key = name.lowercase()
        val removed = tools.remove(key) != null
        if (removed) McpToolStore.unregister(key)
        return removed
    }
}

// ==================== godo 输入解析 ====================

object GodoToolParser {

    class ParseException(message: String) : Exception(message)

    /** 解析 godo 输入，提取【do】【hell】【theusenee】三个必要字段 */
    fun parse(input: String): GodoToolDefinition {
        val text = input.trim()

        // 依次定位三个标记（允许 【do】： / 【do】: / 【do】 空格 等写法）
        val doIdx = text.indexOf("【do】")
        val hellIdx = text.indexOf("【hell】")
        val theuseneeIdx = text.indexOf("【theusenee】")

        if (doIdx < 0) throw ParseException("missing 【do】: tool name")
        if (hellIdx < 0) throw ParseException("missing 【hell】: tool description")
        if (theuseneeIdx < 0) throw ParseException("missing 【theusenee】: tool logic code")

        val name = text.substring(doIdx + "【do】".length, hellIdx)
            .trimStart('：', ':', ' ', '\n', '\r', '\t').trim()
        val description = text.substring(hellIdx + "【hell】".length, theuseneeIdx)
            .trimStart('：', ':', ' ', '\n', '\r', '\t').trim()
        val code = text.substring(theuseneeIdx + "【theusenee】".length)
            .trimStart('：', ':', ' ', '\n', '\r', '\t').trim()

        if (name.isEmpty()) throw ParseException("【do】 is empty: tool needs a name")
        if (description.isEmpty()) throw ParseException("【hell】 is empty: tool needs a description")
        if (code.isEmpty()) throw ParseException("【theusenee】 is empty: tool needs logic code")
        return GodoToolDefinition(name, description, code)
    }
}

// ==================== GodoScript 引擎 ====================

/**
 * GodoScript：theusenee 逻辑代码的微型脚本语言。
 *   # 注释
 *   say 文本                                  → 输出一行
 *   tcopsay                                   → 直接执行命令（内置命令 + 已注册的用户 tool）
 *   $var = tcopsay                            → 执行命令，输出（多行 \n 连接）存入变量
 *   anyenone $var                             → 命令参数中的 $var 被替换
 *   if $var contains "ok":                    → 条件：== / != / contains / startswith /
 *   ...                                       →       endswith / empty / notempty，
 *   end                                       →       and / or 组合
 *   for $line in tcopsay:                     → 按输出行迭代
 *   ...                                       →
 *   end                                       →
 * AI 调用自定义 tool 时传入的命名参数以 $参数名 注入；$input 为自由文本参数。
 */
object GodoEngine {

    class GodoException(message: String) : Exception(message)

    /** godo / 用户自定义 tool 最大递归深度（防无限递归） */
    const val MAX_DEPTH = 8

    // ==================== AST ====================

    sealed class Node {
        data class Cmd(val line: String) : Node()
        data class Assign(val varName: String, val cmdLine: String) : Node()
        data class Say(val text: String) : Node()
        data class IfNode(val condition: String, val body: List<Node>) : Node()
        data class ForNode(val varName: String, val cmdLine: String, val body: List<Node>) : Node()
    }

    private val VAR_NAME_RE = Regex("""\$([A-Za-z_][A-Za-z0-9_]*)""")

    // ==================== 解析 ====================

    fun parse(script: String): List<Node> {
        val root = mutableListOf<Node>()
        val stack = mutableListOf<MutableList<Node>>()
        stack.add(root)

        script.lines().forEachIndexed { idx, raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEachIndexed
            val lineno = idx + 1

            when {
                line.startsWith("if ") -> {
                    val cond = line.removePrefix("if ").removeSuffix(":").trim()
                    if (cond.isEmpty()) throw GodoException("line $lineno: empty if condition")
                    val body = mutableListOf<Node>()
                    stack.last().add(Node.IfNode(cond, body))
                    stack.add(body)
                }
                line.startsWith("for ") -> {
                    val inner = line.removePrefix("for ").removeSuffix(":").trim()
                    val m = Regex("""\$([A-Za-z_][A-Za-z0-9_]*)\s+in\s+(.+)""").matchEntire(inner)
                        ?: throw GodoException("line $lineno: bad for syntax, expected: for \$var in <command>")
                    val body = mutableListOf<Node>()
                    stack.last().add(Node.ForNode(m.groupValues[1], m.groupValues[2].trim(), body))
                    stack.add(body)
                }
                line == "end" -> {
                    if (stack.size <= 1) throw GodoException("line $lineno: unexpected end")
                    stack.removeAt(stack.size - 1)
                }
                else -> {
                    val assignMatch = Regex("""\$([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.+)""").matchEntire(line)
                    when {
                        assignMatch != null ->
                            stack.last().add(Node.Assign(assignMatch.groupValues[1], assignMatch.groupValues[2].trim()))
                        line.startsWith("say ") ->
                            stack.last().add(Node.Say(line.removePrefix("say ").trim()))
                        else ->
                            stack.last().add(Node.Cmd(line))
                    }
                }
            }
        }

        if (stack.size != 1) throw GodoException("missing end for if/for block")
        return root
    }

    // ==================== 执行 ====================

    /**
     * 执行一段 GodoScript。
     * @param initialVars 预置变量（AI 调用自定义 tool 时的命名参数等）
     * @param depth 递归深度（自定义 tool 互相调用时防无限递归）
     */
    suspend fun execute(
        script: String,
        caller: CommandCaller,
        initialVars: Map<String, String> = emptyMap(),
        depth: Int = 0,
        onLine: (String) -> Unit = {}
    ): List<String> {
        if (depth > MAX_DEPTH) throw GodoException("tool recursion too deep (max $MAX_DEPTH)")
        val nodes = parse(script)
        val out = mutableListOf<String>()
        val vars = initialVars.toMutableMap()

        fun emit(line: String) {
            out.add(line)
            onLine(line)
        }

        suspend fun evalNodes(nodes: List<Node>) {
            for (n in nodes) {
                when (n) {
                    is Node.Cmd ->
                        CmdStore.executeForResult(interpolate(n.line, vars), caller, depth).forEach { emit(it) }
                    is Node.Assign ->
                        vars[n.varName] = CmdStore.executeForResult(interpolate(n.cmdLine, vars), caller, depth)
                            .joinToString("\n")
                    is Node.Say ->
                        emit(interpolate(n.text, vars))
                    is Node.IfNode ->
                        if (evalCondition(n.condition, vars)) evalNodes(n.body)
                    is Node.ForNode -> {
                        val items = CmdStore.executeForResult(interpolate(n.cmdLine, vars), caller, depth)
                        for (item in items) {
                            vars[n.varName] = item
                            evalNodes(n.body)
                        }
                    }
                }
            }
        }

        evalNodes(nodes)
        return out
    }

    // ==================== 求值 ====================

    private fun interpolate(text: String, vars: Map<String, String>): String {
        if (!text.contains('$')) return text
        return VAR_NAME_RE.replace(text) { m ->
            vars[m.groupValues[1]] ?: m.value
        }
    }

    private fun evalCondition(cond: String, vars: Map<String, String>): Boolean {
        // or 优先级低于 and
        return cond.split(Regex("""\s+or\s+""")).any { orPart ->
            orPart.split(Regex("""\s+and\s+""")).all { term ->
                evalTerm(term.trim(), vars)
            }
        }
    }

    private fun evalTerm(term: String, vars: Map<String, String>): Boolean {
        if (term.isEmpty()) throw GodoException("empty condition term")

        // $var op value
        val bin = Regex("""^\$([A-Za-z_][A-Za-z0-9_]*)\s+(\S+)\s+(.+)$""").matchEntire(term)
        if (bin != null) {
            val v = vars[bin.groupValues[1]] ?: ""
            val op = bin.groupValues[2]
            val operand = literalOf(bin.groupValues[3], vars)
            return when (op) {
                "==" -> v == operand
                "!=" -> v != operand
                "contains" -> v.contains(operand)
                "startswith" -> v.startsWith(operand)
                "endswith" -> v.endsWith(operand)
                else -> throw GodoException("unknown operator: $op")
            }
        }

        // $var empty / $var notempty
        val unary = Regex("""^\$([A-Za-z_][A-Za-z0-9_]*)\s+(empty|notempty)$""").matchEntire(term)
        if (unary != null) {
            val v = vars[unary.groupValues[1]] ?: ""
            return if (unary.groupValues[2] == "empty") v.isEmpty() else v.isNotEmpty()
        }

        // 字面量比较："a" == "b"
        val lit = Regex("""^(.+?)\s*(==|!=)\s*(.+)$""").matchEntire(term)
        if (lit != null) {
            val a = literalOf(lit.groupValues[1], vars)
            val b = literalOf(lit.groupValues[3], vars)
            return if (lit.groupValues[2] == "==") a == b else a != b
        }

        throw GodoException("bad condition: $term")
    }

    /** 字面量求值：带引号字符串 / $变量 / 裸文本 */
    private fun literalOf(raw: String, vars: Map<String, String>): String {
        val r = raw.trim()
        if (r.length >= 2 && r.startsWith('"') && r.endsWith('"')) {
            return r.substring(1, r.length - 1)
        }
        if (r.startsWith('$')) {
            val name = r.substring(1)
            return vars[name] ?: ""
        }
        return r
    }
}
