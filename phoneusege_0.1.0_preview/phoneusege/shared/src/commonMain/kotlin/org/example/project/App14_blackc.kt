package org.example.project

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ==================== 黑色画布主体 ====================

@Composable
internal fun BoxScope.GreenBlackCanvas(
    isRendered: Boolean,
    offsetFraction: Float,
    onClose: () -> Unit,
    pixelFont: FontFamily
) {
    if (!isRendered) return

    val isLoaded by JsWorkspaceStore.isLoaded.collectAsState()
    val rootPath by JsWorkspaceStore.rootPath.collectAsState()
    val entries by JsWorkspaceStore.entries.collectAsState()
    val openedFileIds by JsWorkspaceStore.openedFileIds.collectAsState()
    val selectedFileId by JsWorkspaceStore.selectedFileId.collectAsState()
    val fileContents by JsWorkspaceStore.fileContents.collectAsState()
    val lastError by JsWorkspaceStore.lastError.collectAsState()

    var showManager by remember { mutableStateOf(false) }
    val jsRunning by JsRunnerStore.isRunning.collectAsState()
    val jsConsoleLines by JsRunnerStore.consoleLines.collectAsState()
    val jsShowConsole by JsRunnerStore.showConsole.collectAsState()
    var managerFolderId by remember { mutableStateOf<String?>(null) }

    // 新建文件命名弹窗（加号触发）：创建前先让用户取名——
    // 用户输入名字 → 直接以该名字创建；留空直接 Create → store 自动生成默认名 Mew_File_[数字]；Cancel → 不创建。
    // 注意：必须用 createFileNamed 直接按名创建（不再走 createFile + renameEntry——
    // 旧版 renameEntry 会对文件强制追加 .js，store 已一并修复）。
    var showCreateDialog by remember { mutableStateOf(false) }
    var dialogDefaultName by remember { mutableStateOf("Mew_File_1") }

    // 新画布独占[5]：下拉画板（「新新画布」）。画板组件与手势定义在 App71_lc.kt，
    // 这里只是宿主；仅 isNewCanvas 身份激活。作画区与笔迹已整体拆除，画板现在是纯项目板。
    var sketchOpen by remember { mutableStateOf(false) }

    val openedTabs = openedFileIds.mapNotNull { id ->
        entries.firstOrNull { it.id == id && !it.isFolder }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationX = offsetFraction * size.width
                clip = true
            }
            // 新画布独占[5]：顶部下滑打开画板（仅 isNewCanvas 且画板未开时生效）。
            // 手势与画板定义在 App71_lc.kt，这里只是挂载点；画板渲染在反色层之外（见底部）。
            .pullDownSketchGesture(
                enabled = CanvasIdentity.isNewCanvas && !sketchOpen,
                onOpen = { sketchOpen = true }
            )
            .pointerInput(showManager) {
                awaitEachGesture {
                    val down = awaitFirstDown(
                        requireUnconsumed = false,
                        pass = PointerEventPass.Initial
                    )
                    val pointerId = down.id
                    val closeThresholdPx = 48.dp.toPx()
                    var totalX = 0f
                    var totalY = 0f
                    while (true) {
                        val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                        val delta = change.positionChange()
                        totalX += delta.x
                        totalY += delta.y
                        if (!change.pressed) break
                    }
                    val isRightSwipe = totalX > closeThresholdPx &&
                        kotlin.math.abs(totalX) > kotlin.math.abs(totalY) * 1.20f
                    if (!showManager && isRightSwipe) {
                        onClose()
                    }
                }
            }
    ) {
        // 反色层降级为内层 Box，只包编辑器本体（编辑器+标签页+控制台+资源管理器）；
        // 画板渲染在其外（见底部），白底粉灰边自持配色，不随画布身份反色。
        Box(
            modifier = Modifier
                .fillMaxSize()
                // 画布身份：新画布（NEW_CANVAS）身份时整块按位反色；旧黑画布原样直通，零开销。
                // 身份开关 CanvasIdentity 由资源管理器 ↓↑ 手势切换（定义见 App71_lc.kt）。
                .invertedColors(enabled = CanvasIdentity.isNewCanvas)
                .background(Color.Black)
        ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 顶部栏
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 6.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BlackCanvasFolderButton(
                        onClick = { showManager = true },
                        onLongClick = { onClose() }
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clipToBounds()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxHeight()
                                .horizontalScroll(rememberScrollState())
                                .padding(end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            openedTabs.forEach { tab ->
                                BlackCanvasFileTab(
                                    name = displayEntryName(tab.name),
                                    isSelected = tab.id == selectedFileId,
                                    pixelFont = pixelFont,
                                    onSelect = { JsWorkspaceStore.openFile(tab.id) },
                                    onCloseTab = { JsWorkspaceStore.closeTab(tab.id) }
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // FIXED_TOP_RIGHT_CONTROLS_BEGIN
                    BlackCanvasAddTabButton(
                        enabled = isLoaded,
                        onClick = {
                            // 点加号 → 先弹命名窗（预填下一个默认名）；确认后才创建，Cancel 则不创建
                            dialogDefaultName = nextMewFileName(entries, managerFolderId)
                            showCreateDialog = true
                        }
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    // C：控制台开关
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .background(
                                color = if (jsShowConsole) Color(0xFF2A2A2A) else Color.Transparent,
                                shape = RoundedCornerShape(4.dp)
                            )
                            .border(1.dp, Color(0xFF404040), RoundedCornerShape(4.dp))
                            .clickable { JsRunnerStore.toggleConsole() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "C",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontFamily = pixelFont,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // ▶：运行当前文件
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .background(
                                color = if (jsRunning) Color(0xFF2A2A2A) else Color.Transparent,
                                shape = RoundedCornerShape(4.dp)
                            )
                            .border(1.dp, Color(0xFF404040), RoundedCornerShape(4.dp))
                            .clickable(enabled = !jsRunning && selectedFileId != null) {
                                JsRunnerStore.runCurrentFile()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(width = 9.dp, height = 11.dp)) {
                            val triangle = Path().apply {
                                moveTo(0f, 0f)
                                lineTo(size.width, size.height / 2f)
                                lineTo(0f, size.height)
                                close()
                            }
                            drawPath(
                                path = triangle,
                                color = if (!jsRunning && selectedFileId != null) {
                                    Color.White
                                } else {
                                    Color.White.copy(alpha = 0.25f)
                                }
                            )
                        }
                    }

                    // FIXED_TOP_RIGHT_CONTROLS_END
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color(0xFF808080))
                )
            }

            // 编辑区
            val currentEntry = entries.firstOrNull { it.id == selectedFileId }
            when {
                !isLoaded -> EditorCenterMessage("loading...", pixelFont)
                currentEntry != null && !currentEntry.isFolder -> {
                    val id = currentEntry.id
                    val currentContent = fileContents[id] ?: ""
                    BasicTextField(
                        value = currentContent,
                        onValueChange = { JsWorkspaceStore.updateContent(id, it) },
                        visualTransformation = JsPseudoSyntaxHighlightTransformation,
                        textStyle = TextStyle(
                            color = Color(0xFFD4D4D4),
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace
                        ),
                        cursorBrush = SolidColor(Color(0xFF3DDC84)),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                }
                // 空状态：不显示任何提示（已去掉原来的 "+ .js" 图标）
                else -> Unit
            }
        }

        // 控制台
        if (jsShowConsole) {
            JsConsolePanel(
                lines = jsConsoleLines,
                pixelFont = pixelFont,
                onClear = { JsRunnerStore.clearConsole() },
                onClose = { JsRunnerStore.toggleConsole() },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(0.35f)
            )
        }

        // 错误提示
        if (lastError != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color(0xFF300000))
                    .padding(6.dp)
            ) {
                Text(
                    text = lastError ?: "",
                    color = Color(0xFFFF7777),
                    fontSize = 10.sp,
                    fontFamily = pixelFont,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // 资源管理器
        BlackCanvasFileManager(
            visible = showManager,
            rootPath = rootPath.ifBlank { "/js_workspace/scripts" },
            entries = entries,
            currentFolderId = managerFolderId,
            onCurrentFolderIdChange = { managerFolderId = it },
            selectedFileId = selectedFileId,
            fileContents = fileContents,
            pixelFont = pixelFont,
            onOpenFile = { id ->
                JsWorkspaceStore.openFile(id)
                showManager = false
            },
            onDismissRequest = { showManager = false }
        )

        // 新建文件命名弹窗：Create → 创建并应用名字；Cancel → 不创建
        if (showCreateDialog) {
            CreateFileDialog(
                defaultName = dialogDefaultName,
                pixelFont = pixelFont,
                onConfirm = { name ->
                    // 直接以用户确认的名字创建（留空则由 store 生成 Mew_File_N 默认名，无后缀）
                    JsWorkspaceStore.createFileNamed(managerFolderId, name.trim())
                    showCreateDialog = false
                },
                onDismiss = { showCreateDialog = false }
            )
        }
        }

        // 新画布独占[5]：下拉画板，渲染在反色层之外（白底粉灰边，自持配色，不随身份反色）。
        // 组件定义在 App71_lc.kt（internal），状态 sketchOpen / sketchStrokes 在本函数顶部。
        if (CanvasIdentity.isNewCanvas) {
            SketchPadPanel(
                visible = sketchOpen,
                pixelFont = pixelFont,
                onClose = { sketchOpen = false },
                // 点击竖块 = 打开该项目：新新画布上滑收起，工作区切到该项目的工作区。
                // 每个竖块（除加号母体）= 一个新项目，键 = __proj_ + id（全局、不挂会话、永远保存；见 App15 store）。
                // 项目继承：画布里攒下的文件库只继承给第一个点击的竖块（门禁 = 磁盘事实，见 store）。
                onOpenProject = { projectId ->
                    sketchOpen = false
                    JsWorkspaceStore.switchProject(projectId)
                },
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}

/** 计算当前文件夹内下一个默认文件名：Mew_File_[数字]（与 store 的 generateDefaultFileName 同逻辑，编号从小往大填补空缺，用于弹窗预填） */
private fun nextMewFileName(entries: List<JsWorkspaceEntry>, parentId: String?): String {
    val used = entries
        .filter { it.parentId == parentId && !it.isFolder }
        .mapNotNull {
            Regex("""Mew_File_(\d+)""").matchEntire(it.name)?.groupValues?.get(1)?.toIntOrNull()
        }
        .toSet()
    var n = 1
    while (n in used) n++
    return "Mew_File_$n"
}

@Composable
internal fun EditorCenterMessage(text: String, pixelFont: FontFamily) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            color = Color.White.copy(alpha = 0.22f),
            fontSize = 13.sp,
            fontFamily = pixelFont
        )
    }
}


@Composable
internal fun JsConsolePanel(
    lines: List<JsConsoleLine>,
    pixelFont: FontFamily,
    onClear: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.background(Color(0xFF0E0E0E)).border(1.dp, Color(0xFF2A2A2A))) {
        Row(
            Modifier.fillMaxWidth().height(26.dp).background(Color(0xFF1A1A1A)).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Console", color = Color(0xFF3DDC84), fontSize = 10.sp, fontFamily = pixelFont, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text(
                "Clear",
                color = Color.White,
                fontSize = 9.sp,
                fontFamily = pixelFont,
                modifier = Modifier.clickable { onClear() }.padding(horizontal = 6.dp)
            )
            Text(
    "x",
    color = Color(0xFFE55353),
    fontSize = 10.sp,
    fontFamily = pixelFont,
    fontWeight = FontWeight.Bold,
    modifier = Modifier
        .clickable { onClose() }
        .padding(horizontal = 4.dp)
)
        }
        val scroll = rememberScrollState()
        LaunchedEffect(lines.size) { scroll.animateScrollTo(scroll.maxValue) }
        Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(8.dp)) {
            if (lines.isEmpty()) {
                Text(
                    "Press Run to execute.",
                    color = Color.White.copy(alpha = 0.28f),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            } else {
                lines.forEach { line ->
                    val c = when (line.kind) {
                        JsConsoleKind.Stdout -> Color(0xFFD4D4D4)
                        JsConsoleKind.Stderr -> Color(0xFFFF6B6B)
                        JsConsoleKind.Info -> Color(0xFFFFC857)
                    }
                    Text(
                        line.text,
                        color = c,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
            }
        }
    }
}
