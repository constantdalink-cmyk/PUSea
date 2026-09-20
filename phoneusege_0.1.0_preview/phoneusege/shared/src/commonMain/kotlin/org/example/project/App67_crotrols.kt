// ============ App57 Skill Management 【3/3】带子算法 · 收纳凹槽控件 · 顶栏 · 子画布入口 ============
package org.example.project

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ==============================================================================
// 5. 带子均分与众数宽度算法
// ==============================================================================

/**
 * 将下滑画布总宽度尽量平均分给 count 块。
 * 若除不尽有余数，将余量平分到已有的块中（前面的块各 +1dp），如果多一点不能再平均加就顺位加上。
 */
fun calculateEquallyDistributedWidths(totalWidth: Int, count: Int): List<Int> {
    if (count <= 0) return emptyList()
    val base = totalWidth / count
    val remainder = totalWidth % count
    return List(count) { index ->
        base + if (index < remainder) 1 else 0
    }
}

/**
 * 获取当前已有带子宽度列表中相同数字出现最多的那个数字（众数 Mode）。
 * 往左/往右新建'带子'时，新带子的宽度就是这个数字。
 */
fun getMostFrequentWidth(widths: List<Int>, fallback: Int = 78): Int {
    if (widths.isEmpty()) return fallback
    val freqMap = widths.groupingBy { it }.eachCount()
    return freqMap.maxByOrNull { it.value }?.key ?: widths.first()
}

/**
 * 边界修剪算法：
 * 1. 最开始的初始基础带子 (isInitial == true) 始终锁定保护，即便未取名也绝不会被清除。
 * 2. 用户向最左/最右动态新建的带子，若未取名且位于两端最外侧，下次打开下滑画布时会被修剪，
 *    视口最左/最右两端精确限制到【初始带子】或【最后一个有名字的带子】。
 */
fun pruneOuterUnnamedRibbons(columns: List<RibbonColumnData>): List<RibbonColumnData> {
    val firstKeepIndex = columns.indexOfFirst { it.isInitial || (it.name != "+" && it.name.isNotBlank()) }
    val lastKeepIndex = columns.indexOfLast { it.isInitial || (it.name != "+" && it.name.isNotBlank()) }
    if (firstKeepIndex == -1 || lastKeepIndex == -1) {
        return columns
    }
    return columns.subList(firstKeepIndex, lastKeepIndex + 1)
}

// ==============================================================================
// 8. 【前端 UI 组件层】右下角专属圆角收纳凹槽及弹出控制器 (BottomNotchWithControlsView)
// ==============================================================================

