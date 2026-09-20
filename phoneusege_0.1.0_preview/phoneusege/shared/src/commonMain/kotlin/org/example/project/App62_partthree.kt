package org.example.project

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ============================================================================
// 【Part 3/3】独立组件
// 碎屑粒子 / 单行网格与手势 / 单个方块卡片 / 震源波动图标 / 波形环 / GITHUB 矢量图
// 注:被 Part1/Part2 跨文件调用的组件由 private 提升为 internal
// ============================================================================

// ==================== 碎屑粒子组件 ====================
@Composable
internal fun DebrisParticle(particle: DebrisParticleState) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(particle.id) {
        progress.animateTo(1f, tween(particle.durationMs, easing = LinearEasing))
    }

    val p = progress.value
    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    x = (particle.startX + particle.dx * p).roundToInt(),
                    y = ((700f + particle.dy * p)).roundToInt()
                )
            }
            .size(7.dp)
            .graphicsLayer {
                rotationZ = p * 720f
                alpha = 1f - p
            }
            .background(Color.White, RoundedCornerShape(1.dp))
    )
}

// ==================== 单行网格方块与手势 ====================
@Composable
internal fun SkillGridRowView(
    rowIndex: Int,
    row: SkillGridRow,
    pixelFont: FontFamily,
    isDraggingRow: Boolean,
    draggedCol: Int,
    isDevouring: Boolean,
    dragOffset: Offset,
    modifier: Modifier = Modifier,
    onClickItem: (SkillData, Int) -> Unit,
    onStartDrag: (Int) -> Unit,
    onDrag: (Offset) -> Unit,
    onEndDrag: () -> Unit,
    onSlideReplace: (Int, Boolean) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val slideAnimLeft = remember { Animatable(0f) }
    val slideAnimRight = remember { Animatable(0f) }
    val alphaLeft = remember { Animatable(1f) }
    val alphaRight = remember { Animatable(1f) }
    val scaleLeft = remember { Animatable(1f) }
    val scaleRight = remember { Animatable(1f) }

    // 当被吞噬时，被吃掉的卡片在大嘴处缩小至 0 并完全透明消失
    LaunchedEffect(isDevouring) {
        if (isDevouring) {
            if (draggedCol == 0) {
                launch { alphaLeft.animateTo(0f, tween(240)) }
                launch { scaleLeft.animateTo(0.1f, tween(240)) }
            } else if (draggedCol == 1) {
                launch { alphaRight.animateTo(0f, tween(240)) }
                launch { scaleRight.animateTo(0.1f, tween(240)) }
            }
        }
    }

    // 【BUG修复】首次组合必须跳过动画: 原实现在每行初次上屏(开页/滚动新行进入)时
    // LaunchedEffect 立即执行, 导致所有卡片从 alpha 0.2 闪一下。仅在数据真正被替换后淡入。
    var skipLeftFirstFadeIn by remember { mutableStateOf(true) }
    var skipRightFirstFadeIn by remember { mutableStateOf(true) }

    // 新技能在原位生成时，从 0 渐隐平滑浮现 (fade-in)
    LaunchedEffect(row.itemLeft) {
        if (skipLeftFirstFadeIn) {
            skipLeftFirstFadeIn = false
            return@LaunchedEffect
        }
        if (!isDraggingRow && slideAnimLeft.value == 0f) {
            alphaLeft.snapTo(0.2f)
            scaleLeft.snapTo(0.9f)
            launch { alphaLeft.animateTo(1f, tween(350, easing = SmoothEase)) }
            launch { scaleLeft.animateTo(1f, tween(350, easing = SmoothEase)) }
        }
    }
    LaunchedEffect(row.itemRight) {
        if (skipRightFirstFadeIn) {
            skipRightFirstFadeIn = false
            return@LaunchedEffect
        }
        if (!isDraggingRow && slideAnimRight.value == 0f) {
            alphaRight.snapTo(0.2f)
            scaleRight.snapTo(0.9f)
            launch { alphaRight.animateTo(1f, tween(350, easing = SmoothEase)) }
            launch { scaleRight.animateTo(1f, tween(350, easing = SmoothEase)) }
        }
    }

    // 【BUG修复】滑动换卡重入保护: 上一次滑出动画未结束时(280ms 临界区内)再次横滑同一行,
    // 会并发执行两次 onSlideReplace → 双倍消耗技能池 + 行数据被连写两次。
    var isSlideBusy by remember { mutableStateOf(false) }

    fun triggerSlideOut(col: Int, diffX: Float) {
        if (isSlideBusy) return
        isSlideBusy = true

        val shouldPush = (col == 0 && diffX > 0) || (col == 1 && diffX < 0)
        val outX = if (diffX > 0) 500f else -500f

        coroutineScope.launch {
            if (shouldPush) {
                launch { slideAnimLeft.animateTo(outX, tween(260)) }
                launch { slideAnimRight.animateTo(outX, tween(260)) }
                launch { alphaLeft.animateTo(0f, tween(260)) }
                launch { alphaRight.animateTo(0f, tween(260)) }
                delay(280)
                onSlideReplace(col, true)
                slideAnimLeft.snapTo(outX)
                slideAnimRight.snapTo(outX)
                launch { slideAnimLeft.animateTo(0f, tween(320, easing = SmoothEase)) }
                launch { slideAnimRight.animateTo(0f, tween(320, easing = SmoothEase)) }
                launch { alphaLeft.animateTo(1f, tween(300)) }
                launch { alphaRight.animateTo(1f, tween(300)) }
            } else {
                val targetAnim = if (col == 0) slideAnimLeft else slideAnimRight
                val targetAlpha = if (col == 0) alphaLeft else alphaRight
                launch { targetAnim.animateTo(outX, tween(260)) }
                launch { targetAlpha.animateTo(0f, tween(260)) }
                delay(280)
                onSlideReplace(col, false)
                targetAnim.snapTo(outX)
                launch { targetAnim.animateTo(0f, tween(320, easing = SmoothEase)) }
                launch { targetAlpha.animateTo(1f, tween(300)) }
            }
            isSlideBusy = false
        }
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val isLeftDragging = isDraggingRow && draggedCol == 0
        Box(
            modifier = Modifier
                .weight(1f)
                .graphicsLayer {
                    if (isLeftDragging) {
                        translationX = dragOffset.x
                        translationY = dragOffset.y
                        shadowElevation = 24f
                        alpha = alphaLeft.value
                        scaleX = scaleLeft.value
                        scaleY = scaleLeft.value
                    } else {
                        translationX = slideAnimLeft.value
                        alpha = alphaLeft.value
                        scaleX = scaleLeft.value
                        scaleY = scaleLeft.value
                    }
                }
                .zIndex(if (isLeftDragging) 1000f else 1f)
        ) {
            SingleSkillCard(
                skill = row.itemLeft,
                isTall = row.isTall,
                pixelFont = pixelFont,
                onClick = { onClickItem(row.itemLeft, 0) },
                onLongPress = { onStartDrag(0) },
                onDrag = onDrag,
                onDragEnd = onEndDrag,
                onSwipeOut = { diffX -> triggerSlideOut(0, diffX) }
            )
        }

        val isRightDragging = isDraggingRow && draggedCol == 1
        Box(
            modifier = Modifier
                .weight(1f)
                .graphicsLayer {
                    if (isRightDragging) {
                        translationX = dragOffset.x
                        translationY = dragOffset.y
                        shadowElevation = 24f
                        alpha = alphaRight.value
                        scaleX = scaleRight.value
                        scaleY = scaleRight.value
                    } else {
                        translationX = slideAnimRight.value
                        alpha = alphaRight.value
                        scaleX = scaleRight.value
                        scaleY = scaleRight.value
                    }
                }
                .zIndex(if (isRightDragging) 1000f else 1f)
        ) {
            SingleSkillCard(
                skill = row.itemRight,
                isTall = row.isTall,
                pixelFont = pixelFont,
                onClick = { onClickItem(row.itemRight, 1) },
                onLongPress = { onStartDrag(1) },
                onDrag = onDrag,
                onDragEnd = onEndDrag,
                onSwipeOut = { diffX -> triggerSlideOut(1, diffX) }
            )
        }
    }
}

