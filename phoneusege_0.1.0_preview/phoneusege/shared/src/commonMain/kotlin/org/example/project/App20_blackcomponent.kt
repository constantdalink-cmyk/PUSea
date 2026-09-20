package org.example.project

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex

// ==================== 显示名兜底 ====================
// store 已修复（renameEntry 不再强制补 .js），这里仅兜底旧版本遗留数据：
// 历史上创建的 Mew_File_N.js 条目在界面上自动剥离 .js 显示。

private val MEW_FILE_JS_RE = Regex("^Mew_File_\\d+\\.js$")

internal fun displayEntryName(actualName: String): String {
    return if (MEW_FILE_JS_RE.matches(actualName)) {
        actualName.removeSuffix(".js")
    } else {
        actualName
    }
}

// ==================== 编辑/重命名对话框 ====================

@Composable
internal fun EntryActionDialog(
    entry: JsWorkspaceEntry,
    pixelFont: FontFamily,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    var input by remember(entry.id) {
        mutableStateOf(displayEntryName(entry.name))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(200f)
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.86f)
                .background(Color(0xFF1B1B1B), RoundedCornerShape(6.dp))
                .border(1.dp, Color(0xFF3DDC84), RoundedCornerShape(6.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { }
                .padding(16.dp)
        ) {
            Text(
                text = if (entry.isFolder) "Edit folder" else "Edit file",
                color = Color.White,
                fontSize = 13.sp,
                fontFamily = pixelFont,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Name",
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 10.sp,
                fontFamily = pixelFont
            )
            Spacer(modifier = Modifier.height(4.dp))
            BasicTextField(
                value = input,
                onValueChange = { input = it },
                textStyle = TextStyle(
                    color = Color(0xFF3DDC84),
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace
                ),
                cursorBrush = SolidColor(Color(0xFF3DDC84)),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F0F0F), RoundedCornerShape(4.dp))
                    .border(1.dp, Color(0xFF333333), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            )
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                DialogButton("Delete", Color(0xFF702020), Color(0xFFFFB0B0), pixelFont) { onDelete() }
                Row {
                    DialogButton("Cancel", Color(0xFF2A2A2A), Color.White.copy(alpha = 0.7f), pixelFont) { onDismiss() }
                    Spacer(modifier = Modifier.width(8.dp))
                    DialogButton("Rename", Color(0xFF3DDC84), Color.Black, pixelFont) { onRename(input) }
                }
            }
        }
    }
}

// ==================== 创建文件夹对话框 ====================

@Composable
internal fun CreateFolderDialog(
    defaultName: String,
    pixelFont: FontFamily,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    // 预填 Mew_Folder_N，光标置于末尾（可退格/微调）；
    // 用户首次输入字符时自动替换整个预填名（避免 Mew_Folder_N.py 式残留）
    var input by remember(defaultName) { mutableStateOf(defaultName) }
    var userEdited by remember(defaultName) { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(200f)
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.86f)
                .background(Color(0xFF1B1B1B), RoundedCornerShape(6.dp))
                .border(1.dp, Color(0xFF3DDC84), RoundedCornerShape(6.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { }
                .padding(16.dp)
        ) {
            Text(
                text = "New folder",
                color = Color.White,
                fontSize = 13.sp,
                fontFamily = pixelFont,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(10.dp))
            BasicTextField(
                value = input,
                onValueChange = { newValue ->
                    if (!userEdited) {
                        // 首次输入：若是在预填名末尾追加，则只保留新输入的部分（替换默认名）
                        userEdited = true
                        input = if (newValue.length > defaultName.length && newValue.startsWith(defaultName)) {
                            newValue.removePrefix(defaultName)
                        } else {
                            newValue
                        }
                    } else {
                        input = newValue
                    }
                },
                textStyle = TextStyle(
                    color = Color(0xFF3DDC84),
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace
                ),
                cursorBrush = SolidColor(Color(0xFF3DDC84)),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F0F0F), RoundedCornerShape(4.dp))
                    .border(1.dp, Color(0xFF333333), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            )
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                DialogButton("Cancel", Color(0xFF2A2A2A), Color.White.copy(alpha = 0.7f), pixelFont) { onDismiss() }
                Spacer(modifier = Modifier.width(8.dp))
                DialogButton("Create", Color(0xFF3DDC84), Color.Black, pixelFont) { onConfirm(input) }
            }
        }
    }
}

