package org.example.project

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.*
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun BoxScope.TypedTextDisplay(
    typedText: String,
    typingFinished: Boolean,
    pixelFont: FontFamily,
    pixelYAnim: Float,
    subtitleBasePx: Float,
    step4Px: Float
) {
    Text(
        text = typedText,
        color = Color(0xFF33B5E5),
        fontSize = 28.sp,
        lineHeight = 38.sp,
        fontFamily = pixelFont,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = Modifier.align(Alignment.Center).fillMaxWidth().height(76.dp).wrapContentHeight(Alignment.CenterVertically).padding(horizontal = 20.dp).graphicsLayer {
            translationY = if (typingFinished && typedText.isNotEmpty()) {
                val dy = when (((pixelYAnim.toInt() % 4) + 4) % 4) {
                    0 -> 0f; 1 -> -1f; 2 -> 0f; else -> 1f
                }
                subtitleBasePx + step4Px * dy
            } else subtitleBasePx
        }
    )
}

@Composable
fun BoxScope.InputArea(
    sent: Boolean,
    typingFinished: Boolean,
    boxOffset: Dp,
    boxHeight: Dp,
    pixelFont: FontFamily,
    arrowPhase: ArrowPhase = ArrowPhase.Idle,
    onPositioned: (Rect) -> Unit,
    onSend: (String) -> Unit,
    onArrowClick: () -> Unit = {},
    onArrowLongPress: (String) -> Unit = {}
) {
    var userInput by rememberSaveable { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    var cursorChar by remember { mutableStateOf("|") }

    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            cursorChar = if (cursorChar == "|") "_" else "|"
        }
    }
    LaunchedEffect(typingFinished) {
        if (typingFinished) focusRequester.requestFocus()
    }

    val useNativeCursor = sent && userInput.isNotEmpty()

    val cyberVisualTransformation = remember(cursorChar) {
        VisualTransformation { text ->
            val transformed = buildAnnotatedString {
                if (text.text.isEmpty()) {
                    withStyle(SpanStyle(color = Color(0xFF33B5E5), fontWeight = FontWeight.Bold)) { append(cursorChar) }
                    withStyle(SpanStyle(color = Color.Gray.copy(alpha = 0.5f), fontWeight = FontWeight.Normal)) { append(uiText(UiText.TypeYourAnswer)) }
                } else {
                    withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Normal)) { append(text.text) }
                    withStyle(SpanStyle(color = Color(0xFF33B5E5), fontWeight = FontWeight.Bold)) { append(cursorChar) }
                }
            }
            val offsetMapping = if (text.text.isEmpty()) NoOffsetMapping else object : OffsetMapping {
                override fun originalToTransformed(offset: Int): Int = offset
                override fun transformedToOriginal(offset: Int): Int = if (offset <= text.text.length) offset else text.text.length
            }
            TransformedText(transformed, offsetMapping)
        }
    }

    // ===== 右箭头状态机：发送 ➔ / 暂停·继续 三合一 =====
    // Idle ：静止白色 ➔，点击 = 发送
    // Pop  ：发送瞬间向右弹 ~7dp 再弹回（0.5s），播完经 onArrowClick 自动进入 Spin
    // Spin ：顺时针无限转圈 = AI 正在输出；点一下 → Paused
    // Paused：逆时针转回原位停住 = 暂停；再点一下 → Spin（继续输出）
    // AI 回复结束（Spin→Idle）：自动逆时针转回原位
    // Paused 长按：停止并重发 —— 新内容顶掉上一条消息，整轮重走 Pop→Spin
    val arrowAngle = remember { Animatable(0f) }
    val arrowShift = remember { Animatable(0f) }
    val density = LocalDensity.current
    val popDistancePx = with(density) { 7.dp.toPx() }

    // 长按反馈：按住时箭头微缩（暗示"这里按得住"），长按触发瞬间给一次系统震动
    val haptics = LocalHapticFeedback.current
    val arrowInteraction = remember { MutableInteractionSource() }
    val arrowPressed by arrowInteraction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (arrowPressed &&
            (arrowPhase == ArrowPhase.Paused || arrowPhase == ArrowPhase.Idle)
        ) 0.82f else 1f,
        animationSpec = tween(140, easing = FastOutSlowInEasing),
        label = "arrowPressScale"
    )

    LaunchedEffect(arrowPhase) {
        when (arrowPhase) {
            ArrowPhase.Pop -> {
                arrowShift.snapTo(0f)
                arrowShift.animateTo(1f, tween(200, easing = CubicBezierEasing(0.30f, 1.6f, 0.55f, 1f)))
                arrowShift.animateTo(0f, tween(300, easing = FastOutSlowInEasing))
                onArrowClick() // 右弹完毕 → 通知逻辑层进入 Spin，顺时针开转
            }
            ArrowPhase.Spin -> {
                // 顺时针（rotationZ 递增）匀速转圈，一圈 1.15s
                while (true) {
                    arrowAngle.animateTo(360f, tween(1150, easing = LinearEasing))
                    arrowAngle.snapTo(0f)
                }
            }
            ArrowPhase.Paused, ArrowPhase.Idle -> {
                // 逆时针回卷到原位：角度递减即逆时针，时长按剩余角度折算
                val rem = ((arrowAngle.value % 360f) + 360f) % 360f
                if (rem > 0.5f) {
                    arrowAngle.animateTo(
                        0f,
                        tween(
                            durationMillis = (rem / 360f * 800f).toInt().coerceIn(160, 800),
                            easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
                        )
                    )
                }
                arrowAngle.snapTo(0f)
                arrowShift.snapTo(0f)
            }
        }
    }

    Box(
        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 20.dp)
            .zIndex(1f)
            .offset { IntOffset(0, boxOffset.toPx().roundToInt()) }
            .then(Modifier.height(boxHeight))
            .onGloballyPositioned {
                val pos = it.positionInRoot()
                onPositioned(Rect(pos.x, pos.y, pos.x + it.size.width, pos.y + it.size.height))
            }
            .border(1.dp, Color(0xFF4A4A4F), RoundedCornerShape(12.dp))
            .background(Color(0xFF161619), RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        BasicTextField(
            value = userInput,
            onValueChange = { userInput = it },
            singleLine = false,
            visualTransformation = if (useNativeCursor) VisualTransformation.None else cyberVisualTransformation,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
            keyboardActions = KeyboardActions.Default,
            cursorBrush = if (useNativeCursor) SolidColor(Color(0xFF33B5E5)) else SolidColor(Color.Transparent),
            modifier = Modifier.align(Alignment.TopStart).fillMaxWidth().padding(end = 40.dp)
                .verticalScroll(rememberScrollState()).focusRequester(focusRequester),
            textStyle = TextStyle(
                color = Color.White,
                fontSize = 16.sp,
                fontFamily = pixelFont
            ),
            decorationBox = { it() }
        )
        // 发送 / 暂停 / 继续 三合一箭头：
        // 静止 = 发送；顺时针转圈中点一下 = 暂停（逆时针回原位）；暂停时点一下 = 继续（顺时针转起）
        val isWorking = arrowPhase == ArrowPhase.Spin || arrowPhase == ArrowPhase.Pop
        Text(
            text = "➔",
            color = when {
                arrowPhase == ArrowPhase.Paused -> Color(0xFF33B5E5) // 暂停 = 品牌青，一眼可辨
                isWorking -> Color.White                             // 输出中保持白色
                userInput.isBlank() -> Color(0xFF4A4A4F)             // 没内容 = 暗色
                else -> Color.White
            },
            fontSize = 22.sp,
            fontFamily = pixelFont,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 4.dp, bottom = 4.dp)
                .size(28.dp) // 固定方形包围盒，➔ 字形旋转时才不晃
                .graphicsLayer {
                    rotationZ = arrowAngle.value
                    translationX = arrowShift.value * popDistancePx
                    // 新版 Compose 移除了 GraphicsLayerScope.scale，拆成 X/Y
                    scaleX = pressScale
                    scaleY = pressScale
                }
                .combinedClickable(
                    interactionSource = arrowInteraction,
                    indication = null, // 像素风不要水波纹，按压反馈交给 pressScale
                    onClick = {
                        when (arrowPhase) {
                            ArrowPhase.Idle -> if (userInput.isNotBlank()) {
                                onSend(userInput.trim())
                                userInput = ""
                            }
                            ArrowPhase.Spin -> onArrowClick()   // 暂停 → Paused
                            ArrowPhase.Paused -> onArrowClick() // 继续 → Spin
                            ArrowPhase.Pop -> Unit              // 右弹过渡中，不响应
                        }
                    },
                    // 长按 = 停止并重发：仅暂停态有效 ——
                    // 用输入框新内容顶掉上一条消息，掐掉旧请求整轮重发
                    onLongClick = {
                        if (arrowPhase == ArrowPhase.Paused && userInput.isNotBlank()) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onArrowLongPress(userInput.trim())
                            userInput = ""
                        }
                    }
                )
        )
    }
}

