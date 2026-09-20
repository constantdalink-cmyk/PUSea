package org.example.project

// ==============================================================================
// 画布文件 1/10 之分割 3/3：subcanvascomponentplazaui.kt —— 工具广场画布（全部 UI 组件）
//
// 原 subcanvascomponentplaza.kt 按代码量均分 3 个文件，本文件为第 3 份：
// 1. 彻底修复最后连续两个方块展示的 Bug：
//    - 严格保证方块与条框交替排布，末尾孤立无条框的方块自动合并至上一个轮播池，杜绝连续方块层；
// 2. 方块展示：左中右 3 卡片立体轮播滑动（手势左右拖动替换当前卡片 + 点击切到中央）；
// 3. 长条框排版：整齐舒展左对齐，大标题 12.sp，独立来源胶囊，宽幅描述 9.3sp，杜绝中间拥挤；
// 4. 纯白轻单色提亮 + 底部触底爬虫旋转弧指示（贴底常亮不闪灭）；
// 5. 点击中央方块本体（非下载按钮）弹出信息弹窗：遮罩压暗 + 方块淡入，
//    左上真实加载图、居中大标题、可滑动描述，右上角手绘 90° ">" 点击淡出（无任何多余按钮）。
//
// 依赖：subcanvasdispatcher.kt（SlideDownContainer，同包直接调用）
//       同包：分割 1/3（SquareImageLoader / HandDrawnDownloadButton / PlazaToolItem）
//            分割 2/3（PlazaStore / PlazaDisplayCycle / PlazaCrawlerFooter）
// ==============================================================================

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.random.Random

// ==============================================================================
// 画布主界面
// ==============================================================================

