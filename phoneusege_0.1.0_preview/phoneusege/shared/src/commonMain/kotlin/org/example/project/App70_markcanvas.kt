package org.example.project

/* ════════════════════════════════════════════════════════════════════
 * KraftSubtitleCanvasV2 —— 记忆编辑画布 · 重构版(分步搭建中)
 *
 * 与 KraftSubtitleBookmarkCanvas.kt 同源；旧文件保留不删，供对照。
 * 复用蓝画布(SpaceBlueGrayPullDownCanvas)的挂载接口：
 *   (visible, onClose, pixelFont, cardId)，cardId = 唤起来源卡片编号
 *
 * 当前步骤：wipe 线改为两段式"先旋转 · 后平移"(与 htm 演示同效)：
 *           线体 50° 起步、中程缓慢续转，一旦与屏幕上下边垂直
 *           (线角 = 90°)即锁死旋转，交接为纯左移直至扫出左缘。
 *
 * 本步重构(翻页错觉 · 双层架构)：错觉不再是可开关的旗标，而是结构不变量——
 *           · 叙事引擎 TurnNarrativeLayer：按导演"声称"的方向渲染盖顶页
 *             对垫底页的过场。引擎自身不做任何方向计算，拿到叙事就如实上演；
 *           · 翻页导演 requestPage(唯一知情者)：把真实走向编码进
 *             (wipeFromPage, page)，并计算 谎言 = 真相取反、排好层叠——
 *             真实在增 → 声称反翻：新页盖顶沿已擦半边"扫入"盖住旧页(真·反翻)；
 *             真实在减 → 声称正翻：旧页盖顶沿未擦半边"被扫走"露出新页；
 *           · 计数器(EdgeTab 页码)：运动方向与纸面的声称保持一致，
 *             落定的数字永远是真的页码——运动是谎言，内容是真相。
 *           正着翻新页从上面扫入盖住旧页(反翻动作)、反着翻旧页被扫走
 *           露出新页(正翻动作)：纸永远朝着真相的反方向翻。
 *
 * 三层结构(自下而上)：
 *   ① 画布根     只负责自右侧滑入 620ms / 收回 360ms(GreenPeelOffEasing)
 *                zIndex 31；同时是唯一手势入口，按状态路由：
 *                  · 闭合时左滑 → 擦拭开启
 *                  · 闭合时右滑 → 收回整块画布(120dp 阈值)
 *                  · 开启时右滑 → 已禁用(不再回放黑画布)
 *   ② 记忆画布   静止底层(MEMORY 占位 + 呼吸点)，不参与任何位移，
 *                从擦过的区域显露
 *   ③ 黑画布     静止原地，被 wipe 线裁剪消失；线体顺时针 50° 起步、
 *                中程 smoothstep 缓慢续转，与屏幕上下垂直即停，
 *                交接为纯左移直至扫出左缘；openFrac ≈ 0.7 时擦净
 *                ("完全不漏")；字幕钉在其上一同被擦除；
 *                满程 = 画布全宽 + 48dp，越界阻尼，半程阈值吸附
 *
 * 兼容性约定(沿用本工程已验证写法)：
 *   · 手势只用 detectHorizontalDragGestures(单参 onDragStart)
 *   · 跟手 = 状态直写(零协程，不可能"没跑")；松手吸附 =
 *     LaunchedEffect(snapTarget) 驱动 animate()，key 变化自动取消
 *     旧动画，杜绝新旧写入源打架导致的抖动
 *   · graphicsLayer 只用 translationX 和 alpha；旋转走纯 2D
 *     顶点数学(Path 旋转四边形)，不碰 3D 管线、不依赖
 *     DrawScope.translate/rotate
 *   · 裁剪用 object:Shape + Outline.Generic(已验证)
 *   · tween 一律具名参数
 * ════════════════════════════════════════════════════════════════════ */

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** 滑动吸附曲线 —— CSS cubic-bezier(0.2, 0.9, 0.3, 1) */
private val CoverEasingV2 = CubicBezierEasing(0.2f, 0.9f, 0.3f, 1f)

/** 擦拭完成点：openFrac 到达此值时 wipe 线已扫出左缘、完全擦净；余下行程为阻尼空程 */
private const val WIPE_SPAN = 0.7f

/** wipe 线本体旋转(度，顺时针) —— 相对原始对角线 */
private const val WIPE_LINE_ROT = 50f

/** 中程继续缓慢旋转量(度，顺时针)；与屏幕上下垂直时被截断，通常转不满 */
private const val WIPE_MID_ROT = 40f

/** 旋转窗口起点：行程前 20% 稳住 50° 不转 */
private const val WIPE_ROT_FROM = 0.2f

/** 旋转窗口终点(垂直截断发生在此之前) */
private const val WIPE_ROT_TO = 0.8f

/** 垂直后纯左移，越出左缘的屏宽占比，保证完全擦净 */
private const val WIPE_SLIDE_OVERSHOOT = 0.1f

/** 牛皮纸记忆层配色与尺寸常量(与 htm 逐值同构) */
private val KraftPaper = Color(0xFFC7A47A)
private val KraftCream = Color(0xEBF4F1E7)
private val BeltGold = Color(0xFFE4B45C)

/** 燕尾"皮革染"色板：点击燕尾在六色间轮换，首色 = 原橄榄墨 */
private val TabDyes = listOf(
    Color(0xFF3D3A2E), // 橄榄墨(原色)
    Color(0xFF6B3527), // 牛血红
    Color(0xFF2F4639), // 苔原绿
    Color(0xFF31405E), // 普鲁士蓝
    Color(0xFF593A50), // 梅子紫
    Color(0xFF6E5326)  // 焦糖棕
)

/** 皮带 JSON 排版：2 空格缩进，皮面展示用 */
private val PRETTY_JSON = Json { prettyPrint = true; prettyPrintIndent = "  " }

/** 手势路由：0 未定向 / 1 开启(左滑) / 3 收回画布(闭合时右滑)；开启后右滑不再回放黑画布 */
private const val GESTURE_NONE = 0
private const val GESTURE_OPEN = 1
private const val GESTURE_CLOSE_CANVAS = 3

/**
 * 四缘出血(印刷"出血"做法)：牛皮纸画布与下方蓝画布是同尺寸兄弟层，
 * graphicsLayer 栅格化时层边缘的亚像素/抗锯齿会让底层蓝画布从四缘
 * 渗出极细蓝缝。本修饰符把子级实测尺寸每边扩 bleed、整体外扩渲染并
 * 放置于 -bleed——四缘之外只有纸面(与黑画布)溢出屏幕，任何亚像素
 * 错位都露不出蓝画布；对父级汇报的布局尺寸不变，不影响任何外部布局。
 * 内部所有计算(滑出满程、EdgeTab、wipe 形)基于实测尺寸，自动适应。
 */
private fun Modifier.bleed(bleed: Dp) = layout { measurable, constraints ->
    val b = bleed.roundToPx()
    val expanded = Constraints(
        minWidth = if (constraints.hasBoundedWidth) {
            constraints.minWidth + 2 * b
        } else {
            constraints.minWidth
        },
        maxWidth = if (constraints.hasBoundedWidth) {
            constraints.maxWidth + 2 * b
        } else {
            constraints.maxWidth
        },
        minHeight = if (constraints.hasBoundedHeight) {
            constraints.minHeight + 2 * b
        } else {
            constraints.minHeight
        },
        maxHeight = if (constraints.hasBoundedHeight) {
            constraints.maxHeight + 2 * b
        } else {
            constraints.maxHeight
        }
    )
    val placeable = measurable.measure(expanded)
    layout(constraints.maxWidth, constraints.maxHeight) {
        placeable.place(-b, -b)
    }
}

/**
 * V2 画布入口 —— 蓝画布左滑卡片唤出。
 *
 * @param visible   是否展开
 * @param onClose   请求收回画布(闭合时右滑越阈值)
 * @param pixelFont 全局像素字体
 * @param cardId    唤起来源卡片编号(conversationId)
 */
