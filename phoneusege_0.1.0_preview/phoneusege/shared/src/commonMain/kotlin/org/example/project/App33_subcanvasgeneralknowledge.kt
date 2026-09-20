package org.example.project

// ==============================================================================
// 画布文件 3/10：subcanvasgeneralknowledge.kt —— 通识问答画布
// 职责：SubCanvasGeneralKnowledge 占位子画布（待实现）
// 依赖：subcanvasdispatcher.kt（SlideDownContainer，同包直接调用）
// ==============================================================================
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily

@Composable
fun SubCanvasGeneralKnowledge(visible: Boolean, onClose: () -> Unit, pixelFont: FontFamily) {
    SlideDownContainer(visible, uiText(UiText.GeneralKnowledge), onClose, pixelFont) {
        // TODO: 待实现
    }
}