@Composable
fun SubCanvasComponentPlaza(
    visible: Boolean,
    onClose: () -> Unit,
    pixelFont: FontFamily
) {
    SlideDownContainer(
        visible = visible,
        title = "MCP PLAZA",
        onClose = onClose,
        pixelFont = pixelFont
    ) {
        LaunchedEffect(visible) {
            if (visible) {
                PlazaStore.runCrawlerLoop()
            }
        }

        val listState = rememberLazyListState()
        val cycles = remember(PlazaStore.allTools) { PlazaStore.buildDisplayCycles() }

        // 【方块信息弹窗】状态：点击中央方块本体（非下载按钮）记录目标工具并弹出；
        // 右上角手绘 90° ">" 号点击后置 false，整块淡出
        var popupTool by remember { mutableStateOf<PlazaToolItem?>(null) }
        var showToolPopup by remember { mutableStateOf(false) }

        // 记录容器总高与用户手势起始落点（纯只读探测，0% 消费，绝不阻断任何按钮点击）
        var touchDownY by remember { mutableStateOf(-1f) }
        var containerHeight by remember { mutableStateOf(1f) }

        // 底部接近判定（用于触底静默爬虫续抓与指示区 sticky）
        val isNearBottom by remember {
            derivedStateOf {
                val layoutInfo = listState.layoutInfo
                val totalItems = layoutInfo.totalItemsCount
                val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                totalItems > 0 && lastVisibleIndex >= totalItems - 2
            }
        }

        // 【搜索替换联动 · 来自 App49 的 PlazaSearchSignal】：
        // 每次搜索结果替换广场资源（信号 generation +1）：
        // ① 列表直接回到顶部，让用户第一眼看到的就是被替换后的资源；
        // ② 武装“压制一次触底爬虫”——替换动作自带的 isFetching 回落会触发
        //    下方爬虫续抓，若不压制，刚替换的结果会立刻被爬来的旧数据污染。
        var suppressCrawlOnce by remember { mutableStateOf(false) }
        LaunchedEffect(PlazaSearchSignal.generation) {
            if (PlazaSearchSignal.generation > 0) {
                listState.scrollToItem(0)
                suppressCrawlOnce = true
            }
        }

        // 【触底无间隔自动重试】双键触发：每轮抓取结束（isFetching true→false）
        // 立即复检，仍贴底则马上续爬，空轮不等待、不熔断；
        // 每轮请求自身的网络耗时即天然间隔。
        // 搜索替换后的第一次触底被压制一次（否则替换结果被爬虫污染），之后恢复正常。
        LaunchedEffect(isNearBottom, PlazaStore.isFetching) {
            if (isNearBottom && !PlazaStore.isFetching) {
                if (suppressCrawlOnce) {
                    suppressCrawlOnce = false
                } else {
                    PlazaStore.runCrawlerLoop()
                }
            }
        }

        val nestedScrollConnection = remember {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    // 1. 若顶部下滑画布处于展开态中（isDropOpen），用户向上推（available.y < 0），优先收回顶部天气画布
                    if (PlazaOverlayCoordinator.isDropOpen && available.y < 0f) {
                        PlazaOverlayCoordinator.onDriveDrop?.invoke(available.y)
                        return Offset(0f, available.y)
                    }
                    // 2. 若底部上滑画布处于展开态中（isRevealOpen），用户向下拉（available.y > 0），优先收回底部搜索画布
                    if (PlazaRevealCoordinator.isRevealOpen && available.y > 0f) {
                        PlazaRevealCoordinator.onDrive?.invoke(available.y)
                        return Offset(0f, available.y)
                    }

                    // 3. 屏幕顶部 10% 与底部 20% 边缘手势唤出：
                    // 当手势发生在屏幕顶部 10% 区域（touchDownY < containerHeight * 0.10f）且用户向下拉（available.y > 0f）：
                    // 无论列表当前滚动在何处，直接跟手唤出顶部下滑天气画布！
                    // Compose 1.7+ 官方替代：Fling → SideEffect（惯性/动画驱动），仅放行真实手指拖动（UserInput）
                    if (source != NestedScrollSource.SideEffect && available.y > 0f && touchDownY >= 0f && touchDownY < containerHeight * 0.10f && !PlazaRevealCoordinator.isRevealOpen) {
                        PlazaOverlayCoordinator.onDriveDrop?.invoke(available.y)
                        return Offset(0f, available.y)
                    }

                    // 当手势发生在屏幕底部 20% 区域（touchDownY > containerHeight * 0.80f）且用户向上拉（available.y < 0f）：
                    // 无论列表当前滚动在何处，直接跟手唤出底部上滑搜索画布！
                    // Compose 1.7+ 官方替代：Fling → SideEffect（惯性/动画驱动），仅放行真实手指拖动（UserInput）
                    if (source != NestedScrollSource.SideEffect && available.y < 0f && touchDownY > containerHeight * 0.80f && !PlazaOverlayCoordinator.isDropOpen) {
                        PlazaRevealCoordinator.onDrive?.invoke(available.y)
                        return Offset(0f, available.y)
                    }

                    return Offset.Zero
                }

                override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                    // 彻底删除滑到顶/底就让上/下滑画布强制滑出的代码，列表滑到顶/底自然停止，不拉出画布
                    return Offset.Zero
                }

                override suspend fun onPreFling(available: Velocity): Velocity {
                    PlazaOverlayCoordinator.onEndDragDrop?.invoke(available.y)
                    PlazaRevealCoordinator.onEndDrag?.invoke(available.y)
                    return Velocity.Zero
                }

                override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                    PlazaOverlayCoordinator.onEndDragDrop?.invoke(available.y)
                    PlazaRevealCoordinator.onEndDrag?.invoke(available.y)
                    return Velocity.Zero
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .padding(horizontal = 8.dp)
                .onSizeChanged { containerHeight = it.height.toFloat().coerceAtLeast(1f) }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        touchDownY = down.position.y
                    }
                }
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().nestedScroll(nestedScrollConnection),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                cycles.forEachIndexed { cycleIdx, cycle ->
                    // 第 1 层：【左中右 3 卡片立体轮播滑动】
                    if (cycle.layer1BoxTools.isNotEmpty()) {
                        item(key = "cycle_${cycleIdx}_boxes") {
                            ThreeCardCarousel(
                                tools = cycle.layer1BoxTools,
                                pixelFont = pixelFont,
                                onCenterBoxClick = { tool ->
                                    // 点击中央方块本体（非下载按钮）→ 弹出该工具信息弹窗
                                    popupTool = tool
                                    showToolPopup = true
                                }
                            )
                        }
                    }

                    // 第 2 - 3 - 4 - 5 - 6 层：【加高整齐舒展的条框卡片】
                    if (cycle.layer2to6BarTools.isNotEmpty()) {
                        item(key = "cycle_${cycleIdx}_bars") {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                cycle.layer2to6BarTools.forEach { tool ->
                                    NeatHorizontalBarCard(tool = tool, pixelFont = pixelFont)
                                }
                            }
                        }
                    }
                }

                // 底部指示区
                item(key = "plaza_crawler_indicator") {
                    PlazaCrawlerFooter(
                        isFetching = PlazaStore.isFetching,
                        // 贴底期间加载圈常亮：轮与轮之间不闪灭
                        sticky = isNearBottom
                    )
                }
            }
        }

        // 【下滑圆角子画布】置顶挂载在最上层，优先捕获顶部手势
        PlazaDropOverlay(
            pixelFont = pixelFont,
            onBackToOrigin = onClose
        )

        // 【上滑圆角子画布】置顶挂载在最上层，优先捕获底部手势
        PlazaRevealOverlay(pixelFont = pixelFont)

        // 【方块信息弹窗】最顶层挂载：遮罩压暗 + 方块淡入，点击手绘 90° ">" 淡出
        PlazaToolPopup(
            tool = popupTool,
            visible = showToolPopup,
            onDismiss = { showToolPopup = false },
            pixelFont = pixelFont
        )
    }
}

