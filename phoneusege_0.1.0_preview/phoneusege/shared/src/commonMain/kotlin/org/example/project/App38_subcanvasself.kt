package org.example.project

// ==============================================================================
// 画布文件 8/10：subcanvasself.kt —— 自我画布
// 职责：SubCanvasSelf 占位子画布（待实现）
// 依赖：subcanvasdispatcher.kt（SlideDownContainer，同包直接调用）
// ==============================================================================
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily

@Composable
fun SubCanvasSelf(visible: Boolean, onClose: () -> Unit, pixelFont: FontFamily) {
    SlideDownContainer(visible, uiText(UiText.Self), onClose, pixelFont) {
        // TODO: 待实现
    }
}
