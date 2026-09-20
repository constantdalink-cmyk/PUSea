package org.example.project

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Compose Multiplatform 资源统一导入
import org.jetbrains.compose.resources.vectorResource
import org.example.project.generated.resources.Res
import org.example.project.generated.resources.ic_discord
import org.example.project.generated.resources.ic_github
import org.example.project.generated.resources.ic_wechat
import org.example.project.generated.resources.ic_x

// 顶层缓动曲线常量
val GreenPeelOffEasing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

// 各社交平台联系内容(点击图标后卡片展示)
private val SocialContactInfo = mapOf(
    "GitHub" to "github.com/constantdalink-cmyk",
    "WeChat" to "RWXH_ZKYL",
    "X" to "Constant_Dalink",
    "Discord" to "RWXHMXSN1"
)

// 社交图标列表(从左到右顺序)
private val SocialItems = listOf("GitHub", "WeChat", "X", "Discord")

// 主画布按钮规格表(CardButtons/MainButtonSpec)与动态按钮组件 MainButtonGrid
// 已整体移至 FunctionManagementCanvas.kt，由功能管理画布的小格子布局统一驱动。

@Composable
fun BoxScope.GreenBottomSubtitle(pixelFont: FontFamily) {
    Box(
        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 24.dp)
    ) {
        Text(
            text = uiText(UiText.BottomSubtitle),
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 12.sp,
            fontFamily = pixelFont,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun BoxScope.GreenSharpSideSliders(trigger: Boolean) {
    if (trigger) {
        GreenSharpSideSlidersContent()
    }
}

@Composable
private fun BoxScope.GreenSharpSideSlidersContent() {
    val anim = remember { Animatable(32f) }
    val breatheAnim = remember { Animatable(0.4f) }
    val animDuration = 1200

    LaunchedEffect(Unit) {
        delay(100)
        launch { anim.animateTo(0f, tween(animDuration, easing = GreenPeelOffEasing)) }
        launch {
            breatheAnim.animateTo(
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1600, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                )
            )
        }
    }

    val androidGreen = Color(0xFF3DDC84)

    Box(
        modifier = Modifier
            .align(Alignment.CenterStart)
            .width(32.dp)
            .height(80.dp)
            .graphicsLayer { translationX = (-anim.value - 14f) * density }
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
                .graphicsLayer { alpha = if (anim.value < 10f) (1f - anim.value / 10f) * 0.35f * breatheAnim.value else 0f }
                .drawWithCache {
                    onDrawWithContent {
                        drawContent()
                        drawRoundRect(androidGreen, Offset(-16.dp.toPx(), -3.dp.toPx()),
                            Size(51.dp.toPx(), 86.dp.toPx()), CornerRadius(19.dp.toPx(), 19.dp.toPx()))
                    }
                }
        )
        Box(
            modifier = Modifier.fillMaxSize().drawWithCache {
                onDrawWithContent {
                    drawContent()
                    drawRoundRect(androidGreen.copy(alpha = 0.95f), Offset(-16.dp.toPx(), 0f),
                        Size(48.dp.toPx(), 80.dp.toPx()), CornerRadius(16.dp.toPx(), 16.dp.toPx()))
                }
            }
        )
        Box(
            modifier = Modifier.fillMaxSize()
                .graphicsLayer { alpha = 0.5f + breatheAnim.value * 0.5f }
                .drawWithCache {
                    val arrowLeftPx = 19.dp.toPx()
                    val arrowTopPx = (size.height - 14.dp.toPx()) / 2f
                    val arrowPath = Path().apply {
                        moveTo(arrowLeftPx, arrowTopPx)
                        lineTo(arrowLeftPx + 8.dp.toPx(), arrowTopPx + 14.dp.toPx() / 2f)
                        lineTo(arrowLeftPx, arrowTopPx + 14.dp.toPx())
                    }
                    onDrawWithContent {
                        drawContent()
                        drawPath(arrowPath, Color.White,
                            style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Butt, join = StrokeJoin.Miter))
                    }
                }
        )
    }

    Box(
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .width(32.dp)
            .height(80.dp)
            .graphicsLayer { translationX = (anim.value + 14f) * density }
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
                .graphicsLayer { alpha = if (anim.value < 10f) (1f - anim.value / 10f) * 0.35f * breatheAnim.value else 0f }
                .drawWithCache {
                    onDrawWithContent {
                        drawContent()
                        drawRoundRect(androidGreen, Offset(-3.dp.toPx(), -3.dp.toPx()),
                            Size(51.dp.toPx(), 86.dp.toPx()), CornerRadius(19.dp.toPx(), 19.dp.toPx()))
                    }
                }
        )
        Box(
            modifier = Modifier.fillMaxSize().drawWithCache {
                onDrawWithContent {
                    drawContent()
                    drawRoundRect(androidGreen.copy(alpha = 0.95f), Offset(0f, 0f),
                        Size(48.dp.toPx(), 80.dp.toPx()), CornerRadius(16.dp.toPx(), 16.dp.toPx()))
                }
            }
        )
        Box(
            modifier = Modifier.fillMaxSize()
                .graphicsLayer { alpha = 0.5f + breatheAnim.value * 0.5f }
                .drawWithCache {
                    val arrowLeftPx = 5.dp.toPx()
                    val arrowTopPx = (size.height - 14.dp.toPx()) / 2f
                    val arrowPath = Path().apply {
                        moveTo(arrowLeftPx + 8.dp.toPx(), arrowTopPx)
                        lineTo(arrowLeftPx, arrowTopPx + 14.dp.toPx() / 2f)
                        lineTo(arrowLeftPx + 8.dp.toPx(), arrowTopPx + 14.dp.toPx())
                    }
                    onDrawWithContent {
                        drawContent()
                        drawPath(arrowPath, Color.White,
                            style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Butt, join = StrokeJoin.Miter))
                    }
                }
        )
    }
}

