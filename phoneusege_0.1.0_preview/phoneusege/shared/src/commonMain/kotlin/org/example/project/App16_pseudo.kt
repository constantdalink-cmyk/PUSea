package org.example.project

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * JavaScript / TypeScript 拟语法高亮。
 *
 * 这是轻量词法扫描器，不会修改原始字符串长度，因此可以使用
 * OffsetMapping.Identity 保持光标和选区位置一致。
 */
internal object JsPseudoSyntaxHighlightTransformation : VisualTransformation {

    // 接近常见深色代码编辑器的配色。
    private val commentStyle = SpanStyle(
        color = Color(0xFF6A9955),
        fontStyle = FontStyle.Italic
    )

    private val todoStyle = SpanStyle(
        color = Color(0xFFFFC857),
        fontWeight = FontWeight.Bold
    )

    private val keywordStyle = SpanStyle(
        color = Color(0xFFC586C0),
        fontWeight = FontWeight.SemiBold
    )

    private val literalStyle = SpanStyle(
        color = Color(0xFF569CD6),
        fontWeight = FontWeight.Medium
    )

    private val stringStyle = SpanStyle(
        color = Color(0xFFCE9178)
    )

    private val templateStyle = SpanStyle(
        color = Color(0xFFD7BA7D)
    )

    private val escapeStyle = SpanStyle(
        color = Color(0xFFD7BA7D),
        fontWeight = FontWeight.Bold
    )

    private val numberStyle = SpanStyle(
        color = Color(0xFFB5CEA8)
    )

    private val regexStyle = SpanStyle(
        color = Color(0xFFD16969)
    )

    private val functionStyle = SpanStyle(
        color = Color(0xFFDCDCAA)
    )

    private val declarationStyle = SpanStyle(
        color = Color(0xFF4FC1FF),
        fontWeight = FontWeight.SemiBold
    )

    private val propertyStyle = SpanStyle(
        color = Color(0xFF9CDCFE)
    )

    private val builtInStyle = SpanStyle(
        color = Color(0xFF4EC9B0)
    )

    private val typeStyle = SpanStyle(
        color = Color(0xFF4EC9B0),
        fontWeight = FontWeight.Medium
    )

    private val operatorStyle = SpanStyle(
        color = Color(0xFFD4D4D4)
    )

    private val punctuationStyle = SpanStyle(
        color = Color(0xFF808080)
    )

    private val decoratorStyle = SpanStyle(
        color = Color(0xFFFFC66D)
    )

    private val jsxTagStyle = SpanStyle(
        color = Color(0xFF569CD6),
        fontWeight = FontWeight.Medium
    )

    private val bracketStyles = listOf(
        SpanStyle(color = Color(0xFFFFD700)),
        SpanStyle(color = Color(0xFFDA70D6)),
        SpanStyle(color = Color(0xFF179FFF))
    )

    private val declarationStarters = setOf(
        "const", "let", "var", "using",
        "function", "class", "interface", "type",
        "enum", "namespace", "module"
    )

    private val keywords = setOf(
        // JavaScript 基础关键字
        "as", "async", "await",
        "break", "case", "catch", "class", "const", "continue",
        "debugger", "default", "delete", "do",
        "else", "export", "extends",
        "finally", "for", "from", "function",
        "get", "if", "import", "in", "instanceof",
        "let", "new", "of",
        "return", "set", "static", "super", "switch",
        "this", "throw", "try", "typeof",
        "using", "var", "void", "while", "with", "yield",

        // 保留字、严格模式与较新的提案词
        "accessor", "assert", "enum",
        "implements", "interface",
        "package", "private", "protected", "public",

        // TypeScript
        "abstract", "any", "asserts", "bigint", "boolean",
        "declare", "infer", "is", "keyof", "module",
        "namespace", "never", "object", "override",
        "readonly", "require", "satisfies", "string",
        "symbol", "type", "undefined", "unique", "unknown"
    )

    private val literalWords = setOf(
        "true", "false", "null", "undefined",
        "NaN", "Infinity"
    )

    private val typeWords = setOf(
        // JavaScript / TypeScript 基础类型
        "any", "bigint", "boolean", "never", "number",
        "object", "string", "symbol", "unknown", "void",

        // TypeScript 常用工具类型
        "Awaited", "Capitalize", "ConstructorParameters",
        "Exclude", "Extract", "InstanceType",
        "Lowercase", "NonNullable", "NoInfer",
        "Omit", "OmitThisParameter", "Parameters",
        "Partial", "Pick", "Readonly", "Record",
        "Required", "ReturnType", "ThisParameterType",
        "ThisType", "Uncapitalize", "Uppercase"
    )

