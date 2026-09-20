package org.example.project

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext

// ==============================================================================
// KeepAliveBridgeHooks —— App() 侧的一键接线钩子。
//
// 在你的 App() composable 里：
//
//   ① 顶部拿桥（自动 bind / unbind，随组合生命周期）：
//        val bridge = rememberKeepAliveBridge()
//
//   ② 把原来直接调 handleSend(...) 的 onSend 回调，换成：
//        bridge.send(
//            state      = chatUiState,
//            msg        = inputText,
//            textMeasurer = textMeasurer,
//            density      = density,
//            pixelFont    = pixelFont,
//            addUserBubble = false   // ← 保留你自己的"用户气泡飞入动画"
//        )
//
//   关于 addUserBubble：
//     - false = 用户气泡由你现有的飞入动画代码挂上（保留动画），桥只负责 AI 侧
//       （思考气泡 / 流式文本 / 工具气泡 / plan / 箭头）。
//       ⚠ 此时你的飞入动画代码里【不要再 ConversationStore.append(userMsg)】——
//       持久化已统一由引擎完成，重复 append 会出现两条一样的消息。
//     - true  = 桥直接把用户气泡挂上（无飞入动画），最省事。
// ==============================================================================

/** 记忆并保持一个"已绑定服务"的 KeepAliveBridge（组合销毁时自动解绑） */
@Composable
fun rememberKeepAliveBridge(): KeepAliveBridge {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val bridge = remember { KeepAliveBridge(context) }
    DisposableEffect(Unit) {
        bridge.bind(scope)
        onDispose { bridge.unbind() }
    }
    return bridge
}
