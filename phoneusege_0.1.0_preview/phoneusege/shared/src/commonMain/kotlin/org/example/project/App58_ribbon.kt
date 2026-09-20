package org.example.project

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ==============================================================================
// 单个彩色带子列组件 (Ribbon Column View)
// ==============================================================================

@Composable
internal fun RibbonColumnView(
    col: RibbonColumnData,
    allCards: List<SkillCardItemData>,
    pixelFont: FontFamily,
    isEditing: Boolean,
    onStartEditing: () -> Unit,
    onFinishEditing: (String) -> Unit,
    onToggleSelection: (String) -> Unit,
    onStageAdvance: () -> Unit,
    onCardClick: (String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var inputName by remember(col.id, col.name) {
        mutableStateOf(if (col.name == "+") "" else col.name)
    }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    var lastTapTime by remember { mutableStateOf(0L) }

    // 滑入滑出动画控制器
    val slideAnimX = remember { Animatable(0f) }
    val slideAnimAlpha = remember { Animatable(1f) }
    var isTransitioning by remember { mutableStateOf(false) }

    // 监听编辑状态切换
    LaunchedEffect(isEditing) {
        if (isEditing) {
            inputName = if (col.name == "+") "" else col.name
            delay(60)
            focusRequester.requestFocus()
        } else {
            val trimmed = inputName.trim()
            val finalName = if (trimmed.isEmpty()) "+" else trimmed
            if (col.name != finalName) {
                onFinishEditing(finalName)
            }
        }
    }

    val currentStage by rememberUpdatedState(col.stage)
    val currentOnStageAdvance by rememberUpdatedState(onStageAdvance)

    // 双击触发三阶段平滑滑入滑出切换
    fun triggerStageAdvance() {
        if (isTransitioning) return
        coroutineScope.launch {
            isTransitioning = true
            when (currentStage) {
                0 -> {
                    slideAnimX.snapTo(-1.2f)
                    slideAnimAlpha.snapTo(0f)
                    currentOnStageAdvance()
                    val j1 = launch {
                        slideAnimX.animateTo(
                            0f,
                            animationSpec = tween(320, easing = CubicBezierEasing(0f, 0f, 0.2f, 1f))
                        )
                    }
                    val j2 = launch {
                        slideAnimAlpha.animateTo(
                            1f,
                            animationSpec = tween(320, easing = CubicBezierEasing(0f, 0f, 0.2f, 1f))
                        )
                    }
                    j1.join()
                    j2.join()
                }
                1 -> {
                    val j1 = launch {
                        slideAnimX.animateTo(
                            1.2f,
                            animationSpec = tween(260, easing = CubicBezierEasing(0.4f, 0f, 1f, 1f))
                        )
                    }
                    val j2 = launch {
                        slideAnimAlpha.animateTo(
                            0f,
                            animationSpec = tween(260, easing = CubicBezierEasing(0.4f, 0f, 1f, 1f))
                        )
                    }
                    j1.join()
                    j2.join()

                    currentOnStageAdvance()
                    slideAnimX.snapTo(-1.2f)
                    slideAnimAlpha.snapTo(0f)

                    val j3 = launch {
                        slideAnimX.animateTo(
                            0f,
                            animationSpec = tween(320, easing = CubicBezierEasing(0f, 0f, 0.2f, 1f))
                        )
                    }
                    val j4 = launch {
                        slideAnimAlpha.animateTo(
                            1f,
                            animationSpec = tween(320, easing = CubicBezierEasing(0f, 0f, 0.2f, 1f))
                        )
                    }
                    j3.join()
                    j4.join()
                }
                2 -> {
                    val j1 = launch {
                        slideAnimX.animateTo(
                            1.2f,
                            animationSpec = tween(260, easing = CubicBezierEasing(0.4f, 0f, 1f, 1f))
                        )
                    }
                    val j2 = launch {
                        slideAnimAlpha.animateTo(
                            0f,
                            animationSpec = tween(260, easing = CubicBezierEasing(0.4f, 0f, 1f, 1f))
                        )
                    }
                    j1.join()
                    j2.join()

                    currentOnStageAdvance()
                    slideAnimX.snapTo(0f)
                    slideAnimAlpha.snapTo(1f)
                }
            }
            isTransitioning = false
        }
    }

    Box(
        modifier = Modifier
            .width(col.widthDp.dp)
            .fillMaxHeight()
            .clipToBounds()
            .background(col.color)
            .border(width = 0.5.dp, color = Color(0x14000000))
            .pointerInput(col.id, col.name) {
                detectTapGestures(
                    onTap = {
                        val now = System.currentTimeMillis()
                        if (now - lastTapTime < 350) {
                            if (col.name != "+" && col.name.isNotBlank()) {
                                triggerStageAdvance()
                            }
                        }
                        lastTapTime = now
                    }
                )
            }
            .padding(top = 14.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ===== 顶部取名单元区 =====
            val btnAlpha by animateFloatAsState(
                targetValue = if (isEditing) 0f else 1f,
                animationSpec = tween(200, easing = FastOutSlowInEasing)
            )
            val btnScale by animateFloatAsState(
                targetValue = if (isEditing) 0.6f else 1f,
                animationSpec = tween(200, easing = FastOutSlowInEasing)
            )
            val lineWidthFraction by animateFloatAsState(
                targetValue = if (isEditing) 0.85f else 0f,
                animationSpec = tween(300, easing = CubicBezierEasing(0.25f, 1f, 0.5f, 1f))
            )
            val inputAlpha by animateFloatAsState(
                targetValue = if (isEditing) 1f else 0f,
                animationSpec = tween(200, easing = FastOutSlowInEasing)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .clickable {
                        if (!isEditing) {
                            onStartEditing()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (btnAlpha > 0.01f) {
                    Text(
                        text = col.name,
                        fontFamily = pixelFont,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF111111),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .graphicsLayer {
                                alpha = btnAlpha
                                scaleX = btnScale
                                scaleY = btnScale
                            }
                    )
                }

                if (inputAlpha > 0.01f || isEditing) {
                    BasicTextField(
                        value = inputName,
                        onValueChange = { if (it.length <= 5) inputName = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            fontFamily = pixelFont,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF111111),
                            textAlign = TextAlign.Center
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            val trimmed = inputName.trim()
                            val finalName = if (trimmed.isEmpty()) "+" else trimmed
                            onFinishEditing(finalName)
                            focusManager.clearFocus()
                        }),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth(0.8f)
                            .graphicsLayer {
                                alpha = inputAlpha
                            }
                            .focusRequester(focusRequester)
                    )
                }

                if (lineWidthFraction > 0.001f) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth(lineWidthFraction)
                            .height(2.dp)
                            .background(Color(0xFF111111), RoundedCornerShape(1.dp))
                    )
                }
            }

            // ===== 竖向排列的真实获得资源展示块容器 =====
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clipToBounds()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = slideAnimX.value * size.width
                            alpha = slideAnimAlpha.value
                        }
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 6.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    when (col.stage) {
                        0 -> {
                            // 阶段 0: 空白状态
                        }
                        1 -> {
                            // 阶段 1: 展示所有卡片供多选勾选
                            allCards.forEach { card ->
                                val isSelected = card.id in col.selectedItemIds
                                val itemScale by animateFloatAsState(
                                    targetValue = if (isSelected) 1.04f else 1.0f,
                                    animationSpec = tween(200, easing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f))
                                )
                                val itemBgColor by animateColorAsState(
                                    targetValue = if (isSelected) Color(0xFF2ECC71) else Color(0xBFDFDFDF),
                                    animationSpec = tween(250)
                                )
                                val itemBorderColor by animateColorAsState(
                                    targetValue = if (isSelected) Color(0xFF27AE60) else Color.Transparent,
                                    animationSpec = tween(250)
                                )
                                val itemTextColor by animateColorAsState(
                                    targetValue = if (isSelected) Color.White else Color(0xFF222222),
                                    animationSpec = tween(250)
                                )

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .graphicsLayer {
                                            scaleX = itemScale
                                            scaleY = itemScale
                                        }
                                        .shadow(
                                            elevation = if (isSelected) 4.dp else 1.dp,
                                            shape = RoundedCornerShape(8.dp),
                                            ambientColor = if (isSelected) Color(0x732ECC71) else Color(0x1A000000),
                                            spotColor = if (isSelected) Color(0x732ECC71) else Color(0x1A000000)
                                        )
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(itemBgColor)
                                        .border(
                                            1.5.dp,
                                            itemBorderColor,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable {
                                            onToggleSelection(card.id)
                                        }
                                        .padding(vertical = 8.dp, horizontal = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = card.name,
                                        fontFamily = pixelFont,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = itemTextColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                        2 -> {
                            // 阶段 2: 仅展示已确认选中的真实获得资源展示块
                            val activeCards = allCards.filter { it.id in col.selectedItemIds }
                            activeCards.forEach { card ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .shadow(
                                            elevation = 4.dp,
                                            shape = RoundedCornerShape(8.dp),
                                            ambientColor = Color(0x732ECC71),
                                            spotColor = Color(0x732ECC71)
                                        )
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF2ECC71))
                                        .border(1.5.dp, Color(0xFF27AE60), RoundedCornerShape(8.dp))
                                    .clickable {
                                        onCardClick(card.id)
                                    }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = card.name,
                                    fontFamily = pixelFont,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
        }
    }
}
