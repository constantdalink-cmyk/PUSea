package org.example.project

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

// ==============================================================================
// ChatEngine —— 纯执行引擎（后台常驻的"心脏"）
//
// 从 ChatUiState.handleSend 中抽出的【纯执行】部分：
//   组请求 → 流式接收 → 解析 [[tool_call]] → 执行工具 → 结果回灌 → 循环
// 完全不含任何 Compose / UI 依赖（没有 textMeasurer / density / scrollState /
// displayedMessages / 飞行动画 / 箭头状态机），因此可以安全地跑在
// Foreground Service 的协程里，息屏、退后台、进程被拉起后都能继续工作。
//
// UI 层（ChatUiState）退化为"事件订阅者"：collect events / state 来驱动
// 气泡、工具框、plan、箭头动画。这样前后台行为天然一致，不会分叉。
// ==============================================================================

/** 引擎对外发出的事件（UI / Service 订阅） */
sealed class EngineEvent {
    /** 一轮任务开始 */
    object Started : EngineEvent()
    /** 流式文本增量（delta=本次增量，accumulated=累计全文） */
    data class StreamDelta(val delta: String, val accumulated: String) : EngineEvent()
    /** 检测到 plan 步骤 */
    data class PlanDetected(val steps: List<String>) : EngineEvent()
    /** 开始执行某个工具 */
    data class ToolStarted(val name: String, val args: Map<String, String>) : EngineEvent()
    /** 工具执行结束 */
    data class ToolFinished(val name: String, val success: Boolean, val preview: String) : EngineEvent()
    /** 整轮成功收尾 */
    data class Finished(val replyText: String) : EngineEvent()
    /** 整轮失败/无有效回复 */
    data class Failed(val message: String) : EngineEvent()
}

/** 引擎的可观察状态 */
data class EngineState(
    val running: Boolean = false,
    val replySoFar: String = ""
)

/**
 * 流式能力提供器（依赖注入点 = 一层"保护壳"）。
 * 引擎不认识 KeyConfigStore，只认这个接口；真实项目把多 Key 调度接进来即可。
 */
interface ChatStreamProvider {
    suspend fun stream(
        history: List<ChatTurn>,
        onDelta: suspend (String) -> Unit
    ): ChatStreamResult
}

/**
 * 引擎接线点：真实项目里由 UI / Service 在启动时填入"当前激活的 Key 配置"。
 * 这样引擎与后台服务完全不需要知道 KeyConfigStore 的实际代码。
 */
object EngineWiring {
    /** 取当前激活配置；返回 null 时引擎按"无可用配置"失败 */
    var activeConfigProvider: () -> SavedKeyConfig? = { null }
}

/**
 * 多 Key 流式提供器（真实路径）：委托给 KeyConfigStore.requestFirstStreamSuccess，
 * 由它按顺序尝试各激活配置直到首个成功——与 handleSend / runAiReplyFlow 的行为完全一致。
 * 引擎不认识 KeyConfigStore 内部，只通过这个接口拿流。
 */
class KeyConfigChatStreamProvider : ChatStreamProvider {
    override suspend fun stream(
        history: List<ChatTurn>,
        onDelta: suspend (String) -> Unit
    ): ChatStreamResult = KeyConfigStore.requestFirstStreamSuccess(history, onDelta)
}

/** 默认流式提供器：直连 expect 函数 requestChatStream（单配置，保护壳路径） */
class DefaultChatStreamProvider : ChatStreamProvider {
    override suspend fun stream(
        history: List<ChatTurn>,
        onDelta: suspend (String) -> Unit
    ): ChatStreamResult {
        val config = EngineWiring.activeConfigProvider()
            ?: return ChatStreamResult(
                isSuccess = false,
                errorMessage = "no active key config (shell)"
            )
        return requestChatStream(config, history, onDelta)
    }
}

/**
 * 纯执行引擎。由 KeepAliveService（前台服务）持有并驱动。
 */