// ==============================================================================
// 第 1 层：【左、中、右 3卡片轮播滑动】组件
// ==============================================================================

@Composable
private fun ThreeCardCarousel(
    tools: List<PlazaToolItem>,
    pixelFont: FontFamily,
    onCenterBoxClick: (PlazaToolItem) -> Unit
) {
    if (tools.isEmpty()) return

    val safeList = remember(tools) {
        when (tools.size) {
            1 -> listOf(tools[0], tools[0], tools[0])
            2 -> listOf(tools[0], tools[1], tools[0])
            else -> tools
        }
    }

    var centerIndex by remember { mutableStateOf(0) }
    val dragOffset = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()

    val leftIndex = (centerIndex - 1 + safeList.size) % safeList.size
    val rightIndex = (centerIndex + 1) % safeList.size

    val leftTool = safeList[leftIndex]
    val centerTool = safeList[centerIndex]
    val rightTool = safeList[rightIndex]

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(168.dp)
                .pointerInput(safeList.size, centerIndex) {
                    detectSmoothHorizontalDrag(
                        onHorizontalDrag = { dx ->
                            coroutineScope.launch {
                                dragOffset.snapTo(dragOffset.value + dx * 0.9f)
                            }
                        },
                        onDragEnd = {
                            coroutineScope.launch {
                                val current = dragOffset.value
                                if (current < -40f) {
                                    dragOffset.animateTo(-140f, tween(140, easing = LinearEasing))
                                    centerIndex = (centerIndex + 1) % safeList.size
                                    dragOffset.snapTo(140f)
                                    dragOffset.animateTo(0f, tween(150))
                                } else if (current > 40f) {
                                    dragOffset.animateTo(140f, tween(140, easing = LinearEasing))
                                    centerIndex = (centerIndex - 1 + safeList.size) % safeList.size
                                    dragOffset.snapTo(-140f)
                                    dragOffset.animateTo(0f, tween(150))
                                } else {
                                    dragOffset.animateTo(0f, tween(120))
                                }
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            // 1. 左侧卡片
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        val baseShift = -105.dp.toPx()
                        translationX = baseShift + dragOffset.value * 0.6f
                        scaleX = 0.84f
                        scaleY = 0.84f
                        alpha = (0.55f + (dragOffset.value / 300f)).coerceIn(0.2f, 0.9f)
                    }
                    .zIndex(1f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        coroutineScope.launch {
                            dragOffset.animateTo(140f, tween(140))
                            centerIndex = leftIndex
                            dragOffset.snapTo(-140f)
                            dragOffset.animateTo(0f, tween(150))
                        }
                    }
            ) {
                ClassicSquareBoxCard(tool = leftTool, pixelFont = pixelFont)
            }

            // 2. 右侧卡片
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        val baseShift = 105.dp.toPx()
                        translationX = baseShift + dragOffset.value * 0.6f
                        scaleX = 0.84f
                        scaleY = 0.84f
                        alpha = (0.55f - (dragOffset.value / 300f)).coerceIn(0.2f, 0.9f)
                    }
                    .zIndex(1f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        coroutineScope.launch {
                            dragOffset.animateTo(-140f, tween(140))
                            centerIndex = rightIndex
                            dragOffset.snapTo(140f)
                            dragOffset.animateTo(0f, tween(150))
                        }
                    }
            ) {
                ClassicSquareBoxCard(tool = rightTool, pixelFont = pixelFont)
            }

            // 3. 中央卡片 (置顶最高 zIndex，其内部的安装下载按钮享有 100% 独立最高点击权)
            // 点击方块本体（未命中下载按钮的点击）→ 回调弹出信息弹窗；
            // 横向拖动仍由外层手势检测器接管，静态点击绝不消费，按钮点击不受任何影响
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        translationX = dragOffset.value
                        val dragScale = (1.0f - (abs(dragOffset.value) / 500f)).coerceIn(0.85f, 1.0f)
                        scaleX = dragScale
                        scaleY = dragScale
                        alpha = 1.0f
                    }
                    .zIndex(5f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        onCenterBoxClick(centerTool)
                    }
            ) {
                ClassicSquareBoxCard(tool = centerTool, pixelFont = pixelFont)
            }
        }

        // 底部微型指示点
        if (safeList.size > 1) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                safeList.indices.forEach { index ->
                    val isCurrent = index == centerIndex
                    Box(
                        modifier = Modifier
                            .size(if (isCurrent) 4.5.dp else 3.dp)
                            .background(
                                color = if (isCurrent) Color(0xFF222222) else Color(0xFFDCDCDC),
                                shape = CircleShape
                            )
                    )
                }
            }
        }
    }
}