@Composable
fun BoxScope.KraftSubtitleCanvasV2(
    visible: Boolean,
    onClose: () -> Unit,
    pixelFont: FontFamily,
    cardId: String
) {
    // 1f = 停靠在右侧屏外, 0f = 完全覆盖
    val anim = remember { Animatable(1f) }
    // 点上三角：整块画布上滑退回蓝画布
    val exitUp = remember { Animatable(0f) }
    var dismissing by remember { mutableStateOf(false) }

    LaunchedEffect(visible) {
        if (visible) {
            exitUp.snapTo(0f)
            dismissing = false
        }
        anim.animateTo(
            targetValue = if (visible) 0f else 1f,
            animationSpec = tween(
                durationMillis = if (visible) 620 else 360,
                easing = GreenPeelOffEasing
            )
        )
    }

    val rendered = visible || anim.value < 0.999f
    if (!rendered) return

    // ── 黑画布左移量：0 = 原位(闭合)，-maxShiftPx = 满程(开启) ──
    // 唯一渲染源。跟手 = 事件里同步直写(零协程)；
    // 吸附 = snapTarget 置目标值，由下方 LaunchedEffect 逐帧回写
    var memShiftPx by remember { mutableStateOf(0f) }
    var coverOpen by remember { mutableStateOf(false) }
    var snapTarget by remember { mutableStateOf<Float?>(null) }
    // 画布实测宽度(px)：布局后测得，用于计算滑出满程
    var canvasWidthPx by remember { mutableStateOf(0f) }
    val density = LocalDensity.current

    // 满程 = 画布全宽 + 48dp 余量(连阴影也完全滑出)：
    // 黑画布整体滑出左缘，记忆画布完全显露
    val overshootPx = with(density) { 48.dp.toPx() }
    val maxShiftPx =
        if (canvasWidthPx > 0f) {
            canvasWidthPx + overshootPx
        } else {
            with(density) { 360.dp.toPx() }
        }
    // 开/合阈值 = 半程；收回画布阈值 = 120dp；方向判定死区 = 12dp
    val openThresholdPx = maxShiftPx * 0.5f
    val closeThresholdPx = with(density) { 120.dp.toPx() }
    val deadZonePx = with(density) { 12.dp.toPx() }

    // 开启进度 —— 组合级读取(必被快照追踪)：memShiftPx 每次写入都
    // 触发重组，③ 黑画布裁剪每帧必得最新值，与手势直写严格同帧
    val openFrac =
        (-memShiftPx / maxShiftPx).coerceIn(0f, 1f)

    // 吸附动画：key = snapTarget —— 目标值一变(含置 null)，
    // Compose 自动取消旧动画协程并重启，无需手动 cancel，
    // 从根本上杜绝"旧吸附回写"与"新拖动直写"两个写入源打架
    LaunchedEffect(snapTarget) {
        val target = snapTarget ?: return@LaunchedEffect
        animate(
            initialValue = memShiftPx,
            targetValue = target,
            animationSpec = tween(
                durationMillis = 450,
                easing = CoverEasingV2
            )
        ) { value, _ ->
            memShiftPx = value
        }
        snapTarget = null
    }

    // ── 记忆纸数据源(提升到根：页码状态需同时供 MemoryContent 与 EdgeTab) ──
    val currentId by ConversationStore.currentConversationId.collectAsState()
    val liveMessages by ConversationStore.messages.collectAsState()
    val conversation = ConversationStore.findConversation(cardId)
    val allMessages = remember(liveMessages, currentId, cardId) {
        (if (cardId == currentId) liveMessages else conversation?.messages.orEmpty())
            .filter { it.role == "user" || it.role == "assistant" }
    }
    // 一页 16 个气泡
    val pageCount = maxOf(1, (allMessages.size + 15) / 16)
    var page by remember(cardId) { mutableStateOf(1) }
    val safePage = page.coerceIn(1, pageCount)

    // ── 翻页擦拭：直接复用 wipe 线(先旋转·后平移)作为翻页过场 ──
    // 新页垫底、旧页盖顶，同一条 wipe 线把旧页扫掉显露新页。
    // ── 翻页导演：真相与叙事分两层，错觉是结构不变量而非旗标 ──
    // 真相层(导演独有)：页码增减。target 与当前页的差值即真相本身；
    //       clamp 后编码进 (wipeFromPage, page) 对：盖顶 = 旧页，垫底 = 新页。
    // 叙事层：导演算 谎言 = 真相取反，并据此排层叠、定轨迹：
    //       · 声称正翻(真实后退)：旧页盖顶"被扫走"、新页垫底显露(未擦半边)；
    //       · 声称反翻(真实前进)：新页盖顶"扫入"盖住旧页(已擦半边)，真·反翻。
    //       叙事引擎 TurnNarrativeLayer 只拿"声称方向 + 盖顶页 + 进度 0→1"，
    //       照声称上演，不碰真实走向。
    // 错觉：纸永远朝真相的反方向翻——正着翻新页扫入(反翻动作)，
    //       反着翻旧页扫走(正翻动作)；落定的页码是唯一证词。
    // 翻页进度沿用"状态直写 + LaunchedEffect(key) 驱动 animate()"——与 memShiftPx/snapTarget、
    // EdgeTab 的 slidePx 同族已验证写法：每帧回写必触发重组(杜绝"数字变了但纸面不扫")，
    // key 变化自动取消旧动画，锁随动画结束确定释放，不会卡死
    var pageWipeT by remember { mutableStateOf(0f) }
    var wipeFromPage by remember { mutableStateOf<Int?>(null) }
    val wipeScope = rememberCoroutineScope()
    val requestPage: (Int) -> Unit = { target ->
        val clamped = target.coerceIn(1, pageCount)
        if (clamped != page && wipeFromPage == null) {
            wipeFromPage = page   // 置锁(编码盖顶页) + 作为下方 LaunchedEffect 的 key 触发开播
            page = clamped        // 垫底换上新页；真实走向 = sign(page - wipeFromPage)，藏在这一层，引擎不得见
        }
    }
    // 翻页擦拭动画：key = wipeFromPage —— 置入旧页即从 0 播到 1，跑完释放锁
    LaunchedEffect(wipeFromPage) {
        if (wipeFromPage == null) return@LaunchedEffect
        animate(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 800,
                easing = CoverEasingV2
            )
        ) { value, _ ->
            pageWipeT = value
        }
        wipeFromPage = null
    }

    // 点上三角 → 整块画布上滑出屏顶，回到蓝画布(与滑入同族曲线)
    val requestDismissUp: () -> Unit = {
        if (!dismissing) {
            dismissing = true
            wipeScope.launch {
                exitUp.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = 360,
                        easing = GreenPeelOffEasing
                    )
                )
                onClose()
            }
        }
    }

    Box(
        modifier = Modifier
            .bleed(4.dp) // 四缘出血：压住层边缘亚像素蓝缝(见 bleed 注释)
            .fillMaxSize()
            .zIndex(31f)
            .graphicsLayer {
                // ① 画布根：只管滑入/收回；上滑退回时 translationY 顶出屏顶
                translationX = anim.value * size.width
                translationY = -exitUp.value * size.height
            }
            // 根底色与牛皮纸同色：闭合时黑画布(CoverPanel 自带满幅黑底)全覆盖，
            // 根底色只用于亚像素边缘兜底——同色即无缝，杜绝牛皮纸四缘细黑缝
            .background(KraftPaper)
            .onSizeChanged { size ->
                // 实测画布宽度 → 滑出满程的基准
                canvasWidthPx = size.width.toFloat()
            }
            // 唯一手势入口：黑画布与记忆层都不带手势，事件直达此处
            .pointerInput(Unit) {
                var acc = 0f
                var gestureMode = GESTURE_NONE
                detectHorizontalDragGestures(
                        onDragStart = { _ ->
                            acc = 0f
                            gestureMode = GESTURE_NONE
                            // 手指重新接管：吸附动画当场作废(key 变化即取消)
                            snapTarget = null
                        },
                    onDragEnd = {
                        when (gestureMode) {
                            GESTURE_OPEN -> {
                                if (memShiftPx <= -openThresholdPx) {
                                    // 越过半程：黑画布吸附到满程
                                    coverOpen = true
                                    snapTarget = -maxShiftPx
                                } else {
                                    // 未越过：弹回原位(闭合)
                                    snapTarget = 0f
                                }
                            }
                            GESTURE_CLOSE_CANVAS -> {
                                if (acc >= closeThresholdPx) {
                                    onClose()
                                }
                            }
                        }
                        gestureMode = GESTURE_NONE
                    },
                    onDragCancel = {
                        if (gestureMode == GESTURE_OPEN) {
                            val target =
                                if (memShiftPx <= -maxShiftPx * 0.5f) {
                                    -maxShiftPx
                                } else {
                                    0f
                                }
                            coverOpen = target != 0f
                            snapTarget = target
                        }
                        gestureMode = GESTURE_NONE
                    },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            acc += dragAmount
                            // 累计越过死区才定向(首帧抖动不会定错向)
                            if (gestureMode == GESTURE_NONE) {
                                gestureMode = when {
                                    // 开启后右滑不再回放黑画布：仅闭合时右滑才路由到收回画布
                                    acc > deadZonePx ->
                                        if (coverOpen) GESTURE_NONE
                                        else GESTURE_CLOSE_CANVAS
                                    acc < -deadZonePx -> GESTURE_OPEN
                                    else -> GESTURE_NONE
                                }
                            }
                            when (gestureMode) {
                                GESTURE_OPEN -> {
                                    // 状态直写，不起协程：跟手必然逐帧同步
                                    val raw = memShiftPx + dragAmount
                                    memShiftPx = when {
                                        // 只允许向左(开启方向)
                                        raw >= 0f -> 0f
                                        // 越过满程后加阻尼
                                        raw < -maxShiftPx ->
                                            -maxShiftPx +
                                                (raw + maxShiftPx) * 0.3f
                                        else -> raw
                                    }
                                }
                                GESTURE_CLOSE_CANVAS -> {
                                    // 收回画布：只累计，松手结算
                                }
                                GESTURE_NONE -> {
                                    // 死区内：不动作
                                }
                            }
                        }
                )
            }
    ) {
        // ② 记忆画布：静止底层，不参与任何位移
        MemoryContent(
            openFrac = openFrac,
            messages = allMessages,
            page = safePage,
            cardId = cardId,
            pixelFont = pixelFont,
            onDismissUp = requestDismissUp,
            wipeFromPage = wipeFromPage,
            wipeT = pageWipeT
        )

        // 右缘燕尾标签：属第二层画布(记忆层与黑画布之间，z=5)
        EdgeTab(
            pixelFont = pixelFont,
            cardId = cardId,
            pageCount = pageCount,
            currentPage = safePage,
            onPageChange = requestPage
        )

        // ③ 黑画布：静止原地，被 wipe 线裁剪消失(字幕随之)
        CoverPanel(
            openFrac = openFrac,
            cardId = cardId,
            pixelFont = pixelFont
        )
    }
}

