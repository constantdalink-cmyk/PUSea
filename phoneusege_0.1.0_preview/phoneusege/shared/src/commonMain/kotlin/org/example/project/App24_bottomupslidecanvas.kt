package org.example.project

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex

// ==================== 底部上滑画布 ====================
@Composable
fun BottomUpSlideCanvas(
    visible: Boolean,
    onClose: () -> Unit,
    pixelFont: FontFamily
) {
    val slideAnim = remember { Animatable(1f) }
    var rendered by remember { mutableStateOf(false) }
    var searchInput by remember { mutableStateOf("") }

    val capturedFiles by CanvasFileStore.files.collectAsState()
    val captureError by CanvasFileStore.lastError.collectAsState()
    var cmdInput by remember { mutableStateOf("") }
    val cmdBusy by CmdStore.busy.collectAsState()
    val cmdLogs by CmdStore.logs.collectAsState()

    val filteredFiles = remember(capturedFiles, searchInput) {
        if (searchInput.isBlank()) capturedFiles
        else capturedFiles.filter {
            it.name.contains(searchInput, ignoreCase = true) ||
                it.contentPreview.contains(searchInput, ignoreCase = true)
        }
    }

    LaunchedEffect(visible) {
        if (visible) {
            rendered = true
            slideAnim.snapTo(1f)
            slideAnim.animateTo(
                targetValue = 0f,
                animationSpec = tween(380, easing = ElegantGlideEasing)
            )
        } else if (rendered) {
            slideAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(300, easing = PeelOffEasing)
            )
            rendered = false
            searchInput = ""
        }
    }

    if (!rendered) return

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(20f)
            .background(
                Color.Black.copy(alpha = 0.28f * (1f - slideAnim.value))
            ),
        contentAlignment = Alignment.BottomCenter
    ) {
        val panelShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        val glassBoxShape = RoundedCornerShape(10.dp)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(2f / 3f)
                .graphicsLayer {
                    translationY = slideAnim.value * size.height
                }


                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF20242F),
                            Color(0xFF14161E),
                            Color(0xFF0B0C11)
                        )
                    ),
                    shape = panelShape
                )
                .drawWithCache {
                    val topHighlight = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.10f),
                            Color.Transparent
                        ),
                        startY = 0f,
                        endY = 64.dp.toPx()
                    )
                    onDrawWithContent {
                        drawContent()
                        drawRoundRect(
                            brush = topHighlight,
                            topLeft = Offset.Zero,
                            size = size,
                            cornerRadius = CornerRadius(28.dp.toPx(), 28.dp.toPx())
                        )
                    }
                }
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.08f),
                    shape = panelShape
                )
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            // ===== 上半区：拖动条 + Search + 网格文件 =====
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                // 拖动条 (仅此处下滑关闭)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    var accY = 0f
                                    var handled = false
                                    var change: PointerInputChange? = null

                                    do {
                                        val event = awaitPointerEvent()
                                        change = event.changes.firstOrNull { it.id == down.id }
                                        if (change != null && change.pressed) {
                                            accY += change.position.y - change.previousPosition.y
                                            if (accY > 40.dp.toPx()) {
                                                onClose()
                                                handled = true
                                                event.changes.forEach { it.consume() }
                                            }
                                        }
                                    } while (change != null && change.pressed && !handled)
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .height(5.dp)
                            .background(
                                color = Color.White.copy(alpha = 0.32f),
                                shape = RoundedCornerShape(2.5.dp)
                            )
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Search 框（输入路径回车导入）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color(0xFF262B38), Color(0xFF13151C))
                            ),
                            shape = glassBoxShape
                        )
                        .drawWithCache {
                            val hl = Brush.verticalGradient(
                                colors = listOf(Color.White.copy(alpha = 0.08f), Color.Transparent),
                                startY = 0f,
                                endY = 16.dp.toPx()
                            )
                            onDrawWithContent {
                                drawContent()
                                drawRoundRect(
                                    brush = hl,
                                    topLeft = Offset.Zero,
                                    size = size,
                                    cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx())
                                )
                            }
                        }
                        .border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.10f),
                            shape = glassBoxShape
                        )
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Canvas(modifier = Modifier.size(14.dp)) {
                        val sw = 1.6.dp.toPx()
                        val r = size.minDimension * 0.32f
                        val c = Offset(size.width * 0.42f, size.height * 0.42f)
                        drawCircle(
                            color = Color.White.copy(alpha = 0.75f),
                            radius = r,
                            center = c,
                            style = Stroke(width = sw)
                        )
                        drawLine(
                            color = Color.White.copy(alpha = 0.75f),
                            start = Offset(c.x + r * 0.7071f, c.y + r * 0.7071f),
                            end = Offset(size.width * 0.92f, size.height * 0.92f),
                            strokeWidth = sw,
                            cap = StrokeCap.Round
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    BasicTextField(
                        value = searchInput,
                        onValueChange = { searchInput = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = Color.White.copy(alpha = 0.88f),
                            fontSize = 12.sp,
                            fontFamily = pixelFont
                        ),
                        cursorBrush = SolidColor(Color(0xFF1E3A8A)),
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                val path = searchInput.trim()
                                if (path.isNotEmpty()) {
                                    CanvasFileStore.importByPath(path)
                                    searchInput = ""
                                }
                            }
                        ),
                        decorationBox = { inner ->
                            Box {
                                if (searchInput.isEmpty()) {
                                    Text(
                                        text = uiText(UiText.SkillSearchShortPlaceholder),
                                        color = Color.White.copy(alpha = 0.40f),
                                        fontSize = 12.sp,
                                        fontFamily = pixelFont
                                    )
                                }
                                inner()
                            }
                        }
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clickable { CanvasFileStore.capture() },
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(16.dp)) {
                            val sw = 1.4.dp.toPx()
                            val pad = 1.5.dp.toPx()
                            drawRoundRect(
                                color = Color.White.copy(alpha = 0.80f),
                                topLeft = Offset(pad, pad),
                                size = Size(size.width - pad * 2, size.height - pad * 2),
                                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                                style = Stroke(width = sw)
                            )
                            drawCircle(
                                color = Color.White.copy(alpha = 0.90f),
                                radius = 2.2.dp.toPx(),
                                center = Offset(size.width / 2f, size.height / 2f)
                            )
                        }
                    }
                }

                if (captureError != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = captureError ?: "",
                        color = Color(0xFFFF7777),
                        fontSize = 10.sp,
                        fontFamily = pixelFont
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 大方块网格
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    capturedFiles.chunked(2).forEach { rowItems ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            rowItems.forEach { file ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .pointerInput(file.id) {
                                            detectTapGestures(
                                                onDoubleTap = {
                                                    CanvasFileStore.remove(file.id)
                                                }
                                            )
                                        }
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(
                                            brush = Brush.linearGradient(
                                                colors = listOf(
                                                    Color(0xFF262D40),
                                                    Color(0xFF1A1F2E),
                                                    Color(0xFF181D2A)
                                                )
                                            )
                                        )
                                        .drawWithCache {
                                            val topLight = Brush.verticalGradient(
                                                colors = listOf(
                                                    Color.White.copy(alpha = 0.32f),
                                                    Color.White.copy(alpha = 0.10f),
                                                    Color.Transparent
                                                ),
                                                startY = 0f,
                                                endY = 90.dp.toPx()
                                            )

                                            val bottomLight = Brush.verticalGradient(
                                                colors = listOf(
                                                    Color.Transparent,
                                                    Color(0xFF242A3A).copy(alpha = 0.35f)
                                                ),
                                                startY = size.height * 0.65f,
                                                endY = size.height
                                            )

                                            val strokeGradient = Brush.verticalGradient(
                                                colors = listOf(
                                                    Color.White.copy(alpha = 0.18f),
                                                    Color(0xFF3DDC84).copy(alpha = 0.06f),
                                                    Color.White.copy(alpha = 0.06f)
                                                )
                                            )

                                            onDrawWithContent {
                                                drawContent()

                                                drawRoundRect(
                                                    brush = topLight,
                                                    topLeft = Offset.Zero,
                                                    size = size,
                                                    cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx())
                                                )

                                                drawRoundRect(
                                                    brush = bottomLight,
                                                    topLeft = Offset.Zero,
                                                    size = size,
                                                    cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx())
                                                )

                                                drawRoundRect(
                                                    brush = strokeGradient,
                                                    topLeft = Offset(0.5.dp.toPx(), 0.5.dp.toPx()),
                                                    size = Size(
                                                        size.width - 1.dp.toPx(),
                                                        size.height - 1.dp.toPx()
                                                    ),
                                                    cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx()),
                                                    style = Stroke(width = 1.dp.toPx())
                                                )
                                            }
                                        }
                                ) {
                                    if (file.isImage && file.rawBytes != null) {
                                        val bmp = remember(file.id) {
                                            ImageBitmapDecodeStore.decode(file.rawBytes!!)
                                        }
                                        if (bmp != null) {
                                            Image(
                                                bitmap = bmp,
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                            // 左上角信息列（叠在图片上）
                                            Column(
                                                modifier = Modifier
                                                    .align(Alignment.TopStart)
                                                    .padding(start = 8.dp, top = 0.dp, end = 8.dp)

                                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                                            ) {
                                                Text(
                                                    text = displayFileName(file.name),
                                                    color = Color(0xFF888888),
                                                    fontSize = 10.sp,
                                                    fontFamily = pixelFont,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "${file.sizeLabel} · image",
                                                    color = Color(0xFF555555),
                                                    fontSize = 8.sp,
                                                    fontFamily = pixelFont
                                                )
                                                Text(
                                                    text = "Path: ${file.localPath}",
                                                    color = Color.Black,
                                                    fontSize = 7.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        } else {
                                            Text(
                                                text = "Image Fail",
                                                modifier = Modifier.align(Alignment.Center),
                                                color = Color(0xFFFF7777),
                                                fontSize = 10.sp,
                                                fontFamily = pixelFont
                                            )
                                        }
                                    } else {
                                        // === 透明、修长、居中偏上的透明背景图标 ===
                                        Canvas(
                                            modifier = Modifier
                                                .align(Alignment.Center)
                                                .padding(6.dp)
                                                .size(width = 28.dp, height = 40.dp)
                                        ) {
                                            val w = size.width
                                            val h = size.height
                                            val fold = w * 0.35f
                                            val sw = 1.4.dp.toPx()

                                            val body = Path().apply {
                                                moveTo(fold, 0f)
                                                lineTo(w, 0f)
                                                lineTo(w, h)
                                                lineTo(0f, h)
                                                lineTo(0f, fold)
                                                close()
                                            }
                                            val foldTri = Path().apply {
                                                moveTo(0f, fold)
                                                lineTo(fold, fold)
                                                lineTo(fold, 0f)
                                                close()
                                            }

                                            // 透明底，白色低透明度空心线描边
                                            val col = Color.White.copy(alpha = 0.18f)
                                            drawPath(
                                                path = body,
                                                color = col,
                                                style = Stroke(width = sw, cap = StrokeCap.Square, join = StrokeJoin.Miter)
                                            )
                                            drawPath(
                                                path = foldTri,
                                                color = col,
                                                style = Stroke(width = sw, cap = StrokeCap.Square, join = StrokeJoin.Miter)
                                            )

                                            // 内部内容短线
                                            drawLine(color = col, start = Offset(fold * 1.2f, h * 0.35f), end = Offset(w * 0.8f, h * 0.35f), strokeWidth = sw, cap = StrokeCap.Square)
                                            drawLine(color = col, start = Offset(w * 0.2f, h * 0.55f), end = Offset(w * 0.8f, h * 0.55f), strokeWidth = sw, cap = StrokeCap.Square)
                                            drawLine(color = col, start = Offset(w * 0.2f, h * 0.75f), end = Offset(w * 0.8f, h * 0.75f), strokeWidth = sw, cap = StrokeCap.Square)
                                        }

                                        // === 文件名左上角，往下依次排布信息 ===
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(start = 10.dp, top = 0.dp, end = 10.dp, bottom = 10.dp)
                                        ) {
                                            Text(
                                                text = displayFileName(file.name),
                                                color = Color.Black,
                                                fontSize = 11.sp,
                                                fontFamily = pixelFont,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "${file.sizeLabel} · ${if (file.isText) "text" else "binary"}",
                                                color = Color.Black,
                                                fontSize = 9.sp,
                                                fontFamily = pixelFont
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "Path: ${file.localPath}",
                                                color = Color.White,
                                                fontSize = 8.sp,
                                                fontFamily = FontFamily.Monospace,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(0.dp))
                                            Text(
                                                text = maskedPreview(file.contentPreview),
                                                color = Color.White.copy(alpha = 0.78f),
                                                fontSize = 10.sp,
                                                fontFamily = FontFamily.Monospace,
                                                lineHeight = 13.sp,
                                                maxLines = 3,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }


                                }
                            }
                            if (rowItems.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            // ===== 中间 1/2 黑横线 =====
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.Black.copy(alpha = 0.70f))
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ===== 恢复 cmd 框（带背景和边框，纯展示文字） =====
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color(0xFF262B38), Color(0xFF13151C))
                        ),
                        shape = glassBoxShape
                    )
                    .drawWithCache {
                        val hl = Brush.verticalGradient(
                            colors = listOf(Color.White.copy(alpha = 0.08f), Color.Transparent),
                            startY = 0f,
                            endY = 16.dp.toPx()
                        )
                        onDrawWithContent {
                            drawContent()
                            drawRoundRect(
                                brush = hl,
                                topLeft = Offset.Zero,
                                size = size,
                                cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx())
                            )
                        }
                    }
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.10f),
                        shape = glassBoxShape
                    )
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = ">",
                    color = Color(0xFF4ADE80),
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                BasicTextField(
                    value = cmdInput,
                    onValueChange = { cmdInput = it },
                    singleLine = true,
                    enabled = !cmdBusy,
                    textStyle = TextStyle(
                        color = Color.White.copy(alpha = 0.88f),
                        fontSize = 12.sp,
                        fontFamily = pixelFont
                    ),
                    cursorBrush = SolidColor(Color(0xFF4ADE80)),
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            val cmd = cmdInput.trim()
                            if (cmd.isNotEmpty()) {
                                CmdStore.execute(cmd)
                                cmdInput = ""
                            }
                        }
                    ),
                    decorationBox = { inner ->
                        Box {
                            if (cmdInput.isEmpty()) {
                                Text(
                                    text = "cmd",
                                    color = Color.White.copy(alpha = 0.40f),
                                    fontSize = 12.sp,
                                    fontFamily = pixelFont
                                )
                            }
                            inner()
                        }
                    }
                )
            }

            // ===== cmd 输出日志：显示在 cmd 框下方，不再堆在输入框里 =====
            val cmdLogScroll = rememberScrollState()
            LaunchedEffect(cmdLogs.size) {
                if (cmdLogs.isNotEmpty()) {
                    cmdLogScroll.animateScrollTo(cmdLogScroll.maxValue)
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 10.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .verticalScroll(cmdLogScroll)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color(0xFF1B202B), Color(0xFF10131A))
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.07f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    if (cmdLogs.isEmpty()) {
                        Text(
                            text = "cmd output 显示在这里",
                            color = Color.White.copy(alpha = 0.22f),
                            fontSize = 10.sp,
                            fontFamily = pixelFont
                        )
                    } else {
                        cmdLogs.forEach { line ->
                            Text(
                                text = line,
                                color = Color(0xFF4ADE80),
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 14.sp,
                                modifier = Modifier.padding(bottom = 3.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
// ==================== 文件名字幕辅助 ====================

private fun displayFileName(raw: String): String = raw

private val DisplayUuidRegex =
    Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")

private fun maskUuidForDisplay(raw: String): String {
    return DisplayUuidRegex.replace(raw) { match ->
        match.value.map { ch ->
            if (ch == '-') '-' else '•'
        }.joinToString("")
    }
}
private val UUID_REGEX = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")

private fun maskedPreview(raw: String): String =
    UUID_REGEX.replace(raw, "").trim()
