package org.example.project

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.*
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import androidx.compose.foundation.ScrollState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun BoxScope.WarmupOverlay(
    pixelFont: FontFamily,
    pixelBitmap: ImageBitmap,
    screenWidth: Dp,
    screenHeight: Dp
) {
    val density = LocalDensity.current
    Box(
        modifier = Modifier
            .size(200.dp, 100.dp)
            .graphicsLayer { alpha = 0.001f }
            .align(Alignment.TopEnd)
    ) {
        val warmupMsg = remember {
            MessageData(0L, "Warmup: abcdefghijklmnopqrstuvwxyz ABCDEFGHIJKLMNOPQRSTUVWXYZ 0123456789 是一我在有地他国个中要工的时理 の 하 !?_", "12:00", 120f, true)
        }

        val glowPath = remember { Path() }
        val glowPathMeasure = remember { PathMeasure() }
        val segPath = remember { Path() }
        val glowStrokes = remember(density) {
            with(density) { listOf(Stroke(width = 6.dp.toPx()), Stroke(width = 2.dp.toPx())) }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF161619), RoundedCornerShape(12.dp))
                .drawWithCache {
                    glowPath.addRoundRect(RoundRect(Rect(Offset.Zero, size), CornerRadius(12.dp.toPx())))
                    glowPathMeasure.setPath(glowPath, false)
                    onDrawWithContent {
                        drawContent()
                        drawGlowSweep(glowPathMeasure, segPath, 180f, glowStrokes[0], glowStrokes[1])
                    }
                }
        ) {
            Text(text = warmupMsg.text, color = Color.White, fontSize = 14.sp, fontFamily = pixelFont)
            Text(
                text = warmupMsg.time,
                modifier = Modifier.graphicsLayer {
                    translationY = (-8).dp.toPx()
                    transformOrigin = TransformOrigin.Center
                }
            )
        }

        FlyingMessageBox(
            msg = warmupMsg,
            flyAnimProvider = { 0.5f },
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            flyingStartBounds = Rect(0f, 0f, 300f, 100f),
            pixelFont = pixelFont,
            targetYDp = 40.dp
        )

        Box(
            modifier = Modifier.matchParentSize().drawWithContent {
                val pngHeightPx = size.height
                val clipBottom = (0.5f * pngHeightPx).coerceIn(0f, pngHeightPx)
                clipRect(0f, 0f, size.width, clipBottom) {
                    drawRect(color = BgBlack)
                    this@drawWithContent.drawContent()
                    drawRect(color = FogWhite)
                }
            },
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = pixelBitmap,
                contentDescription = "Warmup Mascot Small",
                modifier = Modifier.size(180.dp),
                contentScale = ContentScale.Fit,
                filterQuality = FilterQuality.None
            )
        }
    }
}

