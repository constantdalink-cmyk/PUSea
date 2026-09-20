package org.example.project

// ==============================================================================
// 画布文件 7/10：整合版 —— 组件层 + 场景层（上册）合一
// （原 App45_aiassistcomponents.kt + App46_subcanvasaiassistsystemone.kt）
//
// 整合说明：应用户要求，原"平均解耦"拆分的组件层（App45）与场景层上册
// （App46）合并为单一文件，少一次跨文件拷贝、少一处丢文件的风险。
// 合并后 AI Assist 画布只需要两个文件：
//   1) 本文（App45_aiassistcomponents.kt）
//      —— 组件层（TearBlock / TrackSlot / AssistCard / SettingsDrawer /
//         CustomEntryCard）+ 画布主体（UpArrow / AIAssistSystemContent）
//         + 顶层入口 fun SubCanvasAIAssistSystem（全工程唯一）
//   2) App37_subcanvasaiassistsystem.kt —— 场景层（下册）：撕开滑块自足模块
//      （EntryRound private + ReverseSlider internal）
//
// ⚠ 拷入须知（务必照做，否则 Redeclaration）：
//   - 用本文【覆盖】工程里的 App45_aiassistcomponents.kt；
//   - 【删除】工程里的 App46_subcanvasaiassistsystemone.kt ——
//     其内容已全部并入本文，留着会和本文重复声明
//     （SubCanvasAIAssistSystem / AIAssistSystemContent / UpArrow）；
//   - App37 保持不动。本文与 App37 声明互不重叠、互相引用
//     （本文调 App37 的 ReverseSlider；App37 调本文的 CustomEntryCard /
//     TearBlock / TrackSlot），同模块同包内合法。
//
// 交互设计（按需求逐条实现，原 App46 说明保留）：
//   1) 标题（七语本地化字标）深灰、淡入在左上角
//   2) 标题下方一条淡灰线，从最左边向右伸出
//   3) 线动画结束后，可编辑卡片逐个从左滑出（错峰，动画舒缓）
//   4) 卡片：白底黑字、圆角、左右内边距 17dp；靠左上标题、靠左下说明、
//      右侧一个向右的箭头（仅箭头头，无箭头身）
//   5) 点击卡片/箭头 → 箭头平滑旋转 90° 为向下，卡片下方展开编辑框
//   6) 展开时卡片高度弹簧动画，把下方卡片顺滑向下推移
//   7) 卡片下方：横线伸出 → 三个黑色抽屉面板右缘扫出盖住 → 停留 →
//      右缘往回扫，露出滑动调节设置 → 竖排字幕淡入
//   8) 右上角向上箭头：点击 → onClose → 容器整页自动上滑回主页
//
// 本地化说明：六张卡片标题 / 说明走 uiText(UiText.AssistCard*)；回复模式
// 选项片走 uiText(UiText.AssistReplyMode*)；抽屉竖排字幕走
// uiText(UiText.AssistDrawerCaption*)；顶部标题走
// uiText(UiText.AssistCanvasWordmark)。编辑框虚写字幕 DEFAULT_* 与滑杆
// 标签（Temperature / Max Tokens / Top P）按要求保持原样不翻译。
//
// 依赖注意：shared 模块未引入 material，全部用 foundation API
// （BasicText / BasicTextField / Canvas），动画只用 animation-core。
// 基础层（Mono / PixelText / ChevronArrow / AnimatedReveal / MonoTextArea /
// GhostLineField / DrawerSlot / SliderBlock / OptionChip / AiAssistStore /
// computeTearMetrics / BLOCK_* / ERASE_* / TRACK_SHRINK_AT_DP）在
// AIAssistTheme.kt；uiText / UiText / SlideDownContainer 在工程既有文件。
// ==============================================================================

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// ==============================================================================
// 一、组件层（原 App45_aiassistcomponents.kt）
// ==============================================================================

// ==================== 撕开滑块视觉原语 ====================
// ChevronArrow（120° 钝角箭头）在基础层 AIAssistTheme.kt。

/**
 * 黑色滑块本体（52×76、纯黑 + 90° 粗箭头）—— 撕开交互中的原黑块与镜像黑块共用：
 * - shape 决定圆角方向（原块右圆角、镜像块左圆角）；
 * - arrowRight 决定箭头朝向（原块向右、镜像块向左）；
 * - erasePx 从右缘往左裁剪（clipRect 只作用于本图层，裁掉的部分露出下层白卡）。
 * ⚠ drawWithContent 保持最外层：clipRect 连黑色背景一起裁剪；
 *   若放在 background 之后，背景先绘制不被裁剪（APK 上"没遮住"的 bug）。
 * App37（下册）的 ReverseSlider 跨文件调用 → internal。
 */
