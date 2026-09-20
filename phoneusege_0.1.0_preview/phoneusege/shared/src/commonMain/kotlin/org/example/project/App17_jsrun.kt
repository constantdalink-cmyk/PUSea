package org.example.project

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class JsRunOutput(
    val ok: Boolean,
    val logs: List<String>,
    val result: String?,
    val error: String?,
    val durationMs: Long
)

interface JsRunner {
    /**
     * 在后台线程同步执行纯 JavaScript 代码。
     * 实现侧负责注入 console.log / print 并捕获输出。
     */
    suspend fun run(code: String, fileName: String): JsRunOutput

    /**
     * 引擎层语法预检：只解析不执行，返回第一个语法错误信息；null = 无错误或引擎不支持。
     * 默认不支持（返回 null）；实现侧可用 JS_CheckSyntax（QuickJS）等 parse-only 能力。
     */
    suspend fun checkSyntax(code: String, fileName: String): String? = null
}

/** 控制台输出行类别：Stdout 程序输出 / Stderr 错误输出 / Info 系统提示 */
enum class JsConsoleKind { Stdout, Stderr, Info }

/** 单行控制台输出 */
data class JsConsoleLine(
    val text: String,
    val kind: JsConsoleKind = JsConsoleKind.Stdout
)

object JsRunnerStore {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var runner: JsRunner? = null

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    private val _consoleLines = MutableStateFlow<List<JsConsoleLine>>(emptyList())
    val consoleLines: StateFlow<List<JsConsoleLine>> = _consoleLines

    private val _showConsole = MutableStateFlow(false)
    val showConsole: StateFlow<Boolean> = _showConsole

    fun initialize(runner: JsRunner) {
        this.runner = runner
    }

    fun toggleConsole() { _showConsole.value = !_showConsole.value }
    fun clearConsole() { _consoleLines.value = emptyList() }

    fun runCurrentFile() {
        val r = runner ?: return
        if (_isRunning.value) return

        val id = JsWorkspaceStore.selectedFileId.value ?: run {
            _showConsole.value = true
            append("No file selected.", JsConsoleKind.Info)
            return
        }

        val entry = JsWorkspaceStore.getEntry(id) ?: return
        if (entry.isFolder) return

        val userCode = JsWorkspaceStore.fileContents.value[id] ?: ""
        // 按文件后缀自动调用解释器集成文件中的解释器（App28_rundispatch.kt）
        val interpreter = RunInterpreterDispatcher.resolveForFile(id, entry.name)
        // 找不到解释器时明确提示：非 .js 后缀文件按原生 JS 运行会导致困惑，必须说出来
        if (interpreter == null) {
            val ext = RunInterpreterDispatcher.extensionOf(entry.name)
            if (ext != null && ext != "js") {
                _showConsole.value = true
                append("no interpreter for .$ext — running as plain JS", JsConsoleKind.Info)
            }
        }
        val code = RunInterpreterDispatcher.buildRunCode(interpreter, userCode)

        _isRunning.value = true
        _showConsole.value = true

        scope.launch {
            // 语法预检（只报第一个问题，不执行）：
            //   有解释器 → 解释器协议 checkSyntax（自动适配，任何语种）
            //   无解释器 → 引擎层 checkSyntax（.js 原生，引擎实现了才生效）
            if (interpreter != null && interpreter.supportsSyntaxCheck) {
                val err = firstSyntaxError(r, interpreter.sourceCode, userCode, entry.name)
                if (err != null) {
                    append("SyntaxError: $err", JsConsoleKind.Stderr)
                    _isRunning.value = false
                    return@launch
                }
            } else if (interpreter == null) {
                val engineErr = try { r.checkSyntax(userCode, entry.name) } catch (t: Throwable) { null }
                if (engineErr != null) {
                    append("SyntaxError: $engineErr", JsConsoleKind.Stderr)
                    _isRunning.value = false
                    return@launch
                }
            }

            val output = try {
                r.run(code, entry.name)
            } catch (t: Throwable) {
                JsRunOutput(false, emptyList(), null, t.message ?: "Error", 0L)
            }

            // 标准执行输出框：只显示程序真实输出，无运行提示 / 勾叉 / 耗时装饰
            output.logs.forEach { append(it, JsConsoleKind.Stdout) }
            output.error?.let { append(it, JsConsoleKind.Stderr) }

            _isRunning.value = false
        }
    }

    /**
     * 一次性收集全部语法错误（供 chkall 命令用）。
     * 走解释器协议 checkSyntaxAll（返回多行字符串，每行一个错误；空 = 无错误）。
     */
    suspend fun collectAllSyntaxErrors(
        fileId: String,
        fileName: String,
        interpreter: IntegratedInterpreter
    ): List<String> {
        val r = runner ?: return emptyList()
        val userCode = JsWorkspaceStore.fileContents.value[fileId] ?: return emptyList()
        val checkCode = buildCheckCode(interpreter.sourceCode, userCode, "checkSyntaxAll")
        val out = try {
            r.run(checkCode, fileName)
        } catch (t: Throwable) {
            return emptyList()
        }
        val res = out.result?.trim()
        if (res.isNullOrBlank() || res == "undefined" || res == "null") return emptyList()
        return res.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
    }

    /** 解释器协议预检：调用 checkSyntax(code)，返回第一个错误信息；null = 无错误。 */
    private suspend fun firstSyntaxError(
        r: JsRunner,
        interpreterSource: String,
        userCode: String,
        fileName: String
    ): String? {
        val checkCode = buildCheckCode(interpreterSource, userCode, "checkSyntax")
        val out = try {
            r.run(checkCode, fileName)
        } catch (t: Throwable) {
            return null
        }
        val res = out.result?.trim()
        if (res.isNullOrBlank() || res == "undefined" || res == "null") return null
        return res
    }

    /**
     * 组装检查代码：解释器源码 + 调用协议函数 + 表达式求值暴露返回值。
     * 协议：checkSyntax(code) -> string|null（第一个错误）；checkSyntaxAll(code) -> string（每行一个错误）。
     */
    private fun buildCheckCode(interpreterSource: String, userCode: String, fnName: String): String {
        val escaped = userCode
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
        return interpreterSource + "\n;\nvar __checkRes__ = " + fnName + "(\"" + escaped + "\");\n__checkRes__;"
    }

    private fun append(line: String, kind: JsConsoleKind = JsConsoleKind.Stdout) {
        val next = _consoleLines.value + JsConsoleLine(line, kind)
        _consoleLines.value = if (next.size > 300) next.takeLast(300) else next
    }
}
