package org.example.project

// ==============================================================================
// 画布文件 10/10：subcanvaslanguage.kt —— 语言画布
// 职责：SubCanvasLanguage 语言滚轮选择 + 退出确认弹窗 + LanguageWheelRow
// 依赖：subcanvasdispatcher.kt（SlideDownContainer）、外部符号（uiText / UiText / GreenPeelOffEasing）
//
// 修复：滑动不顺滑 / 大幅度滑动一卡一卡 ——
//   1) pointerInput 不再以 selectedIndex / isSettling 为 key,手势检测器不会
//      在每次落定时重启,进行中的触摸不再被打断;
//   2) 落定动画改用可取消的 Job,新触摸 onDragStart 立即接管,连续滑动无缝衔接;
//   3) 引入帧级速度追踪,按"当前偏移 + 速度惯性"的投影终点一次跨越多项,
//      大幅度甩动不再是一格一格地跳。
//
// ⚠ 编译错误修复（compileAndroidMain FAILED 的三个根因）：
//   A) VelocityTracker 的包名在 commonMain 是
//      androidx.compose.ui.input.pointer.util.VelocityTracker，
//      原写法 androidx.compose.ui.input.pointer.VelocityTracker 是旧 Android
//      专属路径 → Unresolved reference 'VelocityTracker'（18/193/198 行）。
//   B) detectVerticalDragGestures 的 onDragStart 形参是 (Offset) -> Unit，
//      不是 PointerInputChange → down.uptimeMillis / down.position
//      Unresolved reference（199 行）。
//   C) runCatching { tracker.calculateVelocity().y } 因 A 无法推断
//      → Cannot infer type for type parameter 'R'（202 行，连锁错误）。
//   解决方案：不再使用 VelocityTracker / uptimeMillis —— 这两者的包名与
//   类型（Long ↔ Uptime）在 Compose 各版本 commonMain 中不一致，跨版本极易
//   再次报错。改为自算帧级速度：onVerticalDrag 约每帧回调一次，dragAmount
//   即"本帧位移"，对其做指数平滑即得抬手瞬间的每帧速度，乘以预估帧数
//   （FLING_FRAME_GAIN）即惯性投影终点 —— 甩动跨多项手感不变，且零版本依赖。
// ==============================================================================
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// LANGUAGE_WHEEL_BEGIN

// ---- 甩动（fling）手感参数 ----
// 平滑系数：越大越跟手（越贴近最近一帧的真实速度），越小越稳（抗抖动）
private const val VELOCITY_SMOOTHING = 0.65f
// 惯性放大帧数：抬手后按"每帧速度 × N 帧"投影终点，N 越大甩得越远
private const val FLING_FRAME_GAIN = 10f
// 单次甩动最多跨越的项数（防止误触一次飞出七八项）
private const val MAX_FLING_STEPS = 4

private data class LanguageWheelOption(
    val code: String,
    val nativeName: String,
    val englishName: String
)

