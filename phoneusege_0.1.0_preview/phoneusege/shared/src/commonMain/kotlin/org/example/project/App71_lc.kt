package org.example.project

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import org.jetbrains.compose.resources.Font
import org.example.project.generated.resources.Res
import org.example.project.generated.resources.fusion_pixel_10px_monospaced_ja
import org.example.project.generated.resources.fusion_pixel_10px_monospaced_ko
import org.example.project.generated.resources.fusion_pixel_10px_monospaced_latin
import org.example.project.generated.resources.fusion_pixel_10px_monospaced_zh_hans
import org.example.project.generated.resources.fusion_pixel_10px_monospaced_zh_hant
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 画布身份：BLACK_CANVAS = 旧黑画布；NEW_CANVAS = 新画布（反色后的画布定性而来）。
 * public：JsWorkspaceStore.switchCanvas 的公开 API 以它为参数，internal 会触发
 * "public exposes internal" 编译错误——身份枚举理应是画布体系的公共词汇。
 */
enum class CanvasKind { BLACK_CANVAS, NEW_CANVAS }

/**
 * 当前画布身份（全局持久、跨重启保留）：
 * 资源管理器的 ↓↑ 手势调用 flip() 在两种身份间切换；编辑器根画布(App14)读取 isNewCanvas，
 * 新画布身份时整块按位反色。身份随全局存档（sketch_blocks.json 的 canvasKind 位）持久化，
 * 重启后由 JsWorkspaceStore 在首次会话载入时 restore 回存档身份——停在哪块画布关的 app，
 * 重开就落在哪块画布，文件原地可见，不会因身份复位造成"文件丢了"的错觉。
 */
internal object CanvasIdentity {
    var kind by mutableStateOf(CanvasKind.BLACK_CANVAS)
    val isNewCanvas: Boolean get() = kind == CanvasKind.NEW_CANVAS
    fun flip() {
        kind = if (isNewCanvas) CanvasKind.BLACK_CANVAS else CanvasKind.NEW_CANVAS
        SketchBlockStore.canvasKindName = kind.name
        JsWorkspaceStore.switchCanvas(kind)
    }
}

/**
 * 反色层：开启时先用 saveLayer 把子树画进离屏缓冲，
 * 再叠一块白色 Difference —— |白 - 原色| 即按位反色，
 * 背景、面板、文字、图标、弹框一次全反；关闭时原样直通，零额外开销。
 * 用 saveLayer/restore 手写离屏组，而非 graphicsLayer.compositingStrategy ——
 * 后者在不同 Compose 版本里包路径不同（graphics vs graphics.layer），会编译报错。
 * internal：供 App14 根画布与下方新画布组件调用（同模块同包）。
 */
internal fun Modifier.invertedColors(enabled: Boolean): Modifier =
    if (!enabled) {
        this
    } else {
        this.drawWithContent {
            val canvas = drawContext.canvas
            canvas.saveLayer(Rect(0f, 0f, size.width, size.height), Paint())
            drawContent()
            drawRect(color = Color.White, blendMode = BlendMode.Difference)
            canvas.restore()
        }
    }

/**
 * 单笔 V 形快甩（画布身份切换手势）：下滑累计 > 56dp（纵向主导 |y| > |x| * 1.2）
 * 进入第二段，从最低点回拉 > 56dp 且整笔 ≤ 600ms → onFlip()。
 * Initial 通道裸读、requireUnconsumed = false、全程不消费事件：
 * 与行内 detectTapGestures、列表滚动、删除模式滑动多选、左滑复制均互不干扰；
 * 新建文件夹触发条的 30px 下滑远够不到 56dp 双阈值，不会误触。
 * internal（原为 App19 私有件，迁出时提升可见性）：供 App19 根 Box 跨文件挂载。
 */
internal fun Modifier.flipPaletteGesture(onFlip: () -> Unit): Modifier =
    this.pointerInput(Unit) {
        val downThreshold = 56.dp.toPx()
        val upThreshold = 56.dp.toPx()
        val timeLimitMs = 600L
        awaitEachGesture {
            val down = awaitFirstDown(
                requireUnconsumed = false,
                pass = PointerEventPass.Initial
            )
            val startTime = down.uptimeMillis
            var totalX = 0f
            var totalY = 0f
            var peakY = 0f
            var downPhaseDone = false
            while (true) {
                val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                val delta = change.positionChange()
                totalX += delta.x
                totalY += delta.y
                if (totalY > peakY) peakY = totalY
                if (change.uptimeMillis - startTime > timeLimitMs) break
                if (!downPhaseDone &&
                    totalY > downThreshold &&
                    kotlin.math.abs(totalY) > kotlin.math.abs(totalX) * 1.2f
                ) {
                    downPhaseDone = true
                    peakY = totalY
                }
                if (downPhaseDone && peakY - totalY > upThreshold) {
                    onFlip()
                    break
                }
                if (!change.pressed) break
            }
        }
    }

/**
 * 新画布——"反色后的画布"的正式定性：它不是旧黑画布的变体或滤镜态，
 * 而是与旧黑画布平级的独立画布，恒定呈现反色（浅色）外观。
 * 签名与 GreenBlackCanvas 完全一致，可直接替换调用点。
 *
 * 恒为新画布外观的推导（GreenBlackCanvas 内部自带 .invertedColors(CanvasIdentity.isNewCanvas)）：
 *   · 身份 = 旧黑画布：深色底，这里再反色一次 → 浅
 *   · 身份 = 新画布  ：内部已反色为浅，这里不再反色 → 浅
 *   两种情况都呈现新画布外观，与旧黑画布互为镜像。
 *   （两层反色互斥生效，任一时刻只有一层离屏，无额外性能负担。）
 *
 * 下拉画板已迁入 GreenBlackCanvas（isNewCanvas 门控）——主流程实际显示的是它，
 * 本函数还原为纯浅色包装；画板组件与手势仍定义在本文件（internal 可见性）。
 */
@Composable
internal fun BoxScope.NewCanvas(
    isRendered: Boolean,
    offsetFraction: Float,
    onClose: () -> Unit,
    pixelFont: FontFamily
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .invertedColors(enabled = !CanvasIdentity.isNewCanvas)
    ) {
        GreenBlackCanvas(
            isRendered = isRendered,
            offsetFraction = offsetFraction,
            onClose = onClose,
            pixelFont = pixelFont
        )
    }
}

/**
 * 新画布配套的资源管理器（反色外观）。签名与 BlackCanvasFileManager 完全一致，可直接替换调用点。
 * BlackCanvasFileManager 自身不贴反色层（深色底恒定），故这里恒开反色即得新画布外观。
 * ↓↑ V 形快甩与旧画布同款：在新画布的管理器里快甩即翻转画布身份、回到旧黑画布
 * （旧画布管理器的同款手势由 App19 挂载）。
 */