    private val builtInWords = setOf(
        // ECMAScript
        "AggregateError", "Array", "ArrayBuffer",
        "Atomics", "BigInt", "BigInt64Array", "BigUint64Array",
        "Boolean", "DataView", "Date",
        "decodeURI", "decodeURIComponent",
        "encodeURI", "encodeURIComponent", "Error", "eval",
        "EvalError", "FinalizationRegistry",
        "Float32Array", "Float64Array",
        "Function", "Int8Array", "Int16Array", "Int32Array",
        "Intl", "isFinite", "isNaN", "JSON", "Map", "Math",
        "Number", "Object", "parseFloat", "parseInt",
        "Promise", "Proxy", "RangeError", "ReferenceError",
        "Reflect", "RegExp", "Set",
        "SharedArrayBuffer", "String", "Symbol",
        "SyntaxError", "TypeError",
        "Uint8Array", "Uint8ClampedArray",
        "Uint16Array", "Uint32Array",
        "URIError", "WeakMap", "WeakRef", "WeakSet", "WebAssembly",

        // 浏览器与 DOM
        "AbortController", "AbortSignal",
        "alert", "atob", "Blob", "btoa",
        "cancelAnimationFrame", "clearInterval", "clearTimeout",
        "console", "crypto", "CustomEvent",
        "document", "Event", "EventTarget",
        "fetch", "File", "FileList", "FileReader", "FormData",
        "Headers", "history", "HTMLElement",
        "indexedDB", "localStorage", "location",
        "MutationObserver", "navigator", "Node",
        "Notification", "performance",
        "queueMicrotask", "requestAnimationFrame",
        "Request", "Response",
        "screen", "sessionStorage",
        "setInterval", "setTimeout",
        "TextDecoder", "TextEncoder",
        "URL", "URLSearchParams",
        "WebSocket", "window", "Worker", "XMLHttpRequest",

        // Node.js / CommonJS
        "Buffer", "clearImmediate", "exports", "global",
        "module", "process", "require", "setImmediate",
        "__dirname", "__filename",

        // 常见环境全局
        "globalThis", "structuredClone"
    )

    private val regexPrefixTokens = setOf(
        "(", "[", "{", ",", ";", ":",
        "=", "==", "===", "!=", "!==",
        "!", "&&", "||", "??",
        "?", "=>",
        "+", "-", "*", "%", "&", "|", "^", "~",
        "return", "throw", "case", "delete", "typeof",
        "void", "new", "in", "of", "yield", "await"
    )

    private val operators = listOf(
        ">>>=", "&&=", "||=", "??=", "**=", "<<=", ">>=",
        "===", "!==", ">>>", "...",
        "=>", "==", "!=", "<=", ">=",
        "++", "--", "&&", "||", "??", "?.",
        "**", "<<", ">>",
        "+=", "-=", "*=", "/=", "%=",
        "&=", "|=", "^=",
        "=", "+", "-", "*", "/", "%",
        "!", "~", "&", "|", "^", "<", ">", "?"
    )

    private val commentMarkers = listOf(
        "TODO", "FIXME", "NOTE", "HACK", "BUG", "XXX"
    )

    override fun filter(text: AnnotatedString): TransformedText {
        if (text.text.isEmpty()) {
            return TransformedText(text, OffsetMapping.Identity)
        }

        // 从原 AnnotatedString 创建 Builder，保留输入法可能附带的原始样式信息。
        val builder = AnnotatedString.Builder(text)

        applyHighlighting(
            source = text.text,
            builder = builder
        )

        return TransformedText(
            text = builder.toAnnotatedString(),
            offsetMapping = OffsetMapping.Identity
        )
    }

