package org.example.project

// ==============================================================================
// 基础文件：subcanvasdispatcher.kt —— 分发中心与滑动容器
// 职责：SubCanvasDispatcher 分发中心 + SlideDownContainer 滑动容器（全部画布共用）
// 结构：10 个画布各占一个文件，统一依赖本基础文件（同包，无需 import）
// 依赖：仅外部符号（uiText / UiText / GreenPeelOffEasing），不依赖任何画布文件
// 说明：SlideDownContainer 的 title / showDefaultTitle / onClose 参数保留，
//       仅为兼容各画布现有的旧签名调用；顶部大字幕与底部关闭按钮已移除，
//       这些参数不再渲染任何内容
// ==============================================================================
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/**
 * 【App3 分发中心】根据 activeSubCanvas 显示对应的子画布
 */
@Composable
fun SubCanvasDispatcher(
    activeSubCanvas: String?,
    onClose: () -> Unit,
    pixelFont: FontFamily,
    currentLanguageCode: String = "en",
    onConfirmLanguageAndExit: (String) -> Unit = {}
) {
    SubCanvasComponentPlaza(activeSubCanvas == "ComponentPlaza", onClose, pixelFont)
    SubCanvasChess(activeSubCanvas == "Chess", onClose, pixelFont)
    SubCanvasGeneralKnowledge(activeSubCanvas == "GeneralKnowledge", onClose, pixelFont)
    SubCanvasComponentManagement(activeSubCanvas == "ComponentManagement", onClose, pixelFont)
    SubCanvasFunctionManagement(activeSubCanvas == "FunctionManagement", onClose, pixelFont)
    SubCanvasSkillPlaza(activeSubCanvas == "SkillPlaza", onClose, pixelFont)
    SubCanvasSkillManagement(activeSubCanvas == "SkillManagement", onClose, pixelFont)
    SubCanvasKey(activeSubCanvas == "Key", onClose, pixelFont)
    SubCanvasAIAssistSystem(activeSubCanvas == "AIAssistSystem", onClose, pixelFont)
    SubCanvasSelf(activeSubCanvas == "Self", onClose, pixelFont)
    SubCanvasCredits(activeSubCanvas == "Credits", onClose, pixelFont)
    SubCanvasLanguage(activeSubCanvas == "Language", onClose, pixelFont, currentLanguageCode, onConfirmLanguageAndExit)
}

// ==================== 私有滑动容器 ====================
// 兼容旧签名：title / showDefaultTitle / onClose 保留但不再使用，
// 顶部大字幕与底部关闭按钮已按需求移除
@Composable
internal fun SlideDownContainer(
    visible: Boolean,
    title: String,
    onClose: () -> Unit,
    pixelFont: FontFamily,
    showDefaultTitle: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    val subCanvasAnim = remember { Animatable(if (visible) 0f else -1f) }

    LaunchedEffect(visible) {
        subCanvasAnim.animateTo(
            targetValue = if (visible) 0f else -1f,
            animationSpec = tween(
                durationMillis = if (visible) 650 else 240, // 退出时从原 420ms 提速至 240ms，彻底消除滞留与拖尾感
                easing = GreenPeelOffEasing
            )
        )
    }

    val rendered = visible || subCanvasAnim.value > -0.999f
    if (!rendered) return

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(1000f)
            .graphicsLayer {
                translationY = subCanvasAnim.value * size.height
            }
            .background(Color.White)
            .border(3.dp, Color.Black)
            .clickable { }
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(6.dp)
                .background(Color.Black)
        )

        content()
    }
}
