// BookPhoneScreen.kt
// 通识书架前端：会话每满 60 次，经"额外请求端"把这 60 次往来写成一本日记小书，
// 且【每一次总结都单独成册、累加上架】——不再覆盖，书以封皮横着 2 个一排摆在书架上。
// 书架内容可成为"正规军"：点"注入 System"按钮，整架书经 BookLibraryStore 注册为
// 一条 AiSkill，拼进每次常规聊天的 system 词条（handleSend / runAiReplyFlow 双路径零改动吃到）。
//
// 隔离面：成册走独立的额外整理请求（不污染聊天）；注入走现成技能管道（用户按钮显式授权）。
package com.example.bookphone

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.example.project.BookLibraryStore
import org.example.project.BookShelfStore
import org.example.project.ChatTurn
import org.example.project.ConversationStore
import org.example.project.KeyConfigStore

/* ================= 常量（与 CSS 一一对应，单位 dp 等同网页 px） ================= */

private val Ease     = CubicBezierEasing(.4f, 0f, .2f, 1f)
private val FlipEase = CubicBezierEasing(.5f, 0f, .3f, 1f)

private val ScreenBg  = Color(0xFFFCFAF2)
private val Ink       = Color(120, 100, 70, (255 * .28f).toInt())   // 页面横线
private val LineColor = Color(120, 100, 70, (255 * .35f).toInt())   // 顶部 / 书下横线
private val TextColor = Color(110, 90, 60, (255 * .80f).toInt())

/** 翻开书的命中区域（屏幕 dp 坐标） */
private val OpenBookRect = Rect(8f, 290f, 348f, 550f)

/** 合上时内页的微小倾斜角，循环使用 */
private val Tilts = floatArrayOf(-1.5f, 1f, -.5f, 2f)

/* ================= 通识书：自动整理的触发参数 ================= */

private const val BOOK_TRIGGER_TURNS   = 60      // 每满 60 次会话（user+assistant 合计）发出一轮额外整理请求
private const val BOOK_DEBOUNCE_MS     = 1200L   // 防抖：连发消息合并成一次额外请求

/* 装订侧硬规格：页数不限（AI 想写多少写多少），但每一页的排法由这里说了算 */
private const val LINES_PER_PAGE       = 7       // 一页的可写横线数（标题另占 2 格）
private const val LINE_CAPACITY        = 8f      // 一条横线放 8 个全角字（半角按 0.5 计）

/* ================= 书架：封皮配色（一排排书，颜色要活） ================= */

private val CoverPalette = listOf(
    Color(0xFF3E6257),   // 墨绿
    Color(0xFF8A4B3B),   // 砖红
    Color(0xFF46557A),   // 黛蓝
    Color(0xFF7A6032),   // 驼金
    Color(0xFF6E4A6E),   // 紫棠
    Color(0xFF2F5D62)    // 青碧
)

private val CoverInk = Color(0xFFF3E9D2)          // 封皮上的米色字

private fun Color.lighten(f: Float = .22f): Color = Color(
    red + (1f - red) * f,
    green + (1f - green) * f,
    blue + (1f - blue) * f,
    alpha
)

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

/* ================= 通识书：数据模型 / 技能词条 / 解析 ================= */

@Serializable
private data class BookPageSpec(
    val title: String = "",
    /** 线上契约：AI 写连贯散文，不手工换行、不数行数 */
    val text: String = "",
    /** 装订产物：客户端自动排出的行，AI 不写这个字段 */
    val lines: List<String> = emptyList()
)

@Serializable
private data class BookSpec(
    val title: String = "",
    /** 一册简介：合上时封面只见 fragment + "…"，翻开后扉页长横线上完整展开 */
    val desc: String = "",
    val pages: List<BookPageSpec> = emptyList(),
    /** 每次总结末尾 AI 给出的高信息熵关键词（[词 词 ...] 块，半角空格分隔，至少 1 个、不设上限） */
    val entropyWords: List<String> = emptyList(),
    /* ---- 以下由客户端在成册上架时填写，AI 不产出 ---- */
    val volume: Int = 0,            // 第几册（书架上的稳定身份，只增不减）
    val compiledTurns: Int = 0,     // 截至第几次对话整理
    val compiledAt: String = ""     // 成册时间戳（展示用）
)

private val bookJson = Json { ignoreUnknownKeys = true; isLenient = true }

/**
 * 额外注入的技能词条：教 AI 扮演"通识日记主笔"，
 * 把整段对话压缩成小书能放得下的结构化页面。
 */
private fun bookSkillTurn(): ChatTurn = ChatTurn(
    role = "system",
    content = """
【额外技能 · 通识日记主笔】
你现在的唯一任务：把本次总结覆盖的这 60 次你与用户的对话，写成一本掌上日记小书——
作者是"我"（AI），以第一人称记我与用户这 60 次往来的手记，是日记，不是冰冷的总结报告。
书的纸张是横线纸，但排线的事轮不到你操心：你只管写连贯的散文，不要手工换行，不要数行数、数字数——
装订一方会把你的文字自动排到横线上（自动换行，写多了自动续页，内容不会丢）。
日记贵在克制：短白描，宁短勿长（可仿"晴。问字。改Key。夜再谈。"的简净）。
一页横线纸约放 56 字（7 行 × 8 字），每段文字控制在一页上下即可；页数不限，想写多少写多少，但确实不必写太多。
完整输出 = 一个 JSON 块 + 末尾的熵词块，除此之外不得有任何文字：
[[book:{"title":"册名","desc":"一册简介","pages":[{"title":"页标题","text":"一段连贯的日记文字"}]}]] [熵词1 熵词2 熵词3]
整理规则：
1. 册名像日记本的一册之名，不超过 8 个字，按这 60 次的内容命名（如"问字小记""深夜改Key"）；
   desc 是这一册的简介：用第一人称一句话概括这本日记，30 字以内，连贯不成行——
   它会被印在两处：合上的封面上只露出前一截加省略号吊人胃口，翻开后的扉页才在横线上一字不少地展开，所以把信息写密；
2. 头 4 页必写、按此顺序，页标题就用这四个词（每个页标题不超过 6 个字）：
   - 「聊天状态」：这 60 次聊得怎么样——氛围、节奏、密度、起伏与转折；
   - 「用户态度」：用户显得怎么样——情绪、立场、口吻、信任与急切程度，如实写你的观察；
   - 「核心要点」：这 60 次谈了什么要紧的事、定了什么、结出了什么；内容多你可以自己分几页，后续页标题可为"核心要点·某主题"；
   - 「经历总结」：我从这 60 次里学到/经历了什么——我自己的收获与体会；
3. text 写连贯的段落（不要换行）；万一字符串内出现换行必须写成转义的 \n，保证 JSON 可直接解析；
4. 日记口吻：第一人称"我"，短白描，可以有温度、有态度、有猜测，但一切判断必须真实基于对话内容，禁止虚构、禁止照抄原句；
5. 内容太少就如实写少（如"尚不熟。待观察。"），但四段必须齐全；
6. 熵词块写在 JSON 块之后：给出本次总结里信息熵最高的关键词——至少 1 个，不设上限；
   词与词之间只用一个半角空格（U+0020）分隔，禁止顿号、逗号、引号、换行等任何其它分隔符；
   每个词必须真实引用自对话内容（实际出现过的概念、名词、结论），让日后引用这本书的读者能据此分开理解，不得虚构。
""".trimIndent()
)

