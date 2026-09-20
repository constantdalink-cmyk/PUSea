package org.example.project

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex

// ============================================================================
// 【Part 2/3】页面区块组件
// 品牌展示区 / 技能网格区 / 上滑搜索层 / 嘴巴抽屉 / 碎屑粒子层
// 状态统一由 Part1 的 SkillPlazaContent 持有,经参数与回调注入
// ============================================================================

// ==================== GITHUB 品牌展示区与大卡片 (高度 130dp, 无缝撑满裁剪) ====================
@Composable
internal fun SkillPlazaBrandHeader(
    pixelFont: FontFamily,
    selectedSkill: SkillData?,
    detailCardAnim: Animatable<Float, AnimationVector1D>,
    onCloseDetailCard: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp)
            .clipToBounds()
            .background(Color.White)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            GitHubBrandIcon(modifier = Modifier.size(85.dp))
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = "GITHUB",
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                color = Color(0xFF181717)
            )
        }

        val isDetailCardVisible = selectedSkill != null || (detailCardAnim.value > -0.999f && detailCardAnim.value < 0.999f)
        if (isDetailCardVisible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(20f)
                    .graphicsLayer {
                        translationX = detailCardAnim.value * size.width
                    }
                    .background(Color.White)
                    .clickable { onCloseDetailCard() }
                    .padding(16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    selectedSkill?.let { current ->
                        RobustSkillImage(
                            skill = current,
                            modifier = Modifier.size(85.dp),
                            isLarge = true,
                            pixelFont = pixelFont
                        )
                    }

                    val detailScrollState = rememberScrollState()
                    LaunchedEffect(selectedSkill) {
                        detailScrollState.scrollTo(0)
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(detailScrollState)
                            .padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.Top
                    ) {
                        Text(
                            text = selectedSkill?.title ?: "",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = pixelFont,
                            color = Color(0xFF1A1A1A)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = selectedSkill?.author ?: "",
                                fontSize = 10.sp,
                                fontFamily = pixelFont,
                                color = Color(0xFF888888)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "[${selectedSkill?.tag ?: uiText(UiText.SkillOfficialTag)}]",
                                fontSize = 9.sp,
                                fontFamily = pixelFont,
                                color = Color(0xFF3DDC84)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = selectedSkill?.desc ?: "",
                            fontSize = 11.sp,
                            fontFamily = pixelFont,
                            color = Color(0xFF444444),
                            lineHeight = 15.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = uiText(UiText.SkillTapToCloseDetail),
                            fontSize = 8.sp,
                            fontFamily = pixelFont,
                            color = Color(0xFFAAAAAA)
                        )
                    }
                }
            }
        }
    }
}

// ==================== 技能列表网格区域 (支持实时关键词搜索展示) ====================
@Composable
internal fun ColumnScope.SkillPlazaGridSection(
    listState: LazyListState,
    displayedRows: List<SkillGridRow>,
    rowsList: SnapshotStateList<SkillGridRow>,
    activeKeywordFilter: String,
    isLoadingMore: Boolean,
    draggedRowIndex: Int,
    draggedCol: Int,
    isDevouringByMouth: Boolean,
    dragOffset: Offset,
    pixelFont: FontFamily,
    getNextSkill: () -> SkillData,
    openDetailCard: (SkillData, Int) -> Unit,
    onStartDragRow: (Int, Int) -> Unit,
    onDragRow: (Offset) -> Unit,
    onEndDragRow: (Int) -> Unit,
    onClearFilter: () -> Unit
) {
    LazyColumn(
        state = listState,
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (displayedRows.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = uiText(UiText.SkillNoMatchPrefix) + activeKeywordFilter + uiText(UiText.SkillNoMatchSuffix),
                            fontSize = 12.sp,
                            fontFamily = pixelFont,
                            color = Color(0xFF888888)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = uiText(UiText.SkillTapToClearFilter),
                            fontSize = 10.sp,
                            fontFamily = pixelFont,
                            color = Color(0xFF3DDC84),
                            modifier = Modifier.clickable { onClearFilter() }
                        )
                    }
                }
            }
        }

        itemsIndexed(
            items = displayedRows,
            key = { _, row -> row.pairId }
        ) { index, row ->
            val isThisRowDragging = (draggedRowIndex == index)
            SkillGridRowView(
                rowIndex = index,
                row = row,
                pixelFont = pixelFont,
                isDraggingRow = isThisRowDragging,
                draggedCol = if (isThisRowDragging) draggedCol else -1,
                isDevouring = isThisRowDragging && isDevouringByMouth,
                dragOffset = dragOffset,
                modifier = Modifier.zIndex(if (isThisRowDragging) 1000f else 1f),
                onClickItem = { skill, col ->
                    openDetailCard(skill, col)
                },
                onStartDrag = { col ->
                    onStartDragRow(index, col)
                },
                onDrag = { offsetChange ->
                    onDragRow(offsetChange)
                },
                onEndDrag = {
                    onEndDragRow(index)
                },
                onSlideReplace = { col, isPartnerPush ->
                    val newLeft = if (col == 0 || isPartnerPush) getNextSkill() else row.itemLeft
                    val newRight = if (col == 1 || isPartnerPush) getNextSkill() else row.itemRight
                    // 【BUG修复】关键词过滤生效时, index 是 displayedRows(子集)的可见行下标,
                    // 与 rowsList 全集下标错位, 直接写会覆盖错行数据。必须按 pairId 定位真实行。
                    val realIndex = rowsList.indexOfFirst { it.pairId == row.pairId }
                    if (realIndex != -1) {
                        rowsList[realIndex] = rowsList[realIndex].copy(itemLeft = newLeft, itemRight = newRight)
                    }
                }
            )
        }

        // 触底加载转圈
        if (isLoadingMore && activeKeywordFilter.isBlank()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.5.dp,
                        color = Color(0xFF1A1A1A)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = uiText(UiText.SkillLoadingMore),
                        fontSize = 12.sp,
                        fontFamily = pixelFont,
                        color = Color(0xFF888888)
                    )
                }
            }
        }
    }
}