@Composable
fun BottomNotchWithControlsView(
    isSettingsOpen: Boolean,
    searchQuery: String,
    pixelFont: FontFamily,
    modifier: Modifier = Modifier,
    onToggleSettings: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onPerformSearch: (String) -> Unit = {},
    onCloseClick: () -> Unit,
    onAddEmptyCard: () -> Unit = {}
) {
    val settingsRotation by animateFloatAsState(
        targetValue = if (isSettingsOpen) 180f else 0f,
        animationSpec = tween(380, easing = CubicBezierEasing(0.25f, 1f, 0.5f, 1f))
    )
    // 布局级动画：搜索框宽度向左生长 (收起时完全藏进凹槽后方)，退出按钮 bottom padding 抬升
    val exitBtnBottomPadding by animateDpAsState(
        targetValue = if (isSettingsOpen) 74.dp else 12.dp,
        animationSpec = tween(380, easing = CubicBezierEasing(0.25f, 1f, 0.5f, 1f))
    )
    // 新建空白长条框按钮（笔+横线图标）：位于退出按钮更上方
    val penBtnBottomPadding by animateDpAsState(
        targetValue = if (isSettingsOpen) 126.dp else 12.dp,
        animationSpec = tween(420, easing = CubicBezierEasing(0.25f, 1f, 0.5f, 1f))
    )
    // 收起宽度 0 (完全收纳不可见)；展开 230dp，框体右端恒定停在齿轮凹槽左缘外 12dp
    val searchBarWidth by animateDpAsState(
        targetValue = if (isSettingsOpen) 230.dp else 0.dp,
        animationSpec = tween(380, easing = CubicBezierEasing(0.25f, 1f, 0.5f, 1f))
    )



    Box(
        modifier = modifier,
        contentAlignment = Alignment.BottomEnd
    ) {
        // 委托 App60_search.kt 中的 ManagementNotchSearchBar 渲染向左滑出的搜索框
        ManagementNotchSearchBar(
            searchBarWidth = searchBarWidth,
            searchQuery = searchQuery,
            pixelFont = pixelFont,
            onSearchQueryChange = onSearchQueryChange,
            onPerformSearch = onPerformSearch,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 78.dp, bottom = 12.dp)
        )

        // 向上实体滑出的退出按钮 (bottom padding 动画：命中区域与绘制位置严格一致)
        // 新建空白长条框按钮：一支笔 + 下面一条横线
        if (penBtnBottomPadding > 13.dp) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 12.dp, bottom = penBtnBottomPadding)
                    .size(42.dp)
                    .shadow(4.dp, RoundedCornerShape(12.dp))
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .border(2.dp, Color(0xFF1A1A1A), RoundedCornerShape(12.dp))
                    .clickable { onAddEmptyCard() },
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(20.dp)) {
                    val strokeW = 2.dp.toPx()
                    val pColor = Color(0xFF1A1A1A)
                    val w = size.width
                    val h = size.height

                    // 笔杆（斜向）
                    drawLine(
                        pColor,
                        Offset(w * 0.30f, h * 0.62f),
                        Offset(w * 0.74f, h * 0.16f),
                        strokeWidth = strokeW,
                        cap = StrokeCap.Round
                    )
                    // 笔杆第二条边，形成笔身厚度
                    drawLine(
                        pColor,
                        Offset(w * 0.44f, h * 0.74f),
                        Offset(w * 0.88f, h * 0.30f),
                        strokeWidth = strokeW,
                        cap = StrokeCap.Round
                    )
                    // 笔头连接线
                    drawLine(
                        pColor,
                        Offset(w * 0.74f, h * 0.16f),
                        Offset(w * 0.88f, h * 0.30f),
                        strokeWidth = strokeW,
                        cap = StrokeCap.Round
                    )
                    // 笔尖（三角收口）
                    drawLine(
                        pColor,
                        Offset(w * 0.30f, h * 0.62f),
                        Offset(w * 0.24f, h * 0.80f),
                        strokeWidth = strokeW,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        pColor,
                        Offset(w * 0.24f, h * 0.80f),
                        Offset(w * 0.44f, h * 0.74f),
                        strokeWidth = strokeW,
                        cap = StrokeCap.Round
                    )
                    // 下面的一条横线
                    drawLine(
                        pColor,
                        Offset(w * 0.12f, h * 0.94f),
                        Offset(w * 0.88f, h * 0.94f),
                        strokeWidth = strokeW,
                        cap = StrokeCap.Round
                    )
                }
            }
        }

        // 向上实体滑出的退出按钮
        if (exitBtnBottomPadding > 13.dp) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 12.dp, bottom = exitBtnBottomPadding)
                    .size(42.dp)
                    .shadow(4.dp, RoundedCornerShape(12.dp))
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .border(2.dp, Color(0xFF1A1A1A), RoundedCornerShape(12.dp))
                    .clickable { onCloseClick() },
                contentAlignment = Alignment.Center
            ) {
                // 门形退出图标
                Canvas(modifier = Modifier.size(20.dp)) {
                    val strokeW = 2.dp.toPx()
                    val pColor = Color(0xFF1A1A1A)

                    // 门框
                    drawPath(
                        path = Path().apply {
                            moveTo(size.width * 0.6f, size.height * 0.1f)
                            lineTo(size.width * 0.25f, size.height * 0.1f)
                            lineTo(size.width * 0.25f, size.height * 0.9f)
                            lineTo(size.width * 0.6f, size.height * 0.9f)
                        },
                        color = pColor,
                        style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                    // 箭头
                    drawLine(
                        pColor,
                        Offset(size.width * 0.4f, size.height * 0.5f),
                        Offset(size.width * 0.9f, size.height * 0.5f),
                        strokeWidth = strokeW,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        pColor,
                        Offset(size.width * 0.72f, size.height * 0.32f),
                        Offset(size.width * 0.9f, size.height * 0.5f),
                        strokeWidth = strokeW,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        pColor,
                        Offset(size.width * 0.72f, size.height * 0.68f),
                        Offset(size.width * 0.9f, size.height * 0.5f),
                        strokeWidth = strokeW,
                        cap = StrokeCap.Round
                    )
                }
            }
        }

        // 右下角专属圆角收纳凹槽 (始终固定吸附在屏幕右下角，不随搜索框位移)
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(66.dp)
                .drawBehind {
                    val strokeW = 2.5f.dp.toPx()
                    val r = 28.dp.toPx()

                    val path = Path().apply {
                        moveTo(0f, size.height)
                        lineTo(0f, r)
                        quadraticBezierTo(0f, 0f, r, 0f)
                        lineTo(size.width, 0f)
                        lineTo(size.width, size.height)
                        close()
                    }
                    drawPath(path, color = Color.White)

                    val borderPath = Path().apply {
                        moveTo(0f, size.height)
                        lineTo(0f, r)
                        quadraticBezierTo(0f, 0f, r, 0f)
                        lineTo(size.width, 0f)
                    }
                    drawPath(
                        borderPath,
                        color = Color(0xFF1A1A1A),
                        style = Stroke(width = strokeW, cap = StrokeCap.Round)
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            val gearPath = remember {
                PathParser().parsePathString(
                    "M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z"
                ).toPath()
            }

            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onToggleSettings() },
                contentAlignment = Alignment.Center
            ) {
                Canvas(
                    modifier = Modifier
                        .size(24.dp)
                        .rotate(settingsRotation)
                ) {
                    val scaleFactor = size.width / 24f
                    val pColor = Color(0xFF1A1A1A)
                    val strokeW = 2f * scaleFactor

                    drawCircle(
                        color = pColor,
                        radius = 3f * scaleFactor,
                        center = Offset(12f * scaleFactor, 12f * scaleFactor),
                        style = Stroke(width = strokeW)
                    )

                    withTransform({
                        scale(scaleFactor, scaleFactor, Offset.Zero)
                    }) {
                        drawPath(
                            path = gearPath,
                            color = pColor,
                            style = Stroke(
                                width = 2f,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }
                }
            }
        }
    }
}