@Composable
internal fun TearBlock(
    modifier: Modifier = Modifier,
    offsetX: Dp,
    erasePx: Float,
    shape: RoundedCornerShape,
    arrowRight: Boolean
) {
    Box(
        modifier
            .offset(x = offsetX)
            .width(BLOCK_W_DP)
            // 高度与那些白块等高（折叠白卡 ≈ 76dp，与上方可编辑卡片同高）
            .height(BLOCK_H_DP)
            .clipToBounds()
            .drawWithContent {
                clipRect(right = size.width - erasePx) {
                    this@drawWithContent.drawContent()
                }
            }
    ) {
        Box(
            Modifier
                .width(BLOCK_W_DP)
                .height(BLOCK_H_DP)
                .clip(shape)
                .background(Color(0xFF000000)),
            contentAlignment = Alignment.Center
        ) {
            // 90° 粗箭头 —— 只有箭头头，无箭头身
            // （两条粗线从箭头顶端向回张开 90°，粗描边 + 圆头）
            Canvas(Modifier.size(22.dp, 18.dp)) {
                val strokeWidth = 3.dp.toPx()
                val armLen = 9.dp.toPx()
                val dx = armLen * 0.7071f
                val dy = armLen * 0.7071f
                if (arrowRight) {
                    val tipX = size.width
                    val tipY = size.height / 2f
                    drawLine(
                        color = Color(0xFFEDEDF2),
                        start = Offset(tipX, tipY),
                        end = Offset(tipX - dx, tipY - dy),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = Color(0xFFEDEDF2),
                        start = Offset(tipX, tipY),
                        end = Offset(tipX - dx, tipY + dy),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )
                } else {
                    val tipX = 0f
                    val tipY = size.height / 2f
                    drawLine(
                        color = Color(0xFFEDEDF2),
                        start = Offset(tipX, tipY),
                        end = Offset(tipX + dx, tipY - dy),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = Color(0xFFEDEDF2),
                        start = Offset(tipX, tipY),
                        end = Offset(tipX + dx, tipY + dy),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )
                }
            }
        }
    }
}

/**
 * 白矩形槽位（撕开滑块的白色填充黑边框轨道）：
 * - 左缘直角贴 x=0（最左边不留缝）；宽度由几何引擎算出 ——
 *   新黑滑块最右完全到达原滑块最左（overDead ≥ 104）前保持全宽（W−68）不动，
 *   之后才开始向左收缩（W−68−(overDead−104)，1:1 跟手），随 extend 缩放；
 * - 撕完时随 fade（tear 进度）淡出消失，白块（卡片）完整露出
 */
@Composable
internal fun TrackSlot(
    trackWidth: Dp,
    fade: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .width(trackWidth)
            // 高度与那些白块等高（折叠白卡 ≈ 76dp，与上方可编辑卡片同高）
            .height(BLOCK_H_DP)
            // 拉开的部分：白色填充、黑框不变
            .background(Color(0xFFFFFFFF))
            .border(1.dp, Color(0xFF000000))
            // 撕完动画中随 tear 淡出，不再残留盖住白块
            .graphicsLayer { alpha = 1f - fade }
    )
}

// ==================== 可编辑卡片 ====================

@Composable
internal fun AssistCard(
    visible: Boolean,
    index: Int,
    title: String,
    description: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    pixelFont: FontFamily,
    editor: @Composable () -> Unit
) {
    // 入场：画布一打开就立刻开始（不再空等），灰线将尽时卡片逐个左滑（错峰 140ms）
    val enter = remember { Animatable(0f) }
    LaunchedEffect(visible) {
        if (!visible) {
            enter.snapTo(0f)
            return@LaunchedEffect
        }
        delay(900L + index * 140L)
        enter.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 220f))
    }

    // 箭头：纯旋转动画 —— 同一个 chevron 形状整体旋转 90°，
    // 旋转前（右 ">"）与旋转后（下 "v"）形状完全一样
    val arrow = remember { Animatable(if (expanded) 1f else 0f) }
    LaunchedEffect(expanded) {
        arrow.animateTo(
            if (expanded) 1f else 0f,
            tween(300, easing = FastOutSlowInEasing)
        )
    }

    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = enter.value
                translationX = (1f - enter.value) * -44f
            }
            .clip(shape)
            .background(Mono.cardBg)
            .border(1.dp, if (expanded) Mono.cardBorderStrong else Mono.cardBorder, shape)
            .clickable(onClick = onToggle)
            .padding(horizontal = 17.dp, vertical = 14.dp)
    ) {
        // 靠左：上方 = 可编辑什么（黑）；下方 = 意义（深灰英文）；靠右：箭头
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                PixelText(
                    title,
                    color = Mono.cardText,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = pixelFont
                )
                Spacer(Modifier.height(3.dp))
                PixelText(
                    description,
                    color = Mono.cardDim,
                    fontSize = 10.5.sp,
                    lineHeight = 15.sp,
                    fontFamily = pixelFont
                )
            }
            Spacer(Modifier.width(10.dp))
            // 箭头（仅箭头头，无箭头身）：120° 钝角 chevron，缩小；
            // 纯旋转 —— 固定形状整体旋转 90°，旋转前后一样
            Box(
                Modifier
                    .size(20.dp)
                    .graphicsLayer { rotationZ = arrow.value * 90f },
                contentAlignment = Alignment.Center
            ) {
                ChevronArrow(modifier = Modifier.size(13.dp))
            }
        }

        // 展开区：高度动画把下方卡片顺滑推下
        AnimatedReveal(expanded) {
            Column(Modifier.padding(top = 12.dp)) {
                editor()
            }
        }
    }
}