/* ─────────────────────────── ② 记忆画布内容 ─────────────────────────── */

@Composable
private fun MemoryContent(
    openFrac: Float,
    messages: List<ConversationMessageRecord>,
    page: Int,
    cardId: String,
    pixelFont: FontFamily,
    onDismissUp: () -> Unit,
    wipeFromPage: Int?,
    wipeT: Float
) {
    // 关键修复：仅在擦净(openFrac ≥ WIPE_SPAN)后才挂载纵向滚动。
    // 滚动层是几何命中的(不受黑画布视觉遮挡影响)，闭合时若带手势会抢占
    // 父级横向 wipe 检测，导致"黑画布滑不动"；闭合时不挂滚动即根治
    val scrollEnabled = openFrac >= WIPE_SPAN
    val scrollState = rememberScrollState()

    // 两份切片：目的页(新页)与来源页(旧页)
    val destMessages = remember(messages, page) {
        messages.drop((page - 1) * 16).take(16)
    }
    val srcMessages = remember(messages, wipeFromPage) {
        wipeFromPage?.let { messages.drop((it - 1) * 16).take(16) }
    }
    val inTransition = wipeFromPage != null
    // 真相：页码是否真实在增。导演永远声称相反方向(谎言 = 真相取反)
    val claimsForward = !(page > (wipeFromPage ?: page))
    // 真·反翻 = 层叠反转：声称反翻(真实前进)时旧页垫底、新页盖顶扫入盖住旧页；
    // 声称正翻(真实后退)时维持常态——新页垫底、旧页盖顶被扫走
    val reverseAnim = inTransition && !claimsForward
    // 基页(垫底)：反翻垫来源页，其余垫目的页
    val basePage = if (reverseAnim) wipeFromPage!! else page
    val pageMessages = if (reverseAnim) srcMessages!! else destMessages
    val count = pageMessages.size
    // 皮带开关：按消息 id 键控，翻页不重置
    val beltOpen = remember { mutableStateMapOf<Long, Boolean>() }
    // 气泡编辑缓冲：写透存档；本地缓冲避免 Flow 回写抖动光标
    val textBuf = remember { mutableStateMapOf<Long, String>() }
    // 皮带批注缓冲：默认 = 该消息 JSON，手改后为本地临时批注
    val beltBuf = remember { mutableStateMapOf<Long, String>() }
    // 移动页(盖顶)：正翻声称 = 来源页被扫走；反翻声称 = 目的页扫入
    val movingPage = if (claimsForward) wipeFromPage else page
    val movingMessages = remember(messages, movingPage) {
        movingPage?.let { messages.drop((it - 1) * 16).take(16) }
    }

    Box(
        Modifier
            .fillMaxSize()
            .clipToBounds()
            .background(KraftPaper)  // 牛皮纸纯色，无打光
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val band = maxHeight / 32f       // 带高 = 视口高/32
            val contentBands = 3 * count + 4 // 头 2 带 + 每条消息 3 带 + 尾 2 带；16 条 → 52(与 htm 同)
            // maxWidth 是 BoxWithConstraintsScope 的属性，嵌套 Box 里拿不到隐式接收者，
            // 先在本层捕获成局部 val 供下方使用
            val paperWidth = maxWidth

            Box(
                Modifier
                    .fillMaxWidth()
                    .then(
                        if (scrollEnabled) Modifier.verticalScroll(scrollState)
                        else Modifier
                    )
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(band * contentBands)
                ) {
                    // 信纸横线：逐条画，跳过每个气泡上下最近的两条，
                    // 末带留空 → 底边无线；气泡浮于无线空白
                    val skipLines = remember(count) {
                        buildSet {
                            for (i in 0 until count) { add(3 * i + 3); add(3 * i + 4) }
                        }
                    }
                    Canvas(Modifier.fillMaxSize()) {
                        val bandPx = size.height / contentBands
                        for (b in 1 until contentBands) {
                            if (b in skipLines) continue
                            drawLine(
                                color = Color.Black, // 横线：纯黑，与脑标墨色同族
                                start = Offset(0f, b * bandPx - 1f),
                                end = Offset(size.width, b * bandPx - 1f),
                                strokeWidth = 1f
                            )
                        }
                    }

                    // 顶部抬头：整组基准居中、明显左移到 -25dp(中线以左)——"档案"与编号收拢到 '-' 两侧
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(band)
                            .offset(x = (-25).dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ARCHIVE",
                            color = Color.Black,
                            fontSize = 10.sp,
                            fontFamily = pixelFont,
                            letterSpacing = 3.sp,
                            maxLines = 1
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "-",
                            color = Color.Black,
                            fontSize = 10.sp,
                            fontFamily = pixelFont,
                            maxLines = 1
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = cardId.removePrefix("conversation_").ifBlank { "--" },
                            color = Color.Black,
                            fontSize = 10.sp,
                            fontFamily = pixelFont,
                            letterSpacing = 1.sp,
                            maxLines = 1
                        )
                    }

                    // 脑子标志只属第一页(信纸抬头)，跟随垫底的基页：
                    // 反翻时基页是来源页，来源页的脑子留在底层、被扫入的新页盖住
                    if (basePage == 1) {
                        BrainMark(
                            Modifier
                                .align(Alignment.TopCenter)
                                .offset(y = band * 1.55f)
                                .size(band * 1.15f)
                                .graphicsLayer { rotationZ = 180f }
                        )
                    }

                    // 最后一条横线正中的向上实心黑三角：点击整块画布上滑退回蓝画布
                    // 44×30dp 触控热区包住小三角；按压缩放反馈；仅在纸面擦净后可点
                    val triInteraction = remember { MutableInteractionSource() }
                    val triPressed by triInteraction.collectIsPressedAsState()
                    Box(
                        Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = band * (3 * count + 3) - 6.5.dp - 9.dp)
                            .size(44.dp, 30.dp)
                            .graphicsLayer {
                                val s = if (triPressed) 0.78f else 1f
                                scaleX = s
                                scaleY = s
                                alpha = if (triPressed) 0.6f else 1f
                            }
                            .clickable(
                                interactionSource = triInteraction,
                                indication = null,
                                enabled = scrollEnabled,
                                onClick = onDismissUp
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        TriangleUp(Modifier.size(15.dp, 13.dp))
                    }

                    // 空会话占位
                    if (count == 0) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .offset(y = band * 2f)
                                .height(band),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "· no messages ·",
                                color = Color(0x593B2A1A),
                                fontSize = 9.sp,
                                fontFamily = pixelFont,
                                letterSpacing = 2.sp
                            )
                        }
                    }

                    // 每条消息一个槽：user 居左、assistant 居右；气泡显正文、皮带滑出显 JSON
                    PageSlots(
                        pageMessages = pageMessages,
                        band = band,
                        paperWidth = paperWidth,
                        beltOpen = beltOpen,
                        coverOpen = openFrac >= WIPE_SPAN,
                        pixelFont = pixelFont,
                        cardId = cardId,
                        textBuf = textBuf,
                        beltBuf = beltBuf
                    )

                    // 翻页擦拭叠层：整块交给叙事引擎 TurnNarrativeLayer——
                    // 导演已把真实走向编码进 (wipeFromPage, page)、排好层叠
                    // (反翻 → 新页盖顶扫入；正翻 → 旧页盖顶被扫走)并给出声称方向。
                    // 引擎照声称上演、从不问真相，过场中槽级手势全关
                    if (movingMessages != null) {
                        TurnNarrativeLayer(
                            movingMessages = movingMessages,
                            movingIsFirstPage = movingPage == 1,
                            progress = wipeT,
                            claimsForward = claimsForward,
                            band = band,
                            paperWidth = paperWidth,
                            beltOpen = beltOpen,
                            textBuf = textBuf,
                            beltBuf = beltBuf,
                            cardId = cardId,
                            pixelFont = pixelFont
                        )
                    }
                }
            }
        }
    }
}

