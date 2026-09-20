package org.example.project

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

// ==============================================================================
// 【本地化记录 · 第三版重接】功能管理画布（垃圾桶数据分区 + "ARE YOU REAL?" 删除确认窗）
//   本版相对上一版的行为差异（均不涉及新增可见字幕，无需新增词典键）：
//     · 左竖块开关由本地 remember 状态升级为 ContactIconStore.showContactIcons
//       持久化全局信号（绿点=显示联系方式图标；粉色=隐藏；重启后保持上次状态）。
//     · collapseUnit() 不再重置任一竖块：显隐与聊天模式都是已持久化的全局信号，
//       leftOn / rightOn 均从 Store 只读派生，不随垃圾桶归位被意外改动。
//
//   5 处硬编码用户可见字幕维持 uiText(UiText.FunctionMgmtXxx) 接线，
//   走 AppLanguage 七语词典（en / zh-Hans / zh-Hant / ja / ko / fr / de）：
//     1. "Skills Data"                   → UiText.FunctionMgmtSkillsDataTitle
//     2. "Delete your downloaded skills" → UiText.FunctionMgmtSkillsDataDesc
//     3. "Tools Data"                    → UiText.FunctionMgmtToolsDataTitle
//     4. "Delete your downloaded tools"  → UiText.FunctionMgmtToolsDataDesc
//     5. "ARE YOU REAL?"                 → UiText.FunctionMgmtAreYouReal
//
//   【项目约定·牢记】确认窗两个字幕方框的文字恒定：
//     圆字幕(蓝方框) = "O"（确认删光）；叉字幕(红方框) = "X"（反悔）。
//   "O" / "X" 为语言无关的通用确认/取消图形符号（七语含义一致），不入词典；
//   来料中若出现以 "圆字幕" / "叉字幕" 占位的写法，一律还原为 "O" / "X"。
//
//   生效保证：uiText() 调用均位于 @Composable 重组路径，AppLanguage.initialize(newCode)
//   后画布重组即显示新语言；text() 三重兜底保证缺键只降级不崩溃。
// ==============================================================================

/** 确认弹窗里"叉字幕"方框的亮红色 */
private val CrossRed = Color(0xFFFF3B30)