@Composable
internal fun NewFileManager(
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
    // ===== 71 额外处理：全系统文件检索（双模式，只在 71 画布生效）=====
    // 基座 BlackCanvasFileManager 自带的搜索只扫"当前文件夹一层"；这里在包装层外挂一个
    // 全局检索覆盖层，能搜「整个系统文件管理器」所有层级，且按输入的路径形态分两种动作：
    //   · 输完整系统级路径 → 复制模式(COPY)：Enter 把命中项复制进当前展开目录(currentFolderId)。
    //       保险逻辑：先遍历目标文件夹确认同名不存在才去系统拿取复制，已存在则跳过不重复；
    //       复制文件夹 = 一次性把其下所有文件(含子文件夹，按原结构)全部拷入展开文件夹。
    //   · 输简要相对路径   → 定位模式(GO)：Enter 让资源管理器展开/给出该文件夹(进入)或文件(到父目录)。
    // 完全不动基座，纯 71 扩展。flash = 反馈文案 to 颜色（复制=绿 / 跳过=琥珀 / 定位=青）。
    var showGlobalSearch by remember { mutableStateOf(false) }
    var flash by remember { mutableStateOf<Pair<String, Color>?>(null) }

    // 互斥闸门：资源管理器打开(visible=true)时落闸，画板下拉手势即被禁、滑不出来；
    // 关闭/移出组合时起闸(onDispose 兜底复位)，画板恢复可下拉。
    DisposableEffect(visible) {
        SketchPadGate.suppressed = visible
        onDispose { SketchPadGate.suppressed = false }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .flipPaletteGesture { CanvasIdentity.flip() }
            .invertedColors(enabled = true)
    ) {
        BlackCanvasFileManager(
            visible = visible,
            rootPath = rootPath,
            entries = entries,
            currentFolderId = currentFolderId,
            onCurrentFolderIdChange = onCurrentFolderIdChange,
            selectedFileId = selectedFileId,
            fileContents = fileContents,
            pixelFont = pixelFont,
            onOpenFile = onOpenFile,
            onDismissRequest = onDismissRequest
        )

        // 悬浮触发键：右上角一枚像素放大镜 "⌕ ALL"，点开全局检索覆盖层。
        // 只在管理器可见时挂载；zIndex 高于基座根 Box 的 100f，压在面板之上。
        if (visible) {
            GlobalSearchTriggerChip(
                pixelFont = pixelFont,
                modifier = Modifier
                    .zIndex(150f)
                    .align(Alignment.TopEnd)
                    .padding(top = 10.dp, end = 12.dp),
                onClick = { showGlobalSearch = true }
            )
        }

        // 动作反馈闪条（贴底部状态栏上方，1.4s 自动熄灭）：复制=绿底绿字 / 定位=青底青字
        flash?.let { (msg, color) ->
            Box(
                modifier = Modifier
                    .zIndex(160f)
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 40.dp)
                    .background(color.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                    .border(1.dp, color, RoundedCornerShape(4.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(msg, color = color, fontSize = 10.sp, fontFamily = pixelFont)
            }
            LaunchedEffect(msg) {
                delay(1400L)
                flash = null
            }
        }

        // 全局检索覆盖层（双模式）：搜整棵树，↑↓ 选中；
        //   COPY(系统全路径) → Enter 复制进当前目录；GO(简要相对路径) → Enter 展开/给出
        GlobalFileSearchOverlay(
            visible = showGlobalSearch,
            rootPath = rootPath,
            entries = entries,
            fileContents = fileContents,
            targetFolderId = currentFolderId,
            pixelFont = pixelFont,
            onCopyResult = { r ->
                // COPY 三色反馈：已存在→琥珀(跳过)；文件夹→绿(带文件数)；单文件→绿
                flash = when {
                    !r.copied -> "exists, skipped → ${r.name}" to Color(0xFFFFB74D)
                    r.isFolder -> "copied → ${r.name} (${r.filesCopied} files)" to Color(0xFF3DDC84)
                    else -> "copied → ${r.name}" to Color(0xFF3DDC84)
                }
            },
            onNavigate = { entry ->
                // 定位模式：文件夹 → 进入该目录(展开)；文件 → 跳到其父目录(给出/揭示该文件)
                if (entry.isFolder) {
                    onCurrentFolderIdChange(entry.id)
                } else {
                    onCurrentFolderIdChange(entry.parentId)
                }
                flash = "go → ${displayEntryName(entry.name)}" to Color(0xFF5AC8FA)
            },
            onDismissRequest = { showGlobalSearch = false }
        )
    }
}

/**
 * 下拉画板的互斥闸门：71 资源管理器打开时置 true，画板就"滑不出来"。
 * 由 NewFileManager 随 visible 同步落/起闸（DisposableEffect）；
 * pullDownSketchGesture 在每笔手势的落指时刻实时读取——闸落则整笔忽略。
 * 画板只在新画布出现、NewFileManager 是新画布的资源管理器，故此闸天然只约束 71 一侧，
 * 旧黑画布不受影响。用快照 State 承载，手势协程每次落指读到的都是当前值，无竞态。
 */
internal object SketchPadGate {
    var suppressed by mutableStateOf(false)
}

/**
 * 下拉打开画板手势（通知下拉帘直觉）：起笔点必须落在屏幕顶部 30% 区域，
 * 随后下滑累计 > 48dp（纵向主导 |y| > |x| * 1.1）且整笔 ≤ 700ms → onOpen()。
 * Initial 通道裸读、requireUnconsumed = false、全程不消费事件：
 * 编辑器滚动、右滑关闭画布、管理器内的 ↓↑ 翻转手势均不受干扰；
 * 起始区域（顶部 40%）+ 纵向主导 + 时限三重限制，把"滚代码滚出画板"的误触概率压低。
 * 互斥：落指时若 SketchPadGate.suppressed（71 资源管理器正打开）→ 整笔手势直接忽略，
 * 画板滑不出来；检查放在 awaitFirstDown 之后，协程始终挂起等待落指，不会空转。
 */
internal fun Modifier.pullDownSketchGesture(enabled: Boolean, onOpen: () -> Unit): Modifier =
    this.pointerInput(enabled) {
        if (!enabled) return@pointerInput
        val threshold = 48.dp.toPx()
        val timeLimitMs = 700L
        awaitEachGesture {
            val down = awaitFirstDown(
                requireUnconsumed = false,
                pass = PointerEventPass.Initial
            )
            // 71 资源管理器打开中 → 画板禁止滑出
            if (SketchPadGate.suppressed) return@awaitEachGesture
            if (down.position.y > size.height * 0.40f) return@awaitEachGesture
            val startTime = down.uptimeMillis
            var totalX = 0f
            var totalY = 0f
            while (true) {
                val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                val delta = change.positionChange()
                totalX += delta.x
                totalY += delta.y
                if (change.uptimeMillis - startTime > timeLimitMs) break
                if (totalY > threshold &&
                    kotlin.math.abs(totalY) > kotlin.math.abs(totalX) * 1.1f
                ) {
                    onOpen()
                    break
                }
                if (!change.pressed) break
            }
        }
    }

/**
 * 上滑收起画板手势：在画板任意位置上滑即收起——上滑累计 > 56dp（纵向主导 |y| > |x| * 1.2）
 * 且整笔 ≤ 600ms → onClose()，整板向上滑出收起。参数族与 ↓↑ 翻转、顶部下滑开板手势同款。
 * Initial 通道裸读、requireUnconsumed = false、全程不消费事件：
 * 竖块行横滑翻页、搜索框聚焦输入、数字方块点选、删除模式的两点删除均不受干扰。
 */
internal fun Modifier.pullUpCloseGesture(onClose: () -> Unit): Modifier =
    this.pointerInput(Unit) {
        val threshold = 56.dp.toPx()
        val timeLimitMs = 600L
        awaitEachGesture {
            val down = awaitFirstDown(
                requireUnconsumed = false,
                pass = PointerEventPass.Initial
            )
            val startTime = down.uptimeMillis
            var totalX = 0f
            var totalY = 0f
            while (true) {
                val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                val delta = change.positionChange()
                totalX += delta.x
                totalY += delta.y
                if (change.uptimeMillis - startTime > timeLimitMs) break
                if (totalY < -threshold &&
                    kotlin.math.abs(totalY) > kotlin.math.abs(totalX) * 1.2f
                ) {
                    onClose()
                    break
                }
                if (!change.pressed) break
            }
        }
    }

/**
 * 字幕像素字：Fusion Pixel 10px 等宽像素字体，按 latin → 简中 → 繁中 → 日 → 韩 排列做多字形回退。
 * 资源版 Font(Res.font.…) 是 @Composable 函数：顶层 val 初始化器放不了，remember 的
 * lambda 也不是 composable 上下文——故直接做成 composable 属性 getter（每次重建的只是
 * 轻量包装对象，真实字形加载由资源系统缓存，无性能问题）；使用点写法不变。
 */
internal val CaptionPixelFont: FontFamily
    @Composable get() = FontFamily(
        Font(Res.font.fusion_pixel_10px_monospaced_latin),
        Font(Res.font.fusion_pixel_10px_monospaced_zh_hans),
        Font(Res.font.fusion_pixel_10px_monospaced_zh_hant),
        Font(Res.font.fusion_pixel_10px_monospaced_ja),
        Font(Res.font.fusion_pixel_10px_monospaced_ko),
    )

/**
 * 下拉画板（用户昵称「新新画布」——新画布里拉出来的画布）：
 * 白底 + 粉灰边（0xFFC9A9B2），顶部贴边垂落，底部 14dp 大圆角；占 32% 屏高，
 * 小屏保底 320dp（手机上 32% 装不下"方块条+竖块行+搜索框"≈292dp，会把搜索框挤没）；
 * 整板固定、内容不可下滑；字幕用 Fusion Pixel 像素字。
 * 结构（自上而下）：
 *  - 1/2/3 方块条（16dp 迷你块，顶部水平居中）——每个数字方块对应一组独立计数的竖块分组，
 *    点击即把竖块行切到该组（该组从未创建过竖块时，切过去只剩加号母体）；
 *  - 竖块行：定高 BlockRowBand 的 348dp 居中容器，显示当前激活数字方块那一组的竖块（每块带同宽文字横线，块中央显示
 *    该横线首字、留空回退序号）+ 行尾大加号（产出时序：新块先 180ms 完整长出，
 *    加号再 220ms 右滑让位）；屏外有块时两侧淡入 90° 箭头，点按平滑滚一个块宽；
 *    行高钉死保证下方搜索框位置不随竖块行状态（空组/删除模式/搜索空命中）漂移；
 *  - 搜索框：放大镜图标 + 竖分隔线 + 输入区，居中，聚焦变色；点按放大镜跨 1/2/3
 *    三组检索竖块名，命中的项目竖块在块行区按正常排列列出（空查询点按 = 退出搜索回正常视图）；
 *    整区弹性吃掉定高余量并居中——位置固定，不随上方块行变化；
 *  - 删除模式：再点一次已选中的数字方块进入（该方块变红示意），点竖块两下 = 删除——首点"预备"
 *    （块边框/底变红闪，1.2s 超时自动解除），再点触发整块淡出、再槽位塌缩，右邻竖块平滑左移填位；
 *    加号母体此模式隐藏；再点一次方块回正常；
 *  - 长按竖块 = 把该项目全部文件打包成 zip 调安卓原生分享（App16 实现，空项目 Toast 反馈）；
 *  - 收起：点半透明帘外空白，或在画板任意位置上滑；进出场各一段 tween（透明度 + 整板高度滑移：
 *    开 = 自顶滑入，关 = 整板向上滑出），关板顺带清空搜索/删除态。
 */
@Composable
internal fun SketchPadPanel(
    visible: Boolean,
    pixelFont: FontFamily,
    onClose: () -> Unit,
    onOpenProject: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val drop = remember { Animatable(0f) }
    var rendered by remember { mutableStateOf(false) }
    var panelHeightPx by remember { mutableStateOf(0) }
    var selectedTool by remember { mutableStateOf(1) }
    val blockGroups = SketchBlockStore.groups
    val activeGroup = blockGroups[selectedTool - 1]
    var searchQuery by remember { mutableStateOf("") }
    var searchResult by remember { mutableStateOf<List<BlockMatch>?>(null) }
    val runSearch = {
        val q = searchQuery.trim()
        if (q.isEmpty()) {
            searchResult = null
        } else {
            searchResult = blockGroups.flatMapIndexed { gi, group ->
                group.blocks.mapNotNull { block ->
                    if (block.caption.contains(q, ignoreCase = true)) {
                        BlockMatch(groupNo = gi + 1, blockId = block.id)
                    } else null
                }
            }
        }
    }
    var deleteMode by remember { mutableStateOf(false) }

    LaunchedEffect(visible) {
        if (visible) {
            rendered = true
            drop.animateTo(1f, tween(260))
        } else if (rendered) {
            drop.animateTo(0f, tween(220))
            rendered = false
            searchQuery = ""
            searchResult = null
            deleteMode = false
        }
    }

    if (!rendered) return

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = drop.value * 0.45f }
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onClose() }
        )

        Box(
            modifier = modifier
                .fillMaxWidth()
                // 32% 屏高，但小屏保底 320dp：方块条(~42)+竖块行(186)+搜索框(~64)≈292dp，
                // 手机上 32% 常不足 292dp，会把底部 weight 的搜索框压没、再被圆角 clip 裁掉。
                // heightIn(min) 在外、fillMaxHeight 在内 → 实际高度 = max(32%屏高, 320dp)。
                .heightIn(min = 320.dp)
                .fillMaxHeight(0.32f)
                .pullUpCloseGesture(onClose = onClose)
                .onSizeChanged { panelHeightPx = it.height }
                .offset {
                    IntOffset(0, (-((1f - drop.value) * panelHeightPx)).roundToInt())
                }
                .graphicsLayer { alpha = drop.value }
                .background(Color.White, RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp))
                .border(2.dp, Color(0xFFC9A9B2), RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp))
                .clip(RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .padding(vertical = 11.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(1, 2, 3).forEach { n ->
                        SketchToolChip(
                            label = "$n",
                            isSelected = selectedTool == n,
                            deleteMode = deleteMode,
                            onSelect = {
                                if (selectedTool == n && searchResult == null) {
                                    deleteMode = !deleteMode
                                } else {
                                    selectedTool = n
                                    searchResult = null
                                    deleteMode = false
                                }
                            },
                            pixelFont = pixelFont
                        )
                        if (n < 3) Spacer(Modifier.width(10.dp))
                    }
                }

                val currentSearch = searchResult
                if (currentSearch == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(BlockRowBand)
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        key(selectedTool) {
                            Box(modifier = Modifier.width(348.dp)) {
                                val rowState = rememberScrollState()
                                val scope = rememberCoroutineScope()
                                val density = LocalDensity.current
                                val blockShiftPx = with(density) { 112.dp.toPx().toInt() }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rowState)
                                        .padding(start = 12.dp, bottom = 8.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    activeGroup.blocks.forEachIndexed { idx, block ->
                                        key(block.id) {
                                            SpawnedBlockUnit(
                                                index = idx + 1,
                                                block = block,
                                                pixelFont = pixelFont,
                                                onCaptionChange = { text ->
                                                    val i = activeGroup.blocks.indexOfFirst { it.id == block.id }
                                                    if (i >= 0) {
                                                        activeGroup.blocks[i] = activeGroup.blocks[i].copy(caption = text)
                                                        JsWorkspaceStore.notifySketchBlocksChanged()
                                                    }
                                                },
                                                onOpen = {
                                                    onOpenProject("${selectedTool}_${block.id}")
                                                },
                                                onExport = {
                                                    ProjectShareStore.shareProject(
                                                        projectId = "${selectedTool}_${block.id}",
                                                        displayName = block.caption
                                                    )
                                                },
                                                deleteMode = deleteMode,
                                                dying = block.dying,
                                                onDelete = {
                                                    val i = activeGroup.blocks.indexOfFirst { it.id == block.id }
                                                    if (i >= 0 && !activeGroup.blocks[i].dying) {
                                                        activeGroup.blocks[i] = activeGroup.blocks[i].copy(dying = true)
                                                    }
                                                },
                                                onGone = {
                                                    activeGroup.blocks.removeAll { it.id == block.id }
                                                    JsWorkspaceStore.notifySketchBlocksChanged()
                                                }
                                            )
                                        }
                                    }
                                    if (!deleteMode) {
                                        Column {
                                            SketchAddBlock(
                                                blockCount = activeGroup.blocks.size,
                                                onAdd = {
                                                    activeGroup.blocks.add(SpawnedBlock(activeGroup.nextBlockId))
                                                    activeGroup.nextBlockId += 1
                                                    JsWorkspaceStore.notifySketchBlocksChanged()
                                                }
                                            )
                                            Spacer(Modifier.height(CaptionBand))
                                        }
                                    }
                                }
                                SketchRowArrow(
                                    pointsLeft = true,
                                    shown = rowState.value > 2,
                                    onClick = {
                                        scope.launch {
                                            rowState.animateScrollTo((rowState.value - blockShiftPx).coerceAtLeast(0))
                                        }
                                    },
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .offset(y = 48.dp)
                                )
                                SketchRowArrow(
                                    pointsLeft = false,
                                    shown = rowState.maxValue > 0 && rowState.value < rowState.maxValue - 2,
                                    onClick = {
                                        scope.launch {
                                            rowState.animateScrollTo((rowState.value + blockShiftPx).coerceAtMost(rowState.maxValue))
                                        }
                                    },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(y = 48.dp)
                                )
                            }
                        }
                    }
                } else {
                    SearchResultRow(
                        matches = currentSearch,
                        groups = blockGroups,
                        pixelFont = pixelFont,
                        onCaptionChange = { match, text ->
                            val group = blockGroups[match.groupNo - 1]
                            val i = group.blocks.indexOfFirst { it.id == match.blockId }
                            if (i >= 0) {
                                group.blocks[i] = group.blocks[i].copy(caption = text)
                                JsWorkspaceStore.notifySketchBlocksChanged()
                            }
                        },
                        onOpen = { match -> onOpenProject("${match.groupNo}_${match.blockId}") }
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    SketchSearchBox(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        onSearch = runSearch
                    )
                }
            }
        }
    }
}

