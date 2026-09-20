package org.example.project

// ==============================================================================
// 画布文件（新增）：subcanvascomponentplazareveal.kt —— 广场画布专属的上滑圆角子画布
//
// 【隶属广场画布，不碰分发中心】；对应你们工程里的 App49 文件。
//
// 【v30 —— 勾按钮出现时机修正为“出结果后才出现”+ 移除弹跳动效】：
// ① 勾按钮仅在【搜索结果成功返回且有内容时】才展示（searchDone && searchResults.isNotEmpty()）；
//    搜索中或无结果时不展示，输入词改动自动重置；
// ② 移除勾按钮出现时的放大脉冲动效（confirmPulse 移除），静态直接呈现，干净利落；
// ③ 尺寸（高 32dp/宽 64dp）、2dp 粗黑框、标准安卓绿（#3DDC84）与手绘黑勾全量保持。
//
// 【v29 记录】上下画布手势联动协调器 PlazaOverlayCoordinator。
// 【v28 记录】勾按钮矮化（32dp）+ 2dp 粗黑框。
//
// 【v25 记录】按钮大幅缩短；纯 Canvas 线条箭头动画（根治切割感）。
// 【v24 记录】勾按钮移至新画布底部；确认搜索直接替换 PlazaStore.allTools。
//
// 【历版记录】
// v22：箭头合并动画改逐格停靠吸收（无横切）；v21：删底部字幕；
// v20：搜索中箭头循环动画；v19：搜索结果主屏幕同款无框方块、一排两个、一次抓 10 个。
//
// 交互总览：
//   · 打开：屏幕底部 40% 区域上滑（任意速度）→ 画布从下方跟手升起，停在占屏 1.75/3；
//   · 回落：按住顶部灰线向下滑 → 跟手收回；松手过 40% 自动吸附；
//   · 中部：search 下划线搜索栏（放大镜 = 画布内预览）；
//   · 最下方：【勾】圆角方块 —— 确认搜索，直接替换原画布资源，画布自动滑回。
//
// 挂载方式（广场文件只加一行，无需包裹现有代码）：
//     在广场画布 SlideDownContainer 内容块的末尾追加
//         PlazaRevealOverlay(pixelFont = pixelFont)
//
// 规格：
// 1. 高度：占屏 1.75/3；左右/底部零缝，上缘两个 64dp 大圆角；
// 2. 滑动纯位移，无缩放、无透明、无虚化；隐藏偏移 = 画布自身像素高；
// 3. 进度源 Animatable + animateTo / snapTo；手势抢占器 Initial pass 一次性裁决，
//    干净点击（位移 < 2.6 倍触摸 slop）永远不被消费。
//
// 依赖：同包 PlazaStore / SquareImageLoader / HandDrawnDownloadButton /
//       GreenPeelOffEasing（外部符号）
// ==============================================================================

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.URLEncoder
import kotlin.math.abs
import kotlin.math.roundToInt

// ==============================================================================
// 主组件：追加式圆角子画布叠层（底部 40% 上滑唤出，占屏 1.75/3，纯位移）
//
// 用法：在广场画布 SlideDownContainer 内容块末尾加一行
//     PlazaRevealOverlay(pixelFont = pixelFont)
// 层次（自下而上）：底部隐形触发带 → 圆角画布。
// ==============================================================================