/** 把字符串值里未转义的换行/制表符转义掉，救回模型常犯的裸换行 JSON */
private fun sanitizeJsonControlChars(s: String): String = buildString {
    var inString = false
    var escaped = false
    for (c in s) {
        when {
            escaped            -> { append(c); escaped = false }
            c == '\\' && inString -> { append(c); escaped = true }
            c == '"'           -> { inString = !inString; append(c) }
            inString && c == '\n' -> append("\\n")
            inString && c == '\r' -> Unit
            inString && c == '\t' -> append("\\t")
            else               -> append(c)
        }
    }
}

/** 从 AI 回复里挖出 [[book:...]] 块并解析成 BookSpec（解析失败返回 null，下轮再试） */
private fun parseBookSpec(raw: String): BookSpec? {
    val marker = raw.indexOf("[[book:")
    if (marker < 0) return null
    val jsonStart = raw.indexOf('{', marker)
    if (jsonStart < 0) return null

    val closing = raw.indexOf("]]", jsonStart)
    val searchEnd = if (closing > jsonStart) closing else raw.length
    var jsonEnd = searchEnd - 1
    while (jsonEnd > jsonStart && raw[jsonEnd] != '}') jsonEnd--
    if (jsonEnd <= jsonStart) return null

    val parsed = try {
        bookJson.decodeFromString(
            BookSpec.serializer(),
            sanitizeJsonControlChars(raw.substring(jsonStart, jsonEnd + 1))
        )
    } catch (_: Exception) {
        null
    } ?: return null

    // AI 只交连贯散文；换行 / 分页 / 压线全部交给装订侧（typesetPages）自动整理
    val cleanedPages = parsed.pages
        .filter { it.title.isNotBlank() || it.text.isNotBlank() || it.lines.any { l -> l.isNotBlank() } }
        .map {
            it.copy(
                title = it.title.take(8),
                // 兼容旧的逐行格式：拼回成段，统一走装订
                text = it.text.ifBlank { it.lines.joinToString(" ") },
                lines = emptyList()
            )
        }

    // 末尾熵词块：[词1 词2 词3] —— 在 book JSON 块的 "]]" 之后，半角空格分隔。
    // 数量不设上限是 AI 侧契约；这里只按空白（含全角空格）切分并滤掉明显不是词的杂质。
    val entropyWords = run {
        val blockClose = raw.indexOf("]]", jsonEnd)
        if (blockClose < 0) return@run emptyList()
        val open = raw.indexOf('[', blockClose + 2)
        if (open < 0) return@run emptyList()
        val close = raw.indexOf(']', open + 1)
        if (close < 0) return@run emptyList()
        raw.substring(open + 1, close)
            .split(Regex("[\\s\\u3000]+"))
            .map { it.trim().trim('"', '「', '」', '【', '】', ',', '，', '、') }
            .filter { it.isNotBlank() && it.length <= 16 }
            .distinct()
    }

    return parsed.copy(
        title = parsed.title.take(10),
        desc = parsed.desc.take(40).trim(),   // 目标 30 字（扉页约 4 条横线）；40 字为硬上限
        pages = typesetPages(cleanedPages),
        entropyWords = entropyWords
    )
}

/**
 * 额外请求端本体：
 * - 独立预算：maxMessages / maxCharacters 远大于常规聊天窗口（整理需要看全量上下文）；
 * - 额外注入：bookSkillTurn 置顶，常规 system 词条与对话历史紧随其后；
 * - 通道复用：KeyConfigStore.requestFirstStreamSuccess 多 Key 竞速；
 * - 可被取代：调用方协程被取消（新消息到来）时整体中止，不产生过期书。
 */
private suspend fun compileBook(): BookSpec? {
    val history = ConversationStore.buildContextWindow(
        maxMessages = BOOK_TRIGGER_TURNS + 20,
        maxCharacters = 30000,
        maxSingleMessageChars = 1500
    )
    val request = listOf(bookSkillTurn()) + history

    var streamed = ""
    val result = try {
        withContext(Dispatchers.Default) {
            KeyConfigStore.requestFirstStreamSuccess(request) { delta ->
                streamed += delta
            }
        }
    } catch (e: CancellationException) {
        throw e                      // 被更新的上下文取代 → 让外层 LaunchedEffect 重启接管
    } catch (_: Exception) {
        null
    }

    val source = result?.fullText?.takeIf { it.isNotBlank() } ?: streamed
    if (source.isBlank()) return null
    return parseBookSpec(source)
}

/* ================= 装订：把 AI 的连贯散文排上横线纸（AI 不踩线，线的事归这里管） ================= */

/** 字宽：半角（ASCII）按 0.5 计，全角 / CJK 按 1 计——与 5.5sp 字号在 50dp 纸宽上的实测排布一致 */
private fun charWidth(c: Char): Float = if (c.code < 0x2E80) 0.5f else 1f

