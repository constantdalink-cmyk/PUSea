package org.example.project

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * 剪贴板平台注入：commonMain 无系统剪贴板 API，由平台侧实现并注入。
 * 用于资源管理器左滑复制文件/文件夹路径。
 */
interface TextCopier {
    /** 复制文本到系统剪贴板。返回是否成功；实现侧可自行弹出反馈（如 Toast）。 */
    fun copy(text: String): Boolean
}

object ClipboardStore {
    private var copier: TextCopier? = null

    fun initialize(copier: TextCopier) {
        this.copier = copier
    }

    fun copy(text: String): Boolean {
        val c = copier ?: return false
        return runCatching { c.copy(text) }.getOrDefault(false)
    }
}

/**
 * 左滑复制路径修饰符：手指在条目上向左滑动（水平位移 > 48dp 且明显大于垂直位移）时，
 * 把 path 复制到系统剪贴板并回调 onCopied。
 *
 * 用法（在资源管理器条目 Row 上挂载）：
 *   Row(
 *       modifier = Modifier
 *           .fillMaxWidth()
 *           .clickable { ... }
 *           .copyOnLeftSwipe(path = entryPath) { copied ->
 *               // 可选：界面反馈（如 snackbar / 状态文字）
 *           }
 *   ) { ... }
 */
@Composable
fun Modifier.copyOnLeftSwipe(
    path: String,
    onCopied: (String) -> Unit = {}
): Modifier {
    val statePath = remember(path) { path }
    return this.pointerInput(statePath) {
        awaitEachGesture {
            val down = awaitFirstDown(
                requireUnconsumed = false,
                pass = PointerEventPass.Initial
            )
            val pointerId = down.id
            val thresholdPx = 48.dp.toPx()
            var totalX = 0f
            var totalY = 0f
            while (true) {
                val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                val delta = change.positionChange()
                totalX += delta.x
                totalY += delta.y
                if (!change.pressed) break
            }
            val isLeftSwipe = totalX < -thresholdPx &&
                abs(totalX) > abs(totalY) * 1.20f
            if (isLeftSwipe) {
                ClipboardStore.copy(statePath)
                onCopied(statePath)
            }
        }
    }
}