// ==================== 新建文件命名对话框 ====================

@Composable
internal fun CreateFileDialog(
    defaultName: String,
    pixelFont: FontFamily,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    // 预填 Mew_File_N，光标置于末尾（可退格/微调）；
    // 用户首次输入字符时自动替换整个预填名（如只输入 .py 就不会残留 Mew_File_N 前缀）
    var input by remember(defaultName) { mutableStateOf(defaultName) }
    var userEdited by remember(defaultName) { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(200f)
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.86f)
                .background(Color(0xFF1B1B1B), RoundedCornerShape(6.dp))
                .border(1.dp, Color(0xFF3DDC84), RoundedCornerShape(6.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { }
                .padding(16.dp)
        ) {
            Text(
                text = "New file",
                color = Color.White,
                fontSize = 13.sp,
                fontFamily = pixelFont,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Name (leave blank for Mew_File_N)",
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 10.sp,
                fontFamily = pixelFont
            )
            Spacer(modifier = Modifier.height(4.dp))
            BasicTextField(
                value = input,
                onValueChange = { newValue ->
                    if (!userEdited) {
                        // 首次输入：若是在预填名末尾追加，则只保留新输入的部分（替换默认名）
                        userEdited = true
                        input = if (newValue.length > defaultName.length && newValue.startsWith(defaultName)) {
                            newValue.removePrefix(defaultName)
                        } else {
                            newValue
                        }
                    } else {
                        input = newValue
                    }
                },
                textStyle = TextStyle(
                    color = Color(0xFF3DDC84),
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace
                ),
                cursorBrush = SolidColor(Color(0xFF3DDC84)),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F0F0F), RoundedCornerShape(4.dp))
                    .border(1.dp, Color(0xFF333333), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            )
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                DialogButton("Cancel", Color(0xFF2A2A2A), Color.White.copy(alpha = 0.7f), pixelFont) { onDismiss() }
                Spacer(modifier = Modifier.width(8.dp))
                DialogButton("Create", Color(0xFF3DDC84), Color.Black, pixelFont) { onConfirm(input) }
            }
        }
    }
}

// ==================== 通用对话框按钮 ====================

@Composable
internal fun DialogButton(
    text: String,
    bg: Color,
    fg: Color,
    pixelFont: FontFamily,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(4.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = fg, fontSize = 11.sp, fontFamily = pixelFont, fontWeight = FontWeight.Bold)
    }
}

// ==================== 文件夹按钮 ====================

@Composable
internal fun BlackCanvasFolderButton(
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongClick() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(18.dp, 14.dp)) {
            val c = Color.White.copy(alpha = 0.9f)
            val stroke = 1.5f.dp.toPx()
            val w = size.width
            val h = size.height
            val folder = Path().apply {
                moveTo(0f, h * 0.30f)
                lineTo(0f, h * 0.20f)
                lineTo(w * 0.10f, h * 0.20f)
                lineTo(w * 0.20f, 0f)
                lineTo(w * 0.46f, 0f)
                lineTo(w * 0.56f, h * 0.20f)
                lineTo(w, h * 0.20f)
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            drawPath(folder, color = c, style = Stroke(width = stroke))
            drawLine(c, Offset(0f, h * 0.36f), Offset(w, h * 0.36f), strokeWidth = stroke)
        }
    }
}

// ==================== 文件标签 ====================

@Composable
internal fun BlackCanvasFileTab(
    name: String,
    isSelected: Boolean,
    pixelFont: FontFamily,
    onSelect: () -> Unit,
    onCloseTab: () -> Unit
) {
    Row(
        modifier = Modifier
            .height(26.dp)
            .background(
                color = if (isSelected) Color(0xFF2A2A2A) else Color.Transparent,
                shape = RoundedCornerShape(4.dp)
            )
            .border(
                width = 1.dp,
                color = if (isSelected) Color(0xFF808080) else Color(0xFF404040),
                shape = RoundedCornerShape(4.dp)
            )
            .clickable { onSelect() }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = name,
            color = if (isSelected) Color(0xFF3DDC84) else Color.White.copy(alpha = 0.5f),
            fontSize = 10.sp,
            fontFamily = pixelFont,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
        Spacer(modifier = Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .size(14.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onCloseTab() },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(7.dp)) {
                val c = if (isSelected) Color.White.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.35f)
                val s = 1.2f.dp.toPx()
                drawLine(c, Offset(0f, 0f), Offset(size.width, size.height), strokeWidth = s)
                drawLine(c, Offset(size.width, 0f), Offset(0f, size.height), strokeWidth = s)
            }
        }
    }
}