/** 自动换行：贪心填满一条横线（LINE_CAPACITY）；\n 强制断行；行首不留空格 */
private fun wrapToLines(text: String): List<String> {
    val lines = mutableListOf<String>()
    for (paragraph in text.replace('\r', ' ').split('\n')) {
        val current = StringBuilder()
        var width = 0f
        for (c in paragraph) {
            val w = charWidth(c)
            if (width + w > LINE_CAPACITY + 1e-3f && current.isNotEmpty()) {
                lines += current.toString().trimEnd()
                current.clear()
                width = 0f
                if (c == ' ') continue      // 空格不带到下一行行首
            }
            current.append(c)
            width += w
        }
        val tail = current.toString().trim()
        if (tail.isNotEmpty()) lines += tail
    }
    return lines
}

/** 自动分页：每 LINES_PER_PAGE 行钉成一张纸；多出的内容流进续页（「xxx·续」「xxx·续2」…），不丢字 */
private fun typesetPages(pages: List<BookPageSpec>): List<BookPageSpec> =
    pages.flatMap { page ->
        val lines = wrapToLines(page.text).ifEmpty { listOf("待补充") }
        lines.chunked(LINES_PER_PAGE).mapIndexed { idx, chunk ->
            BookPageSpec(
                title = when {
                    idx == 0 -> page.title
                    idx == 1 -> "${page.title}·续"
                    else     -> "${page.title}·续$idx"
                },
                lines = chunk
            )
        }
    }

/* ================= 书架持久化编解码：整架书 ↔ 不透明 JSON 快照 ================= */

private val shelfCodec = Json { ignoreUnknownKeys = true; isLenient = true }
private val shelfListSerializer = ListSerializer(BookSpec.serializer())

/** 整架书 → 持久化快照（对 BookShelfStore 不透明） */
private fun serializeShelf(shelf: List<BookSpec>): String =
    runCatching { shelfCodec.encodeToString(shelfListSerializer, shelf) }.getOrDefault("[]")

/** 持久化快照 → 整架书（坏档 / 空档 → 空架，不影响重新成册） */
private fun deserializeShelf(raw: String): List<BookSpec> =
    runCatching { shelfCodec.decodeFromString(shelfListSerializer, raw) }.getOrDefault(emptyList())

/* ================= 书架 → system：把整架书序列化成"正规军"片段 ================= */

/** 成册时间戳（展示用，避免依赖不确定可见性的 getCurrentTime） */
private fun nowStamp(): String = try {
    java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
        .format(java.util.Date())
} catch (_: Exception) {
    ""
}

/** 单册 → 紧凑文本块（供注入 system 使用） */
private fun formatBookBlock(book: BookSpec): String = buildString {
    appendLine("【第${book.volume}册 ·《${book.title.ifBlank { "通识" }}》】（截至第${book.compiledTurns}次对话）")
    book.desc.takeIf { it.isNotBlank() }?.let { appendLine("简介：$it") }
    book.pages.forEach { pg ->
        val body = pg.lines.joinToString("；").ifBlank { pg.text.take(60) }
        appendLine("· ${pg.title.ifBlank { "页" }}：$body")
    }
    if (book.entropyWords.isNotEmpty()) {
        appendLine("关键词：${book.entropyWords.joinToString(" ")}")
    }
    appendLine()
}

/**
 * 整架书 → 长期记忆 system 片段。
 * 书架越长越要克制：总量超 MAX_SNIPPET_CHARS 时保留最近的册（旧的先让位）。
 */
private fun buildShelfSnippet(shelf: List<BookSpec>): String {
    if (shelf.isEmpty()) return ""

    val kept = mutableListOf<BookSpec>()
    var used = 0
    for (book in shelf.asReversed()) {              // 从最新往回挑
        val len = formatBookBlock(book).length
        if (used + len > BookLibraryStore.MAX_SNIPPET_CHARS && kept.isNotEmpty()) break
        kept.add(0, book)                            // 仍按时间正序输出
        used += len
    }

    return buildString {
        appendLine("=== 通识书架（系统自动整理的长期记忆，共 ${shelf.size} 册）===")
        appendLine("以下每册是此前若干轮对话自动整理成的通识书，作为你的长期背景知识，回答时可自然参考，无需逐字复述：")
        kept.forEach { append(formatBookBlock(it)) }
        appendLine("=== 通识书架完 ===")
    }
}

/* ================= 入口：整体自适应缩放到任意屏幕 ================= */

@Composable
fun BookPhoneScreen() {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(Color(0xFFE9EEF5), Color(0xFFCFD8E3)))),
        contentAlignment = Alignment.Center
    ) {
        val scale = minOf(maxWidth / 400.dp, maxHeight / 840.dp).coerceAtMost(1f)
        Box(
            Modifier
                .size(380.dp, 820.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale }
        ) { Phone() }
    }
}

/* ================= 手机机身 ================= */

@Composable
private fun Phone() {
    val shape = RoundedCornerShape(48.dp)
    Box(Modifier.size(380.dp, 820.dp)) {
        SideButton(x = -6f, y = 180f, h = 60f)
        SideButton(x = -6f, y = 250f, h = 60f)
        SideButton(x = 382f, y = 210f, h = 90f)

        Box(
            Modifier
                .fillMaxSize()
                .shadow(28.dp, shape)
                .background(Brush.linearGradient(listOf(Color(0xFF2B2B2E), Color(0xFF0F0F11))), shape)
                .border(2.dp, Color(0xFF444444), shape)
                .padding(10.dp)
        ) { Screen() }
    }
}

@Composable
private fun SideButton(x: Float, y: Float, h: Float) {
    Box(
        Modifier
            .offset(x.dp, y.dp)
            .size(4.dp, h.dp)
            .background(
                Brush.horizontalGradient(listOf(Color(0xFF3A3A3D), Color(0xFF1C1C1E))),
                RoundedCornerShape(2.dp)
            )
    )
}

/* ================= 屏幕：书架 + 成册触发 + 注入开关 ================= */

