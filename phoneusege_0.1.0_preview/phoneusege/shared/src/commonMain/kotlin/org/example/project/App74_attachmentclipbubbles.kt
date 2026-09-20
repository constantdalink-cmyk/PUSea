package org.example.project

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * 附件快照：发送瞬间把 CanvasFileStore 网格里的文件拍进消息气泡。
 * 网格之后增删文件不会改写历史 —— 气泡记录的是"当时发了什么"。
 */
data class AttachedFileInfo(
    val name: String,
    val sizeLabel: String,
    val isImage: Boolean,
    val rawBytes: ByteArray? = null
)

/**
 * 发送气泡下方的附件条：一排"被回形针夹住的薄纸条"。
 *
 * - 薄气泡：灰白填充 + 深灰白边框，内里 = 手绘图标 + 文件名
 *   （名字太长省略中间，后缀永不省略："hvciyqvbb....png"）
 * - 左侧手绘深黑灰回形针横跨边框，一半在外一半在内 = "勾着"
 * - landed=true（消息落定）后逐个从上方咔哒夹入；instant=true（历史恢复）直接就位
 * - 点击气泡：从页面中心弹出 —— 图片弹真图，文件弹大号手绘图标，
 *   下方一律标注完整文件名/后缀
 * - 空列表不渲染任何东西
 */
@Composable
fun AttachmentClipBubbles(
    attachments: List<AttachedFileInfo>,
    pixelFont: FontFamily,
    landed: Boolean,
    instant: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (attachments.isEmpty()) return

    var popupFile by remember { mutableStateOf<AttachedFileInfo?>(null) }

    Column(
        modifier = modifier
            .padding(start = 8.dp), // 给回形针钩出边框的那一半留出画面
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        attachments.forEachIndexed { index, file ->
            ClipBubble(
                file = file,
                pixelFont = pixelFont,
                landed = landed,
                instant = instant,
                staggerIndex = index,
                onTap = { popupFile = file }
            )
        }
    }

    popupFile?.let { file ->
        FilePopup(file = file, pixelFont = pixelFont, onDismiss = { popupFile = null })
    }
}

// ==================== 单个"被夹住的纸条" ====================

