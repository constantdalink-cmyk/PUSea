package org.example.project

// ==============================================================================
// 画布文件 7/10：AIAssistTheme —— 基础层（平均解耦 1/3）【补缺修复文件】
//
// 修复背景：App45 / App46 编译报大量 Unresolved reference ——
//   Mono / PixelText / ChevronArrow / AnimatedReveal / MonoTextArea /
//   GhostLineField / OptionChip / AiAssistStore / AiAssistEntry /
//   AssistReplyMode / DEFAULT_* / BLOCK_W_DP / BLOCK_H_DP /
//   ERASE_FREEZE_AT / ERASE_RESUME_DP / TRACK_SHRINK_AT_DP /
//   computeTearMetrics
// 全部无声明。原因：三层解耦（Theme ← Components ← Screen）里基础层文件
// 没有随 App45 / App46 / App37 一起拷入工程。本文即该基础层，补齐全部缺口。
//
// ⚠ 本文件【只声明】上面这批缺失符号；以下符号经编译错误列表反证
//   【工程里已存在】（使用处零报错），本文绝不重复声明，否则 Redeclaration：
//     - uiText / UiText（含 AssistCard* / AssistReplyMode* / AssistDrawerCaption* /
//       AssistCanvasWordmark / AiAssistSystem 条目，App9_localization.kt）
//     - SlideDownContainer（全工程共享容器，「方案 A」签名）
//   若工程里另有旧版 AIAssistTheme.kt 残留：以本文为准删掉旧版（各自唯一）。
//
// ⚠ 二轮修复补记：DrawerSlot / SliderBlock 首轮被误判为"工程已存在"——
//   首份错误日志从 App45:481 起贴，481 之前的使用处（抽屉编排区 425-455 行）
//   报错被截掉没看到。整合版拷入后设置区报 Unresolved，证实二者从未在
//   工程任何文件声明 —— 现补入本文（5b/6b 节），文件头清单已同步。
//
// ⚠ 三轮修复（持久化接入）：AiAssistStore 由内存实现升级为 AndroidFileStorage
//   落盘版——新增 initialize(StorageProvider)（MainActivity 启动注入并回读），
//   update {} 写回时同步落盘；AssistReplyMode / AiAssistEntry / AiAssistState /
//   AiAssistStore 去掉 internal（MainActivity 在 androidApp 模块跨模块调用），
//   三个数据类型加 @Serializable。调用方（update / state.xxx）接口零改动。
//
// 本文内容：
//   1) Mono：单色主题色板（白底黑字、淡灰线、深灰字幕）
//   2) PixelText：基础文字（BasicText，无 material 依赖）
//   3) ChevronArrow：120° 钝角箭头（仅箭头头，无箭头身）
//   4) AnimatedReveal：高度动画展开（弹簧推开下方内容）
//   5) MonoTextArea / GhostLineField：等宽输入框（正文多行 / 单行虚写占位）
//   5b) DrawerSlot / SliderBlock：抽屉黑幕槽位（0→1 盖住 / 1→2 揭开）+
//       滑动调节（标签 + 数值 + 轨道把手，落点即数值）【二轮修复补入】
//   6) OptionChip：回复模式选项片（选中 = 黑底白字反转）
//   7) AssistReplyMode 枚举 + AiAssistEntry / AiAssistState / AiAssistStore
//      （状态提升与持久化通道；已接入 AndroidFileStorage 落盘）
//   8) DEFAULT_* 虚写占位字幕（输入框空值时显示的例子）
//   9) 撕开几何常量 + computeTearMetrics 纯函数引擎（与 HTML 版一一对应）
//
// 依赖注意：shared 模块未引入 material —— 全部 foundation / ui API
// （BasicText / BasicTextField / Canvas / SubcomposeLayout），
// 动画只用 animation-core。
// ==============================================================================

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