@Composable
private fun Screen() {
    // 启动回读存档：整架书 / 注入开关 / 整理档位跨重启原样回来（未初始化或无档 → 空架/false/0）
    var shelf            by remember { mutableStateOf(deserializeShelf(BookShelfStore.loadShelfJson())) }   // 每一册，只增不减
    var openVolume       by remember { mutableStateOf<Int?>(null) }                    // 当前翻开的是第几册
    var lastOpenBook     by remember { mutableStateOf<BookSpec?>(null) }               // 供退场动画留影
    var switchOn         by remember { mutableStateOf(true) }                          // 额外请求端总开关（自动整理）
    var injectOn         by remember { mutableStateOf(BookShelfStore.loadInjectOn()) } // ★ 注入 System 开关（跨重启保留）
    var isCompiling      by remember { mutableStateOf(false) }
    var lastCompiledBucket by remember { mutableStateOf(BookShelfStore.loadLastCompiledBucket()) }  // 已完成总结的"60 次"档位（防重启后重复成册）

    val messages by ConversationStore.messages.collectAsState()

    val openBook = shelf.firstOrNull { it.volume == openVolume }
    LaunchedEffect(openBook) { if (openBook != null) lastOpenBook = openBook }

    // 触发器：每满 60 次会话（turns 跨入新的 60 档位：60/120/180…）才发出额外整理请求。
    // 成功后【新成一册、追加上架】（volume = 已有册数 + 1），不覆盖旧书。
    LaunchedEffect(messages, switchOn) {
        if (!switchOn) { isCompiling = false; return@LaunchedEffect }

        val turns  = messages.count { it.role == "user" || it.role == "assistant" }
        val bucket = turns / BOOK_TRIGGER_TURNS            // 60→1 档，120→2 档…
        if (bucket < 1 || bucket <= lastCompiledBucket) return@LaunchedEffect

        delay(BOOK_DEBOUNCE_MS)     // 连发合并：停顿 1.2s 才真正发起额外请求
        isCompiling = true
        try {
            val spec = compileBook()
            if (spec != null && spec.pages.isNotEmpty()) {
                shelf = shelf + spec.copy(                 // ← 上架：追加一册
                    volume = shelf.size + 1,
                    compiledTurns = turns,
                    compiledAt = nowStamp()
                )
                lastCompiledBucket = bucket                // 成功才推进档位；失败留在原档，下条消息重试
            }
        } catch (_: CancellationException) {
            // 被更新鲜的上下文取代，新一轮 LaunchedEffect 已接管
        } finally {
            isCompiling = false
        }
    }

    // 注入同步：书架或开关一变，就把整架书 sync 成"通识书架"技能（拼进 system 的正规军）。
    // injectOn=false 或书架为空 → 技能撤下/不注入；请求管道（handleSend/runAiReplyFlow）零改动即吃到。
    LaunchedEffect(shelf, injectOn) {
        BookLibraryStore.sync(buildShelfSnippet(shelf), injectOn)
    }

    // 持久化：书架 / 注入开关 / 整理档位任一变化即整体落盘——重启后整架书、开关、档位原样回来。
    // 整架书以"不透明 JSON 快照"交给 BookShelfStore（BookSpec 是本文件私有模型，Store 不碰其结构）。
    LaunchedEffect(shelf, injectOn, lastCompiledBucket) {
        BookShelfStore.persist(serializeShelf(shelf), injectOn, lastCompiledBucket)
    }

    Box(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(40.dp))
            .background(ScreenBg)
    ) {
        // 书架主体（翻开书时被暗场盖住）
        ShelfHeader(count = shelf.size, isCompiling = isCompiling)
        if (shelf.isEmpty()) {
            EmptyShelfHint(isCompiling = isCompiling)
        } else {
            ShelfGrid(shelf = shelf, onOpen = { openVolume = it })
        }

        // 底部控制条：自动整理开关 + ★注入 System 按钮
        ControlBar(
            bookCount = shelf.size,
            switchOn = switchOn,
            injectOn = injectOn,
            onSwitch = { switchOn = !switchOn },
            onInject = { injectOn = !injectOn }
        )

        // 翻开某一册：暗场 + 大图翻书（进场/退场都有淡入淡出）
        AnimatedVisibility(
            visible = openBook != null,
            enter = fadeIn(tween(250)),
            exit = fadeOut(tween(250))
        ) {
            lastOpenBook?.let { book ->
                OpenBookOverlay(book = book, isCompiling = isCompiling) { openVolume = null }
            }
        }

        Camera()
        TopLine()
    }
}

/* ================= 摄像头 / 顶部横线 ================= */

@Composable
private fun BoxScope.Camera() {
    Box(
        Modifier
            .align(Alignment.TopCenter)
            .offset(y = 12.dp)
            .size(20.dp)
            .zIndex(10f)
            .background(Color.Black.copy(alpha = .6f), CircleShape)
            .padding(2.dp)
            .background(
                Brush.radialGradient(listOf(Color(0xFF3A3F4A), Color(0xFF0A0A0C))),
                CircleShape
            )
    )
}

@Composable
private fun TopLine() {
    val style = TextStyle(
        fontSize = 13.sp,
        fontFamily = FontFamily.Serif,
        letterSpacing = .5.sp,
        color = TextColor
    )
    Box(
        Modifier
            .offset(20.dp, 22.dp)
            .width(320.dp)
            .height(24.dp)
            .zIndex(3f)
    ) {
        BasicText("general",   Modifier.align(Alignment.BottomStart).padding(start = 2.dp, bottom = 6.dp), style)
        BasicText("knowledge", Modifier.align(Alignment.BottomEnd).padding(end = 2.dp, bottom = 6.dp),   style)
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(2.dp)
                .background(LineColor, RoundedCornerShape(1.dp))
        )
    }
}

/* ================= 书架：抬头 / 网格（2 个一排）/ 空架提示 ================= */

@Composable
private fun ShelfHeader(count: Int, isCompiling: Boolean) {
    val pulse = rememberInfiniteTransition(label = "compilingPulse")
    val glow by pulse.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "glow"
    )
    Row(
        Modifier
            .offset(22.dp, 54.dp)
            .fillMaxWidth()
            .padding(end = 22.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            BasicText(
                "通识书架",
                style = TextStyle(fontSize = 19.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, color = TextColor)
            )
            Spacer(Modifier.width(7.dp))
            BasicText(
                "· $count 册",
                Modifier.padding(bottom = 2.dp),
                TextStyle(fontSize = 12.sp, fontFamily = FontFamily.Serif, color = TextColor.copy(alpha = .75f))
            )
        }
        if (isCompiling) {
            BasicText(
                "整理中 …",
                style = TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Serif, color = Color(0xFFB08D57).copy(alpha = .5f + .5f * glow))
            )
        }
    }
}

