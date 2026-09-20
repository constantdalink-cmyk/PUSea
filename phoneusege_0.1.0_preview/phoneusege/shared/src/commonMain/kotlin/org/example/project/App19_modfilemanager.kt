package org.example.project

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex

// ==================== 资源管理器（完整实现 + ↓↑ 反色手势，反色覆盖编辑器） ====================
//
// 反色核心已统一迁至 App71_lc.kt 收口（单一事实来源），且"反色"已定性为"新画布"：
//   [1] CanvasIdentity     —— 画布身份开关（BLACK_CANVAS 旧黑画布 ⇄ NEW_CANVAS 新画布）
//   [2] invertedColors     —— 反色渲染层（saveLayer + 白色 Difference）
//   [3] flipPaletteGesture —— ↓↑ V 形快甩手势
// 本文件只剩两处"引用"（同模块同包，internal 直接可见）：
//   · 根 Box 挂 .flipPaletteGesture → CanvasIdentity.flip() 切换画布身份
//   · 状态栏读 CanvasIdentity.isNewCanvas 打 [NEW] 前缀
// （App14 根画布同样跨文件引用 invertedColors + CanvasIdentity。）
//
// 架构：反色不再只包管理器自己。开关提升为全局后，由 App14 根画布统一应用
//       .invertedColors —— 一次反色覆盖编辑器+标签页+控制台+本管理器（管理器是
//       App14 根 Box 的子级，天然被包住）。关窗后编辑器保持反色（浅色模式），
//       重开管理器再甩一次翻回。
//
// 上线：若旧版 App71_bcfilemanager.kt 还在，务必删除（旧兼容件，留着会
//       Conflicting overloads）；反色核心与浅色组件现均由 App71_lc.kt 提供。
//
// 手势：管理器内单笔"先下滑再上滑"（各 56dp、纵向主导 |y|>|x|*1.2、600ms 内）。
// 原理：saveLayer 离屏 + 白色 BlendMode.Difference，|白-原色| = 按位反色，关闭零开销。
// 识别在 PointerEventPass.Initial 裸读、不消费事件：列表滚动、单击/双击/长按、
//       删除模式滑动多选、左滑复制路径均不受影响。

// ==================== 反色核心已迁出[1]：画布身份 ====================
// 原 BlackCanvasInvert 布尔开关已迁至 App71_lc.kt 并定性升级为 CanvasIdentity
// （画布身份：旧黑画布 ⇄ 新画布）；本文件下方两处直接引用：
// 根 Box 手势 CanvasIdentity.flip() + 状态栏 isNewCanvas 打 [NEW] 前缀。同模块同包 internal 可见。