// ==============================================================================
// 经典竖版【方块样式】卡片（点击下载后重爬找新资源替换，永远保持清爽白底方块，绝不变成黑块）
// ==============================================================================

@Composable
private fun ClassicSquareBoxCard(
    tool: PlazaToolItem,
    pixelFont: FontFamily,
    onDownloadAndReplace: (() -> Unit)? = null
) {
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .width(132.dp)
            .height(158.dp)
            .background(
                color = Color(0xFFFCFCFC), // 始终保持纯白清爽背景，绝不变成黑块
                shape = RoundedCornerShape(7.dp)
            )
            .border(
                width = 1.dp,
                color = Color(0xFFE2E2E2),
                shape = RoundedCornerShape(7.dp)
            )
            .padding(horizontal = 8.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .background(Color(0xFFF5F5F5), RoundedCornerShape(5.dp))
                .border(1.dp, Color(0xFFEAEAEA), RoundedCornerShape(5.dp)),
            contentAlignment = Alignment.Center
        ) {
            SquareImageLoader(tool = tool, isDarkBg = false)
        }

        Text(
            text = tool.name,
            color = Color(0xFF1E1E1E),
            fontSize = 11.5.sp,
            fontFamily = pixelFont,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )

        Text(
            text = tool.description,
            color = Color(0xFF757575),
            fontSize = 9.sp,
            fontFamily = pixelFont,
            lineHeight = 11.5.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )

        HandDrawnDownloadButton(
            isInstalled = false,
            onClick = {
                if (onDownloadAndReplace != null) {
                    onDownloadAndReplace()
                } else {
                    coroutineScope.launch {
                        PlazaStore.installTool(tool)
                        // 触发重爬抓取更多新鲜资源
                        PlazaStore.runCrawlerLoop()
                        // 从 allTools 中找到未安装的项来替换当前方块位置
                        val currentList = PlazaStore.allTools.toMutableList()
                        val currentIdx = currentList.indexOfFirst { it.id == tool.id }
                        if (currentIdx != -1) {
                            val candidateIdx = currentList.indexOfFirst { item ->
                                !PlazaStore.isInstalled(item.id) && item.id != tool.id
                            }
                            if (candidateIdx != -1 && candidateIdx != currentIdx) {
                                val candidate = currentList.removeAt(candidateIdx)
                                currentList[currentIdx] = candidate
                                currentList.add(tool)
                                PlazaStore.allTools = currentList
                            }
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(26.dp)
        )
    }
}

// ==============================================================================
// 第 2-3-4-5-6 层：【整齐舒展的加高条框卡片】（点击安装后触发重爬并替换新资源，保持纯白清爽，绝不变成黑长条块）
// ==============================================================================

@Composable
private fun NeatHorizontalBarCard(
    tool: PlazaToolItem,
    pixelFont: FontFamily
) {
    val coroutineScope = rememberCoroutineScope()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .background(
                color = Color(0xFFFCFCFC), // 始终保持纯白清爽，绝不变黑
                shape = RoundedCornerShape(5.dp)
            )
            .border(
                width = 1.dp,
                color = Color(0xFFE8E8E8),
                shape = RoundedCornerShape(5.dp)
            )
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.Start
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                // 【防竖排挤压修复】标题独占剩余弹性宽度并优先省略号收缩，
                // 来源胶囊绝不参与收缩，彻底杜绝胶囊被饿死成单字符宽竖排换行
                Text(
                    text = tool.name,
                    color = Color(0xFF1E1E1E),
                    fontSize = 12.sp,
                    fontFamily = pixelFont,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .background(
                            Color(0xFFEDEDED),
                            RoundedCornerShape(3.dp)
                        )
                        .padding(horizontal = 5.dp, vertical = 1.5.dp)
                ) {
                    Text(
                        text = tool.sourceRegistry,
                        color = Color(0xFF757575),
                        fontSize = 7.5.sp,
                        fontFamily = pixelFont,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip
                    )
                }
            }

            Text(
                text = tool.description,
                color = Color(0xFF757575),
                fontSize = 9.3.sp, // 字幕字号 -0.2sp，行内更舒展
                fontFamily = pixelFont,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        HandDrawnDownloadButton(
            isInstalled = false,
            onClick = {
                coroutineScope.launch {
                    PlazaStore.installTool(tool)
                    // 1. 触发后台异步爬虫抓取更多新鲜资源
                    PlazaStore.runCrawlerLoop()
                    // 2. 从资源池中调取未安装的工具，即时替换当前长条框位置
                    val currentList = PlazaStore.allTools.toMutableList()
                    val currentIdx = currentList.indexOfFirst { it.id == tool.id }
                    if (currentIdx != -1) {
                        val candidateIdx = currentList.indexOfFirst { item ->
                            !PlazaStore.isInstalled(item.id) && item.id != tool.id
                        }
                        if (candidateIdx != -1 && candidateIdx != currentIdx) {
                            val candidate = currentList.removeAt(candidateIdx)
                            currentList[currentIdx] = candidate
                            currentList.add(tool)
                            PlazaStore.allTools = currentList
                        }
                    }
                }
            },
            modifier = Modifier.size(30.dp)
        )
    }
}

// ==============================================================================
// 轮播平滑横向拖拽手势检测器（带 touchSlop 判定，绝不拦截/误吃点击事件）
// 1. 静态点击（位移 < touchSlop）在 PointerEventPass.Initial 中绝不 consume()，
//    保证卡片内的【安装/下载按钮】(HandDrawnDownloadButton) 100% 独立干净触发；
// 2. 纵向位移优先放行给 LazyColumn 滚动；横向明确拖动才接管并消费事件。
// ==============================================================================

private suspend fun PointerInputScope.detectSmoothHorizontalDrag(
    onHorizontalDrag: (dragAmount: Float) -> Unit,
    onDragEnd: () -> Unit
) {
    val decisionThreshold = viewConfiguration.touchSlop * 1.0f
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var accumulatedX = 0f
        var accumulatedY = 0f
        var decided = false
        var accepted = false
        var moved = false

        while (true) {
            val event = awaitPointerEvent(pass = PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) break

            val dx = change.position.x - change.previousPosition.x
            val dy = change.position.y - change.previousPosition.y

            if (!decided) {
                accumulatedX += dx
                accumulatedY += dy

                if (abs(accumulatedY) > abs(accumulatedX) && abs(accumulatedY) > decisionThreshold) {
                    decided = true
                    accepted = false
                    break // 纵向滚动优先放行给列表
                }
                if (abs(accumulatedX) > decisionThreshold) {
                    decided = true
                    accepted = true
                }
            }

            if (accepted && dx != 0f) {
                change.consume()
                moved = true
                onHorizontalDrag(dx)
            }
        }
        if (accepted && moved) {
            onDragEnd()
        }
    }
}

// ==============================================================================
// 【方块信息弹窗】：点击中央方块本体（非下载按钮）→ 遮罩压暗 + 方块淡入；
// 方块内容（严格按需求，绝无任何多余按钮）：
//   · 左上角：真实加载图（复用分割 1/3 的 SquareImageLoader，与卡片同源，绝不使用假资源）
//   · 中间：工具名称（居中大标题）+ 来源胶囊（工具自带字段 sourceRegistry）
//   · 下面：描述（可纵向滑动查看长文本）
//   · 右上角：手绘 90° ">" 号，点击后整个弹窗淡出
// ==============================================================================

@Composable
private fun PlazaToolPopup(
    tool: PlazaToolItem?,
    visible: Boolean,
    onDismiss: () -> Unit,
    pixelFont: FontFamily
) {
    if (tool == null) return

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. 底层暗色遮罩：方块下面暗一些（纯淡入 / 淡出，并静默吞掉下层一切手势，杜绝背后偷滚）
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = tween(170)),
            exit = fadeOut(animationSpec = tween(230))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x59141414))
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            do {
                                val event = awaitPointerEvent()
                                event.changes.forEach { it.consume() }
                            } while (event.changes.any { it.pressed })
                        }
                    }
            )
        }

        // 2. 信息方块本体：淡入（+ 极轻微聚拢缩放，仍是“淡入”观感），点击 ">" 后淡出
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = tween(210)) + scaleIn(
                initialScale = 0.93f,
                animationSpec = tween(210)
            ),
            exit = fadeOut(animationSpec = tween(190)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Column(
                modifier = Modifier
                    .width(250.dp)
                    .height(298.dp)
                    .background(Color(0xFFFCFCFC), RoundedCornerShape(9.dp))
                    .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(9.dp))
                    .padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 顶行：左上角加载图 + 右上角手绘 90° ">" 关闭号
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .background(Color(0xFFF5F5F5), RoundedCornerShape(6.dp))
                            .border(1.dp, Color(0xFFEAEAEA), RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        SquareImageLoader(tool = tool, isDarkBg = false)
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    HandDrawnNinetyChevron(onClick = onDismiss)
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 中间：工具名称（居中大标题）
                Text(
                    text = tool.name,
                    color = Color(0xFF1E1E1E),
                    fontSize = 14.sp,
                    fontFamily = pixelFont,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp)
                )

                Spacer(modifier = Modifier.height(6.dp))

                // 来源胶囊：工具自带字段 sourceRegistry，轻展示不抢视觉
                Box(
                    modifier = Modifier
                        .background(Color(0xFFEDEDED), RoundedCornerShape(3.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = tool.sourceRegistry,
                        color = Color(0xFF757575),
                        fontSize = 7.5.sp,
                        fontFamily = pixelFont,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 分隔线：名称区与描述区的轻分界
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color(0xFFEFEFEF))
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 下面：描述（可纵向滑动）
                Text(
                    text = tool.description,
                    color = Color(0xFF6B6B6B),
                    fontSize = 10.sp,
                    fontFamily = pixelFont,
                    lineHeight = 15.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(end = 6.dp)
                        .verticalScroll(rememberScrollState())
                )
            }
        }
    }
}

