package org.example.project

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// ==============================================================================
// 技能管理主体内容画板 (SkillsManagementContent)
// 传入 Ribbon 的方块为大嘴吞噬下载真实得到的资源展示块
// ==============================================================================

@Composable
fun SkillsManagementContent(
    onClose: () -> Unit,
    pixelFont: FontFamily = FontFamily.Default
) {
    val coroutineScope = rememberCoroutineScope()

    // ===== 状态集 =====
    // 1. 卡片数据列表：直接使用大嘴吞噬下载获得的真实资源卡片列表
    val cardList = DownloadedResourceStore.downloadedCards

    var activeCardId by remember { mutableStateOf<String?>(null) }
    // 搜索输入与应用过滤分离：输入只更新搜索框内容 (searchQuery)，
    // 仅在点击搜索图标 / 软键盘 Search 键后写入 appliedSearchFilter 才真正执行过滤
    var searchQuery by remember { mutableStateOf("") }
    var appliedSearchFilter by remember { mutableStateOf("") }

    // 2. 详情画布状态 (右侧滑入)
    var detailCanvasVisible by remember { mutableStateOf(false) }
    var detailCanvasCardId by remember { mutableStateOf("") }
    var detailCanvasTitle by remember { mutableStateOf(uiText(UiText.SkillMgmtDetailCanvas)) }
    var detailCanvasColor by remember { mutableStateOf(Color(0xFF6C5CE7)) }
    val detailOffsetAnim = remember { Animatable(1f) }

    // 3. 顶部滑下画布 (占 8/9 高度)
    var topCanvasOpen by remember { mutableStateOf(false) }
    val topCanvasProgress = remember { Animatable(0f) }

    // 4. 带子列表与无限颜色循环
    var leftColorIdx by remember { mutableStateOf(0) }
    var rightColorIdx by remember { mutableStateOf(4) }
    // 带子列持久化：优先读存档（名称/绿标阶段/选中集合/宽度全保留），无存档才用默认 5 列
    val persistedColumns = remember { SkillRibbonStore.loadColumns() }
    var ribbonColumns by remember {
        mutableStateOf(
            persistedColumns ?: List(5) { i ->
                RibbonColumnData(
                    id = "col_$i",
                    color = RibbonColorPalette[i % RibbonColorPalette.size],
                    widthDp = 78,
                    isInitial = true
                )
            }
        )
    }
    // 宽度不持久化：无论读档还是默认列，打开画布时都做一次初始均分（按屏宽实时分配）
    var hasDistributedInitialWidths by remember { mutableStateOf(false) }
    var editingColumnId by remember { mutableStateOf<String?>(null) }
    val ribbonLazyState = rememberLazyListState()

    // 5. 右下角设置按钮与弹出操作状态
    var isSettingsOpen by remember { mutableStateOf(false) }

    // 6. 点击长条框弹出的信息弹窗（淡入，与解析网址弹窗同款但无下载图标）
    var infoModalCardId by remember { mutableStateOf<String?>(null) }

    // 自动对管理画板中的所有已下载资源执行图片网络爬虫，确保各视图均有对应图片
    // 预爬：遍历前快照，避免迭代中修改 cardList 导致并发问题
    LaunchedEffect(cardList.size) {
        val snapshot = cardList.toList()
        snapshot.forEach { card ->
            if (card.id.startsWith("res_blank_")) return@forEach
            val imgUrl = card.imageUrl.ifBlank {
                DownloadedResourceStore.resolveImageUrlForCard(card.name, card.detail)
            }
            if (imgUrl.isNotBlank() && card.imageUrl.isBlank()) {
                // 回写封面地址到资源池，使列表/弹窗/右画布共用同一 URL
                val idx = cardList.indexOfFirst { it.id == card.id }
                if (idx != -1) {
                    cardList[idx] = cardList[idx].copy(imageUrl = imgUrl)
                }
            }
            if (imgUrl.isNotBlank()) {
                coroutineScope.launch {
                    SkillImageCrawlEngine.fetchOrRecrawlImage(imgUrl) { }
                }
            }
        }
    }

    // 只负责"打开"的滑入；关闭动画由 SkillDetailCanvasView 自己播完后再 onDismiss。
    // 这里若也对 false 抢跑 animateTo(1f)，会与画布内部动画互相取消，导致滑出被打断。
    LaunchedEffect(detailCanvasVisible) {
        if (detailCanvasVisible) {
            detailOffsetAnim.animateTo(
                0f,
                animationSpec = tween(350, easing = CubicBezierEasing(0.25f, 0.46f, 0.45f, 0.94f))
            )
        }
    }

    LaunchedEffect(topCanvasOpen) {
        if (topCanvasOpen) {
            ribbonColumns = pruneOuterUnnamedRibbons(ribbonColumns)
        }
        topCanvasProgress.animateTo(
            if (topCanvasOpen) 1f else 0f,
            animationSpec = tween(350, easing = CubicBezierEasing(0.25f, 0.46f, 0.45f, 0.94f))
        )
    }

    // 带子列自动存档：名称、阶段(绿标)、选中集合、宽度 —— 任何一次变更（重命名/推进阶段/
    // 勾选/新建/修剪/均分）都立即落盘，重启后管理画布里的带子原样保留
    LaunchedEffect(ribbonColumns) {
        SkillRibbonStore.saveColumns(ribbonColumns)
    }

    fun openDetail(card: SkillCardItemData) {
        detailCanvasCardId = card.id
        detailCanvasTitle = card.name
        detailCanvasColor = card.color
        detailCanvasVisible = true
    }

    fun addNewRibbonRight() {
        val nextIdx = (rightColorIdx + 1) % RibbonColorPalette.size
        rightColorIdx = nextIdx
        val modeWidth = getMostFrequentWidth(ribbonColumns.map { it.widthDp })
        val newCol = RibbonColumnData(
            id = "col_${System.currentTimeMillis()}",
            color = RibbonColorPalette[nextIdx],
            widthDp = modeWidth
        )
        ribbonColumns = ribbonColumns + newCol
        coroutineScope.launch {
            delay(50)
            ribbonLazyState.animateScrollToItem(ribbonColumns.size - 1)
        }
    }

    fun addNewRibbonLeft() {
        val prevIdx = (leftColorIdx - 1 + RibbonColorPalette.size) % RibbonColorPalette.size
        leftColorIdx = prevIdx
        val modeWidth = getMostFrequentWidth(ribbonColumns.map { it.widthDp })
        val newCol = RibbonColumnData(
            id = "col_${System.currentTimeMillis()}",
            color = RibbonColorPalette[prevIdx],
            widthDp = modeWidth
        )
        ribbonColumns = listOf(newCol) + ribbonColumns
        coroutineScope.launch {
            delay(50)
            ribbonLazyState.animateScrollToItem(0)
        }
    }

    // 过滤后的卡片列表：直接调用 App60_search.kt 中的 SkillSearchAndParserEngine 引擎
    val filteredCards = remember(cardList.toList(), appliedSearchFilter) {
        SkillSearchAndParserEngine.searchSkills(
            query = appliedSearchFilter,
            poolList = GlobalSkillPool,
            downloadedList = cardList.toList()
        )
    }

    /**
     * 将搜索命中的"技能广场"临时卡 (search_pool_*) 正式收录进真实资源池，
     * 返回入库后的永久卡片；若该资源已存在则直接返回已有卡片，避免重复入库。
     */
    fun adoptPoolCard(source: SkillCardItemData, overrideColor: Color? = null): SkillCardItemData {
        cardList.firstOrNull { it.name.equals(source.name, ignoreCase = true) }?.let { existing ->
            return existing
        }
        // 去除搜索阶段附加的 "[Plaza: xxx] " 展示前缀，还原为纯净的资源描述
        val cleanedDetail = if (source.detail.startsWith("[Plaza: ")) {
            source.detail.substringAfter("] ", "")
        } else {
            source.detail
        }
        val permanent = source.copy(
            id = "res_pool_${System.currentTimeMillis()}_${source.name.hashCode()}",
            detail = cleanedDetail.ifBlank { source.detail },
            color = overrideColor ?: source.color,
            imageUrl = DownloadedResourceStore.resolveImageUrlForCard(source.name, source.detail)
        )
        DownloadedResourceStore.addDownloadedCard(permanent)
        return permanent
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        val screenHeight = maxHeight
        val screenWidth = maxWidth
        val topCanvasHeight = screenHeight * (8f / 9f) - 53.dp

        LaunchedEffect(screenWidth) {
            if (!hasDistributedInitialWidths && screenWidth > 0.dp) {
                hasDistributedInitialWidths = true
                val totalWidthInt = screenWidth.value.roundToInt()
                val distributed = calculateEquallyDistributedWidths(totalWidthInt, ribbonColumns.size)
                ribbonColumns = ribbonColumns.mapIndexed { index, col ->
                    col.copy(widthDp = distributed.getOrElse(index) { 78 })
                }
            }
        }

        // ==================== 真实获得的资源卡片列表 ====================
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 53.dp)
        ) {
            if (filteredCards.isEmpty()) {
                // 空状态：区分"尚无资源"与"搜索无结果"
                val hasSearch = appliedSearchFilter.isNotBlank()
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 60.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = if (hasSearch) uiText(UiText.SkillMgmtNoSearchResultTitle) else uiText(UiText.SkillMgmtNoResourcesTitle),
                        fontFamily = pixelFont,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF666666)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (hasSearch) uiText(UiText.SkillMgmtNoSearchResultHint) else uiText(UiText.SkillMgmtNoResourcesHint),
                        fontFamily = pixelFont,
                        fontSize = 12.sp,
                        color = Color(0xFF999999),
                        textAlign = TextAlign.Center
                    )
                    if (hasSearch) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFF2F2F2))
                                .border(1.dp, Color(0xFFCCCCCC), RoundedCornerShape(12.dp))
                                .clickable {
                                    searchQuery = ""
                                    appliedSearchFilter = ""
                                }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = uiText(UiText.SkillMgmtClearSearch),
                                fontFamily = pixelFont,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF222222)
                            )
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp)
                        .padding(top = 26.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    filteredCards.forEach { card ->
                      key(card.id) {
                        SkillCardRowItem(
                            card = card,
                            isActive = (activeCardId == card.id),
                            pixelFont = pixelFont,
                            screenWidth = screenWidth,
                            detailOffsetAnim = detailOffsetAnim,
                            onRequestDelete = { targetCard ->
                                // 长按 5 秒删除：从根资源池移除（走仓储接口同步落盘），并清理指向该卡的所有残留引用，
                                // 避免弹窗/详情画布继续持有已删除 id 造成脏数据
                                DownloadedResourceStore.removeCard(targetCard.id)
                                if (activeCardId == targetCard.id) activeCardId = null
                                if (infoModalCardId == targetCard.id) infoModalCardId = null
                                if (detailCanvasCardId == targetCard.id) {
                                    detailCanvasCardId = ""
                                    detailCanvasVisible = false
                                }
                            },
                            onToggleActive = {
                                activeCardId = if (activeCardId == card.id) null else card.id
                            },
                            onCardClick = {
                                // 双击：打开详情弹窗（名称/详情/图片，无下载图标）
                                if (card.id.startsWith("search_pool_")) {
                                    val adopted = adoptPoolCard(card)
                                    activeCardId = adopted.id
                                    infoModalCardId = adopted.id
                                } else {
                                    activeCardId = card.id
                                    infoModalCardId = card.id
                                }
                            },
                            onColorCycle = { newColor ->
                                val idx = cardList.indexOfFirst { it.id == card.id }
                                if (idx != -1) {
                                    cardList[idx] = cardList[idx].copy(color = newColor)
                                    detailCanvasColor = newColor
                                    detailCanvasTitle = card.name
                                    detailCanvasCardId = card.id
                                } else {
                                    // 广场临时卡：带上新颜色一并入库，并把详情画布指向入库后的永久卡
                                    val adopted = adoptPoolCard(card, overrideColor = newColor)
                                    detailCanvasColor = newColor
                                    detailCanvasTitle = adopted.name
                                    detailCanvasCardId = adopted.id
                                }
                            },
                            onDragLeftOpenDetail = { targetCard ->
                                val opened = if (targetCard.id.startsWith("search_pool_")) {
                                    adoptPoolCard(targetCard)
                                } else {
                                    targetCard
                                }
                                detailCanvasCardId = opened.id
                                detailCanvasTitle = opened.name
                                detailCanvasColor = opened.color
                                detailCanvasVisible = true
                            }
                        )
                      }
                    }
                }
            }
        }

        // ==================== 顶部滑下画布 (占 8/9 高度) ====================
        val topCanvasOffsetY = (topCanvasProgress.value - 1f) * topCanvasHeight.value

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(topCanvasHeight)
                .offset { IntOffset(0, (53.dp.toPx() + topCanvasOffsetY * density).roundToInt()) }
                .background(Color.White)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 6.dp)
                    .pointerInput(ribbonColumns.size) {
                        awaitEachGesture {
                            val down = awaitFirstDown(pass = PointerEventPass.Initial, requireUnconsumed = false)
                            var totalX = 0f
                            var totalY = 0f
                            while (true) {
                                val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    if (Math.abs(totalX) > 36f && Math.abs(totalX) > Math.abs(totalY) * 1.15f) {
                                        if (totalX < -36f) {
                                            addNewRibbonRight()
                                        } else if (totalX > 36f) {
                                            addNewRibbonLeft()
                                        }
                                    }
                                    break
                                }
                                val dx = change.position.x - change.previousPosition.x
                                val dy = change.position.y - change.previousPosition.y
                                totalX += dx
                                totalY += dy
                            }
                        }
                    }
            ) {
                LazyRow(
                    state = ribbonLazyState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(ribbonColumns, key = { it.id }) { col ->
                        // 长按绿标(stage 2)列块：右画布直接滑出该列选中的资源
                        Box(
                            modifier = Modifier
                                .pointerInput(col.id, col.stage, col.selectedItemIds) {
                                    detectTapGestures(
                                        onLongPress = {
                                            if (col.stage != 2) return@detectTapGestures
                                            val firstId = col.selectedItemIds.firstOrNull()
                                                ?: return@detectTapGestures
                                            val card = cardList.firstOrNull { it.id == firstId }
                                                ?: return@detectTapGestures
                                            openDetail(card)
                                        }
                                    )
                                }
                        ) {
                            RibbonColumnView(
                            col = col,
                            allCards = filteredCards, // 传入经搜索筛选后的真实获得资源展示块
                            pixelFont = pixelFont,
                            isEditing = (editingColumnId == col.id),
                            onStartEditing = {
                                editingColumnId = col.id
                            },
                            onFinishEditing = { newName ->
                                if (editingColumnId == col.id) {
                                    editingColumnId = null
                                }
                                ribbonColumns = ribbonColumns.map {
                                    if (it.id == col.id) it.copy(name = newName.ifBlank { "+" }) else it
                                }
                            },
                            onToggleSelection = { cardId ->
                                ribbonColumns = ribbonColumns.map {
                                    if (it.id == col.id) {
                                        val newSet = if (cardId in it.selectedItemIds) {
                                            it.selectedItemIds - cardId
                                        } else {
                                            it.selectedItemIds + cardId
                                        }
                                        it.copy(selectedItemIds = newSet)
                                    } else it
                                }
                            },
                            onStageAdvance = {
                                if (editingColumnId != null) {
                                    editingColumnId = null
                                }
                                ribbonColumns = ribbonColumns.map {
                                    if (it.id == col.id) {
                                        val nextStage = (it.stage + 1) % 3
                                        it.copy(stage = nextStage)
                                    } else it
                                }
                            },
                            onCardClick = { cardId ->
                                cardList.firstOrNull { it.id == cardId }?.let { openDetail(it) }
                            }
                            )
                        }
                    }
                }
            }

            // 画布底部的独立黑边 (6dp #1A1A1A)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .align(Alignment.BottomCenter)
                    .background(Color(0xFF1A1A1A))
                    .pointerInput(Unit) {
                        var dragSumY = 0f
                        detectVerticalDragGestures(
                            onDragStart = { dragSumY = 0f },
                            onDragEnd = {
                                if (topCanvasOpen && dragSumY < -35f) {
                                    topCanvasOpen = false
                                } else if (!topCanvasOpen && dragSumY > 35f) {
                                    topCanvasOpen = true
                                }
                            },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                dragSumY += dragAmount
                            }
                        )
                    }
            )

            // 顶部中心拉手方块 (44x12dp, border 2dp #222, background #F7F7F7, 底部圆角 6dp)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = 10.dp)
                    .size(width = 44.dp, height = 12.dp)
                    .clip(RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp))
                    .background(Color(0xFFF7F7F7))
                    .border(
                        BorderStroke(2.dp, Color(0xFF222222)),
                        shape = RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp)
                    )
                    .pointerInput(Unit) {
                        var dragSum = 0f
                        detectVerticalDragGestures(
                            onDragStart = { dragSum = 0f },
                            onDragEnd = {
                                if (topCanvasOpen && dragSum < -35f) {
                                    topCanvasOpen = false
                                } else if (!topCanvasOpen && dragSum > 35f) {
                                    topCanvasOpen = true
                                }
                            },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                dragSum += dragAmount
                            }
                        )
                    }
            )
        }

        // ==================== 顶部常驻区域 ====================
        Box(modifier = Modifier.align(Alignment.TopCenter)) {
            SkillsManagementTopHeaderBar(pixelFont = pixelFont)
        }

        // ==================== 详情画布 (右侧滑入全屏) ====================
        SkillDetailCanvasView(
            visible = detailCanvasVisible,
            cardId = detailCanvasCardId,
            title = detailCanvasTitle,
            color = detailCanvasColor,
            screenWidth = screenWidth,
            detailOffsetAnim = detailOffsetAnim,
            pixelFont = pixelFont,
            onDismiss = {
                detailCanvasVisible = false
            },
                onContentEdited = { id, newName, newDetail, newImageUrl ->
                // 就地编辑回写真实资源池：左侧列表条目与 Ribbon 带子内的展示块同步更新
                DownloadedResourceStore.updateCardContent(id, newName, newDetail)
                if (newImageUrl.isNotEmpty()) {
                    DownloadedResourceStore.updateCardImage(id, newImageUrl)
                }
                detailCanvasTitle = newName.ifBlank { detailCanvasTitle }
            }
        )

        // ==================== 右下角专属圆角收纳凹槽及控制按钮组 ====================
        Box(
            modifier = Modifier.align(Alignment.BottomEnd),
            contentAlignment = Alignment.BottomEnd
        ) {
            BottomNotchWithControlsView(
                isSettingsOpen = isSettingsOpen,
                searchQuery = searchQuery,
                pixelFont = pixelFont,
                onToggleSettings = { isSettingsOpen = !isSettingsOpen },
                onSearchQueryChange = { searchQuery = it },
                onPerformSearch = { latestQuery ->
                    searchQuery = latestQuery
                    appliedSearchFilter = latestQuery.trim()
                },
                onCloseClick = onClose,
                onAddEmptyCard = {
                    // 在最后一个长条框下面新增一个完全空白的长条框
                    // 清空搜索过滤，确保新建的空白卡一定可见
                    searchQuery = ""
                    appliedSearchFilter = ""
                    val blank = DownloadedResourceStore.addEmptyCard()
                    activeCardId = blank.id
                }
            )
        }

        // ==================== 点击长条框弹出的信息弹窗（淡入，无下载图标） ====================
        val infoCard = infoModalCardId?.let { id -> cardList.firstOrNull { it.id == id } }
        SkillInfoModalBox(
            visible = infoCard != null,
            card = infoCard,
            pixelFont = pixelFont,
            onDismiss = { infoModalCardId = null },
            onNameChanged = { newName ->
                infoCard?.let {
                    DownloadedResourceStore.updateCardContent(it.id, newName, it.detail)
                }
            },
            onDetailChanged = { newDetail ->
                infoCard?.let {
                    DownloadedResourceStore.updateCardContent(it.id, it.name, newDetail)
                }
            },
            onImagePicked = { pickedPath ->
                infoCard?.let {
                    DownloadedResourceStore.updateCardImage(it.id, pickedPath)
                }
            }
        )
    }
}