// ==================== 1) 主题色板 ====================
// 单色（Mono）体系：白底黑字、淡灰线、深灰标题、更灰的说明与虚写字幕

internal object Mono {
    /** 深灰标题 / 竖排字幕 */
    val title = Color(0xFF1A1A1E)

    /** 淡灰横线 / 竖线 / 轨道线 */
    val line = Color(0xFFD9D9DF)

    /** 卡片白底 */
    val cardBg = Color(0xFFFFFFFF)

    /** 卡片黑边（常态） */
    val cardBorder = Color(0xFF1A1A1E)

    /** 卡片黑边（展开时加强） */
    val cardBorderStrong = Color(0xFF000000)

    /** 卡片正文黑字 */
    val cardText = Color(0xFF111114)

    /** 卡片深灰说明 */
    val cardDim = Color(0xFF6E6E76)

    /** 虚写占位字幕（更灰） */
    val cardFaint = Color(0xFFB9B9C2)
}

// ==================== 2) 基础文字 ====================

/**
 * 像素风文字原语：BasicText 直出（无 material）。
 * 全部场景层 / 组件层文字（标题、说明、字幕、输入框文字）统一走这里，
 * 保证字族 / 行高 / 字重口径一致。
 */
@Composable
internal fun PixelText(
    text: String,
    color: Color,
    fontSize: TextUnit,
    fontWeight: FontWeight = FontWeight.Normal,
    fontFamily: FontFamily = FontFamily.Monospace,
    lineHeight: TextUnit = TextUnit.Unspecified,
    modifier: Modifier = Modifier
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(
            color = color,
            fontSize = fontSize,
            fontWeight = fontWeight,
            fontFamily = fontFamily,
            lineHeight = lineHeight
        )
    )
}

// ==================== 3) 120° 钝角箭头 ====================

/**
 * 仅箭头头（无箭头身）的 120° 钝角 chevron，指向右 ">"。
 * 卡片右侧展开箭头用 —— 展开时由调用方整体旋转 90°（纯旋转，
 * 旋转前后形状一致）。与场景层带杆的 UpArrow 不同，这支没有杆。
 */
