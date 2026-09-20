package org.example.project

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ============================================================================
// 【Part 1/3】入口 + 状态/逻辑中枢 + 页面骨架
// 区块 UI 组件位于 Part2,独立卡片/图标组件位于 Part3
// 注:SmoothEase 由 private 提升为 internal,供三个拆分文件共享
// ============================================================================

internal val SmoothEase = CubicBezierEasing(0.2f, 0.9f, 0.3f, 1f)

/**
 * 【子画布】Skill Plaza 技能广场
 */
@Composable
fun SubCanvasSkillPlaza(
    visible: Boolean,
    onClose: () -> Unit,
    pixelFont: FontFamily
) {
    SlideDownContainer(
        visible = visible,
        title = uiText(UiText.SkillPlazaTitle),
        onClose = onClose,
        pixelFont = pixelFont
    ) {
        SkillPlazaContent(pixelFont = pixelFont, onClose = onClose)
    }
}

/**
 * 兼容性函数别名 (复数形式 SkillsPlaza)
 */
@Composable
fun SubCanvasSkillsPlaza(
    visible: Boolean,
    onClose: () -> Unit,
    pixelFont: FontFamily
) = SubCanvasSkillPlaza(visible = visible, onClose = onClose, pixelFont = pixelFont)

@Composable
fun SkillPlazaContent(
    pixelFont: FontFamily,
    onClose: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    var poolIndex by remember { mutableIntStateOf(0) }
    var pairCounter by remember { mutableIntStateOf(0) }

    fun getNextSkill(): SkillData {
        val item = GlobalSkillPool[poolIndex % GlobalSkillPool.size]
        poolIndex++
        return item
    }

    fun generateCycle(): List<SkillGridRow> {
        val list = mutableListOf<SkillGridRow>()
        // 【BUG修复】技能池为空时 poolIndex % 0 会抛除零异常(ArithmeticException)导致进页闪退
        if (GlobalSkillPool.isEmpty()) return list
        for (r in 0 until 5) {
            list.add(
                SkillGridRow(
                    pairId = pairCounter++,
                    isTall = false,
                    itemLeft = getNextSkill(),
                    itemRight = getNextSkill()
                )
            )
        }
        list.add(
            SkillGridRow(
                pairId = pairCounter++,
                isTall = true,
                itemLeft = getNextSkill(),
                itemRight = getNextSkill()
            )
        )
        return list
    }

    val rowsList = remember {
        mutableStateListOf<SkillGridRow>().apply {
            addAll(generateCycle())
            addAll(generateCycle())
        }
    }

    // ================= 搜索框状态 (对屏幕下方15%向上滑动唤出) =================
    var isSearchBoxVisible by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var activeKeywordFilter by remember { mutableStateOf("") }

    // 关键词过滤后的行数据
    val displayedRows by remember {
        derivedStateOf {
            val query = activeKeywordFilter.trim().lowercase()
            if (query.isEmpty()) {
                rowsList
            } else {
                rowsList.filter { row ->
                    row.itemLeft.title.lowercase().contains(query) ||
                    row.itemLeft.desc.lowercase().contains(query) ||
                    row.itemLeft.tag.lowercase().contains(query) ||
                    row.itemLeft.author.lowercase().contains(query) ||
                    row.itemRight.title.lowercase().contains(query) ||
                    row.itemRight.desc.lowercase().contains(query) ||
                    row.itemRight.tag.lowercase().contains(query) ||
                    row.itemRight.author.lowercase().contains(query)
                }
            }
        }
    }

    // 大卡片状态 (Detail Card)
    var selectedSkill by remember { mutableStateOf<SkillData?>(null) }
    var detailCardFromCol by remember { mutableIntStateOf(0) }
    val detailCardAnim = remember { Animatable(-1f) }

    fun openDetailCard(skill: SkillData, col: Int) {
        selectedSkill = skill
        detailCardFromCol = col
        coroutineScope.launch {
            detailCardAnim.snapTo(if (col == 0) -1f else 1f)
            detailCardAnim.animateTo(0f, tween(350, easing = SmoothEase))
        }
    }

    fun closeDetailCard() {
        coroutineScope.launch {
            val target = if (detailCardFromCol == 0) -1f else 1f
            detailCardAnim.animateTo(target, tween(260, easing = SmoothEase))
            selectedSkill = null
        }
    }

    // 嘴巴吞噬画布动画状态
    val mouthAnim = remember { Animatable(1f) } // 1f = 下拉隐藏, 0f = 完全升起
    var isDraggingOverMouth by remember { mutableStateOf(false) }
    var isDevouringByMouth by remember { mutableStateOf(false) }
    var draggedRowIndex by remember { mutableIntStateOf(-1) }
    var draggedCol by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }

    // 碎屑粒子系统
    val debrisList = remember { mutableStateListOf<DebrisParticleState>() }

    fun triggerDebrisBurst() {
        debrisList.clear()
        for (i in 0 until 28) {
            debrisList.add(
                DebrisParticleState(
                    id = i,
                    startX = Random.nextFloat() * 340f + 20f,
                    dx = (Random.nextFloat() - 0.5f) * 260f,
                    dy = -Random.nextFloat() * 220f - 40f,
                    durationMs = (500 + Random.nextFloat() * 400).toInt()
                )
            )
        }
    }

    // 吞噬处理：被吃卡片在大嘴中渐隐/缩小消失，大嘴闭合完成下载，新卡片在原位平滑淡入浮现（杜绝原地变身瞬移）
    fun handleDevour(rowIndex: Int, col: Int) {
        coroutineScope.launch {
            // 【BUG修复】rowIndex 是网格区传来的"可见行"下标(基于 displayedRows 子集)，
            // 关键词过滤激活时与 rowsList 全集下标错位 → 会吞噬/替换错误的行。
            // 必须先按 pairId 反查出全集中的真实下标。
            val visibleRow = displayedRows.getOrNull(rowIndex)
            val realIndex = visibleRow?.let { target ->
                rowsList.indexOfFirst { it.pairId == target.pairId }
            } ?: -1

            if (visibleRow == null || realIndex == -1) {
                // 定位不到目标行(过滤切换等极端情况): 安全复位拖拽状态, 避免大嘴卡住
                draggedRowIndex = -1
                draggedCol = -1
                dragOffset = Offset.Zero
                isDraggingOverMouth = false
                launch { mouthAnim.animateTo(1f, tween(260, easing = SmoothEase)) }
                return@launch
            }

            isDevouringByMouth = true

            // 1. 大嘴急速闭合 + 碎屑粒子爆发
            launch {
                mouthAnim.animateTo(1f, tween(320, easing = CubicBezierEasing(0.55f, 0.085f, 0.68f, 0.53f)))
            }
            triggerDebrisBurst()

            // 提取被吞噬资源并作为真实下载资源存入
            run {
                val oldRow = rowsList[realIndex]
                val devouredSkill = if (col == 0) oldRow.itemLeft else oldRow.itemRight

                val newResourceCard = SkillCardItemData(
                    id = "res_${System.currentTimeMillis()}",
                    name = devouredSkill.title,
                    detail = "[${devouredSkill.tag}] ${devouredSkill.desc}",
                    color = Color(0xFF2ECC71)
                )
                DownloadedResourceStore.addDownloadedCard(newResourceCard)

                // 额外对被吞噬下载的技能资源执行网络图片异步爬虫
                if (devouredSkill.imageUrl.isNotBlank()) {
                    coroutineScope.launch {
                        SkillImageCrawlEngine.fetchOrRecrawlImage(devouredSkill.imageUrl) {}
                    }
                }
            }

            // 2. 等待被吞噬卡片在大嘴中完全缩小消失 (260ms)
            delay(260)

            // 3. 彻底重置拖拽位移状态 (此时原卡片已消失，拖拽归零)
            draggedRowIndex = -1
            draggedCol = -1
            dragOffset = Offset.Zero
            isDraggingOverMouth = false
            isDevouringByMouth = false

            // 4. 将新卡片更新到原位，并在原位以淡入动画浮现
            //    【BUG修复】delay 期间触底加载可能已追加行, 下标会漂移, 重新按 pairId 定位
            val freshIndex = rowsList.indexOfFirst { it.pairId == visibleRow.pairId }
            if (freshIndex != -1) {
                val oldRow = rowsList[freshIndex]
                val newSkill = getNextSkill()
                val updatedRow = if (col == 0) {
                    oldRow.copy(itemLeft = newSkill)
                } else {
                    oldRow.copy(itemRight = newSkill)
                }
                rowsList[freshIndex] = updatedRow
            }

            delay(200)
            debrisList.clear()
        }
    }

    // 触底无限加载
    val listState = rememberLazyListState()
    var isLoadingMore by remember { mutableStateOf(false) }

    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()
            if (totalItems == 0 || lastVisibleItem == null) false
            else lastVisibleItem.index >= totalItems - 1
        }
    }

    // 【BUG修复】key 只有 shouldLoadMore 时, 加载完成后其值仍为 true 不变化,
    // Effect 不会重触发 → 停留在底部时只会加载一次就"自锁"。追加 rowsList.size 作为 key。
    LaunchedEffect(shouldLoadMore, rowsList.size) {
        if (shouldLoadMore && !isLoadingMore && activeKeywordFilter.isBlank()) {
            isLoadingMore = true
            delay(650)
            val nextCycle = generateCycle()
            rowsList.addAll(nextCycle)
            isLoadingMore = false
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ================= 顶部标题栏 + 震源波动图标 =================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = uiText(UiText.SkillPlazaTitle),
                    fontSize = 16.sp,
                    fontFamily = pixelFont,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF111111)
                )

                EpicenterIcon(onClick = onClose)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color(0xFFE8E8E8))
            )

            // ================= GITHUB 品牌展示区与大卡片 (高度 130dp, 无缝撑满裁剪) =================
            SkillPlazaBrandHeader(
                pixelFont = pixelFont,
                selectedSkill = selectedSkill,
                detailCardAnim = detailCardAnim,
                onCloseDetailCard = { closeDetailCard() }
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color(0xFFE8E8E8))
            )

            // ================= 技能列表网格区域 (支持实时关键词搜索展示) =================
            SkillPlazaGridSection(
                listState = listState,
                displayedRows = displayedRows,
                rowsList = rowsList,
                activeKeywordFilter = activeKeywordFilter,
                isLoadingMore = isLoadingMore,
                draggedRowIndex = draggedRowIndex,
                draggedCol = draggedCol,
                isDevouringByMouth = isDevouringByMouth,
                dragOffset = dragOffset,
                pixelFont = pixelFont,
                getNextSkill = { getNextSkill() },
                openDetailCard = { skill, col -> openDetailCard(skill, col) },
                onStartDragRow = { idx, col ->
                    draggedRowIndex = idx
                    draggedCol = col
                    // 拖动卡片时自动隐藏搜索框
                    isSearchBoxVisible = false
                    focusManager.clearFocus()
                    coroutineScope.launch {
                        mouthAnim.animateTo(0f, tween(300, easing = SmoothEase))
                    }
                },
                onDragRow = { offsetChange ->
                    dragOffset += offsetChange
                    isDraggingOverMouth = dragOffset.y > 180f
                },
                onEndDragRow = { idx ->
                    if (isDraggingOverMouth) {
                        handleDevour(idx, draggedCol)
                    } else {
                        coroutineScope.launch {
                            mouthAnim.animateTo(1f, tween(260, easing = SmoothEase))
                        }
                        draggedRowIndex = -1
                        draggedCol = -1
                        dragOffset = Offset.Zero
                        isDraggingOverMouth = false
                    }
                },
                onClearFilter = {
                    searchQuery = ""
                    activeKeywordFilter = ""
                }
            )
        }

        // ================= 屏幕下方 15% 上滑捕获区 + 圆方框搜索框 =================
        SkillPlazaSearchLayer(
            pixelFont = pixelFont,
            isSearchBoxVisible = isSearchBoxVisible,
            searchQuery = searchQuery,
            onShowSearchBox = { visible -> isSearchBoxVisible = visible },
            onSearchQueryChange = { searchQuery = it },
            onExecuteSearch = {
                activeKeywordFilter = searchQuery.trim()
                focusManager.clearFocus()
            },
            onClearFilter = {
                searchQuery = ""
                activeKeywordFilter = ""
            },
            onHideSearchBox = {
                isSearchBoxVisible = false
                focusManager.clearFocus()
            }
        )

        // ================= 底部嘴巴画布抽屉 =================
        SkillPlazaMouthDrawer(
            draggedRowIndex = draggedRowIndex,
            mouthAnim = mouthAnim,
            isDraggingOverMouth = isDraggingOverMouth
        )

        // ================= 碎屑粒子特效 =================
        SkillPlazaDebrisLayer(debrisList = debrisList)
    }
}
