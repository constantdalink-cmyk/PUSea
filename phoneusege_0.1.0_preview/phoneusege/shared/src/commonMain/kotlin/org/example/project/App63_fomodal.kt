package org.example.project

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex

// ==============================================================================
// 1. 平台文件管理器选图桥接 (SkillImagePicker)
//    commonMain 只持有回调句柄；androidMain / iosMain 在启动时注入真实实现。
// ==============================================================================

object SkillImagePicker {
    /**
     * 由平台层注入：拉起系统文件管理器/相册。
     * 平台侧（androidMain）在拿到结果后调用 SkillImagePicker.notifyPicked(path) 回传。
     */
    var handler: ((onPicked: (String) -> Unit) -> Unit)? = null

    private var pending: ((String) -> Unit)? = null

    fun pick(onPicked: (String) -> Unit) {
        val impl = handler
        if (impl == null) {
            onPicked("")
            return
        }
        // 记录本次回调，等待系统选图完成后由 notifyPicked 派发
        pending = onPicked
        impl.invoke { path ->
            val target = pending ?: onPicked
            pending = null
            target(path)
        }
    }

    /** 平台层（launcher 回调等异步场景）使用：把选中的 uri/路径派发给最近的请求者 */
    fun notifyPicked(path: String) {
        val target = pending
        pending = null
        if (target != null) target(path)
    }

    /** 取消等待（弹窗被关闭时调用，避免回调串到别的卡片） */
    fun cancelPending() {
        pending = null
    }

    val isAvailable: Boolean
        get() = handler != null
}

// ==============================================================================
// 1b. 本地图片解码桥接 (SkillLocalImageLoader)
//     commonMain 只留句柄；androidMain 注入基于 ContentResolver 的实现，
//     使 content:// / 绝对路径 的本地图片也能被渲染。
// ==============================================================================

object SkillLocalImageLoader {
    var loader: ((path: String) -> androidx.compose.ui.graphics.ImageBitmap?)? = null

    /** 非 http(s) 的路径才尝试本地解码 */
    fun looksLocal(path: String): Boolean =
        path.isNotBlank() && !path.startsWith("http://", true) && !path.startsWith("https://", true)

    suspend fun load(path: String): androidx.compose.ui.graphics.ImageBitmap? {
        val impl = loader ?: return null
        // 使用 Dispatchers.Default 而非 IO：commonMain 元数据编译下 IO 不可用，
        // 且位图解码为 CPU/IO 混合负载，Default 调度器足够且跨平台安全。
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            runCatching { impl(path) }.getOrNull()
        }
    }
}

// ==============================================================================
// 2. 调试兜底：图片路径/网址手动输入框 (FallbackImagePickerDialog)
//    当系统/平台层未注入真实文件管理器时，弹出此输入框供开发与实测人员输入自定义封面
// ==============================================================================