@Composable
internal fun ChevronArrow(
    modifier: Modifier = Modifier,
    color: Color = Mono.title,
    strokeWidthDp: Dp = 2.dp
) {
    Canvas(modifier) {
        val strokeWidth = strokeWidthDp.toPx()
        // 120° 钝角：两翼与水平轴各夹 60°。
        // 翼长 L 由画布高反推（垂直方向刚好撑满）：dy = L·sin60 = h/2
        val armLen = size.height / 0.8660254f
        val dx = armLen * 0.5f          // cos60
        val dy = armLen * 0.8660254f    // sin60
        val tipX = size.width
        val tipY = size.height / 2f
        drawLine(
            color = color,
            start = Offset(tipX, tipY),
            end = Offset((tipX - dx).coerceAtLeast(0f), tipY - dy),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(tipX, tipY),
            end = Offset((tipX - dx).coerceAtLeast(0f), tipY + dy),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
}

// ==================== 4) 高度动画展开 ====================

/**
 * 展开区高度动画：visible 切换时在 0 ↔ 内容实测高度之间动画，
 * 把下方内容顺滑推下 / 收回。展开用弹簧（卡片"弹簧展开"），
 * 收起用 tween 不过冲。
 *
 * 实现：SubcomposeLayout 实测内容全高 → 按动画进度裁出显示高度；
 * progress 在组合期读取（快照状态），进度变化触发重组 → 重测重布局。
 */
@Composable
internal fun AnimatedReveal(
    visible: Boolean,
    content: @Composable () -> Unit
) {
    val progress = remember { Animatable(if (visible) 1f else 0f) }
    LaunchedEffect(visible) {
        if (visible) {
            progress.animateTo(1f, spring(dampingRatio = 0.82f, stiffness = 260f))
        } else {
            progress.animateTo(0f, tween(240, easing = FastOutSlowInEasing))
        }
    }
    // 组合期读取进度：动画每帧重组 → SubcomposeLayout 拿到新 fraction 重布局
    val fraction = progress.value
    SubcomposeLayout(
        Modifier
            .fillMaxWidth()
            .clipToBounds()
    ) { constraints ->
        val placeables = subcompose(Unit) { content() }.map { measurable ->
            measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
        }
        val fullHeight = placeables.maxOfOrNull { it.height } ?: 0
        val shownHeight = (fullHeight * fraction).roundToInt().coerceAtLeast(0)
        layout(constraints.maxWidth, shownHeight) {
            placeables.forEach { it.place(0, 0) }
        }
    }
}

// ==================== 5) 输入框 ====================

/**
 * 多行正文输入框（等宽字族）：固定高度、内部可纵向滚动、
 * 空值显示虚写占位字幕。光标与正文同色。
 */
@Composable
internal fun MonoTextArea(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    pixelFont: FontFamily,
    heightDp: Dp,
    fontSize: TextUnit = 12.sp,
    textColor: Color = Mono.cardText,
    placeholderColor: Color = Mono.cardFaint
) {
    val innerScroll = rememberScrollState()
    val style = TextStyle(
        color = textColor,
        fontSize = fontSize,
        lineHeight = 18.sp,
        fontFamily = pixelFont
    )
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .height(heightDp)
            .verticalScroll(innerScroll),
        textStyle = style,
        cursorBrush = SolidColor(textColor),
        decorationBox = { innerTextField ->
            Box {
                if (value.isEmpty()) {
                    BasicText(placeholder, style = style.copy(color = placeholderColor))
                }
                innerTextField()
            }
        }
    )
}

/**
 * 单行（矮）/ 双行（高）虚写输入框：卡片标题与说明用。
 * 空值显示虚写占位字幕（例子），点击即写；占位与输入文字共用
 * 同一字号 / 字重 / 行高，切换无跳动。
 */
@Composable
internal fun GhostLineField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    pixelFont: FontFamily,
    heightDp: Dp,
    fontSize: TextUnit,
    fontWeight: FontWeight = FontWeight.Normal,
    textColor: Color,
    placeholderColor: Color,
    lineHeight: TextUnit = TextUnit.Unspecified
) {
    val style = TextStyle(
        color = textColor,
        fontSize = fontSize,
        fontWeight = fontWeight,
        fontFamily = pixelFont,
        lineHeight = lineHeight
    )
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .height(heightDp),
        textStyle = style,
        cursorBrush = SolidColor(textColor),
        decorationBox = { innerTextField ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    BasicText(
                        placeholder,
                        style = style.copy(color = placeholderColor),
                        maxLines = 2
                    )
                }
                innerTextField()
            }
        }
    )
}

// ==================== 6) 选项片 ====================

/**
 * 回复模式选项片：未选中 = 白底黑字黑边；选中 = 黑底白字（内容反转），
 * 与整体"白底黑字、反转强调"的语言一致。
 */
@Composable
internal fun OptionChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    pixelFont: FontFamily
) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
    Box(
        Modifier
            .clip(shape)
            .background(if (selected) Color(0xFF000000) else Color(0xFFFFFFFF))
            .border(1.dp, Color(0xFF000000), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            label,
            style = TextStyle(
                color = if (selected) Color(0xFFFFFFFF) else Color(0xFF000000),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = pixelFont
            )
        )
    }
}

// ==================== 5b/6b) 抽屉原语：黑幕槽位 + 滑动调节 ====================
// 【二轮修复补入】SettingsDrawer 的三个抽屉位直接调用这两个组件。

