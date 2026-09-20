package org.example.project

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val SpaceBlueGrayTop = Color(0xFF1A2531)
private val SpaceBlueGrayMid = Color(0xFF263747)
private val SpaceBlueGrayBottom = Color(0xFF344A5E)
private val SpaceLine = Color(0xFF9DB4C9)
private val SpaceDeleteRed = Color(0xFFC26558)

@Composable
fun BoxScope.SpaceBlueGrayPullDownCanvas(
    visible: Boolean,
    onClose: () -> Unit,
    pixelFont: FontFamily
) {
    val anim = remember { Animatable(-1f) }
    val archiveScope = rememberCoroutineScope()

    val conversationCards by
        ConversationStore
            .conversationCards
            .collectAsState()

    val currentConversationId by
        ConversationStore
            .currentConversationId
            .collectAsState()

    // 整体向左滑 → 从右侧滑入牛皮纸字幕画布
    var kraftCanvasOpen by remember {
        mutableStateOf(false)
    }
    // 唤起来源卡片的编号(左滑卡片时写入)
    var swipedCardId by remember {
        mutableStateOf("")
    }
    // 右滑卡片后待确认删除的会话（null = 弹窗关闭）
    var pendingDeleteCard by remember {
        mutableStateOf<ConversationCardRecord?>(null)
    }

    LaunchedEffect(visible) {
        anim.animateTo(
            targetValue =
                if (visible) 0f else -1f,
            animationSpec = tween(
                durationMillis =
                    if (visible) 620 else 360,
                easing = GreenPeelOffEasing
            )
        )
    }

    val rendered =
        visible || anim.value > -0.999f

    if (!rendered) {
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(30f)
            .graphicsLayer {
                translationY =
                    anim.value * size.height
            }
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        SpaceBlueGrayTop,
                        SpaceBlueGrayMid,
                        SpaceBlueGrayBottom
                    )
                )
            )
            .clickable { },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = 18.dp,
                    end = 18.dp,
                    top = 54.dp,
                    bottom = 24.dp
                ),
            horizontalAlignment =
                Alignment.CenterHorizontally
        ) {
            Text(
                text = uiText(UiText.ConversationArchive),
                color =
                    Color.White.copy(alpha = 0.94f),
                fontSize = 19.sp,
                fontFamily = pixelFont,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(
                        SpaceLine.copy(alpha = 0.55f)
                    )
            )

            Spacer(Modifier.height(14.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .background(
                        Color.White.copy(alpha = 0.08f),
                        RoundedCornerShape(8.dp)
                    )
                    .border(
                        1.dp,
                        SpaceLine.copy(alpha = 0.55f),
                        RoundedCornerShape(8.dp)
                    )
                    .clickable {
                        archiveScope.launch {
                            ConversationStore.createNewConversation()
                            delay(280)
                            onClose()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = uiText(UiText.NewConversation),
                    color =
                        Color.White.copy(alpha = 0.88f),
                    fontSize = 11.sp,
                    fontFamily = pixelFont,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(14.dp))

            if (conversationCards.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = uiText(UiText.NoSavedConversations),
                        color =
                            Color.White.copy(alpha = 0.48f),
                        fontSize = 12.sp,
                        fontFamily = pixelFont
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(
                            rememberScrollState()
                        ),
                    verticalArrangement =
                        Arrangement.spacedBy(10.dp)
                ) {
                    conversationCards.forEach { card ->
                        ConversationArchiveCard(
                            card = card,
                            isCurrent =
                                card.conversationId ==
                                    currentConversationId,
                            pixelFont = pixelFont,
                            onClick = {
                                val selected =
                                    ConversationStore
                                        .selectConversation(
                                            card.conversationId
                                        )

                                if (selected) {
                                    archiveScope.launch {
                                        delay(280)
                                        onClose()
                                    }
                                }
                            },
                            onRequestDelete = {
                                pendingDeleteCard = card
                            },
                            onOpenCanvas = { id ->
                                swipedCardId = id
                                kraftCanvasOpen = true
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            Box(
                modifier = Modifier
                    .width(150.dp)
                    .height(42.dp)
                    .background(
                        Color.White.copy(alpha = 0.08f),
                        RoundedCornerShape(8.dp)
                    )
                    .border(
                        1.dp,
                        SpaceLine.copy(alpha = 0.55f),
                        RoundedCornerShape(8.dp)
                    )
                    .clickable {
                        onClose()
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = uiText(UiText.Close),
                    color =
                        Color.White.copy(alpha = 0.88f),
                    fontSize = 13.sp,
                    fontFamily = pixelFont,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 22.dp)
                .width(56.dp)
                .height(4.dp)
                .background(
                    SpaceLine.copy(alpha = 0.5f),
                    RoundedCornerShape(10.dp)
                )
        )

        // 右缘竖条：提示可向左滑唤出新画布
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 6.dp)
                .width(4.dp)
                .height(56.dp)
                .background(
                    SpaceLine.copy(alpha = 0.32f),
                    RoundedCornerShape(10.dp)
                )
        )

        // 左滑唤出：牛皮纸字幕画布 V2(自右侧滑入，盖在本画布之上)
        // 旧版 KraftSubtitleBookmarkCanvas 保留在工程内仅供对照，不再挂载
        KraftSubtitleCanvasV2(
            visible = kraftCanvasOpen,
            onClose = { kraftCanvasOpen = false },
            pixelFont = pixelFont,
            cardId = swipedCardId
        )

        // ============ 右滑删除确认弹窗 ============
        AnimatedVisibility(
            visible = pendingDeleteCard != null,
            enter =
                fadeIn(tween(180)) + scaleIn(
                    initialScale = 0.86f,
                    animationSpec = tween(220)
                ),
            exit =
                fadeOut(tween(140)) + scaleOut(
                    targetScale = 0.9f,
                    animationSpec = tween(140)
                )
        ) {
            val targetCard =
                pendingDeleteCard
                    ?: return@AnimatedVisibility

            // 半透明遮罩：点击遮罩 = 放弃删除
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(40f)
                    .background(
                        Color.Black.copy(alpha = 0.58f)
                    )
                    .clickable {
                        pendingDeleteCard = null
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 34.dp)
                        .fillMaxWidth()
                        .background(
                            SpaceBlueGrayMid,
                            RoundedCornerShape(12.dp)
                        )
                        .border(
                            2.dp,
                            SpaceDeleteRed.copy(alpha = 0.65f),
                            RoundedCornerShape(12.dp)
                        )
                        .clickable { }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = 18.dp,
                                vertical = 13.dp
                            ),
                        horizontalAlignment =
                            Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Delete Conversation",
                            color =
                                Color.White.copy(alpha = 0.94f),
                            fontSize = 17.sp,
                            fontFamily = pixelFont,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(Modifier.height(10.dp))

                        Text(
                            text = "\"" + targetCard.title + "\"",
                            color = SpaceLine,
                            fontSize = 15.sp,
                            fontFamily = pixelFont,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(Modifier.height(8.dp))

                        Text(
                            text =
                                "This conversation's chat history " +
                                    "will be permanently removed from the archive.",
                            color =
                                Color.White.copy(alpha = 0.62f),
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            fontFamily = pixelFont,
                            textAlign = TextAlign.Center
                        )

                        Spacer(Modifier.height(18.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement =
                                Arrangement.spacedBy(12.dp)
                        ) {
                            // 取消：关闭弹窗，什么都不删
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .background(
                                        Color.White.copy(alpha = 0.08f),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .border(
                                        1.dp,
                                        SpaceLine.copy(alpha = 0.55f),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        pendingDeleteCard = null
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Cancel",
                                    color =
                                        Color.White.copy(alpha = 0.88f),
                                    fontSize = 12.sp,
                                    fontFamily = pixelFont,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // 确认删除：真正从存档移除
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .background(
                                        SpaceDeleteRed.copy(alpha = 0.22f),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .border(
                                        1.dp,
                                        SpaceDeleteRed.copy(alpha = 0.85f),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        ConversationStore
                                            .deleteConversation(
                                                targetCard.conversationId
                                            )
                                        pendingDeleteCard = null
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Delete",
                                    color = SpaceDeleteRed,
                                    fontSize = 12.sp,
                                    fontFamily = pixelFont,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationArchiveCard(
    card: ConversationCardRecord,
    isCurrent: Boolean,
    pixelFont: FontFamily,
    onClick: () -> Unit,
    onRequestDelete: () -> Unit,
    // 左滑唤出新画布：卡片自行消费水平拖动(根检测器收不到)，
    // 向左手势在卡片内部消化——仅卡片本身微移，整屏不动，越阈值才唤出
    // 回调携带本卡片编号(conversationId)，供新画布显示
    onOpenCanvas: (String) -> Unit
) {
    // 右滑位移：0 = 原位，越大滑得越远
    val swipeX = remember(card.conversationId) {
        Animatable(0f)
    }
    val cardScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val deleteThresholdPx = with(density) {
        120.dp.toPx()
    }
    val leftThresholdPx = with(density) {
        120.dp.toPx()
    }

    // 滑动进度 0..1：驱动底色显现与透明度
    val progress =
        (swipeX.value / deleteThresholdPx)
            .coerceIn(0f, 1f)

    Box(modifier = Modifier.fillMaxWidth()) {
        // 前景卡片：可右拖拽，越过后松手弹出删除确认
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    translationX = swipeX.value
                    alpha = 1f - 0.3f * progress
                }
                .background(
                    color =
                        if (isCurrent) {
                            SpaceLine.copy(alpha = 0.17f)
                        } else {
                            Color.Black.copy(alpha = 0.16f)
                        },
                    shape = RoundedCornerShape(10.dp)
                )
                .border(
                    width =
                        if (isCurrent) 2.dp else 1.dp,
                    color =
                        if (isCurrent) {
                            SpaceLine.copy(alpha = 0.75f)
                        } else {
                            SpaceLine.copy(alpha = 0.30f)
                        },
                    shape = RoundedCornerShape(10.dp)
                )
                .clickable {
                    onClick()
                }
                .pointerInput(card.conversationId) {
                    // 卡片上的水平拖动由卡片优先处理，按方向分流：
                    //   向右 → 卡片删除滑动(原逻辑不变)
                    //   向左 → 仅卡片本身微移(整屏不动)，越阈值唤出新画布
                    var leftAcc = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { _ ->
                            leftAcc = 0f
                        },
                        onDragEnd = {
                            if (
                                leftAcc <= -leftThresholdPx &&
                                swipeX.value <= 0f
                            ) {
                                // 左滑越阈值：卡片归位并唤出新画布
                                cardScope.launch {
                                    swipeX.animateTo(
                                        0f,
                                        tween(200)
                                    )
                                }
                                onOpenCanvas(card.conversationId)
                            } else if (swipeX.value >= deleteThresholdPx) {
                                // 右滑越阈值：卡片归位并请求删除确认
                                cardScope.launch {
                                    swipeX.animateTo(
                                        0f,
                                        tween(200)
                                    )
                                }
                                onRequestDelete()
                            } else {
                                // 未越过：弹回原位
                                cardScope.launch {
                                    swipeX.animateTo(
                                        0f,
                                        tween(240)
                                    )
                                }
                            }
                        },
                        onDragCancel = {
                            cardScope.launch {
                                swipeX.animateTo(
                                    0f,
                                    tween(200)
                                )
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            if (dragAmount < 0f && swipeX.value <= 0f) {
                                // 向左且未处于删除滑动：卡片级微移，不波及整屏
                                leftAcc += dragAmount
                                cardScope.launch {
                                    val raw =
                                        swipeX.value + dragAmount
                                    val next = when {
                                        raw >= 0f -> 0f
                                        // 越阈值后加阻尼，与右侧手感一致
                                        raw < -leftThresholdPx ->
                                            -leftThresholdPx +
                                                (raw + leftThresholdPx) * 0.3f
                                        else -> raw
                                    }
                                    swipeX.snapTo(next)
                                }
                            } else {
                                cardScope.launch {
                                    val raw =
                                        swipeX.value + dragAmount
                                    val next = when {
                                        // 只允许向右滑(删除方向)
                                        raw <= 0f -> 0f
                                        // 越过阈值后加阻尼，形成"已到位"手感
                                        raw > deleteThresholdPx ->
                                            deleteThresholdPx +
                                                (raw - deleteThresholdPx) * 0.3f
                                        else -> raw
                                    }
                                    swipeX.snapTo(next)
                                }
                            }
                        }
                    )
                }
                .padding(12.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.SpaceBetween,
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {
                    Text(
                        text = card.title,
                        modifier =
                            Modifier.weight(1f),
                        color =
                            Color.White.copy(alpha = 0.94f),
                        fontSize = 13.sp,
                        fontFamily = pixelFont,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(Modifier.width(10.dp))

                    Text(
                        text = card.displayTime,
                        color =
                            Color.White.copy(alpha = 0.42f),
                        fontSize = 9.sp,
                        fontFamily = pixelFont
                    )
                }

                Spacer(Modifier.height(7.dp))

                Text(
                    text =
                        card.preview.ifBlank { uiText(UiText.EmptyConversation) },
                    color =
                        Color.White.copy(alpha = 0.67f),
                    fontSize = 10.sp,
                    lineHeight = 15.sp,
                    fontFamily = pixelFont,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(7.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text =
                            "${card.messageCount} " +
                                uiText(UiText.MessagesSuffix) +
                                if (isCurrent) {
                                    "  ·  " + uiText(UiText.CurrentTag)
                                } else {
                                    ""
                                },
                        color =
                            SpaceLine.copy(alpha = 0.72f),
                        fontSize = 9.sp,
                        fontFamily = pixelFont
                    )

                    Text(
                        text = "ID · " + card.conversationId,
                        color = SpaceLine.copy(alpha = 0.45f),
                        fontSize = 9.sp,
                        fontFamily = pixelFont
                    )
                }
            }
        }
    }
}