    private fun applyHighlighting(
        source: String,
        builder: AnnotatedString.Builder
    ) {
        var index = 0
        var lastToken: String? = null
        var expectDeclaration = false
        var bracketDepth = 0

        while (index < source.length) {
            val current = source[index]

            if (current.isWhitespace()) {
                index += 1
                continue
            }

            // Unix shebang
            if (index == 0 && source.startsWith("#!")) {
                val end = scanLineEnd(source, index)
                styleComment(builder, source, index, end)
                index = end
                continue
            }

            // 单行注释
            if (
                current == '/' &&
                source.getOrNull(index + 1) == '/'
            ) {
                val end = scanLineEnd(source, index)
                styleComment(builder, source, index, end)
                index = end
                continue
            }

            // 块注释 / JSDoc
            if (
                current == '/' &&
                source.getOrNull(index + 1) == '*'
            ) {
                val end = scanBlockComment(source, index)
                styleComment(builder, source, index, end)
                index = end
                continue
            }

            // 普通字符串
            if (current == '\'' || current == '"') {
                val result = scanQuotedString(
                    source = source,
                    start = index,
                    quote = current,
                    allowNewLine = false
                )

                val nextIndex = nextNonWhitespaceIndex(source, result.end)
                val isPropertyKey =
                    nextIndex < source.length &&
                    source[nextIndex] == ':'

                addStyle(
                    builder,
                    if (isPropertyKey) propertyStyle else stringStyle,
                    index,
                    result.end
                )

                result.escapeRanges.forEach { range ->
                    addStyle(
                        builder,
                        escapeStyle,
                        range.first,
                        range.second
                    )
                }

                index = result.end
                lastToken = "value"
                expectDeclaration = false
                continue
            }

            // 模板字符串
            if (current == '`') {
                val result = scanQuotedString(
                    source = source,
                    start = index,
                    quote = '`',
                    allowNewLine = true
                )

                addStyle(
                    builder,
                    templateStyle,
                    index,
                    result.end
                )

                result.escapeRanges.forEach { range ->
                    addStyle(
                        builder,
                        escapeStyle,
                        range.first,
                        range.second
                    )
                }

                index = result.end
                lastToken = "value"
                expectDeclaration = false
                continue
            }

            // 装饰器
            if (
                current == '@' &&
                source.getOrNull(index + 1)?.let(::isIdentifierStart) == true
            ) {
                val end = scanIdentifier(source, index + 1)

                addStyle(
                    builder,
                    decoratorStyle,
                    index,
                    end
                )

                lastToken = "value"
                index = end
                continue
            }

            // JavaScript 私有字段
            if (
                current == '#' &&
                source.getOrNull(index + 1)?.let(::isIdentifierStart) == true
            ) {
                val end = scanIdentifier(source, index + 1)

                addStyle(
                    builder,
                    propertyStyle,
                    index,
                    end
                )

                lastToken = "value"
                index = end
                continue
            }

            // 数字
            if (
                current.isDigit() ||
                (
                    current == '.' &&
                    source.getOrNull(index + 1)?.isDigit() == true
                )
            ) {
                val end = scanNumber(source, index)

                addStyle(
                    builder,
                    numberStyle,
                    index,
                    end
                )

                lastToken = "value"
                expectDeclaration = false
                index = end
                continue
            }

            // 标识符、关键字、类型、内置对象
            if (isIdentifierStart(current)) {
                val start = index
                val end = scanIdentifier(source, start)
                val word = source.substring(start, end)

                val nextIndex = nextNonWhitespaceIndex(source, end)
                val nextChar = source.getOrNull(nextIndex)

                val declarationName = expectDeclaration
                val propertyAfterDot =
                    lastToken == "." ||
                    lastToken == "?."

                val objectProperty =
                    nextChar == ':' &&
                    (
                        lastToken == null ||
                        lastToken == "{" ||
                        lastToken == "," ||
                        lastToken == "("
                    )

                val functionCall = nextChar == '('

                val selectedStyle = when {
                    declarationName -> declarationStyle
                    word in literalWords -> literalStyle
                    word in keywords -> keywordStyle
                    word in typeWords -> typeStyle
                    word in builtInWords -> builtInStyle
                    propertyAfterDot || objectProperty -> propertyStyle
                    functionCall -> functionStyle
                    word.firstOrNull()?.isUpperCase() == true -> typeStyle
                    else -> null
                }

                if (selectedStyle != null) {
                    addStyle(
                        builder,
                        selectedStyle,
                        start,
                        end
                    )
                }

                expectDeclaration =
                    word in declarationStarters

                lastToken = word
                index = end
                continue
            }

            // 正则表达式。通过前一个有效 token 估计 / 是正则还是除号。
            if (
                current == '/' &&
                canStartRegex(lastToken)
            ) {
                val regexEnd = scanRegex(source, index)

                if (regexEnd > index + 1) {
                    addStyle(
                        builder,
                        regexStyle,
                        index,
                        regexEnd
                    )

                    lastToken = "value"
                    expectDeclaration = false
                    index = regexEnd
                    continue
                }
            }

            // JSX 标签名，如 <View>、</View>、<UI.Button>
            if (
                current == '<' &&
                looksLikeJsxTag(source, index)
            ) {
                val bracketEnd =
                    if (source.getOrNull(index + 1) == '/') {
                        index + 2
                    } else {
                        index + 1
                    }

                addStyle(
                    builder,
                    operatorStyle,
                    index,
                    bracketEnd
                )

                val tagStart = bracketEnd
                var tagEnd = tagStart

                while (
                    tagEnd < source.length &&
                    (
                        isIdentifierPart(source[tagEnd]) ||
                        source[tagEnd] == '.' ||
                        source[tagEnd] == ':' ||
                        source[tagEnd] == '-'
                    )
                ) {
                    tagEnd += 1
                }

                addStyle(
                    builder,
                    jsxTagStyle,
                    tagStart,
                    tagEnd
                )

                lastToken = "value"
                index = tagEnd
                continue
            }

            // 彩色括号
            if (current == '(' || current == '[' || current == '{') {
                val style = bracketStyles[
                    bracketDepth % bracketStyles.size
                ]

                addStyle(
                    builder,
                    style,
                    index,
                    index + 1
                )

                bracketDepth += 1
                lastToken = current.toString()
                index += 1
                continue
            }

            if (current == ')' || current == ']' || current == '}') {
                bracketDepth =
                    (bracketDepth - 1).coerceAtLeast(0)

                val style = bracketStyles[
                    bracketDepth % bracketStyles.size
                ]

                addStyle(
                    builder,
                    style,
                    index,
                    index + 1
                )

                lastToken = current.toString()
                expectDeclaration = false
                index += 1
                continue
            }

            // 运算符
            val operator = matchOperator(source, index)

            if (operator != null) {
                addStyle(
                    builder,
                    operatorStyle,
                    index,
                    index + operator.length
                )

                lastToken = operator
                index += operator.length
                continue
            }

            // 标点
            if (
                current == ';' ||
                current == ',' ||
                current == '.' ||
                current == ':'
            ) {
                addStyle(
                    builder,
                    punctuationStyle,
                    index,
                    index + 1
                )

                if (current == ';') {
                    expectDeclaration = false
                }

                lastToken = current.toString()
                index += 1
                continue
            }

            index += 1
        }
    }