/**
 * 抽屉黑幕槽位：内容（滑动调节）常驻，一块纯黑面板按 barProgress 扫过遮挡 ——
 *  - bar = 0   ：面板宽度 0（右缘贴左缘，设置完全可见）
 *  - 0 → 1    ：右缘从左往右扫出盖住（左缘始终贴边、撑满不留缝）
 *  - bar = 1   ：完全盖住（SettingsDrawer 此刻广播 onCoveredChange(true)）
 *  - 1 → 2    ：右缘往回扫（向左收拢），露出滑动调节设置
 * 高度自动跟随内容（matchParentSize），宽度按 fillMaxWidth(frac) 比例展开。
 */
@Composable
internal fun DrawerSlot(
    barProgress: Float,
    content: @Composable () -> Unit
) {
    val frac = when {
        barProgress <= 0f -> 0f
        barProgress <= 1f -> barProgress
        else -> (2f - barProgress).coerceIn(0f, 1f)
    }
    Box(Modifier.fillMaxWidth().clipToBounds()) {
        content()
        if (frac > 0f) {
            Box(Modifier.matchParentSize()) {
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxWidth(frac)
                        .fillMaxHeight()
                        .background(Color(0xFF000000))
                )
            }
        }
    }
}

/**
 * 滑动调节：上行 = 标签（左）+ 数值（右）；下行 = 轨道细线 + 方形把手（像素风）。
 * 在轨道区按住左右拖动：落点即数值（onFractionChange，0..1），调用方
 * （SettingsDrawer）映射成 Temperature / Max Tokens / Top P 的实际量纲，
 * 经 AiAssistStore 落盘。
 */
@Composable
internal fun SliderBlock(
    label: String,
    fraction: Float,
    display: String,
    onFractionChange: (Float) -> Unit,
    pixelFont: FontFamily
) {
    val clamped = fraction.coerceIn(0f, 1f)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 17.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PixelText(
                label,
                color = Mono.title,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = pixelFont
            )
            Spacer(Modifier.weight(1f))
            PixelText(
                display,
                color = Mono.cardDim,
                fontSize = 11.sp,
                fontFamily = pixelFont
            )
        }
        Spacer(Modifier.height(8.dp))
        BoxWithConstraints(Modifier.fillMaxWidth().height(18.dp)) {
            val knobSize = 14.dp
            val knobX = (maxWidth - knobSize) * clamped
            // 轨道细线
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Mono.line)
            )
            // 方形把手（纯黑，像素风格，无圆角）
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = knobX)
                    .size(knobSize)
                    .background(Color(0xFF000000))
            )
            // 拖拽面（覆盖整个轨道区）：落点即数值
            Box(
                Modifier
                    .matchParentSize()
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragStart = { offset ->
                                onFractionChange((offset.x / size.width).coerceIn(0f, 1f))
                            },
                            onHorizontalDrag = { change, _ ->
                                onFractionChange(
                                    (change.position.x / size.width).coerceIn(0f, 1f)
                                )
                            }
                        )
                    }
            )
        }
    }
}

// ==================== 7) 状态与持久化通道 ====================

/** 回复模式：枚举名仅用于持久化，显示标签走 uiText 七语词典。
 *  public + @Serializable：被 AiAssistState（落盘）引用，且 MainActivity 跨模块初始化 */
@Serializable
enum class AssistReplyMode { Stream, Full }

/** 撕开一轮产生的自定义请求词条（标题 / 说明 / 正文） */
@Serializable
data class AiAssistEntry(
    val title: String = "",
    val description: String = "",
    val text: String = ""
)

/**
 * AI Assist 画布全部持久化状态：六卡编辑内容 + 回复模式 +
 * 三个滑杆数值 + 撕开词条列表。copy 合并写回，各文件只动自己的字段。
 */
@Serializable
data class AiAssistState(
    val systemPrompt: String = "",
    val identity: String = "",
    val replyMode: AssistReplyMode = AssistReplyMode.Stream,
    val behaviorRules: String = "",
    val outputFormat: String = "",
    val examples: String = "",
    val temperature: Float = 0.7f,
    val maxTokens: Int = 2048,
    val topP: Float = 0.9f,
    val entries: List<AiAssistEntry> = emptyList()
)