/** 书架网格：封皮横着 2 个一排，点哪册翻哪册 */
@Composable
private fun ShelfGrid(shelf: List<BookSpec>, onOpen: (Int) -> Unit) {
    Box(
        Modifier
            .offset(y = 86.dp)
            .fillMaxWidth()
            .height(600.dp)
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            items(shelf.size) { i ->
                ShelfBookCell(
                    book = shelf[i],
                    index = i,
                    isNewest = shelf[i].volume == shelf.size
                ) { onOpen(shelf[i].volume) }
            }
        }
    }
}

/** 一册封皮：站姿、微微歪斜、落架有先后（错落进场），按压实有回弹 */
@Composable
private fun ShelfBookCell(book: BookSpec, index: Int, isNewest: Boolean, onOpen: () -> Unit) {
    val cover = CoverPalette[(book.volume - 1).coerceAtLeast(0) % CoverPalette.size]
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(70L * index)     // 错落上架：一册册落下来
        appeared = true
    }
    val scale by animateFloatAsState(
        targetValue = if (appeared) (if (pressed) .94f else 1f) else .55f,
        animationSpec = tween(380, easing = Ease),
        label = "cellScale"
    )
    val alpha by animateFloatAsState(if (appeared) 1f else 0f, tween(380), label = "cellAlpha")
    val tilt = Tilts[index % Tilts.size] * .9f

    Box(
        Modifier
            .fillMaxWidth()
            .height(212.dp)
            .clickable(interactionSource = interaction, indication = null, onClick = onOpen),
        contentAlignment = Alignment.Center
    ) {
        // 架板上的投影
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .offset(y = (-4).dp)
                .size(112.dp, 12.dp)
                .background(Color(0x1A3A2E1A), RoundedCornerShape(50))
        )
        Box(
            Modifier
                .width(142.dp)
                .height(186.dp)
                .graphicsLayer {
                    scaleX = scale; scaleY = scale
                    rotationZ = tilt
                    this.alpha = alpha
                }
                .shadow(7.dp, RoundedCornerShape(6.dp))
                .background(
                    Brush.linearGradient(listOf(cover.lighten(), cover)),
                    RoundedCornerShape(6.dp)
                )
                .border(1.dp, Color.White.copy(alpha = .35f), RoundedCornerShape(6.dp))
        ) {
            // 内框（精装感）
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(6.dp)
                    .border(1.dp, Color.White.copy(alpha = .22f), RoundedCornerShape(3.dp))
            )
            // 书脊 + 两道脊线
            Box(Modifier.fillMaxHeight().width(13.dp).background(Color.Black.copy(alpha = .18f)))
            Box(Modifier.offset(2.dp, 26.dp).size(9.dp, 1.5.dp).background(Color.White.copy(alpha = .3f)))
            Box(Modifier.offset(2.dp, 150.dp).size(9.dp, 1.5.dp).background(Color.White.copy(alpha = .3f)))

            // 册号（大字，顶部）
            Column(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                BasicText("第", style = TextStyle(fontSize = 9.sp, fontFamily = FontFamily.Serif, color = CoverInk.copy(alpha = .85f)))
                BasicText("${book.volume}", style = TextStyle(fontSize = 30.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, color = CoverInk))
                BasicText("册", style = TextStyle(fontSize = 9.sp, fontFamily = FontFamily.Serif, color = CoverInk.copy(alpha = .85f)))
            }

            // 册名（底部）
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 18.dp, end = 8.dp, bottom = 18.dp),
                contentAlignment = Alignment.Center
            ) {
                BasicText(
                    book.title.ifBlank { "通识" }.take(10),
                    style = TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, color = CoverInk)
                )
            }

            // 最新一册的火漆"新"印
            if (isNewest) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset((-5).dp, 9.dp)
                        .size(21.dp)
                        .shadow(2.dp, CircleShape)
                        .background(Color(0xFFC05B4D), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    BasicText("新", style = TextStyle(fontSize = 9.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, color = Color(0xFFFFF4E4)))
                }
            }
        }
    }
}

/** 空书架：还没成书时的占位提示（整理中会呼吸） */
@Composable
private fun EmptyShelfHint(isCompiling: Boolean) {
    val pulse = rememberInfiniteTransition(label = "hintPulse")
    val glow by pulse.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "hintGlow"
    )
    val alpha = if (isCompiling) lerp(.35f, .75f, glow) else .4f
    val ink = Color(120, 100, 70, (255 * alpha).toInt())

    Column(
        Modifier
            .offset(y = 86.dp)
            .fillMaxWidth()
            .height(600.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 一本虚线书
        Box(Modifier.size(120.dp, 150.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                val r = 8.dp.toPx()
                drawRoundRect(
                    color = ink,
                    cornerRadius = CornerRadius(r, r),
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(6.dp.toPx(), 5.dp.toPx())
                        )
                    )
                )
            }
            BasicText(
                if (isCompiling) "…" else "书",
                Modifier.align(Alignment.Center),
                TextStyle(fontSize = 26.sp, fontFamily = FontFamily.Serif, color = ink)
            )
        }
        Spacer(Modifier.height(16.dp))
        BasicText(
            if (isCompiling) "AI 正在整理这一册…" else "满 60 次对话 · 自动成书上架",
            style = TextStyle(fontSize = 12.sp, fontFamily = FontFamily.Serif, letterSpacing = .5.sp, color = ink)
        )
    }
}

/* ================= 翻开某一册：暗场 + 大图翻书 ================= */

@Composable
private fun OpenBookOverlay(book: BookSpec, isCompiling: Boolean, onClose: () -> Unit) {
    var current by remember(book.volume) { mutableStateOf(1) }
    val paperSheets = book.pages.size.coerceAtLeast(1)
    LaunchedEffect(paperSheets) { if (current > paperSheets) current = paperSheets }

    val p by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(800, easing = Ease),
        label = "open"
    )

    Box(Modifier.fillMaxSize()) {
        // 暗场
        Box(Modifier.fillMaxSize().background(Color(0xFF5A4628).copy(alpha = .22f * p)))

        // 手势面：← 下一页，→ 上一页 / 合上，点书外 → 合上回书架
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(book.volume) {
                    awaitEachGesture {
                        val down  = awaitFirstDown(requireUnconsumed = false)
                        val start = down.position
                        val startDp = Offset(start.x.toDp().value, start.y.toDp().value)
                        val onBook = OpenBookRect.contains(startDp)

                        var last = start
                        while (true) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            last = ch.position
                            if (!ch.pressed) break
                        }
                        val dx = (last.x - start.x).toDp().value
                        val dy = (last.y - start.y).toDp().value
                        val tap   = abs(dx) < 8 && abs(dy) < 8
                        val swipe = abs(dx) > 40 && abs(dx) > abs(dy)

                        when {
                            swipe -> if (dx < 0) {
                                if (current < paperSheets) current++
                            } else {
                                if (current > 1) current-- else onClose()
                            }
                            tap && !onBook -> onClose()
                        }
                    }
                }
        ) {
            Book(p = p, current = current, book = book, compiling = isCompiling)
        }
    }
}