// ==================== 抽屉编排（卡片下方区块） ====================

/**
 * 卡片下方整个区块：
 * 1) 横线二（卡片下方）从左到右伸出；
 * 2) 横线三（竖线底端）从左伸到右；
 * 3) 全部横线动画完成后，竖线从上下两边同时向竖线中点相伸连接；
 * 4) 三个黑色抽屉面板（左缘贴边、撑满不留缝）右缘扫出盖住 → 停留 →
 *    右缘往回扫，露出滑动调节设置 → 字幕淡入。
 *
 * 状态内聚、自带动画序列，只通过 onCoveredChange 向场景层广播
 * "黑块已盖住（bar1 ≥ 1f）"事件（驱动撕开滑块淡入）。
 * 三个滑块的数值初值从 AiAssistStore 恢复、改动即落盘（持久化）。
 */
@Composable
internal fun SettingsDrawer(
    visible: Boolean,
    pixelFont: FontFamily,
    onCoveredChange: (Boolean) -> Unit
) {
    // 横线进度 + 竖线进度 + 三个抽屉进度 + 滑动调节的数值（全部内聚）
    // - line2Progress：卡片下方横线（横线二）
    // - bottomLineProgress：竖线底端横线（横线三，新增：从左伸到右）
    // - vLineProgress：竖线上下两段向中点延伸连接（全部横线动画完成后启动）
    val line2Progress = remember { Animatable(0f) }
    val bottomLineProgress = remember { Animatable(0f) }
    val vLineProgress = remember { Animatable(0f) }
    val bar1 = remember { Animatable(0f) }
    val bar2 = remember { Animatable(0f) }
    val bar3 = remember { Animatable(0f) }
    // 三个滑动调节的数值：初值从持久化恢复（AiAssistStore.state）
    var temperature by remember { mutableStateOf(AiAssistStore.state.temperature) }
    var maxTokens by remember { mutableStateOf(AiAssistStore.state.maxTokens) }
    var topP by remember { mutableStateOf(AiAssistStore.state.topP) }

    // 持久化：数值随改动落盘（copy 合并，不影响其它字段）
    LaunchedEffect(temperature, maxTokens, topP) {
        AiAssistStore.update {
            it.copy(temperature = temperature, maxTokens = maxTokens, topP = topP)
        }
    }

    // 事件广播（用 rememberUpdatedState 避免回调引用变化重启协程）：
    // 黑块盖住到位（bar1 ≥ 1f）→ true；复位 → false
    val currentOnCoveredChange by rememberUpdatedState(onCoveredChange)
    LaunchedEffect(bar1.value) {
        currentOnCoveredChange(bar1.value >= 1f)
    }

    // 主序列：横线二 → 横线三（从左伸到右）→ 竖线上下两段向中点延伸连接
    // → 黑块【同时】滑出 → 字幕淡入（并行）→ 黑块【分开】滑回
    // 注意：animateTo 是挂起函数，必须用 launch 并行启动，三个黑块才会真正同时滑出
    LaunchedEffect(visible) {
        if (!visible) {
            line2Progress.snapTo(0f)
            bottomLineProgress.snapTo(0f)
            vLineProgress.snapTo(0f)
            bar1.snapTo(0f); bar2.snapTo(0f); bar3.snapTo(0f)
            return@LaunchedEffect
        }
        // 1) 横线二：最后一张卡片下面的横线，从左到右伸出后撑满，不回去
        delay(1500)
        line2Progress.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
        delay(100)

        // 2) 横线三：竖线底端的底部横线，从左伸到右（第三个横线动画）
        //    —— 速度与前两个横线一致（tween 700，同横线二）
        bottomLineProgress.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
        delay(80)

        // 3) 全部横线动画都 OK → 竖线从上下两边同时向竖线中点相伸连接：
        //    上段从上往下伸、下段从下往上伸，在中点（113.5dp 处）汇合
        vLineProgress.animateTo(1f, tween(450, easing = FastOutSlowInEasing))
        delay(80)

        // 4) 三个黑块【同时】向右滑出盖住 —— launch 并行，真正同步
        launch { bar1.animateTo(1f, tween(340, easing = FastOutSlowInEasing)) }
        launch { bar2.animateTo(1f, tween(340, easing = FastOutSlowInEasing)) }
        launch { bar3.animateTo(1f, tween(340, easing = FastOutSlowInEasing)) }
        delay(380)

        // 3) 三个黑块【分开】滑回 —— 下面的先回（bar3 → bar2 → bar1）
        bar3.animateTo(2f, tween(340, easing = FastOutSlowInEasing))
        delay(190)
        bar2.animateTo(2f, tween(340, easing = FastOutSlowInEasing))
        delay(190)
        bar1.animateTo(2f, tween(340, easing = FastOutSlowInEasing))
    }

    Spacer(Modifier.height(22.dp))

    // 1) 从左到右的横线（淡灰）：左右撑满（在水平 padding 之外）
    Box(
        Modifier
            .fillMaxWidth(line2Progress.value)
            .height(1.dp)
            .background(Mono.line)
    )

    // 2) 滑动设置区（无水平 padding —— 黑块左边撑满屏幕左缘）：
    //    滑动设置列（黑块在左）→ 竖线（零间隙，黑块右缘正好到竖线）→ 右侧字幕
    //    顶对齐：黑块顶部刚好与横线贴着，竖线顶部与横线无缝相接
    Row(verticalAlignment = Alignment.Top) {
        // 滑动设置列：列宽从屏幕左缘一直到竖线
        Column(Modifier.weight(1f)) {
            DrawerSlot(barProgress = bar1.value) {
                SliderBlock(
                    label = "Temperature",
                    fraction = temperature / 1.5f,
                    display = ((temperature * 100).roundToInt() / 100f).toString(),
                    onFractionChange = { temperature = it * 1.5f },
                    pixelFont = pixelFont
                )
            }
            DrawerSlot(barProgress = bar2.value) {
                SliderBlock(
                    label = "Max Tokens",
                    fraction = (maxTokens - 256).toFloat() / (8192 - 256),
                    display = maxTokens.toString(),
                    onFractionChange = { maxTokens = (256 + it * (8192 - 256)).roundToInt() },
                    pixelFont = pixelFont
                )
            }
            DrawerSlot(barProgress = bar3.value) {
                SliderBlock(
                    label = "Top P",
                    fraction = topP,
                    display = ((topP * 100).roundToInt() / 100f).toString(),
                    onFractionChange = { topP = it },
                    pixelFont = pixelFont
                )
            }
        }

        // 竖线：紧贴滑动设置列右侧（零间隙，黑块右缘正好到竖线），顶部与横线无缝。
        // 竖线分上下两段：全部横线动画完成后，上段从上往下伸、下段从下往上伸，
        // 同时向竖线中点（227/2 = 113.5dp 处）相伸连接；中间空隙随进度收缩。
        // 底部横线（横线三）：与横线二同款 fillMaxWidth 动画，见 Row 下方
        val vLineHeight = 227.dp          // 竖线总高（与三个抽屉位等高）
        val vLineHalf = vLineHeight / 2f  // 113.5dp —— 上下两段的中点
        Column(horizontalAlignment = Alignment.Start) {
            // 上段：顶部固定，向下伸
            Box(
                Modifier
                    .width(1.dp)
                    .height(vLineHalf * vLineProgress.value)
                    .background(Mono.line)
            )
            // 中间空隙：随两段延伸而收缩，两段在中点汇合时归零
            Spacer(
                Modifier
                    .width(1.dp)
                    .height((vLineHeight - vLineHeight * vLineProgress.value).coerceAtLeast(0.dp))
            )
            // 下段：底部固定，向上伸
            Box(
                Modifier
                    .width(1.dp)
                    .height(vLineHalf * vLineProgress.value)
                    .background(Mono.line)
            )
        }

        Spacer(Modifier.width(12.dp))

        // 右侧字幕：竖排 —— 一竖列一个单词（字母从上到下逐字排列），
        // 大字 + 淡入（跟随黑块盖住到位的时机，bar1 >= 1f 才显示），深色字
        val captionShow = bar1.value >= 1f
        val captionAlpha = remember(captionShow) {
            Animatable(if (captionShow) 1f else 0f)
        }
        LaunchedEffect(captionShow) {
            captionAlpha.animateTo(if (captionShow) 1f else 0f, tween(500))
        }
        // 字幕：恢复逐字母竖排（一竖列一个单词），两列并排 + 淡入
        // 本地化：两个竖排单词改走 uiText 七语词典（UiText.AssistDrawerCaption*）；
        // 竖排渲染不变 —— word.forEach 按字堆叠，任意语言通用，CJK 译文天然成竖排
        Row(
            Modifier
                .width(46.dp)
                .height(228.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(
                uiText(UiText.AssistDrawerCaptionSlider),
                uiText(UiText.AssistDrawerCaptionSettings)
            ).forEachIndexed { idx, word ->
                if (idx > 0) Spacer(Modifier.width(12.dp))
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    word.forEach { ch ->
                        PixelText(
                            ch.toString(),
                            color = Mono.title,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = pixelFont,
                            modifier = Modifier.graphicsLayer { alpha = captionAlpha.value }
                        )
                    }
                }
            }
        }

    }

    // 底部横线（横线三）：与横线二同款动画 —— 从左伸到右撑满整行。
    // ⚠ 修复"第三个横线动画太快"：原来用 drawBehind 从 -1000dp 扫到
    // +1200dp（总长 2200dp ≈ 6 倍屏宽），时长虽同为 tween(700)，
    // 但前 ~59% 时间线在屏幕外不可见，可见部分只用约 290ms 扫过全屏，
    // 比横线二（700ms 扫全屏）快一倍多。
    // 现在 fillMaxWidth(fraction) 与横线二同款 —— 距离相同、时长相同、
    // 视觉速度完全一致；offset(-1dp) 对齐竖线列底部横线的位置
    // （竖线列 227dp + 横线 1dp = Row 高 228dp）
    // ⚠ 必须放在 Row 外（父 Column 的直接子元素）：若放 Row 内，
    // fillMaxWidth 会占满 Row 的剩余宽度，把 weight(1f) 的滑动设置列
    // 挤成 0 宽 —— 三个抽屉/滑块全部不显示（"下面都不显示了"的 bug）
    Box(
        Modifier
            .offset(y = (-1).dp)
            .fillMaxWidth(bottomLineProgress.value)
            .height(1.dp)
            .background(Mono.line)
    )
}