/**
 * 持久化通道：场景层 / 组件层统一经此读写，不互相暴露内部状态。
 * 已接入平台持久化（AndroidFileStorage）：initialize 注入存储并同步回读存档，
 * update {} 写回时同步落盘。接口与内存版完全一致，全部调用方
 * （update { it.copy(...) } / state.xxx）零改动。
 * public（不加 internal）：MainActivity 在 androidApp 模块，跨模块调用 initialize。
 */
object AiAssistStore {
    private const val KEY = "ai_assist_state_v1"
    private var storage: StorageProvider? = null
    private val json = Json { ignoreUnknownKeys = true }

    var state by mutableStateOf(AiAssistState())
        private set

    /** MainActivity 启动时注入持久化存储，并同步回读全部 AI Assist 状态 */
    fun initialize(provider: StorageProvider) {
        storage = provider
        provider.load(KEY)?.let { raw ->
            runCatching { state = json.decodeFromString<AiAssistState>(raw) }
        }
    }

    /** copy 合并写回：只覆盖传入字段，不影响其它字段；改动同步落盘 */
    fun update(transform: (AiAssistState) -> AiAssistState) {
        state = transform(state)
        runCatching { storage?.save(KEY, json.encodeToString(state)) }
    }
}

// ==================== 8) 虚写占位字幕 ====================
// 输入框空值时显示的例子；点击即写，留空失焦重现

internal val DEFAULT_SYSTEM_PROMPT =
    "e.g. You are a concise assistant. Answer in short bullets, no filler."

internal val DEFAULT_IDENTITY =
    "e.g. Name: Friday. Tone: calm, direct, slightly dry."

internal val DEFAULT_BEHAVIOR_RULES =
    "e.g. Never invent sources. Ask before assuming. Keep replies under 200 words."

internal val DEFAULT_OUTPUT_FORMAT =
    "e.g. Markdown. Code in fenced blocks. Tables only when comparing."

internal val DEFAULT_EXAMPLES =
    "e.g. Q: Summarize this log.  A: 3 bullets — error, cause, fix."

// ==================== 9) 撕开几何：常量 + 纯函数引擎 ====================
// 与 HTML 版一一对应（px / dp 口径一致）。场景层（App37 下册）只负责
// 手势与状态，全部位置 / 裁剪量由这里的纯函数算出，便于单测与对齐。

/** 黑色滑块宽（52dp）—— 原黑块与镜像黑块共用 */
internal val BLOCK_W_DP = 52.dp

/** 黑色滑块高（76dp）—— 与折叠白卡等高 */
internal val BLOCK_H_DP = 76.dp

/**
 * 清除块冻结位置（1.1/4 处 ≈ 14.3dp）：pullAccum 在此之前清除区 1:1 跟手，
 * 之后冻结保持遮挡；引擎内再往左补 2px 密合
 */
internal val ERASE_FREEZE_AT = 14.3.dp

/**
 * 清除块解锁点（p = 4/3×52 ≈ 69.33dp）：新黑滑块【最右】（W+36−p）到达
 * 原黑块 2/3 处（W−33.33）时解锁，此前清除区一直冻结
 */
internal val ERASE_RESUME_DP = 69.33.dp

/**
 * 白矩形（槽位）开始收缩的阈值（2×52 = 104dp）：新黑滑块最右完全到达
 * 原滑块最左（overDead ≥ 104）后槽位才开始 1:1 向左收缩
 */
internal val TRACK_SHRINK_AT_DP = 104.dp