/* ================= 底部控制条：自动整理开关 + 注入 System 按钮 ================= */

@Composable
private fun ControlBar(
    bookCount: Int,
    switchOn: Boolean,
    injectOn: Boolean,
    onSwitch: () -> Unit,
    onInject: () -> Unit
) {
    Row(
        Modifier
            .offset(22.dp, 700.dp)
            .fillMaxWidth()
            .padding(end = 22.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 自动整理（额外请求端总开关）
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(on = switchOn, onToggle = onSwitch)
            Spacer(Modifier.width(8.dp))
            BasicText(
                "自动整理",
                style = TextStyle(fontSize = 12.sp, fontFamily = FontFamily.Serif, color = TextColor.copy(alpha = .85f))
            )
        }
        // ★ 注入 System 按钮
        InjectToggle(on = injectOn, bookCount = bookCount, onToggle = onInject)
    }
}

/** 小拨动开关（自动整理） */
@Composable
private fun Switch(on: Boolean, onToggle: () -> Unit) {
    val bg   by animateColorAsState(if (on) Color(0xFFC9A678) else Color(0xFFD9D3C4), tween(250), label = "swBg")
    val knob by animateFloatAsState(if (on) 18f else 2f, tween(250, easing = Ease), label = "swKnob")
    Box(
        Modifier
            .size(34.dp, 18.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(bg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggle
            )
    ) {
        Box(
            Modifier
                .offset(knob.dp, 2.dp)
                .size(14.dp)
                .shadow(1.dp, CircleShape)
                .background(Color.White, CircleShape)
        )
    }
}

/** ★ 注入 System 按钮：把整架书拼进 system 的开关（有书才有得注） */
@Composable
private fun InjectToggle(on: Boolean, bookCount: Int, onToggle: () -> Unit) {
    val bg by animateColorAsState(
        if (on) Color(0xFFC9A678) else Color(0xFFEFE9DA),
        tween(250),
        label = "injBg"
    )
    val fg = if (on) Color(0xFF4A3A22) else Color(0xFF8A8272)
    val pulse = rememberInfiniteTransition(label = "injPulse")
    val glow by pulse.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "injGlow"
    )
    Box(
        Modifier
            .clip(RoundedCornerShape(15.dp))
            .background(bg)
            .border(1.dp, Color(0xFFB49A6E), RoundedCornerShape(15.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggle
            )
            .padding(horizontal = 13.dp, vertical = 7.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 状态灯：注入中呼吸发光
            Box(
                Modifier
                    .size(8.dp)
                    .background(
                        if (on) Color(0xFF8F6B33).copy(alpha = .55f + .45f * glow) else Color(0xFFC4BCA8),
                        CircleShape
                    )
            )
            Spacer(Modifier.width(7.dp))
            BasicText(
                when {
                    on            -> "已注入 · $bookCount 册"
                    bookCount > 0 -> "注入 System · $bookCount 册"
                    else          -> "注入 System"
                },
                style = TextStyle(
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Serif,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    color = fg
                )
            )
        }
    }
}

/* ================= 书：书脊固定在容器 x = 14 处 ================= */

@Composable
private fun Book(p: Float, current: Int, book: BookSpec?, compiling: Boolean) {
    val s  = lerp(1.6f, 2.2f, p)          // 合上 1.6 倍，翻开 2.2 倍
    val tx = lerp(0f, 129f, p)            // 翻开后书脊落在屏幕中线 x=180
    val ty = lerp(0f, 221f, p)

    Box(
        Modifier
            .offset(20.dp, 60.dp)
            .size(120.dp)
            .zIndex(2f)
            .graphicsLayer {
                transformOrigin = TransformOrigin(0f, 0f)
                translationX = tx.dp.toPx()
                translationY = ty.dp.toPx()
                scaleX = s; scaleY = s
            }
    ) {
        LoosePage(p, 12f, 10f, 70f, 92f, rotClosed = -6f, color = Color(0xFFFBF8F0), z = 1f)
        LoosePage(p, 18f, 6f,  72f, 90f, rotClosed = 4f,  color = Color(0xFFFDFCF7), z = 2f)
        BackBoard(p)
        UnderLines(p)
        Bookmark(p, compiling)

        // 页数由装订结果决定（页数不设上限）；只渲染当前页附近的纸张 + 封面
        val paperSheets = (book?.pages?.size ?: 3).coerceAtLeast(1)
        val indices = (listOf(0) + (maxOf(1, current - 3)..minOf(paperSheets, current + 4)).toList()).distinct()
        indices.forEach { i ->
            key(i) {
                Sheet(
                    i = i,
                    current = current,
                    p = p,
                    book = book,
                    page = book?.pages?.getOrNull(i - 1)
                )
            }
        }
    }
}

/* ---- 底下散乱的纸 ---- */
@Composable
private fun LoosePage(p: Float, cx: Float, cy: Float, w: Float, h: Float, rotClosed: Float, color: Color, z: Float) {
    val shape = RoundedCornerShape(2.dp)
    Box(
        Modifier
            .zIndex(z)
            .offset(lerp(cx, 14f, p).dp, lerp(cy, 8f, p).dp)
            .size(w.dp, h.dp)
            .graphicsLayer { rotationZ = lerp(rotClosed, 0f, p) }
            .shadow(2.dp, shape)
            .background(color, shape)
            .border(1.dp, Color.Black.copy(alpha = .05f), shape)
    )
}

/* ---- 后封板：合上是歪着的一块皮，翻开撑满左右两页 ---- */
@Composable
private fun BackBoard(p: Float) {
    val shape = RoundedCornerShape(4.dp)
    Box(
        Modifier
            .zIndex(3f)
            .offset(lerp(12f, -64f, p).dp, 7.dp)
            .size(lerp(76f, 152f, p).dp, 94.dp)
            .graphicsLayer {
                rotationZ = lerp(5f, 0f, p)
                translationX = lerp(3f, 0f, p).dp.toPx()
                translationY = lerp(2f, 0f, p).dp.toPx()
            }
            .shadow(3.dp, shape)
            .background(Brush.linearGradient(listOf(Color(0xFFEFE7D3), Color(0xFFE2D6BA))), shape)
    )
}

/* ---- 书下面的两行横线 ---- */
@Composable
private fun UnderLines(p: Float) {
    Column(
        Modifier
            .zIndex(4f)
            .offset(lerp(16f, -64f, p).dp, 114.dp)
            .width(lerp(64f, 152f, p).dp)
    ) {
        Box(Modifier.fillMaxWidth().height(2.dp).background(LineColor, RoundedCornerShape(1.dp)))
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth(.62f).height(2.dp).background(LineColor, RoundedCornerShape(1.dp)))
    }
}

