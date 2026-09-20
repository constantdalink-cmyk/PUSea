package org.example.project

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** commonMain 没有网络 API，下载能力由平台侧注入。 */
interface InterpreterDownloader {
    suspend fun downloadText(url: String): String
}

/** 任务9：浏览器搜索由平台侧注入（commonMain 无浏览器 API）。 */
interface BrowserOpener {
    suspend fun openSearch(query: String)
}

/**
 * 命令调用者身份（任务5：AI 与用户通用全部命令）。
 *  - User：用户在 cmd 框手动输入；
 *  - Ai：AI 通过 MCP 工具/命令通道调用。
 * 所有命令对两者完全开放，不再做权限隔离。
 */
enum class CommandCaller { User, Ai }

object CmdStore {
    init {
        // 任务5：AI 可用用户的所有命令 —— 全部 8 个命令都注册为 MCP 工具。
        // 同步命令（tcopsay/anyenone/sonowinter/reafile/killinter）走通用捕获注册；
        // 异步命令（adcodesee/sick/copycode）走专用 suspend 处理器（等待真实完成结果）。
        registerSyncMcpTool(
            "tcopsay",
            "List all conversation archives with their IDs; the current conversation is marked with *."
        )
        registerSyncMcpTool(
            "anyenone",
            "Summarize the content of a conversation by ID (title, message count by role, total chars, recent message previews).",
            listOf(McpToolParameter(name = "conversationId", description = "The conversation ID to summarize"))
        )
        registerSyncMcpTool(
            "sonowinter",
            "List current interpreters (id / name / supported extensions / bind count)."
        )
        registerSyncMcpTool(
            "reafile",
            "List all file paths in the current session's bound code library (with root prefix)."
        )
        registerSyncMcpTool(
            "killinter",
            "Remove an interpreter by name or id.",
            listOf(McpToolParameter(name = "target", description = "Interpreter name or id"))
        )
        McpToolStore.register(
            McpToolDefinition(
                name = "adcodesee",
                description = "Download an interpreter from a URL and bind it to the currently selected file.",
                parameters = listOf(McpToolParameter(name = "url", description = "Interpreter script URL"))
            )
        ) { args -> mcpAdcodesee(args) }
        McpToolStore.register(
            McpToolDefinition(
                name = "sick",
                description = "Run a one-shot syntax error check on a file. Omitting path checks the currently selected file.",
                parameters = listOf(
                    McpToolParameter(name = "path", description = "File path (optional, defaults to selected file)", required = false)
                )
            )
        ) { args -> mcpSick(args) }
        McpToolStore.register(
            McpToolDefinition(
                name = "copycode",
                description = "Copy the code library bound to another conversation into the current session.",
                parameters = listOf(McpToolParameter(name = "conversationId", description = "Source conversation ID"))
            )
        ) { args -> mcpCopycode(args) }
        // 任务6（修订）：godo 是"用户自定义 tool"接口 —— 解析【do】【hell】【theusenee】
        // 后自动注册为 AI 的 MCP tool，由 UserToolStore 负责注册与导入，这里不再注册内置 godo 工具。

        // ==================== 任务9：快捷键命令注册为 MCP 工具（AI 可用） ====================
        McpToolStore.register(
            McpToolDefinition(
                name = "w",
                description = "Create a file in the current workspace ('w <name>'), or a folder ('w folder <name>').",
                parameters = listOf(
                    McpToolParameter(name = "name", description = "File name, or 'folder <name>'", required = false)
                )
            )
        ) { args -> McpToolResult(isSuccess = true, output = createLines(args["name"]?.trim()).joinToString("\n")) }
        McpToolStore.register(
            McpToolDefinition(
                name = "b",
                description = "Replace old code with new code in the currently selected file.",
                parameters = listOf(
                    McpToolParameter(name = "old", description = "Original code snippet", required = true),
                    McpToolParameter(name = "new", description = "Replacement code snippet", required = true)
                )
            )
        ) { args ->
            McpToolResult(
                isSuccess = true,
                output = replaceLines("${args["old"]?.trim().orEmpty()} / ${args["new"]?.trim().orEmpty()}")
                    .joinToString("\n")
            )
        }
        McpToolStore.register(
            McpToolDefinition(
                name = "s",
                description = "Open the system browser to search for a query.",
                parameters = listOf(McpToolParameter(name = "query", description = "Search query", required = true))
            )
        ) { args -> McpToolResult(isSuccess = true, output = browserSearchLines(args["query"]?.trim()).joinToString("\n")) }
        McpToolStore.register(
            McpToolDefinition(
                name = "ba",
                description = "Restore the currently selected file (or a target file) to its previous code state.",
                parameters = listOf(McpToolParameter(name = "target", description = "File name or path (optional)", required = false))
            )
        ) { args -> McpToolResult(isSuccess = true, output = restorePrevLines(args["target"]?.trim()).joinToString("\n")) }
        McpToolStore.register(
            McpToolDefinition(
                name = "c",
                description = "Restore the file to its initial state (the first state in the current context).",
                parameters = listOf(McpToolParameter(name = "target", description = "File name or path (optional)", required = false))
            )
        ) { args -> McpToolResult(isSuccess = true, output = restoreInitialLines(args["target"]?.trim()).joinToString("\n")) }
        McpToolStore.register(
            McpToolDefinition(
                name = "k",
                description = "Delete the currently selected file (or a target file/folder).",
                parameters = listOf(McpToolParameter(name = "target", description = "File name or path (optional)", required = false))
            )
        ) { args -> McpToolResult(isSuccess = true, output = deleteLines(args["target"]?.trim()).joinToString("\n")) }
        McpToolStore.register(
            McpToolDefinition(
                name = "r",
                description = "Test-run the currently selected file (or a target file).",
                parameters = listOf(McpToolParameter(name = "target", description = "File name or path (optional)", required = false))
            )
        ) { args -> McpToolResult(isSuccess = true, output = runFileLines(args["target"]?.trim()).joinToString("\n")) }
        McpToolStore.register(
            McpToolDefinition(
                name = "o",
                description = "Mark the current file state as the clean baseline (first state).",
                parameters = listOf(McpToolParameter(name = "target", description = "File name or path (optional)", required = false))
            )
        ) { args -> McpToolResult(isSuccess = true, output = markCleanLines(args["target"]?.trim()).joinToString("\n")) }
        McpToolStore.register(
            McpToolDefinition(
                name = "p",
                description = "Clear (delete) the content of the currently selected file.",
                parameters = listOf(McpToolParameter(name = "target", description = "File name or path (optional)", required = false))
            )
        ) { args -> McpToolResult(isSuccess = true, output = clearContentLines(args["target"]?.trim()).joinToString("\n")) }
        McpToolStore.register(
            McpToolDefinition(
                name = "peko",
                description = "Back up a file by creating a copy named '<name>_backup'.",
                parameters = listOf(McpToolParameter(name = "target", description = "File name or path", required = true))
            )
        ) { args -> McpToolResult(isSuccess = true, output = backupLines(args["target"]?.trim()).joinToString("\n")) }
        McpToolStore.register(
            McpToolDefinition(
                name = "serg",
                description = "Replace the target file's code content with the source file's content: <src> / <dst>.",
                parameters = listOf(
                    McpToolParameter(name = "src", description = "Source file name", required = true),
                    McpToolParameter(name = "dst", description = "Target file name", required = true)
                )
            )
        ) { args ->
            McpToolResult(
                isSuccess = true,
                output = sergLines("${args["src"]?.trim().orEmpty()} / ${args["dst"]?.trim().orEmpty()}")
                    .joinToString("\n")
            )
        }
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var downloader: InterpreterDownloader? = null
    /** 任务7：外部 HTTP 请求能力（导入的 HTTP 转发 MCP tool 执行用） */
    private var httpRequester: HttpRequester? = null
    /** 任务9：浏览器搜索（s 命令用） */
    private var browserOpener: BrowserOpener? = null
    /** MCP 工具捕获模式：非 null 时 output() 同时收集行，供工具调用结果返回 */
    private var captureSink: MutableList<String>? = null

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    private val _lastMessage = MutableStateFlow<String?>(null)
    val lastMessage: StateFlow<String?> = _lastMessage

    /** cmd 输出日志：每次 output() 追加一行，显示在 cmd 框下方 */
    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs

    /** 日志最多保留的行数 */
    private const val MAX_CMD_LOG_LINES = 200

    fun initialize(
        downloader: InterpreterDownloader,
        httpRequester: HttpRequester? = null,
        browserOpener: BrowserOpener? = null
    ) {
        this.downloader = downloader
        this.httpRequester = httpRequester
        this.browserOpener = browserOpener
    }

    fun execute(rawInput: String, caller: CommandCaller = CommandCaller.User) {
        val input = rawInput.trim()
        if (input.isEmpty() || _busy.value) return

        val parts = input.split(Regex("\\s+"), limit = 2)
        when (parts[0].lowercase()) {
            "adcodesee" -> {
                val url = parts.getOrNull(1)?.trim()
                if (url.isNullOrEmpty()) {
                    output("usage: adcodesee <interpreter-url>")
                    return
                }
                runAdcodesee(url)
            }
            "sonowinter" -> runSonowinter()
            "reafile" -> runReafile()
            "killinter" -> {
                val target = parts.getOrNull(1)?.trim()
                if (target.isNullOrEmpty()) {
                    output("usage: killinter <interpreter-name-or-id>")
                    return
                }
                runKillinter(target)
            }
            "sick" -> {
                // sick [文件路径]：一次性吐全部语法错误；无参数 = 当前选中文件
                runSick(parts.getOrNull(1)?.trim())
            }
            "tcopsay" -> runTcopsay()
            "anyenone" -> {
                // 任务5：所有命令 AI 与用户通用，不再限制调用者
                val target = parts.getOrNull(1)?.trim()
                if (target.isNullOrEmpty()) {
                    output("usage: anyenone <conversation-id>")
                    return
                }
                runAnyenone(target)
            }
            "copycode" -> {
                // 任务5：所有命令 AI 与用户通用，不再限制调用者
                val target = parts.getOrNull(1)?.trim()
                if (target.isNullOrEmpty()) {
                    output("usage: copycode <conversation-id>")
                    return
                }
                runCopycode(target)
            }
            "godo" -> {
                // 任务6/7：godo 接口 —— 手写定义（【do】...）或从 URL / 工作区文件导入 tool
                val raw = parts.getOrNull(1)?.trim().orEmpty()
                if (raw.isEmpty()) {
                    output("usage:")
                    output("  godo：\n    【do】：名字\n    【hell】：定义\n    【theusenee】：逻辑代码")
                    output("  godo <url>    → 从网址导入 MCP tool（godo 格式或 JSON 规格）")
                    output("  godo <file>   → 从工作区文件导入（同上）")
                    return
                }
                when {
                    raw.startsWith("http://") || raw.startsWith("https://") -> runGodoImport(raw)
                    raw.contains("【do】") -> runGodoDefinition(raw)
                    else -> runGodoImport(raw)
                }
            }
            // ==================== 任务9：工作区快捷键命令 ====================
            "w" -> createLines(parts.getOrNull(1)?.trim()).forEach { output(it) }
            "b" -> replaceLines(parts.getOrNull(1)?.trim()).forEach { output(it) }
            "s" -> runBrowserSearch(parts.getOrNull(1)?.trim())
            "ba" -> restorePrevLines(parts.getOrNull(1)?.trim()).forEach { output(it) }
            "c" -> restoreInitialLines(parts.getOrNull(1)?.trim()).forEach { output(it) }
            "k" -> deleteLines(parts.getOrNull(1)?.trim()).forEach { output(it) }
            "r" -> runFileLines(parts.getOrNull(1)?.trim()).forEach { output(it) }
            "o" -> markCleanLines(parts.getOrNull(1)?.trim()).forEach { output(it) }
            "p" -> clearContentLines(parts.getOrNull(1)?.trim()).forEach { output(it) }
            "peko" -> backupLines(parts.getOrNull(1)?.trim()).forEach { output(it) }
            "serg" -> sergLines(parts.getOrNull(1)?.trim()).forEach { output(it) }
            else -> output("unknown command: ${parts[0]}")
        }
    }

    /**
     * 任务4：AI 侧的命令执行入口（与用户入口共用同一权限模型）。
     * AI 主要走 McpToolStore.callMcpTool()；此方法供 AI 以命令字符串形式调用时使用。
     */
    fun executeAsAi(rawInput: String) = execute(rawInput, CommandCaller.Ai)

    /**
     * 任务6：统一的"命令 → 输出行"执行入口（godo 引擎 / 未来 skill 引擎的底层）。
     * 同步命令直接返回输出行；异步命令（adcodesee/sick/copycode）suspend 等待真实完成结果。
     * 不写 cmd 日志（由调用方决定如何展示），不做 busy 检查（由调用方控制并发）。
     * @param depth godo 递归深度（用户自定义 tool 互相调用时防无限递归）
     */
    suspend fun executeForResult(
        rawInput: String,
        caller: CommandCaller,
        depth: Int = 0
    ): List<String> {
        if (depth > GodoEngine.MAX_DEPTH) return listOf("fail: tool recursion too deep")
        val input = rawInput.trim()
        if (input.isEmpty()) return emptyList()
        val parts = input.split(Regex("\\s+"), limit = 2)
        return when (parts[0].lowercase()) {
            "adcodesee" -> {
                val url = parts.getOrNull(1)?.trim()
                if (url.isNullOrEmpty()) listOf("usage: adcodesee <interpreter-url>")
                else try { adcodeseeCore(url) } catch (t: Throwable) { listOf("fail: ${t.message ?: t}") }
            }
            "sonowinter" -> sonowinterLines()
            "reafile" -> reafileLines()
            "killinter" -> {
                val target = parts.getOrNull(1)?.trim()
                if (target.isNullOrEmpty()) listOf("usage: killinter <interpreter-name-or-id>")
                else listOf(if (CodeRiViewStore.removeInterpreter(target)) "removed: $target" else "not found: $target")
            }
            "sick" -> {
                try { sickCore(parts.getOrNull(1)?.trim()) }
                catch (t: Throwable) { listOf("fail: ${t.message ?: t}") }
            }
            "tcopsay" -> runTcopsayLines()
            "anyenone" -> {
                val target = parts.getOrNull(1)?.trim()
                if (target.isNullOrEmpty()) listOf("usage: anyenone <conversation-id>")
                else runAnyenoneLines(target)
            }
            "copycode" -> {
                val target = parts.getOrNull(1)?.trim()
                if (target.isNullOrEmpty()) listOf("usage: copycode <conversation-id>")
                else try { copycodeCore(target) } catch (t: Throwable) { listOf("fail: ${t.message ?: t}") }
            }
            // ==================== 任务9：快捷键命令（godo 内可用） ====================
            "w" -> createLines(parts.getOrNull(1)?.trim())
            "b" -> replaceLines(parts.getOrNull(1)?.trim())
            "s" -> browserSearchLines(parts.getOrNull(1)?.trim())
            "ba" -> restorePrevLines(parts.getOrNull(1)?.trim())
            "c" -> restoreInitialLines(parts.getOrNull(1)?.trim())
            "k" -> deleteLines(parts.getOrNull(1)?.trim())
            "r" -> runFileLines(parts.getOrNull(1)?.trim())
            "o" -> markCleanLines(parts.getOrNull(1)?.trim())
            "p" -> clearContentLines(parts.getOrNull(1)?.trim())
            "peko" -> backupLines(parts.getOrNull(1)?.trim())
            "serg" -> sergLines(parts.getOrNull(1)?.trim())
            else -> {
                // 用户自定义 tool（godo 注册的）：剩余文本作为 $input 注入
                val tool = UserToolStore.get(parts[0].lowercase())
                if (tool == null) {
                    listOf("unknown command: ${parts[0]}")
                } else {
                    val result = McpToolStore.callMcpTool(
                        tool.name,
                        mapOf("input" to (parts.getOrNull(1)?.trim().orEmpty()))
                    )
                    if (result.isSuccess) {
                        result.output.split("\n")
                    } else {
                        listOf("fail: ${result.error ?: "godo tool failed"}")
                    }
                }
            }
        }
    }

    /** 任务6（修订）：godo 接口入口 —— 解析定义并注册为用户 tool（自动导入 AI 的 MCP 工具） */
    private fun runGodoDefinition(raw: String) {
        val def = try {
            GodoToolParser.parse(raw)
        } catch (t: GodoToolParser.ParseException) {
            output("godo fail: ${t.message}")
            output("format: godo：\n【do】：名字\n【hell】：定义\n【theusenee】：逻辑代码")
            return
        }
        val error = UserToolStore.register(def)
        if (error != null) {
            output("godo fail: $error")
        } else {
            output("godo ok: tool '${def.name}' registered (AI can call it via MCP)")
        }
    }

    /** 任务7：godo <url|file> —— 从外部导入 MCP tool（godo 格式或 JSON 规格） */
    private fun runGodoImport(source: String) {
        if (_busy.value) return
        _busy.value = true
        output("importing $source ...")
        scope.launch {
            try {
                val text = resolveToolSource(source)
                importToolText(text, source, 0).forEach { output(it) }
            } catch (t: Throwable) {
                output("godo fail: ${t.message ?: t}")
            } finally {
                _busy.value = false
            }
        }
    }

    /** 解析导入来源：URL → 下载；否则 → 工作区文件 */
    private suspend fun resolveToolSource(source: String): String {
        if (source.startsWith("http://") || source.startsWith("https://")) {
            val d = downloader ?: throw IllegalStateException("downloader not ready")
            val text = d.downloadText(source)
            if (text.isBlank()) throw IllegalStateException("empty download from $source")
            return text
        }
        val id = findEntryByPath(source)?.id
            ?: JsWorkspaceStore.entries.value.firstOrNull { it.name == source }?.id
            ?: throw IllegalStateException("file not found: $source")
        return JsWorkspaceStore.fileContents.value[id] ?: ""
    }

    /** 识别文本格式并注册：godo 格式 → JSON 规格 → 报错；JSON 内 source 字段可再拉取解析（限深度） */
    private suspend fun importToolText(text: String, source: String, depth: Int): List<String> {
        if (depth > 2) return listOf("godo fail: import recursion too deep")
        val t = text.trim()
        if (t.isEmpty()) return listOf("godo fail: empty tool definition from $source")

        // godo 原生格式
        if (t.contains("【do】")) {
            val def = try {
                GodoToolParser.parse(t)
            } catch (e: GodoToolParser.ParseException) {
                return listOf("godo fail: ${e.message}")
            }
            val err = UserToolStore.register(def)
            return if (err != null) {
                listOf("godo fail: $err")
            } else {
                listOf("godo ok: godo tool '${def.name}' imported from $source")
            }
        }

        // JSON 规格
        val spec = McpToolSpecParser.parseJson(t)
        if (spec != null) {
            return registerExternalSpec(spec, source, depth)
        }

        return listOf("godo fail: unsupported tool format from $source (expected godo 【do】 definition or JSON spec)")
    }

    /** 注册外部 JSON 规格：code 模式 / endpoint HTTP 转发模式 / source 递归下载模式 */
    private suspend fun registerExternalSpec(spec: ExternalMcpToolSpec, source: String, depth: Int): List<String> {
        if (spec.name.isBlank() || spec.description.isBlank()) {
            return listOf("godo fail: spec needs 'name' and 'description'")
        }

        // source 模式：下载内容后递归解析（不注册转发 handler）
        if (!spec.source.isNullOrBlank()) {
            val d = downloader ?: return listOf("godo fail: downloader not ready")
            val content = try {
                d.downloadText(spec.source)
            } catch (t: Throwable) {
                return listOf("godo fail: download ${spec.source}: ${t.message}")
            }
            return importToolText(content, spec.source, depth + 1)
        }

        val params = spec.parameters.map {
            McpToolParameter(name = it.name, type = it.type, description = it.description, required = it.required)
        }

        // code 模式：GodoScript 逻辑执行
        if (!spec.code.isNullOrBlank()) {
            val code = spec.code
            val err = UserToolStore.registerMcpTool(
                name = spec.name,
                description = spec.description + " (imported tool)",
                parameters = params
            ) { args ->
                try {
                    val vars = args.mapKeys { it.key.lowercase() } + mapOf("input" to (args["input"] ?: ""))
                    val lines = GodoEngine.execute(
                        code,
                        CommandCaller.Ai,
                        initialVars = vars,
                        depth = McpToolStore.currentDepth()
                    )
                    McpToolResult(isSuccess = true, output = lines.joinToString("\n"))
                } catch (t: Throwable) {
                    McpToolResult(isSuccess = false, output = "", error = t.message ?: "godo tool '${spec.name}' failed")
                }
            }
            return if (err != null) {
                listOf("godo fail: $err")
            } else {
                listOf("godo ok: tool '${spec.name}' imported from $source (godo code mode)")
            }
        }

        // endpoint 模式：HTTP 转发（搜索引擎等外部 MCP tool）
        if (!spec.endpoint.isNullOrBlank()) {
            val err = UserToolStore.registerMcpTool(
                name = spec.name,
                description = spec.description + " (imported http tool)",
                parameters = params
            ) { args ->
                httpToolCall(spec, params, args)
            }
            return if (err != null) {
                listOf("godo fail: $err")
            } else {
                listOf("godo ok: tool '${spec.name}' imported from $source (http ${spec.method.ifBlank { "GET" }})")
            }
        }

        return listOf("godo fail: spec '${spec.name}' needs 'code', 'endpoint' or 'source'")
    }

    /** endpoint 模式执行：GET 拼 query / POST 带 JSON body，结果截断防爆上下文 */
    private suspend fun httpToolCall(
        spec: ExternalMcpToolSpec,
        params: List<McpToolParameter>,
        args: Map<String, String>
    ): McpToolResult {
        val requester = httpRequester
            ?: return McpToolResult(isSuccess = false, output = "", error = "http requester not ready")
        val query = params
            .mapNotNull { p -> args[p.name]?.takeIf { it.isNotBlank() }?.let { p.name to it } }
            .toMap()
        return try {
            val isPost = spec.method.equals("POST", true)
            val body = if (isPost) buildJsonBody(query) else null
            val text = requester.request(
                method = spec.method.ifBlank { "GET" }.uppercase(),
                url = spec.endpoint.orEmpty(),
                queryParams = query,
                body = body
            )
            McpToolResult(isSuccess = true, output = text.take(ContextLimits.MAX_HTTP_RESPONSE_CHARS))
        } catch (t: Throwable) {
            McpToolResult(isSuccess = false, output = "", error = t.message ?: "http request failed")
        }
    }

    private fun buildJsonBody(params: Map<String, String>): String {
        return params.entries.joinToString(",", prefix = "{", postfix = "}") { (k, v) ->
            "\"${escapeJson(k)}\":\"${escapeJson(v)}\""
        }
    }

    private fun escapeJson(s: String): String =
        s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")

    /** sonowinter：列出现在有的解释器（id / 名称 / 支持后缀 / 绑定数）。 */
    private fun runSonowinter() {
        sonowinterLines().forEach { output(it) }
    }

    /** 返回 sonowinter 的输出行（用户命令 / godo / MCP 共用） */
    private fun sonowinterLines(): List<String> {
        val list = CodeRiViewStore.interpreters.value
        if (list.isEmpty()) return listOf("interpreters: none")
        val bindings = CodeRiViewStore.bindings.value
        return buildList {
            add("interpreters: ${list.size}")
            list.forEach { ci ->
                val hub = InterpreterHubStore.byId(ci.id)
                val ext = hub?.extensions?.takeIf { it.isNotEmpty() }?.joinToString(",") ?: "-"
                val boundCount = bindings.values.count { it == ci.id }
                add("${ci.id}  ${ci.name}  [$ext]" + if (boundCount > 0) "  bound:$boundCount" else "")
            }
        }
    }

    /** reafile：列出现在会话的所有文件路径（含根路径前缀）。 */
    private fun runReafile() {
        reafileLines().forEach { output(it) }
    }

    /** 返回 reafile 的输出行（用户命令 / godo / MCP 共用） */
    private fun reafileLines(): List<String> {
        val entries = JsWorkspaceStore.entries.value
        if (entries.isEmpty()) return listOf("files: none")
        val byId = entries.associateBy { it.id }
        val root = JsWorkspaceStore.rootPath.value.ifBlank { "/js_workspace/scripts" }
        val files = entries.filter { !it.isFolder }
        return buildList {
            add("files: ${files.size}/${entries.size} (root: $root)")
            files.forEach { e ->
                val segs = mutableListOf<String>()
                var cur: JsWorkspaceEntry? = e
                while (cur != null) {
                    segs.add(0, cur.name)
                    cur = cur.parentId?.let { byId[it] }
                }
                add("  " + joinPath(root, segs))
            }
        }
    }

    /** killinter：按名称或 id 删除解释器。 */
    private fun runKillinter(target: String) {
        val removed = CodeRiViewStore.removeInterpreter(target)
        output(if (removed) "removed: $target" else "not found: $target")
    }

    /** sick：一次性吐出全部语法错误（走解释器协议 checkSyntaxAll，不执行代码）。 */
    private fun runSick(target: String?) {
        if (_busy.value) return
        _busy.value = true
        scope.launch {
            try {
                sickCore(target).forEach { output(it) }
            } catch (t: Throwable) {
                output("fail: ${t.message ?: t}")
            } finally {
                _busy.value = false
            }
        }
    }

    /** sick 核心逻辑（用户命令与 MCP 工具共用），返回输出行列表 */
    private suspend fun sickCore(target: String?): List<String> {
        val fileId: String? = if (target.isNullOrEmpty()) {
            JsWorkspaceStore.selectedFileId.value
        } else {
            findEntryByPath(target)?.id ?: JsWorkspaceStore.entries.value.firstOrNull { it.name == target }?.id
        }
        if (fileId == null) {
            return listOf(
                if (target.isNullOrEmpty()) {
                    "usage: sick [file-path] (no file selected)"
                } else {
                    "not found: $target"
                }
            )
        }
        val entry = JsWorkspaceStore.getEntry(fileId) ?: return listOf("not found")
        val interpreter = RunInterpreterDispatcher.resolveForFile(fileId, entry.name)
            ?: return listOf("no interpreter for ${entry.name}")
        if (!interpreter.supportsSyntaxCheckAll) {
            return listOf("${interpreter.name} has no checkSyntaxAll protocol")
        }
        val errors = JsRunnerStore.collectAllSyntaxErrors(fileId, entry.name, interpreter)
        if (errors.isEmpty()) return listOf("syntax ok")
        return buildList {
            add("syntax errors: ${errors.size}")
            errors.forEach { add("  $it") }
        }
    }

    /** tcopsay：列出所有会话档案及其 ID（当前会话带 * 标记）。 */
    private fun runTcopsay() {
        runTcopsayLines().forEach { output(it) }
    }

    /** 返回 tcopsay 的输出行（用户命令与 MCP 工具共用） */
    private fun runTcopsayLines(): List<String> {
        val list = ConversationStore.listConversationSummaries()
        if (list.isEmpty()) return listOf("conversations: none")
        val current = ConversationStore.currentConversationId.value
        return buildList {
            add("conversations: ${list.size} (current: $current)")
            list.forEach { s ->
                val marker = if (s.conversationId == current) " *" else ""
                val title = s.title.replace(Regex("\\s+"), " ").trim().take(24)
                add("${s.conversationId}  ${title.ifBlank { "(no title)" }}  (${s.messageCount} msgs)$marker")
            }
        }
    }

    /** anyenone <会话ID>：对该会话的内容做本地摘要（消息数、角色分布、总字符 + 最近消息预览）。 */
    private fun runAnyenone(target: String) {
        runAnyenoneLines(target).forEach { output(it) }
    }

    /** 返回 anyenone 的输出行（用户命令与 MCP 工具共用） */
    private fun runAnyenoneLines(target: String): List<String> {
        val conv = ConversationStore.findConversation(target)
        if (conv == null) return listOf("not found: $target")
        return buildList {
            add("conversation: ${conv.conversationId}")
            val title = conv.title.replace(Regex("\\s+"), " ").trim()
            add("title: ${title.ifBlank { "(no title)" }}")
            val msgs = conv.messages
            val byRole = msgs.groupingBy { it.role }.eachCount()
            add("messages: ${msgs.size} (${byRole.entries.joinToString { "${it.key}:${it.value}" }})")
            add("chars: ${msgs.sumOf { it.content.length }}")
            // 内容摘要：最近最多 10 条消息的截断预览
            val previews = msgs.takeLast(10)
            previews.forEach { m ->
                val roleTag = when (m.role) {
                    "user" -> "u"
                    "assistant" -> "a"
                    "system" -> "s"
                    else -> "?"
                }
                val snippet = m.content.replace(Regex("\\s+"), " ").trim().take(48)
                add("  [$roleTag] ${snippet.ifBlank { "(empty)" }}")
            }
            if (msgs.size > previews.size) {
                add("  ... (${msgs.size - previews.size} more)")
            }
        }
    }

    /** copycode <会话ID>：把目标会话绑定的代码库整体复制到当前会话（文件、文件夹、内容、标签状态）。 */
    private fun runCopycode(target: String) {
        if (_busy.value) return
        val currentId = JsWorkspaceStore.activeConversationId.value
        if (target == currentId) {
            output("cannot copy to itself: $target")
            return
        }
        _busy.value = true
        output("copying $target -> $currentId ...")
        scope.launch {
            try {
                copycodeCore(target).forEach { output(it) }
            } catch (t: Throwable) {
                output("fail: ${t.message ?: t}")
            } finally {
                _busy.value = false
            }
        }
    }

    /** copycode 核心逻辑（用户命令与 MCP 工具共用），返回输出行列表 */
    private suspend fun copycodeCore(target: String): List<String> {
        val snap = JsWorkspaceStore.snapshotOf(target)
            ?: return listOf("not found: $target")
        JsWorkspaceStore.applyExternalSnapshot(snap)
        val fileCount = snap.entries.count { !it.isFolder }
        val folderCount = snap.entries.count { it.isFolder }
        return listOf("ok: copied $fileCount file(s), $folderCount folder(s)")
    }

    /** 按完整路径查找条目（与 reafile 输出格式一致：root 前缀 + 逐级目录名）。 */
    private fun findEntryByPath(path: String): JsWorkspaceEntry? {
        val entries = JsWorkspaceStore.entries.value
        if (entries.isEmpty()) return null
        val byId = entries.associateBy { it.id }
        val root = JsWorkspaceStore.rootPath.value.ifBlank { "/js_workspace/scripts" }
        val cleaned = path.trim().trimStart('/', '\\').trimEnd('/', '\\')
        if (cleaned.isEmpty()) return null
        return entries.firstOrNull { e ->
            if (e.isFolder) return@firstOrNull false
            val segs = mutableListOf<String>()
            var cur: JsWorkspaceEntry? = e
            while (cur != null) {
                segs.add(0, cur.name)
                cur = cur.parentId?.let { byId[it] }
            }
            joinPath(root, segs).trimStart('/', '\\').equals(cleaned, ignoreCase = false)
        }
    }

    private fun runAdcodesee(url: String) {
        val d = downloader ?: run {
            output("downloader not ready")
            return
        }
        _busy.value = true
        output("downloading...")
        scope.launch {
            try {
                adcodeseeCore(url).forEach { output(it) }
            } catch (t: Throwable) {
                output("fail: ${t.message ?: t}")
            } finally {
                _busy.value = false
            }
        }
    }

    /** adcodesee 核心逻辑（用户命令与 MCP 工具共用），返回输出行列表 */
    private suspend fun adcodeseeCore(url: String): List<String> {
        val d = downloader ?: return listOf("downloader not ready")
        val text = d.downloadText(url)
        if (text.isBlank()) throw IllegalStateException("empty response")
        val name = url.substringAfterLast('/').ifBlank { "interpreter.js" }
        val boundFileId = CodeRiViewStore.attachDownloaded(name, url, text)
        return listOf(
            if (boundFileId != null) "ok: $name bound" else "ok: $name (no file selected)"
        )
    }

    /** 命令结果写入 lastMessage，并追加到输出日志（显示在 cmd 框下方）。 */
    private fun output(line: String) {
        _lastMessage.value = line
        _logs.value = (_logs.value + line).takeLast(MAX_CMD_LOG_LINES)
        // 任务5：MCP 捕获模式 —— 同步命令执行期间同时收集输出行，作为工具调用结果返回
        captureSink?.add(line)
    }

    /** 任务8诊断：AI 工具桥的运行日志（写入 cmd 日志，供排查链路） */
    fun logBridge(line: String) {
        _lastMessage.value = line
        _logs.value = (_logs.value + line).takeLast(MAX_CMD_LOG_LINES)
    }

    // ==================== 任务9：工作区快捷键命令核心实现 ====================

    /** 文件内容历史（ba/c0 用）：fileId -> 内容快照栈 */
    private val fileHistory = mutableMapOf<String, MutableList<String>>()

    private fun pushHistory(id: String, content: String) {
        val h = fileHistory.getOrPut(id) { mutableListOf() }
        h.add(content)
        if (h.size > 50) h.removeAt(0)
    }

    private fun resolveEntryId(target: String?): String? {
        if (target.isNullOrBlank()) return JsWorkspaceStore.selectedFileId.value
        return findEntryByPath(target)?.id
            ?: JsWorkspaceStore.entries.value.firstOrNull { it.name == target }?.id
    }

    private fun entryName(id: String): String = JsWorkspaceStore.getEntry(id)?.name ?: id

    private fun currentParentId(): String? =
        JsWorkspaceStore.selectedFileId.value
            ?.let { JsWorkspaceStore.getEntry(it) }
            ?.parentId

    /** c：创建文件/文件夹。c <name> 建文件；c folder <name> 建文件夹；无参 = 默认名 */
    private fun createLines(arg: String?): List<String> {
        val a = arg?.trim().orEmpty()
        val parent = currentParentId()
        val parts = a.split(Regex("\\s+"), limit = 2)
        val isFolder = parts[0].lowercase() in setOf("folder", "dir")
        return if (isFolder) {
            val name = parts.getOrNull(1)?.trim().orEmpty()
            JsWorkspaceStore.createFolder(parent, name)
            listOf("created folder: ${name.ifBlank { "Mew_Folder_N" }}")
        } else {
            val name = a
            JsWorkspaceStore.createFileNamed(parent, name)
            val created = JsWorkspaceStore.selectedFileId.value?.let { JsWorkspaceStore.getEntry(it) }
            listOf("created file: ${created?.name ?: name.ifBlank { "Mew_File_N" }}")
        }
    }

    /** b：改代码。格式：b <原代码> / <修改后代码>（对当前选中文件，全部替换） */
    private fun replaceLines(expr: String?): List<String> {
        val e = expr?.trim().orEmpty()
        if (e.isEmpty()) return listOf("usage: b <原代码> / <修改后代码>")
        val sep = e.indexOf('/')
        if (sep <= 0) return listOf("usage: b <原代码> / <修改后代码>")
        val old = e.substring(0, sep).trim()
        val new = e.substring(sep + 1).trim()
        if (old.isEmpty()) return listOf("usage: b <原代码> / <修改后代码>")
        val id = JsWorkspaceStore.selectedFileId.value
            ?: return listOf("no file selected")
        val content = JsWorkspaceStore.fileContents.value[id] ?: ""
        if (!content.contains(old)) return listOf("not found: $old")
        val count = content.split(old).size - 1
        pushHistory(id, content)
        JsWorkspaceStore.updateContent(id, content.replace(old, new))
        return listOf("replaced $count occurrence(s) in ${entryName(id)}")
    }

    /** s：搜索浏览器（平台注入 BrowserOpener 打开系统浏览器搜索） */
    private fun runBrowserSearch(q: String?) {
        _busy.value = true
        scope.launch {
            try {
                browserSearchLines(q).forEach { output(it) }
            } finally {
                _busy.value = false
            }
        }
    }

    private suspend fun browserSearchLines(q: String?): List<String> {
        val query = q?.trim().orEmpty()
        if (query.isEmpty()) return listOf("usage: s <search-query>")
        val b = browserOpener ?: return listOf("browser opener not ready")
        return try {
            b.openSearch(query)
            listOf("opened browser: $query")
        } catch (t: Throwable) {
            listOf("fail: ${t.message ?: t}")
        }
    }

    /** ba：回到上一次代码状态（历史栈弹出最近一次） */
    private fun restorePrevLines(target: String?): List<String> {
        val id = resolveEntryId(target)
            ?: return listOf(if (target.isNullOrBlank()) "no file selected" else "not found: $target")
        val h = fileHistory[id]
        if (h.isNullOrEmpty()) return listOf("no previous state for ${entryName(id)}")
        val prev = h.removeAt(h.size - 1)
        JsWorkspaceStore.updateContent(id, prev)
        return listOf("restored previous state for ${entryName(id)}")
    }

    /** c0：回到最初（当前上下文第一次状态；恢复到第一条后，该条成为新基线） */
    private fun restoreInitialLines(target: String?): List<String> {
        val id = resolveEntryId(target)
            ?: return listOf(if (target.isNullOrBlank()) "no file selected" else "not found: $target")
        val h = fileHistory[id]
        if (h.isNullOrEmpty()) return listOf("no history for ${entryName(id)}")
        val first = h.first()
        fileHistory[id] = mutableListOf(first)
        JsWorkspaceStore.updateContent(id, first)
        return listOf("restored initial state for ${entryName(id)}")
    }

    /** k：删掉（当前选中文件或指定条目，文件夹递归删除） */
    private fun deleteLines(target: String?): List<String> {
        val id = resolveEntryId(target)
            ?: return listOf(if (target.isNullOrBlank()) "no file selected" else "not found: $target")
        val name = entryName(id)
        JsWorkspaceStore.deleteEntry(id)
        fileHistory.remove(id)
        return listOf("deleted: $name")
    }

    /** r：测试运行哪个（当前选中文件或指定文件） */
    private fun runFileLines(target: String?): List<String> {
        val id = resolveEntryId(target)
            ?: return listOf(if (target.isNullOrBlank()) "no file selected" else "not found: $target")
        val entry = JsWorkspaceStore.getEntry(id) ?: return listOf("not found")
        if (entry.isFolder) return listOf("cannot run folder: ${entry.name}")
        JsWorkspaceStore.openFile(id)
        JsRunnerStore.runCurrentFile()
        return listOf("running ${entry.name} ...")
    }

    /** o：将此次当做第一次的干净（当前状态设为基线，清空历史） */
    private fun markCleanLines(target: String?): List<String> {
        val id = resolveEntryId(target)
            ?: return listOf(if (target.isNullOrBlank()) "no file selected" else "not found: $target")
        val content = JsWorkspaceStore.fileContents.value[id] ?: ""
        fileHistory[id] = mutableListOf(content)
        return listOf("marked as clean baseline: ${entryName(id)}")
    }

    /** p：删掉这个内容（清空当前文件内容，历史留档可 ba 回退） */
    private fun clearContentLines(target: String?): List<String> {
        val id = resolveEntryId(target)
            ?: return listOf(if (target.isNullOrBlank()) "no file selected" else "not found: $target")
        val content = JsWorkspaceStore.fileContents.value[id] ?: ""
        pushHistory(id, content)
        JsWorkspaceStore.updateContent(id, "")
        return listOf("cleared content: ${entryName(id)}")
    }

    /** peko：备份那个文件（创建 <原名>_backup 副本，内容相同） */
    private fun backupLines(target: String?): List<String> {
        val t = target?.trim().orEmpty()
        if (t.isEmpty()) return listOf("usage: peko <文件名>")
        val entry = findEntryByPath(t)
            ?: JsWorkspaceStore.entries.value.firstOrNull { it.name == t }
            ?: return listOf("not found: $t")
        if (entry.isFolder) return listOf("cannot backup folder: ${entry.name}")
        val content = JsWorkspaceStore.fileContents.value[entry.id] ?: ""
        val prevSelected = JsWorkspaceStore.selectedFileId.value
        JsWorkspaceStore.createFileNamed(entry.parentId, entry.name + "_backup")
        val newId = JsWorkspaceStore.selectedFileId.value
        if (newId == null || newId == prevSelected) return listOf("backup failed")
        JsWorkspaceStore.updateContent(newId, content)
        val backupName = JsWorkspaceStore.getEntry(newId)?.name ?: "${entry.name}_backup"
        // 恢复原选中文件
        if (prevSelected != null) JsWorkspaceStore.openFile(prevSelected)
        return listOf("backed up: ${entry.name} -> $backupName")
    }

    /** serg：将代码内容替换成对应文件代码。serg <源文件名> / <目标文件名>（源内容覆盖目标） */
    private fun sergLines(expr: String?): List<String> {
        val e = expr?.trim().orEmpty()
        if (e.isEmpty()) return listOf("usage: serg <源文件名> / <目标文件名>")
        val sep = e.indexOf('/')
        if (sep <= 0) return listOf("usage: serg <源文件名> / <目标文件名>")
        val srcName = e.substring(0, sep).trim()
        val dstName = e.substring(sep + 1).trim()
        if (srcName.isEmpty() || dstName.isEmpty()) return listOf("usage: serg <源文件名> / <目标文件名>")
        val src = findEntryByPath(srcName)
            ?: JsWorkspaceStore.entries.value.firstOrNull { it.name == srcName }
            ?: return listOf("source not found: $srcName")
        val dst = findEntryByPath(dstName)
            ?: JsWorkspaceStore.entries.value.firstOrNull { it.name == dstName }
            ?: return listOf("target not found: $dstName")
        if (src.isFolder || dst.isFolder) return listOf("serg works on files only")
        val content = JsWorkspaceStore.fileContents.value[src.id] ?: ""
        pushHistory(dst.id, JsWorkspaceStore.fileContents.value[dst.id] ?: "")
        JsWorkspaceStore.updateContent(dst.id, content)
        return listOf("replaced ${dst.name} with ${src.name} content")
    }

    // ==================== 任务5：MCP 工具通用注册（AI 可用用户的所有命令） ====================

    /**
     * 同步命令的通用工具注册：按参数定义顺序拼命令字符串，
     * 经 executeCaptured 捕获输出行作为工具结果返回。
     */
    private fun registerSyncMcpTool(
        name: String,
        description: String,
        parameters: List<McpToolParameter> = emptyList()
    ) {
        McpToolStore.register(McpToolDefinition(name, description, parameters)) { args ->
            val command = buildCommandString(name, args, parameters)
            McpToolResult(
                isSuccess = true,
                output = executeCaptured(command, CommandCaller.Ai).joinToString("\n")
            )
        }
    }

    /** 按参数定义顺序把工具参数拼成命令字符串 */
    private fun buildCommandString(
        name: String,
        args: Map<String, String>,
        parameters: List<McpToolParameter>
    ): String {
        val sb = StringBuilder(name)
        parameters.forEach { p ->
            val v = args[p.name]?.trim().orEmpty()
            if (v.isNotEmpty()) sb.append(' ').append(v)
        }
        return sb.toString()
    }

    /** 执行命令并捕获全部 output 行（供 MCP 工具结果返回） */
    private fun executeCaptured(rawInput: String, caller: CommandCaller): List<String> {
        val captured = mutableListOf<String>()
        val previous = captureSink
        captureSink = captured
        try {
            execute(rawInput, caller)
        } finally {
            captureSink = previous
        }
        return captured
    }

    /** adcodesee 的 MCP 处理器（suspend 等待下载完成，返回真实结果） */
    private suspend fun mcpAdcodesee(args: Map<String, String>): McpToolResult {
        val url = args["url"]?.trim()
        if (url.isNullOrEmpty()) {
            return McpToolResult(isSuccess = false, output = "", error = "missing required parameter: url")
        }
        output("downloading...")
        return try {
            val lines = adcodeseeCore(url)
            McpToolResult(isSuccess = true, output = lines.joinToString("\n"))
        } catch (t: Throwable) {
            McpToolResult(isSuccess = false, output = "", error = t.message ?: "adcodesee failed")
        }
    }

    /** sick 的 MCP 处理器（suspend 等待语法检查完成，返回真实结果） */
    private suspend fun mcpSick(args: Map<String, String>): McpToolResult {
        val path = args["path"]?.trim()
        return try {
            val lines = sickCore(path)
            McpToolResult(isSuccess = true, output = lines.joinToString("\n"))
        } catch (t: Throwable) {
            McpToolResult(isSuccess = false, output = "", error = t.message ?: "sick failed")
        }
    }

    /** copycode 的 MCP 处理器（suspend 等待复制完成，返回真实结果） */
    private suspend fun mcpCopycode(args: Map<String, String>): McpToolResult {
        val target = args["conversationId"]?.trim()
        if (target.isNullOrEmpty()) {
            return McpToolResult(isSuccess = false, output = "", error = "missing required parameter: conversationId")
        }
        return try {
            val lines = copycodeCore(target)
            McpToolResult(isSuccess = true, output = lines.joinToString("\n"))
        } catch (t: Throwable) {
            McpToolResult(isSuccess = false, output = "", error = t.message ?: "copycode failed")
        }
    }
}