/* ─────────────────────────── 翻页错觉引擎(叙事层) ─────────────────────────── */

/**
 * 叙事引擎——翻页错觉的"演出层"。
 * 按导演声称的方向(claimsForward)渲染盖顶页(movingMessages)，完成对垫底页的过场：
 *  · claimsForward = true (声称正翻、真实后退)：旧页盖顶"被扫走"——
 *    裁剪取未擦半边(mirror = true, keep = true)，盖顶页沿推进轨迹被擦除、显露垫底新页；
 *  · claimsForward = false (声称反翻、真实前进)：新页盖顶"扫入"——
 *    裁剪取已擦半边(mirror = false, keep = false)，盖顶页沿反翻轨迹生长、盖住垫底旧页。
 *    页从上面翻回来盖住当前页，这才是真正的反着翻。
 *
 * 契约(真相与谎言全是导演的事)：
 *  · 引擎不做任何方向计算、也不知页码真实走向，拿到叙事就如实上演；
 *  · 导演 requestPage 永远算 谎言 = 真相取反：真实在增就声称反着翻、
 *    真实在减就声称正着翻。因此纸永远朝真相的反方向翻；
 *  · 层叠也由导演排好：声称反翻时新页在上(扫入)、声称正翻时旧页在上(被扫走)；
 *  · progress 来自导演 LaunchedEffect(key) 的逐帧回写，与动画严格同帧；
 *  · 盖顶页槽位一律 coverOpen = false：过场不抢占一丝拖动。
 */
@Composable
private fun TurnNarrativeLayer(
    movingMessages: List<ConversationMessageRecord>,
    movingIsFirstPage: Boolean,
    progress: Float,
    claimsForward: Boolean,
    band: androidx.compose.ui.unit.Dp,
    paperWidth: androidx.compose.ui.unit.Dp,
    beltOpen: MutableMap<Long, Boolean>,
    textBuf: MutableMap<Long, String>,
    beltBuf: MutableMap<Long, String>,
    cardId: String,
    pixelFont: FontFamily
) {
    // 照叙事上演：
    //  · 声称"正翻" → 盖顶页"被扫走"：未擦半边收缩(mirror = true, keep = true)
    //  · 声称"反翻" → 盖顶页"扫入"：已擦半边生长(mirror = false, keep = false)
    // 谎言由导演决定，引擎只负责演
    val wipeShape = wipeClipShape(progress, mirror = claimsForward, keep = claimsForward)
    Box(
        Modifier
            .fillMaxSize()
            // 阴影在裁剪之外：沿翻页擦拭边缘扩散到新页上，增强"扫过"深度；
            // 擦净即随叠层卸载，无残留
            .shadow(
                elevation = 8.dp,
                shape = wipeShape,
                ambientColor = Color(0x663C220E),
                spotColor = Color(0x663C220E)
            )
            .clip(wipeShape)
    ) {
        // 盖顶页若是第一页：脑子随擦拭线一同被扫走/盖住，过场不穿帮
        if (movingIsFirstPage) {
            BrainMark(
                Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = band * 1.55f)
                    .size(band * 1.15f)
                    .graphicsLayer { rotationZ = 180f }
            )
        }
        PageSlots(
            pageMessages = movingMessages,
            band = band,
            paperWidth = paperWidth,
            beltOpen = beltOpen,
            coverOpen = false,
            pixelFont = pixelFont,
            cardId = cardId,
            textBuf = textBuf,
            beltBuf = beltBuf
        )
    }
}

/** 一页的槽位集合：基页与叙事引擎 TurnNarrativeLayer 共用同一套排布 */
@Composable
private fun BoxScope.PageSlots(
    pageMessages: List<ConversationMessageRecord>,
    band: androidx.compose.ui.unit.Dp,
    paperWidth: androidx.compose.ui.unit.Dp,
    beltOpen: MutableMap<Long, Boolean>,
    coverOpen: Boolean,
    pixelFont: FontFamily,
    cardId: String,
    textBuf: MutableMap<Long, String>,
    beltBuf: MutableMap<Long, String>
) {
    pageMessages.forEachIndexed { i, msg ->
        KraftSlot(
            index = i,
            // 水平镜像:user 居右、assistant 居左;腰带触发/滑出/滑回方向随之全部反向
            isLeft = msg.role == "assistant",
            band = band,
            paperWidth = paperWidth,
            message = msg,
            text = textBuf[msg.id] ?: msg.content,
            onTextChange = { new ->
                // 本地缓冲先行(光标稳)，再写透存档；皮带 JSON 同步刷新
                textBuf[msg.id] = new
                ConversationStore.updateMessageContent(cardId, msg.id, new)
                beltBuf[msg.id] =
                    PRETTY_JSON.encodeToString(msg.copy(content = new))
            },
            beltOn = beltOpen[msg.id] == true,
            onBeltChange = { beltOpen[msg.id] = it },
            beltText = beltBuf[msg.id] ?: PRETTY_JSON.encodeToString(msg),
            onBeltTextChange = { beltBuf[msg.id] = it },
            coverOpen = coverOpen,
            pixelFont = pixelFont
        )
    }
}