@Composable
internal fun PlazaRevealOverlay(
    pixelFont: FontFamily,
    revealHeightFraction: Float = 1.75f / 3f,
    revealContent: @Composable (
        canvasDrive: (Float) -> Unit,
        canvasDragEnd: (Float) -> Unit,
        onConfirmSearch: (String) -> Unit
    ) -> Unit = { drive, endDrag, confirm ->
        PlazaInstalledPanel(pixelFont, drive, endDrag, confirm)
    }
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize() // v23：底部零缝，不再让位 6dp 黑条（画布直接压住）
    ) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val fallbackHPx = with(density) { (maxHeight * revealHeightFraction).toPx() }

        // 画布实际像素高：由 onSizeChanged 回传，未就绪时自动采用 fallbackHPx
        var revealHPx by remember { mutableStateOf(0f) }

        // 展开进度：0 = 完全收在屏幕下方（露出 0），1 = 画布停在占屏 1.75/3 处
        val revealAnim = remember { Animatable(0f) }
        val scope = rememberCoroutineScope()

        // 记录单次手势启动时的初始进度，辅助双向判定
        var gestureStartProgress by remember { mutableStateOf(0f) }

        // 统一吸附：阈值判定后动画滑到开或合
        fun settle(toOpen: Boolean) {
            PlazaRevealCoordinator.isRevealOpen = toOpen
            scope.launch {
                revealAnim.animateTo(
                    targetValue = if (toOpen) 1f else 0f,
                    animationSpec = tween(280, easing = GreenPeelOffEasing)
                )
                PlazaRevealCoordinator.isRevealOpen = revealAnim.value > 0.001f
                PlazaRevealCoordinator.progress = revealAnim.value
            }
        }

        // 跟手驱动：dy 向上为负 → 进度增加；换算按画布实际像素高，跟手 1:1
        fun drive(dy: Float) {
            val total = if (revealHPx > 0f) revealHPx else fallbackHPx
            if (total <= 0f) return
            if (revealAnim.value <= 0.001f) {
                gestureStartProgress = 0f
            } else if (revealAnim.value >= 0.999f) {
                gestureStartProgress = 1f
            }
            val progress = revealAnim.value
            val newProgress = ((progress * total - dy * 0.98f) / total)
                .coerceIn(0f, 1f)
            PlazaRevealCoordinator.progress = newProgress
            PlazaRevealCoordinator.isRevealOpen = newProgress > 0.001f
            scope.launch { revealAnim.snapTo(newProgress) }
        }

        fun endDrag(speedPxPerSec: Float = 0f) {
            val p = revealAnim.value
            if (p > 0.001f && p < 0.999f) {
                // 智能双向吸附与速度判决：
                val shouldOpen = when {
                    speedPxPerSec > 100f -> false // 快速向下划/甩 -> 顺滑收回
                    speedPxPerSec < -100f -> true // 快速向上划/甩 -> 自动展开
                    gestureStartProgress > 0.4f -> p > 0.60f // 展开态下拉：只要向下拉动过 40% (p <= 0.60) 即顺畅收回
                    else -> p > 0.20f // 收起态上拉：拉起过 20% 即自动展开
                }
                settle(shouldOpen)
            } else {
                PlazaRevealCoordinator.isRevealOpen = p > 0.001f
                PlazaRevealCoordinator.progress = p
            }
        }

        // 注册跨画布联动与顶级手势触发器连接
        LaunchedEffect(Unit) {
            PlazaRevealCoordinator.onDrive = { dy -> drive(dy) }
            PlazaRevealCoordinator.onEndDrag = { speed -> endDrag(speed) }
            PlazaOverlayCoordinator.onDropPullDownAction = { dy ->
                if (revealAnim.value > 0f) {
                    val total = if (revealHPx > 0f) revealHPx else fallbackHPx
                    val cur = revealAnim.value
                    val next = ((cur * total - dy * 0.95f) / total).coerceIn(0f, 1f)
                    scope.launch { revealAnim.snapTo(next) }
                }
            }
            PlazaOverlayCoordinator.onDropSettleAction = {
                if (revealAnim.value > 0f) {
                    scope.launch {
                        revealAnim.animateTo(0f, tween(260, easing = GreenPeelOffEasing))
                    }
                }
            }
        }

        // 同步上滑画布实时展开状态给全局手势协调器
        LaunchedEffect(revealAnim.value) {
            PlazaRevealCoordinator.progress = revealAnim.value
            PlazaRevealCoordinator.isRevealOpen = revealAnim.value > 0.001f
        }

        // ============ 【勾】确认搜索：并发抓取 + 直接替换原画布资源 ============
        // 点击后：上滑画布自动滑回；三站并发搜索结果写入 PlazaStore.allTools ——
        // 广场的展示周期以 allTools 为键自动重建，原画布内容即被搜索结果取代
        // （不是全屏搜索页）；搜索期间广场底部抓取圈亮起（isFetching）。
        var projecting by remember { mutableStateOf(false) }

        fun confirmSearch(raw: String) {
            val q = raw.trim()
            if (q.isBlank() || projecting) return
            projecting = true
            PlazaStore.isFetching = true // 广场底部抓取圈亮起，作为搜索中的实时反馈
            settle(false)                // 上滑画布自动滑回
            scope.launch {
                val requester = PlazaStore.http ?: AndroidHttpRequester().also { PlazaStore.http = it }
                val enc = URLEncoder.encode(q, "UTF-8")
                val endpoints = listOf(
                    "https://api.smithery.ai/servers?query=$enc&pageSize=10" to "Smithery",
                    "https://mcp.so/api/servers?search=$enc&limit=10" to "mcp.so",
                    "https://glama.ai/api/mcp/servers?search=$enc&limit=10" to "Glama MCP"
                )
                val batches = endpoints.map { (url, reg) ->
                    async(Dispatchers.IO) {
                        try {
                            PlazaRegistryParser.parse(requester.request("GET", url, emptyMap(), null), reg)
                        } catch (_: Throwable) {
                            emptyList<PlazaToolItem>()
                        }
                    }
                }.awaitAll()
                val merged = batches.flatten().distinctBy { it.id + it.name }.take(10)
                // 非空才写入：搜索失败 / 无匹配时广场保持原资源
                if (merged.isNotEmpty()) {
                    PlazaStore.allTools = merged
                    // 通知原画布（广场）：资源已被替换 → 列表回顶 + 压制一次触底爬虫，
                    // 防止本次 isFetching 回落引发的续爬把刚替换的结果污染掉
                    PlazaSearchSignal.bump()
                }
                PlazaStore.isFetching = false
                projecting = false
            }
        }

        // ============ 圆角新画布主体 (处于展开态时赋予 zIndex(50f)，所有内部按钮/搜索框 100% 自由点击) ============
        // 收起态时不放置任何全宽隐形透明覆盖层，主页面底部所有条框与安装按钮 100% 享受零遮挡原生点击；
        // 唤出动作由底部边缘 20% 手势驱动 PlazaRevealCoordinator 流畅无缝接管。
        if (revealAnim.value > 0.001f) {
            val h = if (revealHPx > 0f) revealHPx else fallbackHPx
            val shiftY = ((1f - revealAnim.value) * h).roundToInt()

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(revealHeightFraction)
                    .align(Alignment.BottomCenter)
                    .onSizeChanged { revealHPx = it.height.toFloat() }
                    .offset { IntOffset(0, shiftY) }
                    .shadow(16.dp, RoundedCornerShape(topStart = 64.dp, topEnd = 64.dp))
                    .clip(RoundedCornerShape(topStart = 64.dp, topEnd = 64.dp))
                    .background(Color(0xFFFBFBFA))
                    .border(
                        width = 1.dp,
                        color = Color(0xFFE2E2DF),
                        shape = RoundedCornerShape(topStart = 64.dp, topEnd = 64.dp)
                    )
                    .zIndex(50f)
            ) {
                revealContent({ dy -> drive(dy) }, { speed -> endDrag(speed) }, { q -> confirmSearch(q) })
            }
        }
    }
}