@Composable
fun BoxScope.OptimizedWarmupChamber(
    isWarmingUp: Boolean,
    modifier: Modifier = Modifier,
    mascotContent: @Composable () -> Unit
) {
    if (!isWarmingUp) return

    val sweepProgress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        sweepProgress.animateTo(
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(2200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            )
        )
    }

    Box(modifier = modifier.align(Alignment.TopEnd).size(200.dp, 100.dp).graphicsLayer()) {
        Box(
            modifier = Modifier.fillMaxSize()
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                .drawWithCache {
                    val width = size.width
                    val height = size.height
                    val sweepWidth = width * 0.8f
                    onDrawWithContent {
                        drawContent()
                        val p = sweepProgress.value
                        val startX = -sweepWidth + (width + 2 * sweepWidth) * p
                        val endX = startX + sweepWidth
                        val sweepBrush = Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.0f),
                                Color.White.copy(alpha = 0.45f),
                                Color.White.copy(alpha = 0.0f)
                            ),
                            start = Offset(startX, 0f),
                            end = Offset(endX, height)
                        )
                        drawRect(brush = sweepBrush, blendMode = BlendMode.SrcAtop)
                    }
                }
        ) {
            mascotContent()
        }
    }
}

@Composable
fun OptimizedFlyingMessageBox(
    startX: Float, startY: Float, endX: Float, endY: Float,
    onAnimationEnd: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(800, easing = CubicBezierEasing(0.25f, 1f, 0.5f, 1f)))
        onAnimationEnd()
    }
    Box(
        modifier = modifier.graphicsLayer {
            val p = progress.value
            translationX = startX + (endX - startX) * p
            translationY = startY + (endY - startY) * p
            val scale = if (p < 0.2f) p / 0.2f else if (p > 0.8f) (1f - p) / 0.2f else 1f
            scaleX = scale; scaleY = scale
            alpha = if (p > 0.8f) (1f - p) / 0.2f else 1f
        }
    ) { content() }
}

