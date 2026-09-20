package org.example.project

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.material3.Text
import androidx.compose.ui.unit.*
import androidx.compose.ui.zIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.drawscope.clipRect

import org.example.project.generated.resources.*
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.imageResource

@Composable
fun App(
    currentLanguageCode: String = "en",
    onConfirmLanguageAndExit: (String) -> Unit = {}
) {
    val hasExistingHistory = remember { ConversationStore.messages.value.isNotEmpty() }
    val state = remember { ChatUiState(hasExistingHistory) }

    // 任务8：注册 plan 等 AI 工具（幂等），并初始化工具桥
    remember { AiToolBridge.ensureInitialized() }

    SetupSystemUI(LocalView.current)
    val density = LocalDensity.current

    val pixelFont = FontFamily(
        Font(Res.font.fusion_pixel_10px_monospaced_zh_hans),
        Font(Res.font.fusion_pixel_10px_monospaced_zh_hant),
        Font(Res.font.fusion_pixel_10px_monospaced_ja),
        Font(Res.font.fusion_pixel_10px_monospaced_ko),
        Font(Res.font.fusion_pixel_10px_monospaced_latin)
    )

    val fixedMessage = uiText(UiText.WhatDoYouWantToDo)
    val textMeasurer = rememberTextMeasurer()
    val scope = rememberCoroutineScope()
    val messageScrollState = rememberScrollState()

    val infiniteTransition = rememberInfiniteTransition()
    val pixelYAnim by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 4f,
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Restart)
    )

    val storedConversationMessages by ConversationStore.messages.collectAsState()
    val conversationLoaded by ConversationStore.isLoaded.collectAsState()
    val selectedConversationId by ConversationStore.currentConversationId.collectAsState()

    // 任务8：plan 展示状态（AI 调用 plan 工具时更新）
    val planRecord by PlanStore.current.collectAsState()

    // 冷启动以及点击历史会话卡片时执行
    LaunchedEffect(conversationLoaded, selectedConversationId) {
        state.restoreConversation(
            conversationLoaded = conversationLoaded,
            selectedConversationId = selectedConversationId,
            storedConversationMessages = storedConversationMessages,
            density = density,
            pixelFont = pixelFont,
            textMeasurer = textMeasurer
        )
    }

    LaunchedEffect(state.welcomeResetToken, state.showInitialWelcome) {
        if (hasExistingHistory && state.welcomeResetToken == 0) return@LaunchedEffect

        launch {
            if (state.flyAnimVal.value != 0f) state.flyAnimVal.snapTo(0f)
            state.flyAnimVal.animateTo(1f, tween(1400, easing = ElegantGlideEasing))
            state.flyAnimVal.snapTo(0f)
        }

        if (state.typedText.length < fixedMessage.length) {
            for (i in (state.typedText.length + 1)..fixedMessage.length) {
                state.typedText = fixedMessage.substring(0, i)
                delay(40)
            }
        }

        delay(800)
        state.isWarmingUp = false
        state.typingFinished = true
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(state.activeCanvas, state.showSpaceBlueCanvas, state.showBottomUpCanvas) {
                if (state.activeCanvas != null || state.showSpaceBlueCanvas || state.showBottomUpCanvas) {
                    return@pointerInput
                }
                awaitPointerEventScope {
                    // 顶部下拉画布：只有从屏幕顶部区域（顶部 200dp 内）下滑才触发
                    val topTriggerZonePx = 200.dp.toPx()
                    // 底部上滑画布：只有从屏幕底部区域（底部 200dp 内）上滑才触发
                    val bottomTriggerZonePx = 200.dp.toPx()
                    while (true) {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        val downStartY = down.position.y
                        var accumulatedX = 0f
                        var accumulatedY = 0f
                        var swiped = false
                        var dragEvent = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull()

                        do {
                            val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                            dragEvent = event.changes.firstOrNull()
                            if (dragEvent != null && dragEvent.pressed) {
                                accumulatedX += dragEvent.position.x - dragEvent.previousPosition.x
                                accumulatedY += dragEvent.position.y - dragEvent.previousPosition.y

                                val threshold = 55.dp.toPx()
                                val absX = abs(accumulatedX)
                                val absY = abs(accumulatedY)

                                if (absX > threshold && absX > absY * 1.5f) {
                                    state.activeCanvas = if (accumulatedX < 0) "white" else "black"
                                    swiped = true
                                    event.changes.forEach { it.consume() }
                                    break
                                } else if (accumulatedY > threshold && absY > absX * 1.5f &&
                                    downStartY <= topTriggerZonePx
                                ) {
                                    state.showSpaceBlueCanvas = true
                                    swiped = true
                                    event.changes.forEach { it.consume() }
                                    break
                                } else if (accumulatedY < -threshold && absY > absX * 1.5f &&
                                    downStartY >= size.height - bottomTriggerZonePx
                                ) {
                                    state.showBottomUpCanvas = true
                                    swiped = true
                                    event.changes.forEach { it.consume() }
                                    break
                                }
                            }
                        } while (dragEvent != null && dragEvent.pressed && !swiped)
                    }
                }
            }
    ) {
        val screenHeight = maxHeight
        val screenWidth = maxWidth
        val screenHeightPx = with(density) { screenHeight.toPx() }
        val stepPx = with(density) { 11.dp.toPx() }
        val pixelBitmap = imageResource(Res.drawable.pixel_16x16)

        if (state.isWarmingUp) {
            WarmupOverlay(pixelFont, pixelBitmap, screenWidth, screenHeight)
        }

        val sent = state.sweepKey > 0

        LaunchedEffect(sent) {
            if (sent && !state.showCustomStatusBar) {
                state.showCustomStatusBar = true
                Animatable(0f).animateTo(1f, tween(500)) { state.statusAnim = value }
            }
        }

        val subtitleBasePx = with(density) { (-177).dp.toPx() }
        val step4Px = with(density) { 4.dp.toPx() }

        LaunchedEffect(state.sweepKey) {
            if (state.sweepKey == 1) {
                launch {
                    while (state.typedText.isNotEmpty()) {
                        state.typedText = state.typedText.dropLast(1)
                        delay(40)
                    }
                }
                state.sweepY = 0f
                val totalSteps = (screenHeightPx / stepPx).toInt().coerceAtLeast(1)
                for (i in 1..totalSteps) {
                    state.sweepY = i * stepPx
                    delay(35L)
                }
                state.sweepY = screenHeightPx
            }
        }

        val boxOffset by animateDpAsState(
            targetValue = if (state.inputAreaCompacted) (-24).dp else (-(screenHeight / 2f) + 260.dp),
            animationSpec = tween(if (state.inputAreaCompacted && !state.hasInputAreaCompactedOnce) 560 else 250, easing = PeelOffEasing),
            label = "boxOffset"
        )
        val boxHeight by animateDpAsState(
            targetValue = if (state.inputAreaCompacted) 80.dp else 100.dp,
            animationSpec = tween(if (state.inputAreaCompacted && !state.hasInputAreaCompactedOnce) 560 else 250, easing = PeelOffEasing),
            label = "boxHeight"
        )

        Box(
            modifier = Modifier.fillMaxSize().drawWithContent {
                drawRect(color = BgBlack)
                if (state.sweepY > 0f) {
                    drawRect(color = FogWhite, topLeft = Offset.Zero, size = Size(size.width, state.sweepY))
                }
            }
        )

        Box(modifier = Modifier.align(Alignment.Center).size(300.dp), contentAlignment = Alignment.Center) {
            Image(
                bitmap = pixelBitmap,
                contentDescription = "Giant Mascot",
                modifier = Modifier.size(300.dp),
                contentScale = ContentScale.Fit,
                filterQuality = FilterQuality.None
            )

            Box(
                modifier = Modifier.matchParentSize()
                    .graphicsLayer { alpha = if (sent) 1f else 0f }
                    .drawWithContent {
                        if (!sent) return@drawWithContent
                        val pngHeightPx = size.height
                        val pngTopOffsetPx = (screenHeightPx - pngHeightPx) / 2f
                        val localLineY = state.sweepY - pngTopOffsetPx
                        val clipBottom = localLineY.coerceIn(0f, pngHeightPx)
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
                    contentDescription = "Mascot Small",
                    modifier = Modifier.size(180.dp),
                    contentScale = ContentScale.Fit,
                    filterQuality = FilterQuality.None
                )
            }
        }

        if (state.showInitialWelcome && state.displayedMessages.isEmpty()) {
            TypedTextDisplay(
                typedText = state.typedText,
                typingFinished = state.typingFinished,
                pixelFont = pixelFont,
                pixelYAnim = pixelYAnim,
                subtitleBasePx = subtitleBasePx,
                step4Px = step4Px
            )
        }

        if (state.showInitialWelcome && state.displayedMessages.isEmpty()) {
            GreenBottomSubtitle(pixelFont = pixelFont)
        }
        GreenSharpSideSliders(trigger = state.firstSentTrigger)

        InputArea(
            sent = state.inputAreaCompacted,
            typingFinished = state.typingFinished,
            boxOffset = boxOffset,
            boxHeight = boxHeight,
            pixelFont = pixelFont,
            arrowPhase = state.arrowPhase,
            onArrowClick = {
                // 箭头点击桥接：Spin ↔ Paused，并同步暂停/继续状态
                // （Pop 完成时 InputArea 也走这里进入 Spin = AI 开始输出）
                state.arrowPhase = when (state.arrowPhase) {
                    ArrowPhase.Spin -> {
                        state.isOutputPaused = true
                        ArrowPhase.Paused
                    }
                    ArrowPhase.Paused -> {
                        state.isOutputPaused = false
                        ArrowPhase.Spin
                    }
                    ArrowPhase.Pop -> {
                        state.isOutputPaused = false
                        ArrowPhase.Spin
                    }
                    ArrowPhase.Idle -> ArrowPhase.Idle
                }
            },
            onArrowLongPress = { newMsg ->
                // 长按重发：先在主线程拿稳旧 Job 再覆盖 currentSendJob，
                // 防止新协程抢先启动后把自己当成旧流程掐掉
                state.flyingStartBounds = state.currentInputBoxBounds
                state.flyingTargetBounds = null
                val previousJob = state.currentSendJob
                state.currentSendJob = scope.launch(Dispatchers.Default) {
                    state.resendReplacingLast(
                        newMsg = newMsg,
                        previousJob = previousJob,
                        density = density,
                        pixelFont = pixelFont,
                        textMeasurer = textMeasurer,
                        messageScrollState = messageScrollState,
                        scope = scope
                    )
                }
            },
            onPositioned = {
                state.currentInputBoxBounds = it
                if (state.flyingStartBounds.width == 0f) state.flyingStartBounds = it
            },
            onSend = { msg ->
    state.flyingStartBounds = state.currentInputBoxBounds
    state.flyingTargetBounds = null
    state.currentSendJob = scope.launch(Dispatchers.Default) {
        state.handleSend(
            msg = msg,
            density = density,
            pixelFont = pixelFont,
            textMeasurer = textMeasurer,
            messageScrollState = messageScrollState,
            scope = scope        // ← 传入外层 scope
        )
    }
}
        )

        Box(
            modifier = Modifier
                .zIndex(8f)
                .fillMaxWidth()
                .height(30.dp)
                .background(Color.Black)
                .align(Alignment.TopCenter)
                .graphicsLayer { alpha = if (state.showCustomStatusBar) 1f else 0f }
                .offset { IntOffset(0, ((1f - state.statusAnim) * -30.dp.toPx()).roundToInt()) }
        ) {
            Text(text = "PUSea", color = Color.White, fontSize = 14.sp, fontFamily = pixelFont, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterStart).padding(start = 8.dp, top = 6.dp))
            Text(text = "beta", color = Color.White, fontSize = 10.sp, fontFamily = pixelFont, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 2.dp))
        }

        MessageList(state, messageScrollState, pixelFont, screenHeight, planRecord)

        // 任务11（重构）：工具/plan 气泡改为在 MessageList 内联渲染 ——
        // 直接嵌在触发工具调用的 AI 消息下方（纯工具调用时渲染在列表末尾），
        // 随消息流一起滚动。彻底移除原 root 坐标 offset 定位方案：
        // 原方案依赖 messageTargetCoordinates（仅登记飞行中的用户消息，落定即移除），
        // AI 消息永远查不到坐标 → coordsValid 恒为 false → 工具气泡完全看不到。

        val phantomFlyMsg = remember { MessageData(1L, "Warmup", "00:00", 80f, true) }
        Box(modifier = Modifier.zIndex(3f).graphicsLayer { alpha = if (state.flyingMessage != null) 1f else 0.001f }) {
            FlyingMessageBox(
                msg = state.flyingMessage ?: phantomFlyMsg,
                flyAnimProvider = { state.flyAnimVal.value },
                screenWidth = screenWidth,
                screenHeight = screenHeight,
                flyingStartBounds = state.flyingStartBounds,
                pixelFont = pixelFont,
                targetYDp = state.targetYDp,
                targetBounds = state.flyingTargetBounds,
                startCornerDp = if (!state.isFirstSend) 16.dp else 14.dp
            )
        }

        Box(modifier = Modifier.zIndex(10f)) {
            GreenLuaSwipeCanvas(
                activeCanvas = state.activeCanvas,
                onActiveCanvasChange = { state.activeCanvas = it },
                pixelFont = pixelFont,
                currentLanguageCode = currentLanguageCode,
                onConfirmLanguageAndExit = onConfirmLanguageAndExit
            )
            SpaceBlueGrayPullDownCanvas(
                visible = state.showSpaceBlueCanvas,
                onClose = { state.showSpaceBlueCanvas = false },
                pixelFont = pixelFont
            )
            BottomUpSlideCanvas(
                visible = state.showBottomUpCanvas,
                onClose = { state.showBottomUpCanvas = false },
                pixelFont = pixelFont
            )
        }
    }
}
