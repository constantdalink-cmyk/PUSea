package org.example.project

// ==============================================================================
// 画布文件 2/10：subcanvaschess.kt —— 象棋画布
// 职责：SubCanvasChess 占位子画布（待实现）
// 依赖：subcanvasdispatcher.kt（SlideDownContainer，同包直接调用）
// ==============================================================================
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily

@Composable
fun SubCanvasChess(visible: Boolean, onClose: () -> Unit, pixelFont: FontFamily) {
    SlideDownContainer(visible, uiText(UiText.Chess), onClose, pixelFont) {
        // TODO: 待实现
    }
}