/* ─────────────────────────── 条目槽：气泡 + 皮带 ─────────────────────────── */

@Composable
private fun KraftSlot(
    index: Int,
    isLeft: Boolean,
    band: androidx.compose.ui.unit.Dp,
    paperWidth: androidx.compose.ui.unit.Dp,
    message: ConversationMessageRecord,
    text: String,
    onTextChange: (String) -> Unit,
    beltOn: Boolean,
    onBeltChange: (Boolean) -> Unit,
    beltText: String,
    onBeltTextChange: (String) -> Unit,
    coverOpen: Boolean,
    pixelFont: FontFamily
) {
    val slotScroll = rememberScrollState()
    // 槽位写死在两可见线之间(整 3 带高，上下贴线不留缝)，气泡加高只在槽内滚动
    Box(
        Modifier
            .fillMaxWidth()
            .height(band * 3f)
            .offset(y = band * (3f * index + 2f))
            .then(
                // 槽级伸出判定：仅开启态生效；整条槽可起划(朝屏中央方向)
                if (coverOpen && !beltOn) Modifier.pointerInput(Unit) {
                    var acc = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { acc = 0f },
                        onDragEnd = { },
                        onDragCancel = { },
                        onHorizontalDrag = { change, amount ->
                            change.consume()
                            acc += amount
                            val hit = if (isLeft) acc > 24f else acc < -24f
                            if (hit && abs(acc) > 24f) onBeltChange(true)
                        }
                    )
                } else Modifier
            )
    ) {
        // 槽内滚动内容：时间字幕固定占满首带(钉在带底同侧)，气泡从下一带起笔——
        // 与 htm 同节奏：首带是"时间留白"，气泡居中带，末带是"皮带滑道留白"
        // 底部内缩 6dp：气泡(含边框)最多顶到离底横线 6dp 处即被裁切，
        // 无论气泡多高/屏幕多小，边框都不会压住槽底那条可见横线
        Box(
            Modifier
                .fillMaxSize()
                .padding(top = 3.dp, bottom = 6.dp)
                .verticalScroll(slotScroll)
        ) {
            Column(Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().height(band - 3.dp)) {
                    Text(
                        text = message.displayTime + "  ·  " + message.role,
                        color = Color(0x8C3B2A1A),
                        fontSize = 7.sp,
                        fontFamily = pixelFont,
                        letterSpacing = 1.sp,
                        maxLines = 1,
                        modifier = Modifier
                            .align(if (isLeft) Alignment.BottomStart else Alignment.BottomEnd)
                            .padding(horizontal = paperWidth * 0.04f)
                            .padding(bottom = 2.dp)
                    )
                }
                // 气泡：可编辑消息正文(写透存档)，>16/>36 字符自动加宽(66%/86%)
                val len = text.length
                val targetW by animateDpAsState(
                    targetValue = when {
                        len > 36 -> (paperWidth * 0.86f).coerceAtMost(340.dp)
                        len > 16 -> (paperWidth * 0.66f).coerceAtMost(260.dp)
                        else -> (paperWidth * 0.48f).coerceAtMost(200.dp)
                    }
                )
                BasicTextField(
                    value = text,
                    onValueChange = onTextChange,
                    textStyle = TextStyle(
                        fontFamily = pixelFont,
                        fontSize = 10.sp,
                        letterSpacing = 1.sp,
                        lineHeight = 15.sp,
                        color = Color(0xE63B2A1A)
                    ),
                    cursorBrush = SolidColor(Color(0xFF3B2A1A)),
                    modifier = Modifier
                        .align(if (isLeft) Alignment.Start else Alignment.End)
                        .padding(
                            start = if (isLeft) paperWidth * 0.04f else 0.dp,
                            end = if (isLeft) 0.dp else paperWidth * 0.04f
                        )
                        .width(targetW)
                        .defaultMinSize(minHeight = band - 6.dp)
                        .border(1.dp, Color(0x733B2A1A), RoundedCornerShape(10.dp))
                        // 先垫一层不透明纸色：脑标线条不再从气泡透出来，
                        // 再叠原 8% 象牙微光，外观不变
                        .background(KraftPaper, RoundedCornerShape(10.dp))
                        .background(Color(0x14FFFAF0), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    decorationBox = { inner ->
                        Box {
                            if (text.isEmpty()) {
                                Text(
                                    "· note…",
                                    color = Color(0x593B2A1A),
                                    fontSize = 9.sp,
                                    fontFamily = pixelFont,
                                    letterSpacing = 1.sp
                                )
                            }
                            inner()
                        }
                    }
                )
            }
        }

        // 皮带覆盖层：朝屏中央一划伸出，皮面默认本条消息 JSON、可手改批注
        BeltOverlay(
            isLeft = isLeft,
            on = beltOn,
            onOff = { onBeltChange(false) },
            beltText = beltText,
            onBeltTextChange = onBeltTextChange,
            pixelFont = pixelFont
        )
    }
}

/* ─────────────────────────── 牛皮腰带覆盖层 ─────────────────────────── */