@Composable
fun BoxScope.MessageList(
    state: ChatUiState,
    messageScrollState: ScrollState,
    pixelFont: FontFamily,
    screenHeight: Dp,
    planRecord: PlanRecord?
) {
    val density = LocalDensity.current

    Column(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp)
            .heightIn(max = (screenHeight - 130.dp).coerceAtLeast(0.dp))
            .verticalScroll(messageScrollState)
            .padding(top = 40.dp),
        horizontalAlignment = Alignment.Start
    ) {
        if (state.displayedMessages.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset { IntOffset(2000, 0) }
                        .width(200.dp)
                        .padding(bottom = 8.dp)
                ) {
                    val glowPath = remember { Path() }
                    val glowPathMeasure = remember { PathMeasure() }
                    val segPath = remember { Path() }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF161619), RoundedCornerShape(12.dp))
                            .drawWithCache {
                                glowPath.addRoundRect(RoundRect(Rect(Offset.Zero, size), CornerRadius(12.dp.toPx())))
                                glowPathMeasure.setPath(glowPath, false)
                                onDrawWithContent {
                                    drawContent()
                                    repeat(15) {
                                        segPath.reset()
                                        glowPathMeasure.getSegment(0f, 10f, segPath, true)
                                        drawPath(segPath, Color.White.copy(alpha = 0.5f), style = Stroke(6.dp.toPx()))
                                        drawPath(segPath, Color.White.copy(alpha = 1f), style = Stroke(2.dp.toPx()))
                                    }
                                }
                            }
                            .border(1.dp, Color(0xFF4A4A4F), RoundedCornerShape(12.dp))
                            .padding(10.dp)
                    ) {
                        Text(text = "Warmup", color = Color.White, fontSize = 14.sp, fontFamily = pixelFont)
                    }
                }
            }
        }

        LaunchedEffect(
            state.displayedMessages.size,
            state.toolCalls.size,
            planRecord?.steps?.size ?: 0
        ) {
            withFrameNanos { }
            if (state.flyingMessage != null) {
                messageScrollState.scrollTo(messageScrollState.maxValue)
            } else {
                messageScrollState.animateScrollTo(messageScrollState.maxValue)
            }
        }

        state.displayedMessages.forEach { msgData ->
            key(msgData.id) {
                val sweepAnimatable = remember { Animatable(0f) }
                val timeAnimatable = remember { Animatable(0f) }
                val contentAlphaAnimatable = remember { Animatable(1f) }
                var animPlayed by remember { mutableStateOf(false) }

                val glowStrokes = remember(density) {
                    with(density) { listOf(Stroke(width = 6.dp.toPx()), Stroke(width = 2.dp.toPx())) }
                }

                val revealReady = msgData.isLanded && !msgData.isThinking

                LaunchedEffect(revealReady) {
                    if (revealReady && !animPlayed) {
                        animPlayed = true
                        if (!msgData.isUser) {
                            contentAlphaAnimatable.snapTo(0f)
                            launch {
                                contentAlphaAnimatable.animateTo(1f, tween(320, easing = ElegantGlideEasing))
                            }
                        }
                        delay(300)
                        launch { timeAnimatable.animateTo(1f, tween(400)) }
                        launch { sweepAnimatable.animateTo(360f, tween(1000, easing = LinearEasing)) }
                    }
                }

                val glowPath = remember { Path() }
                val glowPathMeasure = remember { PathMeasure() }
                val segPath = remember { Path() }
                val bubbleAlignment = if (msgData.isUser) Alignment.TopEnd else Alignment.TopStart

                val isBlankAi = !msgData.isUser && !msgData.isThinking && msgData.text.isBlank()

                Box(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    if (!isBlankAi) {
                        Box(
                            modifier = Modifier
                                .width(200.dp)
                                .align(bubbleAlignment)
                                .onGloballyPositioned { coordinates ->
                                    if (msgData.isUser && !msgData.isLanded) {
                                        state.messageTargetCoordinates[msgData.id] = coordinates
                                    }
                                }
                                .graphicsLayer {
                                    alpha = when {
                                        !msgData.isLanded -> 0f
                                        msgData.isThinking -> 1f
                                        !msgData.isUser && !animPlayed -> 0f
                                        else -> contentAlphaAnimatable.value
                                    }
                                }
                                .background(
                                    color = when {
                                        msgData.isUser -> Color(0xFF161619)
                                        msgData.isThinking -> Color.Black
                                        else -> Color(0xFF222228)
                                    },
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .drawWithCache {
                                    glowPath.reset()
                                    glowPath.addRoundRect(RoundRect(Rect(Offset.Zero, size), CornerRadius(12.dp.toPx())))
                                    glowPathMeasure.setPath(glowPath, false)
                                    onDrawWithContent {
                                        drawContent()
                                        drawGlowSweep(glowPathMeasure, segPath, sweepAnimatable.value, glowStrokes[0], glowStrokes[1])
                                    }
                                }
                                .border(1.dp, Color(0xFF4A4A4F), RoundedCornerShape(12.dp))
                                .padding(10.dp)
                        ) {
                            Text(text = msgData.text, color = Color.White, fontSize = 14.sp, fontFamily = pixelFont)
                        }

                        if (!msgData.isThinking) {
                            Text(
                                text = msgData.time,
                                color = Color.White,
                                fontSize = 10.sp,
                                fontFamily = pixelFont,
                                modifier = Modifier
                                    .align(bubbleAlignment)
                                    .padding(
                                        end = if (msgData.isUser) 4.dp else 0.dp,
                                        start = if (msgData.isUser) 0.dp else 4.dp
                                    )
                                    .zIndex(1f)
                                    .graphicsLayer {
                                        val tVal = timeAnimatable.value
                                        alpha = if (msgData.isLanded) tVal * contentAlphaAnimatable.value else 0f
                                        translationY = lerp(20.dp.toPx(), (-12).dp.toPx(), tVal)
                                        scaleX = lerp(0.6f, 1f, tVal)
                                        scaleY = lerp(0.01f, 1f, tVal)
                                        transformOrigin = TransformOrigin.Center
                                    }
                            )
                        }
                    }
                    // 任务15：空 AI 气泡（纯工具调用占位）不渲染可见内容，
                    // 工具框（带 AI 标识头）渲染在其下方，视觉上替换聊天气泡
                }
            }

            // 任务11（重构）：工具/plan 气泡内联渲染 —— 锚定在触发工具调用的 AI 消息下方，
            // 随消息流一起滚动、永远可见。原方案在 App 层用 root 坐标 offset 定位：
            // messageTargetCoordinates 只登记"飞行中"的用户消息（落定即移除），AI 消息
            // 永远查不到坐标 → coordsValid 恒为 false → 气泡完全看不到。此处彻底改为
            // 列表内联渲染，不依赖任何坐标计算。
            // 任务17：plan 与工具气泡分开锚定 —— plan 用 planAnchorMessageId（首次锁定，
            // 不随后续工具执行瞬移），工具气泡用 toolAnchorMessageId（跟随最新工具）。
            if (msgData.id == state.planAnchorMessageId && planRecord != null) {
                ToolCallFeed(
                    toolCalls = emptyList(),
                    planRecord = planRecord,
                    pixelFont = pixelFont,
                    aiTextEmpty = msgData.text.isBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    introTimeText = msgData.time
                )
            }
            // 任务18（重构）：按每条记录的 anchorMessageId 过滤 ——
            // 一个回复里的每一次工具调用各渲染一个气泡（修复"只能有一个气泡"）
            val anchoredToolCalls = state.toolCalls.filter { it.anchorMessageId == msgData.id }
            if (anchoredToolCalls.isNotEmpty()) {
                ToolCallFeed(
                    toolCalls = anchoredToolCalls,
                    planRecord = null,
                    pixelFont = pixelFont,
                    aiTextEmpty = msgData.text.isBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )
            }
        }

        // AI 纯工具调用（回复无可见文本）：空 AI 气泡为隐形占位 → 锚点仍在列表中，
        // 对应内联渲染分支已处理。若锚点不在列表中（消息被裁剪），在列表末尾兜底渲染。
        val planAnchorInList = state.displayedMessages.any { it.id == state.planAnchorMessageId }
        if (!planAnchorInList && planRecord != null) {
            ToolCallFeed(
                toolCalls = emptyList(),
                planRecord = planRecord,
                pixelFont = pixelFont,
                aiTextEmpty = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )
        }
        // 任务18（重构）：锚点消息不在列表中（被裁剪）时，孤儿记录兜底渲染在列表末尾
        val orphanToolCalls = state.toolCalls.filter { rec ->
            state.displayedMessages.none { it.id == rec.anchorMessageId }
        }
        if (orphanToolCalls.isNotEmpty()) {
            ToolCallFeed(
                toolCalls = orphanToolCalls,
                planRecord = null,
                pixelFont = pixelFont,
                aiTextEmpty = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )
        }
    }
}