    private fun styleComment(
        builder: AnnotatedString.Builder,
        source: String,
        start: Int,
        end: Int
    ) {
        addStyle(
            builder,
            commentStyle,
            start,
            end
        )

        commentMarkers.forEach { marker ->
            var cursor = start

            while (cursor < end) {
                val markerIndex = source.indexOf(
                    string = marker,
                    startIndex = cursor,
                    ignoreCase = true
                )

                if (
                    markerIndex < start ||
                    markerIndex < 0 ||
                    markerIndex + marker.length > end
                ) {
                    break
                }

                addStyle(
                    builder,
                    todoStyle,
                    markerIndex,
                    markerIndex + marker.length
                )

                cursor = markerIndex + marker.length
            }
        }
    }

    private fun addStyle(
        builder: AnnotatedString.Builder,
        style: SpanStyle,
        start: Int,
        end: Int
    ) {
        if (start >= 0 && end > start) {
            builder.addStyle(
                style = style,
                start = start,
                end = end
            )
        }
    }

    private fun scanLineEnd(
        source: String,
        start: Int
    ): Int {
        var index = start

        while (
            index < source.length &&
            source[index] != '\n'
        ) {
            index += 1
        }

        return index
    }

    private fun scanBlockComment(
        source: String,
        start: Int
    ): Int {
        var index = start + 2

        while (index + 1 < source.length) {
            if (
                source[index] == '*' &&
                source[index + 1] == '/'
            ) {
                return index + 2
            }

            index += 1
        }

        return source.length
    }

    private data class StringScanResult(
        val end: Int,
        val escapeRanges: List<Pair<Int, Int>>
    )

    private fun scanQuotedString(
        source: String,
        start: Int,
        quote: Char,
        allowNewLine: Boolean
    ): StringScanResult {
        val escapes = mutableListOf<Pair<Int, Int>>()
        var index = start + 1

        while (index < source.length) {
            val current = source[index]

            if (current == '\\') {
                val escapeEnd =
                    minOf(source.length, index + 2)

                escapes += index to escapeEnd
                index = escapeEnd
                continue
            }

            if (current == quote) {
                index += 1
                break
            }

            if (
                !allowNewLine &&
                (current == '\n' || current == '\r')
            ) {
                break
            }

            index += 1
        }

        return StringScanResult(
            end = index,
            escapeRanges = escapes
        )
    }

