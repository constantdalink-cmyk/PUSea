package org.example.project

import androidx.compose.animation.core.*
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.*
import kotlinx.coroutines.*
import kotlin.math.roundToInt

@Stable
class ChatUiState(hasExistingHistory: Boolean) {
    var showInitialWelcome by mutableStateOf(!hasExistingHistory)
    var welcomeResetToken by mutableStateOf(0)

    var typingFinished by mutableStateOf(hasExistingHistory)
    var typedText by mutableStateOf("")
    var isWarmingUp by mutableStateOf(!hasExistingHistory)

    var sweepKey by mutableStateOf(if (hasExistingHistory) 2 else 0)
    var sweepY by mutableStateOf(if (hasExistingHistory) 100000f else 0f)

    var displayedMessages by mutableStateOf(listOf<MessageData>())
    var flyingMessage by mutableStateOf<MessageData?>(null)
    val flyAnimVal = Animatable(0f)

    /** 任务8：AI 工具调用记录（UI 显示工具气泡） */
    var toolCalls by mutableStateOf(listOf<ToolCallRecord>())
    /** 任务11：工具气泡锚定的 AI 消息 id（工具气泡贴在这条 AI 消息下方，不随后续消息漂移） */
    var toolAnchorMessageId by mutableStateOf<Long?>(null)

    /** 任务17：plan 面板锚定的 AI 消息 id（plan 首次出现时锁定一次，
     *  不随后续工具执行而漂移 —— 修复"每执行一个工具，plan 瞬移一次"） */
    var planAnchorMessageId by mutableStateOf<Long?>(null)

    /** 任务12：AI 是否正在输出（输出期间禁止用户发送新消息） */
    var isAiResponding by mutableStateOf(false)
    /** 任务12：AI 输出是否被用户暂停（暂停期间增量先缓存，恢复后补放） */
    var isOutputPaused by mutableStateOf(false)

    /** 箭头状态机：idle → pop（右弹）→ spin（顺时针转圈=输出中）
     *  spin 点击一下 → paused（逆时针转回原位=暂停）
     *  paused 再点 → spin（顺时针转起=继续）
     *  AI 回复结束 → 自动逆时针回到 idle */
    var arrowPhase by mutableStateOf(ArrowPhase.Idle)

    /** 当前发送流程的协程 Job —— 长按重发时先掐掉上一轮 */
    var currentSendJob: Job? = null

    /** 发送代际：每次 handleSend 自增；旧流程残留的思考动画协程发现代数变化即自行退出 */
    var sendGeneration = 0L

    var showCustomStatusBar by mutableStateOf(hasExistingHistory)
    var isFirstSend by mutableStateOf(!hasExistingHistory)
    var inputAreaCompacted by mutableStateOf(hasExistingHistory)
    var hasInputAreaCompactedOnce by mutableStateOf(hasExistingHistory)
    var firstSentTrigger by mutableStateOf(hasExistingHistory)
    var statusAnim by mutableStateOf(if (hasExistingHistory) 1f else 0f)

    var restoredConversationId by mutableStateOf<String?>(null)
    var initialHistoryScrollPending by mutableStateOf(hasExistingHistory)

    var currentInputBoxBounds by mutableStateOf(Rect(0f, 0f, 0f, 0f))
    var flyingStartBounds by mutableStateOf(Rect(0f, 0f, 0f, 0f))
    var targetYDp by mutableStateOf(40.dp)

    var activeCanvas by mutableStateOf<String?>(null)
    var showSpaceBlueCanvas by mutableStateOf(false)
    var showBottomUpCanvas by mutableStateOf(false)

    var flyingTargetBounds by mutableStateOf<Rect?>(null)

    val messageTargetCoordinates = mutableMapOf<Long, LayoutCoordinates>()
}

// ==================== 会话恢复 ====================
suspend fun ChatUiState.restoreConversation(
    conversationLoaded: Boolean,
    selectedConversationId: String?,
    storedConversationMessages: List<ConversationMessageRecord>,
    density: Density,
    pixelFont: FontFamily,
    textMeasurer: TextMeasurer
) {
    if (!conversationLoaded || restoredConversationId == selectedConversationId) return

    if (storedConversationMessages.isEmpty()) {
        val shouldReplayWelcome = restoredConversationId != null

        displayedMessages = emptyList()
        showInitialWelcome = true
        typedText = ""
        typingFinished = false
        isWarmingUp = true
        isFirstSend = true
        inputAreaCompacted = false
        hasInputAreaCompactedOnce = false
        firstSentTrigger = false
        showCustomStatusBar = false
        statusAnim = 0f
        sweepKey = 0
        sweepY = 0f
        initialHistoryScrollPending = false
        // 任务11（重构）：切换会话时清掉残留工具气泡与锚点
        toolCalls = emptyList()
        toolAnchorMessageId = null
        planAnchorMessageId = null
        PlanStore.clear()
        restoredConversationId = selectedConversationId

        if (shouldReplayWelcome) welcomeResetToken += 1
        return
    }

    val restoredMessages = withContext(Dispatchers.Default) {
        val restoredTextStyle = TextStyle(fontSize = 14.sp, fontFamily = pixelFont)
        val restoredMaxWidthPx = with(density) { 180.dp.toPx().roundToInt() }

        storedConversationMessages
            .takeLast(ContextLimits.MAX_UI_MESSAGES)
            .mapNotNull { stored ->
                val isUserMessage = when (stored.role) {
                    "user" -> true
                    "assistant" -> false
                    else -> return@mapNotNull null
                }

                val layoutResult = textMeasurer.measure(
                    text = stored.content,
                    style = restoredTextStyle,
                    constraints = Constraints(maxWidth = restoredMaxWidthPx)
                )

                val measuredHeightDp = with(density) {
                    (layoutResult.size.height.toDp() + 20.dp).value.coerceAtLeast(50f)
                }

                MessageData(
                    id = stored.id,
                    text = stored.content,
                    time = stored.displayTime,
                    heightDp = measuredHeightDp,
                    isLanded = true,
                    isUser = isUserMessage,
                    isThinking = false,
                    skipRevealAnimation = true
                )
            }
    }

    displayedMessages = restoredMessages
    showInitialWelcome = false
    initialHistoryScrollPending = restoredMessages.isNotEmpty()
    // 任务22：从会话档案水合工具气泡与 plan（跨重启保留）
    toolCalls = ConversationStore.loadToolCallsForCurrent()
    toolAnchorMessageId = null
    val restoredPlan = ConversationStore.loadPlanForCurrent()
    if (restoredPlan != null && restoredPlan.first.isNotEmpty()) {
        planAnchorMessageId = restoredPlan.second
        // 恢复的旧 plan 不重播灯泡开场（灯泡只在第一次新调用时出现）
        PlanStore.bulbIntroShown = true
        PlanStore.showSteps(restoredPlan.first)
    } else {
        planAnchorMessageId = null
        PlanStore.clear()
    }

    if (restoredMessages.isNotEmpty()) {
        typingFinished = true
        isWarmingUp = false
        isFirstSend = false
        inputAreaCompacted = true
        hasInputAreaCompactedOnce = true
        firstSentTrigger = true
        showCustomStatusBar = true
        statusAnim = 1f
        sweepKey = 2
        sweepY = 100000f
    }

    restoredConversationId = selectedConversationId
}