// ==================== 屏幕下方 15% 向上滑动捕获区域 + 圆方框搜索框 ====================
@Composable
internal fun BoxScope.SkillPlazaSearchLayer(
    pixelFont: FontFamily,
    isSearchBoxVisible: Boolean,
    searchQuery: String,
    onShowSearchBox: (Boolean) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onExecuteSearch: () -> Unit,
    onClearFilter: () -> Unit,
    onHideSearchBox: () -> Unit
) {
    // 屏幕下方 15% 双向滑动捕获区域:
    // - 向上滑动超过 18dp -> 唤出搜索框
    // - 搜索框可见时向下滑动超过 18dp -> 将搜索框滑回去(收起)
    Box(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .fillMaxHeight(0.15f)
            .pointerInput(isSearchBoxVisible) {
                var totalDragY = 0f
                detectVerticalDragGestures(
                    onDragStart = { totalDragY = 0f },
                    onDragEnd = {
                        if (totalDragY < -18f) {
                            onShowSearchBox(true)
                        } else if (totalDragY > 18f && isSearchBoxVisible) {
                            onHideSearchBox()
                        }
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        totalDragY += dragAmount
                    }
                )
            }
    )

    // 圆方框搜索框 (最左搜索图标，右边英文搜索字幕，点击搜索图标触发搜索)
    AnimatedVisibility(
        visible = isSearchBoxVisible,
        enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(320, easing = SmoothEase)) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(240)) + fadeOut(),
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 20.dp)
            .padding(horizontal = 16.dp)
            .zIndex(80f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .shadow(elevation = 12.dp, shape = RoundedCornerShape(14.dp), spotColor = Color(0x33000000))
                .clip(RoundedCornerShape(14.dp)) // 圆方框
                .background(Color.White)
                .border(1.5.dp, Color(0xFF181717), RoundedCornerShape(14.dp))
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. 最左手绘搜索图标 (点击触发关键词搜索)
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFF2F2F2))
                        .clickable { onExecuteSearch() },
                    contentAlignment = Alignment.Center
                ) {
                    HandDrawnSearchIcon(modifier = Modifier.size(20.dp))
                }

                Spacer(modifier = Modifier.width(10.dp))

                // 2. 搜索输入框 (带英文搜索字幕占位符: Search skills...)
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = uiText(UiText.SkillSearchPlaceholder),
                            fontFamily = pixelFont,
                            fontSize = 12.sp,
                            color = Color(0xFF999999)
                        )
                    }

                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { onSearchQueryChange(it) },
                        singleLine = true,
                        textStyle = TextStyle(
                            fontFamily = pixelFont,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF181717)
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onExecuteSearch() }),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // 3. 右侧搜索操作按钮或清除/关闭按钮
                if (searchQuery.isNotEmpty()) {
                    Text(
                        text = "✕",
                        fontSize = 13.sp,
                        fontFamily = pixelFont,
                        color = Color(0xFF888888),
                        modifier = Modifier
                            .clickable { onClearFilter() }
                            .padding(horizontal = 6.dp)
                    )
                }

                    // 提示字幕: 搜索框通过向下滑动收回 (已无 Hide 按钮)
                    Text(
                        text = uiText(UiText.SkillSwipeDownToClose),
                        fontSize = 8.sp,
                        fontFamily = pixelFont,
                        color = Color(0xFFBBBBBB),
                        modifier = Modifier.padding(start = 4.dp)
                    )
            }
        }
    }
}

// ==================== 底部嘴巴画布抽屉 ====================
@Composable
internal fun BoxScope.SkillPlazaMouthDrawer(
    draggedRowIndex: Int,
    mouthAnim: Animatable<Float, AnimationVector1D>,
    isDraggingOverMouth: Boolean
) {
    val isMouthRendered = draggedRowIndex != -1 || mouthAnim.value < 0.999f
    if (isMouthRendered) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(140.dp)
                .graphicsLayer {
                    translationY = mouthAnim.value * size.height
                }
                .background(if (isDraggingOverMouth) Color(0xFFFF8FA3) else Color(0xFFFF758C))
                .zIndex(40f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(Color.Black)
                    .align(Alignment.TopCenter)
                    .zIndex(2f)
            )

            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val toothHeight = 36.dp.toPx()
                drawRect(color = Color.White, size = Size(w, toothHeight))

                val toothWidth = 30.dp.toPx()
                var x = toothWidth
                while (x < w) {
                    drawLine(
                        color = Color(0xFFD8D8D8),
                        start = Offset(x, 0f),
                        end = Offset(x, toothHeight),
                        strokeWidth = 1.5.dp.toPx()
                    )
                    x += toothWidth
                }
                drawLine(
                    color = Color(0xFFD8D8D8),
                    start = Offset(0f, toothHeight),
                    end = Offset(w, toothHeight),
                    strokeWidth = 1.5.dp.toPx()
                )
            }
        }
    }
}

// ==================== 碎屑粒子特效层 ====================
@Composable
internal fun SkillPlazaDebrisLayer(
    debrisList: List<DebrisParticleState>
) {
    if (debrisList.isNotEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(60f)
        ) {
            debrisList.forEach { p ->
                DebrisParticle(particle = p)
            }
        }
    }
}