    private fun scanRegex(
        source: String,
        start: Int
    ): Int {
        var index = start + 1
        var inCharacterClass = false
        var escaped = false

        while (index < source.length) {
            val current = source[index]

            if (current == '\n' || current == '\r') {
                return start + 1
            }

            if (escaped) {
                escaped = false
                index += 1
                continue
            }

            if (current == '\\') {
                escaped = true
                index += 1
                continue
            }

            if (current == '[') {
                inCharacterClass = true
                index += 1
                continue
            }

            if (current == ']') {
                inCharacterClass = false
                index += 1
                continue
            }

            if (current == '/' && !inCharacterClass) {
                index += 1

                while (
                    index < source.length &&
                    source[index].isLetter()
                ) {
                    index += 1
                }

                return index
            }

            index += 1
        }

        return start + 1
    }

    private fun scanNumber(
        source: String,
        start: Int
    ): Int {
        var index = start

        if (
            source.getOrNull(index) == '0' &&
            source.getOrNull(index + 1) in listOf('x', 'X')
        ) {
            index += 2

            while (
                index < source.length &&
                (
                    isHexDigit(source[index]) ||
                    source[index] == '_'
                )
            ) {
                index += 1
            }

            return if (source.getOrNull(index) == 'n') {
                index + 1
            } else {
                index
            }
        }

        if (
            source.getOrNull(index) == '0' &&
            source.getOrNull(index + 1) in listOf('b', 'B')
        ) {
            index += 2

            while (
                index < source.length &&
                (
                    source[index] == '0' ||
                    source[index] == '1' ||
                    source[index] == '_'
                )
            ) {
                index += 1
            }

            return if (source.getOrNull(index) == 'n') {
                index + 1
            } else {
                index
            }
        }

        if (
            source.getOrNull(index) == '0' &&
            source.getOrNull(index + 1) in listOf('o', 'O')
        ) {
            index += 2

            while (
                index < source.length &&
                (
                    source[index] in '0'..'7' ||
                    source[index] == '_'
                )
            ) {
                index += 1
            }

            return if (source.getOrNull(index) == 'n') {
                index + 1
            } else {
                index
            }
        }

        var hasDot = false

        if (source.getOrNull(index) == '.') {
            hasDot = true
            index += 1
        }

        while (
            index < source.length &&
            (
                source[index].isDigit() ||
                source[index] == '_'
            )
        ) {
            index += 1
        }

        if (
            !hasDot &&
            source.getOrNull(index) == '.'
        ) {
            hasDot = true
            index += 1

            while (
                index < source.length &&
                (
                    source[index].isDigit() ||
                    source[index] == '_'
                )
            ) {
                index += 1
            }
        }

        if (
            source.getOrNull(index) == 'e' ||
            source.getOrNull(index) == 'E'
        ) {
            index += 1

            if (
                source.getOrNull(index) == '+' ||
                source.getOrNull(index) == '-'
            ) {
                index += 1
            }

            while (
                index < source.length &&
                (
                    source[index].isDigit() ||
                    source[index] == '_'
                )
            ) {
                index += 1
            }
        }

        if (!hasDot && source.getOrNull(index) == 'n') {
            index += 1
        }

        return index
    }

    private fun scanIdentifier(
        source: String,
        start: Int
    ): Int {
        var index = start

        while (
            index < source.length &&
            isIdentifierPart(source[index])
        ) {
            index += 1
        }

        return index
    }

    private fun nextNonWhitespaceIndex(
        source: String,
        start: Int
    ): Int {
        var index = start

        while (
            index < source.length &&
            source[index].isWhitespace()
        ) {
            index += 1
        }

        return index
    }

    private fun matchOperator(
        source: String,
        start: Int
    ): String? {
        return operators.firstOrNull { operator ->
            source.startsWith(
                prefix = operator,
                startIndex = start
            )
        }
    }

    private fun canStartRegex(
        lastToken: String?
    ): Boolean {
        return lastToken == null ||
            lastToken in regexPrefixTokens
    }

    private fun looksLikeJsxTag(
        source: String,
        start: Int
    ): Boolean {
        var index = start + 1

        if (source.getOrNull(index) == '/') {
            index += 1
        }

        val first = source.getOrNull(index) ?: return false
        return isIdentifierStart(first)
    }

    private fun isIdentifierStart(
        char: Char
    ): Boolean {
        return char == '_' ||
            char == '$' ||
            char.isLetter()
    }

    private fun isIdentifierPart(
        char: Char
    ): Boolean {
        return isIdentifierStart(char) ||
            char.isDigit()
    }

    private fun isHexDigit(
        char: Char
    ): Boolean {
        return char.isDigit() ||
            char.lowercaseChar() in 'a'..'f'
    }
}