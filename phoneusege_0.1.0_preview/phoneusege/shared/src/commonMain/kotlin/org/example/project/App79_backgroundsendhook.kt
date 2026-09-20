package org.example.project

import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density

// ==============================================================================
// BackgroundSendHook —— commonMain ⇄ androidMain 的"后台执行接线点"（依赖注入缝）。
//
// 为什么需要它：
//   handleSend 在 commonMain（纯 Kotlin，碰不到 Android）；KeepAliveBridge / 前台
//   服务在 androidMain。commonMain 无法直接引用 androidMain，所以用一个"缝"：
//   commonMain 只定义接口 + 持有器，androidMain（MainActivity）启动时把实现注入进来。
//
// 效果：
//   注入后，handleSend 播完用户气泡飞入动画，就把整段 AI 执行（持久化 / 流式 /
//   工具循环 / 回复落盘 / UI 投影）交给前台服务常驻运行——调用 handleSend 的位置
//   （App 的 onSend，无论它在哪）一行都不用改。
//   未注入（executor == null，如纯 commonMain 环境）→ handleSend 走原有内联逻辑，
//   行为完全不变。
// ==============================================================================

/** 后台执行器：把一轮对话的 AI 执行交给前台服务（由 androidMain 实现） */
fun interface BackgroundSendExecutor {
    fun execute(
        state: ChatUiState,
        msg: String,
        density: Density,
        pixelFont: FontFamily,
        textMeasurer: TextMeasurer
    )
}

/** 接线点持有器：androidMain 启动时注入，handleSend 运行时读取 */
object BackgroundSendHook {
    @Volatile
    var executor: BackgroundSendExecutor? = null
}