// ==================== 自定义请求卡片（撕开后留下的白块） ====================

/**
 * 撕开后留下的白块 = 同款可编辑卡片，字幕全是输入框（自己增加请求词条）。
 * 卡片外观、箭头旋转、弹簧展开、自动滚底全部内聚；
 * 所有状态（标题 / 说明 / 内容 / 展开）提升为参数，由场景层持有。
 * App37（下册）的 ReverseSlider 跨文件调用 → internal。
 */
@Composable
internal fun CustomEntryCard(
    expanded: Boolean,
    onToggle: () -> Unit,
    title: String,
    onTitleChange: (String) -> Unit,
    description: String,
    onDescriptionChange: (String) -> Unit,
    text: String,
    onTextChange: (String) -> Unit,
    appear: Float,              // 出现动画进度（黑滑块完全拉出后淡入 + 轻微放大）
    scrollState: ScrollState,   // 展开后自动滚到底，让编辑区可见
    pixelFont: FontFamily
) {
    // 展开箭头：纯旋转动画（与上面卡片一致）
    val customArrow = remember { Animatable(if (expanded) 1f else 0f) }
    LaunchedEffect(expanded) {
        customArrow.animateTo(
            if (expanded) 1f else 0f,
            tween(300, easing = FastOutSlowInEasing)
        )
    }
    // 展开后自动滚到底，让编辑区可见
    LaunchedEffect(expanded) {
        if (expanded) {
            delay(150)
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    Column(
        Modifier
            .zIndex(0f)
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .graphicsLayer {
                alpha = appear
                scaleX = 0.94f + 0.06f * appear
                scaleY = 0.94f + 0.06f * appear
            }
            .clip(RoundedCornerShape(12.dp))
            .background(Mono.cardBg)
            .border(1.dp, Mono.cardBorder, RoundedCornerShape(12.dp))
            .clickable { onToggle() }
            // 垂直内边距收小：折叠高度与上方可编辑卡片一致（≈ 76dp）
            .padding(horizontal = 17.dp, vertical = 12.dp)
    ) {
        // 未展开的样子 = 与上面卡片同款：靠左上方标题、靠左下方说明、右侧箭头；
        // 字幕全部替换成输入框：标题与说明都是可编辑的
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                GhostLineField(
                    value = title,
                    onValueChange = onTitleChange,
                    placeholder = "Custom Request",
                    pixelFont = pixelFont,
                    heightDp = 19.dp,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    textColor = Mono.cardText,
                    placeholderColor = Mono.cardFaint
                )
                Spacer(Modifier.height(3.dp))
                GhostLineField(
                    value = description,
                    onValueChange = onDescriptionChange,
                    placeholder = "Your own request entry — torn open from the black block. Tap to fill it in.",
                    pixelFont = pixelFont,
                    heightDp = 30.dp,
                    fontSize = 10.5.sp,
                    lineHeight = 15.sp,
                    textColor = Mono.cardDim,
                    placeholderColor = Mono.cardFaint
                )
            }
            Spacer(Modifier.width(10.dp))
            // 同款小箭头（仅箭头头，120° 钝角）：点击旋转 90° 展开
            // —— 复用提取的 ChevronArrow
            Box(
                Modifier
                    .size(20.dp)
                    .graphicsLayer { rotationZ = customArrow.value * 90f },
                contentAlignment = Alignment.Center
            ) {
                ChevronArrow(modifier = Modifier.size(13.dp))
            }
        }

        // 展开区：高度动画把下方内容顺滑推下（与上面卡片同款 AnimatedReveal）；
        // 预留空等用户自己填：空值显示虚写字幕，点击即写，留空失焦字幕重现
        AnimatedReveal(expanded) {
            Column(Modifier.padding(top = 12.dp)) {
                MonoTextArea(
                    value = text,
                    onValueChange = onTextChange,
                    placeholder = "Type your own request entry…",
                    pixelFont = pixelFont,
                    heightDp = 110.dp
                )
            }
        }
    }
}

