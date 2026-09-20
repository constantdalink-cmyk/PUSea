package org.example.project

// ==============================================================================
// 画布文件（拆分 1/3）：App50_1_plazadrop_overlay.kt —— 下滑天气圆角子画布主容器与数据调度
//
// 【隶属广场画布，不碰分发中心】；对应工程中的 App50 模块核心层。
//
// 【v47 · 核心视觉与图层定版记录】：
//  1. 【飘云图层层级定版（最高图层，仅小于右画布）】：
//     - 图层层级架构：全屏横穿飘云 Canvas 赋予顶层 zIndex(50f)，浮于下滑阶梯卡片 (zIndex(10f)) 及一切卡片内文字、图标、搜索框之上横向飘过；
//     - 严格仅低于右侧滑出的箱子工具箱画布 (zIndex(100f))，当右画布滑出时完整遮盖飘云；
//     - 从屏幕右侧外一路匀速飘至屏幕左侧外，纵向紧跟卡片展开进度。
//  2. 【BUG 2 · 搜索框严格内部边界锁定（已彻底修复）】：
//     - 几何内胆安全计算：卡片内胆宽度 innerCardBodyWidth = maxWidth - 2 * topBandSizeDp，
//       搜索框最大宽度 maxAllowedSearchWidth = innerCardBodyWidth - 28.dp；
//     - 防溢出防穿透：通过 coerceIn(120.dp, maxAllowedSearchWidth) 进行刚性边界锁定，
//       配合外容器 clip(RoundedCornerShape(6.dp)) 与内部 BasicTextField(singleLine = true, maxLines = 1)，
//       长文本平滑单行横滚，绝不刺破卡片两翼阶梯边缘。
//  3. 【BUG 3 · 箱子图标点击唤出右滑画布（已彻底修复）】：
//     - 手势通道分流：顶部 30% 隐形下拉手势感应带仅在卡片收起时激活（progress < 0.08f），
//       展开后自动让出全屏点击通道；
//     - 卡片交互策略：卡片主体 detectStealVerticalDrag 判定策略严格限定为向上推（acc < 0f），
//       彻底放行所有静态 Tap/Click 事件；
//     - 箱子图标响应级置顶：右上 1/5×1/5 方块区域赋予最高响应级 zIndex(30f)，点击 100% 毫秒级触发
//       PlazaCrateDrawerCoordinator.open()，顺畅滑出 App51 箱子工具箱画布。
// ==============================================================================

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlin.math.sin

// ==============================================================================
// 专属阶梯外形：顶部 1/5 贴边零缝（左右形成 1/5×1/5 左/右方块），下方主体卡片左右留缝 1/5
// ==============================================================================

internal class PlazaDropCardShape(
    private val topFraction: Float = 0.20f,
    private val bottomRadius: Dp = 22.dp
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val w = size.width
        val h = size.height
        val topH = h * topFraction // 1/5 画布高
        val margin = topH          // 左右留缝宽 = 1/5 画布高
        val r = with(density) { bottomRadius.toPx() }.coerceAtMost((w - 2 * margin) / 2f)

        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(w, 0f)
            lineTo(w, topH)
            lineTo(w - margin, topH)
            lineTo(w - margin, h - r)
            arcTo(
                rect = Rect(w - margin - 2 * r, h - 2 * r, w - margin, h),
                startAngleDegrees = 0f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false
            )
            lineTo(margin + r, h)
            arcTo(
                rect = Rect(margin, h - 2 * r, margin + 2 * r, h),
                startAngleDegrees = 90f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false
            )
            lineTo(margin, topH)
            lineTo(0f, topH)
            close()
        }
        return Outline.Generic(path)
    }
}

// ==============================================================================
// 天气数据模型与全套天气状态
// ==============================================================================

enum class PlazaWeatherIconMode {
    SUNNY,         // 晴天
    PARTLY_CLOUDY, // 多云
    OVERCAST,      // 阴天
    RAIN,          // 下雨
    SNOW,          // 下雪
    SLEET,         // 雨夹雪
    HAIL,          // 冰雹
    TYPHOON,       // 台风
    EARTHQUAKE     // 地震
}

