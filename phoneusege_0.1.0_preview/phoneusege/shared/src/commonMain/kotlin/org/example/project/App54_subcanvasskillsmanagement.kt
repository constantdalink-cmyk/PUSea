// ============ App57 Skill Management 【1/3】基础封装 · 数据模型 · 图片组件 · 卡片行组件 ============
package org.example.project

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ==============================================================================
// 1. Text 基础封装 (兼容纯 Foundation 模块)
// ==============================================================================

@Composable
internal fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    lineHeight: TextUnit = TextUnit.Unspecified,
    textAlign: TextAlign = TextAlign.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(
            color = color,
            fontSize = fontSize,
            fontWeight = fontWeight,
            fontFamily = fontFamily,
            letterSpacing = letterSpacing,
            lineHeight = lineHeight,
            textAlign = textAlign
        ),
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines
    )
}

// ==============================================================================
// 2. 数据模型与调色板定义 (与 HTML 严格对应)
// ==============================================================================

internal val CardColorPalette = listOf(
    Color(0xFFFF6B6B),
    Color(0xFF4D96FF),
    Color(0xFF6BCB77),
    Color(0xFFFFD93D),
    Color(0xFF9B59B6),
    Color(0xFFFF8E53),
    Color(0xFF00CEC9),
    Color(0xFFFD79A8),
    Color(0xFF0984E3)
)

internal val RibbonColorPalette = listOf(
    Color(0xFFFF7675),
    Color(0xFFFDCB6E),
    Color(0xFF55EFC4),
    Color(0xFF74B9FF),
    Color(0xFFA29BFE),
    Color(0xFFFD79A8)
)

data class SkillCardItemData(
    val id: String,
    val name: String,
    val detail: String,
    val color: Color,
    val imageUrl: String = "",
    val iconKey: String = "",
    // 用户可编辑的 SKILL.md 正文：注入 AI 时优先于网络抓取；抓取成功也会回写到这里
    val customMd: String = ""
)

data class RibbonColumnData(
    val id: String,
    val color: Color,
    val widthDp: Int = 78,
    val name: String = "+",
    val stage: Int = 0, // 0: 空白, 1: 待选列表, 2: 已确认选中高亮
    val selectedItemIds: Set<String> = emptySet(),
    val isEditing: Boolean = false,
    val isInitial: Boolean = false // 初始带子标记 (锁定保护：即便未起名也绝不会被清除)
)

// ==============================================================================
// 3. 资源仓储与图片爬虫引擎归属说明
//    DownloadedResourceStore  定义于 App56_aiskills.kt   (含 addDownloadedCard 等真实下载接口)
//    SkillImageCrawlEngine     定义于 App59_skillsloader.kt (含真实网络图片断点重爬状态机)
// ==============================================================================

// ==============================================================================
// 4. 资源对应专属图片/图标组件 (SkillResourceImageView)
// ==============================================================================

@Composable
fun SkillResourceImageView(
    card: SkillCardItemData,
    modifier: Modifier = Modifier,
    iconSize: Dp = 20.dp
) {
    // 空白自建卡不自动去 GitHub 兜底；其余卡片 imageUrl 为空时按名称/详情解析真实封面
    val resolvedImageUrl = remember(card.id, card.imageUrl, card.name, card.detail) {
        when {
            card.imageUrl.isNotBlank() -> card.imageUrl
            card.id.startsWith("res_blank_") -> ""
            else -> DownloadedResourceStore.resolveImageUrlForCard(card.name, card.detail)
        }
    }
    var imageStatus by remember(card.id, resolvedImageUrl) { mutableStateOf<ImageCrawlStatus>(ImageCrawlStatus.Idle) }

    LaunchedEffect(resolvedImageUrl) {
        SkillImageCrawlEngine.fetchOrRecrawlImage(resolvedImageUrl) { newStatus ->
            imageStatus = newStatus
        }
    }

    // 预留框不再跟随卡片"那一抹颜色"出现对应色：移除原先 card.color 15% 透明度的跟随着色底，
    // 框体保持调用方给的底色（列表灰白 / 右画布白），空着就是纯净的预留框
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        when (val currentStatus = imageStatus) {
            is ImageCrawlStatus.Fetching, is ImageCrawlStatus.Retrying -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(if (iconSize > 30.dp) 20.dp else 14.dp),
                    strokeWidth = 2.dp,
                    color = Color(0xFF181717)
                )
            }
            is ImageCrawlStatus.Success -> {
                androidx.compose.foundation.Image(
                    bitmap = currentStatus.bitmap,
                    contentDescription = card.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            is ImageCrawlStatus.Failed, ImageCrawlStatus.Idle -> {
                // 预留框保持空白：不再绘制任何默认示例方块/图标，长条预留框就空着。
                // 真实封面仅在爬虫 Success 时回填，失败/空闲时留白。
            }
        }
    }
}

// ==============================================================================
// 6. 【前端 UI 组件层】技能卡片单个条目组件 (SkillCardRowItem)
// ==============================================================================

