package org.example.project

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

// ==============================================================================
// KeepAliveBridge —— 把"前台服务里的 ChatEngine"接回现有 ChatUiState 的桥。
//
// 它解决的是接线的核心难题：执行已搬到服务进程（发 EngineEvent），而 UI 状态
// （displayedMessages / toolCalls / plan / 箭头）还是原来那个 Compose 类。
// 桥在两者之间做"事件 → UI 投影"，且 collector 在 bind 时就常驻，不会漏事件。
//
// 用法（在你的 App composable 里，替换直接调 handleSend 的地方）：
//
//   val scope = rememberCoroutineScope()
//   val bridge = remember { KeepAliveBridge(context) }
//   DisposableEffect(Unit) {
//       bridge.bind(scope)
//       onDispose { bridge.unbind() }
//   }
//   // onSend 时（主线程）：
//   bridge.send(chatUiState, msg, textMeasurer, density, pixelFont)
//
// 注意：持久化（用户消息 / AI 回复落盘）已由引擎负责，App 侧不要再 append；
//       若你想保留自己的"用户气泡飞入动画"，传 addUserBubble = false 并自行挂气泡。
// ==============================================================================
class KeepAliveBridge(private val context: Context) {

    private var service: KeepAliveService? = null
    private var collectJob: Job? = null
    private var mainScope: CoroutineScope? = null

    /** 一轮投影所需的 UI 上下文（send 时设置） */
    private data class Projection(
        val state: ChatUiState,
        val textMeasurer: TextMeasurer,
        val density: Density,
        val pixelFont: FontFamily
    )

    @Volatile
    private var projection: Projection? = null