// ==============================================================================
// 手绘 90° ">" 号：两笔夹角严格 90°（正方形画布下两笔向量点积恒为 0），
// 以固定种子抖动折线模拟手绘笔触，点击即触发弹窗淡出。
// ==============================================================================

@Composable
private fun HandDrawnNinetyChevron(onClick: () -> Unit) {
    // 固定随机种子：手绘抖动一次生成，不随重组漂移
    val jitterSeed = remember { Random.nextInt(1_000_000) }

    Box(
        modifier = Modifier
            .size(34.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(16.dp)) {
            val w = size.width
            val h = size.height
            val jitter = Random(jitterSeed)
            val wobble = w * 0.075f

            // 手绘抖动折线：把直线拆成多段并叠加微小垂直噪声
            fun sketchLine(x1: Float, y1: Float, x2: Float, y2: Float) {
                val path = Path().apply { moveTo(x1, y1) }
                val segments = 5
                for (i in 1 until segments) {
                    val t = i / segments.toFloat()
                    path.lineTo(
                        x = x1 + (x2 - x1) * t + (jitter.nextFloat() - 0.5f) * wobble,
                        y = y1 + (y2 - y1) * t + (jitter.nextFloat() - 0.5f) * wobble
                    )
                }
                path.lineTo(x2, y2)
                drawPath(
                    path = path,
                    color = Color(0xFF222222),
                    style = Stroke(
                        width = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }

            // 90° 夹角的 ">"：两笔向量 (0.42w, 0.42h) 与 (-0.42w, 0.42h)，
            // 正方形画布下点积恒为 0 —— 严格直角
            sketchLine(w * 0.29f, h * 0.08f, w * 0.71f, h * 0.50f)
            sketchLine(w * 0.71f, h * 0.50f, w * 0.29f, h * 0.92f)
        }
    }
}