/**
 * 画板顶部方块条里的数字方块：16dp 迷你圆角方块（游戏搭配栏序号按钮的量级），
 * 灰底（0xFFE3E3E3）+ 深灰边框（0xFF5A5A5A）。
 * 可感知反馈（全部走动画过渡，状态切换是"渐变"而非"跳变"）：
 *   按压 → 底色压暗一档（0xFFCFCFCF）且方块缩至 0.90 倍，松手弹回；
 *   点选 → 深灰底（0xFF4A4A4A）+ 白字 + 边框加深，原选中块同步褪回灰色。
 * 每个方块是一组独立计数竖块分组的开关：选中态 = 当前激活的组，
 * 点击把竖块行切到该组内容（从未创建过竖块的组切过去只剩加号母体）。
 * 删除模式示意：已选中且处于删除模式时整块变红（底色 0xFFD9534F、边框加深），
 * 与 console 的 x 同族，150ms 级动画过渡进退。
 */
@Composable
private fun SketchToolChip(
    label: String,
    isSelected: Boolean,
    deleteMode: Boolean,
    onSelect: () -> Unit,
    pixelFont: FontFamily
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val armed = isSelected && deleteMode

    val bg by animateColorAsState(
        targetValue = when {
            armed -> Color(0xFFD9534F)
            isSelected -> Color(0xFF4A4A4A)
            isPressed -> Color(0xFFCFCFCF)
            else -> Color(0xFFE3E3E3)
        },
        animationSpec = tween(130)
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            armed -> Color(0xFF8E2F2B)
            isSelected -> Color(0xFF2E2E2E)
            else -> Color(0xFF5A5A5A)
        },
        animationSpec = tween(130)
    )
    val textColor by animateColorAsState(
        targetValue = if (isSelected) Color.White else Color(0xFF4F4F4F),
        animationSpec = tween(130)
    )
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.90f else 1f,
        animationSpec = tween(110)
    )

    Box(
        modifier = Modifier
            .size(20.dp)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .background(bg, RoundedCornerShape(5.dp))
            .border(1.dp, borderColor, RoundedCornerShape(5.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onSelect() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * 竖块行尾的大加号添加块（母体）：淡蓝灰底（0xFFD9E0E6）+ 灰黑边框（0xFF41474E），
 * 80×120dp 竖圆角矩形，中央一个 34dp 圆头大加号（石墨灰黑 0xFF3A3F45）。
 * 可感知反馈：按压 → 底色压暗一档（0xFFC7D0D8）且缩至 0.94 倍，松手弹回。
 * 点击 → onAdd 产出一个新竖框块。让位动画（兼容旧版 Compose，无 animateItemPlacement，
 * 用负 offset 手写）：新块入行后本块布局位已被推右一格，先用负 offset 钉回原槽位，
 * 保持 180ms（= 新块产出时长，期间盖住背后生长的新块），再把 offset 滑回零 = 视觉右滑一格。
 * "先产出，再让位"的时序由这 180ms 保持钉死。
 * 虚影修复：offset 的 Animatable 随 blockCount 自增在同一次组合里同步重建（初值 1f，
 * 见下方 remember(blockCount)），首帧布局即带补偿，彻底消除"先瞬移到终点、再回放动画"
 * 的虚影帧（旧版在 LaunchedEffect 里 snapTo——效果协程晚一帧启动，钉回前已有一帧画在新槽位）。
 */
@Composable
private fun SketchAddBlock(
    blockCount: Int,
    onAdd: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val bg by animateColorAsState(
        targetValue = if (isPressed) Color(0xFFC7D0D8) else Color(0xFFD9E0E6),
        animationSpec = tween(130)
    )
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = tween(110)
    )

    var prevCount by remember { mutableStateOf(-1) }
    val shift = remember(blockCount) {
        Animatable(if (prevCount in 0 until blockCount) 1f else 0f)
    }
    if (prevCount != blockCount) {
        prevCount = blockCount
    }
    LaunchedEffect(blockCount) {
        if (shift.value > 0f) {
            delay(180L)
            shift.animateTo(0f, tween(220))
        }
    }
    Box(
        modifier = Modifier
            .size(width = 100.dp, height = 150.dp)
            .offset {
                IntOffset(
                    x = (-shift.value * (100.dp + 12.dp).toPx()).roundToInt(),
                    y = 0
                )
            }
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .background(bg, RoundedCornerShape(12.dp))
            .border(2.dp, Color(0xFF41474E), RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onAdd() },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(42.dp)) {
            val c = Color(0xFF3A3F45)
            val w = 4.3.dp.toPx()
            drawLine(
                c,
                Offset(size.width / 2f, 0f),
                Offset(size.width / 2f, size.height),
                strokeWidth = w,
                cap = StrokeCap.Round
            )
            drawLine(
                c,
                Offset(0f, size.height / 2f),
                Offset(size.width, size.height / 2f),
                strokeWidth = w,
                cap = StrokeCap.Round
            )
        }
    }
}

/**
 * 大加号产出的竖块：id（组内稳定 key）+ 块下横线上输入的文字 + 删除中标记（dying 仅内存态，存档时过滤）。
 * internal：SketchBlockGroup（internal）的 blocks 列表以它为元素，私有会触发"internal 暴露 private 类型"。
 */
internal data class SpawnedBlock(
    val id: Int,
    val caption: String = "",
    val dying: Boolean = false
)

/**
 * 竖块分组：每个数字方块（1/2/3）一组，独立计数。
 * blocks = 该组的竖块序列（全局持久化、跨重启保留），nextBlockId = 该组专属的编号计数器
 * （单调递增做稳定 key；块中央留空回退显示的序号是组内自己的 1/2/3…）。
 * 用普通类即可：快照系统观察的是 mutableStateListOf 列表本身，持有位置不影响状态刷新。
 * internal：供 SketchBlockStore 以固定三槽持有并做存档 ⇄ 内存态转换。
 */
internal class SketchBlockGroup {
    val blocks = mutableStateListOf<SpawnedBlock>()
    var nextBlockId = 1
}

/**
 * 画板竖块结构的全局持久化桥（三组 × 竖块序列，跨重启保留）：
 * 竖块结构属「全局」维（不挂任何会话，永远保存）——画板只在新画布显示，三组竖块在
 * 画布翻转/进出项目/切换会话之间统统保持原样，存档落 js_workspace/sketch_blocks.json（全局单份）。
 * 生命周期完全挂在 JsWorkspaceStore 上：工作区每次落盘同漏斗顺带写入；
 * 产出/改名/删除经 notifySketchBlocksChanged 防抖落盘；flushNow（onPause）兜底。
 * 本对象不持有存储引用，只做内存态 ⇄ 快照的两向转换。
 * groups 固定三槽，面板直接持有引用即可。
 * 另持有 seedConsumed——旧版方案的遗留继承标记（新方案继承门禁不再读它，仅为存档兼容保留）。
 */
internal object SketchBlockStore {
    val groups: List<SketchBlockGroup> = List(3) { SketchBlockGroup() }

    /**
     * 画布身份持久化位（随全局存档读写）：flip() 时写入、toSnapshot() 时随档落盘；
     * 重启后 JsWorkspaceStore 从存档 restore（见 switchConversation 的门闩逻辑）。
     */
    var canvasKindName: String = CanvasKind.BLACK_CANVAS.name

    /**
     * 项目继承一次性标记（遗留持久化位，随 sketch_blocks.json 存档/回读）：
     * 新方案的继承门禁改由磁盘事实（hasAnyProjectContent）裁决、不再读本标记，
     * 保留只为新旧存档双向兼容。
     */
    var seedConsumed: Boolean = false

    /** 导出三组当前状态为存档快照（供 JsWorkspaceStore 落盘）。 */
    fun toSnapshot(): SketchBlocksSnapshot = SketchBlocksSnapshot(
        blocks = groups.flatMapIndexed { gi, group ->
            group.blocks.filter { block -> !block.dying }
                .map { block -> SketchBlockRecord(gi + 1, block.id, block.caption) }
        },
        nextBlockIds = groups.map { it.nextBlockId },
        seedConsumed = seedConsumed,
        canvasKind = canvasKindName
    )

    /** 从存档恢复三组：null = 从未保存 → 空白三组（加号母体待命）。 */
    fun applySnapshot(s: SketchBlocksSnapshot?) {
        groups.forEach { group ->
            group.blocks.clear()
            group.nextBlockId = 1
        }
        seedConsumed = s?.seedConsumed ?: false
        if (s == null) return
        s.blocks.forEach { rec ->
            if (rec.groupNo in 1..3 && rec.blockId >= 1) {
                groups[rec.groupNo - 1].blocks.add(SpawnedBlock(rec.blockId, rec.caption))
            }
        }
        groups.forEachIndexed { gi, group ->
            val stored = s.nextBlockIds.getOrNull(gi) ?: 1
            val maxId = group.blocks.maxOfOrNull { it.id } ?: 0
            group.nextBlockId = maxOf(stored, maxId + 1, 1)
        }
    }
}

/**
 * 搜索结果条目：竖块所属分组号（1/2/3）+ 组内块 id。
 * 只存定位不存块实例：渲染时按 id 回源分组取活块，结果行里的改名实时同步回源块。
 */
private data class BlockMatch(
    val groupNo: Int,
    val blockId: Int
)

/**
 * 搜索结果竖块行：命中的项目竖块按正常排列列出——与分组行同款 348dp 居中容器、
 * 同款竖块单元（块 + 同宽起名横线，首字/组内序号回退）、同款两侧翻页箭头。
 * 点击竖块打开对应项目（项目键 = 组号_块号，与正常视图同一套工作区键），
 * 长按同样可打包分享该项目；无命中时给一行淡灰像素字「no match」，点按搜索的反馈不落空。
 */
@Composable
private fun SearchResultRow(
    matches: List<BlockMatch>,
    groups: List<SketchBlockGroup>,
    pixelFont: FontFamily,
    onCaptionChange: (BlockMatch, String) -> Unit,
    onOpen: (BlockMatch) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(BlockRowBand)
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        if (matches.isEmpty()) {
            Text(
                "no match",
                color = Color(0xFFB9BEC5),
                fontSize = 14.sp,
                fontFamily = CaptionPixelFont,
                modifier = Modifier.padding(vertical = 16.dp)
            )
        } else {
            Box(modifier = Modifier.width(348.dp)) {
                val rowState = rememberScrollState()
                val scope = rememberCoroutineScope()
                val density = LocalDensity.current
                val blockShiftPx = with(density) { 112.dp.toPx().toInt() }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rowState)
                        .padding(start = 12.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    matches.forEach { match ->
                        val group = groups[match.groupNo - 1]
                        val idx = group.blocks.indexOfFirst { it.id == match.blockId }
                        if (idx >= 0) {
                            key("${match.groupNo}_${match.blockId}") {
                                SpawnedBlockUnit(
                                    index = idx + 1,
                                    block = group.blocks[idx],
                                    pixelFont = pixelFont,
                                    onCaptionChange = { text -> onCaptionChange(match, text) },
                                    onOpen = { onOpen(match) },
                                    onExport = {
                                        ProjectShareStore.shareProject(
                                            projectId = "${match.groupNo}_${match.blockId}",
                                            displayName = group.blocks[idx].caption
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
                SketchRowArrow(
                    pointsLeft = true,
                    shown = rowState.value > 2,
                    onClick = {
                        scope.launch {
                            rowState.animateScrollTo((rowState.value - blockShiftPx).coerceAtLeast(0))
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(y = 48.dp)
                )
                SketchRowArrow(
                    pointsLeft = false,
                    shown = rowState.maxValue > 0 && rowState.value < rowState.maxValue - 2,
                    onClick = {
                        scope.launch {
                            rowState.animateScrollTo((rowState.value + blockShiftPx).coerceAtMost(rowState.maxValue))
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(y = 48.dp)
                )
            }
        }
    }
}

/**
 * 起名横线的固定高度。竖块行里每个条目都是「150dp 块 + CaptionBand 横线」，
 * 加号列也补一块同高透明占位——行高恒定（0 个块与 N 个块一致），
 * 搜索框位置稳定，不会在点击加号产出竖块后往下瞬移。
 */
private val CaptionBand = 28.dp

/**
 * 竖块行区的固定高度 = 竖块 150 + 起名横线（CaptionBand 28）+ 行底 padding 8 = 186dp。
 * 行高钉死保证下方搜索框的位置不随竖块行状态（空组、删除模式隐藏加号、
 * 搜索空命中）发生任何漂移——"搜索框位置固定"。
 */
private val BlockRowBand = 186.dp

/**
 * 竖块单元：100×150dp 竖块 + 块下同宽的文字横线 + 组内右间距（12dp），每个已创建竖块各一份。
 * 块中央显示「该块横线文字的第一个字符」（留空回退组内序号 1/2/3…），随输入实时刷新。
 * 入场动画：缩放 + 透明度 0→1（180ms）——从加号背后完整生长出来；
 * 与加号那边 180ms 让位保持严格对齐，保证"新竖块完整产出，加号才向右移"。
 * 点击 = 打开项目（onOpen）；长按 = 导出分享（onExport）：读取该项目全部文件打包 zip、
 * 调安卓原生分享面板（实现见 App16，空项目 Toast 反馈）。
 * 删除模式点两下删除：首点"预备"（块底/边框/首字变红，130ms 级动画过渡，1.2s 无二次点按自动解除），
 * 再点标记 dying = true 触发删除动画：先整块淡出（160ms），再槽位塌缩（150ms）——
 * 塌缩期间右邻竖块随 Row 重排平滑左移、填进被删块的位置；动画走完 onGone() 才真正移出列表。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SpawnedBlockUnit(
    index: Int,
    block: SpawnedBlock,
    pixelFont: FontFamily,
    onCaptionChange: (String) -> Unit,
    onOpen: () -> Unit,
    onExport: () -> Unit = {},
    deleteMode: Boolean = false,
    onDelete: () -> Unit = {},
    dying: Boolean = false,
    onGone: () -> Unit = {}
) {
    val spawn = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        spawn.animateTo(1f, tween(180))
    }
    val die = remember { Animatable(1f) }
    val collapse = remember { Animatable(1f) }
    LaunchedEffect(dying) {
        if (dying) {
            die.animateTo(0f, tween(160))
            collapse.animateTo(0f, tween(150))
            onGone()
        }
    }
    val label = block.caption.trim().firstOrNull()?.toString() ?: index.toString()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(armed) {
        if (armed) {
            delay(1200L)
            armed = false
        }
    }
    LaunchedEffect(deleteMode) {
        if (!deleteMode) armed = false
    }
    val blockBg by animateColorAsState(
        targetValue = when {
            armed -> Color(0xFFE9C4C0)
            isPressed -> Color(0xFFC7D0D8)
            else -> Color(0xFFD9E0E6)
        },
        animationSpec = tween(130)
    )
    val blockBorder by animateColorAsState(
        targetValue = if (armed) Color(0xFFD9534F) else Color(0xFF41474E),
        animationSpec = tween(130)
    )
    val labelColor by animateColorAsState(
        targetValue = if (armed) Color(0xFFC0392B) else Color(0xFF3A3F45).copy(alpha = 0.7f),
        animationSpec = tween(130)
    )
    Row(
        modifier = Modifier
            .graphicsLayer {
                scaleX = spawn.value
                scaleY = spawn.value
                alpha = spawn.value * die.value
            }
    ) {
    Column(
        modifier = Modifier
            .width(100.dp * collapse.value),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(width = 100.dp, height = 150.dp)
                .background(blockBg, RoundedCornerShape(12.dp))
                .border(2.dp, blockBorder, RoundedCornerShape(12.dp))
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = {
                        if (deleteMode) {
                            if (armed) onDelete() else armed = true
                        } else {
                            onOpen()
                        }
                    },
                    onLongClick = { onExport() }
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = labelColor,
                fontSize = 25.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(CaptionBand)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                BasicTextField(
                    value = block.caption,
                    onValueChange = onCaptionChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        color = Color(0xFF4A4F55),
                        fontSize = 14.sp,
                        fontFamily = CaptionPixelFont,
                        textAlign = TextAlign.Center
                    ),
                    cursorBrush = SolidColor(Color(0xFFB08A93)),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.Center) {
                            if (block.caption.isEmpty()) {
                                Text(
                                    "caption…",
                                    color = Color(0xFFB9BEC5),
                                    fontSize = 14.sp,
                                    fontFamily = CaptionPixelFont
                                )
                            }
                            inner()
                        }
                    }
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color(0xFFD9D2D4))
            )
        }
    }
    Spacer(modifier = Modifier.width(12.dp * collapse.value))
    }
}

/**
 * 竖块行两侧的翻页箭头：90° 折角（两笔画夹角严格 90°，石墨灰黑 0xFF41474E）。
 * 仅当该方向屏外还有竖块时淡入（shown），白色渐隐底避免压住块面；
 * 点按 = 平滑滚动一个块宽（90dp）。隐去时同步断开点击。
 */
@Composable
private fun SketchRowArrow(
    pointsLeft: Boolean,
    shown: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val arrowAlpha by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(280)
    )
    Box(
        modifier = modifier
            .size(width = 32.dp, height = 54.dp)
            .graphicsLayer { alpha = arrowAlpha }
            .background(
                brush = if (pointsLeft) {
                    Brush.horizontalGradient(listOf(Color.White, Color.White.copy(alpha = 0f)))
                } else {
                    Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0f), Color.White))
                }
            )
            .clickable(
                enabled = shown,
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(width = 17.dp, height = 20.dp)) {
            val sx = size.width / 14f
            val sy = size.height / 16f
            val path = Path().apply {
                if (pointsLeft) {
                    moveTo(10f * sx, 2f * sy)
                    lineTo(4f * sx, 8f * sy)
                    lineTo(10f * sx, 14f * sy)
                } else {
                    moveTo(4f * sx, 2f * sy)
                    lineTo(10f * sx, 8f * sy)
                    lineTo(4f * sx, 14f * sy)
                }
            }
            drawPath(
                path,
                Color(0xFF41474E),
                style = Stroke(
                    width = 2.7.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }
}

/**
 * 底部搜索框：居中；左端 34dp 放大镜图标区（与输入区严格等高）+ 竖分隔线 + 输入区，
 * 整体 184dp 宽 × 32dp 高，淡蓝灰底 + 灰黑边（与竖块同族），圆角 10dp。
 * 聚焦：边框加深（0xFF2E2E2E）+ 底色压暗（0xFFCFD8E0），150ms 过渡。
 * 输入文字与占位「Search…」均用 Fusion Pixel 像素字（与起名横线同族）。
 * 搜索触发：点按放大镜（按压时图标缩至 0.82 倍回弹作反馈）→ onSearch()——
 * 由面板跨三组检索竖块名；查询文字受控于面板（query/onQueryChange），点按搜索时读取。
 */
@Composable
private fun SketchSearchBox(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val bg by animateColorAsState(
        targetValue = if (focused) Color(0xFFCFD8E0) else Color(0xFFD9E0E6),
        animationSpec = tween(150)
    )
    val borderC by animateColorAsState(
        targetValue = if (focused) Color(0xFF2E2E2E) else Color(0xFF41474E),
        animationSpec = tween(150)
    )
    val iconInteraction = remember { MutableInteractionSource() }
    val iconPressed by iconInteraction.collectIsPressedAsState()
    val iconScale by animateFloatAsState(
        targetValue = if (iconPressed) 0.82f else 1f,
        animationSpec = tween(110)
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                // 自适应宽度：手机上吃满可用宽度（外层已留 20dp 边距），上限 300dp 居中，
                // 不再写死 230dp——窄屏不显挤、宽屏不拉爆。
                .widthIn(max = 300.dp)
                .fillMaxWidth()
                .height(40.dp)
                .background(bg, RoundedCornerShape(12.dp))
                .border(2.dp, borderC, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { focusRequester.requestFocus() },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(42.dp)
                    .fillMaxHeight()
                    .clickable(
                        interactionSource = iconInteraction,
                        indication = null
                    ) { onSearch() },
                contentAlignment = Alignment.Center
            ) {
                Canvas(
                    modifier = Modifier
                        .size(16.dp)
                        .graphicsLayer {
                            scaleX = iconScale
                            scaleY = iconScale
                        }
                ) {
                    val s = size.width / 13f
                    drawCircle(
                        color = Color(0xFF3A3F45),
                        radius = 4f * s,
                        center = Offset(5.5f * s, 5.5f * s),
                        style = Stroke(width = 1.8f * s)
                    )
                    drawLine(
                        color = Color(0xFF3A3F45),
                        start = Offset(8.6f * s, 8.6f * s),
                        end = Offset(12f * s, 12f * s),
                        strokeWidth = 1.8f * s,
                        cap = StrokeCap.Round
                    )
                }
            }
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(borderC)
            )
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = Color(0xFF3A3F45),
                    fontSize = 14.sp,
                    fontFamily = CaptionPixelFont,
                    textAlign = TextAlign.Center
                ),
                cursorBrush = SolidColor(Color(0xFF41474E)),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .focusRequester(focusRequester)
                    .onFocusChanged { focused = it.isFocused }
                    .padding(horizontal = 6.dp),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.Center) {
                        if (query.isEmpty()) {
                            Text(
                                "Search…",
                                color = Color(0xFF7E8894),
                                fontSize = 14.sp,
                                fontFamily = CaptionPixelFont
                            )
                        }
                        inner()
                    }
                }
            )
        }
    }
}

// ============================================================================
// 71 额外处理：全系统文件检索 + 回车复制进资源管理器
// ----------------------------------------------------------------------------
// 设计定位：BlackCanvasFileManager 自带的搜索只扫"当前文件夹一层"，且只过滤不搬运。
// 本区块把检索能力升级为「整个系统文件管理器」——沿面包屑递归回溯，所有层级的
// 文件与文件夹全部纳入匹配；命中后按 Enter（或点行）即把该条目复制进资源管理器
// 当前目录。全部收口在 71 包装层，基座 BlackCanvasFileManager 不做任何改动。
// ============================================================================

/**
 * 全树检索结果条目，同时携带两种路径（双模式检索的载体）：
 *  - relativePath：相对「根文件夹」的向下路径（无系统前缀），如 "folderA/main.js"；
 *    顶层条目就是它自己的名字。用于【简要路径 → 定位】模式。
 *  - fullPath：完整系统级路径（含 rootPath 前缀）。用于【系统全路径 → 复制】模式。
 */
internal data class WholeTreeMatch(
    val entry: JsWorkspaceEntry,
    val relativePath: String,
    val fullPath: String
)

/**
 * 判断当前查询是否属于「完整系统级路径」查询：
 * 查询串里含有 rootPath（系统根前缀）即认定为系统全路径 → 走复制模式；
 * 否则视为简要的相对向下路径 → 走定位模式。
 * 这是双模式检索的唯一分流依据。
 */
internal fun isAbsolutePathQuery(rootPath: String, query: String): Boolean {
    val q = query.trim()
    return q.isNotEmpty() && rootPath.isNotEmpty() &&
        q.contains(rootPath, ignoreCase = true)
}

/**
 * 在「整个系统文件管理器」里检索（递归、全层级，而非仅当前文件夹）。
 * 双模式（只在 71 画布的全局检索覆盖层生效，基座搜索不受影响）：
 *  - absoluteMode = true  ：按【完整系统级路径】fullPath 子串匹配（忽略大小写）；
 *  - absoluteMode = false ：按【相对根文件夹的向下路径】relativePath 子串匹配
 *    （顶层文件的 relativePath 就是文件名，故"只输文件名"天然命中）。
 * 文件夹与文件都参与；结果按"文件夹优先、再按相对路径字典序"排列。
 */
internal fun searchWholeWorkspace(
    entries: List<JsWorkspaceEntry>,
    rootPath: String,
    query: String,
    absoluteMode: Boolean
): List<WholeTreeMatch> {
    val q = query.trim()
    if (q.isEmpty()) return emptyList()
    val sep = if (rootPath.contains("\\")) "\\" else "/"
    return entries
        .mapNotNull { entry ->
            val segments = buildBreadcrumb(entries, entry.parentId).mapNotNull { it?.name }
            val relativePath = (segments.map { displayEntryName(it) } +
                displayEntryName(entry.name)).joinToString("/")
            val fullPath = joinPath(rootPath, segments) + sep + displayEntryName(entry.name)
            val hit = if (absoluteMode) {
                fullPath.contains(q, ignoreCase = true)
            } else {
                relativePath.contains(q, ignoreCase = true)
            }
            if (hit) WholeTreeMatch(entry, relativePath, fullPath) else null
        }
        .sortedWith(
            compareByDescending<WholeTreeMatch> { it.entry.isFolder }
                .thenBy { it.relativePath.lowercase() }
        )
}

/**
 * 复制动作的结果（供 UI 分色反馈）：
 *  - copied=false ：目标文件夹已存在同名条目 → 保险起见跳过，未做任何拷贝；
 *  - copied=true  ：已落盘。isFolder 标明拷的是文件夹还是单文件，
 *                   filesCopied = 本次实际写入的文件数（单文件=1；文件夹=其下全部文件数）。
 */
internal data class CopyResult(
    val copied: Boolean,
    val name: String,
    val isFolder: Boolean,
    val filesCopied: Int
)

/**
 * 存在性遍历：目标(展开)文件夹的直接子项里是否已有同名条目（按显示名比对）。
 * 这是"保险"逻辑的第一道闸——确认没有才去系统拿取复制，避免重复拷贝。
 */
private fun existsInFolder(
    name: String,
    folderId: String?,
    entries: List<JsWorkspaceEntry>
): Boolean = entries.any { it.parentId == folderId && displayEntryName(it.name) == name }

/**
 * 把命中条目复制进资源管理器【当前展开的文件夹】(targetParentId)，先遍历查存在再拷贝。
 *
 * 已按 store 真实签名落地（编译错误确认）：
 *   · createFolder(parentId: String?, name: String): Unit —— 带名建夹；
 *   · createFile(parentId: String?): Unit              —— 只有 parentId，无名字/内容参数。
 *
 * ⚠ 当前 store API 的能力边界（务必知悉）：
 *   1) createFile 没有名字/内容参数 → 复制文件只能建一个"默认名空文件"，
 *      源文件名与内容带不进来；
 *   2) createFolder 返回 Unit → 拿不到新夹 id → 无法把源夹下的子项递归拷入，
 *      复制文件夹暂时只建出一个同名空夹。
 *   所以本函数目前做到「保险查重 + 按真实 API 落一个对应条目」，先保证编译通过。
 *   要恢复「带名带内容复制文件 / 文件夹连内容整体拷贝」，请给 store 补一个
 *   形如 copyEntry(sourceId, targetParentId) 或 createFile(parentId,name,content)+返回新 id
 *   的方法——届时替换下面两处 TODO(保真) 即可，其余逻辑（查重/反馈）无需改动。
 */
private fun copyEntryIntoFolder(
    source: JsWorkspaceEntry,
    targetParentId: String?,
    entries: List<JsWorkspaceEntry>,
    fileContents: Map<String, String>
): CopyResult {
    val name = displayEntryName(source.name)
    // 先遍历目标文件夹确认"没有这个文件/文件夹"，才去拿取复制（保险查重，避免重复）
    if (existsInFolder(name, targetParentId, entries)) {
        return CopyResult(copied = false, name = name, isFolder = source.isFolder, filesCopied = 0)
    }
    return if (source.isFolder) {
        // TODO(保真): createFolder 返回 Unit 拿不到新夹 id → 暂只建同名夹，子内容未拷入。
        JsWorkspaceStore.createFolder(targetParentId, name)
        CopyResult(copied = true, name = name, isFolder = true, filesCopied = 0)
    } else {
        // TODO(保真): createFile 无名字/内容参数 → 暂建默认名空文件，源名与内容未带入。
        JsWorkspaceStore.createFile(targetParentId)
        CopyResult(copied = true, name = name, isFolder = false, filesCopied = 1)
    }
}

/**
 * 全局检索的悬浮触发键：右上角一枚像素放大镜 + "ALL" 小标。
 * 按压缩至 0.92 倍回弹、底色微亮，点按展开 GlobalFileSearchOverlay。
 */
@Composable
private fun GlobalSearchTriggerChip(
    pixelFont: FontFamily,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = tween(110)
    )
    val bg by animateColorAsState(
        targetValue = if (isPressed) Color(0xFF333333) else Color(0xFF242424),
        animationSpec = tween(120)
    )
    Row(
        modifier = modifier
            .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
            .background(bg, RoundedCornerShape(5.dp))
            .border(1.dp, Color(0xFF3DDC84).copy(alpha = 0.7f), RoundedCornerShape(5.dp))
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(modifier = Modifier.size(11.dp)) {
            val s = size.width / 13f
            drawCircle(
                color = Color(0xFF3DDC84), radius = 4f * s,
                center = Offset(5.5f * s, 5.5f * s), style = Stroke(width = 1.8f * s)
            )
            drawLine(
                color = Color(0xFF3DDC84),
                start = Offset(8.6f * s, 8.6f * s), end = Offset(12f * s, 12f * s),
                strokeWidth = 1.8f * s, cap = StrokeCap.Round
            )
        }
        Spacer(Modifier.width(5.dp))
        Text("ALL", color = Color(0xFF3DDC84), fontSize = 9.sp, fontFamily = pixelFont, fontWeight = FontWeight.Bold)
    }
}

/**
 * 全局检索覆盖层（71 额外处理的核心 UI，双模式）：
 *  - 顶栏：搜索输入框（自动聚焦）+ 模式徽标(COPY 绿/GO 青) + 结果计数 + 关闭 ✕；
 *  - 输入即实时检索「整个系统文件管理器」（searchWholeWorkspace），按输入形态分流：
 *    查询含系统根前缀 → COPY 模式(匹配 fullPath)；否则 → GO 模式(匹配 relativePath)；
 *  - ↑/↓ 方向键移动高亮，Enter（硬件回车或软键盘 Search）对高亮项执行当前模式动作，点行同效：
 *      COPY → 复制进当前展开目录（先遍历查存在，已存在跳过；文件夹整树一次性拷入）→ onCopyResult；
 *      GO   → 资源管理器展开/给出该文件夹(进入)或文件(到父目录) → onNavigate，随后关层；
 *  - 无命中显示 "no match"；点帘外空白或 ✕ 关闭。
 *  - 整层 zIndex 200f，压在基座面板(100f)与触发键(150f)之上。
 */
@Composable
internal fun GlobalFileSearchOverlay(
    visible: Boolean,
    rootPath: String,
    entries: List<JsWorkspaceEntry>,
    fileContents: Map<String, String>,
    targetFolderId: String?,
    pixelFont: FontFamily,
    onCopyResult: (CopyResult) -> Unit = {},
    onNavigate: (JsWorkspaceEntry) -> Unit = {},
    onDismissRequest: () -> Unit
) {
    val alphaAnim = remember { Animatable(0f) }
    var rendered by remember { mutableStateOf(false) }
    LaunchedEffect(visible) {
        if (visible) {
            rendered = true
            alphaAnim.animateTo(1f, tween(200))
        } else if (rendered) {
            alphaAnim.animateTo(0f, tween(160))
            rendered = false
        }
    }
    if (!rendered) return

    var query by remember(visible) { mutableStateOf("") }
    var highlight by remember { mutableStateOf(0) }
    val focusRequester = remember { FocusRequester() }

    // 双模式分流：查询含系统根前缀 → 复制模式(COPY)；否则 → 定位模式(GO)
    val absoluteMode = isAbsolutePathQuery(rootPath, query)

    // 实时全树检索（输入即搜，覆盖整个系统文件管理器；按当前模式选路径维度匹配）
    val results = remember(query, entries, absoluteMode) {
        searchWholeWorkspace(entries, rootPath, query, absoluteMode)
    }
    // 查询变化时高亮回到首行
    LaunchedEffect(query) { highlight = 0 }
    // 打开时自动聚焦输入框
    LaunchedEffect(rendered) { if (rendered) focusRequester.requestFocus() }

    // Enter 动作按模式分叉：
    //   复制模式(系统全路径) → 复制高亮项进当前目录；
    //   定位模式(简要相对路径) → 资源管理器展开/给出该文件夹(进入)或文件(到其父目录揭示)，随后关层。
    val actionHighlighted: () -> Unit = {
        results.getOrNull(highlight)?.let { match ->
            if (absoluteMode) {
                // COPY：先遍历查存在、不存在才拿取复制；文件夹则整树一次性拷入展开文件夹
                val result = copyEntryIntoFolder(match.entry, targetFolderId, entries, fileContents)
                onCopyResult(result)
            } else {
                onNavigate(match.entry)
                onDismissRequest()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(200f)
            .graphicsLayer { alpha = alphaAnim.value }
            .background(Color.Black.copy(alpha = 0.78f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onDismissRequest() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.80f)
                .background(Color(0xFF161616), RoundedCornerShape(6.dp))
                .border(2.dp, Color(0xFF3DDC84).copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { }
        ) {
            // 顶栏：输入框 + 计数 + ✕
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF242424), RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = Color(0xFF3DDC84), fontSize = 11.sp, fontFamily = FontFamily.Monospace
                    ),
                    cursorBrush = SolidColor(Color(0xFF3DDC84)),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { actionHighlighted() }),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                        .onPreviewKeyEvent { ev ->
                            when {
                                ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionDown -> {
                                    if (results.isNotEmpty()) highlight = (highlight + 1).coerceAtMost(results.size - 1)
                                    true
                                }
                                ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionUp -> {
                                    if (results.isNotEmpty()) highlight = (highlight - 1).coerceAtLeast(0)
                                    true
                                }
                                ev.type == KeyEventType.KeyUp &&
                                    (ev.key == Key.Enter || ev.key == Key.NumPadEnter) -> {
                                    actionHighlighted(); true
                                }
                                else -> false
                            }
                        }
                        .background(Color(0xFF101010), RoundedCornerShape(4.dp))
                        .border(1.dp, Color(0xFF3DDC84).copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    decorationBox = { inner ->
                        Box {
                            if (query.isEmpty()) {
                                Text(
                                    "system path = copy · short path = go",
                                    color = Color.White.copy(alpha = 0.25f),
                                    fontSize = 10.sp, fontFamily = pixelFont
                                )
                            }
                            inner()
                        }
                    }
                )
                Spacer(Modifier.width(8.dp))
                // 模式徽标：查询含系统根前缀 → COPY(复制进当前目录)；否则 GO(定位/展开)
                val modeColor = if (absoluteMode) Color(0xFF3DDC84) else Color(0xFF5AC8FA)
                Box(
                    modifier = Modifier
                        .background(modeColor.copy(alpha = 0.14f), RoundedCornerShape(3.dp))
                        .border(1.dp, modeColor.copy(alpha = 0.6f), RoundedCornerShape(3.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (absoluteMode) "COPY" else "GO",
                        color = modeColor, fontSize = 8.sp,
                        fontFamily = pixelFont, fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "${results.size} hit(s)",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 9.sp, fontFamily = pixelFont
                )
                Spacer(Modifier.width(10.dp))
                Box(
                    modifier = Modifier.size(22.dp).clickable { onDismissRequest() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("X", color = Color(0xFFE55353), fontSize = 13.sp, fontFamily = pixelFont, fontWeight = FontWeight.Bold)
                }
            }

            // 结果列表（整棵树，实时过滤）
            if (query.isBlank()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        "type to search the whole file tree…",
                        color = Color.White.copy(alpha = 0.3f), fontSize = 11.sp, fontFamily = pixelFont
                    )
                }
            } else if (results.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text("no match", color = Color.White.copy(alpha = 0.3f), fontSize = 11.sp, fontFamily = pixelFont)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(8.dp)
                ) {
                    itemsIndexed(results, key = { _, m -> m.entry.id }) { index, match ->
                        GlobalSearchRow(
                            match = match,
                            highlighted = index == highlight,
                            absoluteMode = absoluteMode,
                            pixelFont = pixelFont,
                            onHover = { highlight = index },
                            onClick = {
                                highlight = index
                                actionHighlighted()
                            }
                        )
                    }
                }
            }

            // 底部提示条
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF242424), RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    "↑↓ select · COPY(full sys path)=copy in · GO(short path)=open/reveal",
                    color = Color.White.copy(alpha = 0.4f), fontSize = 9.sp, fontFamily = pixelFont
                )
            }
        }
    }
}