/* ---- 书签：整理中呼吸闪烁，提示额外请求在飞 ---- */
private val RibbonShape = GenericShape { size, _ ->
    val w = size.width; val h = size.height
    moveTo(0f, .30f * h)
    lineTo(.35f * w, .10f * h); lineTo(.70f * w, .35f * h); lineTo(w, .15f * h)
    lineTo(.90f * w, .65f * h); lineTo(.65f * w, .85f * h); lineTo(.30f * w, .60f * h)
    lineTo(0f, .75f * h)
    close()
}

@Composable
private fun Bookmark(p: Float, compiling: Boolean) {
    val pulse = rememberInfiniteTransition(label = "bookmarkPulse")
    val glow by pulse.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(550), RepeatMode.Reverse),
        label = "glow"
    )
    Box(
        Modifier
            .zIndex(if (p > .5f) 30f else 5f)
            .offset(lerp(58f, 15f, p).dp, lerp(56f, 58f, p).dp)
            .size(58.dp, 22.dp)
            .graphicsLayer {
                transformOrigin = TransformOrigin(0f, .5f)
                rotationZ = lerp(6f, 72f, p)          // 合上向右垂，翻开从书缝垂下
                alpha = if (compiling) 1f - .5f * glow else 1f
            }
            .shadow(2.dp, RibbonShape)
            .background(Brush.linearGradient(listOf(Color(0xFFDFC08D), Color(0xFFCAAA73))), RibbonShape)
    )
}

/* ---- 纸张：正面 = 右页，背面 = 翻过去后的左页；i = 0 为封面 ---- */
@Composable
private fun Sheet(i: Int, current: Int, p: Float, book: BookSpec?, page: BookPageSpec?) {
    val isCover = i == 0
    val flipped = i < current

    val rotY by animateFloatAsState(
        targetValue = if (flipped) -180f else 0f,
        animationSpec = tween(
            durationMillis = 900,
            delayMillis = if (isCover && flipped) 300 else 0,   // 飞到中央后再翻开封面
            easing = FlipEase
        ),
        label = "flip$i"
    )

    val tiltClosed = if (isCover) -3f else Tilts[(i - 1) % Tilts.size]
    val tilt = lerp(tiltClosed, 0f, p)

    // 正在翻动的纸压在最上面；右边越靠前越在上，左边越后翻越在上
    val inTransit = if (flipped) rotY > -179.5f else rotY < -0.5f
    val z = when {
        inTransit -> 60f
        flipped   -> 10f + (i - (current - 4)).coerceAtLeast(0)
        else      -> 10f + (current + 5 - i)
    }
    val showBack = rotY <= -90f

    val x = if (isCover) 12f else 14f
    val y = if (isCover) 7f  else 8f
    val w = if (isCover) 76f else 72f
    val h = if (isCover) 94f else 92f

    Box(
        Modifier
            .zIndex(z)
            .offset(x.dp, y.dp)
            .size(w.dp, h.dp)
            .graphicsLayer {
                transformOrigin = TransformOrigin(0f, .5f)   // 绕左侧书脊翻
                rotationY = rotY
                rotationZ = tilt
                cameraDistance = 16f
            }
    ) {
        if (!showBack) {
            FrontFace(isCover, p, book, page)
        } else {
            // 翻过去后水平镜像，把背面内容翻正
            Box(Modifier.fillMaxSize().graphicsLayer { scaleX = -1f }) {
                BackFace(isCover, p, page, book)
            }
        }
    }
}

@Composable
private fun FrontFace(isCover: Boolean, p: Float, book: BookSpec?, page: BookPageSpec?) {
    if (isCover) {
        val shape = RoundedCornerShape(topStart = 3.dp, bottomStart = 3.dp, topEnd = 5.dp, bottomEnd = 5.dp)
        Box(
            Modifier
                .fillMaxSize()
                .shadow(3.dp, shape)
                .background(Brush.linearGradient(listOf(Color(0xFFF2EBD9), Color(0xFFE5DAC0))), shape)
                .border(1.dp, Color.White.copy(alpha = .7f), shape)
        ) {
            // 书脊压痕
            Box(
                Modifier
                    .fillMaxHeight()
                    .width(10.dp)
                    .background(
                        Brush.horizontalGradient(
                            0f to Color(0xFFCABDA2), .7f to Color(0xFFE5DAC0), 1f to Color(0xFFCABDA2)
                        )
                    )
            )
            // 合上的封面：标题整理到一条长横线上（字压线），描述缩成短线、以一截省略号吊住
            Column(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 15.dp, top = 26.dp, end = 5.dp)
            ) {
                Box(
                    Modifier.fillMaxWidth().height(13.dp),
                    contentAlignment = Alignment.BottomStart
                ) {
                    BasicText(
                        book?.title?.takeIf { it.isNotBlank() } ?: "通识",
                        style = TextStyle(
                            fontSize = 6.5.sp,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF8A744E)
                        )
                    )
                }
                // 长横线：标题压着的那条（封面全宽）
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFBEA87E)))
                Spacer(Modifier.height(4.dp))
                // 短描述线：封面只给前一截 + "…"（估算：5sp 在 56dp 封宽上约容 10 字）
                BasicText(
                    book?.desc?.takeIf { it.isNotBlank() }?.take(10)?.plus("…") ?: "…",
                    style = TextStyle(
                        fontSize = 5.sp,
                        fontFamily = FontFamily.Serif,
                        color = Color(110, 90, 60, (255 * .75f).toInt())
                    )
                )
            }
        }
    } else {
        val shape = RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp)
        Box(
            Modifier
                .fillMaxSize()
                .shadow(2.dp, shape)
                .background(Color.White, shape)
                .border(1.dp, Color.Black.copy(alpha = .05f), shape)
        ) {
            PageContent(page)
            Gutter(alignEnd = false, alpha = .28f * p)
        }
    }
}

