package org.example.project

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 解释器集成文件（InterpreterHub）。
 *
 * 所有通过 cmd 命令（adcodesee <url>）下载到的解释器都会自动集成到这里：
 * CodeRiViewStore.attachDownloaded() 注册解释器的同时调用 integrate()。
 *
 * 职责：
 *   - 汇总全部已集成解释器（来源 URL / 源码 / 支持的文件后缀）
 *   - 解析每个解释器声明的文件后缀（魔法注释 → 文件名推断）
 *   - 维护 后缀 -> 解释器 索引，供 Run 按文件后缀自动调用
 *
 * 后缀信息从解释器源码 / 文件名推导，而源码本身已由 CodeRiViewStorage
 * 持久化（ci_N.js），因此本文件无需额外存储，重启后 refresh() 即可重建。
 */

data class IntegratedInterpreter(
    val id: String,
    val name: String,
    val sourceUrl: String,
    val sourceCode: String,
    /** 该解释器支持的文件后缀（小写、不含点），如 ["py", "py3"] */
    val extensions: List<String>,
    /** 实现了协议 checkSyntax(code) -> string|null：Run 预检只报第一个错误 */
    val supportsSyntaxCheck: Boolean = false,
    /** 实现了协议 checkSyntaxAll(code) -> string：chkall 命令一次性吐全部错误 */
    val supportsSyntaxCheckAll: Boolean = false
) {
    /** 展示用后缀串，如 ".py, .py3" */
    val displayExtensions: String get() = extensions.joinToString(", ") { ".$it" }
}

object InterpreterHubStore {

    private val _interpreters = MutableStateFlow<List<IntegratedInterpreter>>(emptyList())
    /** 全部已集成解释器 */
    val interpreters: StateFlow<List<IntegratedInterpreter>> = _interpreters

    private val _extensionMap = MutableStateFlow<Map<String, String>>(emptyMap())
    /** 后缀(小写,无点) -> interpreterId */
    val extensionMap: StateFlow<Map<String, String>> = _extensionMap

    /** 由 CodeRiViewStore.initialize 加载完持久化数据后调用，全量重建集成表（幂等）。 */
    fun refresh() {
        val integrated = CodeRiViewStore.interpreters.value.map { toIntegrated(it) }
        _interpreters.value = integrated
        _extensionMap.value = buildExtensionMap(integrated)
    }

    /** 自动集成入口：新解释器注册时由 CodeRiViewStore 调用。同 id 重复集成只保留最新。 */
    fun integrate(interpreter: CodeInterpreter) {
        val integrated = _interpreters.value
            .filterNot { it.id == interpreter.id } + toIntegrated(interpreter)
        _interpreters.value = integrated
        _extensionMap.value = buildExtensionMap(integrated)
    }

    fun byId(id: String): IntegratedInterpreter? =
        _interpreters.value.firstOrNull { it.id == id }

    /** 按文件后缀取解释器（ext 小写、无点）。同后缀多解释器时，最新集成的生效。 */
    fun interpreterForExtension(ext: String): IntegratedInterpreter? {
        val key = ext.trim().lowercase().removePrefix(".")
        val id = _extensionMap.value[key] ?: return null
        return byId(id)
    }

    /**
     * 推断解释器支持的后缀，优先级：
     *   ① 源码头部魔法注释（前 50 行），行内注释或块注释均可，如：
     *      // @extensions py, py3
     *      // @ext py
     *      // ext: py
     *      // lang: mew
     *   ② 文件名推断：mew.py.js -> py；python-3.10.js -> 无（段首非字母，拒绝）
     *   ③ 都无 → 空列表（不注册后缀，仅可经显式绑定使用）
     */
    fun detectExtensions(sourceCode: String, name: String): List<String> {
        parseMagicExtensions(sourceCode).let { if (it.isNotEmpty()) return it }
        parseNameExtensions(name).let { if (it.isNotEmpty()) return it }
        return emptyList()
    }

    // ==================== 内部 ====================

    private fun toIntegrated(ci: CodeInterpreter): IntegratedInterpreter =
        IntegratedInterpreter(
            id = ci.id,
            name = ci.name,
            sourceUrl = ci.sourceUrl,
            sourceCode = ci.sourceCode,
            extensions = detectExtensions(ci.sourceCode, ci.name),
            supportsSyntaxCheck = supportsSyntaxFn(ci.sourceCode, "checkSyntax"),
            supportsSyntaxCheckAll = supportsSyntaxFn(ci.sourceCode, "checkSyntaxAll")
        )

    /**
     * 自动适配检测：解释器源码里是否存在协议函数定义（function checkSyntax( /
     * var/let/const checkSyntax = / checkSyntax: function）。只扫前 200 行。
     * 任何语种的解释器只要实现该协议，即自动获得语法预检能力，无需硬编码语言映射。
     */
    fun supportsSyntaxFn(sourceCode: String, fnName: String): Boolean {
        val head = sourceCode.lineSequence().take(200).joinToString("\n")
        return Regex(
            """(?m)(?:function\s+$fnName\s*\(|(?:var|let|const)\s+$fnName\s*=|$fnName\s*:\s*function)"""
        ).containsMatchIn(head)
    }

    private fun buildExtensionMap(list: List<IntegratedInterpreter>): Map<String, String> {
        val map = mutableMapOf<String, String>()
        list.forEach { interp ->
            interp.extensions.forEach { ext -> map[ext] = interp.id }
        }
        return map
    }

    private val MAGIC_EXT_RE = Regex(
        """(?i)^\s*(?://|/\*+)\s*@?(?:extensions?|langs?)\s*[:=]?\s*([a-zA-Z0-9_,.\s-]+)"""
    )
    private val EXT_TOKEN_RE = Regex("^[a-zA-Z][a-zA-Z0-9_]{0,9}$")

    private fun parseMagicExtensions(sourceCode: String): List<String> {
        val head = sourceCode.lineSequence().take(50).joinToString("\n")
        return MAGIC_EXT_RE.findAll(head)
            .mapNotNull { it.groupValues.getOrNull(1) }
            .flatMap { it.split(',', ' ', '\t') }
            .map { it.trim().lowercase().removePrefix(".") }
            .filter { EXT_TOKEN_RE.matches(it) }
            .distinct()
            .toList()
    }

    private fun parseNameExtensions(name: String): List<String> {
        val base = name.substringAfterLast('/').substringAfterLast('\\')
        val withoutJs = base.removeSuffix(".js").removeSuffix(".JS")
        val dot = withoutJs.lastIndexOf('.')
        if (dot <= 0 || dot == withoutJs.length - 1) return emptyList()
        val cand = withoutJs.substring(dot + 1).lowercase()
        return if (EXT_TOKEN_RE.matches(cand)) listOf(cand) else emptyList()
    }
}