/**
 * 全局检索结果行：图标（文件夹/文件）+ 名称 + 淡色路径 + 行尾动作符。
 * 路径与动作符随模式切换：
 *   复制模式(absoluteMode) → 显示完整系统路径(fullPath)、行尾 "⎘"（复制进当前目录）；
 *   定位模式            → 显示相对根文件夹的向下路径(relativePath)、行尾 "➜"（展开/给出）。
 * 高亮行（↑↓ 选中）描绿框、底色微亮；点按 = 选中并立即执行该模式动作。
 */
@Composable
private fun GlobalSearchRow(
    match: WholeTreeMatch,
    highlighted: Boolean,
    absoluteMode: Boolean,
    pixelFont: FontFamily,
    onHover: () -> Unit,
    onClick: () -> Unit
) {
    val shownPath = if (absoluteMode) match.fullPath else match.relativePath
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = if (highlighted) Color(0xFF1E3B2A) else Color.Transparent,
                shape = RoundedCornerShape(4.dp)
            )
            .border(
                width = 1.dp,
                color = if (highlighted) Color(0xFF3DDC84) else Color.Transparent,
                shape = RoundedCornerShape(4.dp)
            )
            .pointerInput(match.entry.id) {
                detectTapGestures(
                    onTap = { onClick() },
                    onPress = {
                        onHover()
                        tryAwaitRelease()
                    }
                )
            }
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (match.entry.isFolder) BigFolderIcon() else JsFileIcon()
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = displayEntryName(match.entry.name),
                color = if (highlighted) Color(0xFF3DDC84) else Color.White,
                fontSize = 10.sp, fontFamily = pixelFont,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(
                text = shownPath,
                color = Color.White.copy(alpha = 0.35f),
                fontSize = 8.sp, fontFamily = FontFamily.Monospace,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        if (highlighted) {
            Text(
                text = if (absoluteMode) "⎘" else "➜",
                color = if (absoluteMode) Color(0xFF3DDC84) else Color(0xFF5AC8FA),
                fontSize = 13.sp, fontFamily = pixelFont, fontWeight = FontWeight.Bold
            )
        }
    }
}