@Composable
private fun BackFace(isCover: Boolean, p: Float, page: BookPageSpec?, book: BookSpec?) {
    if (isCover) {
        val shape = RoundedCornerShape(topStart = 5.dp, bottomStart = 5.dp, topEnd = 3.dp, bottomEnd = 3.dp)
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(listOf(Color(0xFFF7F1E3), Color(0xFFECE3CF))), shape)
        ) {
            // 扉页同样是横线纸——翻开后：标题退成一条短横线，描述在长横线上一字不少地展开
            Lines()
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(start = 12.dp, top = 20.dp, end = 10.dp, bottom = 6.dp)
            ) {
                // 标题打开后：短横线一条（册名）
                Box(
                    Modifier.fillMaxWidth().height(14.dp),
                    contentAlignment = Alignment.BottomStart
                ) {
                    BasicText(
                        book?.title?.takeIf { it.isNotBlank() } ?: "通识",
                        style = TextStyle(
                            fontSize = 6.sp,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            color = TextColor
                        )
                    )
                }
                // 描述打开后：完整展开，逐行压在长横线上（装订侧自动换行，不省略）
                book?.desc?.let { d ->
                    wrapToLines(d).forEach { line ->
                        Box(
                            Modifier.fillMaxWidth().height(7.dp),
                            contentAlignment = Alignment.BottomStart
                        ) {
                            BasicText(
                                line,
                                style = TextStyle(fontSize = 5.5.sp, fontFamily = FontFamily.Serif, color = TextColor)
                            )
                        }
                    }
                }
                // 索引：每次总结末尾 AI 给出的高信息熵关键词（存档全量；扉页是取景框，展示前几个）
                val entropy = book?.entropyWords.orEmpty()
                if (entropy.isNotEmpty()) {
                    Box(
                        Modifier.fillMaxWidth().height(11.dp),
                        contentAlignment = Alignment.BottomStart
                    ) {
                        BasicText(
                            "索 引",
                            style = TextStyle(
                                fontSize = 6.sp,
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.Bold,
                                color = TextColor
                            )
                        )
                    }
                    Box(Modifier.width(22.dp).height(1.dp).background(LineColor))
                    Spacer(Modifier.height(3.dp))
                    BasicText(
                        entropy.take(4).joinToString(" "),   // 空格回显，呼应 AI 侧的分隔契约
                        style = TextStyle(
                            fontSize = 5.5.sp,
                            fontFamily = FontFamily.Serif,
                            lineHeight = 7.sp,
                            color = TextColor
                        )
                    )
                }
            }
        }
    } else {
        val shape = RoundedCornerShape(topStart = 3.dp, bottomStart = 3.dp)
        Box(
            Modifier
                .fillMaxSize()
                .shadow(2.dp, shape)
                .background(Color.White, shape)
                .border(1.dp, Color.Black.copy(alpha = .05f), shape)
        ) {
            PageContent(page)
            Gutter(alignEnd = true, alpha = .28f * p)
        }
    }
}

/**
 * 页面内容：把整理出的每一行精确压印在一条横线上。
 * 网格契约（与 Lines() 必须一致）：
 *  - 第一条横线在页顶 y=20dp（Lines 顶部 padding 14 + 起画偏移 6），步长 7dp；
 *  - 每个文字格固定高 7dp、内容贴格子底部（BottomStart）→ 文字基线恰好压在格子下缘那条横线上；
 *  - 页标题占两格（14dp）压在第 2 条横线上；一页正文恰好放 7 行（装订侧已按此自动分页，AI 不参与）。
 */
@Composable
private fun PageContent(page: BookPageSpec?) {
    Box(Modifier.fillMaxSize()) {
        Lines()
        if (page != null) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(start = 12.dp, top = 20.dp, end = 10.dp, bottom = 6.dp)
            ) {
                // 标题格 = 两条横线高，文字贴底 → 压在第 2 条横线上
                Box(
                    Modifier.fillMaxWidth().height(14.dp),
                    contentAlignment = Alignment.BottomStart
                ) {
                    BasicText(
                        page.title.ifBlank { "· · ·" },
                        style = TextStyle(
                            fontSize = 6.sp,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            color = TextColor
                        )
                    )
                }
                // 正文：一行一格、格底对齐 → 每行正文都坐在自己那条横线上
                page.lines.forEach { line ->
                    Box(
                        Modifier.fillMaxWidth().height(7.dp),
                        contentAlignment = Alignment.BottomStart
                    ) {
                        BasicText(
                            line,
                            style = TextStyle(fontSize = 5.5.sp, fontFamily = FontFamily.Serif, color = TextColor)
                        )
                    }
                }
            }
        }
    }
}

/** 页面底色：纯粹的横线 */
@Composable
private fun Lines() {
    Canvas(
        Modifier
            .fillMaxSize()
            .padding(start = 11.dp, top = 14.dp, end = 10.dp, bottom = 6.dp)   // 底 padding 收到 6dp：让第 7 条可写横线落在页内（与 PageContent 的 7 行正文互为契约）
    ) {
        val step = 7.dp.toPx()
        val lw   = 1.dp.toPx()
        var y    = 6.dp.toPx()
        while (y + lw <= size.height) {
            drawRect(Ink, Offset(0f, y), Size(size.width, lw))
            y += step
        }
    }
}

/** 书缝阴影 */
@Composable
private fun BoxScope.Gutter(alignEnd: Boolean, alpha: Float) {
    val colors = listOf(Color.Black.copy(alpha = alpha), Color.Transparent)
    Box(
        Modifier
            .align(if (alignEnd) Alignment.CenterEnd else Alignment.CenterStart)
            .fillMaxHeight()
            .width(8.dp)
            .background(Brush.horizontalGradient(if (alignEnd) colors.reversed() else colors))
    )
}