@Composable
private fun BeltOverlay(
    isLeft: Boolean,
    on: Boolean,
    onOff: () -> Unit,
    beltText: String,
    onBeltTextChange: (String) -> Unit,
    pixelFont: FontFamily
) {
    // 滑出机制：整条皮带用 translationX 从屏外平移进槽位(比裁剪揭示更可靠)
    // slide: 1 = 完全藏在屏外；0 = 就位(完整占满两横线之间)
    val slide by animateFloatAsState(
        targetValue = if (on) 0f else 1f,
        animationSpec = tween(
            durationMillis = 550,
            easing = CubicBezierEasing(0.2f, 0.8f, 0.3f, 1f)
        )
    )
    if (!on && slide >= 0.999f) return

    // 头/尾约定：头 = 滑出朝向端(圆角 + 包边缝线)，尾 = 屏幕缘端(平切、不缝)。
    // 左带：头在右、尾在左；右带：头在左、尾在右(镜像)
    val headR = 12.dp
    val beltShape = RoundedCornerShape(
        topStart = if (isLeft) 0.dp else headR,
        topEnd = if (isLeft) headR else 0.dp,
        bottomEnd = if (isLeft) headR else 0.dp,
        bottomStart = if (isLeft) 0.dp else headR
    )

    // 皮身 = 裁剪容器的 fillMaxSize：尾贴屏缘、头圆角(结构上不可能不满)，
    // 高度即槽高(上下贴线)。滑出只靠 graphicsLayer 平移自身像素宽：
    // 无 Dp 换算、无 align/offset 算术、无过填余量——满宽是构造保证，不是计算结果
    Box(
        Modifier
            .fillMaxSize()
            .clipToBounds()
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // 左带从左侧滑入、右带从右侧滑入；静止时 translationX = 0 完整覆盖
                    translationX = slide * size.width * if (isLeft) -1f else 1f
                }
                // 头圆角、尾平切；阴影跟随头形增加厚度，clip 让皮面与颗粒不出圆角
                .shadow(
                    elevation = 6.dp,
                    shape = beltShape,
                    ambientColor = Color(0x523C220E),
                    spotColor = Color(0x523C220E)
                )
                .clip(beltShape)
                // 收回判定挂在皮身：文字区(TextField)自带消费，不会误触
                .pointerInput(on) {
                    if (!on) return@pointerInput
                    var acc = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { acc = 0f },
                        onDragEnd = { },
                        onDragCancel = { },
                        onHorizontalDrag = { change, amount ->
                            change.consume()
                            acc += amount
                            // 左带往左划回、右带往右划回
                            if ((isLeft && acc < -24f) || (!isLeft && acc > 24f)) onOff()
                        }
                    )
                }
                .drawBehind {
                    // ── 皮面绘制，与 htm 成品逐层同构 ──
                    val w = size.width
                    val h = size.height
                    // ① 皮身竖向五段渐变(中段提亮一档，贴近成品暖棕)
                    drawRect(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFF66401F), Color(0xFF82512B), Color(0xFF916038),
                                Color(0xFF82512B), Color(0xFF57341B)
                            )
                        )
                    )
                    // ② 左上暖光(28%, -10%)
                    drawRect(
                        Brush.radialGradient(
                            colors = listOf(Color(0x2EFFDEAA), Color(0x00FFDEAA)),
                            center = Offset(w * 0.28f, -h * 0.10f),
                            radius = w * 0.7f
                        )
                    )
                    // ③ 右下暗部(80%, 120%)
                    drawRect(
                        Brush.radialGradient(
                            colors = listOf(Color(0x59281406), Color(0x00281406)),
                            center = Offset(w * 0.80f, h * 1.20f),
                            radius = w * 0.8f
                        )
                    )
                    // ④ 顶缘 1px 内高光
                    drawRect(
                        Color(0x38FFE1B4),
                        topLeft = Offset.Zero,
                        size = Size(w, 1f * density)
                    )
                    // ⑤ 底缘内阴影(4dp 向上渐隐)
                    drawRect(
                        Brush.verticalGradient(
                            colors = listOf(Color(0x001E0F04), Color(0x661E0F04)),
                            startY = h - 4f * density,
                            endY = h
                        ),
                        topLeft = Offset(0f, h - 4f * density),
                        size = Size(w, 4f * density)
                    )
                    // ⑥ 皮面颗粒(固定种子 → 逐帧位置稳定)
                    val rnd = Random(7)
                    repeat(240) {
                        val gx = rnd.nextFloat() * w
                        val gy = rnd.nextFloat() * h
                        drawRect(
                            if (rnd.nextBoolean()) Color(0x14FFFFFF) else Color(0x14000000),
                            topLeft = Offset(gx, gy),
                            size = Size(1.2f * density, 1.2f * density)
                        )
                    }
                    // ⑦ 包头缝线：上边 → 头圆角 → 头竖边 → 头圆角 → 下边，开口朝尾(屏缘)不缝；
                    //    虚线按 density 缩放，段:空 ≈ 6:4。每道先压 1px 暗投影再描金
                    val gold = BeltGold
                    val tackShade = Color(0x731E0F04)
                    val dash = PathEffect.dashPathEffect(
                        floatArrayOf(6f * density, 4f * density), 0f
                    )
                    val sy = 9f * density      // 上下缘内缩
                    val hi = 9f * density      // 头竖边内缩
                    val rs = 6f * density      // 缝线圆角半径
                    val lw = 2f * density
                    val dy = 1f * density
                    val tailX = if (isLeft) 0f else w
                    fun stitch(dyOff: Float): Path = Path().apply {
                        if (isLeft) {
                            // 头在右：尾上缘起笔 → 右行绕头 → 下缘回尾
                            moveTo(tailX, sy + dyOff)
                            lineTo(w - hi - rs, sy + dyOff)
                            arcTo(
                                Rect(w - hi - 2f * rs, sy + dyOff, w - hi, sy + dyOff + 2f * rs),
                                270f, 90f, false
                            )
                            lineTo(w - hi, h - sy - rs + dyOff)
                            arcTo(
                                Rect(w - hi - 2f * rs, h - sy - 2f * rs + dyOff, w - hi, h - sy + dyOff),
                                0f, 90f, false
                            )
                            lineTo(tailX, h - sy + dyOff)
                        } else {
                            // 头在左：尾上缘起笔 → 左行绕头 → 下缘回尾
                            moveTo(tailX, sy + dyOff)
                            lineTo(hi + rs, sy + dyOff)
                            arcTo(
                                Rect(hi, sy + dyOff, hi + 2f * rs, sy + dyOff + 2f * rs),
                                270f, -90f, false
                            )
                            lineTo(hi, h - sy - rs + dyOff)
                            arcTo(
                                Rect(hi, h - sy - 2f * rs + dyOff, hi + 2f * rs, h - sy + dyOff),
                                180f, -90f, false
                            )
                            lineTo(tailX, h - sy + dyOff)
                        }
                    }
                    drawPath(stitch(dy), tackShade, style = Stroke(width = lw, pathEffect = dash))
                    drawPath(stitch(0f), gold, style = Stroke(width = lw, pathEffect = dash))
                }
        ) {
            // 皮面文字：默认本条消息 JSON、可手改批注；金色光标、长文纵向滚动
            val beltScroll = rememberScrollState()
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(
                        start = if (isLeft) 16.dp else 20.dp,
                        end = if (isLeft) 20.dp else 14.dp,
                        top = 13.dp,
                        bottom = 13.dp
                    )
                    .verticalScroll(beltScroll)
            ) {
                BasicTextField(
                    value = beltText,
                    onValueChange = onBeltTextChange,
                    textStyle = TextStyle(
                        fontFamily = pixelFont,
                        fontSize = 9.sp,
                        letterSpacing = 0.sp,
                        lineHeight = 14.sp,
                        color = KraftCream
                    ),
                    cursorBrush = SolidColor(BeltGold),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        Box {
                            if (beltText.isEmpty()) {
                                Text(
                                    "· belt note…",
                                    color = Color(0x61F4F1E7),
                                    fontSize = 8.sp,
                                    fontFamily = pixelFont,
                                    letterSpacing = 1.sp
                                )
                            }
                            inner()
                        }
                    }
                )
            }
        }
    }
}

/* ─────────────────────────── 脑子标志 / 三角符号 ─────────────────────────── */

/** 24 网格双半球脑标：1.5 线宽纯黑、牛皮纸填色压线(与 htm 路径逐值同构) */
@Composable
private fun BrainMark(modifier: Modifier) {
    Canvas(modifier) {
        val s = size.width / 24f
        // 墨色改淡：纯黑 → 70% 暖深棕，与信纸墨族同色、视觉上明显变浅
        val ink = Color(0xB33B2A1A)
        val w = 1.5f * s
        fun stroke(path: Path) = drawPath(path, ink, style =
            androidx.compose.ui.graphics.drawscope.Stroke(
                width = w,
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                join = androidx.compose.ui.graphics.StrokeJoin.Round
            ))
        // 按 htm 的双半球轮廓以三次贝塞尔重建(24 网格)
        val left = Path().apply {
            // 顶边改平(图标整体旋转 180°，路径顶 = 屏幕底)：小圆角接入侧缘
            moveTo(12f * s, 6f * s)
            lineTo(9.6f * s, 6f * s)
            cubicTo(9.2f * s, 6f * s, 9f * s, 6.6f * s, 9f * s, 8f * s)
            cubicTo(6.79f * s, 8f * s, 5f * s, 9.79f * s, 5f * s, 12f * s)
            cubicTo(3.5f * s, 13.5f * s, 3.5f * s, 16.5f * s, 5.5f * s, 17.5f * s)
            // 脑叶在路径底部(旋转后朝上)
            cubicTo(4.5f * s, 19.5f * s, 5.5f * s, 22f * s, 8f * s, 22f * s)
            cubicTo(8.5f * s, 24f * s, 11f * s, 25f * s, 12f * s, 23.5f * s)
            close()
        }
        val right = Path().apply {
            // 顶边改平(旋转后 = 屏幕底)：小圆角接入侧缘
            moveTo(12f * s, 6f * s)
            lineTo(14.4f * s, 6f * s)
            cubicTo(14.8f * s, 6f * s, 15f * s, 6.6f * s, 15f * s, 8f * s)
            cubicTo(17.21f * s, 8f * s, 19f * s, 9.79f * s, 19f * s, 12f * s)
            cubicTo(20.5f * s, 13.5f * s, 20.5f * s, 16.5f * s, 18.5f * s, 17.5f * s)
            // 脑叶在路径底部(旋转后朝上)
            cubicTo(19.5f * s, 19.5f * s, 18.5f * s, 22f * s, 16f * s, 22f * s)
            cubicTo(15.5f * s, 24f * s, 13f * s, 25f * s, 12f * s, 23.5f * s)
            close()
        }
        // 先填牛皮纸压住身后横线，再描黑边
        drawPath(left, KraftPaper)
        drawPath(right, KraftPaper)
        stroke(left)
        stroke(right)
        // 中缝
        stroke(Path().apply {
            moveTo(12f * s, 6.6f * s)
            cubicTo(11f * s, 8f * s, 13f * s, 10f * s, 12f * s, 12.5f * s)
            cubicTo(11f * s, 15f * s, 13f * s, 17f * s, 12f * s, 20.5f * s)
        })
        // 突触短划(左右错半格)
        stroke(Path().apply { moveTo(12f * s, 10.5f * s); lineTo(9.7f * s, 10.5f * s) })
        stroke(Path().apply { moveTo(12f * s, 13.5f * s); lineTo(14.3f * s, 13.5f * s) })
    }
}