@Composable
internal fun BlackCanvasFileManager(
    visible: Boolean,
    rootPath: String,
    entries: List<JsWorkspaceEntry>,
    currentFolderId: String?,
    onCurrentFolderIdChange: (String?) -> Unit,
    selectedFileId: String?,
    fileContents: Map<String, String>,
    pixelFont: FontFamily,
    onOpenFile: (String) -> Unit,
    onDismissRequest: () -> Unit
) {
    val alphaAnim = remember { Animatable(0f) }
    var rendered by remember { mutableStateOf(false) }

    LaunchedEffect(visible) {
        if (visible) {
            rendered = true
            alphaAnim.animateTo(1f, tween(280))
        } else if (rendered) {
            alphaAnim.animateTo(0f, tween(260, easing = GreenPeelOffEasing))
            rendered = false
        }
    }

    if (!rendered) return

    var searchQuery by remember(currentFolderId, visible) { mutableStateOf("") }
    // 长按进入删除模式；删除模式下可上下滑动多选，一次性批量删除
    var deleteModeActive by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var actionTarget by remember { mutableStateOf<JsWorkspaceEntry?>(null) }
    var showCreateFolder by remember { mutableStateOf(false) }
    // 删除确认弹窗开关（取代原顶部 Cancel/Delete 提示条）：长按进删除模式时弹出，点行尾 ✕ 按当前选中重开
    var showDeleteConfirm by remember { mutableStateOf(false) }
    // 每行窗口坐标（滑动多选命中检测）+ 列表顶部窗口坐标
    val rowBounds = remember { mutableStateMapOf<String, Rect>() }
    var listWindowTop by remember { mutableStateOf(0f) }
    // 供滑动多选协程读取的最新删除模式状态（协程常驻，不随状态切换重启、不丢失手势）
    val deleteModeActiveState by rememberUpdatedState(deleteModeActive)

    LaunchedEffect(entries, currentFolderId) {
        val cf = currentFolderId
        if (cf != null && entries.none { it.id == cf }) {
            onCurrentFolderIdChange(null)
        }
    }

    val breadcrumb = buildBreadcrumb(entries, currentFolderId)
    val pathSegments = breadcrumb.mapNotNull { it?.name }
    val children = entries
        .filter { it.parentId == currentFolderId }
        .filter { searchQuery.isBlank() || it.name.contains(searchQuery, ignoreCase = true) }
        .sortedWith(compareByDescending<JsWorkspaceEntry> { it.isFolder }.thenBy { it.name.lowercase() })

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(100f)
            // [2]：↓↑ V 形快甩切换画布身份——旧黑画布 ⇄ 新画布（手势与身份开关定义于 App71_lc.kt；
            //      App14 根画布据此整体反色呈现新画布外观，含本管理器）
            .flipPaletteGesture(onFlip = { CanvasIdentity.flip() })
            .graphicsLayer { alpha = alphaAnim.value }
            .background(Color.Black.copy(alpha = 0.72f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onDismissRequest() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.86f)
                .background(Color(0xFF161616), RoundedCornerShape(6.dp))
                .border(2.dp, Color(0xFF333333), RoundedCornerShape(6.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { }
            // FIX: 移除原先挂在整块面板上的全局下滑手势（totalDy > 90f 即弹窗），
            //      现在只有从下方箭头触发条上开始的下滑才会打开 CreateFolderDialog
        ) {
            // 标题栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .background(Color(0xFF242424), RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SmallFolderIcon(Color(0xFF3DDC84))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Workspace Explorer",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontFamily = pixelFont,
                        fontWeight = FontWeight.Bold
                    )
                }
                Box(
                    modifier = Modifier.size(24.dp).clickable { onDismissRequest() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "X",
                        color = Color(0xFFE55353),
                        fontSize = 14.sp,
                        fontFamily = pixelFont,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // 原多选删除工具栏（顶部 Cancel/Delete 提示条）已移除，
            // 改由文件尾部的 DeleteConfirmDialog 承载，触发规则：
            // 点行尾 ✕ 时多选数 > 1 才弹窗确认；单选（=1）点 ✕ 直接删除、不弹窗。
            // 弹窗内容只有一句 "Delete N files?"（N = 选中数），底部仅 Cancel / Del 两个选项。

            // 地址栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E1E1E))
                    .border(1.dp, Color(0xFF2A2A2A))
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ← 返回上级按钮（进入子文件夹后可逐级返回）
                val canGoUp = currentFolderId != null
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .background(
                            color = if (canGoUp) Color(0xFF232A24) else Color(0xFF161616),
                            shape = RoundedCornerShape(3.dp)
                        )
                        .border(
                            1.dp,
                            if (canGoUp) Color(0xFF3A423A) else Color(0xFF232323),
                            RoundedCornerShape(3.dp)
                        )
                        .clickable(enabled = canGoUp) {
                            val parentId = entries.firstOrNull { it.id == currentFolderId }?.parentId
                            onCurrentFolderIdChange(parentId)
                            deleteModeActive = false
                            selectedIds = emptySet()
                            searchQuery = ""
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "←",
                        color = if (canGoUp) Color.White else Color.White.copy(alpha = 0.25f),
                        fontSize = 11.sp,
                        fontFamily = pixelFont,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text("Path:", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp, fontFamily = pixelFont)
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(Color(0xFF121212), RoundedCornerShape(2.dp))
                        .border(1.dp, Color(0xFF2A2A2A), RoundedCornerShape(2.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = joinPath(rootPath, pathSegments),
                        color = Color(0xFF3DDC84),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // 当前文件夹搜索
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF171717))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Search:",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 10.sp,
                    fontFamily = pixelFont
                )
                Spacer(modifier = Modifier.width(8.dp))
                BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = Color(0xFF3DDC84),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    ),
                    cursorBrush = SolidColor(Color(0xFF3DDC84)),
                    modifier = Modifier.weight(1f),
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF101010), RoundedCornerShape(4.dp))
                                .border(1.dp, Color(0xFF2A2A2A), RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "search in current folder",
                                    color = Color.White.copy(alpha = 0.22f),
                                    fontSize = 10.sp,
                                    fontFamily = pixelFont
                                )
                            }
                            innerTextField()
                        }
                    }
                )
            }

            // 下滑创建文件夹触发条（唯一的下滑触发区域）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(Color(0xFF1A1A1A))
                    .pointerInput(currentFolderId) {
                        var accY = 0f
                        detectVerticalDragGestures(
                            onDragStart = { accY = 0f },
                            onDragEnd = {
                                if (accY > 30f) showCreateFolder = true
                                accY = 0f
                            },
                            onDragCancel = { accY = 0f },
                            onVerticalDrag = { change, dy ->
                                change.consume()
                                accY += dy
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                // 放大的向下箭头 "⌄"（无箭杆、主题绿）：仅从本条带开始的下滑才触发新建文件夹
                Canvas(modifier = Modifier.size(30.dp, 22.dp)) {
                    val c = Color(0xFF3DDC84)
                    val stroke = 2f.dp.toPx()
                    val w = size.width
                    val h = size.height
                    // 仅两翼构成 "⌄" 形（无垂直箭杆），垂直居中
                    drawLine(c, Offset(0f, h * 0.22f), Offset(w / 2f, h * 0.72f), strokeWidth = stroke)
                    drawLine(c, Offset(w, h * 0.22f), Offset(w / 2f, h * 0.72f), strokeWidth = stroke)
                }
            }

            // 表头
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1C1C1C))
                    .border(1.dp, Color(0xFF252525))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text("Name", modifier = Modifier.weight(0.32f), color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp, fontFamily = pixelFont)
                Text("Type", modifier = Modifier.weight(0.16f), color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp, fontFamily = pixelFont)
                Text("Size", modifier = Modifier.weight(0.14f), color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp, fontFamily = pixelFont)
                Text("Full Path", modifier = Modifier.weight(0.30f), color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp, fontFamily = pixelFont)
                Text("", modifier = Modifier.weight(0.08f))
            }

            // 文件列表（删除模式下：上下滑动划过行即多选，此时禁用滚动避免抖动）
            val listScrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFF161616))
                    .verticalScroll(listScrollState, enabled = !deleteModeActive)
                    .padding(8.dp)
                    .onGloballyPositioned { listWindowTop = it.boundsInWindow().top }
                    // FIX: 删除模式下上下滑动多选。
                    // 原 detectVerticalDragGestures 有两个致命问题：
                    //  1) 行内 detectTapGestures 会在 Main 通道消费按下事件，父级拖拽检测直接取消（手势仲裁失败）；
                    //  2) 挂在 pointerInput(deleteModeActive) 上，长按触发删除模式的瞬间协程重启，
                    //     正在按下的那根手指错过了 down，之后怎么滑都不触发。
                    // 改为 Initial 通道裸读指针位置（子节点消费前即可读到，且协程常驻不重启）：
                    //   - 长按后不抬手直接拖动，同一指针的后续位移也能捕获；
                    //   - 抬手后重新下滑，新的手势同样生效。
                    .pointerInput(listScrollState) {
                        awaitEachGesture {
                            val down = awaitFirstDown(
                                requireUnconsumed = false,
                                pass = PointerEventPass.Initial
                            )
                            var lastHitId: String? = null
                            while (true) {
                                val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                if (deleteModeActiveState) {
                                    val pointerWindowY = listWindowTop + change.position.y
                                    val hitId = rowBounds.entries.firstOrNull {
                                        pointerWindowY >= it.value.top && pointerWindowY <= it.value.bottom
                                    }?.key
                                    if (hitId != null && hitId != lastHitId) {
                                        lastHitId = hitId
                                        selectedIds = selectedIds + hitId
                                    }
                                }
                            }
                        }
                    }
            ) {
                if (children.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "(empty folder)",
                            color = Color.White.copy(alpha = 0.3f),
                            fontSize = 11.sp,
                            fontFamily = pixelFont
                        )
                    }
                } else {
                    children.forEach { entry ->
                        val shownName = displayEntryName(entry.name)
                        val entryPath = joinPath(rootPath, pathSegments) +
                                (if (rootPath.contains("\\")) "\\" else "/") + shownName

                        // 记录每行窗口坐标，供删除模式下滑动多选命中检测
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .onGloballyPositioned { coords ->
                                    rowBounds[entry.id] = coords.boundsInWindow()
                                }
                        ) {
                            ExplorerRow(
                                entry = entry,
                                shownName = shownName,
                                isSelected = !entry.isFolder && entry.id == selectedFileId && entry.parentId == currentFolderId,
                                isDeleteMode = entry.id in selectedIds,
                                sizeText = if (entry.isFolder) "" else formatByteSize(
                                    (fileContents[entry.id] ?: "").encodeToByteArray().size
                                ),
                                fullPath = entryPath,
                                pixelFont = pixelFont,
                                onSingleTap = {
                                    when {
                                        // 删除模式：点按行切换选中（用于修正滑动误选）
                                        deleteModeActive -> {
                                            selectedIds = if (entry.id in selectedIds) {
                                                selectedIds - entry.id
                                            } else {
                                                selectedIds + entry.id
                                            }
                                            if (selectedIds.isEmpty()) deleteModeActive = false
                                        }
                                        entry.isFolder -> {
                                            // FIX: 点击文件夹 = 进入该目录，面板保持打开（原代码误调 onDismissRequest 导致整个面板消失）
                                            onCurrentFolderIdChange(entry.id)
                                            searchQuery = ""
                                        }
                                        else -> onOpenFile(entry.id)
                                    }
                                },
                                onDoubleTap = {
                                    actionTarget = entry
                                    deleteModeActive = false
                                    selectedIds = emptySet()
                                },
                                onLongPress = {
                                    // 长按静默进入删除模式（原顶部提示条已删，弹窗触发点移到了行尾 ✕）：
                                    // 单选不弹窗，故长按也不弹；再次长按或点按行 = 切换选中。
                                    if (!deleteModeActive) {
                                        deleteModeActive = true
                                        selectedIds = setOf(entry.id)
                                    } else {
                                        selectedIds = if (entry.id in selectedIds) {
                                            selectedIds - entry.id
                                        } else {
                                            selectedIds + entry.id
                                        }
                                        if (selectedIds.isEmpty()) deleteModeActive = false
                                    }
                                },
                                onDeleteRequest = {
                                    // 点击 ✕ 的分流规则（✕ 只出现在已选中行上，故 size==1 时选中的就是本行）：
                                    //   单选（=1）→ 直接删除，不弹窗，零打扰；
                                    //   多选（>1）→ 弹 DeleteConfirmDialog 按"全部选中"确认，Del 批量删。
                                    if (selectedIds.size > 1) {
                                        showDeleteConfirm = true
                                    } else {
                                        JsWorkspaceStore.deleteEntry(entry.id)
                                        selectedIds = selectedIds - entry.id
                                        if (selectedIds.isEmpty()) deleteModeActive = false
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // 状态栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .background(Color(0xFF242424), RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val sel = entries.firstOrNull { it.id == selectedFileId }
                val statusText = when {
                    sel != null -> {
                        val selPath = joinPath(
                            rootPath,
                            buildBreadcrumb(entries, sel.parentId).mapNotNull { it?.name }
                        )
                        "Selected: $selPath" + (if (rootPath.contains("\\")) "\\" else "/") +
                                displayEntryName(sel.name)
                    }
                    else -> "${children.size} object(s)"
                }
                // [3]：新画布身份时状态栏打 [NEW] 前缀并转主题绿，一眼知道当前是新画布
                Text(
                    text = (if (CanvasIdentity.isNewCanvas) "[NEW] " else "") + statusText,
                    color = if (CanvasIdentity.isNewCanvas) Color(0xFF3DDC84) else Color.White.copy(alpha = 0.55f),
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // 双击编辑对话框
        actionTarget?.let { target ->
            EntryActionDialog(
                entry = target,
                pixelFont = pixelFont,
                onRename = { newName ->
                    // store 已修复：renameEntry 按输入原样命名，不再强制补 .js
                    JsWorkspaceStore.renameEntry(target.id, newName)
                    actionTarget = null
                },
                onDelete = {
                    if (target.id == currentFolderId) {
                        onCurrentFolderIdChange(target.parentId)
                    }
                    JsWorkspaceStore.deleteEntry(target.id)
                    actionTarget = null
                },
                onDismiss = { actionTarget = null }
            )
        }

        // 创建文件夹对话框（预填 Mew_Folder_N，编号从小到大填补空缺）
        if (showCreateFolder) {
            CreateFolderDialog(
                defaultName = nextMewFolderName(entries, currentFolderId),
                pixelFont = pixelFont,
                onConfirm = { name ->
                    JsWorkspaceStore.createFolder(currentFolderId, name)
                    showCreateFolder = false
                },
                onDismiss = { showCreateFolder = false }
            )
        }

        // 删除确认弹窗（取代原顶部 Cancel/Delete 提示条，与改名弹窗同款样式）：
        // 仅当多选数 > 1 时点 ✕ 触发；单选直接删、不经过这里。
        // 内容只有一句 "Delete N files?"（N = 当前选中数）；底部仅 Cancel / Del。
        // Cancel = 只关弹窗，删除模式保留（继续滑动多选，点 ✕ 再开）；
        // Del = 批量删除全部选中并退出删除模式（沿用原 Delete 按钮的删当前目录回落逻辑）。
        if (showDeleteConfirm) {
            DeleteConfirmDialog(
                count = selectedIds.size,
                pixelFont = pixelFont,
                onConfirm = {
                    if (selectedIds.isNotEmpty()) {
                        val cf = currentFolderId
                        if (cf != null && cf in selectedIds) {
                            onCurrentFolderIdChange(entries.firstOrNull { it.id == cf }?.parentId)
                        }
                        selectedIds.forEach { JsWorkspaceStore.deleteEntry(it) }
                    }
                    selectedIds = emptySet()
                    deleteModeActive = false
                    showDeleteConfirm = false
                },
                onDismiss = { showDeleteConfirm = false }
            )
        }

    }
}

// ==================== 反色核心已迁出[2][3] ====================
// invertedColors（反色层）与 flipPaletteGesture（↓↑ 手势）已迁至 App71_lc.kt 收口。
// 注意：flipPaletteGesture 由原 private 提升为 internal（App19 根 Box 跨文件挂载所需）。

// ==================== 文件行 ====================

@Composable
private fun ExplorerRow(
    entry: JsWorkspaceEntry,
    shownName: String,
    isSelected: Boolean,
    isDeleteMode: Boolean,
    sizeText: String,
    fullPath: String,
    pixelFont: FontFamily,
    onSingleTap: () -> Unit,
    onDoubleTap: () -> Unit,
    onLongPress: () -> Unit,
    onDeleteRequest: () -> Unit
) {
    // 自动识别文件后缀作为类型标签（不再死板显示 JS）；类型基于显示名识别
    val typeText = when {
        entry.isFolder -> "Folder"
        else -> {
            val dot = shownName.lastIndexOf('.')
            if (dot > 0 && dot < shownName.length - 1) {
                shownName.substring(dot + 1).uppercase()
            } else {
                "FILE"
            }
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = if (isDeleteMode) Color(0xFF2A1B1B) else Color.Transparent,
                shape = RoundedCornerShape(4.dp)
            )
            .pointerInput(entry.id) {
                detectTapGestures(
                    onTap = { onSingleTap() },
                    onDoubleTap = { onDoubleTap() },
                    onLongPress = { onLongPress() }
                )
            }
            // 左滑复制路径：手指在行上向左划（水平 > 48dp 且明显大于垂直）即复制完整路径到剪贴板。
            // 文件、文件夹通用（fullPath 已按条目计算好）；复制成功有 Toast（AndroidTextCopier）。
            .copyOnLeftSwipe(path = fullPath)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (entry.isFolder) BigFolderIcon() else JsFileIcon()

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = shownName,
            modifier = Modifier.weight(0.32f),
            color = when {
                isSelected -> Color(0xFF3DDC84)
                isDeleteMode -> Color(0xFFFF9B9B)
                else -> Color.White
            },
            fontSize = 10.sp,
            fontFamily = pixelFont,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = typeText,
            modifier = Modifier.weight(0.16f),
            color = Color.White.copy(alpha = 0.50f),
            fontSize = 9.sp,
            fontFamily = pixelFont
        )

        Text(
            text = sizeText,
            modifier = Modifier.weight(0.14f),
            color = Color.White.copy(alpha = 0.50f),
            fontSize = 9.sp,
            fontFamily = pixelFont
        )

        Text(
            text = fullPath,
            modifier = Modifier.weight(0.30f),
            color = Color.White.copy(alpha = 0.35f),
            fontSize = 7.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Box(
            modifier = Modifier.weight(0.08f),
            contentAlignment = Alignment.CenterEnd
        ) {
            if (isDeleteMode) {
                TrashCanIcon(onClick = { onDeleteRequest() })
            }
        }
    }
}

// ==================== 默认文件夹名辅助 ====================

/** 计算当前文件夹内下一个默认文件夹名：Mew_Folder_[数字]（编号从小往大填补空缺，用于弹窗预填） */
private fun nextMewFolderName(entries: List<JsWorkspaceEntry>, parentId: String?): String {
    val used = entries
        .filter { it.parentId == parentId && it.isFolder }
        .mapNotNull {
            Regex("""Mew_Folder_(\d+)""").matchEntire(it.name)?.groupValues?.get(1)?.toIntOrNull()
        }
        .toSet()
    var n = 1
    while (n in used) n++
    return "Mew_Folder_$n"
}

// ==================== 删除确认弹窗（改名弹窗同款，取代原顶部 Cancel/Delete 提示条） ====================

/**
 * 批量删除确认弹窗（仅多选数 > 1 时点 ✕ 触发；单选直接删，不进本弹窗）：
 * 内容只有一句 "Delete N files?"（N = 当前选中数，N > 1 恒定复数），
 * 底部仅 Cancel / Del 两个选项。
 * 视觉沿用本文件面板语言：0xFF161616 面板 + 0xFF333333 边框 + 0xFF242424 标题栏 + 像素字体 + DialogButton。
 * 点弹窗外空白 = Cancel（只关弹窗，删除模式保留）。
 */
@Composable
private fun DeleteConfirmDialog(
    count: Int,
    pixelFont: FontFamily,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .background(Color(0xFF161616), RoundedCornerShape(6.dp))
                .border(2.dp, Color(0xFF333333), RoundedCornerShape(6.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { }
        ) {
            // 标题栏（与资源管理器/改名弹窗同构）：红 ✕ + Confirm Delete
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(30.dp)
                    .background(Color(0xFF242424), RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Canvas(modifier = Modifier.size(8.dp)) {
                    val c = Color(0xFFFF5555)
                    val s = 1.6f.dp.toPx()
                    drawLine(c, Offset(0f, 0f), Offset(size.width, size.height), strokeWidth = s)
                    drawLine(c, Offset(size.width, 0f), Offset(0f, size.height), strokeWidth = s)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Confirm Delete",
                    color = Color(0xFFFFB0B0),
                    fontSize = 11.sp,
                    fontFamily = pixelFont,
                    fontWeight = FontWeight.Bold
                )
            }

            // 内容只有这一句（英文已修正：单数 file / 复数 files）
            Text(
                text = if (count == 1) "Delete 1 file?" else "Delete $count files?",
                color = Color.White,
                fontSize = 11.sp,
                fontFamily = pixelFont,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 14.dp)
            )

            // 底部两个选项：Cancel（关弹窗留模式）/ Del（批量删除并退出模式）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                DialogButton("Cancel", Color(0xFF2A2A2A), Color.White.copy(alpha = 0.7f), pixelFont) {
                    onDismiss()
                }
                Spacer(modifier = Modifier.width(8.dp))
                DialogButton("Del", Color(0xFF702020), Color(0xFFFFB0B0), pixelFont) {
                    onConfirm()
                }
            }
        }
    }
}

// ==================== 简易 X 删除图标 ====================

@Composable
private fun TrashCanIcon(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(22.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(7.dp)) {
            val c = Color(0xFFFF5555)
            val s = 1.3f.dp.toPx()
            drawLine(c, Offset(0f, 0f), Offset(size.width, size.height), strokeWidth = s)
            drawLine(c, Offset(size.width, 0f), Offset(0f, size.height), strokeWidth = s)
        }
    }
}