@Composable
fun FallbackImagePickerDialog(
    visible: Boolean,
    pixelFont: FontFamily,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    if (!visible) return
    // 以 visible 取反为 key：每次关闭后重置，避免下次打开残留上一次输入
    var textInput by remember(!visible) { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onDismiss() }
            .zIndex(400f),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(280.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White)
                .border(2.dp, Color(0xFF181717), RoundedCornerShape(16.dp))
                .clickable(enabled = false) {}
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "🖼️ 自定义图片路径或网址",
                fontFamily = pixelFont,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF111111)
            )
            Spacer(modifier = Modifier.height(10.dp))
            BasicTextField(
                value = textInput,
                onValueChange = { textInput = it },
                textStyle = TextStyle(
                    fontFamily = pixelFont,
                    fontSize = 11.sp,
                    color = Color(0xFF111111)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .background(Color(0xFFF7F7F7), RoundedCornerShape(8.dp))
                    .border(1.dp, Color(0xFFD0D0D0), RoundedCornerShape(8.dp))
                    .padding(8.dp),
                decorationBox = { inner ->
                    Box(modifier = Modifier.fillMaxWidth()) {
                        if (textInput.isEmpty()) {
                            Text(
                                text = "例如: E:/my_icon.png 或 https://...",
                                fontFamily = pixelFont,
                                fontSize = 11.sp,
                                color = Color(0xFFBBBBBB)
                            )
                        }
                        inner()
                    }
                }
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFEEEEEE))
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "取消",
                        fontFamily = pixelFont,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF555555)
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF181717))
                        .clickable {
                            onConfirm(textInput.trim())
                            onDismiss()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "确定",
                        fontFamily = pixelFont,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

// ==============================================================================
// 3. 技能信息弹窗 (SkillInfoModalBox)
// ==============================================================================

@Composable
fun SkillInfoModalBox(
    visible: Boolean,
    card: SkillCardItemData?,
    pixelFont: FontFamily = FontFamily.Default,
    onDismiss: () -> Unit,
    onNameChanged: (String) -> Unit = {},
    onDetailChanged: (String) -> Unit = {},
    onImagePicked: (String) -> Unit = {}
) {
    var showFallbackPicker by remember { mutableStateOf(false) }

    // 草稿态提升到函数作用域：切换卡片时才重建，避免输入被重组打断，
    // 同时让下方兜底输入框（位于 AnimatedVisibility 之外）也能写回 draftImageUrl
    val draftCardId = card?.id ?: ""
    var draftName by remember(draftCardId) { mutableStateOf(card?.name ?: "") }
    var draftDetail by remember(draftCardId) { mutableStateOf(card?.detail ?: "") }
    var draftImageUrl by remember(draftCardId) { mutableStateOf(card?.imageUrl ?: "") }

    AnimatedVisibility(
        visible = visible && card != null,
        enter = fadeIn(animationSpec = tween(280, easing = CubicBezierEasing(0.25f, 1f, 0.5f, 1f))),
        exit = fadeOut(animationSpec = tween(220))
    ) {
        val activeCard = card ?: return@AnimatedVisibility
        val focusManager = LocalFocusManager.current

        // 图片爬虫状态（本地路径或网络地址都走同一条管线）
        var imageStatus by remember(activeCard.id, draftImageUrl) {
            mutableStateOf<ImageCrawlStatus>(ImageCrawlStatus.Idle)
        }
        var manualRetryTrigger by remember(activeCard.id) { mutableIntStateOf(0) }

        LaunchedEffect(draftImageUrl, draftName, draftDetail, activeCard.id, manualRetryTrigger) {
            val fetchUrl = when {
                draftImageUrl.isNotBlank() -> draftImageUrl
                activeCard.id.startsWith("res_blank_") -> ""
                else -> DownloadedResourceStore.resolveImageUrlForCard(
                    draftName.ifBlank { activeCard.name },
                    draftDetail.ifBlank { activeCard.detail }
                )
            }
            if (fetchUrl.isNotBlank()) {
                SkillImageCrawlEngine.fetchOrRecrawlImage(fetchUrl) { newStatus ->
                    imageStatus = newStatus
                }
            } else {
                imageStatus = ImageCrawlStatus.Idle
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x8A000000))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    focusManager.clearFocus()
                    onDismiss()
                }
                .zIndex(300f),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(264.dp)
                    .height(440.dp)
                    .clickable(enabled = false) {} // 阻断点击穿透
                    .shadow(16.dp, RoundedCornerShape(22.dp))
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color.White)
                    .border(2.dp, Color(0xFF181717), RoundedCornerShape(22.dp))
                    .padding(horizontal = 18.dp, vertical = 16.dp)
            ) {
                // ===== 顶部居中：圆形 + 斜 45° 加号（取消按钮）=====
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 4.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFF0F0F0))
                        .border(1.5.dp, Color(0xFF222222), CircleShape)
                        .clickable {
                            focusManager.clearFocus()
                            onDismiss()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(
                        modifier = Modifier
                            .size(13.dp)
                            .rotate(45f)
                    ) {
                        val strokeW = 2.2f.dp.toPx()
                        val pColor = Color(0xFF181717)
                        drawLine(
                            color = pColor,
                            start = Offset(0f, size.height / 2f),
                            end = Offset(size.width, size.height / 2f),
                            strokeWidth = strokeW,
                            cap = StrokeCap.Round
                        )
                        drawLine(
                            color = pColor,
                            start = Offset(size.width / 2f, 0f),
                            end = Offset(size.width / 2f, size.height),
                            strokeWidth = strokeW,
                            cap = StrokeCap.Round
                        )
                    }
                }

                // ===== 主体内容（无下载图标，底部不再预留按钮空间）=====
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 40.dp, bottom = 4.dp)
                        .imePadding(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // ---- 34dp 图片预留框：点击调用系统文件管理器，未注入时调用路径/网址输入弹框 📁 ----
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .shadow(2.dp, RoundedCornerShape(10.dp))
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFF4F4F4))
                            .border(1.5.dp, Color(0xFF222222), RoundedCornerShape(10.dp))
                            .clickable {
                                if (SkillImagePicker.isAvailable) {
                                    SkillImagePicker.pick { pickedPath ->
                                        // 用户在系统选图器里返回（取消）时路径为空，保持原图不动
                                        if (pickedPath.isNotBlank()) {
                                            draftImageUrl = pickedPath
                                            onImagePicked(pickedPath)
                                        }
                                    }
                                } else {
                                    // 平台层未注入时，淡入弹出输入路径的 dialog 调试
                                    showFallbackPicker = true
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        when (val st = imageStatus) {
                            is ImageCrawlStatus.Fetching, is ImageCrawlStatus.Retrying -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = Color(0xFF181717)
                                )
                            }
                            is ImageCrawlStatus.Success -> {
                                Image(
                                    bitmap = st.bitmap,
                                    contentDescription = draftName,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            is ImageCrawlStatus.Failed, ImageCrawlStatus.Idle -> {
                                // 空白态：手绘"添加图片"占位（山形 + 加号）
                                Canvas(modifier = Modifier.size(18.dp)) {
                                    val w = size.width
                                    val h = size.height
                                    val strokeW = 1.6f.dp.toPx()
                                    val pColor = Color(0xFF9A9A9A)
                                    drawRoundRect(
                                        color = pColor,
                                        topLeft = Offset(w * 0.06f, h * 0.14f),
                                        size = androidx.compose.ui.geometry.Size(w * 0.88f, h * 0.72f),
                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                                            2.dp.toPx(), 2.dp.toPx()
                                        ),
                                        style = Stroke(width = strokeW)
                                    )
                                    val mountain = Path().apply {
                                        moveTo(w * 0.12f, h * 0.76f)
                                        lineTo(w * 0.38f, h * 0.44f)
                                        lineTo(w * 0.56f, h * 0.64f)
                                        lineTo(w * 0.70f, h * 0.50f)
                                        lineTo(w * 0.88f, h * 0.76f)
                                    }
                                    drawPath(mountain, color = pColor, style = Stroke(width = strokeW))
                                    drawCircle(
                                        color = pColor,
                                        radius = w * 0.07f,
                                        center = Offset(w * 0.30f, h * 0.30f)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // ---- 名称（可编辑）----
                    BasicTextField(
                        value = draftName,
                        onValueChange = {
                            draftName = it
                            onNameChanged(it)
                        },
                        singleLine = true,
                        textStyle = TextStyle(
                            fontFamily = pixelFont,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF111111),
                            textAlign = TextAlign.Center
                        ),
                        cursorBrush = SolidColor(Color(0xFF181717)),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp),
                        decorationBox = { inner ->
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                if (draftName.isEmpty()) {
                                    Text(
                                        text = "Name",
                                        fontFamily = pixelFont,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFBBBBBB),
                                        textAlign = TextAlign.Center
                                    )
                                }
                                inner()
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // 分割装饰线
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .height(2.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(Color(0xFF222222))
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // ---- 详情（可编辑 + 可滑动，避免过多导致省略）----
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFF9F9F9))
                            .border(1.dp, Color(0xFFE8E8E8), RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                        ) {
                            BasicTextField(
                                value = draftDetail,
                                onValueChange = {
                                    draftDetail = it
                                    onDetailChanged(it)
                                },
                                textStyle = TextStyle(
                                    fontFamily = pixelFont,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = Color(0xFF2C2C2C),
                                    lineHeight = 16.sp
                                ),
                                cursorBrush = SolidColor(Color(0xFF181717)),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                                keyboardActions = KeyboardActions(onDone = {
                                    focusManager.clearFocus()
                                }),
                                modifier = Modifier.fillMaxWidth(),
                                decorationBox = { inner ->
                                    Box(modifier = Modifier.fillMaxWidth()) {
                                        if (draftDetail.isEmpty()) {
                                            Text(
                                                text = "Details…",
                                                fontFamily = pixelFont,
                                                fontSize = 11.5.sp,
                                                color = Color(0xFFBBBBBB)
                                            )
                                        }
                                        inner()
                                    }
                                }
                            )
                        }
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
            // 同时写回草稿态，弹窗内立刻反映新封面（无需等外部 store 重组）
            draftImageUrl = pickedPath
            onImagePicked(pickedPath)
        }
    )
}