class ChatEngine(
    private val streamProvider: ChatStreamProvider = KeyConfigChatStreamProvider()
) {
    private val _events = MutableSharedFlow<EngineEvent>(extraBufferCapacity = 128)
    val events: SharedFlow<EngineEvent> = _events

    private val _state = MutableStateFlow(EngineState())
    val state: StateFlow<EngineState> = _state

    /**
     * 跑完一整轮"用户消息 → AI 回复（含工具循环）"。
     * 与 handleSend 的差异：只做执行与持久化，不碰任何 UI 状态。
     */
    suspend fun run(userMessage: String) {
        if (userMessage.isBlank()) return

        _state.value = _state.value.copy(running = true, replySoFar = "")
        _events.emit(EngineEvent.Started)

        // 1. 持久化用户消息（与 handleSend 保持一致的写入路径）
        val userMsgId = ConversationStore.allocateMessageId()
        ConversationStore.append(
            ConversationMessageRecord(
                id = userMsgId,
                role = "user",
                content = userMessage,
                displayTime = getCurrentTime()
            )
        )

        // 2. 构建上下文窗口
        val requestHistory = ConversationStore.buildContextWindow(
            maxMessages = ContextLimits.MAX_API_MESSAGES,
            maxCharacters = ContextLimits.MAX_API_CHARS,
            maxSingleMessageChars = ContextLimits.MAX_SINGLE_MESSAGE_CHARS
        )

        // 3. 组装请求消息：用户 System 词条置顶 + 工具协议 System 词条 + 对话历史
        val requestMessages = mutableListOf<ChatTurn>().apply {
            val userSystem = requestHistory.firstOrNull { it.role == "system" }
            if (userSystem != null) add(userSystem)
            add(ChatTurn(role = "system", content = AiToolBridge.buildToolSystemPrompt()))
            addAll(requestHistory.filterNot { it.role == "system" })
        }

        // 4. 工具执行循环（对齐 handleSend 的核心逻辑）
        val maxToolLoops = 4
        var loopCount = 0
        var finalReplyText = ""
        var streamOk = false
        var planEmitted = false

        while (loopCount < maxToolLoops) {
            loopCount++
            val accumulated = StringBuilder()

            val result = try {
                streamProvider.stream(requestMessages) { delta ->
                    accumulated.append(delta)
                    _state.value = _state.value.copy(replySoFar = accumulated.toString())
                    _events.emit(EngineEvent.StreamDelta(delta, accumulated.toString()))
                }
            } catch (e: CancellationException) {
                // 服务销毁 / 任务取消：清理状态后向上抛，交给协程框架
                _state.value = _state.value.copy(running = false)
                throw e
            } catch (e: Exception) {
                ChatStreamResult(
                    isSuccess = false,
                    fullText = accumulated.toString(),
                    errorMessage = e.message?.take(200) ?: "stream error"
                )
            }

            // 检测源 = 平台 fullText + 流式累积文本（与 handleSend 的双保险一致）
            val streamedText = accumulated.toString()
            val fullText = if (result.fullText.isNotBlank()) result.fullText else streamedText

            var invocations = AiToolBridge.extractToolCalls(fullText)
            if (invocations.isEmpty() && streamedText.isNotBlank()) {
                invocations = AiToolBridge.extractToolCalls(streamedText)
            }
            if (invocations.isEmpty()) {
                // 截断标记（缺 ]]）修复后照常执行
                val repaired = (
                    AiToolBridge.extractUnclosedToolCalls(fullText) +
                        AiToolBridge.extractUnclosedToolCalls(streamedText)
                    ).distinctBy { it.name }
                if (repaired.isNotEmpty()) invocations = repaired
            }

            // plan 检测（每轮只发一次事件，去重交给 UI 亦可）
            if (!planEmitted) {
                AiToolBridge.extractPlanSteps(fullText)?.let { steps ->
                    planEmitted = true
                    _events.emit(EngineEvent.PlanDetected(steps))
                }
            }

            if (invocations.isEmpty()) {
                // 未闭合标记 → 要求 AI 重说
                if (AiToolBridge.hasUnclosedToolMarker(fullText) && loopCount < maxToolLoops) {
                    requestMessages += ChatTurn(
                        role = "user",
                        content = "Your reply contains an INCOMPLETE tool-call block: it starts with [[tool_call but is missing the closing ]]. Append a COMPLETE block [[tool_call:{\"name\":\"toolname\",\"args\":{\"arg\":\"value\"}}]] at the very end. Please redo your reply now."
                    )
                    finalReplyText = AiToolBridge.removeToolMarkers(fullText).trim()
                    continue
                }
                // 本轮无工具：收尾
                finalReplyText = when {
                    result.isSuccess && fullText.isNotBlank() ->
                        AiToolBridge.removeToolMarkers(fullText).trim()
                    streamedText.isNotBlank() -> streamedText + "\n[Stream interrupted]"
                    else -> result.errorMessage ?: "所有激活配置均未返回有效回复。"
                }
                streamOk = result.isSuccess && fullText.isNotBlank()
                break
            }

            // 有工具：先拿到干净正文，再逐个执行
            finalReplyText = AiToolBridge.removeToolMarkers(fullText).trim()
            for (inv in invocations) {
                // plan 容错：args 缺 steps 时从全文兜底提取
                val effectiveArgs = if (inv.name == "plan" && (inv.args["steps"] ?: "").isBlank()) {
                    AiToolBridge.extractPlanSteps(fullText)?.let { steps ->
                        inv.args + mapOf(
                            "task" to (inv.args["task"] ?: "plan"),
                            "steps" to steps.joinToString("\n")
                        )
                    } ?: inv.args
                } else inv.args

                _events.emit(EngineEvent.ToolStarted(inv.name, effectiveArgs))

                val mc = try {
                    AiToolBridge.executeToolCall(inv.name, effectiveArgs)
                } catch (t: Throwable) {
                    McpToolResult(isSuccess = false, output = "", error = t.message ?: "tool failed")
                }

                _events.emit(
                    EngineEvent.ToolFinished(
                        name = inv.name,
                        success = mc.isSuccess,
                        preview = (if (mc.isSuccess) mc.output else (mc.error ?: "")).take(140)
                    )
                )

                // 工具结果回灌上下文，供下一轮 AI 继续
                requestMessages += ChatTurn(
                    role = "user",
                    content = "Tool call: ${inv.name} ${inv.args}\nTool result: ${
                        if (mc.isSuccess) mc.output else "ERROR: ${mc.error ?: "unknown"}"
                    }"
                )
            }
            streamOk = result.isSuccess
            // 继续循环：AI 基于工具结果生成下一段回复
        }

        // 5. 持久化 AI 回复（完整成功才落盘，与 handleSend 一致）
        if (streamOk && finalReplyText.isNotBlank()) {
            val replyId = ConversationStore.allocateMessageId()
            ConversationStore.append(
                ConversationMessageRecord(
                    id = replyId,
                    role = "assistant",
                    content = finalReplyText,
                    displayTime = getCurrentTime(),
                    replyToId = userMsgId
                )
            )
        }

        _state.value = _state.value.copy(running = false, replySoFar = finalReplyText)
        _events.emit(
            if (streamOk) EngineEvent.Finished(finalReplyText)
            else EngineEvent.Failed(finalReplyText.ifBlank { "no reply" })
        )
    }
}