    /** 本轮 AI 回复气泡 id 与测高节流 */
    private var replyBoxId: Long = 0L
    private var lastMeasuredLength = 0

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            service = (binder as KeepAliveService.LocalBinder).getService()
            startCollecting()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            service = null
        }
    }

    /** 绑定服务并确保前台服务在跑。scope 传 Compose 的 rememberCoroutineScope()（Main）。 */
    fun bind(scope: CoroutineScope) {
        mainScope = scope
        KeepAliveService.start(context)
        context.bindService(
            Intent(context, KeepAliveService::class.java),
            connection,
            Context.BIND_AUTO_CREATE
        )
    }

    fun unbind() {
        collectJob?.cancel()
        collectJob = null
        projection = null
        try {
            context.unbindService(connection)
        } catch (_: Exception) {
        }
        service = null
    }

    /** 常驻订阅引擎事件（bind 成功即挂上，保证不漏 Started） */
    private fun startCollecting() {
        val svc = service ?: return
        val scope = mainScope ?: return
        collectJob?.cancel()
        collectJob = scope.launch {
            svc.engine.events.collect { event ->
                val p = projection ?: return@collect
                project(p, event)
            }
        }
    }

    /**
     * 发送一条消息（需在主线程调用，如 Compose 点击回调）。
     * @param addUserBubble true = 桥直接把用户气泡挂上；false = 你自己做飞入动画
     */
    fun send(
        state: ChatUiState,
        msg: String,
        textMeasurer: TextMeasurer,
        density: Density,
        pixelFont: FontFamily,
        addUserBubble: Boolean = true,
        /** 经 handleSend 接线点进来时，isAiResponding 已被其持有，跳过桥的重入保护 */
        bypassGuard: Boolean = false
    ) {
        if (msg.isBlank()) return
        if (!bypassGuard && state.isAiResponding) return
        val svc = service ?: return

        projection = Projection(state, textMeasurer, density, pixelFont)
        // 兜底：若 bind 后 onServiceConnected 尚未回调就已 send，这里补挂 collector
        if (collectJob == null) startCollecting()

        if (addUserBubble) {
            appendUserBubble(state, msg, textMeasurer, density, pixelFont)
        }

        svc.launchTask(msg)
    }

    private fun appendUserBubble(
        state: ChatUiState,
        msg: String,
        textMeasurer: TextMeasurer,
        density: Density,
        pixelFont: FontFamily
    ) {
        state.displayedMessages = (state.displayedMessages + MessageData(
            id = ConversationStore.allocateMessageId(),
            text = msg,
            time = getCurrentTime(),
            heightDp = measureHeight(msg, textMeasurer, density, pixelFont),
            isLanded = true,
            isUser = true,
            isThinking = false
        )).takeLast(ContextLimits.MAX_UI_MESSAGES)
    }

    /** 事件 → UI 投影（全部写回主线程） */
    private suspend fun project(p: Projection, event: EngineEvent) = withContext(Dispatchers.Main) {
        val state = p.state
        when (event) {
            is EngineEvent.Started -> {
                replyBoxId = ConversationStore.allocateMessageId()
                lastMeasuredLength = 0
                state.isAiResponding = true
                state.arrowPhase = ArrowPhase.Spin
                // 挂上 AI 思考气泡
                state.displayedMessages = (state.displayedMessages + MessageData(
                    id = replyBoxId,
                    text = uiText(UiText.Thinking),
                    time = getCurrentTime(),
                    heightDp = 50f,
                    isLanded = true,
                    isUser = false,
                    isThinking = true
                )).takeLast(ContextLimits.MAX_UI_MESSAGES)
            }

            is EngineEvent.StreamDelta -> {
                val displayText = AiToolBridge.removeToolMarkers(event.accumulated)
                // 测高节流：每累计 24 字符重测一次（与 handleSend 行为一致）
                val measured = if (event.accumulated.length - lastMeasuredLength >= 24) {
                    lastMeasuredLength = event.accumulated.length
                    measureHeight(event.accumulated, p.textMeasurer, p.density, p.pixelFont)
                } else null
                state.displayedMessages = state.displayedMessages.map { m ->
                    if (m.id == replyBoxId) {
                        m.copy(
                            text = displayText,
                            heightDp = measured ?: m.heightDp,
                            isLanded = true,
                            isUser = false,
                            isThinking = false
                        )
                    } else m
                }
            }

            is EngineEvent.PlanDetected -> {
                state.planAnchorMessageId = replyBoxId
                PlanStore.showSteps(event.steps)
            }

            is EngineEvent.ToolStarted -> {
                state.toolAnchorMessageId = replyBoxId
                state.toolCalls = state.toolCalls + ToolCallRecord(
                    id = ConversationStore.allocateMessageId(),
                    toolName = event.name,
                    argsText = event.args.entries.joinToString(" ") { "${it.key}=${it.value}" },
                    status = ToolCallStatus.Running,
                    resultPreview = "",
                    displayTime = getCurrentTime(),
                    anchorMessageId = replyBoxId
                )
            }

            is EngineEvent.ToolFinished -> {
                // 把最近一条同名 Running 记录置为终态
                val idx = state.toolCalls.indexOfLast {
                    it.toolName == event.name && it.status == ToolCallStatus.Running
                }
                if (idx >= 0) {
                    state.toolCalls = state.toolCalls.toMutableList().apply {
                        this[idx] = this[idx].copy(
                            status = if (event.success) ToolCallStatus.Done else ToolCallStatus.Error,
                            resultPreview = event.preview
                        )
                    }
                }
            }

            is EngineEvent.Finished -> {
                finalizeBubble(p, event.replyText)
                state.isAiResponding = false
                state.arrowPhase = ArrowPhase.Idle
            }

            is EngineEvent.Failed -> {
                finalizeBubble(p, event.message.ifBlank { "所有激活配置均未返回有效回复。" })
                state.isAiResponding = false
                state.arrowPhase = ArrowPhase.Idle
            }
        }
    }

    /** 收尾：按最终文本重测气泡高度 */
    private fun finalizeBubble(p: Projection, text: String) {
        val state = p.state
        val finalText = text.ifBlank { "…" }
        val height = measureHeight(finalText, p.textMeasurer, p.density, p.pixelFont)
        state.displayedMessages = state.displayedMessages.map { m ->
            if (m.id == replyBoxId) {
                m.copy(
                    text = finalText,
                    heightDp = height,
                    isLanded = true,
                    isUser = false,
                    isThinking = false
                )
            } else m
        }
    }

    /** 与 handleSend 相同的气泡测高逻辑 */
    private fun measureHeight(
        text: String,
        textMeasurer: TextMeasurer,
        density: Density,
        pixelFont: FontFamily
    ): Float {
        val maxTextWidthPx = with(density) { (200.dp - 20.dp).toPx() }
        val style = TextStyle(fontSize = 14.sp, fontFamily = pixelFont)
        val result = textMeasurer.measure(
            text = text,
            style = style,
            constraints = Constraints(maxWidth = maxTextWidthPx.roundToInt())
        )
        return with(density) {
            (result.size.height.toDp() + 20.dp).value.coerceAtLeast(50f)
        }
    }
}