/** 长按删除：进度条推进时长（此段进度条匀速前进） */
private const val DELETE_FILL_MS = 5000f

/** 长按删除：满格后的静止延长时长（此段进度条完全隐藏不显示） */
private const val DELETE_HOLD_MS = 300L

/** 长按删除：进度条开始显示前的按压判定阈值（短于它的单击/连点/误触不会闪出红色销毁条） */
private const val DELETE_SHOW_THRESHOLD_MS = 300L

@Composable
fun SkillCardRowItem(
    card: SkillCardItemData,
    isActive: Boolean,
    pixelFont: FontFamily,
    screenWidth: androidx.compose.ui.unit.Dp,
    detailOffsetAnim: Animatable<Float, AnimationVector1D>,
    onCardClick: () -> Unit,
    onToggleActive: () -> Unit = {},
    onColorCycle: (Color) -> Unit,
    onDragLeftOpenDetail: (card: SkillCardItemData) -> Unit,
    onRequestDelete: (SkillCardItemData) -> Unit = {}
) {
    val coroutineScope = rememberCoroutineScope()

    // 长按 5 秒删除状态
    var isPressed by remember { mutableStateOf(false) }
    var deleteProgress by remember { mutableFloatStateOf(0f) }
    var isDeleting by remember { mutableStateOf(false) }
    var deleteJob by remember { mutableStateOf<Job?>(null) }
    // 进度条已充满：此后松手不再回滚，保证 300ms 静止延长期间删除不被取消
    var deleteArmed by remember { mutableStateOf(false) }
    // 手动双击判定：不用 onDoubleTap，避免 Compose 等满双击超时导致单击选中延迟约 300ms
    var lastTapTime by remember { mutableStateOf(0L) }

    val elevation by animateDpAsState(
        targetValue = if (isActive) 6.dp else 1.dp,
        animationSpec = tween(300, easing = CubicBezierEasing(0.25f, 1f, 0.5f, 1f))
    )
    val translateY by animateDpAsState(
        targetValue = if (isActive) (-2).dp else 0.dp,
        animationSpec = tween(300, easing = CubicBezierEasing(0.25f, 1f, 0.5f, 1f))
    )
    val tailWidth by animateDpAsState(
        targetValue = if (isActive) 38.dp else 0.dp,
        animationSpec = tween(300, easing = CubicBezierEasing(0.25f, 1f, 0.5f, 1f))
    )
    // 末端"那一抹颜色"：每个长条框一律从天蓝 (0xFF4D96FF) 开始，不跟随卡片自带的历史颜色，
    // 修复初始长方框不是天蓝的 BUG；点击末端可循环变色 (见末端 clickable)
    var tailDisplayColor by remember(card.id) { mutableStateOf(CardColorPalette[1]) }
    // 首次组合把天蓝回写资源池：保证之后无论从哪滑出的右画布颜色都与末端一致
    LaunchedEffect(card.id) {
        if (!card.id.startsWith("search_pool_") && card.color != tailDisplayColor) {
            onColorCycle(tailDisplayColor)
        }
    }
    val tailColor by animateColorAsState(
        targetValue = tailDisplayColor,
        animationSpec = tween(250, easing = FastOutSlowInEasing)
    )

    // 淡出动画：删除达成后整行从屏幕淡出
    val itemAlpha by animateFloatAsState(
        targetValue = if (isDeleting) 0f else 1f,
        animationSpec = tween(380, easing = LinearOutSlowInEasing)
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .offset(y = translateY)
            .alpha(itemAlpha)
            .shadow(elevation, shape = RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .border(
                width = 1.dp,
                color = if (isActive) Color(0x14000000) else Color(0xFFE6E6E6),
                shape = RoundedCornerShape(16.dp)
            )
            .pointerInput(card.id) {
                detectTapGestures(
                    onPress = {
                        // 已进入删除流程（满格锁定中 / 淡出中）则忽略后续按压，
                        // 否则 deleteJob?.cancel() 会把已经排好的删除动作取消掉
                        if (isDeleting || deleteArmed) return@detectTapGestures
                        isPressed = true
                        deleteProgress = 0f
                        deleteArmed = false
                        deleteJob?.cancel()
                        deleteJob = coroutineScope.launch {
                            val startTime = System.currentTimeMillis()
                            // 第一段 5000ms：前 300ms 为按压判定阈值——单击/连点/误触在阈值内松手时
                            // deleteProgress 一帧都不写，红色销毁条不会出现；超过阈值后进度条从 0 匀速
                            // 推进到满格，长按总时长仍精确为 5000ms
                            while (isPressed) {
                                val elapsed = System.currentTimeMillis() - startTime
                                if (elapsed >= DELETE_SHOW_THRESHOLD_MS) {
                                    deleteProgress = ((elapsed - DELETE_SHOW_THRESHOLD_MS) / (DELETE_FILL_MS - DELETE_SHOW_THRESHOLD_MS)).coerceIn(0f, 1f)
                                    if (deleteProgress >= 1f) break
                                }
                                delay(16)
                            }
                            if (deleteProgress >= 1f) {
                                // 满格即锁定：此后松手不再回滚
                                deleteArmed = true
                                // 满格立即归零（双保险）：渲染条件 deleteProgress > 0.01f 与 !deleteArmed
                                // 同时失效，确保 300ms 静止期内红色销毁条彻底不显示，一帧都不会残留
                                deleteProgress = 0f
                                // 第二段 300ms 延长：屏幕上无任何进度条，静止等待后进入淡出删除
                                delay(DELETE_HOLD_MS)
                                // 第三段：进入删除态触发整行淡出 → 动画结束后真正从根删除
                                isDeleting = true
                                isPressed = false
                                delay(400)
                                onRequestDelete(card)
                                // 删除完成后全量复位：即使卡片因故未被移除，本行也回到干净状态，
                                // 下一次长按会重新走完整的 5000ms 匀速 → 300ms 静止计时，
                                // 修复"300ms 删除计时只会计一次"的 BUG
                                isDeleting = false
                                deleteArmed = false
                                deleteProgress = 0f
                                deleteJob = null
                            }
                        }
                        tryAwaitRelease()
                        isPressed = false
                        // 只有"未充满且未进入删除态"才回滚进度；
                        // 已充满(deleteArmed)或已淡出(isDeleting)时保留，让流程走完
                        if (!isDeleting && !deleteArmed) {
                            deleteJob?.cancel()
                            deleteJob = null
                            deleteProgress = 0f
                        }
                    },
                    onTap = {
                        // 删除已锁定/淡出中：不再响应选中与详情，避免操作一张即将消失的卡片
                        if (isDeleting || deleteArmed) return@detectTapGestures
                        val now = System.currentTimeMillis()
                        val isDoubleTap = (now - lastTapTime) < 350L
                        lastTapTime = now
                        if (isDoubleTap) {
                            // 双击：打开详情弹窗（名称/详情/图片，无下载按钮）
                            lastTapTime = 0L
                            onCardClick()
                        } else {
                            // 单击：立即切换选中态（彩色末端展开），零延迟
                            onToggleActive()
                        }
                    }
                )
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(end = 46.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧方形图标框 (展示爬虫自动抓取的对应真实资源图片/徽标)
            Box(
                modifier = Modifier
                    .padding(start = 10.dp)
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFF4F4F4))
                    .border(1.dp, Color(0xFFDCDCDC), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                SkillResourceImageView(
                    card = card,
                    modifier = Modifier.fillMaxSize(),
                    iconSize = 22.dp
                )
            }

            // 文字介绍
            Column(
                modifier = Modifier
                    .padding(start = 12.dp, end = 8.dp)
                    .weight(1f)
            ) {
                Text(
                    text = card.name,
                    fontFamily = pixelFont,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.W600,
                    color = Color(0xFF111111),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = card.detail,
                    fontFamily = pixelFont,
                    fontSize = 12.sp,
                    color = Color(0xFF888888),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        // 动态彩色末端 (宽度伸缩动画 0.3s 与背景颜色渐变动画 0.25s，支持点击循环变色与左拖拉出详情)
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(tailWidth)
                .fillMaxHeight()
                .clip(RoundedCornerShape(topEnd = 15.dp, bottomEnd = 15.dp))
                .background(tailColor)
                .pointerInput(card.id, card.color, card.name) {
                    var totalDragX = 0f
                    detectHorizontalDragGestures(
                        onDragStart = {
                            totalDragX = 0f
                        },
                        onDragEnd = {
                            coroutineScope.launch {
                                if (totalDragX < -40f) {
                                    onDragLeftOpenDetail(card)
                                    detailOffsetAnim.animateTo(
                                        0f,
                                        animationSpec = tween(280, easing = CubicBezierEasing(0.25f, 0.46f, 0.45f, 0.94f))
                                    )
                                } else {
                                    detailOffsetAnim.animateTo(1f, animationSpec = tween(200))
                                }
                            }
                        },
                        onDragCancel = {
                            coroutineScope.launch {
                                detailOffsetAnim.animateTo(1f, animationSpec = tween(200))
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            totalDragX += dragAmount
                            val widthPx = screenWidth.toPx()
                            if (widthPx > 0f) {
                                val newFraction = (1f + (totalDragX / widthPx)).coerceIn(0f, 1f)
                                coroutineScope.launch {
                                    detailOffsetAnim.snapTo(newFraction)
                                }
                            }
                        }
                    )
                }
                .clickable {
                    // 以当前显示色定位索引循环取下一色；不在调色板时按天蓝起点顺延
                    val curIdx = CardColorPalette.indexOf(tailDisplayColor).let { if (it == -1) 1 else it }
                    val nextColor = CardColorPalette[(curIdx + 1) % CardColorPalette.size]
                    tailDisplayColor = nextColor
                    onColorCycle(nextColor)
                }
        )
    }
}
