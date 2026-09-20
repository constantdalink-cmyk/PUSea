// ============ App57 Skill Management 【2/3】右侧滑入全屏详情画布 ============
package org.example.project

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// ==============================================================================
// 7. 【前端 UI 组件层】右侧滑入全屏详情画布 (SkillDetailCanvasView)
//    依赖 1/3 中的 Text / SkillCardItemData / SkillResourceImageView (同包直接可见)
//
// 【本地化记录】原文件中 7 处硬编码字幕（"注入 AI"/"已注入 AI"/"简介"/"正在抓取 SKILL.md…"/
//   "改动下一条消息生效"/"SKILL.md 指令…（留空则注入时自动抓取原文）"/"Tap to write…"）
//   已全部改为 uiText(UiText.SkillDetailXxx)，走 AppLanguage 七语词典，
//   切换语言后无需改本文件即可即时生效（详情画布每次进入都重新取词）。
// ==============================================================================

@Composable
fun SkillDetailCanvasView(
    visible: Boolean,
    cardId: String,
    title: String,
    color: Color,
    screenWidth: androidx.compose.ui.unit.Dp,
    detailOffsetAnim: Animatable<Float, AnimationVector1D>,
    pixelFont: FontFamily,
    onDismiss: () -> Unit,
    onContentEdited: (id: String, newName: String, newDetail: String, newImageUrl: String) -> Unit = { _, _, _, _ -> }
) {
    val coroutineScope = rememberCoroutineScope()
    // 右画布跟随卡片行末端"那一抹颜色"同款兜底：透明/未指定色回落到默认蓝，
    // 保证从末端滑出的画布颜色永远与末端保持一致，且颜色变化时跟着渐变
    val safeCanvasColor = if (color.alpha <= 0.01f) Color(0xFF4D96FF) else color
    val animatedDetailCanvasColor by animateColorAsState(
        targetValue = safeCanvasColor,
        animationSpec = tween(250, easing = FastOutSlowInEasing)
    )

    var showFallbackPicker by remember { mutableStateOf(false) }

    // 定位当前画布对应的真实资源卡片（优先按 id 精确命中，退回按名称匹配）
    val sourceCard = DownloadedResourceStore.downloadedCards.firstOrNull { it.id == cardId }
        ?: DownloadedResourceStore.downloadedCards.firstOrNull { it.name.equals(title, ignoreCase = true) }

    val editingId = sourceCard?.id ?: cardId

    // 编辑草稿：仅在切换资源时重新拉取，保证输入过程不被外部重组打断
    var draftName by remember(editingId) { mutableStateOf(sourceCard?.name ?: title) }
    var draftDetail by remember(editingId) { mutableStateOf(sourceCard?.detail ?: "") }
    var draftImageUrl by remember(editingId) { mutableStateOf(sourceCard?.imageUrl ?: "") }

    val currentCard = (sourceCard ?: SkillCardItemData(
        id = editingId,
        name = title,
        detail = "",
        color = color
    )).copy(
        name = draftName,
        detail = draftDetail,
        color = color,
        imageUrl = draftImageUrl.ifBlank {
            (sourceCard?.imageUrl ?: "").ifBlank {
                DownloadedResourceStore.resolveImageUrlForCard(draftName, draftDetail)
            }
        }
    )

    val nameFocusRequester = remember { FocusRequester() }
    var nameFocused by remember(editingId) { mutableStateOf(false) }
    var isEditingBody by remember(editingId) { mutableStateOf(false) }
    val bodyFocusRequester = remember { FocusRequester() }
    val bodyScrollState = rememberScrollState()
    val focusManagerForDetail = LocalFocusManager.current
    val accentWhite = Color.White

    // ===== AI 注入激活状态（末块拼图：SkillActivationBridge 把卡片接进 SkillStore 注入管道）=====
    val injectedSkills by SkillStore.skills.collectAsState()
    val injectedSkill = sourceCard?.let { sc ->
        injectedSkills.firstOrNull { s ->
            s.name == SkillActivationBridge.normalizeName(sc.name) ||
                    s.displayName.equals(sc.name, ignoreCase = true)
        }
    }
    val isInjected = injectedSkill?.isEnabled == true
    var isInjecting by remember(cardId) { mutableStateOf(false) }
    var injectError by remember(cardId) { mutableStateOf<String?>(null) }

    // ===== SKILL.md 编辑状态：右画布可直接修改 skills.md =====
    // 初值优先级：已注入的提示词 > 卡片 customMd；注入成功后抓回的正文会回写卡片并刷新此值
    var isMdMode by remember(cardId) { mutableStateOf(false) }
    var mdDraft by remember(cardId) {
        mutableStateOf(injectedSkill?.systemPrompt ?: sourceCard?.customMd ?: "")
    }

    // SKILL.md 编辑回写：持久化进卡片；若已注入则同步刷新注入提示词（下一条消息生效）
    fun commitMd(newMd: String) {
        mdDraft = newMd
        DownloadedResourceStore.updateCardMd(editingId, newMd)
        injectedSkill?.let { SkillStore.updateSkill(it.copy(systemPrompt = newMd)) }
    }

    // SKILL.md 打开即取兜底：卡片无正文且未注入时后台抓真实内容——
    // 不必点"注入 AI"右画布就能看到 skills 正文（下载预取为主路径，这里兜历史卡/秒开画布的边缘情况）
    var isMdFetching by remember(cardId) { mutableStateOf(false) }
    LaunchedEffect(cardId) {
        val target = sourceCard ?: return@LaunchedEffect
        if (injectedSkill != null) return@LaunchedEffect
        if (target.customMd.isNotBlank()) {
            mdDraft = target.customMd
            return@LaunchedEffect
        }
        isMdFetching = true
        val body = SkillActivationBridge.ensureSkillMd(target)
        isMdFetching = false
        if (body != null) mdDraft = body
    }

    // ===== 本画布全部用户可见字幕：统一经 uiText() 取七语词典，缺键时兜底英文/键名 =====
    val labelInjectAi = uiText(UiText.SkillDetailInjectAi)
    val labelInjectedAi = uiText(UiText.SkillDetailInjectedAi)
    val labelTabIntro = uiText(UiText.SkillDetailTabIntro)
    val labelFetchingMd = uiText(UiText.SkillDetailFetchingMd)
    val labelMdEditHint = uiText(UiText.SkillDetailMdEditHint)
    val labelMdPlaceholder = uiText(UiText.SkillDetailMdPlaceholder)
    val labelTapToWrite = uiText(UiText.SkillDetailTapToWrite)

    if (visible || detailOffsetAnim.value < 1f) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset((detailOffsetAnim.value * screenWidth.toPx()).roundToInt(), 0) }
                .background(animatedDetailCanvasColor)
                .pointerInput(Unit) {
                    var totalDragX = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { totalDragX = 0f },
                        onDragEnd = {
                            coroutineScope.launch {
                                if (totalDragX > 50f) {
                                    // 先把滑出动画完整播完，再卸载画布。
                                    // 若先 onDismiss()，visible 立刻变 false 且 offset 已在 1f，
                                    // 渲染条件两项皆假 → 画布瞬间消失，滑出动画一帧都播不出来。
                                    detailOffsetAnim.animateTo(
                                        1f,
                                        animationSpec = tween(280, easing = CubicBezierEasing(0.25f, 0.46f, 0.45f, 0.94f))
                                    )
                                    onDismiss()
                                } else {
                                    detailOffsetAnim.animateTo(0f, animationSpec = tween(200))
                                }
                            }
                        },
                        onDragCancel = {
                            coroutineScope.launch {
                                detailOffsetAnim.animateTo(0f, animationSpec = tween(200))
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            totalDragX += dragAmount
                            val widthPx = screenWidth.toPx()
                            if (widthPx > 0f) {
                                // 上限压到 0.98f：留住动画余量，避免拖到正好 1f 后
                                // animateTo(1f) 因"当前值==目标值"而瞬间返回
                                val newFraction = (totalDragX / widthPx).coerceIn(0f, 0.98f)
                                coroutineScope.launch {
                                    detailOffsetAnim.snapTo(newFraction)
                                }
                            }
                        }
                    )
                }
                .padding(horizontal = 22.dp)
                .padding(top = 46.dp, bottom = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding(),
                horizontalAlignment = Alignment.Start
            ) {
                // ===== 顶部：资源徽标图 + 可编辑名称 + 关闭按钮（无任何说明字幕） =====
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 爬虫抓取的该 skills 对应真实资源图片 —— 点击可使用文件管理器/输入框更换图片
                    Box(
                        modifier = Modifier
                            .size(66.dp)
                            .shadow(10.dp, RoundedCornerShape(18.dp))
                            .clip(RoundedCornerShape(18.dp))
                            .background(accentWhite)
                            .border(2.dp, accentWhite, RoundedCornerShape(18.dp))
                            .clickable {
                                // 点击调用文件管理器选图API，选中后回写真实资源池
                                if (SkillImagePicker.isAvailable) {
                                    SkillImagePicker.pick { pickedPath ->
                                        // 取消返回时路径为空，保持原封面不动
                                        if (pickedPath.isNotBlank()) {
                                            draftImageUrl = pickedPath
                                            onContentEdited(editingId, draftName, draftDetail, pickedPath)
                                        }
                                    }
                                } else {
                                    // 无平台选图器时，触发通用输入弹框作为调试兜底
                                    showFallbackPicker = true
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        SkillResourceImageView(
                            card = currentCard,
                            modifier = Modifier.fillMaxSize(),
                            iconSize = 34.dp
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    // skills 名称：可直接点击编辑
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (nameFocused) Color.White.copy(alpha = 0.18f) else Color.Transparent
                            )
                            .padding(vertical = 4.dp, horizontal = 2.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        BasicTextField(
                            value = draftName,
                            onValueChange = { newText ->
                                draftName = newText
                                onContentEdited(editingId, newText, draftDetail, draftImageUrl)
                            },
                            singleLine = true,
                            textStyle = TextStyle(
                                fontFamily = pixelFont,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold,
                                color = accentWhite,
                                letterSpacing = 0.2.sp
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(nameFocusRequester)
                                .onFocusChanged { nameFocused = it.isFocused }
                        )
                    }

                    // 圆形关闭按钮（斜加号），非字幕
                    Box(
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.22f))
                            .border(1.5.dp, accentWhite.copy(alpha = 0.7f), CircleShape)
                            .clickable {
                                isEditingBody = false
                                focusManagerForDetail.clearFocus()
                                coroutineScope.launch {
                                    // 同样先滑出再卸载，保证关闭动画完整可见
                                    detailOffsetAnim.animateTo(
                                        1f,
                                        animationSpec = tween(280, easing = CubicBezierEasing(0.25f, 0.46f, 0.45f, 0.94f))
                                    )
                                    onDismiss()
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(12.dp).rotate(45f)) {
                            val s = 2f.dp.toPx()
                            drawLine(accentWhite, Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), strokeWidth = s, cap = StrokeCap.Round)
                            drawLine(accentWhite, Offset(size.width / 2f, 0f), Offset(size.width / 2f, size.height), strokeWidth = s, cap = StrokeCap.Round)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // ===== AI 注入激活栏：抓 SKILL.md 正文 → 生成 AiSkill → 走现成管道注入对话 =====
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.White.copy(alpha = if (isInjected) 0.92f else 0.16f))
                            .border(
                                1.5.dp,
                                if (isInjected) Color.White else Color.White.copy(alpha = 0.5f),
                                RoundedCornerShape(20.dp)
                            )
                            .clickable(enabled = !isInjecting && sourceCard != null) {
                                val targetCard = sourceCard ?: return@clickable
                                coroutineScope.launch {
                                    injectError = null
                                    if (isInjected) {
                                        // 已注入 → 关开关（保留条目，再开免重抓）
                                        SkillActivationBridge.deactivate(targetCard)
                                    } else {
                                        isInjecting = true
                                        injectError = SkillActivationBridge.activate(targetCard)
                                        isInjecting = false
                                        if (injectError == null) {
                                            // 抓取的真实正文已回写卡片：刷新编辑草稿，SKILL.md 页直接可见可改
                                            mdDraft = DownloadedResourceStore.downloadedCards
                                                .firstOrNull { it.id == targetCard.id }
                                                ?.customMd?.takeIf { it.isNotBlank() } ?: mdDraft
                                        }
                                    }
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isInjecting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = accentWhite
                            )
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isInjected) Color(0xFF2ECC71)
                                            else Color.White.copy(alpha = 0.55f)
                                        )
                                        .border(
                                            1.dp,
                                            if (isInjected) Color(0xFF27AE60)
                                            else Color.White.copy(alpha = 0.8f),
                                            CircleShape
                                        )
                                )
                                Spacer(modifier = Modifier.width(7.dp))
                                Text(
                                    // 【已本地化】原硬编码 "已注入 AI" / "注入 AI"
                                    text = if (isInjected) labelInjectedAi else labelInjectAi,
                                    fontFamily = pixelFont,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isInjected) Color(0xFF1A6B45) else accentWhite
                                )
                            }
                        }
                    }

                    // 抓取/解析失败时的短提示（右侧跟随，不占额外行高）
                    if (injectError != null) {
                        Text(
                            text = injectError!!,
                            fontFamily = pixelFont,
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.85f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 10.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ===== 编辑模式切换：简介 / SKILL.md（SKILL.md 才是真正注入 AI 的提示词）=====
                Row(verticalAlignment = Alignment.CenterVertically) {
                    listOf(labelTabIntro to false, "SKILL.md" to true).forEach { (label, toMd) ->
                        val selected = isMdMode == toMd
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White.copy(alpha = if (selected) 0.92f else 0.14f))
                                .border(
                                    1.dp,
                                    if (selected) Color.White else Color.White.copy(alpha = 0.45f),
                                    RoundedCornerShape(14.dp)
                                )
                                .clickable {
                                    isMdMode = toMd
                                    isEditingBody = false
                                    focusManagerForDetail.clearFocus()
                                }
                                .padding(horizontal = 12.dp, vertical = 5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                // 【已本地化】"简介" 经 uiText 取词；"SKILL.md" 为文件名保持原样
                                text = label,
                                fontFamily = pixelFont,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) Color(0xFF222222) else accentWhite
                            )
                        }
                        if (!toMd) Spacer(modifier = Modifier.width(8.dp))
                    }
                    if (isMdFetching) {
                        Text(
                            // 【已本地化】原硬编码 "正在抓取 SKILL.md…"
                            text = labelFetchingMd,
                            fontFamily = pixelFont,
                            fontSize = 9.sp,
                            color = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.padding(start = 10.dp)
                        )
                    } else if (isMdMode && isInjected) {
                        Text(
                            // 【已本地化】原硬编码 "改动下一条消息生效"
                            text = labelMdEditHint,
                            fontFamily = pixelFont,
                            fontSize = 9.sp,
                            color = Color.White.copy(alpha = 0.75f),
                            modifier = Modifier.padding(start = 10.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // ===== 可编辑内容区：简介 或 SKILL.md 正文（随上方模式切换）=====
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = if (isEditingBody) 0.20f else 0.13f))
                        .border(
                            1.dp,
                            if (isEditingBody) accentWhite else Color.White.copy(alpha = 0.3f),
                            RoundedCornerShape(16.dp)
                        )
                        .clickable {
                            isEditingBody = true
                            runCatching { bodyFocusRequester.requestFocus() }
                        }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(bodyScrollState)
                            .padding(14.dp)
                    ) {
                        BasicTextField(
                            value = if (isMdMode) mdDraft else draftDetail,
                            onValueChange = { newText ->
                                if (isMdMode) {
                                    commitMd(newText)
                                } else {
                                    draftDetail = newText
                                    onContentEdited(editingId, draftName, newText, draftImageUrl)
                                }
                            },
                            textStyle = TextStyle(
                                fontFamily = pixelFont,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.W500,
                                color = accentWhite,
                                lineHeight = 20.sp
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                isEditingBody = false
                                focusManagerForDetail.clearFocus()
                            }),
                            cursorBrush = SolidColor(accentWhite),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(bodyFocusRequester)
                                .onFocusChanged { isEditingBody = it.isFocused },
                            decorationBox = { innerTextField ->
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    val isEmpty = if (isMdMode) mdDraft.isEmpty() else draftDetail.isEmpty()
                                    if (isEmpty && !isEditingBody) {
                                        Text(
                                            // 【已本地化】原硬编码 "SKILL.md 指令…（留空则注入时自动抓取原文）" / "Tap to write…"
                                            text = if (isMdMode) labelMdPlaceholder else labelTapToWrite,
                                            fontFamily = pixelFont,
                                            fontSize = 13.sp,
                                            color = Color.White.copy(alpha = 0.5f)
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // 调试输入框
    FallbackImagePickerDialog(
        visible = showFallbackPicker,
        pixelFont = pixelFont,
        onDismiss = { showFallbackPicker = false },
        onConfirm = { pickedPath ->
            draftImageUrl = pickedPath
            onContentEdited(editingId, draftName, draftDetail, pickedPath)
        }
    )
}