// ==================== 新增标签按钮 ====================

@Composable
internal fun BlackCanvasAddTabButton(
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(26.dp)
            .border(1.dp, if (enabled) Color(0xFF404040) else Color(0xFF202020), RoundedCornerShape(4.dp))
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(10.dp)) {
            val c = if (enabled) Color.White.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.18f)
            val s = 1.4f.dp.toPx()
            val cx = size.width / 2f
            val cy = size.height / 2f
            drawLine(Color.White, Offset(0f, cy), Offset(size.width, cy), strokeWidth = s)
            drawLine(Color.White, Offset(cx, 0f), Offset(cx, size.height), strokeWidth = s)
        }
    }
}

// ==================== 图标组件 ====================

@Composable
internal fun SmallFolderIcon(color: Color) {
    Canvas(modifier = Modifier.size(14.dp, 11.dp)) {
        val w = size.width
        val h = size.height
        val p = Path().apply {
            moveTo(0f, h * 0.28f)
            lineTo(w * 0.34f, h * 0.28f)
            lineTo(w * 0.46f, 0f)
            lineTo(w, 0f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(p, color)
    }
}

@Composable
internal fun BigFolderIcon() {
    Canvas(modifier = Modifier.size(16.dp, 13.dp)) {
        val w = size.width
        val h = size.height
        val col = Color(0xFFE8D688)
        val p = Path().apply {
            moveTo(0f, h * 0.28f)
            lineTo(w * 0.34f, h * 0.28f)
            lineTo(w * 0.46f, 0f)
            lineTo(w, 0f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(p, col)
    }
}

@Composable
internal fun JsFileIcon() {
    Canvas(modifier = Modifier.size(14.dp, 16.dp)) {
        val fileColor = Color(0xFFDDDDDD)
        val green = Color(0xFF3DDC84)
        val stroke = 1.15f.dp.toPx()
        val w = size.width
        val h = size.height
        val p = Path().apply {
            moveTo(w * 0.15f, 0f)
            lineTo(w * 0.65f, 0f)
            lineTo(w * 0.88f, h * 0.24f)
            lineTo(w * 0.88f, h)
            lineTo(w * 0.15f, h)
            close()
        }
        drawPath(p, fileColor, style = Stroke(width = stroke))
        drawLine(fileColor, Offset(w * 0.65f, 0f), Offset(w * 0.65f, h * 0.24f), strokeWidth = stroke)
        drawLine(fileColor, Offset(w * 0.65f, h * 0.24f), Offset(w * 0.88f, h * 0.24f), strokeWidth = stroke)
        drawLine(green, Offset(w * 0.28f, h * 0.58f), Offset(w * 0.72f, h * 0.58f), strokeWidth = stroke)
        drawLine(green, Offset(w * 0.28f, h * 0.74f), Offset(w * 0.56f, h * 0.74f), strokeWidth = stroke)
    }
}

// ==================== 工具函数 ====================

internal fun buildBreadcrumb(
    entries: List<JsWorkspaceEntry>,
    currentFolderId: String?
): List<JsWorkspaceEntry?> {
    val result = mutableListOf<JsWorkspaceEntry?>()
    result.add(null)
    if (currentFolderId == null) return result
    val path = mutableListOf<JsWorkspaceEntry>()
    var cur = entries.firstOrNull { it.id == currentFolderId }
    while (cur != null) {
        path.add(0, cur)
        val pid = cur.parentId
        cur = if (pid == null) null else entries.firstOrNull { it.id == pid }
    }
    result.addAll(path)
    return result
}

internal fun joinPath(rootPath: String, segments: List<String>): String {
    val sep = if (rootPath.contains("\\")) "\\" else "/"
    val cleanedRoot = rootPath.trimEnd('/', '\\')
    if (segments.isEmpty()) return cleanedRoot
    return cleanedRoot + sep + segments.joinToString(sep)
}

internal fun formatByteSize(bytes: Int): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${bytes / (1024 * 1024)} MB"
    }
}
