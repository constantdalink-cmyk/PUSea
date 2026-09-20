package org.example.project

import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * 解耦：假装思考(Thinking 动画) + 持久化 + 呼叫 AI + 流式接收。
 * 从 App() 的 onSend 中原样抽出。调用时假定已运行在 Dispatchers.Main 上下文。
 *
 * getMessages / setMessages 用于读写 App 中的 displayedMessages 状态。
 */
suspend fun runAiReplyFlow(
    userMsgData: MessageData,
    userText: String,
    textMeasurer: TextMeasurer,
    density: Density,
    pixelFont: FontFamily,
    maxTextWidthPx: Float,
    getMessages: () -> List<MessageData>,
    setMessages: (List<MessageData>) -> Unit
) = coroutineScope {
    // 用户消息持久化
    ConversationStore.append(
        ConversationMessageRecord(
            id = userMsgData.id,
            role = "user",
            content = userText,
            displayTime = userMsgData.time
        )
    )

    val requestHistory =
        ConversationStore.buildContextWindow(
            maxMessages = ContextLimits.MAX_API_MESSAGES,
            maxCharacters = ContextLimits.MAX_API_CHARS,
            maxSingleMessageChars =
                ContextLimits.MAX_SINGLE_MESSAGE_CHARS
        )

    // 先创建固定的 AI 占位框，放在左侧，并从 Thinking 开始动画。
    val replyBoxId = ConversationStore.allocateMessageId()

    setMessages(
        (
            getMessages() + MessageData(
                id = replyBoxId,
                text = uiText(UiText.Thinking),
                time = getCurrentTime(),
                heightDp = 50f,
                isLanded = true,
                isUser = false,
                isThinking = true
            )
        ).takeLast(ContextLimits.MAX_UI_MESSAGES)
    )

    // 像素风 Thinking / Thinking. / Thinking.. / Thinking... 动画。
    val thinkingJob = launch {
        var dotCount = 0
        while (true) {
            setMessages(
                getMessages().toMutableList().apply {
                    val idx = indexOfLast { it.id == replyBoxId }
                    if (idx >= 0) {
                        this[idx] = this[idx].copy(
                            text = uiText(UiText.Thinking) + ".".repeat(dotCount)
                        )
                    }
                }
            )
            delay(360)
            dotCount = if (dotCount >= 3) 0 else dotCount + 1
        }
    }

    var accumulatedReplyText = ""
    var hasFirstDelta = false
    var lastMeasuredLength = 0

    val streamTextStyle = TextStyle(fontSize = 14.sp, fontFamily = pixelFont)
    // 构建请求上下文：专属于 AI Skills 的独立 System 词条 + 用户自编辑的 System/历史消息，互不干扰
    val requestMessagesWithSkills = buildSkillEnhancedContext(requestHistory)

    val result = try {
        withContext(Dispatchers.Default) {
            KeyConfigStore.requestFirstStreamSuccess(requestMessagesWithSkills) { delta ->
                withContext(Dispatchers.Main) {
                    if (!hasFirstDelta) {
                        hasFirstDelta = true
                        thinkingJob.cancel()
                    }

                    accumulatedReplyText += delta

                    val shouldRemeasure =
                        accumulatedReplyText.length - lastMeasuredLength >= 24

                    val measuredHeightDp =
                        if (shouldRemeasure) {
                            lastMeasuredLength = accumulatedReplyText.length

                            val layoutResult = textMeasurer.measure(
                                text = accumulatedReplyText,
                                style = streamTextStyle,
                                constraints = Constraints(
                                    maxWidth = maxTextWidthPx.roundToInt()
                                )
                            )

                            with(density) {
                                (layoutResult.size.height.toDp() + 20.dp).value
                                    .coerceAtLeast(50f)
                            }
                        } else {
                            null
                        }

                    setMessages(
                        getMessages().map { message ->
                            if (message.id == replyBoxId) {
                                message.copy(
                                    text = accumulatedReplyText,
                                    heightDp = measuredHeightDp ?: message.heightDp,
                                    isLanded = true,
                                    isUser = false,
                                    isThinking = false
                                )
                            } else {
                                message
                            }
                        }
                    )
                }
            }
        }
    } catch (e: CancellationException) {
        thinkingJob.cancel()
        throw e
    } catch (e: Exception) {
        ChatStreamResult(
            isSuccess = false,
            fullText = accumulatedReplyText,
            errorMessage = e.message?.take(200) ?: "请求过程中发生未知错误。"
        )
    }

    thinkingJob.cancel()

    val successfulReply = result.fullText
        .takeIf { result.isSuccess && it.isNotBlank() }

    val finalReplyText =
        successfulReply
            ?: accumulatedReplyText
                .takeIf { it.isNotBlank() }
                ?.let { it + "\n[Stream interrupted]" }
            ?: result.errorMessage
            ?: "所有激活配置均未返回有效回复。"

    // 只有完整成功的 AI 回复才持久化。
    if (successfulReply != null) {
        ConversationStore.append(
            ConversationMessageRecord(
                id = replyBoxId,
                role = "assistant",
                content = successfulReply,
                displayTime = getCurrentTime(),
                replyToId = userMsgData.id
            )
        )
    }

    // 按实际回复内容重新测量信息框高度。
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

    setMessages(
        getMessages().map { message ->
            if (message.id == replyBoxId) {
                message.copy(
                    text = finalReplyText,
                    time = getCurrentTime(),
                    heightDp = finalReplyHeightDp,
                    isLanded = true,
                    isUser = false,
                    isThinking = false
                )
            } else {
                message
            }
        }
    )
}