// ==================== 单个方块组件 ====================
@Composable
private fun SingleSkillCard(
    skill: SkillData,
    isTall: Boolean,
    pixelFont: FontFamily,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onSwipeOut: (Float) -> Unit
) {
    var totalSwipeX by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (isTall) 140.dp else 52.dp)
            .background(Color(0xFFF6F6F6), RoundedCornerShape(8.dp))
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongPress() }
                )
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { _, dragAmount ->
                        totalSwipeX += dragAmount
                    },
                    onDragEnd = {
                        if (kotlin.math.abs(totalSwipeX) > 60f) {
                            onSwipeOut(totalSwipeX)
                        }
                        totalSwipeX = 0f
                    },
                    onDragCancel = {
                        totalSwipeX = 0f
                    }
                )
            }
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount)
                    },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() }
                )
            }
            .padding(horizontal = 10.dp, vertical = if (isTall) 8.dp else 6.dp),
        contentAlignment = if (isTall) Alignment.TopStart else Alignment.CenterStart
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = if (isTall) Arrangement.Top else Arrangement.Center
        ) {
            if (isTall) {
                RobustSkillImage(
                    skill = skill,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(56.dp),
                    isLarge = false,
                    pixelFont = pixelFont
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Text(
                text = skill.title,
                fontSize = 14.sp,
                fontFamily = pixelFont,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF222222),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = skill.desc,
                fontSize = 11.sp,
                fontFamily = pixelFont,
                color = Color(0xFF888888),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ==================== 右上角震源波动动画图标 ====================
@Composable
internal fun EpicenterIcon(
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    val transition = rememberInfiniteTransition(label = "epicenter")
    val wave1 by transition.waveRingProgress(initialDelay = 0)
    val wave2 by transition.waveRingProgress(initialDelay = 600)
    val wave3 by transition.waveRingProgress(initialDelay = 1200)

    Box(
        modifier = modifier
            .size(32.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        WaveRing(progress = wave1)
        WaveRing(progress = wave2)
        WaveRing(progress = wave3)

        Box(
            modifier = Modifier
                .size(6.dp)
                .background(Color(0xFF181717), CircleShape)
                .zIndex(2f)
        )
    }
}

@Composable
private fun InfiniteTransition.waveRingProgress(
    initialDelay: Int
): State<Float> = animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
        animation = tween(1800, delayMillis = initialDelay, easing = LinearEasing),
        repeatMode = RepeatMode.Restart
    ),
    label = "waveProgress"
)

@Composable
private fun WaveRing(progress: Float) {
    if (progress <= 0.01f || progress >= 0.99f) return
    val scale = 0.2f + (1.35f - 0.2f) * progress
    val alpha = if (progress < 0.6f) 0.85f - 0.45f * (progress / 0.6f) else 0.4f * (1f - (progress - 0.6f) / 0.4f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .scale(scale)
            .border(1.5.dp, Color(0xFF181717).copy(alpha = alpha.coerceIn(0f, 1f)), CircleShape)
    )
}

// ==================== GITHUB 矢量图绘图组件 ====================
@Composable
fun GitHubBrandIcon(
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF181717)
) {
    Canvas(modifier = modifier) {
        val scale = size.minDimension / 24f
        val path = Path().apply {
            moveTo(12f * scale, 0f * scale)
            cubicTo(5.37f * scale, 0f * scale, 0f * scale, 5.37f * scale, 0f * scale, 12f * scale)
            cubicTo(0f * scale, 17.31f * scale, 3.435f * scale, 21.795f * scale, 8.205f * scale, 23.385f * scale)
            cubicTo(8.805f * scale, 23.49f * scale, 9.03f * scale, 23.13f * scale, 9.03f * scale, 22.815f * scale)
            cubicTo(9.03f * scale, 22.53f * scale, 9.015f * scale, 21.585f * scale, 9.015f * scale, 20.58f * scale)
            cubicTo(6f * scale, 21.135f * scale, 5.22f * scale, 19.845f * scale, 4.98f * scale, 19.17f * scale)
            cubicTo(4.845f * scale, 18.825f * scale, 4.26f * scale, 17.76f * scale, 3.75f * scale, 17.475f * scale)
            cubicTo(3.33f * scale, 17.25f * scale, 2.73f * scale, 16.695f * scale, 3.735f * scale, 16.68f * scale)
            cubicTo(4.68f * scale, 16.665f * scale, 5.355f * scale, 17.55f * scale, 5.58f * scale, 17.91f * scale)
            cubicTo(6.66f * scale, 19.725f * scale, 8.385f * scale, 19.215f * scale, 9.075f * scale, 18.9f * scale)
            cubicTo(9.18f * scale, 18.12f * scale, 9.495f * scale, 17.595f * scale, 9.84f * scale, 17.295f * scale)
            cubicTo(7.17f * scale, 16.995f * scale, 4.38f * scale, 15.96f * scale, 4.38f * scale, 11.37f * scale)
            cubicTo(4.38f * scale, 10.065f * scale, 4.845f * scale, 8.985f * scale, 5.61f * scale, 8.145f * scale)
            cubicTo(5.49f * scale, 7.845f * scale, 5.07f * scale, 6.615f * scale, 5.73f * scale, 4.965f * scale)
            cubicTo(5.73f * scale, 4.965f * scale, 6.735f * scale, 4.65f * scale, 9.03f * scale, 6.195f * scale)
            cubicTo(9.99f * scale, 5.925f * scale, 11.01f * scale, 5.79f * scale, 12.03f * scale, 5.79f * scale)
            cubicTo(13.05f * scale, 5.79f * scale, 14.07f * scale, 5.925f * scale, 15.03f * scale, 6.195f * scale)
            cubicTo(17.325f * scale, 4.635f * scale, 18.33f * scale, 4.965f * scale, 18.33f * scale, 4.965f * scale)
            cubicTo(18.99f * scale, 6.615f * scale, 18.57f * scale, 7.845f * scale, 18.45f * scale, 8.145f * scale)
            cubicTo(19.215f * scale, 8.985f * scale, 19.68f * scale, 10.05f * scale, 19.68f * scale, 11.37f * scale)
            cubicTo(19.68f * scale, 15.975f * scale, 16.875f * scale, 16.995f * scale, 14.205f * scale, 17.295f * scale)
            cubicTo(14.64f * scale, 17.67f * scale, 15.015f * scale, 18.39f * scale, 15.015f * scale, 19.515f * scale)
            cubicTo(15.015f * scale, 21.12f * scale, 15f * scale, 22.41f * scale, 15f * scale, 22.815f * scale)
            cubicTo(15f * scale, 23.13f * scale, 15.225f * scale, 23.505f * scale, 15.825f * scale, 23.385f * scale)
            cubicTo(20.595f * scale, 21.795f * scale, 24.03f * scale, 17.31f * scale, 24.03f * scale, 12f * scale)
            cubicTo(24.03f * scale, 5.37f * scale, 18.66f * scale, 0f * scale, 12f * scale, 0f * scale)
            close()
        }
        drawPath(path = path, color = color, style = Fill)
    }
}