/** computeTearMetrics 的纯数据结果（单位全是 px，调用方自行 toDp） */
internal class TearMetrics(
    /** 镜像黑块左缘（容器坐标） */
    val mirrorLeftPx: Float,
    /** 白矩形槽位宽度 */
    val trackWidthPx: Float,
    /** 原黑块右缘裁剪量（clipRect 从右往左裁） */
    val erasePx: Float,
    /** 镜像黑块自身右缘裁剪量 */
    val mirrorErasePx: Float
)

/**
 * 撕开几何引擎（纯函数，无状态，可单测）：
 *
 * 1) mirrorLeftPx：镜像块左缘 = 原块右缘（W−16dp）− pullAccum，1:1 跟手，
 *    可一直拉到最左缘 x=0（pullAccum 拉满 tearTravel = W−16dp）；
 * 2) erasePx（原黑块裁剪）三段式：
 *    - p ≤ freeze(+2px)：1:1 跟手（刚激活时贴合撕开）；
 *    - freeze < p < resume：冻结在 freeze 值（中段保持遮挡，防穿帮）；
 *    - p ≥ resume：重新 1:1 追赶，直到裁满整个块宽（完全交接给镜像块）；
 *    - revealed（已撕完）：直接全裁。
 * 3) mirrorErasePx（镜像块自身裁剪）：A 全裁（p=0 不可见）→
 *    B 右缘渐进露出（visible = 左起 p px，像从原块右缘被抽出来）→
 *    C p ≥ 块宽后完全露出自由滑动；
 * 4) trackWidthPx：随 extend 从 0 长到全宽（W−68dp = W−16−52）；
 *    overDead = pullAccum − 104dp > 0 后再 1:1 向左收缩。
 */
internal fun computeTearMetrics(
    containerWidthPx: Float,
    extend: Float,
    tearSlide: Float,
    pullAccum: Float,
    tearTravelPx: Float,
    eraseFreezePx: Float,
    eraseResumePx: Float,
    trackShrinkAtPx: Float,
    blockWidthPx: Float,
    revealed: Boolean,
    density: Density
): TearMetrics {
    val e = extend.coerceIn(0f, 1f)
    val p = pullAccum.coerceAtLeast(0f)

    val insetPx = with(density) { 16.dp.toPx() }   // 原块右缘与容器右缘的间隙
    val startPx = with(density) { 24.dp.toPx() }   // 原块默认向左探出量
    val spanPx = with(density) { 44.dp.toPx() }    // 探出位到完全拉出的总行程补偿

    // 1) 镜像块左缘：起点 = 原块右缘（W−16dp），随 pullAccum 1:1 左移
    val mirrorLeftPx = (containerWidthPx - insetPx) - p

    // 2) 原黑块右缘裁剪：跟手 → 冻结（+2px 密合）→ 解锁追赶 → 全裁
    val freezePx = eraseFreezePx + 2f
    val erasePx = when {
        revealed -> blockWidthPx
        p <= freezePx -> p.coerceAtMost(blockWidthPx)
        p < eraseResumePx -> freezePx.coerceAtMost(blockWidthPx)
        else -> (freezePx + (p - eraseResumePx)).coerceAtMost(blockWidthPx)
    }

    // 3) 镜像块自身右缘裁剪：A 全裁 → B 渐进露出 → C 全露
    val mirrorErasePx = if (revealed) 0f else (blockWidthPx - p).coerceIn(0f, blockWidthPx)

    // 4) 槽位宽度：随 extend 生长（e=1 → W−68 全宽）；overDead>0 后 1:1 收缩
    val growPx = (containerWidthPx - spanPx) * e - startPx
    val overDeadPx = (tearSlide * tearTravelPx - trackShrinkAtPx).coerceAtLeast(0f)
    val trackWidthPx = (growPx - overDeadPx).coerceAtLeast(0f)

    return TearMetrics(
        mirrorLeftPx = mirrorLeftPx,
        trackWidthPx = trackWidthPx,
        erasePx = erasePx,
        mirrorErasePx = mirrorErasePx
    )
}