object PlazaWeatherManager {
    var iconMode by mutableStateOf(PlazaWeatherIconMode.RAIN)
    var hasFetched = false

    fun autoDetect(requester: HttpRequester?) {
        if (hasFetched || requester == null) return
        hasFetched = true

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val geoJson = requester.request("GET", "https://ipapi.co/json/", emptyMap(), null)
                val lat = extractDouble(geoJson, "latitude") ?: 31.23
                val lon = extractDouble(geoJson, "longitude") ?: 121.47

                val weatherUrl = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current_weather=true"
                val weatherJson = requester.request("GET", weatherUrl, emptyMap(), null)
                val code = extractInt(weatherJson, "weathercode") ?: 61

                val mode = when (code) {
                    0 -> PlazaWeatherIconMode.SUNNY
                    1, 2 -> PlazaWeatherIconMode.PARTLY_CLOUDY
                    51, 53, 55, 61, 63, 65, 80, 81, 82 -> PlazaWeatherIconMode.RAIN
                    71, 73, 75, 77, 85, 86 -> PlazaWeatherIconMode.SNOW
                    66, 67 -> PlazaWeatherIconMode.SLEET
                    96, 99 -> PlazaWeatherIconMode.HAIL
                    else -> PlazaWeatherIconMode.OVERCAST
                }

                withContext(Dispatchers.Main) {
                    iconMode = mode
                }
            } catch (_: Throwable) {
                // 离线默认保持当前模式
            }
        }
    }

    private fun extractDouble(json: String, key: String): Double? {
        val regex = Regex("\"$key\"\\s*:\\s*([0-9.-]+)")
        return regex.find(json)?.groupValues?.get(1)?.toDoubleOrNull()
    }

    private fun extractInt(json: String, key: String): Int? {
        val regex = Regex("\"$key\"\\s*:\\s*([0-9]+)")
        return regex.find(json)?.groupValues?.get(1)?.toIntOrNull()
    }
}

// ==============================================================================
// 主组件：追加式圆角子画布叠层（顶部边缘下滑唤出，占屏 1/3，纯位移）
// ==============================================================================