// ==============================================================================
// 二、场景层上册：画布主体 + 入口（原 App46_subcanvasaiassistsystemone.kt）
// ==============================================================================

/**
 * 向上箭头（带箭头身）：一条竖直杆 + V 形箭头头。
 * 右上角"返回主页"按钮用 —— 与卡片里仅箭头头的 ChevronArrow 不同，
 * 这支箭头带杆：杆从箭头头分叉点竖直向下延伸到底。
 * 圆头描边，风格与整体像素线条一致。
 */
@Composable
private fun UpArrow(
    color: Color = Mono.title,
    strokeWidthDp: Dp = 1.5.dp,
    modifier: Modifier = Modifier
) {
    // 尺寸由调用方通过 modifier 传入（Canvas 直接按 modifier 绘制，支持非正方形：
    // 12×18 —— 箭头身加长 1/2：杆长 = 45% × 18 = 8.1dp，是原来的 5.4dp 的 1.5 倍；
    // V 形头分叉点仍在 55%，夹角更锐（≈62°））
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val strokeWidth = strokeWidthDp.toPx()
        val cx = w / 2f
        // 箭头头分叉点：从顶端往下 45% 处 —— 两翼斜线只延伸到 45% 高度
        // 就收进杆里（斜线变短 = 两翼长度减少），杆从 45% 延伸到底（杆更长）；
        // 两翼端点仍张开到画布左右边缘（0 / w），张开幅度不变
        val headBaseY = h * 0.45f
        // 杆：分叉点竖直向下延伸到箭头底端
        drawLine(
            color = color,
            start = Offset(cx, headBaseY),
            end = Offset(cx, h),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        // 箭头头：顶端向两侧张开的 V 形（两条斜线，与杆同宽同色）
        drawLine(
            color = color,
            start = Offset(cx, 0f),
            end = Offset(0f, headBaseY),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(cx, 0f),
            end = Offset(w, headBaseY),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun AIAssistSystemContent(
    visible: Boolean,
    pixelFont: FontFamily,
    onClose: () -> Unit
) {
    // 1) 标题淡入 —— 画布一打开立即开始
    val titleAlpha = remember { Animatable(0f) }
    // 2) 淡灰线从左向右伸出 —— 紧随标题开始，无空等
    val lineProgress = remember { Animatable(0f) }
    LaunchedEffect(visible) {
        if (visible) {
            titleAlpha.animateTo(1f, tween(650))
            // 线1：从左向右伸出后撑满，不回去
            lineProgress.animateTo(1f, tween(1000, delayMillis = 120, easing = FastOutSlowInEasing))
        } else {
            titleAlpha.snapTo(0f)
            lineProgress.snapTo(0f)
        }
    }

    // 各卡片编辑内容（本地状态）—— 初值从持久化恢复（AiAssistStore.state）；
    // 恢复为空时显示虚写字幕（例子）；用户点一下字幕消失自己写；留空失焦字幕重现
    var systemPrompt by remember { mutableStateOf(AiAssistStore.state.systemPrompt) }
    var identity by remember { mutableStateOf(AiAssistStore.state.identity) }
    var replyMode by remember { mutableStateOf(AiAssistStore.state.replyMode) }
    var behaviorRules by remember { mutableStateOf(AiAssistStore.state.behaviorRules) }
    var outputFormat by remember { mutableStateOf(AiAssistStore.state.outputFormat) }
    var examples by remember { mutableStateOf(AiAssistStore.state.examples) }

    // 持久化：六张卡片的编辑内容随输入落盘（copy 合并，不影响其它字段）
    LaunchedEffect(systemPrompt, identity, replyMode, behaviorRules, outputFormat, examples) {
        AiAssistStore.update {
            it.copy(
                systemPrompt = systemPrompt,
                identity = identity,
                replyMode = replyMode,
                behaviorRules = behaviorRules,
                outputFormat = outputFormat,
                examples = examples
            )
        }
    }

    // 默认白块全部未展开（点击卡片/箭头展开编辑区）
    var expanded0 by remember { mutableStateOf(false) }
    var expanded1 by remember { mutableStateOf(false) }
    var expanded2 by remember { mutableStateOf(false) }
    var expanded3 by remember { mutableStateOf(false) }
    var expanded4 by remember { mutableStateOf(false) }
    var expanded5 by remember { mutableStateOf(false) }

    // 抽屉区块"黑块已盖住"事件：由组件层 SettingsDrawer 广播，
    // 驱动撕开滑块淡入（bar1 ≥ 1f 的时机）
    var barsCovered by remember { mutableStateOf(false) }

    // 滚动状态提升到外层：贴纸撕开时自动滚到底，让撕出的白块（预留空）完整可见
    val contentScrollState = rememberScrollState()

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(contentScrollState)
            .padding(vertical = 14.dp)
    ) {
        // 标题行：左上角标题淡入（深灰）+ 右上角"向上箭头"——
        // 点击箭头 → onClose → 容器整页自动上滑回主页（与标题同步淡入）
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 顶部标题：原为硬编码 "aiassistsystem" 字标，现按用户要求接入
            // 七语词典（UiText.AssistCanvasWordmark）；各语言保持全小写连写字标风格
            PixelText(
                uiText(UiText.AssistCanvasWordmark),
                color = Mono.title,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = pixelFont,
                modifier = Modifier
                    .weight(1f)
                    .graphicsLayer { alpha = titleAlpha.value }
            )
            // 向上箭头（带箭头身）：竖直杆 + V 形箭头头 —— 杆从箭头头分叉点
            // 向下延伸到底，指向上方，点击回到主页；36dp 点击热区；
            // 纯黑 + 锐角 + 缩小 0.5 倍 + 箭头身加长 1/2（12×18dp 画布，1.5dp 描边）
            // 按压不闪暗：indication = null 去掉 ripple
            // （原来按一下箭头，箭头所占的地方就暗一下）
            val arrowInteraction = remember { MutableInteractionSource() }
            Box(
                Modifier
                    .size(36.dp)
                    .clickable(
                        interactionSource = arrowInteraction,
                        indication = null,
                        onClick = onClose
                    )
                    .graphicsLayer { alpha = titleAlpha.value },
                contentAlignment = Alignment.Center
            ) {
                UpArrow(
                    color = Color(0xFF000000),
                    strokeWidthDp = 1.5.dp,
                    modifier = Modifier.size(12.dp, 18.dp)
                )
            }
        }

        // 淡灰线：从最左边向右伸出
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .fillMaxWidth(lineProgress.value)
                .height(1.dp)
                .background(Mono.line)
        )

        // 可编辑卡片：逐个从左滑出（水平 16dp 内边距；横线在其外，左右撑满）
        // 本地化：六卡标题 / 说明全部走 uiText（UiText.AssistCard*，七语词典
        // 见 App9_localization.kt）；回复模式选项片直接走 uiText(UiText.AssistReplyMode*)；
        // 编辑框虚写字幕 DEFAULT_* 已在基础层接入 uiText
        Column(Modifier.padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(16.dp))

        AssistCard(
            visible = visible,
            index = 0,
            title = uiText(UiText.AssistCardSystemPromptTitle),
            description = uiText(UiText.AssistCardSystemPromptDesc),
            expanded = expanded0,
            onToggle = { expanded0 = !expanded0 },
            pixelFont = pixelFont
        ) {
            MonoTextArea(
                value = systemPrompt,
                onValueChange = { systemPrompt = it },
                placeholder = DEFAULT_SYSTEM_PROMPT,
                pixelFont = pixelFont,
                heightDp = 110.dp
            )
        }

        Spacer(Modifier.height(10.dp))

        AssistCard(
            visible = visible,
            index = 1,
            title = uiText(UiText.AssistCardIdentityTitle),
            description = uiText(UiText.AssistCardIdentityDesc),
            expanded = expanded1,
            onToggle = { expanded1 = !expanded1 },
            pixelFont = pixelFont
        ) {
            MonoTextArea(
                value = identity,
                onValueChange = { identity = it },
                placeholder = DEFAULT_IDENTITY,
                pixelFont = pixelFont,
                heightDp = 84.dp
            )
        }

        Spacer(Modifier.height(10.dp))

        AssistCard(
            visible = visible,
            index = 2,
            title = uiText(UiText.AssistCardReplyModeTitle),
            description = uiText(UiText.AssistCardReplyModeDesc),
            expanded = expanded2,
            onToggle = { expanded2 = !expanded2 },
            pixelFont = pixelFont
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // 本地化：枚举名仅用于持久化，显示标签直接走 uiText → 七语词典。
                // ⚠ 不依赖主题层的 AssistReplyMode.label() 扩展 —— 旧版主题文件
                // 没有该扩展，内联调用零跨文件依赖，主题文件新旧都能编译
                OptionChip(
                    label = uiText(UiText.AssistReplyModeStream),
                    selected = replyMode == AssistReplyMode.Stream,
                    onClick = { replyMode = AssistReplyMode.Stream },
                    pixelFont = pixelFont
                )
                OptionChip(
                    label = uiText(UiText.AssistReplyModeFull),
                    selected = replyMode == AssistReplyMode.Full,
                    onClick = { replyMode = AssistReplyMode.Full },
                    pixelFont = pixelFont
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        AssistCard(
            visible = visible,
            index = 3,
            title = uiText(UiText.AssistCardBehaviorRulesTitle),
            description = uiText(UiText.AssistCardBehaviorRulesDesc),
            expanded = expanded3,
            onToggle = { expanded3 = !expanded3 },
            pixelFont = pixelFont
        ) {
            MonoTextArea(
                value = behaviorRules,
                onValueChange = { behaviorRules = it },
                placeholder = DEFAULT_BEHAVIOR_RULES,
                pixelFont = pixelFont,
                heightDp = 84.dp
            )
        }

        Spacer(Modifier.height(10.dp))

        AssistCard(
            visible = visible,
            index = 4,
            title = uiText(UiText.AssistCardOutputFormatTitle),
            description = uiText(UiText.AssistCardOutputFormatDesc),
            expanded = expanded4,
            onToggle = { expanded4 = !expanded4 },
            pixelFont = pixelFont
        ) {
            MonoTextArea(
                value = outputFormat,
                onValueChange = { outputFormat = it },
                placeholder = DEFAULT_OUTPUT_FORMAT,
                pixelFont = pixelFont,
                heightDp = 84.dp
            )
        }

        Spacer(Modifier.height(10.dp))

        AssistCard(
            visible = visible,
            index = 5,
            title = uiText(UiText.AssistCardExamplesTitle),
            description = uiText(UiText.AssistCardExamplesDesc),
            expanded = expanded5,
            onToggle = { expanded5 = !expanded5 },
            pixelFont = pixelFont
        ) {
            MonoTextArea(
                value = examples,
                onValueChange = { examples = it },
                placeholder = DEFAULT_EXAMPLES,
                pixelFont = pixelFont,
                heightDp = 100.dp
            )
        }

        } // 卡片区（水平 padding）结束

        // ==================== 卡片下方：横线 → 抽屉拉开 → 字幕淡入 ====================
        // 整个区块在组件层（SettingsDrawer，本文上半部分）：状态内聚、
        // 自带动画序列，只广播 onCoveredChange 事件
        SettingsDrawer(
            visible = visible,
            pixelFont = pixelFont,
            onCoveredChange = { barsCovered = it }
        )

        // ==================== 相反滑块（撕开）====================
        // 自足模块在场景层下册 App37_subcanvasaiassistsystem.kt（internal，
        // 同模块可见）：拉出 / 撕开 / 白块全部内聚，出现时机跟随
        // barsCovered（黑块盖住到位后淡入）
        ReverseSlider(
            visible = visible,
            extraShow = barsCovered,
            pixelFont = pixelFont,
            scrollState = contentScrollState
        )
    }
}

// ==================== 画布入口 ====================
// ⚠ 顶层入口必须且只能声明一次（全工程唯一，声明在本文）

@Composable
fun SubCanvasAIAssistSystem(visible: Boolean, onClose: () -> Unit, pixelFont: FontFamily) {
    // 当前项目 SlideDownContainer 签名：(visible, title, onClose, pixelFont, content)
    // —— 容器采用「方案 A」已恢复 title: String 参数，必须传标题字符串；
    // 画布内容顶部仍自行绘制本地化字标标题
    SlideDownContainer(visible, uiText(UiText.AiAssistSystem), onClose, pixelFont) {
        AIAssistSystemContent(visible, pixelFont, onClose)
    }
}