/** 最后一条横线正中的向上实心黑三角 */
@Composable
private fun TriangleUp(modifier: Modifier) {
    Canvas(modifier) {
        drawPath(
            Path().apply {
                moveTo(size.width / 2f, 0f)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            },
            Color.Black
        )
    }
}

/* ─────────────────────────── 右缘燕尾标签 ─────────────────────────── */

/**
 * 右缘燕尾缺口标签：属第二层画布(记忆层与黑画布之间)，尺寸 = 整屏宽 × 3 带高，
 * 静态右探 88%(仅燕尾尖露在右缘)；左划跟手滑出、右划滑回，松手半程吸附；
 * 带身中央两行翻页标注：上 "n PAGES"(总页数)、下当前页数字(光学居中、数字对齐)。
 */

/** 翻页箭头：按压缩放 + 透明度反馈；到边界自动禁用变暗 */
@Composable
private fun PageArrow(
    symbol: String,
    enabled: Boolean,
    onClick: () -> Unit,
    pixelFont: FontFamily
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier = Modifier
            .size(26.dp)
            .graphicsLayer {
                alpha = when {
                    !enabled -> 0.28f
                    pressed -> 0.55f
                    else -> 0.92f
                }
                val s = if (pressed) 0.86f else 1f
                scaleX = s
                scaleY = s
                // 箭头符号整体转 180°(按需求：< > 两钮先旋转再换位)
                rotationZ = 180f
            }
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = symbol,
            color = KraftCream,
            fontSize = 12.sp,
            fontFamily = pixelFont,
            maxLines = 1
        )
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun BoxScope.EdgeTab(
    pixelFont: FontFamily,
    cardId: String,
    pageCount: Int,
    currentPage: Int,
    onPageChange: (Int) -> Unit
) {
    // slidePx: 0 = 收回到右探 88%；-maxReveal = 整幅滑出
    var slidePx by remember { mutableStateOf(0f) }
    var snapTo by remember { mutableStateOf<Float?>(null) }
    // 染色：点击燕尾 dyeIndex 只增不减(供动画连续推进)；animateFloatAsState 把这个
    // "色号"平滑推到新值，再按小数部分在色板相邻两色间 lerp 出当前色——换色如染。
    // (commonMain 未提供 animateColorAsState，改走 float 插值，效果等价；
    //  色号连续自增还顺带让末色回首色时正着染、不倒闪)
    var dyeIndex by remember { mutableStateOf(0) }
    val animatedDye by animateFloatAsState(
        targetValue = dyeIndex.toFloat(),
        animationSpec = tween(
            durationMillis = 420,
            easing = CubicBezierEasing(0.2f, 0.8f, 0.3f, 1f)
        )
    )
    val dyeFrom = animatedDye.toInt()
    val dyeFrac = animatedDye - dyeFrom
    val tabColor = lerp(
        TabDyes[dyeFrom % TabDyes.size],
        TabDyes[(dyeFrom + 1) % TabDyes.size],
        dyeFrac
    )

    // maxHeight 只在 BoxWithConstraints 内容 lambda 内可用 → 外层取约束、内层定高
    // 不加 zIndex：靠兄弟顺序定层——本标签声明在黑画布之前，
    // 黑画布后绘制自然压在它之上(闭合时完全遮蔽)，与 htm 的 z 5<10 等效
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .align(Alignment.CenterEnd)
    ) {
        val tabH = maxHeight / 32f * 3f - 2.dp
        Box(
            Modifier
                .fillMaxWidth()
                .height(tabH)
                .graphicsLayer {
                    translationX = size.width * 0.88f + slidePx
                }
                .clip(TabShape)
                .background(tabColor)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { snapTo = null },
                        onDragEnd = {
                            val maxReveal = size.width * 0.88f
                            snapTo = if (slidePx <= -maxReveal * 0.5f) -maxReveal else 0f
                        },
                        onDragCancel = { snapTo = 0f },
                        onHorizontalDrag = { change, amount ->
                            change.consume()
                            val maxReveal = size.width * 0.88f
                            slidePx = (slidePx + amount).coerceIn(-maxReveal, 0f)
                        }
                    )
                }
                // 点击换色：与横拖共存——轻点(无横向位移)轮换染色，
                // 横向拖动仍走滑出/滑回；子级翻页箭头(clickable)优先响应自身点击
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { dyeIndex = dyeIndex + 1 }
                    )
                }
        ) {
            // 半程吸附动画：key 变化自动取消旧动画(沿用本工程已验证写法)
            LaunchedEffect(snapTo) {
                val target = snapTo ?: return@LaunchedEffect
                animate(
                    initialValue = slidePx,
                    targetValue = target,
                    animationSpec = tween(
                        durationMillis = 500,
                        easing = CubicBezierEasing(0.2f, 0.8f, 0.3f, 1f)
                    )
                ) { v, _ -> slidePx = v }
                snapTo = null
            }

            // 翻页器：< 当前页 >；光学居中(补偿左侧燕尾缺口)
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                // "PAGES" 标注：不带编号、加大白色、上移到带身上缘
                Text(
                    text = "PAGES",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontFamily = pixelFont,
                    letterSpacing = 2.sp,
                    maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp)
                        .offset(x = 20.dp)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.offset(x = 20.dp)
                ) {
                    // 左钮(旋转后形似 <，指向左 = 后退)：目标页 -1(真实后退)；
                    // 方向只交给导演编码：真实后退 → 声称正翻，
                    // 纸面前进着推进——以为是往前翻，实际上是往后翻
                    PageArrow(
                        symbol = ">",
                        enabled = currentPage > 1,
                        onClick = { onPageChange(currentPage - 1) },
                        pixelFont = pixelFont
                    )
                    Spacer(Modifier.width(12.dp))
                    // 当前页数字(大、亮)钉在正中、微微上抬 2dp；PAGES 标注已上移到带身上缘
                    // ── 计数器：翻页错觉的第二处谎言 ──
                    // 数字的运动方向与纸面的"声称"保持一致：取页码增减的符号(真相)再取反(谎言)，
                    // 谎言为"正翻"时旧数字左退、新数字右进；谎言为"反翻"则反之。
                    // 落定的数字永远是真的页码——运动是谎言，内容是真相，
                    // 与纸面完全同构：正着翻数字倒退、反着翻数字前进
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AnimatedContent(
                            targetState = currentPage,
                            transitionSpec = {
                                // 真相 = 页码增减的符号；谎言 = 取反；运动方向跟着谎言走
                                val actualForward = targetState > initialState
                                if (!actualForward) {
                                    // 声称"正翻"：新数字从右(后方)进、旧数字往左(前方)退
                                    slideInHorizontally { fullWidth -> fullWidth } + fadeIn() togetherWith
                                        slideOutHorizontally { fullWidth -> -fullWidth } + fadeOut()
                                } else {
                                    // 声称"反翻"：新数字从左(前方)进、旧数字往右(后方)退
                                    slideInHorizontally { fullWidth -> -fullWidth } + fadeIn() togetherWith
                                        slideOutHorizontally { fullWidth -> fullWidth } + fadeOut()
                                }
                            },
                            contentAlignment = Alignment.Center,
                            label = "pageCounter"
                        ) { pageValue ->
                            Text(
                                text = pageValue.toString(),
                                color = KraftCream,
                                fontSize = 11.sp,
                                fontFamily = pixelFont,
                                letterSpacing = 1.sp,
                                maxLines = 1,
                                modifier = Modifier.offset(y = (-2).dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    // 右钮(旋转后形似 >，指向右 = 前进)：目标页 +1(真实前进)；
                    // 方向只交给导演编码：真实前进 → 声称反翻，
                    // 纸面倒退着揭开——以为是往后翻，实际上是往前翻
                    PageArrow(
                        symbol = "<",
                        enabled = currentPage < pageCount,
                        onClick = { onPageChange(currentPage + 1) },
                        pixelFont = pixelFont
                    )
                }
            }
        }
    }
}