@Composable
fun SubCanvasLanguage(
    visible: Boolean,
    onClose: () -> Unit,
    pixelFont: FontFamily,
    currentLanguageCode: String = "en",
    onConfirmLanguageAndExit: (String) -> Unit = {}
) {
    val languages = remember {
        listOf(
            LanguageWheelOption("zh-Hans", "简体中文", "Chinese / Simplified"),
            LanguageWheelOption("zh-Hant", "繁體中文", "Chinese / Traditional"),
            LanguageWheelOption("en", "English", "English"),
            LanguageWheelOption("ja", "日本語", "Japanese"),
            LanguageWheelOption("ko", "한국어", "Korean"),
            LanguageWheelOption("fr", "Français", "French"),
            LanguageWheelOption("de", "Deutsch", "German")
        )
    }

    val savedLanguageIndex = remember(currentLanguageCode) {
        val initial = languages.indexOfFirst { it.code == currentLanguageCode }
        if (initial >= 0) initial else 0
    }

    var selectedIndex by remember(currentLanguageCode) {
        mutableStateOf(savedLanguageIndex)
    }

    var showExitConfirm by remember { mutableStateOf(false) }
    var dragOffsetPx by remember { mutableStateOf(0f) }
    var isSettling by remember { mutableStateOf(false) }
    var settleJob by remember { mutableStateOf<Job?>(null) }

    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val itemSpacingPx = with(density) { 76.dp.toPx() }
    val switchThresholdPx = itemSpacingPx * 0.28f
    val maximumDragPx = itemSpacingPx * 1.15f

    // 取消进行中的落定动画(新触摸接管时调用)
    fun cancelSettle() {
        settleJob?.cancel()
        settleJob = null
        isSettling = false
    }

    // 一次落定 rawSteps 项(可正可负),动画时长随距离自适应
    fun settleTo(rawSteps: Int) {
        cancelSettle()
        val steps = rawSteps
            .coerceIn(-MAX_FLING_STEPS, MAX_FLING_STEPS)
            .coerceIn(-selectedIndex, languages.lastIndex - selectedIndex)
        isSettling = true
        settleJob = scope.launch {
            val target = -(steps.toFloat() * itemSpacingPx)
            val duration = (240 + 80 * abs(steps)).coerceAtMost(640)
            animate(
                initialValue = dragOffsetPx,
                targetValue = target,
                animationSpec = tween(durationMillis = duration, easing = GreenPeelOffEasing)
            ) { v, _ -> dragOffsetPx = v }

            if (steps != 0) selectedIndex += steps
            dragOffsetPx = 0f
            isSettling = false
        }
    }

    SlideDownContainer(
        visible = visible,
        title = uiText(UiText.Language),
        onClose = {
            if (selectedIndex == savedLanguageIndex) {
                onClose()
            } else {
                showExitConfirm = true
            }
        },
        pixelFont = pixelFont,
        showDefaultTitle = false
    ) {
        Text(
            text = uiText(UiText.Language),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 12.dp, top = 12.dp),
            color = Color.Black,
            fontSize = 20.sp,
            fontFamily = pixelFont,
            fontWeight = FontWeight.Bold
        )

        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(top = 44.dp)
                .height(1.dp)
                .background(Color(0xFFBDBDBD))
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(start = 12.dp, end = 12.dp, top = 58.dp, bottom = 112.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = uiText(UiText.SwipeVerticallyToSelect),
                color = Color.Black.copy(alpha = 0.48f),
                fontSize = 11.sp,
                fontFamily = pixelFont,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clipToBounds()
                    // 关键修复:key 固定为 Unit,selectedIndex / isSettling 变化不再
                    // 重启手势检测器,滑动全程不会被打断(消除一卡一卡)
                    .pointerInput(Unit) {
                        // 帧级速度追踪（替代 VelocityTracker）：
                        // onVerticalDrag 约每帧回调一次，dragAmount = 本帧位移，
                        // 指数平滑后即"抬手瞬间每帧速度"，无需任何时间戳 API。
                        var flingPxPerFrame = 0f
                        detectVerticalDragGestures(
                            // ⚠ 形参类型是 Offset（落点位置），不是 PointerInputChange
                            onDragStart = { _: androidx.compose.ui.geometry.Offset ->
                                // 新触摸立即接管:打断未完成的落定动画,连续滑动无缝衔接
                                cancelSettle()
                                flingPxPerFrame = 0f
                            },
                            onDragEnd = {
                                // 投影终点 = 当前偏移 + 速度惯性,大幅甩动可一次跨越多项
                                val projected = dragOffsetPx + flingPxPerFrame * FLING_FRAME_GAIN
                                var steps = (-projected / itemSpacingPx).roundToInt()
                                if (steps == 0 && abs(projected) >= switchThresholdPx) {
                                    steps = if (projected < 0f) 1 else -1
                                }
                                settleTo(steps)
                            },
                            onDragCancel = {
                                settleTo(0)
                            },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                // 速度平滑：越接近抬手的那几帧权重越高
                                flingPxPerFrame = flingPxPerFrame * (1f - VELOCITY_SMOOTHING) +
                                    dragAmount * VELOCITY_SMOOTHING
                                val proposed = dragOffsetPx + dragAmount
                                val beyondFirst = selectedIndex == 0 && proposed > 0f
                                val beyondLast = selectedIndex == languages.lastIndex && proposed < 0f
                                dragOffsetPx = if (beyondFirst || beyondLast) {
                                    // 边缘橡皮筋阻尼
                                    (dragOffsetPx + dragAmount * 0.28f).coerceIn(-maximumDragPx, maximumDragPx)
                                } else {
                                    proposed.coerceIn(-maximumDragPx, maximumDragPx)
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {

                languages.forEachIndexed { index, lang ->
                    val dist = index - selectedIndex
                    if (abs(dist) <= 3) {
                        val offset = dist * itemSpacingPx + dragOffsetPx
                        val norm = abs(offset) / itemSpacingPx
                        val a = (1f - norm * 0.28f).coerceIn(0.12f, 1f)
                        val s = (1f - norm * 0.075f).coerceIn(0.80f, 1f)
                        val isCenter = norm < 0.50f

                        LanguageWheelRow(
                            language = lang,
                            isCenterItem = isCenter,
                            alpha = a,
                            scale = s,
                            translationYPx = offset,
                            pixelFont = pixelFont,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .zIndex(10f - norm),
                            onClick = {
                                // 点击任意可见项:直接平滑滚动到该项(支持跨级)
                                settleTo(index - selectedIndex)
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = languages[selectedIndex].code.uppercase(),
                color = Color.Black.copy(alpha = 0.42f),
                fontSize = 10.sp,
                fontFamily = pixelFont,
                textAlign = TextAlign.Center
            )
        }

        // ================= 底部圆角黑色关闭方框 =================
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 38.dp)
                .background(Color.Black, RoundedCornerShape(10.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    // 复用与顶部容器一致的退出确认逻辑：
                    // 未改动语言 -> 直接关闭；已改动语言 -> 弹出应用确认框
                    if (selectedIndex == savedLanguageIndex) {
                        onClose()
                    } else {
                        showExitConfirm = true
                    }
                }
                .padding(horizontal = 28.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = uiText(UiText.Close),
                color = Color.White,
                fontSize = 14.sp,
                fontFamily = pixelFont,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }

    if (showExitConfirm) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(2000f)
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    selectedIndex = savedLanguageIndex
                    showExitConfirm = false
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 32.dp)
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(12.dp))
                    .border(2.dp, Color.Black, RoundedCornerShape(12.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { }
                    .padding(20.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = uiText(UiText.ApplyLanguageChange),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = pixelFont,
                        color = Color.Black,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = uiText(UiText.RestartToApply),
                        fontSize = 12.sp,
                        fontFamily = pixelFont,
                        color = Color.Black.copy(alpha = 0.65f),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .background(Color.Black.copy(alpha = 0.06f), RoundedCornerShape(6.dp))
                                .border(1.dp, Color.Black.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    selectedIndex = savedLanguageIndex
                                    showExitConfirm = false
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = uiText(UiText.Cancel),
                                fontSize = 13.sp,
                                fontFamily = pixelFont,
                                color = Color.Black
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .background(Color.Black, RoundedCornerShape(6.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    showExitConfirm = false
                                    onConfirmLanguageAndExit(languages[selectedIndex].code)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = uiText(UiText.ExitNow),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = pixelFont,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LanguageWheelRow(
    language: LanguageWheelOption,
    isCenterItem: Boolean,
    alpha: Float,
    scale: Float,
    translationYPx: Float,
    pixelFont: FontFamily,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .width(280.dp)
            .height(58.dp)
            .graphicsLayer {
                this.alpha = alpha
                translationY = translationYPx
                scaleX = scale
                scaleY = scale
            }
            // 无按压反馈(水波纹已移除)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = language.nativeName,
            color = Color.Black,
            fontSize = if (isCenterItem) 20.sp else 15.sp,
            fontFamily = pixelFont,
            fontWeight = if (isCenterItem) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center
        )
    }
}

// LANGUAGE_WHEEL_END