// ==================== 发送消息 + 飞行动画 + AI 回复 ====================
suspend fun ChatUiState.handleSend(
    msg: String,
    replacedUserText: String? = null,
    density: Density,
    pixelFont: FontFamily,
    textMeasurer: TextMeasurer,
    messageScrollState: ScrollState,
    scope: CoroutineScope
) {
    val textStyle = TextStyle(fontSize = 14.sp, fontFamily = pixelFont)
    val maxTextWidthPx = with(density) { (200.dp - 20.dp).toPx() }
    val textLayoutResult = textMeasurer.measure(
        text = msg, style = textStyle,
        constraints = Constraints(maxWidth = maxTextWidthPx.roundToInt())
    )
    val textHeightDp = with(density) { textLayoutResult.size.height.toDp() }
    val finalHeightDp = (textHeightDp + 20.dp).value

    val msgData = MessageData(
        ConversationStore.allocateMessageId(),
        msg,
        getCurrentTime(),
        finalHeightDp,
        isLanded = false
    )

    withContext(Dispatchers.Main) {
        // 代际标记自增：若上一轮流程是被长按重发掐掉的，其残留协程（思考点点）据此退出
        val myGeneration = ++sendGeneration

        // 任务12：标记 AI 响应中，输出期间禁止发送新消息
        isAiResponding = true
        isOutputPaused = false
        // 箭头状态机入场：置 Pop（与 App 侧点击时的置位幂等）。
        // 注意不能直接置 Spin —— 本函数跑在 Default 线程，几毫秒内就会冲掉
        // InputArea 刚开播的右弹动画；Pop 播完后 InputArea 经 onArrowClick 转入 Spin
        arrowPhase = ArrowPhase.Pop

        // 任务18（重构）：plan 与工具气泡全部常驻 —— 新消息发送不再清空 PlanStore / 锚点。
        // 老 plan 面板留在原位置；本轮若出现新 plan，锚点才重新锁定到当前回复气泡
        // （见 showPlanLoading / showPlanSteps 的 planLockedThisSend 逻辑）
        if (flyAnimVal.value != 0f) flyAnimVal.snapTo(0f)
        targetYDp = 40.dp

        if (isFirstSend) {
            firstSentTrigger = true
            flyingMessage = msgData
            delay(16)
            displayedMessages = (displayedMessages + msgData).takeLast(ContextLimits.MAX_UI_MESSAGES)
            delay(16)
        } else {
            flyingMessage = msgData
            displayedMessages = (displayedMessages + msgData).takeLast(ContextLimits.MAX_UI_MESSAGES)
        }

        var targetWaitFrame = 0
        var stableTargetFrames = 0
        var previousScrollMax = -1

        while (targetWaitFrame < 12 && stableTargetFrames < 2) {
            withFrameNanos { }
            val currentScrollMax = messageScrollState.maxValue
            if (messageScrollState.value != currentScrollMax) {
                messageScrollState.scrollTo(currentScrollMax)
            }

            val coordinates = messageTargetCoordinates[msgData.id]
            stableTargetFrames = if (
                coordinates?.isAttached == true &&
                currentScrollMax == previousScrollMax &&
                messageScrollState.value == currentScrollMax
            ) stableTargetFrames + 1 else 0

            previousScrollMax = currentScrollMax
            targetWaitFrame++
        }

        val targetCoordinates = messageTargetCoordinates[msgData.id]
        if (targetCoordinates?.isAttached == true) {
            val targetPosition = targetCoordinates.positionInRoot()
            flyingTargetBounds = Rect(
                left = targetPosition.x, top = targetPosition.y,
                right = targetPosition.x + targetCoordinates.size.width,
                bottom = targetPosition.y + targetCoordinates.size.height
            )
        }

        withFrameNanos { }
        sweepKey++

        val flyDuration = if (isFirstSend) 1800 else 1650

        if (isFirstSend) {
            delay(380)
            inputAreaCompacted = true
            scope.launch {
                delay(620)
                hasInputAreaCompactedOnce = true
            }
        } else {
            inputAreaCompacted = true
            hasInputAreaCompactedOnce = true
        }

        isFirstSend = false

        flyAnimVal.animateTo(1f, tween(flyDuration, easing = ElegantGlideEasing))

        displayedMessages = displayedMessages.map {
            if (it.id == msgData.id) it.copy(isLanded = true) else it
        }
        messageTargetCoordinates.remove(msgData.id)
        flyingTargetBounds = null
        flyingMessage = null

        // ==================== [KEEPALIVE] 后台执行接线点 ====================
        // androidMain 启动时已向 BackgroundSendHook 注入执行器 → 飞入动画播完后，
        // 整段 AI 执行（用户消息持久化 / 流式 / 工具循环 / 回复落盘 / UI 投影）
        // 交给前台服务常驻运行，handleSend 随即返回 —— 退后台、息屏、被杀都不掉线。
        // 用户气泡由上面的飞入动画挂上（桥侧 addUserBubble=false，不重复）。
        // 未注入（executor == null）→ 跳过此分支，继续走下方原有内联逻辑，行为不变。
        BackgroundSendHook.executor?.let { exec ->
            exec.execute(this@handleSend, msg, density, pixelFont, textMeasurer)
            return@withContext
        }
        // ================================================================

        ConversationStore.append(
            ConversationMessageRecord(
                id = msgData.id, role = "user", content = msg, displayTime = msgData.time
            )
        )

        val requestHistory = ConversationStore.buildContextWindow(
            maxMessages = ContextLimits.MAX_API_MESSAGES,
            maxCharacters = ContextLimits.MAX_API_CHARS,
            maxSingleMessageChars = ContextLimits.MAX_SINGLE_MESSAGE_CHARS
        )

        // 长按重发的上下文重建：存储里的旧消息暂时没法原地改写（ConversationStore
        // 目前只暴露 append），所以本轮直接从"手术后的消息流"重建对话词条 ——
        // 旧消息已被移除、新消息刚飞进列表，顺序与内容即真实上下文。
        // ⚠ 持久层缝线：拿到 ConversationStore 文件后补一个 replace/update 方法，
        //   把存储中的旧用户消息原地改写，此分支即可退役（否则跨重启旧消息会复活）。
        val overrideConversationTurns = replacedUserText?.let {
            displayedMessages
                .filter { msgItem -> !msgItem.isThinking && msgItem.text.isNotBlank() }
                .takeLast(ContextLimits.MAX_API_MESSAGES)
                .map { msgItem ->
                    ChatTurn(
                        role = if (msgItem.isUser) "user" else "assistant",
                        content = msgItem.text
                    )
                }
        }

        val replyBoxId = ConversationStore.allocateMessageId()

        // 任务15：当前 AI 回复气泡 id —— 用完工具后切换为新 id，
        // 让后续回复作为新气泡追加在工具气泡后面
        var activeReplyId = replyBoxId

        displayedMessages = (displayedMessages + MessageData(
            id = activeReplyId,
            text = uiText(UiText.Thinking),
            time = getCurrentTime(),
            heightDp = 50f,
            isLanded = true,
            isUser = false,
            isThinking = true
        )).takeLast(ContextLimits.MAX_UI_MESSAGES)

        // 任务15：思考点点动画跟随 activeReplyId，工具执行后切换新气泡时可重启
        var thinkingJob: Job? = null
        fun startThinkingDots() {
            thinkingJob?.cancel()
            val targetId = activeReplyId
            thinkingJob = scope.launch {
                var dotCount = 0
                // 代数守卫：流程被掐掉后（长按重发），此循环自行终止，不再空转泄漏
                while (sendGeneration == myGeneration) {
                    displayedMessages = displayedMessages.toMutableList().apply {
                        val idx = indexOfLast { it.id == targetId && it.isThinking }
                        if (idx >= 0) {
                            this[idx] = this[idx].copy(text = uiText(UiText.Thinking) + ".".repeat(dotCount))
                        }
                    }
                    delay(360)
                    dotCount = if (dotCount >= 3) 0 else dotCount + 1
                }
            }
        }
        startThinkingDots()

        var accumulatedReplyText = ""
        var hasFirstDelta = false
        var lastMeasuredLength = 0
        // 任务13：流式过程中是否已提前展示过 plan 加载面板（每轮重置）
        var hasPlanShownDuringStream = false
        // 任务13：流式过程中是否已提前展示过齿轮气泡占位（每轮重置）
        var hasToolCallShownDuringStream = false

        val streamTextStyle = TextStyle(fontSize = 14.sp, fontFamily = pixelFont)

        // 任务17+18+19：plan 锚点锁定 helper —— 单轮回复内首次展示 plan 时锁定锚点
        // （此后该轮不再改变，修复"每执行一个工具 plan 瞬移"）。
        // 任务19：plan 已展示过步骤后，同一轮内不再重载（修复"全部任务完成时 plan 重新加载一次"）。
        // 新消息不清空 plan（常驻），本轮出现新 plan 时才重新锁定并刷新。
        var planLockedThisSend = false

        fun showPlanLoading() {
            if (planLockedThisSend) return
            planLockedThisSend = true
            planAnchorMessageId = activeReplyId
            PlanStore.showLoading()
        }

        fun showPlanSteps(steps: List<String>) {
            if (planLockedThisSend && PlanStore.current.value?.isLoading == false) return
            if (!planLockedThisSend) {
                planLockedThisSend = true
                planAnchorMessageId = activeReplyId
            }
            PlanStore.showSteps(steps)
        }

        // ==================== 任务12：暂停/恢复流式输出 ====================
        // 暂停期间流式增量先入本地缓冲，恢复后按流式节奏补放；工具执行前也等待恢复
        val pausedDeltaBuffer = mutableListOf<String>()

        suspend fun applyStreamDelta(delta: String) {
            if (!hasFirstDelta) {
                hasFirstDelta = true
                thinkingJob?.cancel()
            }

            accumulatedReplyText += delta

            // 任务13（重构）：流式过程中一旦出现 [[tool_call → 立即展示工具 UI，
            // 完全不依赖整轮流结束后的解析（这是"气泡看不到"的最后一根保险丝）：
            // 1) 任何工具 → 立即创建齿轮气泡占位记录（工具名从原始流文本提取）；
            // 2) plan → 立即显示 plan 加载面板。
            if (!hasToolCallShownDuringStream && accumulatedReplyText.contains("[[tool_call")) {
                hasToolCallShownDuringStream = true
                val streamedName = Regex(""""name"\s*:\s*"([a-z_][a-z0-9_]*)""")
                    .find(accumulatedReplyText)?.groupValues?.get(1)
                // 工具名解析不到/未知也照样显示气泡（名称兜底为 "tool"）——
                // 保证"AI 调用 tool"这个事件永远有可见反馈
                val displayToolName = streamedName
                    ?.takeIf { it in McpToolStore.listMcpTools().map { n -> n.name } }
                    ?: "tool"
                toolCalls = toolCalls + ToolCallRecord(
                    id = ConversationStore.allocateMessageId(),
                    toolName = displayToolName,
                    argsText = "",
                    status = ToolCallStatus.Running,
                    resultPreview = "",
                    displayTime = getCurrentTime(),
                    anchorMessageId = activeReplyId
                )
                if (!hasPlanShownDuringStream && accumulatedReplyText.contains("plan", ignoreCase = true)) {
                    hasPlanShownDuringStream = true
                    showPlanLoading()
                }
            }

            val shouldRemeasure = accumulatedReplyText.length - lastMeasuredLength >= 24

            val measuredHeightDp = if (shouldRemeasure) {
                lastMeasuredLength = accumulatedReplyText.length
                val layoutResult = textMeasurer.measure(
                    text = accumulatedReplyText,
                    style = streamTextStyle,
                    constraints = Constraints(maxWidth = maxTextWidthPx.roundToInt())
                )
                with(density) {
                    (layoutResult.size.height.toDp() + 20.dp).value.coerceAtLeast(50f)
                }
            } else null

            // 任务13（重构）：显示文本剥掉 [[tool_call 协议标记 ——
            // 用户看到的是工具气泡，而不是裸协议文本
            val displayText = AiToolBridge.removeToolMarkers(accumulatedReplyText)

            displayedMessages = if (displayedMessages.none { it.id == activeReplyId }) {
                // 气泡被移除后又有文本输出 → 重新加回消息流
                if (displayText.isNotBlank()) {
                    displayedMessages + MessageData(
                        id = activeReplyId,
                        text = displayText,
                        time = getCurrentTime(),
                        heightDp = measuredHeightDp ?: 50f,
                        isLanded = true,
                        isUser = false,
                        isThinking = false
                    )
                } else displayedMessages
            } else {
                displayedMessages.map { message ->
                    if (message.id == activeReplyId) {
                        message.copy(
                            text = displayText,
                            heightDp = measuredHeightDp ?: message.heightDp,
                            isLanded = true,
                            isUser = false,
                            isThinking = false
                        )
                    } else message
                }
            }
        }

        suspend fun flushPausedBuffer() {
            while (pausedDeltaBuffer.isNotEmpty()) {
                val batch = pausedDeltaBuffer.toList()
                pausedDeltaBuffer.clear()
                batch.forEach { bufferedDelta ->
                    applyStreamDelta(bufferedDelta)
                    delay(20)
                }
            }
        }

        suspend fun awaitResume() {
            while (isOutputPaused) delay(120)
        }

        // ==================== 任务8：AI 工具执行循环 + 保证用户内容在第一位 ====================
        // 1) 词条 1【第一位】：用户自编辑的 System 词条（若有，位于绝对第 1 位拥有最高优先级）
        // 2) 词条 2【扩展层】：AI Skills 独立 System 词条（若激活了技能）
        // 3) 词条 3【底层协议】：MCP 工具调用协议与定义 System 词条
        // 4) 后续对话：用户历史对话与最新发送的消息（保持严格对话顺序）
            val requestMessages = mutableListOf<ChatTurn>().apply {
                val userSystem = requestHistory.firstOrNull { it.role == "system" }
                val conversationTurns = overrideConversationTurns
                    ?: requestHistory.filterNot { it.role == "system" }

            // 【保证第 1 位】：用户自编辑的 System 词条位于最顶层
            if (userSystem != null) {
                add(userSystem)
            }

            // 【扩展层】：AI Skills 独立 System 词条
            createSkillsSystemTurn()?.let { skillsTurn ->
                add(skillsTurn)
            }

            // 【底层协议】：系统工具协议 System 词条
            add(ChatTurn(role = "system", content = AiToolBridge.buildToolSystemPrompt()))

            // 【用户内容】：用户的对话消息历史（User 与 Assistant 的对话）
            addAll(conversationTurns)
        }

        // ===== 附件注入：画布网格中上传的文件随请求带给模型 =====
        // 文本类：优先全量内容（经 CanvasFileStore 读取钩子），读不到退回 UI 预览；
        // 图片类（≤4MB）：作为多模态附件挂在这条 user 消息上 —— actual 网络层会
        // 拼成 OpenAI image_url parts / Anthropic image 块随请求发出；
        // 超大图/二进制：留在正文清单里给路径，由模型按需调用读文件工具。
        buildAttachmentContextBlock()?.let { attachmentBlock ->
            requestMessages += ChatTurn(
                role = "user",
                content = attachmentBlock,
                attachments = buildImageAttachmentParts()
            )
        }

        val maxToolLoops = 4
        var loopCount = 0
        var streamResult = ChatStreamResult(
            isSuccess = false,
            fullText = "",
            errorMessage = "所有激活配置均未返回有效回复。"
        )
        var finalReplyText = ""

        while (loopCount < maxToolLoops) {
            loopCount++
            accumulatedReplyText = ""
            hasFirstDelta = false
            lastMeasuredLength = 0

            streamResult = try {
                withContext(Dispatchers.Default) {
                    KeyConfigStore.requestFirstStreamSuccess(requestMessages) { delta ->
                        withContext(Dispatchers.Main) {
                            // 任务12：暂停中 → 增量先入缓冲，不更新 UI
                            if (isOutputPaused) {
                                pausedDeltaBuffer.add(delta)
                                return@withContext
                            }

                            // 恢复后先补放暂停期间缓存的增量（保持流式节奏），再处理实时增量
                            if (pausedDeltaBuffer.isNotEmpty()) {
                                val batch = pausedDeltaBuffer.toList()
                                pausedDeltaBuffer.clear()
                                batch.forEach { bufferedDelta ->
                                    applyStreamDelta(bufferedDelta)
                                    delay(20)
                                }
                            }

                            applyStreamDelta(delta)
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                thinkingJob?.cancel()
                isAiResponding = false
                isOutputPaused = false
                arrowPhase = ArrowPhase.Idle
                throw e
            } catch (e: Exception) {
                ChatStreamResult(
                    isSuccess = false,
                    fullText = accumulatedReplyText,
                    errorMessage = e.message?.take(200) ?: "请求过程中发生未知错误。"
                )
            }

            thinkingJob?.cancel()

            // 任务12：流结束后补放暂停期间缓存的增量（若仍处于暂停，则等用户恢复）
            awaitResume()
            flushPausedBuffer()

            // 任务13（重构，关键修复）：检测源 = 平台 fullText + 流式累积文本 并集。
            // 平台返回的 fullText 可能丢失/截断工具标记，而 accumulatedReplyText 是
            // 逐字到达、用户实际看到的内容 —— 两者都过检测，保证"AI 调用 tool"
            // 的任何形态都能被解析到，工具气泡一定会出现。
            val platformText = streamResult.fullText
            val streamedText = accumulatedReplyText
            var invocations = AiToolBridge.extractToolCalls(platformText)
            if (invocations.isEmpty() && streamedText.isNotBlank()) {
                invocations = AiToolBridge.extractToolCalls(streamedText)
            }
            if (invocations.isEmpty()) {
                // 任务13（重构）：标记被截断（缺 ]] 收尾，流式输出常见）→ 修复 JSON 后照常执行，
                // 保证工具气泡一定会出现；只有修复也失败时才走"要求 AI 重说"分支
                val repaired = (
                    AiToolBridge.extractUnclosedToolCalls(platformText) +
                        AiToolBridge.extractUnclosedToolCalls(streamedText)
                    ).distinctBy { it.name }
                if (repaired.isNotEmpty()) {
                    CmdStore.logBridge("bridge: repaired truncated tool call(s): ${repaired.joinToString { it.name }}")
                    invocations = repaired
                }
            }
            // 后续逻辑统一使用 fullText（平台文本缺失时退回流式文本）
            val fullText = if (platformText.isNotBlank()) platformText else streamedText
            CmdStore.logBridge(
                if (invocations.isNotEmpty()) {
                    "bridge: ${invocations.size} tool call(s): ${invocations.joinToString { it.name }}"
                } else {
                    "bridge: no [[tool_call]] markers in reply (fallback detection)"
                }
            )

            // ===== 收尾检查：出现 [[tool_call 但没有 ]] 闭合 → 告诉 AI 重新完整输出 =====
            if (invocations.isEmpty() && AiToolBridge.hasUnclosedToolMarker(fullText) && loopCount < maxToolLoops) {
                CmdStore.logBridge("bridge: unclosed [[tool_call marker (missing ]]), asking AI to redo")
                // 任务13（重构）：即使修复失败要重说，也先把 plan 面板展示出来，
                // 避免用户完全看不到工具 UI（"气泡完全看不到"的最后一层兜底）
                if (fullText.contains("plan", ignoreCase = true)) {
                    showPlanLoading()
                    delay(900)
                    showPlanSteps(AiToolBridge.extractPlanSteps(fullText) ?: listOf("plan"))
                }
                requestMessages += ChatTurn(
                    role = "user",
                    content = "Your reply contains an INCOMPLETE tool-call block: it starts with [[tool_call but is missing the closing ]]. You MUST close it with ]] . Append a COMPLETE block [[tool_call:{\"name\":\"toolname\",\"args\":{\"arg\":\"value\"}}]] at the very end of your reply. Please redo your reply now."
                )
                finalReplyText = AiToolBridge.removeToolMarkers(fullText).trim()
                continue
            }

            if (invocations.isEmpty()) {
                // 任务12：暂停期间等待用户恢复后再继续后续流程
                awaitResume()

                // ===== 任务10：AI 输出错误 [[tool_call 文本标记 → 告诉 AI 并重试 =====
                if (AiToolBridge.hasMalformedToolMarker(fullText) && loopCount < maxToolLoops) {
                    CmdStore.logBridge("bridge: malformed [[tool_call marker, asking AI to redo")
                    // 任务13（重构）：重说之前先把 plan 面板展示出来（同上，最后一层兜底）
                    if (fullText.contains("plan", ignoreCase = true)) {
                        showPlanLoading()
                        delay(900)
                        showPlanSteps(AiToolBridge.extractPlanSteps(fullText) ?: listOf("plan"))
                    }
                    requestMessages += ChatTurn(
                        role = "user",
                        content = "Your reply contained a broken '[[tool_call' text marker. That is a WRONG legacy format - never write it in visible text. If you need a tool, use your native tool/function calling; if you cannot, append a COMPLETE block [[tool_call:{\"name\":\"toolname\",\"args\":{\"arg\":\"value\"}}]] at the very end of your reply. Please redo your reply now."
                    )
                    finalReplyText = AiToolBridge.removeToolMarkers(fullText).trim()
                    continue
                }

                // ===== plan 意图兜底：即使 JSON 解析失败，检测到 plan 调用就展示动画 =====
                // （AI 的 plan 块常因真实换行/截断导致解析失败，这里保证 plan 一定有反应）
                val hasPlanIntent = fullText.contains("[[tool_call", ignoreCase = true) &&
                    fullText.contains("plan", ignoreCase = true)
                // 任务19：已有 plan 显示时，兜底不再重载（修复"全部任务完成时 plan 重新加载一次"）
                val planAlreadyShown = PlanStore.current.value?.isLoading == false
                if (hasPlanIntent && !planAlreadyShown) {
                    val steps = AiToolBridge.extractPlanSteps(fullText)
                    CmdStore.logBridge(
                        if (steps != null) "bridge: plan intent fallback, ${steps.size} steps"
                        else "bridge: plan intent detected (no steps parsed)"
                    )
                    showPlanLoading()
                    delay(900)
                    showPlanSteps(steps ?: listOf("plan"))
                }

                // ===== 兜底（不依赖模型输出标记）：plan 模式 / 工具名提及 =====
                // 1) plan 兜底：回复含步骤列表 → 展示 plan 动画
                //    任务19：已有 plan 显示时不再重载（AI 最终总结里的数字列表不触发重载）
                val planSteps = AiToolBridge.extractPlanSteps(fullText)
                if (planSteps != null && !hasPlanIntent && !planAlreadyShown) {
                    CmdStore.logBridge("bridge: plan fallback, ${planSteps.size} steps detected")
                    showPlanLoading()
                    delay(900)
                    showPlanSteps(planSteps)
                }
                // 2) 工具名提及兜底：显示对应工具气泡（转一圈后消失），锚定到当前 AI 回复下方
                toolAnchorMessageId = activeReplyId
                AiToolBridge.extractMentionedToolCalls(fullText).forEach { inv ->
                    val record = ToolCallRecord(
                        id = ConversationStore.allocateMessageId(),
                        toolName = inv.name,
                        argsText = inv.args.entries.joinToString(" ") { "${it.key}=${it.value}" },
                        status = ToolCallStatus.Running,
                        resultPreview = "",
                        displayTime = getCurrentTime(),
                        anchorMessageId = activeReplyId
                    )
                    toolCalls = toolCalls + record
                    CmdStore.logBridge("bridge: mentioned tool '${inv.name}' (demo bubble)")
                    delay(750)
                    toolCalls = toolCalls.map {
                        if (it.id == record.id) it.copy(status = ToolCallStatus.Done) else it
                    }
                }

                // 本轮没有工具调用：结果即最终回复
                // 重构：先剥掉残留的 [[tool_call 标记文本，避免把协议垃圾显示给用户
                finalReplyText = when {
                    streamResult.isSuccess && fullText.isNotBlank() ->
                        AiToolBridge.removeToolMarkers(fullText).trim()
                    accumulatedReplyText.isNotBlank() -> accumulatedReplyText + "\n[Stream interrupted]"
                    else -> streamResult.errorMessage ?: "所有激活配置均未返回有效回复。"
                }
                break
            }

            // 从正文移除工具标记，先显示干净文本（AI 只调工具时气泡留空）
            finalReplyText = AiToolBridge.removeToolMarkers(fullText).trim()
            if (finalReplyText.isBlank()) {
                // 任务15（重构）：AI 纯工具调用（无可见文本）→ 保留空白占位气泡，
                // 不再从消息流移除。MessageList 将空 AI 气泡渲染为隐形占位，
                // 工具框（带 AI 标识头）紧贴其后显示 = 替换聊天气泡。
            } else {
                displayedMessages = displayedMessages.map { message ->
                    if (message.id == activeReplyId) message.copy(text = finalReplyText) else message
                }
            }

            // 任务15（重构）：工具气泡锚定到本轮触发调用的气泡 activeReplyId。
            // - AI 纯工具调用（无可见文本）：该气泡为空白占位 → 工具框（带 AI 标识头）渲染在其下方；
            // - AI 后续继续输出文本：回复写入新气泡，追加在工具框之后。
            toolAnchorMessageId = activeReplyId

            // 依次执行本轮工具调用
            for (inv in invocations) {
                // 任务12：暂停期间等待用户恢复后再执行工具
                awaitResume()

                // plan 容错：args 缺 steps 时，从整个回复文本兜底提取步骤（AI 格式不全也能画动画）
                val effectiveArgs = if (inv.name == "plan" && (inv.args["steps"] ?: "").isBlank()) {
                    val steps = AiToolBridge.extractPlanSteps(fullText)
                    if (steps != null) {
                        inv.args + mapOf(
                            "task" to (inv.args["task"] ?: "plan"),
                            "steps" to steps.joinToString("\n")
                        )
                    } else inv.args
                } else inv.args

                val record = ToolCallRecord(
                    id = ConversationStore.allocateMessageId(),
                    toolName = inv.name,
                    argsText = effectiveArgs.entries.joinToString(" ") { "${it.key}=${it.value}" },
                    status = ToolCallStatus.Running,
                    resultPreview = "",
                    displayTime = getCurrentTime(),
                    anchorMessageId = toolAnchorMessageId
                )
                // 任务13+18（重构）：替换流式期间创建的占位记录（同名 Running 且同锚点），
                // 避免同一工具调用出现两条气泡记录
                toolCalls = toolCalls.filterNot {
                    it.toolName == inv.name && it.status == ToolCallStatus.Running &&
                        it.anchorMessageId == toolAnchorMessageId
                } + record

                if (inv.name == "plan") {
                    // plan 工具：先显示纯黑加载方块，handler 内 delay 后转入画线阶段
                    showPlanLoading()
                }

                // 任务8修复：工具执行期间强制列表滚到底，确保气泡在可视区内
                try {
                    messageScrollState.scrollTo(messageScrollState.maxValue)
                } catch (_: Exception) {
                }

                val mc = withContext(Dispatchers.Default) {
                    try {
                        AiToolBridge.executeToolCall(inv.name, effectiveArgs)
                    } catch (t: Throwable) {
                        McpToolResult(isSuccess = false, output = "", error = t.message ?: "tool failed")
                    }
                }

                // 重大修复：工具执行完至少再显示一小段时间，保证齿轮动画可感知
                // （同步工具可能一帧就结束，直接置 Done 会让气泡瞬间消失）
                delay(if (mc.isSuccess) 650L else 250L)

                toolCalls = toolCalls.map {
                    if (it.id == record.id) {
                        it.copy(
                            status = if (mc.isSuccess) ToolCallStatus.Done else ToolCallStatus.Error,
                            resultPreview = (if (mc.isSuccess) mc.output else (mc.error ?: "")).take(140)
                        )
                    } else it
                }

                // 工具结果回填上下文，下一轮 AI 基于结果继续
                requestMessages += ChatTurn(
                    role = "user",
                    content = "Tool call: ${inv.name} ${inv.args}\nTool result: ${
                        if (mc.isSuccess) mc.output else "ERROR: ${mc.error ?: "unknown"}"
                    }"
                )
            }

            // 任务15：用完工具后的回复 = 新气泡，追加在工具气泡后面。
            // 切换 activeReplyId + 新增思考气泡，下一轮流式文本写入新气泡。
            if (loopCount < maxToolLoops) {
                activeReplyId = ConversationStore.allocateMessageId()
                displayedMessages = (displayedMessages + MessageData(
                    id = activeReplyId,
                    text = uiText(UiText.Thinking),
                    time = getCurrentTime(),
                    heightDp = 50f,
                    isLanded = true,
                    isUser = false,
                    isThinking = true
                )).takeLast(ContextLimits.MAX_UI_MESSAGES)
                startThinkingDots()
            }

            // 任务13（重构）：此处原本还有一个"malformed 重说检查"（任务10遗留），
            // 它与截断修复逻辑互相打架：截断标记（缺 ]]）经 extractUnclosedToolCalls
            // 修复后工具已正常执行、气泡已填充，但该检查对截断标记恒返回 true，
            // 导致整轮工具流程被无谓地"重说"重启 —— 属于影响气泡显示的多余逻辑，已移除。
            // 空分支内（修复失败才到达）的同款检查保留，仍有兜底意义。
            // 继续循环：AI 基于工具结果生成下一段回复
        }

        // 任务13：流全部结束仍停在"加载中"且无步骤可解析 → 显示兜底文案，
        // 避免 plan 面板永远转加载动画（气泡至少是可见的）
        if (PlanStore.current.value?.isLoading == true) {
            showPlanSteps(emptyList())
        }

        val successfulReply = finalReplyText.takeIf { streamResult.isSuccess && finalReplyText.isNotBlank() }

        // AI 回复贴着齿轮气泡继续：工具气泡常驻消息流，AI 文本气泡接在其后
        // （不再清空 toolCalls，气泡保持在消息流里）

        if (successfulReply != null) {
            ConversationStore.append(
                ConversationMessageRecord(
                    id = activeReplyId,
                    role = "assistant",
                    content = successfulReply,
                    displayTime = getCurrentTime(),
                    replyToId = msgData.id
                )
            )
        }

        val replyTextStyle = TextStyle(fontSize = 14.sp, fontFamily = pixelFont)
        val replyLayoutResult = withContext(Dispatchers.Default) {
            textMeasurer.measure(
                text = finalReplyText,
                style = replyTextStyle,
                constraints = Constraints(maxWidth = maxTextWidthPx.roundToInt())
            )
        }
        val measuredReplyHeightDp = with(density) {
            (replyLayoutResult.size.height.toDp() + 20.dp).value
        }
        val finalReplyHeightDp = measuredReplyHeightDp.coerceAtLeast(50f)

        displayedMessages = if (displayedMessages.none { it.id == activeReplyId }) {
            // 气泡被移除：最终有文本则加回，仍为空则保持移除
            if (finalReplyText.isNotBlank()) {
                displayedMessages + MessageData(
                    id = activeReplyId,
                    text = finalReplyText,
                    time = getCurrentTime(),
                    heightDp = finalReplyHeightDp,
                    isLanded = true,
                    isUser = false,
                    isThinking = false
                )
            } else displayedMessages
        } else {
            displayedMessages.map { message ->
                if (message.id == activeReplyId) {
                    message.copy(
                        text = finalReplyText,
                        time = getCurrentTime(),
                        heightDp = finalReplyHeightDp,
                        isLanded = true,
                        isUser = false,
                        isThinking = false
                    )
                } else message
            }
        }

        // 任务13（重构）：兜底收尾 —— 若还有 Running 状态的占位记录
        // （流中出现了标记但最终没进工具循环执行），置为 Error：
        // 气泡显示 "Failed ✗" 而不是永远转圈，保证用户看到的工具气泡有终态。
        if (toolCalls.any { it.status == ToolCallStatus.Running }) {
            toolCalls = toolCalls.map {
                if (it.status == ToolCallStatus.Running) {
                    it.copy(status = ToolCallStatus.Error, resultPreview = "tool call not confirmed")
                } else it
            }
        }

        // 任务22：工具气泡/plan 写入会话档案（跨重启保留）。
        // toolCalls 是全量记录（含之前会话恢复的），整体覆盖保存；
        // plan 仅在本轮产生过新 plan（PlanStore 非空）时覆盖保存，避免误清历史。
        ConversationStore.saveToolCallsForCurrent(toolCalls)
        PlanStore.current.value?.let { plan ->
            ConversationStore.savePlanForCurrent(plan.steps, planAnchorMessageId)
        }

        // 任务12：AI 输出结束，解锁发送并清除暂停状态
        isAiResponding = false
        isOutputPaused = false
        // 箭头状态机收尾：置回 Idle —— Spin→Idle 会触发 InputArea 的逆时针回卷动画；
        // 若结束时恰好停在 Paused（箭头已在原位），归位无副作用
        arrowPhase = ArrowPhase.Idle
    }
}

// ==================== 长按重发：替换上一条消息并重新请求 ====================
// 仅当箭头停在 Paused（AI 输出已被暂停）时可触发：
// 1) 掐掉上一轮发送流程（暂停时它正停在 awaitResume 里，cancel + join 即干净退出）
// 2) UI 手术：旧用户消息 + 其后所有 AI 气泡整体切除，悬挂的工具/plan 记录一并清理
// 3) 以新文本走完整 handleSend —— 测量、飞行、请求、工具循环全部复用
suspend fun ChatUiState.resendReplacingLast(
    newMsg: String,
    previousJob: Job?,
    density: Density,
    pixelFont: FontFamily,
    textMeasurer: TextMeasurer,
    messageScrollState: ScrollState,
    scope: CoroutineScope
) {
    if (arrowPhase != ArrowPhase.Paused || !isAiResponding) return
    if (newMsg.isBlank()) return

    val lastUserMsg = displayedMessages.lastOrNull { it.isUser } ?: return
    val cutIndex = displayedMessages.indexOfLast { it.id == lastUserMsg.id }
    if (cutIndex < 0) return

    // 1) 掐掉上一轮：显式传入旧 Job（App 侧在覆盖 currentSendJob 之前捕获），
    //    杜绝"新协程抢先启动、cancel 到自己"的竞态；join 等其真正死透再继续
    previousJob?.cancel()
    previousJob?.join()
    isAiResponding = false
    isOutputPaused = false

    // 2) UI 手术：切掉旧消息及其后的一切（AI 思考/回复气泡）
    val removedIds = displayedMessages.drop(cutIndex).map { it.id }.toSet()
    displayedMessages = displayedMessages.take(cutIndex)

    // 悬挂在已移除气泡上的工具记录一并清理（转圈中的占位直接撤掉）
    toolCalls = toolCalls.filterNot {
        it.anchorMessageId in removedIds || it.status == ToolCallStatus.Running
    }
    // plan 若锚在已移除区域：整块撤掉，新流程会按需重新锚定
    if (planAnchorMessageId in removedIds) {
        planAnchorMessageId = null
        PlanStore.clear()
    }

    // 3) 箭头先归位，handleSend 入场会重新 Pop → Spin
    arrowPhase = ArrowPhase.Idle

    handleSend(
        msg = newMsg,
        replacedUserText = lastUserMsg.text,
        density = density,
        pixelFont = pixelFont,
        textMeasurer = textMeasurer,
        messageScrollState = messageScrollState,
        scope = scope
    )
}

// ==================== 附件上下文：画布上传文件 → 请求 ====================
/** 附件注入词条的字符总预算（防上下文爆掉） */
private const val MAX_ATTACHMENT_BLOCK_CHARS = 6000

/**
 * 把 CanvasFileStore 当前网格里的附件整理成一段 user 词条。
 *
 * - 无附件 → 返回 null（不注入任何内容）
 * - 文本：优先 CanvasFileStore.readFullText 全量内容，读不到退回 contentPreview；
 *   超出预算按文件数均分截断，附 "…(截断)" 标记
 * - 图片：只给清单头（名称/大小/localPath），模型需要细节时自己调工具读
 */
private suspend fun buildAttachmentContextBlock(): String? {
    val files = CanvasFileStore.files.value
    if (files.isEmpty()) return null

    val headerBudget = 400
    val remaining = (MAX_ATTACHMENT_BLOCK_CHARS - headerBudget).coerceAtLeast(200)
    val perFileBudget = remaining / files.size

    val sb = StringBuilder()
    sb.append("用户上传了 ${files.size} 个附件，请结合这些附件回答问题；")
    sb.append("需要完整内容或图像细节时，使用你的工具按 localPath 读取。附件清单：\n")

    files.forEachIndexed { index, file ->
        val kind = when {
            file.isImage -> "image"
            file.isText -> "text"
            else -> "binary"
        }
        sb.append("${index + 1}. [").append(kind).append("] ").append(file.name)
            .append(" | ").append(file.sizeLabel)
            .append(" | localPath: ").append(file.localPath).append("\n")

        val body = if (file.isText && !file.isImage) {
            val full = CanvasFileStore.readFullText(file)?.trim()?.takeIf { it.isNotEmpty() }
            full ?: file.contentPreview.trim()
        } else {
            ""
        }
        when {
            body.isNotEmpty() -> {
                val snippet = if (body.length > perFileBudget) {
                    body.take(perFileBudget) + "\n…(截断，如需完整内容请用工具读取 localPath)"
                } else body
                sb.append("内容:\n").append(snippet).append("\n")
            }
            file.isImage -> {
                sb.append(
                    "（图片：≤4MB 已作为图像附件内嵌随请求发送；" +
                        "若模型不支持图像输入或图片过大，请用工具读取 localPath）\n"
                )
            }
        }
    }
    return sb.toString().take(MAX_ATTACHMENT_BLOCK_CHARS)
}

/** 图片文件 → 多模态附件列表（供 actual 网络层组装 content parts）。
 *  超过 4MB 的图不内嵌（base64 膨胀 + 上下文预算），路径说明仍在正文里。 */
private fun buildImageAttachmentParts(): List<ChatAttachment> =
    CanvasFileStore.files.value.mapNotNull { file ->
        if (!file.isImage || file.rawBytes == null) return@mapNotNull null
        if (file.rawBytes!!.size > 4 * 1024 * 1024) return@mapNotNull null
        ChatAttachment(
            fileName = file.name,
            mimeType = guessImageMimeType(file.name),
            bytes = file.rawBytes!!
        )
    }

/** 按扩展名猜图片 MIME（猜不到默认 image/png） */
private fun guessImageMimeType(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
    "jpg", "jpeg" -> "image/jpeg"
    "png" -> "image/png"
    "webp" -> "image/webp"
    "gif" -> "image/gif"
    "bmp" -> "image/bmp"
    "heic", "heif" -> "image/heic"
    else -> "image/png"
}