// ==================== 主手势画布 ====================
@Composable
fun GreenLuaSwipeCanvas(
    activeCanvas: String?,
    onActiveCanvasChange: (String?) -> Unit,
    pixelFont: FontFamily,
    currentLanguageCode: String = "en",
    onConfirmLanguageAndExit: (String) -> Unit = {},
    onSocialIconClick: (String) -> Unit = {}
) {
    Box(modifier = Modifier.fillMaxSize().zIndex(10f)) {
        val leftCanvasAnim = remember { Animatable(-1f) }
        val rightCanvasAnim = remember { Animatable(1f) }

        var activeSubCanvas by remember { mutableStateOf<String?>(null) }
        var selectedIndex by remember { mutableStateOf(-1) }
        val socialProgress = remember { Animatable(0f) }
        val socialScope = rememberCoroutineScope()
        // 统一管理社交图标展开/回位动画,防止新旧动画并发导致瞬移
        var socialJob by remember { mutableStateOf<Job?>(null) }

        // 功能管理画布 12 张卡片轻点 → 写入导航总线 → 这里观察后切换到对应子画布
        LaunchedEffect(CanvasNavigation.requested) {
            CanvasNavigation.requested?.let { target ->
                activeSubCanvas = target
                CanvasNavigation.requested = null
            }
        }

        LaunchedEffect(activeCanvas) {
            if (activeCanvas != "black") {
                activeSubCanvas = null
                selectedIndex = -1
                socialJob?.cancel()
                socialProgress.snapTo(0f)
            }
            when (activeCanvas) {
                "black" -> {
                    launch { leftCanvasAnim.animateTo(0f, tween(260, easing = GreenPeelOffEasing)) }
                    launch { rightCanvasAnim.animateTo(1f, tween(100)) }
                }
                "white" -> {
                    launch { rightCanvasAnim.animateTo(0f, tween(260, easing = GreenPeelOffEasing)) }
                    launch { leftCanvasAnim.animateTo(-1f, tween(100)) }
                }
                else -> {
                    launch { leftCanvasAnim.animateTo(-1f, tween(240, easing = GreenPeelOffEasing)) }
                    launch { rightCanvasAnim.animateTo(1f, tween(240, easing = GreenPeelOffEasing)) }
                }
            }
        }

        LaunchedEffect(activeSubCanvas) {
            if (activeSubCanvas != null && selectedIndex >= 0) {
                socialJob?.cancel()
                socialJob = socialScope.launch {
                    if (socialProgress.value > 0f) {
                        socialProgress.animateTo(0f, tween(600, easing = GreenPeelOffEasing))
                    }
                    selectedIndex = -1
                }
            }
        }

        // 白色主画布渲染
        val isLeftRendered = activeCanvas == "black" || leftCanvasAnim.value > -0.999f
        if (isLeftRendered) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = leftCanvasAnim.value * size.width
                        clip = true
                    }
                    .background(Color.White)
                    .drawWithCache {
                        val circleRadius = 40.dp.toPx()
                        val strokePx = 4.dp.toPx()
                        onDrawBehind {
                            val cx = size.width / 2f
                            val cy = 100.dp.toPx()
                            drawCircle(Color.Black, circleRadius, Offset(cx, cy))
                            val innerLineY = cy + circleRadius * 0.14f + 20.dp.toPx()
                            drawLine(Color.White,
                                Offset(cx - circleRadius, innerLineY),
                                Offset(cx + circleRadius, innerLineY), strokeWidth = strokePx)
                            drawCircle(Color.White, circleRadius * 0.28f,
                                Offset(cx, cy - circleRadius * 0.30f))
                            val dividerY = cy + circleRadius + 48.dp.toPx()
                            drawLine(Color.Black, Offset(0f, dividerY), Offset(size.width, dividerY), strokeWidth = strokePx)
                            drawLine(Color.Black, Offset(cx, dividerY), Offset(cx, size.height), strokeWidth = strokePx)
                        }
                    }
            ) {
                Box(
                    modifier = Modifier.fillMaxSize().clickable {
                        if (selectedIndex >= 0) {
                            socialJob?.cancel()
                            socialJob = socialScope.launch {
                                socialProgress.animateTo(0f, tween(600, easing = GreenPeelOffEasing))
                                selectedIndex = -1
                            }
                        } else {
                            onActiveCanvasChange(null)
                        }
                    }
                )

                Box(
                    modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().height(160.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Text(uiText(UiText.User), color = Color.Black, fontSize = 12.sp, fontFamily = pixelFont,
                        fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                }

                // ==================== 12 个主按钮 ====================
                // 排列顺序 = 功能管理画布小格子排列顺序：排序总线(ButtonOrderStore)与
                // 动态按钮组件(MainButtonGrid)都定义在 FunctionManagementCanvas.kt 里，
                // 小格子换位 / 收进虚线格时实时重排。主画布这边只需这一行调用。
                MainButtonGrid(pixelFont = pixelFont) { activeSubCanvas = it }

                // ==================== 底部社交图标区 (防崩溃强化) ====================
                // 是否显示由功能管理画布第一个竖块控制：绿=显示，红=隐藏(整体不渲染)
                if (ContactIconStore.showContactIcons) BoxWithConstraints(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .widthIn(max = 412.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    val safeMaxWidth = maxWidth.coerceAtLeast(180.dp)
                    val box = 32.dp
                    val space = ((safeMaxWidth - box * 4) / 5f).coerceIn(12.dp, 36.dp)
                    val contentWidth = box * 4 + space * 3
                    val startOffset = ((safeMaxWidth - contentWidth) / 2f).coerceAtLeast(0.dp)
                    val liftDp = 18.dp

                    val cellCenter: (Int) -> Dp = { i -> startOffset + box / 2f + (box + space) * i }

                    val p = socialProgress.value
                    val liftT = (p / 0.32f).coerceIn(0f, 1f)
                    val moveT = ((p - 0.32f) / 0.53f).coerceIn(0f, 1f)
                    val textT = ((p - 0.68f) / 0.32f).coerceIn(0f, 1f)

                    // 安全索引过滤，防止数组越界闪退
                    val sel = selectedIndex
                    val safeSel = if (sel in SocialItems.indices) sel else -1

                    val selOriginX = if (safeSel >= 0) cellCenter(safeSel) else 0.dp
                    val targetX = (selOriginX - 32.dp).coerceAtLeast(box / 2f + 4.dp)
                    val curX = selOriginX - (selOriginX - targetX) * moveT
                    val lineY = liftDp * liftT + box / 2f
                    val lineStartX = curX + box * 0.6f + 4.dp
                    val lineWidth = (selOriginX + box / 2f - lineStartX).coerceAtLeast(0.dp)

                    // 字幕真实测量宽度,供黑色衬板贴合文本右缘(不压字、不留余)
                    val textMeasurer = rememberTextMeasurer()
                    val localDensity = LocalDensity.current
                    val contactStr = if (safeSel >= 0) SocialContactInfo[SocialItems[safeSel]].orEmpty() else ""
                    val subtitleWidthDp = remember(contactStr, pixelFont) {
                        with(localDensity) {
                            textMeasurer.measure(
                                text = contactStr,
                                style = TextStyle(
                                    fontSize = 11.sp,
                                    fontFamily = pixelFont,
                                    fontWeight = FontWeight.Bold
                                )
                            ).size.width.toDp()
                        }
                    }

                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(bottom = 14.dp)
                            .height(box + liftDp + 18.dp)
                    ) {
                        // 黑色衬板(细版):抬升时淡入含住图标,TEXT 相位向右展开贴合字幕
                        // 声明在连线/字幕/图标之前 → 绘制层级位于它们之下;纯色黑,无投影
                        if (safeSel >= 0 && liftT > 0.001f) {
                            val plateH = 36.dp
                            val plateW = plateH + (subtitleWidthDp + 8.dp) * textT
                            // 大方块(黑色衬板)左缘：整体锚定在 curX 上——平移阶段方块向
                            // 左/右移多少，字幕就跟着移多少（字幕与方块同进同退）
                            val plateLeft = curX - plateH / 2f
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .offset(x = plateLeft, y = plateH / 2f - lineY)
                                    .size(width = plateW, height = plateH)
                                    .graphicsLayer {
                                        alpha = (liftT * 1.15f).coerceAtMost(1f)
                                        val s = 0.72f + 0.28f * liftT
                                        scaleX = s
                                        scaleY = s
                                    }
                                    .background(Color.Black, RoundedCornerShape(8.dp))
                            )
                        }

                        // 绿色横线:下移至居中字幕的正下方,作文本下划线,随字幕淡入
                        if (safeSel >= 0 && textT > 0f && subtitleWidthDp > 0.dp) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .offset(x = lineStartX, y = 9.dp - lineY)
                                    .width(subtitleWidthDp)
                                    .height(2.5.dp)
                                    .graphicsLayer { alpha = textT }
                                    .background(Color(0xFF3DDC84), RoundedCornerShape(1.dp))
                            )
                        }

                        // 字幕(联系方式文本)：水平位置与大方块同源——lineStartX = curX + 常量，
                        // 方块向左/右平移时字幕同步向左/右移（这里改的是字幕，不是绿色横线；
                        // 横线保持原位不动）
                        if (safeSel >= 0 && textT > 0f && lineWidth > 0.dp) {
                            Text(
                                text = SocialContactInfo[SocialItems[safeSel]] ?: "",
                                color = Color(0xFF3DDC84),
                                fontSize = 11.sp,
                                fontFamily = pixelFont,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .offset(x = lineStartX, y = 4.dp - lineY)
                                    .graphicsLayer { alpha = textT }
                            )
                        }

                        // 4 个纯 XML 矢量图标渲染
                        SocialItems.forEachIndexed { i, name ->
                            val isSel = i == safeSel
                            SocialIconTile(
                                name = name,
                                size = box,
                                iconTint = if (isSel) Color(0xFF3DDC84) else Color.Black,
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .offset(
                                        x = (if (isSel) curX else cellCenter(i)) - box / 2f,
                                        y = if (isSel) 0.dp - liftDp * liftT else 0.dp
                                    )
                                    .graphicsLayer {
                                        alpha = if (isSel) 1f else 1f - 0.7f * p
                                        if (isSel) {
                                            val s = 1f + 0.2f * liftT
                                            scaleX = s
                                            scaleY = s
                                        }
                                    },
                                onClick = {
                                    onSocialIconClick(name)
                                    socialJob?.cancel()
                                    // 重复点击已展开的图标 → 收起回位
                                    val collapsingSelf = selectedIndex == i && socialProgress.value > 0.5f
                                    socialJob = socialScope.launch {
                                        // 关键修复:切换图标时,先让旧图标基于当前进度完整播放
                                        // 回位动画(此时 selectedIndex 仍是旧值),播完后再切换
                                        // 选中目标并播放新图标的展开动画,避免旧图标瞬间
                                        // 归位、新图标直接跳到终态
                                        if (socialProgress.value > 0f) {
                                            socialProgress.animateTo(
                                                0f,
                                                tween(
                                                    (480 * socialProgress.value).toInt().coerceAtLeast(180),
                                                    easing = GreenPeelOffEasing
                                                )
                                            )
                                        }
                                        if (collapsingSelf) {
                                            selectedIndex = -1
                                        } else {
                                            selectedIndex = i
                                            socialProgress.animateTo(
                                                1f,
                                                tween(700, easing = GreenPeelOffEasing)
                                            )
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        // 黑色画布
        GreenBlackCanvas(
            isRendered = activeCanvas == "white" || rightCanvasAnim.value < 0.999f,
            offsetFraction = rightCanvasAnim.value,
            onClose = { onActiveCanvasChange(null) },
            pixelFont = pixelFont
        )

        // 独立子画布分发中心
        SubCanvasDispatcher(
            activeSubCanvas = activeSubCanvas,
            onClose = { activeSubCanvas = null },
            pixelFont = pixelFont,
            currentLanguageCode = currentLanguageCode,
            onConfirmLanguageAndExit = onConfirmLanguageAndExit
        )
    }
}

@Composable
private fun GreenCanvasButton(
    text: String,
    pixelFont: FontFamily,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
            .background(Color.Black.copy(alpha = 0.08f), RoundedCornerShape(6.dp))
            .border(1.dp, Color.Black.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = Color.Black, fontSize = 12.sp,
            fontFamily = pixelFont, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}

// ==================== 社交图标块 ====================
@Composable
private fun SocialIconTile(
    name: String,
    size: Dp,
    iconTint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val iconResource = when (name) {
        "GitHub" -> Res.drawable.ic_github
        "WeChat" -> Res.drawable.ic_wechat
        "X" -> Res.drawable.ic_x
        "Discord" -> Res.drawable.ic_discord
        else -> null
    }

    Box(
        modifier = modifier
            .size(size)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        if (iconResource != null) {
            Icon(
                imageVector = vectorResource(iconResource),
                contentDescription = name,
                tint = iconTint,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