// ==============================================================================
// 9. 【前端 UI 组件层】顶部常驻标题栏 (SkillsManagementTopHeaderBar)
// ==============================================================================

@Composable
fun SkillsManagementTopHeaderBar(
    pixelFont: FontFamily
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(53.dp)
            .background(Color.White)
    ) {
        Text(
            text = "Skills Management",
            fontFamily = pixelFont,
            fontSize = 15.sp,
            fontWeight = FontWeight.W600,
            color = Color(0xFF111111),
            letterSpacing = 0.3.sp,
            textAlign = TextAlign.End,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp, end = 18.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .align(Alignment.BottomCenter)
                .background(Color(0xFF333333))
        )
    }
}

// ==============================================================================
// 10. 【子画布入口】Skill Management
// ==============================================================================

/**
 * 【子画布】Skill Management 技能管理画布
 */
@Composable
fun SubCanvasSkillManagement(
    visible: Boolean,
    onClose: () -> Unit,
    pixelFont: FontFamily = FontFamily.Default
) {
    SlideDownContainer(
        visible = visible,
        title = "Skills Management",
        onClose = onClose,
        pixelFont = pixelFont
    ) {
        SkillsManagementContent(onClose = onClose, pixelFont = pixelFont)
    }
}

/**
 * 兼容性函数别名 (复数形式 SkillsManagement)
 */
@Composable
fun SubCanvasSkillsManagement(
    visible: Boolean,
    onClose: () -> Unit,
    pixelFont: FontFamily = FontFamily.Default
) = SubCanvasSkillManagement(visible = visible, onClose = onClose, pixelFont = pixelFont)