@Composable
internal fun Chevron(direction: Int, enabled: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.8f else 1f, tween(120), label = "chev")
    Box(
        Modifier
            .padding(8.dp)
            .size(8.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
    ) {
        Canvas(Modifier.size(8.dp)) {
            val w = 2.dp.toPx()
            val path = Path().apply {
                if (direction < 0) {
                    moveTo(6.dp.toPx(), 1.dp.toPx())
                    lineTo(2.dp.toPx(), 4.dp.toPx())
                    lineTo(6.dp.toPx(), 7.dp.toPx())
                } else {
                    moveTo(2.dp.toPx(), 1.dp.toPx())
                    lineTo(6.dp.toPx(), 4.dp.toPx())
                    lineTo(2.dp.toPx(), 7.dp.toPx())
                }
            }
            drawPath(path, Ink, style = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

@Composable
internal fun BottomArea(pixelFont: FontFamily) {
    Column(
        Modifier.offset(y = 334.dp).fillMaxWidth().padding(top = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 上排：保留两个竖块；左右两个竖块的动画与样式完全一致
        TrashRowUnit(
            dir = 1,
            // 【已本地化】原硬编码 "Skills Data" / "Delete your downloaded skills"
            title = uiText(UiText.FunctionMgmtSkillsDataTitle),
            description = uiText(UiText.FunctionMgmtSkillsDataDesc),
            pixelFont = pixelFont,
            showSwitches = true,
        )
        Spacer(Modifier.height(16.dp))
        // 下排：删掉两个竖块，仅保留垃圾桶大方块(Tools 删除入口)
        TrashRowUnit(
            dir = -1,
            // 【已本地化】原硬编码 "Tools Data" / "Delete your downloaded tools"
            title = uiText(UiText.FunctionMgmtToolsDataTitle),
            description = uiText(UiText.FunctionMgmtToolsDataDesc),
            pixelFont = pixelFont,
            showSwitches = false,
        )
    }
}

@Composable
private fun TrashRowUnit(
    dir: Int,
    title: String,
    description: String,
    pixelFont: FontFamily,
    showSwitches: Boolean = true,
) {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    var expanded by remember { mutableStateOf(false) }
    var lifting by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    // 第一个竖块 = 联系方式图标显隐信号，直接读 ContactIconStore(已持久化)：
    // 绿点状态(active=false) = 显示；粉色状态(active=true) = 隐藏——重启后保持上次绿/粉
    val leftOn = !ContactIconStore.showContactIcons
    // 第二个竖块 = 聊天模式信号，直接读 ChatModeStore(单一来源，标记被消耗时开关自动归位)：
    // 拨开 = 即时聊天【不存档】(只作用于下一次新开的对话)；默认 = 长久聊天【仍然存档】
    val rightOn = ChatModeStore.ephemeralNext
    var showConfirm by remember { mutableStateOf(false) }

    fun toggle() {
        if (busy) return
        if (expanded) {
            // 第二次点击 = 触发删除信号：不再直接删，而是弹出 "ARE YOU REAL?" 确认窗——
            // 蓝框"圆字幕"确认删光，红框"叉字幕"反悔。无论点哪个，本垃圾桶大方块都会归位。
            showConfirm = true
        } else {
            // 第一次点击 = 装填删除信号：轻震一次，垃圾桶进入"武装态"（边框/图标变红）
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            busy = true
            lifting = true
            scope.launch {
                delay(200)
                expanded = true
                // 展开完成立即解锁：保证"第二次点击=触发弹窗"随时生效，
                // 不再被动画锁吞掉（原先要等满 900ms，快速双击会丢信号）
                busy = false
            }
        }
    }

    // 无论确认还是反悔（含返回键 / 点窗外），垃圾桶大方块一律归位
    fun collapseUnit() {
        expanded = false
        lifting = false
        // 两个竖块都不再重置：显隐与聊天模式都是已持久化的全局信号，
        // 不随垃圾桶归位而被意外改动(leftOn/rightOn 均从 Store 派生，只读)
        busy = true
        scope.launch { delay(700); busy = false }
    }

    if (showConfirm) {
        Dialog(onDismissRequest = {
            showConfirm = false
            collapseUnit()
        }) {
            AreYouRealDialog(
                pixelFont = pixelFont,
                onConfirm = {
                    // "圆字幕"(蓝方框) = 真正的删除触发信号
                    showConfirm = false
                    if (dir == 1) {
                        // Skills 数据一键删光：清列表 + 只落盘一次 + 摘除已注入 AI 技能
                        if (DownloadedResourceStore.downloadedCards.isNotEmpty()) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            DownloadedResourceStore.clearAllCards()
                        }
                    } else {
                        // Tools 数据一键删光：注销全部 MCP 工具 + 清安装队列 + 只落盘一次
                        if (PlazaStore.installedIds.isNotEmpty() || PlazaStore.installTasks.isNotEmpty()) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            PlazaStore.uninstallAllTools()
                        }
                    }
                    collapseUnit()
                },
                onCancel = {
                    // "叉字幕"(红方框) = 反悔：什么都不删，垃圾桶归位
                    showConfirm = false
                    collapseUnit()
                },
            )
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            Modifier.width(211.dp).height(95.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 竖块(仅上排保留)：左竖块与右竖块完全一致——同样的按压回弹动画与默认配色
            if (showSwitches) {
                SideSwitch(
                    active = leftOn,
                    dimmed = expanded,
                    // 拨动翻转联系方式图标显隐并立即落盘(显示→隐藏 / 隐藏→显示)
                    onToggle = { ContactIconStore.setContactIconsVisible(!ContactIconStore.showContactIcons) },
                )
            } else {
                Spacer(Modifier.width(24.dp))
            }

            Box(Modifier.size(95.dp), contentAlignment = Alignment.Center) {
                val barAlpha by animateFloatAsState(
                    if (expanded) 1f else 0f, tween(700, easing = ExpandEase), label = "bar",
                )
                // 字幕(标题/描述)随大方块移动方向外移：上排(dir=1)向右、下排(dir=-1)向左。
                // 比大方块的位移晚 120ms 起步，形成"方块先移动、字幕随后滑出"的两拍节奏；
                // 落位后与横线齐平。横线保留自身静态偏移，不做任何改动(这次动的不是横线！)。
                val subtitleShift by animateFloatAsState(
                    if (expanded) 14f * dir else 0f,
                    tween(700, delayMillis = 120, easing = ExpandEase),
                    label = "subtitleShift",
                )
                Box(
                    Modifier
                        .requiredWidth(235.dp)
                        .height(95.dp)
                        .graphicsLayer { alpha = barAlpha }
                        .shadow(6.dp, RoundedCornerShape(20.dp))
                        .background(PureWhite, RoundedCornerShape(20.dp))
                        .border(2.dp, Ink, RoundedCornerShape(20.dp)),
                )
                Column(
                    Modifier
                        // 字幕区锚定在垃圾桶大方块的露出侧，与大方块(缩放+位移后的外缘)完全让位、零重叠：
                        // 上排(dir=1)大方块移左 → 字幕贴本盒左缘向右铺开；
                        // 下排(dir=-1)大方块移右 → 字幕贴本盒右缘向左铺开。
                        // 限宽 131dp：字幕再外滑 14dp 也收在 235dp 白条内(超宽自动换行)
                        .widthIn(max = 131.dp)
                        .wrapContentWidth(unbounded = true, align = if (dir == 1) Alignment.Start else Alignment.End)
                        .offset(x = (14 * dir).dp)
                        .graphicsLayer { alpha = barAlpha },
                    horizontalAlignment = if (dir == 1) Alignment.Start else Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    BasicText(
                        text = title,
                        modifier = Modifier.offset(x = subtitleShift.dp),
                        style = TextStyle(
                            fontFamily = pixelFont,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Ink,
                        ),
                    )
                    // 承托字幕的横线随大方块移动方向外移：上排(dir=1)向右、下排(dir=-1)向左
                    Box(
                        Modifier
                            .offset(x = (14 * dir).dp)
                            .width(60.dp)
                            .height(2.dp)
                            .background(Ink, RoundedCornerShape(2.dp)),
                    )
                    BasicText(
                        text = description,
                        modifier = Modifier.offset(x = subtitleShift.dp),
                        style = TextStyle(
                            fontFamily = pixelFont,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium,
                            color = LineGray,
                        ),
                    )
                    Box(
                        Modifier
                            .offset(x = (14 * dir).dp)
                            .width(100.dp)
                            .height(2.dp)
                            .background(Ink, RoundedCornerShape(2.dp)),
                    )
                }
                LargeTrashRect(dir = dir, expanded = expanded, lifting = lifting, onClick = ::toggle)
            }

            // 下排已删掉竖块：不渲染开关，等宽占位保持垃圾桶大方块居中
            if (showSwitches) {
                SideSwitch(
                    active = rightOn,
                    dimmed = expanded,
                    // 开 = 即时聊天(不存档)；关 = 长久聊天(仍然存档)
                    onToggle = { ChatModeStore.setEphemeralMode(!rightOn) },
                )
            } else {
                Spacer(Modifier.width(24.dp))
            }
        }
        DividerLines(
            visible = !expanded,
            title = title,
            description = description,
            pixelFont = pixelFont,
        )
    }
}

@Composable
private fun LargeTrashRect(dir: Int, expanded: Boolean, lifting: Boolean, onClick: () -> Unit) {
    val shiftX by animateFloatAsState(if (expanded) -74f * dir else 0f, tween(700, easing = ExpandEase), label = "x")
    val scale by animateFloatAsState(if (expanded) 0.68f else 1f, tween(700, easing = ExpandEase), label = "s")
    val liftY by animateFloatAsState(
        if (lifting && !expanded) -8f else 0f, tween(700, easing = ExpandEase), label = "y",
    )
    // 武装信号：展开(装填)后边框与图标过渡为红色——明示"下一次点击即删光"
    val armedColor by animateColorAsState(
        if (expanded) Color(0xFFE5484D) else Ink, tween(350, easing = ExpandEase), label = "armed",
    )
    val radius = if (expanded) 14.dp else 18.dp
    val shape = RoundedCornerShape(radius)
    Box(
        Modifier
            .zIndex(2f)
            .size(95.dp)
            .graphicsLayer {
                translationX = shiftX.dp.toPx()
                translationY = liftY.dp.toPx()
                scaleX = scale
                scaleY = scale
            }
            .shadow(if (lifting && !expanded) 16.dp else 6.dp, shape)
            .background(PureWhite, shape)
            .border(2.dp, armedColor, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        TrashIcon(Modifier.size(38.dp, 42.dp), color = armedColor)
    }
}

@Composable
private fun TrashIcon(modifier: Modifier = Modifier, color: Color = Ink) {
    Canvas(modifier) {
        val sx = size.width / 36f
        val sy = size.height / 40f
        val unit = min(sx, sy)
        fun pt(x: Float, y: Float) = Offset(x * sx, y * sy)
        val cap = StrokeCap.Round
        val w12 = 1.2f * unit
        val w11 = 1.1f * unit

        drawLine(color, pt(7f, 12f), pt(29f, 12f), w12, cap)
        drawLine(color, pt(14.5f, 12f), pt(14.5f, 9f), w12, cap)
        drawLine(color, pt(21.5f, 12f), pt(21.5f, 9f), w12, cap)
        drawLine(color, pt(14.5f, 9f), pt(21.5f, 9f), w12, cap)
        drawLine(color, pt(16f, 9.5f), pt(16f, 7.5f), w11, cap)
        drawLine(color, pt(20f, 9.5f), pt(20f, 7.5f), w11, cap)
        drawLine(color, pt(16f, 7.5f), pt(20f, 7.5f), w11, cap)

        val body = Path().apply {
            moveTo(pt(10f, 12f).x, pt(10f, 12f).y)
            lineTo(pt(11.2f, 32f).x, pt(11.2f, 32f).y)
            lineTo(pt(24.8f, 32f).x, pt(24.8f, 32f).y)
            lineTo(pt(26f, 12f).x, pt(26f, 12f).y)
            close()
        }
        drawPath(body, color, style = Stroke(w12, cap = cap, join = StrokeJoin.Round))

        drawLine(color, pt(14.5f, 15f), pt(14.8f, 29f), w11, cap)
        drawLine(color, pt(18f, 15f), pt(18f, 29f), w11, cap)
        drawLine(color, pt(21.5f, 15f), pt(21.2f, 29f), w11, cap)
    }
}

// ==============================================================================
// "ARE YOU REAL?" 删除确认窗：蓝方框"圆字幕" = 确认删光，红方框"叉字幕" = 反悔
// ==============================================================================

@Composable
private fun AreYouRealDialog(
    pixelFont: FontFamily,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    Box(
        Modifier
            .width(240.dp)
            .shadow(10.dp, RoundedCornerShape(16.dp))
            .background(PureWhite, RoundedCornerShape(16.dp))
            .border(2.dp, Ink, RoundedCornerShape(16.dp))
            .padding(vertical = 22.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BasicText(
                // 【已本地化】原硬编码 "ARE YOU REAL?"
                text = uiText(UiText.FunctionMgmtAreYouReal),
                style = TextStyle(
                    fontFamily = pixelFont,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Ink,
                ),
            )
            Spacer(Modifier.height(20.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(30.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 圆字幕(蓝方框) = 确认删光；叉字幕(红方框) = 反悔。
                // 【项目约定】圆字幕恒定显示 "O"、叉字幕恒定显示 "X"——
                // 语言无关的通用确认/取消图形符号（七语含义一致），不入词典。
                CaptionBox(
                    text = "O",
                    color = AccentBlue,
                    pixelFont = pixelFont,
                    pressLabel = "okScale",
                    onClick = onConfirm,
                )
                CaptionBox(
                    text = "X",
                    color = CrossRed,
                    pixelFont = pixelFont,
                    pressLabel = "noScale",
                    onClick = onCancel,
                )
            }
        }
    }
}

/** 确认弹窗下方的小方框字幕按钮：方框颜色 = 原选项的突出色，原封不动——
 *  圆字幕(天蓝 #1677FF) = 确认删光；叉字幕(亮红 #FF3B30) = 反悔。
 *  按压回弹动画沿用原圆圈/叉的规格(0.82 缩放 + 投影切换) */
@Composable
private fun CaptionBox(
    text: String,
    color: Color,
    pixelFont: FontFamily,
    pressLabel: String,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.82f else 1f, tween(120), label = pressLabel)
    val shape = RoundedCornerShape(8.dp)
    Box(
        Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(if (pressed) 2.dp else 5.dp, shape)
            .background(color, shape)
            .border(2.dp, Ink, shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = text,
            style = TextStyle(
                fontFamily = pixelFont,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = PureWhite,
            ),
        )
    }
}

@Composable
private fun DividerLines(
    visible: Boolean,
    title: String,
    description: String,
    pixelFont: FontFamily,
) {
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(500, easing = ExpandEase), label = "a")
    val scale by animateFloatAsState(if (visible) 1f else 0.9f, tween(500, easing = ExpandEase), label = "s")
    Column(
        Modifier
            .padding(vertical = 4.dp)
            .graphicsLayer {
                this.alpha = alpha
                scaleX = scale
                scaleY = scale
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        BasicText(
            text = title,
            style = TextStyle(
                fontFamily = pixelFont,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Ink,
            ),
        )
        Box(Modifier.width(150.dp).height(2.dp).background(Ink, RoundedCornerShape(2.dp)))
        BasicText(
            text = description,
            style = TextStyle(
                fontFamily = pixelFont,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Medium,
                color = LineGray,
            ),
        )
        Box(Modifier.width(60.dp).height(2.dp).background(Ink, RoundedCornerShape(2.dp)))
    }
}

@Composable
private fun SideSwitch(
    active: Boolean,
    dimmed: Boolean,
    onToggle: () -> Unit,
    activeTrackColor: Color = SwitchPink,
    activeDotColor: Color = PureWhite,
    inactiveTrackColor: Color = PureWhite,
    inactiveDotColor: Color = DotGreen,
) {
    val density = LocalDensity.current
    val dotY = remember { Animatable(0f) }
    val shine = remember { Animatable(-1f) }
    var locked by remember { mutableStateOf(false) }
    var initialized by remember { mutableStateOf(false) }

    LaunchedEffect(active) {
        if (!initialized) { initialized = true; return@LaunchedEffect }
        val px58 = with(density) { 58.dp.toPx() }
        shine.snapTo(-1f)
        if (active) {
            dotY.animateTo(
                px58,
                keyframes {
                    durationMillis = 600
                    0f at 0
                    with(density) { 62.dp.toPx() } at 330
                    with(density) { 55.dp.toPx() } at 420
                    with(density) { 59.dp.toPx() } at 504
                    with(density) { 58.dp.toPx() } at 600
                },
            )
        } else if (dotY.value > 1f) {
            dotY.animateTo(
                0f,
                keyframes {
                    durationMillis = 600
                    with(density) { 58.dp.toPx() } at 0
                    0f at 330
                    with(density) { 5.dp.toPx() } at 420
                    with(density) { 1.dp.toPx() } at 504
                    0f at 600
                },
            )
        } else {
            dotY.snapTo(0f)
        }
        shine.snapTo(0f)
        shine.animateTo(1f, tween(500))
        shine.snapTo(-1f)
        locked = false
    }

    val dimAlpha by animateFloatAsState(if (dimmed) 0f else 1f, tween(600), label = "dimA")
    val dimScale by animateFloatAsState(if (dimmed) 0.92f else 1f, tween(600), label = "dimS")

    Box(
        Modifier
            .size(24.dp, 90.dp)
            .graphicsLayer {
                alpha = dimAlpha
                scaleX = dimScale
                scaleY = dimScale
            }
            .background(if (active) activeTrackColor else inactiveTrackColor, RoundedCornerShape(8.dp))
            .border(1.dp, Ink, RoundedCornerShape(8.dp))
            .clickable(enabled = !dimmed && !locked) {
                locked = true
                onToggle()
            },
        contentAlignment = Alignment.TopCenter,
    ) {
        val shineT = shine.value
        Box(
            Modifier
                .padding(top = 4.dp)
                .size(12.dp, 20.dp)
                .graphicsLayer { translationY = dotY.value }
                .background(if (active) activeDotColor else inactiveDotColor, RoundedCornerShape(10.dp))
                .border(1.5.dp, Ink, RoundedCornerShape(10.dp)),
        ) {
            if (shineT in 0f..1f) {
                val leftDp = -16f + shineT * 42f
                val alphaT = if (shineT < 0.35f) shineT / 0.35f else 1f - (shineT - 0.35f) / 0.65f
                Canvas(Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp))) {
                    val barW = 8.dp.toPx()
                    val barH = 34.dp.toPx()
                    translate(leftDp.dp.toPx() + barW / 2, size.height / 2) {
                        rotate(25f) {
                            drawRoundRect(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.White.copy(alpha = 0.95f * alphaT.coerceAtLeast(0f)),
                                        Color.Transparent,
                                    ),
                                    startX = -barW / 2,
                                    endX = barW / 2,
                                ),
                                topLeft = Offset(-barW / 2, -barH / 2),
                                size = Size(barW, barH),
                                cornerRadius = CornerRadius(8.dp.toPx()),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun InputSection(inputs: MutableList<TextFieldValue>, pixelFont: FontFamily) {
    Column(
        Modifier.offset(y = 700.dp).fillMaxWidth().padding(horizontal = 24.dp),
    ) {
        LinesBlock(inputs, 0 until 8, pixelFont)
        Spacer(Modifier.height(32.dp))
        LinesBlock(inputs, 8 until 16, pixelFont)
    }
}

@Composable
private fun LinesBlock(
    inputs: MutableList<TextFieldValue>,
    range: IntRange,
    pixelFont: FontFamily,
) {
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Max),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier
                .width(5.dp)
                .fillMaxHeight()
                .background(Ink, RoundedCornerShape(3.dp)),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for (i in range) {
                UnderlinedInput(
                    value = inputs[i],
                    onValue = {
                        inputs[i] = it
                        // 前八行 Tools：前 3 条是 MCP 爬取源地址，回写 PlazaStore，下一轮爬虫生效
                        if (i in 0..2) PlazaStore.updateCrawlSource(i, it.text)
                        // 后八行 Skills(8-15)：回写 SkillCrawlStore 爬取源地址，下次爬取真实生效
                        if (i in 8..15) SkillCrawlStore.updateSkillSource(i - 8, it.text)
                    },
                    pixelFont = pixelFont,
                )
            }
        }
    }
}

@Composable
private fun UnderlinedInput(
    value: TextFieldValue,
    onValue: (TextFieldValue) -> Unit,
    pixelFont: FontFamily,
) {
    var focused by remember { mutableStateOf(false) }
    BasicTextField(
        value = value,
        onValueChange = onValue,
        singleLine = true,
        textStyle = TextStyle(
            fontFamily = pixelFont,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = Ink,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(22.dp)
            .onFocusChanged { focused = it.isFocused },
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxSize()
                    .drawUnderline(focused)
                    .padding(start = 2.dp, end = 2.dp, bottom = 3.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                inner()
            }
        },
    )
}

private fun Modifier.drawUnderline(focused: Boolean): Modifier = drawBehind {
    val sw = (if (focused) 2f else 1.5f).dp.toPx()
    val color = if (focused) Ink else LineGray
    drawLine(
        color,
        Offset(0f, size.height - sw / 2),
        Offset(size.width, size.height - sw / 2),
        sw,
    )
}

@Preview(showBackground = true, backgroundColor = 0xFFE0E0E0, widthDp = 360, heightDp = 780)
@Composable
private fun FunctionManagementPreview() {
    SubCanvasFunctionManagement(
        visible = true,
        onClose = {},
        pixelFont = FontFamily.Default,
    )
}
