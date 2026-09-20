package org.example.project

/**
 * Run 分派器：让 Run 通过文件后缀自动调用解释器集成文件（InterpreterHub）中的解释器。
 *
 * 解析优先级：
 *   1. 显式绑定（CodeRiViewStore.bindings：fileId -> interpreterId，旧机制，保持兼容）
 *   2. 后缀自动匹配（InterpreterHubStore 集成表）
 *   3. 都不命中 → 返回 null = 原生 JS 直接运行
 *
 * JsRunnerStore.runCurrentFile() 已改为经本文件解析解释器并组装运行代码。
 */

object RunInterpreterDispatcher {

    /** 解析目标文件运行时应前置的解释器；无则返回 null（= 原生 JS）。 */
    fun resolveForFile(fileId: String, fileName: String): IntegratedInterpreter? {
        // 1) 显式绑定优先（保持旧行为；绑定解释器不在集成表时现场包装一份）
        val bound = CodeRiViewStore.interpreterFor(fileId)
        if (bound != null) {
            return InterpreterHubStore.byId(bound.id) ?: IntegratedInterpreter(
                id = bound.id,
                name = bound.name,
                sourceUrl = bound.sourceUrl,
                sourceCode = bound.sourceCode,
                extensions = InterpreterHubStore.detectExtensions(bound.sourceCode, bound.name),
                supportsSyntaxCheck = InterpreterHubStore.supportsSyntaxFn(bound.sourceCode, "checkSyntax"),
                supportsSyntaxCheckAll = InterpreterHubStore.supportsSyntaxFn(bound.sourceCode, "checkSyntaxAll")
            )
        }

        // 2) 按文件后缀自动匹配集成文件中的解释器
        extensionOf(fileName)?.let { ext ->
            InterpreterHubStore.interpreterForExtension(ext)?.let { return it }
        }

        // 3) 无解释器 → 原生 JS
        return null
    }

    /** 检查解释器是否实现了指定协议函数（自动适配：源码里有定义即支持）。 */
    fun supportsProtocol(interpreter: IntegratedInterpreter, fnName: String): Boolean =
        when (fnName) {
            "checkSyntax" -> interpreter.supportsSyntaxCheck
            "checkSyntaxAll" -> interpreter.supportsSyntaxCheckAll
            else -> false
        }

    /** 组装运行代码：解释器源码 + 用户代码（与 JsRunnerStore 原有拼接逻辑一致）。 */
    fun buildRunCode(interpreter: IntegratedInterpreter?, userCode: String): String =
        if (interpreter != null) "${interpreter.sourceCode}\n;\n$userCode" else userCode

    /** 取文件名后缀（小写、无点）："hello.py" -> "py"、"Mew_File_1" -> null。 */
    fun extensionOf(fileName: String): String? {
        val base = fileName.substringAfterLast('/').substringAfterLast('\\')
        val dot = base.lastIndexOf('.')
        if (dot <= 0 || dot == base.length - 1) return null
        val ext = base.substring(dot + 1)
        return ext.takeIf { it.matches(Regex("[a-zA-Z0-9_]{1,16}")) }?.lowercase()
    }
}