@Composable
private fun ClipBubble(
    file: AttachedFileInfo,
    pixelFont: FontFamily,
    landed: Boolean,
    instant: Boolean,
    staggerIndex: Int,
    onTap: () -> Unit
) {
    val density = LocalDensity.current
    val dropPx = with(density) { 8.dp.toPx() }

    // 夹入动画：0→1（透明度 + 从上方 8dp 落下 + 夹子角度回正）
    val appear = remember { Animatable(0f) }
    LaunchedEffect(landed, instant) {
        if (instant) {
            appear.snapTo(1f)
            return@LaunchedEffect
        }
        if (landed && appear.value == 0f) {
            appear.animateTo(
                1f,
                tween(
                    durationMillis = 260,
                    delayMillis = staggerIndex * 70,
                    easing = FastOutSlowInEasing
                )
            )
        }
    }

    // 按压微反馈：按下去纸条轻微陷进去
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.955f else 1f,
        animationSpec = tween(120, easing = FastOutSlowInEasing),
        label = "clipPressScale"
    )

    Box(
        modifier = Modifier.graphicsLayer {
            alpha = appear.value
            translationY = (1f - appear.value) * -dropPx
            // 新版 Compose 移除了 GraphicsLayerScope.scale，拆成 X/Y
            scaleX = (0.92f + 0.08f * appear.value) * pressScale
            scaleY = (0.92f + 0.08f * appear.value) * pressScale
        }
    ) {
        // ===== 薄气泡本体 =====
        Row(
            modifier = Modifier
                .height(32.dp)
                .widthIn(max = 220.dp)
                .background(Color(0xFF3B3C43), RoundedCornerShape(10.dp))
                .border(1.dp, Color(0xFF6E6F78), RoundedCornerShape(10.dp))
                .padding(start = 13.dp, end = 10.dp)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onTap
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 手绘图标：图片 = 相框+山+太阳；文件 = 折角纸+内容线
            if (file.isImage) {
                HandDrawnPictureIcon()
            } else {
                HandDrawnFileIcon()
            }
            Spacer(modifier = Modifier.size(7.dp))
            Text(
                text = ellipsizeKeepingExtension(file.name),
                color = Color(0xFFD4D5DB),
                fontSize = 10.sp,
                fontFamily = pixelFont,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
        }

        // ===== 手绘回形针：横跨左边框，勾住气泡 =====
        Canvas(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = (-6).dp)
                .size(width = 12.dp, height = 20.dp)
                .graphicsLayer {
                    // 从 -14° 回正到 -8°：落下来时带着一点"咔哒"歪劲
                    rotationZ = -14f + appear.value * 6f
                }
        ) {
            val w = size.width
            val h = size.height
            val clipPath = Path().apply {
                // 外圈：右臂下 → 底 U → 左臂上 → 顶 U
                moveTo(w * 0.76f, h * 0.30f)
                lineTo(w * 0.76f, h * 0.80f)
                cubicTo(w * 0.76f, h * 0.96f, w * 0.24f, h * 0.96f, w * 0.24f, h * 0.80f)
                lineTo(w * 0.24f, h * 0.20f)
                cubicTo(w * 0.24f, h * 0.04f, w * 0.64f, h * 0.04f, w * 0.64f, h * 0.20f)
                // 内圈：右臂下 → 小底 U → 左臂上
                lineTo(w * 0.64f, h * 0.68f)
                cubicTo(w * 0.64f, h * 0.79f, w * 0.40f, h * 0.79f, w * 0.40f, h * 0.68f)
                lineTo(w * 0.40f, h * 0.34f)
            }
            drawPath(
                path = clipPath,
                color = Color(0xFF232428),
                style = Stroke(width = 1.7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
    }
}

// ==================== 手绘图标（小/大两档，几何全按比例） ====================

/** 手绘图片图标：圆角相框 + 双峰山 + 小太阳 */
@Composable
private fun HandDrawnPictureIcon(modifier: Modifier = Modifier.size(14.dp)) {
    Canvas(modifier = modifier) {
        val ink = Color(0xFFC9CAD2)
        val sw = (size.width * 0.055f).coerceAtLeast(1.4.dp.toPx())
        val w = size.width
        val h = size.height

        // 相框
        drawRoundRect(
            color = ink,
            topLeft = Offset(sw / 2f, sw / 2f),
            size = Size(w - sw, h - sw),
            cornerRadius = CornerRadius(w * 0.14f, w * 0.14f),
            style = Stroke(width = sw)
        )
        // 太阳
        drawCircle(color = ink, radius = w * 0.11f, center = Offset(w * 0.68f, h * 0.33f))
        // 双峰山
        val peaks = Path().apply {
            moveTo(w * 0.14f, h * 0.78f)
            lineTo(w * 0.40f, h * 0.46f)
            lineTo(w * 0.56f, h * 0.63f)
            lineTo(w * 0.72f, h * 0.48f)
            lineTo(w * 0.86f, h * 0.78f)
        }
        drawPath(peaks, color = ink, style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** 手绘文件图标：折角纸 + 两条内容线 */
@Composable
private fun HandDrawnFileIcon(modifier: Modifier = Modifier.size(14.dp)) {
    Canvas(modifier = modifier) {
        val ink = Color(0xFFC9CAD2)
        val sw = (size.width * 0.055f).coerceAtLeast(1.4.dp.toPx())
        val w = size.width
        val h = size.height
        val fold = w * 0.34f

        // 纸身（左上切角）
        val body = Path().apply {
            moveTo(fold, sw / 2f)
            lineTo(w - sw / 2f, sw / 2f)
            lineTo(w - sw / 2f, h - sw / 2f)
            lineTo(sw / 2f, h - sw / 2f)
            lineTo(sw / 2f, fold)
            close()
        }
        drawPath(body, color = ink, style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
        // 折角
        val foldLine = Path().apply {
            moveTo(sw / 2f, fold)
            lineTo(fold, fold)
            lineTo(fold, sw / 2f)
        }
        drawPath(foldLine, color = ink, style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
        // 内容线
        drawLine(ink, Offset(w * 0.24f, h * 0.55f), Offset(w * 0.76f, h * 0.55f), strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(ink, Offset(w * 0.24f, h * 0.74f), Offset(w * 0.62f, h * 0.74f), strokeWidth = sw, cap = StrokeCap.Round)
    }
}

// ==================== 中心弹出预览 ====================

@Composable
private fun FilePopup(
    file: AttachedFileInfo,
    pixelFont: FontFamily,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        // 从中心弹出：缩放 0.65→1（带回弹）+ 淡入；点暗幕任意处关闭
        val pop = remember { Animatable(0.65f) }
        val fadeIn = remember { Animatable(0f) }
        LaunchedEffect(Unit) {
            fadeIn.animateTo(1f, tween(180, easing = FastOutSlowInEasing))
        }
        LaunchedEffect(Unit) {
            pop.animateTo(1f, tween(320, easing = CubicBezierEasing(0.30f, 1.45f, 0.55f, 1f)))
        }

        val bitmap = remember(file.name, file.rawBytes) {
            if (file.isImage && file.rawBytes != null) {
                ImageBitmapDecodeStore.decode(file.rawBytes)
            } else null
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.72f * fadeIn.value))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .graphicsLayer {
                        // 新版 Compose 移除了 GraphicsLayerScope.scale，拆成 X/Y
                        scaleX = pop.value
                        scaleY = pop.value
                        alpha = fadeIn.value
                    }
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (bitmap != null) {
                    // 图片文件：弹真图，深底灰白边相纸卡
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF161619), RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0xFF6E6F78), RoundedCornerShape(12.dp))
                            .padding(10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = null,
                            modifier = Modifier
                                .widthIn(max = 300.dp)
                                .aspectRatio(
                                    (bitmap.width.toFloat() / bitmap.height.toFloat())
                                        .coerceIn(0.3f, 3f)
                                ),
                            contentScale = ContentScale.Fit
                        )
                    }
                } else {
                    // 文件（或解码失败的图片）：弹大号手绘图标，裸置于暗幕之上
                    if (file.isImage) {
                        HandDrawnPictureIcon(modifier = Modifier.size(96.dp))
                    } else {
                        HandDrawnFileIcon(modifier = Modifier.size(96.dp))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 完整文件名（含后缀），从不省略
                Text(
                    text = file.name,
                    color = Color(0xFFEDEEF2),
                    fontSize = 11.sp,
                    fontFamily = pixelFont,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// ==================== 文件名省略：中间可省，后缀永存 ====================

/**
 * 名字太长时省略中间部分，后缀完整保留：
 * "hvciyqvbbahbjdgqabcdefg.png" → "hvciyqvbb....png"
 */
private fun ellipsizeKeepingExtension(name: String, budget: Int = 16): String {
    if (name.length <= budget) return name
    val dot = name.lastIndexOf('.')
    val ext = if (dot > 0) name.substring(dot) else ""
    return if (ext.isNotEmpty() && ext.length <= 6) {
        val keep = (budget - ext.length - 3).coerceAtLeast(4)
        name.take(keep) + "..." + ext
    } else {
        name.take(budget - 3) + "..."
    }
}