@Composable
internal fun PlazaDropOverlay(
    pixelFont: FontFamily,
    dropHeightFraction: Float = 1f / 3f,
    onBackToOrigin: () -> Unit = {}
) {
    LaunchedEffect(Unit) {
        PlazaWeatherManager.autoDetect(PlazaStore.http)
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        var dropHPx by remember { mutableStateOf(0f) }
        val dropAnim = remember { Animatable(0f) }
        val scope = rememberCoroutineScope()

        val cardHeightDp = maxHeight * dropHeightFraction
        val topBandSizeDp = cardHeightDp * 0.20f
        val stepMarginDp = topBandSizeDp // 左右各缩进 1/5 画布高 = stepMarginDp

        // ----------------------------------------------------------------------
        // 搜索框与 Tool 网址自动解析状态机：
        // 1. 输入判定：只对 Tool 网址（http/https/github.com/mcp:等）触发解析；
        // 2. 解析成功后自动激活 parsedTool，驱动专属二级子画布自下滑画布下方顺畅滑出！
        // ----------------------------------------------------------------------
        var dropSearchText by remember { mutableStateOf("") }
        var parsedTool by remember { mutableStateOf<PlazaToolItem?>(null) }
        val toolCardAnim = remember { Animatable(0f) }
        var gestureStartProgress by remember { mutableStateOf(0f) }

        // 触发单个 Tool 网址真实爬取与解析函数（严格只解析单个具体 Tool，严禁解析根网址）
        fun performToolUrlCrawl() {
            val text = dropSearchText.trim()
            if (isValidSingleToolUrl(text)) {
                val cleanUrl = if (!text.startsWith("http://", true) && !text.startsWith("https://", true) && !text.startsWith("mcp:", true)) "https://$text" else text
                val toolName = parseToolNameFromUrl(cleanUrl)
                val resolvedIcon = PlazaImageMapper.resolveIcon(null, cleanUrl, cleanUrl, toolName)

                // 建立初始 Tool 实例并先展示动画
                val initialItem = PlazaToolItem(
                    id = "parsed-${toolName.lowercase()}",
                    name = toolName,
                    description = "MCP tool package endpoint: $cleanUrl",
                    iconUrl = resolvedIcon,
                    endpoint = cleanUrl,
                    homepage = cleanUrl,
                    sourceRegistry = "Direct MCP Tool",
                    installCommand = "npx -y @smithery/cli install $toolName",
                    useCount = 1,
                    tags = listOf("single-tool", "mcp")
                )
                parsedTool = initialItem
                scope.launch {
                    toolCardAnim.snapTo(0f)
                    toolCardAnim.animateTo(1f, tween(360, easing = GreenPeelOffEasing))

                    // 真实网络异步抓取校验
                    val requester = PlazaStore.http ?: AndroidHttpRequester().also { PlazaStore.http = it }
                    try {
                        val body = withContext(Dispatchers.IO) {
                            requester.request("GET", cleanUrl, emptyMap(), null)
                        }
                        val spec = McpToolSpecParser.parseJson(body)
                        if (spec != null) {
                            val enrichedName = spec.name.ifBlank { toolName }
                            parsedTool = initialItem.copy(
                                name = enrichedName,
                                description = spec.description.ifBlank { initialItem.description },
                                endpoint = spec.endpoint ?: cleanUrl
                            )
                        }
                    } catch (_: Throwable) {
                        // 网络未连通或静态页面时保持初始实例
                    }
                }
            } else {
                // 根网址、非 Tool 链接或普通关键字：绝不解析，收起并清空展示框
                if (parsedTool != null) {
                    scope.launch {
                        toolCardAnim.animateTo(0f, tween(200))
                        parsedTool = null
                    }
                }
            }
        }

        val cardBodyNetWidth = (maxWidth - (stepMarginDp * 2)).coerceAtLeast(100.dp)
        val maxSafeSearchWidth = (cardBodyNetWidth - 36.dp).coerceIn(110.dp, 220.dp)
        val targetBoxWidth = (130.dp + (dropSearchText.length * 6.5f).dp).coerceIn(120.dp, maxSafeSearchWidth)
        val searchBoxWidth by animateDpAsState(
            targetValue = targetBoxWidth,
            animationSpec = tween(150)
        )

        val density = androidx.compose.ui.platform.LocalDensity.current
        val fallbackHPx = with(density) { (maxHeight * dropHeightFraction).toPx() }

        fun settle(toOpen: Boolean) {
            PlazaOverlayCoordinator.isDropOpen = toOpen
            scope.launch {
                dropAnim.animateTo(
                    targetValue = if (toOpen) 1f else 0f,
                    animationSpec = tween(300, easing = GreenPeelOffEasing)
                )
                PlazaOverlayCoordinator.isDropOpen = dropAnim.value > 0.001f
                PlazaOverlayCoordinator.dropProgress = dropAnim.value
            }
            if (toOpen) {
                PlazaOverlayCoordinator.onDropSettleAction?.invoke()
            }
        }

        fun drive(dy: Float) {
            val total = if (dropHPx > 0f) dropHPx else fallbackHPx
            if (total <= 0f) return
            if (dropAnim.value <= 0.001f) {
                gestureStartProgress = 0f
            } else if (dropAnim.value >= 0.999f) {
                gestureStartProgress = 1f
            }
            val currentProgress = dropAnim.value
            val newProgress = ((currentProgress * total + dy * 0.98f) / total)
                .coerceIn(0f, 1f)
            PlazaOverlayCoordinator.dropProgress = newProgress
            PlazaOverlayCoordinator.isDropOpen = newProgress > 0.001f
            scope.launch { dropAnim.snapTo(newProgress) }

            if (dy > 0f) {
                PlazaOverlayCoordinator.onDropPullDownAction?.invoke(dy)
            }
        }

        fun endDrag(speedPxPerSec: Float = 0f) {
            val p = dropAnim.value
            if (p > 0.001f && p < 0.999f) {
                val shouldOpen = when {
                    speedPxPerSec < -100f -> false // 向上推/甩 -> 顺滑收回
                    speedPxPerSec > 100f -> true  // 向下拉/甩 -> 自动展开
                    gestureStartProgress > 0.4f -> p > 0.60f // 展开态上推：只要向上推过 40% (p <= 0.60) 即顺畅收回
                    else -> p > 0.20f // 收起态下拉：只要下拉超过 20% 即自动展开
                }
                settle(shouldOpen)
            } else {
                PlazaOverlayCoordinator.isDropOpen = p > 0.001f
                PlazaOverlayCoordinator.dropProgress = p
            }
            PlazaOverlayCoordinator.onDropSettleAction?.invoke()
        }

        // 注册跨画布联动与手势协调器连接（优先由 NestedScroll 驱动，绝不遮挡主页点击）
        LaunchedEffect(Unit) {
            PlazaOverlayCoordinator.onDriveDrop = { dy -> drive(dy) }
            PlazaOverlayCoordinator.onEndDragDrop = { speed -> endDrag(speed) }
        }

        // 同步下滑画布实时展开状态给全局手势协调器
        LaunchedEffect(dropAnim.value) {
            PlazaOverlayCoordinator.dropProgress = dropAnim.value
            PlazaOverlayCoordinator.isDropOpen = dropAnim.value > 0.001f
        }

        val progress = dropAnim.value

        // =====================================================================
        // 阶梯圆角新画布（展开态时挂载并置顶 zIndex(50f)，所有内部按钮 100% 自由点击）
        // 收起态时不放置任何全宽隐形透明覆盖层，主页面顶部所有轮播卡片与安装按钮 100% 享受零遮挡原生点击；
        // 唤出动作由顶部边缘 10% 手势驱动 PlazaOverlayCoordinator 流畅无缝接管。
        // =====================================================================
        if (progress > 0.001f) {
            val dropShape = remember { PlazaDropCardShape(topFraction = 0.20f, bottomRadius = 22.dp) }
            val cardH = if (dropHPx > 0f) dropHPx else fallbackHPx
            val dropShiftY = (-((1f - progress) * (cardH + with(density) { 40.dp.toPx() }))).roundToInt()

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(dropHeightFraction)
                    .align(Alignment.TopCenter)
                    .onSizeChanged { dropHPx = it.height.toFloat() }
                    .offset { IntOffset(0, dropShiftY) }
                    .shadow(16.dp, dropShape)
                    .clip(dropShape)
                    .background(Color(0xFFFBFBFA))
                    .border(
                        width = 1.dp,
                        color = Color(0xFFE2E2DF),
                        shape = dropShape
                    )
                    .zIndex(50f)
            ) {
                // =============================================================
                // 【底层绘图层】：天气主绘图背景 + 通栏贯通横线
                // =============================================================
                Box(modifier = Modifier.fillMaxSize()) {
                    // 1. 天气主绘图背景
                    PlazaWeatherCanvas(
                        mode = PlazaWeatherManager.iconMode,
                        modifier = Modifier.fillMaxSize()
                    )

                    // 2. 通栏贯通横线
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val topHPx = size.height * 0.20f
                        val strokeW = 2.dp.toPx()
                        val lineY = topHPx - (strokeW / 2f)

                        drawLine(
                            color = Color(0xFF1E1E1E),
                            start = Offset(0f, lineY),
                            end = Offset(size.width, lineY),
                            strokeWidth = strokeW,
                            cap = StrokeCap.Square
                        )
                    }
                }

                // =============================================================
                // 【顶层交互控件层】：手势解耦，放行顶部按钮点击与搜索框输入
                // =============================================================
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectStealVerticalDrag(
                                policy = { acc: Float, _: Float, downPos: Offset ->
                                    gestureStartProgress = dropAnim.value
                                    acc < 0f // 只要向上拖拽，卡片任意位置（包括顶部区域）均可灵敏跟手推回收起
                                },
                                onVerticalDrag = { dy -> drive(dy) },
                                onDragEnd = { speed -> endDrag(speed) }
                            )
                        }
                ) {
                    // 左侧 1/5×1/5 方块中央：房子图标（点击触发全部页面零延迟整体上滑出，直接返回原画布）
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .size(topBandSizeDp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                // 立即零延迟驱动外层大画布整体上滑退出，绝不启动内部 300ms 子动画造成视觉对冲与滞留顿挫
                                onBackToOrigin()
                                PlazaCrateDrawerCoordinator.close()
                                scope.launch {
                                    // 退出后后台静默复位二级卡片
                                    toolCardAnim.snapTo(0f)
                                    parsedTool = null
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        HouseLogoMark(
                            modifier = Modifier.size(topBandSizeDp * 0.58f)
                        )
                    }

                    // 中间字幕：'Tool plaza'
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .height(topBandSizeDp)
                            .padding(top = 18.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = " Tool",
                                color = Color(0xFF1E1E1E),
                                fontSize = 16.sp,
                                fontFamily = pixelFont,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(42.dp))
                            Text(
                                text = "plaza",
                                color = Color(0xFF1E1E1E),
                                fontSize = 16.sp,
                                fontFamily = pixelFont,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // =============================================================
                    // 右侧 1/5×1/5 方块：纯净箱子图标点击触发区（无任何点击反馈特效）
                    // 1. 占满整个右上 1/5×1/5 阶梯方块（topBandSizeDp × topBandSizeDp）；
                    // 2. 纯静态无任何缩放/背景变色特效；
                    // 3. 点击毫秒级直接触发 PlazaCrateDrawerCoordinator.open()！
                    // =============================================================
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(topBandSizeDp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                PlazaCrateDrawerCoordinator.open()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        CrateLogoMark(
                            modifier = Modifier.size(topBandSizeDp * 0.88f)
                        )
                    }

                    // =============================================================
                    // 【图层 5 · 顶层交互 (BUG 2 根治)】：气象图标下方严格定界自适应搜索框
                    // 1. 严格锁定在卡片主体内胆范围 (maxSafeSearchWidth) 内，绝对不刺破两翼阶梯边缘；
                    // 2. 单行平滑水平横滚，长输入内胆自适应单行滚动。
                    // =============================================================
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 12.dp)
                            .width(searchBoxWidth)
                            .height(30.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFFCFCFC))
                            .border(1.dp, Color(0xFF1E1E1E), RoundedCornerShape(6.dp))
                            .padding(horizontal = 9.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        val searchIconInteraction = remember { MutableInteractionSource() }
                        val isSearchIconPressed by searchIconInteraction.collectIsPressedAsState()
                        val searchIconScale by animateFloatAsState(
                            targetValue = if (isSearchIconPressed) 0.82f else 1.0f,
                            animationSpec = spring(dampingRatio = 0.55f, stiffness = 900f),
                            label = "searchIconScale"
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // 点击搜索图标：开始解析并爬取 Tool 网址
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable(
                                        interactionSource = searchIconInteraction,
                                        indication = null
                                    ) {
                                        performToolUrlCrawl()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                HandDrawnSearchIcon(
                                    color = if (isSearchIconPressed) Color(0xFF3DDC84) else Color(0xFF1E1E1E),
                                    modifier = Modifier
                                        .size(15.dp)
                                        .graphicsLayer {
                                            scaleX = searchIconScale
                                            scaleY = searchIconScale
                                        }
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(4.dp)),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (dropSearchText.isEmpty()) {
                                    Text(
                                        text = "search",
                                        color = Color(0xFF757575),
                                        fontSize = 10.5.sp,
                                        fontFamily = pixelFont,
                                        fontWeight = FontWeight.Normal,
                                        maxLines = 1
                                    )
                                }
                                BasicTextField(
                                    value = dropSearchText,
                                    onValueChange = { dropSearchText = it },
                                    singleLine = true,
                                    maxLines = 1,
                                    textStyle = TextStyle(
                                        color = Color(0xFF1E1E1E),
                                        fontSize = 10.5.sp,
                                        fontFamily = pixelFont
                                    ),
                                    cursorBrush = SolidColor(Color(0xFF1E1E1E)),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        }

        // =====================================================================
        // 【全屏横穿飘云（最高图层，仅小于右画布）】：
        // 1. 位于整张全屏叠层的顶层 (zIndex(50f))，浮于阶梯卡片主体 (zIndex(10f)) 与卡片内一切图标、文字、搜索框之上；
        // 2. 仅低于右侧滑出的箱子工具箱画布 (zIndex(100f))；
        // 3. 从屏幕右侧外 (screenW + cloudW) 一路匀速飞到屏幕左侧外 (-cloudW)，纵向跟随展开进度位移。
        // =====================================================================
        if (progress > 0.02f) {
            val cloudInfiniteTransition = rememberInfiniteTransition(label = "screenPassingCloud")
            val screenPassingProgress by cloudInfiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 5200, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "screenPassingProgress"
            )

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val h = if (dropHPx > 0f) dropHPx else fallbackHPx
                        translationY = -(1f - progress) * (h + 40.dp.toPx())
                        alpha = (progress * 1.2f).coerceIn(0f, 0.95f)
                    }
                    .zIndex(50f)
            ) {
                val screenW = size.width
                val cloudScale = 1.05f
                val passingCloudW = 88.dp.toPx() * cloudScale
                val strokeW = 1.8.dp.toPx()

                val startX = screenW + passingCloudW * 0.85f
                val endX = -passingCloudW * 0.85f
                val passingX = startX + (endX - startX) * screenPassingProgress

                val baseY = (dropHPx.takeIf { it > 0f } ?: fallbackHPx) * 0.54f
                val passingY = baseY - 12.dp.toPx() + (sin(screenPassingProgress * 4.0 * Math.PI).toFloat()) * 6.dp.toPx()

                drawTrimThreeCircleCloud(
                    cx = passingX,
                    cy = passingY,
                    scale = cloudScale,
                    fillColor = Color(0xFFFFFFFF),
                    strokeColor = Color(0xFF1E1E1E),
                    strokeWidthPx = strokeW,
                    trimRatio = 0.24f
                )
            }
        }

        // =====================================================================
        // 【二级下滑画布 · URL 解析 Tool 展示卡片】：
        // 当在搜索框中输入/粘贴 Tool 网址并自动解析成功后，
        // 自下滑天气画布正下方平滑向下伸出全新圆角子画布，完整展示该 Tool 资源与一键安装！
        // =====================================================================
        if (parsedTool != null && toolCardAnim.value > 0.001f && progress > 0.05f) {
            val tool = parsedTool!!
            val cardTopOffset = (dropHPx.takeIf { it > 0f } ?: fallbackHPx) + with(density) { 8.dp.toPx() }
            val isInstalled = PlazaStore.isInstalled(tool.id)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .align(Alignment.TopCenter)
                    .graphicsLayer {
                        val baseTranslationY = -(1f - progress) * (dropHPx + 40.dp.toPx())
                        translationY = baseTranslationY + cardTopOffset - (1f - toolCardAnim.value) * 120.dp.toPx()
                        alpha = toolCardAnim.value.coerceIn(0f, 1f)
                    }
                    .shadow(14.dp, RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFFBFBFA))
                    .border(1.dp, Color(0xFFE2E2DF), RoundedCornerShape(16.dp))
                    .padding(14.dp)
                    .zIndex(15f)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // 顶部标签与关闭按钮
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFF3DDC84), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "PARSED TOOL",
                                    color = Color(0xFF1E1E1E),
                                    fontSize = 8.5.sp,
                                    fontFamily = pixelFont,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = tool.sourceRegistry,
                                color = Color(0xFF888885),
                                fontSize = 8.sp,
                                fontFamily = pixelFont
                            )
                        }

                        // 关闭/收起该二级 Tool 画布
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .background(Color(0xFFEDEDED), CircleShape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    scope.launch {
                                        toolCardAnim.animateTo(0f, tween(180))
                                        parsedTool = null
                                        dropSearchText = ""
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "×",
                                color = Color(0xFF1E1E1E),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Tool 主体信息呈现
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Tool 图标
                        SquareImageLoader(tool = tool, isDarkBg = false)

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = tool.name,
                                color = Color(0xFF1E1E1E),
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = tool.homepage ?: "Tool URL auto resolved",
                                color = Color(0xFF888885),
                                fontSize = 9.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // 一键安装按钮
                        Box(
                            modifier = Modifier
                                .background(
                                    color = if (isInstalled) Color(0xFF3DDC84) else Color(0xFF1E1E1E),
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .clickable(enabled = !isInstalled) {
                                    PlazaStore.installTool(tool)
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isInstalled) "✓ INSTALLED" else "INSTALL",
                                color = if (isInstalled) Color(0xFF1E1E1E) else Color(0xFFF5F5F5),
                                fontSize = 9.sp,
                                fontFamily = pixelFont,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Tool 描述
                    Text(
                        text = tool.description,
                        color = Color(0xFF555555),
                        fontSize = 10.5.sp,
                        lineHeight = 14.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // =============================================================
        // 【App51 箱子侧滑画布挂载】：直接在 PlazaDropOverlay 根级装配 (zIndex(100f))
        // 当用户点击右上角箱子图标时，100% 毫秒级自右向左平滑滑出全高工具箱！
        // =============================================================
        PlazaCrateSlideOverlay(pixelFont = pixelFont)
    }
}

// ==============================================================================
// 辅助函数：严格判定是否为单个具体 Tool 网址（拒绝任何纯根网址 / 域名首页）
// ==============================================================================

private fun isValidSingleToolUrl(input: String): Boolean {
    val clean = input.trim().removeSuffix("/").substringAfter("://").removePrefix("www.")
    if (clean.isBlank()) return false

    // 1. 纯根网址排除列表（如 github.com, mcp.so, smithery.ai 等无具体子路径的域名）
    if (!clean.contains("/")) {
        return false // 没有斜杠路径，判定为纯根域名/主页，严禁解析
    }

    val domain = clean.substringBefore('/')
    val path = clean.substringAfter('/').trim('/')
    if (path.isBlank()) {
        return false // 斜杠后无内容，为根网址，严禁解析
    }

    // 2. 针对特定代码仓库与平台路径深度校验：
    // - GitHub: 必须是 owner/repo（至少 2 段路径，如 modelcontextprotocol/server-postgres）
    if (domain.equals("github.com", ignoreCase = true) || domain.equals("gitlab.com", ignoreCase = true)) {
        val segments = path.split('/').filter { it.isNotBlank() }
        return segments.size >= 2
    }

    // - MCP 聚合平台：必须含有具体工具唯一 ID/路径
    if (domain.contains("smithery.ai") || domain.contains("mcp.so") || domain.contains("glama.ai")) {
        val segments = path.split('/').filter { it.isNotBlank() }
        return segments.isNotEmpty() && !setOf("servers", "tools", "search", "explore").contains(path.lowercase())
    }

    // - 通用协议/地址：只要包含有效路径即视作单个具体 Tool 端点
    return path.length >= 2
}

// ==============================================================================
// 辅助函数：从单个具体 Tool 网址中提取标准 Tool 名称
// ==============================================================================

private fun parseToolNameFromUrl(url: String): String {
    val clean = url.trim().removeSuffix("/").substringAfter("://").removePrefix("www.")
    return when {
        clean.contains("github.com/") -> {
            val repoPart = clean.substringAfter("github.com/").trim('/')
            val segments = repoPart.split('/').filter { it.isNotBlank() }
            segments.getOrNull(1)?.ifBlank { null } ?: segments.firstOrNull() ?: "github-tool"
        }
        clean.contains("mcp.so/") || clean.contains("smithery.ai/") || clean.contains("glama.ai/") -> {
            clean.substringAfterLast('/').ifBlank { "mcp-tool" }
        }
        clean.contains('/') -> {
            clean.substringAfterLast('/').ifBlank { "custom-tool" }
        }
        else -> clean.substringBefore('.').ifBlank { "custom-tool" }
    }
}