@Composable
fun BoxScope.FlyingMessageBox(
    msg: MessageData,
    flyAnimProvider: () -> Float,
    screenWidth: Dp,
    screenHeight: Dp,
    flyingStartBounds: Rect,
    pixelFont: FontFamily,
    targetYDp: Dp,
    targetBounds: Rect? = null,
    startCornerDp: Dp = 16.dp
) {
    val density = LocalDensity.current
    val endW = 200.dp
    val endH = msg.heightDp.dp
    val borderStroke = remember(density) { Stroke(width = with(density) { 1.dp.toPx() }) }

    val metrics = remember(
        msg,
        screenWidth,
        screenHeight,
        flyingStartBounds,
        density,
        targetYDp,
        targetBounds
    ) {
        with(density) {
            val endX = screenWidth - endW - 20.dp
            FlightMetricsCache(
                startXPx = flyingStartBounds.left, startYPx = flyingStartBounds.top,
                startWPx = flyingStartBounds.width, startHPx = flyingStartBounds.height,
                endXPx = targetBounds?.left ?: endX.toPx(),
                endYPx = targetBounds?.top ?: targetYDp.toPx(),
                endWPx = targetBounds?.width ?: endW.toPx(),
                endHPx = targetBounds?.height ?: endH.toPx(),
                arcOffsetMax = 24.dp.toPx(), cornerRadiusPx = 16.dp.toPx()
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .layout { measurable, constraints ->
                val placeable = measurable.measure(
                    Constraints.fixed(metrics.endWPx.roundToInt(), metrics.endHPx.roundToInt())
                )
                layout(constraints.maxWidth, constraints.maxHeight) {
                    placeable.place(0, 0)
                }
            }
            .graphicsLayer {
                val flyAnim = flyAnimProvider()
                val arcOffset = metrics.arcOffsetMax * (1f - (2f * flyAnim - 1f).let { it * it })
                translationX = lerp(metrics.startXPx, metrics.endXPx, flyAnim)
                translationY = lerp(metrics.startYPx, metrics.endYPx, flyAnim) - arcOffset
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    val cornerRadiusPx = 12.dp.toPx()
                    onDrawWithContent {
                        val flyAnim = flyAnimProvider()
                        val curWPx = lerp(metrics.startWPx, metrics.endWPx, flyAnim)
                        val curHPx = lerp(metrics.startHPx, metrics.endHPx, flyAnim)
                        val curCorPx = lerp(startCornerDp.toPx(), cornerRadiusPx, flyAnim)
                        val currentRectSize = Size(curWPx, curHPx)

                        drawRoundRect(Color(0xFF161619), Offset.Zero, currentRectSize, CornerRadius(curCorPx))
                        clipRect(right = curWPx, bottom = curHPx) { this@onDrawWithContent.drawContent() }
                        drawRoundRect(Color(0xFF4A4A4F), Offset.Zero, currentRectSize, CornerRadius(curCorPx), style = borderStroke)
                    }
                }
                .padding(10.dp)
        ) {
            Box(modifier = Modifier.requiredSize(endW - 20.dp, (msg.heightDp - 20).dp).align(Alignment.TopStart)) {
                Text(text = msg.text, color = Color.White, fontSize = 14.sp, fontFamily = pixelFont)
            }
        }
    }
}