// ==============================================================================
// 圆角子画布默认内容：灰线把手 + search 搜索栏（放大镜预览）+ 可滚动成果区 + 底部勾按钮
// ==============================================================================

@Composable
private fun PlazaInstalledPanel(
    pixelFont: FontFamily,
    canvasDrive: (Float) -> Unit,
    canvasDragEnd: (Float) -> Unit,
    onConfirmSearch: (String) -> Unit
) {
    // 搜索输入 + 焦点（分隔线反馈）
    var query by remember { mutableStateOf("") }
    var searchFocused by remember { mutableStateOf(false) }

    // 画布内预览搜索状态（放大镜触发）
    var searching by remember { mutableStateOf(false) }
    var searchDone by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<PlazaToolItem>>(emptyList()) }
    val focusRequester = remember { FocusRequester() }
    val searchScope = rememberCoroutineScope()

    // 画布内预览搜索：三站并发，结果显示在本画布的滚动区（不影响当前画布）
    fun runSearch(raw: String) {
        val q = raw.trim()
        if (q.isBlank() || searching) return
        searching = true
        searchDone = false
        searchResults = emptyList()
        searchScope.launch {
            val requester = PlazaStore.http ?: AndroidHttpRequester().also { PlazaStore.http = it }
            val enc = URLEncoder.encode(q, "UTF-8")
            val endpoints = listOf(
                "https://api.smithery.ai/servers?query=$enc&pageSize=10" to "Smithery",
                "https://mcp.so/api/servers?search=$enc&limit=10" to "mcp.so",
                "https://glama.ai/api/mcp/servers?search=$enc&limit=10" to "Glama MCP"
            )
            val batches = endpoints.map { (url, reg) ->
                async(Dispatchers.IO) {
                    try {
                        PlazaRegistryParser.parse(requester.request("GET", url, emptyMap(), null), reg)
                    } catch (_: Throwable) {
                        emptyList<PlazaToolItem>()
                    }
                }
            }.awaitAll()
            searchResults = batches.flatten().distinctBy { it.id + it.name }.take(10)
            searchDone = true
            searching = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp)
    ) {
        // 顶部灰线把手：整条通栏把手区域，按住随意上下拖动/向下滑动即平滑关闭
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .pointerInput(Unit) {
                    detectStealVerticalDrag(
                        policy = { _, _, _ -> true },
                        onVerticalDrag = { dy -> canvasDrive(dy) },
                        onDragEnd = { speed -> canvasDragEnd(speed) }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(38.dp)
                    .height(4.5.dp)
                    .background(Color(0xFFC9C9C6), RoundedCornerShape(2.5.dp))
            )
        }

        // 搜索栏（无框）：放大镜（画布内预览）+ 输入
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { focusRequester.requestFocus() },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            // 放大镜：画布内预览搜索
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clickable { runSearch(query) },
                contentAlignment = Alignment.Center
            ) {
                HandDrawnSearchIcon(
                    color = if (searching) Color(0xFF757575)
                    else if (searchFocused) Color(0xFF1E1E1E) else Color(0xFF262626),
                    modifier = Modifier.size(19.dp)
                )
            }
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.CenterStart
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = "search",
                        color = if (searchFocused) Color(0xFF757575) else Color(0xFF9A9A98),
                        fontSize = 13.sp,
                        fontFamily = pixelFont
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        searchDone = false
                        searchResults = emptyList()
                    },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = Color(0xFF1E1E1E),
                        fontSize = 13.sp,
                        fontFamily = pixelFont
                    ),
                    cursorBrush = SolidColor(Color(0xFF1E1E1E)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onFocusChanged { searchFocused = it.isFocused }
                )
            }
        }

        // 分隔线：平时浅灰；搜索聚焦时变深（最小状态反馈）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(if (searchFocused) Color(0xFF222222) else Color(0xFFE6E6E3))
        )

        // ============ 可滚动内容区：画布内搜索结果（一排两个）+ 已安装工具 ============
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            // 画布内搜索结果：主屏幕同款方块（无框），一排只展示两个
            if (searching) {
                // 箭头循环动画：1 → 2 → 3 逐一出现，尾部逐格停靠吸收，单箭头转一圈
                SearchingArrowLoader(
                    color = Color(0xFF757575),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(14.dp)
                )
            } else if (searchResults.isNotEmpty()) {
                searchResults.chunked(2).forEach { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        pair.forEach { tool ->
                            SearchSquareCard(
                                tool = tool,
                                pixelFont = pixelFont,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        // 奇数收尾：补一个空位保持两个一排的栅格
                        if (pair.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            } else if (searchDone) {
                Text(
                    text = "无匹配",
                    color = Color(0xFF9A9A98),
                    fontSize = 8.5.sp,
                    fontFamily = pixelFont
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
        }

        // ============ 新画布最下方：【勾】圆角方块（点击一次即消失） ============
        // 仅在搜索结果返回且有内容时呈现；点击后立即清除状态使按钮瞬间消失，并执行替换与回落
        if (searchDone && searchResults.isNotEmpty() && query.isNotBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(64.dp)
                        .height(32.dp)
                        .background(
                            color = Color(0xFF3DDC84),
                            shape = RoundedCornerShape(7.dp)
                        )
                        .border(
                            width = 2.dp,
                            color = Color(0xFF1E1E1E),
                            shape = RoundedCornerShape(7.dp)
                        )
                        .clickable {
                            val q = query
                            searchDone = false
                            searchResults = emptyList()
                            onConfirmSearch(q)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    HandDrawnCheckIcon(
                        color = Color(0xFF1E1E1E),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

// ==============================================================================
// 主屏幕同款方块卡片（无框版）：46dp 图标托盘 + 名字 + 两行描述 + 安装按钮
// 与主屏幕 ClassicSquareBoxCard 同款观感，按需求去掉外框；
// 宽度 weight(1f) 由调用方在 Row 作用域内经 modifier 传入（weight 是 RowScope 扩展）
// ==============================================================================

@Composable
private fun SearchSquareCard(tool: PlazaToolItem, pixelFont: FontFamily, modifier: Modifier = Modifier) {
    val isInstalled = PlazaStore.isInstalled(tool.id)

    Column(
        modifier = modifier
            .height(158.dp)
            .background(
                color = if (isInstalled) Color(0xFF222222) else Color(0xFFFCFCFC),
                shape = RoundedCornerShape(7.dp)
            )
            // 无框：不画 border
            .padding(horizontal = 8.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .background(
                    if (isInstalled) Color(0xFF2E2E2E) else Color(0xFFF5F5F5),
                    RoundedCornerShape(5.dp)
                )
                .border(
                    1.dp,
                    if (isInstalled) Color(0xFF484848) else Color(0xFFEAEAEA),
                    RoundedCornerShape(5.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            SquareImageLoader(tool = tool, isDarkBg = isInstalled)
        }

        Text(
            text = tool.name,
            color = if (isInstalled) Color(0xFFF5F5F5) else Color(0xFF1E1E1E),
            fontSize = 11.5.sp,
            fontFamily = pixelFont,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )

        Text(
            text = tool.description,
            color = if (isInstalled) Color(0xFFBDBDBD) else Color(0xFF757575),
            fontSize = 9.sp,
            fontFamily = pixelFont,
            lineHeight = 11.5.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )

        HandDrawnDownloadButton(
            isInstalled = isInstalled,
            onClick = { PlazaStore.installTool(tool) },
            modifier = Modifier.fillMaxWidth().height(26.dp)
        )
    }
}

// ==============================================================================
// 搜索中箭头动画：单 Canvas 纯线条绘制（无独立 Box 容器，零空间占用 / 零裁剪）
// 1 → 2 → 3 逐一出现；尾部两个箭头【同时平滑向左滑入首个箭头】并淡隐；
// 合并后首个箭头原地自旋 360°；短暂停顿后循环。全程手绘线条。
// ==============================================================================

@Composable
private fun SearchingArrowLoader(color: Color, modifier: Modifier = Modifier) {
    // 阶段指示：1=显示1个, 2=显示2个, 3=显示3个
    var visibleArrows by remember { mutableStateOf(1) }
    // 折叠进度：0 = 三箭头展开，1 = 尾部两个箭头同时滑入首个箭头
    val collapseAnim = remember { Animatable(0f) }
    // 首个箭头旋转角度（0 → 360°）
    val spinAnim = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        while (true) {
            // 阶段 1：逐一出现
            visibleArrows = 1
            collapseAnim.snapTo(0f)
            spinAnim.snapTo(0f)
            delay(350)
            visibleArrows = 2
            delay(350)
            visibleArrows = 3
            delay(350)

            // 阶段 2：尾部两个箭头【同时平滑滑入首个箭头】（非逐个收起）
            collapseAnim.animateTo(1f, tween(260, easing = LinearEasing))
            visibleArrows = 1
            collapseAnim.snapTo(0f)
            delay(100)

            // 阶段 3：剩下的单箭头原地旋转一圈
            spinAnim.animateTo(360f, tween(500, easing = LinearEasing))
            spinAnim.snapTo(0f)
            delay(250)
        }
    }

    androidx.compose.foundation.Canvas(modifier = modifier) {
        val stroke = 1.5.dp.toPx()
        val centerY = size.height / 2f
        val arrowW = 14.dp.toPx()
        val gap = 7.dp.toPx()
        val step = arrowW + gap

        val x0 = 0f
        val x1 = x0 + step
        val x2 = x0 + step * 2f

        val cp = collapseAnim.value // 0f -> 1f
        val spin = spinAnim.value   // 0 -> 360

        // 绘制单根手绘箭头的局部函数（纯线条坐标绘制，无任何容器布局限制）
        fun drawOneArrow(x: Float, alpha: Float) {
            if (alpha <= 0.01f) return
            val c = color.copy(alpha = alpha.coerceIn(0f, 1f))
            val tipX = x + arrowW * 0.82f
            val startX = x + arrowW * 0.06f
            val wingBack = arrowW * 0.24f
            val wingH = arrowW * 0.24f
            // 横线
            drawLine(c, Offset(startX, centerY), Offset(tipX, centerY), stroke, StrokeCap.Round)
            // 上下箭头双翼
            drawLine(c, Offset(tipX - wingBack, centerY - wingH), Offset(tipX, centerY), stroke, StrokeCap.Round)
            drawLine(c, Offset(tipX - wingBack, centerY + wingH), Offset(tipX, centerY), stroke, StrokeCap.Round)
        }

        // 1. 首个箭头（若处于旋转阶段则绕自身中心自旋）
        if (visibleArrows >= 1) {
            if (spin > 0f) {
                val pivot = Offset(x0 + arrowW / 2f, centerY)
                rotate(spin, pivot = pivot) {
                    drawOneArrow(x0, 1f)
                }
            } else {
                drawOneArrow(x0, 1f)
            }
        }

        // 2. 第二个箭头：同时向左滑入 x0，并透明渐隐
        if (visibleArrows >= 2 && cp < 1f) {
            val currX1 = x1 + (x0 - x1) * cp
            drawOneArrow(currX1, 1f - cp)
        }

        // 3. 第三个箭头：同时向左滑入 x0，并透明渐隐
        if (visibleArrows >= 3 && cp < 1f) {
            val currX2 = x2 + (x0 - x2) * cp
            drawOneArrow(currX2, 1f - cp)
        }
    }
}

// 手绘勾（标准安卓绿底上的粗黑勾，2.2dp 线宽呼应 2dp 外黑框）
@Composable
private fun HandDrawnCheckIcon(color: Color, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 2.2.dp.toPx()
        drawLine(color, Offset(w * 0.18f, h * 0.55f), Offset(w * 0.42f, h * 0.78f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.42f, h * 0.78f), Offset(w * 0.84f, h * 0.24f), stroke, StrokeCap.Round)
    }
}

// ==============================================================================
// 手绘搜索图标（放大镜：圆环 + 45° 斜柄，同包内共享）
// ==============================================================================

@Composable
internal fun HandDrawnSearchIcon(
    color: Color = Color(0xFF1E1E1E),
    modifier: Modifier = Modifier
) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.6.dp.toPx()
        // 圆环：偏左上，给斜柄留出右下空间
        val cx = w * 0.42f
        val cy = h * 0.42f
        val r = minOf(w, h) * 0.27f
        drawCircle(
            color = color,
            radius = r,
            center = Offset(cx, cy),
            style = Stroke(width = stroke)
        )
        // 斜柄：从圆环 45° 边缘画到右下角
        val edgeX = cx + r * 0.7071f
        val edgeY = cy + r * 0.7071f
        drawLine(
            color = color,
            start = Offset(edgeX, edgeY),
            end = Offset(w * 0.88f, h * 0.88f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

// ==============================================================================
// 搜索替换信号（App49 → 原画布广场；同包直连，无需 import）
// 每次搜索结果替换广场资源 generation +1；原画布监听它并做两件事：
// 列表回顶（直接展示替换后的资源）+ 压制一次触底爬虫（防替换结果被污染）。
// ==============================================================================

internal object PlazaSearchSignal {
    var generation by mutableStateOf(0)
        private set

    fun bump() {
        generation++
    }
}

// ==============================================================================
// 上下滑布联动协调器（App50 下滑 ↔ App49 上滑；同包直连，无需 import）
// 当用户从顶部下滑唤出新画布时，若上滑画布当前已打开，驱动上滑画布向下滑回收起。
// ==============================================================================

internal object PlazaOverlayCoordinator {
    var onDropPullDownAction: ((dy: Float) -> Unit)? = null
    var onDropSettleAction: (() -> Unit)? = null
    var onDriveDrop: ((Float) -> Unit)? = null
    var onEndDragDrop: ((Float) -> Unit)? = null
    var dropProgress by mutableStateOf(0f)
    var isDropOpen by mutableStateOf(false)
}

internal object PlazaRevealCoordinator {
    var onDrive: ((Float) -> Unit)? = null
    var onEndDrag: ((Float) -> Unit)? = null
    var progress by mutableStateOf(0f)
    var isRevealOpen by mutableStateOf(false)
}

// ==============================================================================
// 竖向手势策略抢占器
//
// Initial pass + requireUnconsumed = false：先于列表滚动（Main pass）拿到事件；
// 累计位移越过 2.6 倍触摸 slop 时做【一次性策略判定】：
//   命中（policy = true） → 消费后续位移事件，整段拖动归圆角画布；
//   放行（policy = false）→ 一个事件都不消费，手势完整留给下层。
// 干净点击（位移不越阈值）永远不被消费 → 点击类交互不受影响。
// ==============================================================================

internal suspend fun PointerInputScope.detectStealVerticalDrag(
    policy: (accumulated: Float, speedPxPerSec: Float, downPosition: Offset) -> Boolean,
    onVerticalDrag: (dragAmount: Float) -> Unit,
    onDragEnd: (speedPxPerSec: Float) -> Unit = {}
) {
    val decisionThreshold = viewConfiguration.touchSlop * 1.0f
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var accumulated = 0f
        var decided = false
        var accepted = false
        var moved = false
        var lastTime = down.uptimeMillis
        var lastVelocity = 0f
        while (true) {
            val event = awaitPointerEvent(pass = PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) break
            val dy = change.position.y - change.previousPosition.y
            val dx = change.position.x - change.previousPosition.x
            val now = change.uptimeMillis
            val dt = (now - lastTime).coerceAtLeast(1L)
            lastVelocity = dy * 1000f / dt
            lastTime = now

            if (!decided) {
                accumulated += dy
                // 若横向位移大于竖向，立刻放行，保证三卡片横向拖动 100% 顺畅
                if (abs(dx) > abs(accumulated) && abs(dx) > viewConfiguration.touchSlop) {
                    decided = true
                    accepted = false
                    break
                }
                if (abs(accumulated) > decisionThreshold) {
                    decided = true
                    val elapsed = (change.uptimeMillis - down.uptimeMillis).coerceAtLeast(1L)
                    val speed = abs(accumulated) * 1000f / elapsed
                    accepted = policy(accumulated, speed, down.position)
                    if (!accepted) break // 放行：手势完整交给下层（卡片、下载按钮照常点击或滚动）
                }
            }
            if (accepted && dy != 0f) {
                // 仅在明确向上/向下拖拽（accepted=true）时才消费事件，静态点击（down -> up 期间未 accepted）绝不 consume()！
                change.consume()
                moved = true
                onVerticalDrag(dy)
            }
        }
        if (accepted && moved) onDragEnd(lastVelocity)
    }
}