/** 燕尾缺口形状：缺口朝左(朝屏内)，平边贴右 */
private object TabShape : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline = Outline.Generic(
        Path().apply {
            moveTo(0f, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            lineTo(size.width * 0.0986f, size.height / 2f)
            close()
        }
    )
}

/* ─────────────────────────── ③ 黑画布(对角擦拭) ─────────────────────────── */

@Composable
private fun CoverPanel(
    openFrac: Float,
    cardId: String,
    pixelFont: FontFamily
) {
    // 擦拭进度：openFrac 到 WIPE_SPAN 即擦净；黑画布保持原始默认朝向，不受翻页动画实验牵连
    val t = (openFrac / WIPE_SPAN).coerceIn(0f, 1f)
    val shape = wipeClipShape(t)

    Box(
        Modifier
            .fillMaxSize()
            // 阴影在裁剪之外：沿对角擦拭边缘扩散，增强"揭开"深度；
            // 擦净(t≥1)即刻关闭阴影——裁剪形巨大、其 8dp 黑影可能在屏缘残留，
            // 点上三角收回时就会"突然冒出来"，归零后彻底杜绝
            .shadow(
                elevation = if (t >= 1f) 0.dp else 8.dp,
                shape = shape,
                ambientColor = Color(0x80000000),
                spotColor = Color(0x80000000)
            )
            .clip(shape)
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment =
                Alignment.CenterHorizontally
        ) {
            // 中央确认字幕：字距收紧(1sp)不显稀疏；允许两行居中，杜绝英文结尾被裁
            Text(
                text = "Are you sure you want to modify my memories?",
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 14.sp,
                fontFamily = pixelFont,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier.padding(horizontal = 40.dp)
            )
            Spacer(Modifier.height(10.dp))
            // 档案编号(方括号包裹，白灰)
            Text(
                text = "[" + cardId.ifBlank { "--" } + "]",
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 10.sp,
                fontFamily = pixelFont,
                letterSpacing = 1.sp,
                maxLines = 1
            )
        }

        // 右缘白色竖条(握持提示)：已按需求移除
    }
}

/* ─────────────────────────── 擦拭裁剪形状(先旋转 · 后平移) ─────────────────────────── */

/** smoothstep m²(3-2m) 的反函数：给定 k 解 m(二分，24 次足够精确) */
private fun invSmoothstep(k: Float): Float {
    var lo = 0f
    var hi = 1f
    for (i in 0 until 24) {
        val mid = (lo + hi) / 2f
        if (mid * mid * (3f - 2f * mid) < k) lo = mid else hi = mid
    }
    return (lo + hi) / 2f
}

/**
 * wipe 裁剪：保留未擦侧(左下)，擦除已擦侧(右上)。
 *
 * wipe 线两段式运动(与 htm 演示逐分支同构)：
 *   段① 线体自 WIPE_LINE_ROT(50°)顺时针起步，中程窗口
 *       [WIPE_ROT_FROM, WIPE_ROT_TO] 内 smoothstep 缓慢续转；
 *       锚点沿对角线 右上 → 左下(端点外扩 5% 防角落抖动)；
 *   段② 线一旦与屏幕上下边垂直(线角 = 90°)，旋转立即锁死，
 *       锚点脱离对角改为纯左移，直到越出左缘 WIPE_SLIDE_OVERSHOOT。
 * 交接点 tV 由 smoothstep 窗口方程反解，角度与锚点双连续。
 * t=0 → 全屏保留；t=1 → 全部擦除。
 */
private fun wipeClipShape(t: Float, mirror: Boolean = false, keep: Boolean = true): Shape = object : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val W = size.width
        val H = size.height
        val len = sqrt(W * W + H * H)
        val R = (W + H) * 2f

        // ── 垂直即停：旋转上限与交接点 ──
        val baseRad = atan2(W, H)     // 未旋转时线相对水平的夹角
        val verticalRot =
            PI.toFloat() / 2f - baseRad // 使线与上下边垂直所需的旋转量
        val rotMax = minOf(
            (WIPE_LINE_ROT + WIPE_MID_ROT) * PI.toFloat() / 180f,
            verticalRot
        )

        // 交接行程 tV：反解 smoothstep 窗口，求旋转恰好到垂直的那一刻
        val kV =
            (rotMax - WIPE_LINE_ROT * PI.toFloat() / 180f) /
                (WIPE_MID_ROT * PI.toFloat() / 180f)
        val tV = when {
            kV <= 0f -> 0f            // 起步即已垂直(横屏兜底)：全程左移
            kV >= 1f -> Float.MAX_VALUE
            else -> WIPE_ROT_FROM +
                (WIPE_ROT_TO - WIPE_ROT_FROM) * invSmoothstep(kV)
        }

        // ── 当前旋转量(屏幕坐标 y 向下，顺时针为正) ──
        val mC = ((t - WIPE_ROT_FROM) / (WIPE_ROT_TO - WIPE_ROT_FROM))
            .coerceIn(0f, 1f)
        val rotNow = minOf(
            (WIPE_LINE_ROT +
                WIPE_MID_ROT * mC * mC * (3f - 2f * mC)) *
                PI.toFloat() / 180f,
            rotMax                    // 与屏幕上下垂直 → 停止旋转
        )
        val cw = cos(rotNow)
        val sw = sin(rotNow)

        // 线方向(垂直于行进方向)：(H, W)，再顺时针转 rotNow
        val lx0 = H / len * R
        val ly0 = W / len * R
        val lx = lx0 * cw - ly0 * sw
        val ly = lx0 * sw + ly0 * cw

        // ── 锚点轨迹：交接前 = 对角线，交接后 = 纯左移 ──
        val px: Float
        val py: Float
        if (t < tV) {
            val te = t * 1.1f - 0.05f
            px = W * (1f - te)
            py = H * te
        } else {
            val teV = minOf(tV, 1f) * 1.1f - 0.05f
            val pxV = W * (1f - teV)
            val pyV = H * teV
            val u = if (tV == Float.MAX_VALUE) 0f
                else ((t - tV) / (1f - tV)).coerceIn(0f, 1f)
            px = pxV + (-WIPE_SLIDE_OVERSHOOT * W - pxV) * u // 左移出屏
            py = pyV                        // 垂直线位置与 y 无关，锁交接点
        }

        // 保留方向 = 行进方向 (-W, H)；mirror 时水平镜像(供 < 翻页反向擦拭)
        val kx = -W / len * R
        val ky = H / len * R
        val mPx = if (mirror) W - px else px
        val mLx = if (mirror) -lx else lx
        val mKx = if (mirror) -kx else kx
        // keep = true：未擦半边(随行程缩小，供"被扫走"的盖顶旧页)；
        // keep = false：已擦半边(随行程生长，供反翻"扫入"的盖顶新页)
        val dkx = if (keep) mKx else -mKx
        val dky = if (keep) ky else -ky
        return Outline.Generic(
            Path().apply {
                moveTo(mPx - mLx, py - ly)
                lineTo(mPx + mLx, py + ly)
                lineTo(mPx + mLx + dkx, py + ly + dky)
                lineTo(mPx - mLx + dkx, py - ly + dky)
                close()
            }
        )
    }
}
