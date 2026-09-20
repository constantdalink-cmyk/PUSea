package org.example.project

// ==============================================================================
// 画布文件 4/10：subcanvascomponentmanagement.kt —— 组件管理画布
// 职责：SubCanvasComponentManagement —— 组件管理画布：
//   1) 主画布：MCP 工具以透明玻璃方块卡片展示（可上下滚动查看全部）。
//      卡片内容：第一行名字 / 第二行描述 / 第三行 params；
//      卡片中心顶部小圆点 = 开关：蓝灰实心圆 = 启用，空心圆 = 关闭，点它来回切换。
//      ★ 卡片列表完全由 McpToolStore.uiState 动态驱动：任何已注册的 tool
//      （内置 / godo 用户自定义 / 外部导入）都会自动出现在画布上 ——
//      画布不写死任何工具名，以后加 tool 不用改画布代码。
//   2) 信息页：在主画布上向右滑 → 占屏幕 2/3 的信息页从左边滑出（贴左边缘）；
//      信息页为白底黑字；滑出时主画布变暗（遮罩），因此信息页无需边框；
//      在信息页上向左滑 → 滑回左屏外收起来；点遮罩也可关闭。
//      信息页内容：第一行标题 "Component Management"（英文），
//                  第二行搜索框（按工具名称过滤），
//                  第三行 "Tools (N)" 标题（点击可折叠/展开工具列表，默认收起），
//                  Tools 收束块下方靠左一个 "+" 按钮（点击增加一个块），
//                  下面是已添加的块列表。
//   3) 卡片左滑揭示：当用户添加了块时，对卡片向左滑 →
//      卡片内字幕（除顶部圆点外）整体左移，右侧向左滑出一个带深灰框的白矩形，
//      矩形内显示用户添加的块；此时上下滑动可切换块。
//      当用户没有添加任何块时，卡片向左滑不触发。
//   ★ 持久化：块列表与工具开关状态自动保存（androidMain SharedPreferences），重启后恢复；
//   ★ 信息页左下角房子图标：点击 → 整页向上滑出屏幕，回到主页。
// 依赖：subcanvasdispatcher.kt（SlideDownContainer，同包直接调用）
// ==============================================================================
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** 开关圆点颜色：蓝灰实心圆 = 启用（ON）；空心圆 = 关闭（OFF） */
private val DOT_ENABLED = Color(0xFF78909C)

/** 玻璃方块描边色（淡蓝灰，深色画布上勾勒出玻璃轮廓） */
private val GLASS_BORDER = Color(0xFFAFC4D0).copy(alpha = 0.32f)

/** 信息页面板底色：白色（滑出时主画布变暗，白页无需边框） */
private val PANEL_BG = Color.White

/** 卡片左滑揭示白矩形的深灰框 */
private val REVEAL_BORDER = Color(0xFF3A3A3F)

/** 卡片左滑揭示白矩形的宽度 */
private val REVEAL_WIDTH = 96.dp

// ==================== 用户块存储 ====================

/** 用户通过信息页 "+" 增加的块 */
data class ComponentBlock(
    val id: Long,
    val title: String,
    val content: String,
    /** 关联的 MCP 工具名：null = 普通块（不关联任何 tool） */
    val toolName: String? = null,
    /** 块自身点亮状态（未关联工具时有效）：true = 圆点实心，false = 空心（默认） */
    val lit: Boolean = false,
    /** 矩形内是否展开详情（点击块切换；展开后展出关联 tool 或块内容） */
    val expanded: Boolean = false
)

/** 用户块的存储（卡片左滑揭示白矩形显示，上下滑切换） */
object ComponentBlockStore {
    private val _blocks = MutableStateFlow<List<ComponentBlock>>(emptyList())
    val blocks: StateFlow<List<ComponentBlock>> = _blocks
    private var nextId = 1L

    /**
     * 增加一个块（强制起名：title 为必填）。
     * @param toolName 关联的 MCP 工具名（null = 普通块；名字与已注册工具匹配时自动关联）
     */
    fun add(title: String, toolName: String? = null) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        val block = ComponentBlock(id = nextId, title = trimmed, content = "", toolName = toolName)
        nextId++
        _blocks.value = _blocks.value + block
    }

    /** 删除一个块 */
    fun remove(id: Long) {
        _blocks.value = _blocks.value.filterNot { it.id == id }
    }

    /** 点亮/熄灭一个块（未关联工具时圆点开关） */
    fun setLit(id: Long, lit: Boolean) {
        _blocks.value = _blocks.value.map { if (it.id == id) it.copy(lit = lit) else it }
    }

    /** 展开/收起一个块（矩形内展示详情） */
    fun setExpanded(id: Long, expanded: Boolean) {
        _blocks.value = _blocks.value.map { if (it.id == id) it.copy(expanded = expanded) else it }
    }

    /** 恢复保存的块列表（持久化；画布打开时调用一次） */
    fun restore(saved: List<ComponentBlock>) {
        if (saved.isEmpty()) return
        _blocks.value = saved
        nextId = (saved.maxOfOrNull { it.id } ?: 0L) + 1
    }
}

// ==================== 画布持久化（跨重启保留） ====================

/** 键值存储能力：commonMain 只有接口，androidMain 用 SharedPreferences 实现（expect/actual） */
interface CanvasStorage {
    fun load(key: String): String?
    fun save(key: String, value: String)
}

/** 获取平台存储（androidMain actual：SharedPreferences；Compose expect/actual） */
@Composable
expect fun rememberCanvasStorage(): CanvasStorage

// 存储键
private const val KEY_BLOCKS = "canvas_blocks"
private const val KEY_TOOL_FLAGS = "canvas_tool_flags"

/** 块列表 → 文本（每行 id\u0001title\u0001content\u0001toolName\u0001lit\u0001expanded；兼容旧 3 段数据） */
private fun encodeBlocks(blocks: List<ComponentBlock>): String =
    blocks.joinToString("\n") {
        "${it.id}\u0001${it.title}\u0001${it.content}\u0001${it.toolName ?: ""}\u0001${if (it.lit) 1 else 0}\u0001${if (it.expanded) 1 else 0}"
    }

private fun decodeBlocks(raw: String): List<ComponentBlock> =
    raw.lines().mapNotNull { line ->
        val parts = line.split('\u0001')
        val id = parts.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
        ComponentBlock(
            id = id,
            title = parts.getOrNull(1) ?: "",
            content = parts.getOrNull(2) ?: "",
            toolName = parts.getOrNull(3)?.takeIf { it.isNotBlank() },
            lit = parts.getOrNull(4) == "1",
            expanded = parts.getOrNull(5) == "1"
        )
    }

/** 工具开关状态 → 文本（每行 name\u00011/0） */
private fun encodeToolFlags(entries: List<McpToolUiEntry>): String =
    entries.joinToString("\n") { "${it.name}\u0001${if (it.enabled) 1 else 0}" }

private fun decodeToolFlags(raw: String): Map<String, Boolean> =
    raw.lines().mapNotNull { line ->
        val parts = line.split('\u0001')
        val name = parts.getOrNull(0)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        name to (parts.getOrNull(1) == "1")
    }.toMap()

// ==================== 画布入口 ====================

@Composable
fun SubCanvasComponentManagement(visible: Boolean, onClose: () -> Unit, pixelFont: FontFamily) {
    SlideDownContainer(visible, uiText(UiText.ComponentManagement), onClose, pixelFont) {
        ComponentManagementCanvas(pixelFont, onClose)
    }
}

// ==================== 组件管理画布（主画布 + 信息页） ====================

/**
 * 主画布 + 信息页：
 *  - 主画布：玻璃工具卡片列表（可上下滚动）；向右滑 → 信息页从左边滑出（占 2/3 屏）。
 *  - 信息页：白底黑字；向左滑 → 滑回左屏外收起；滑出时主画布变暗，点遮罩也可关闭。
 */
@Composable
private fun ComponentManagementCanvas(pixelFont: FontFamily, onClose: () -> Unit) {
    // 【字幕出卡修复】画布根容器去掉 clipToBounds：卡片左滑揭示时字幕需要整体左移、
    // 移出卡片边界；根容器 clip 会把移出画布左缘的字幕硬裁掉 → 字幕永远出不了卡片。
    // 信息页的滑出/收起隐藏改由它自己的裁剪容器负责（见下），不再依赖根容器裁剪。
    val storage = rememberCanvasStorage()
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val panelWidthDp = maxWidth * (2f / 3f)
        val density = LocalDensity.current
        val panelWidthPx = with(density) { panelWidthDp.toPx() }
        val canvasHeightPx = with(density) { maxHeight.toPx() }
        val scope = rememberCoroutineScope()
        // 1f = 完全收起（信息页藏在左屏外），0f = 完全展开（贴左边缘占 2/3 屏）
        val progress = remember { Animatable(1f) }
        // 响应式读取进度值（驱动遮罩透明度；derivedStateOf 兼容所有 Compose 版本）
        val progressState by remember { derivedStateOf { progress.value } }
        // ===== 持久化：打开画布时恢复上次保存的数据 =====
        val blocks by ComponentBlockStore.blocks.collectAsState()
        val toolEntries by McpToolStore.uiState.collectAsState()
        // 恢复：块列表 + 工具开关状态（只执行一次）
        LaunchedEffect(Unit) {
            storage.load(KEY_BLOCKS)?.let { raw ->
                val restored = decodeBlocks(raw)
                if (restored.isNotEmpty()) ComponentBlockStore.restore(restored)
            }
            storage.load(KEY_TOOL_FLAGS)?.let { raw ->
                val flags = decodeToolFlags(raw)
                McpToolStore.listAllMcpTools().forEach { def ->
                    flags[def.name]?.let { enabled ->
                        McpToolStore.setEnabled(def.name, enabled)
                    }
                }
            }
        }
        // 保存：块列表变化时写入
        LaunchedEffect(blocks) {
            storage.save(KEY_BLOCKS, encodeBlocks(blocks))
        }
        // 保存：工具开关状态变化时写入
        LaunchedEffect(toolEntries) {
            storage.save(KEY_TOOL_FLAGS, encodeToolFlags(toolEntries))
        }
        // ===== 房子返回主页：整页向上滑出屏幕 =====
        // slideUp：0f = 原位，1f = 整页向上完全滑出屏幕；动画结束后回调 onClose
        val slideUp = remember { Animatable(0f) }
        var leaving by remember { mutableStateOf(false) }

        fun goHome() {
            if (leaving) return
            leaving = true
            scope.launch {
                slideUp.animateTo(1f, tween(460))
                onClose()
            }
        }

        fun closePanel() {
            if (leaving) return
            scope.launch { progress.animateTo(1f, tween(220)) }
        }

        fun settlePanel() {
            if (leaving) return
            scope.launch {
                progress.animateTo(if (progress.value < 0.5f) 0f else 1f, tween(220))
            }
        }

        // 整页容器：房子触发时整体向上滑出屏幕（translationY 负向移出）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = -slideUp.value * canvasHeightPx
                }
        ) {
            // 主画布：卡片列表 + 右滑手势打开信息页
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragEnd = { settlePanel() },
                            onHorizontalDrag = { change, amount ->
                                change.consume()
                                if (amount > 0f && !leaving) {
                                    scope.launch {
                                        progress.snapTo(
                                            (progress.value - amount / panelWidthPx).coerceIn(0f, 1f)
                                        )
                                    }
                                }
                            }
                        )
                    }
            ) {
                McpToolCardCanvas(pixelFont)
            }

            // 遮罩：面板滑出时主画布变暗（透明度随进度），点遮罩关闭面板
            if (progressState < 1f && !leaving) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = (1f - progressState) * 0.55f))
                        .pointerInput(Unit) { detectTapGestures { closePanel() } }
                )
            }

            // 信息页：2/3 屏，贴左边缘；收起时藏在左屏外；白底黑字，无需边框。
            // 【字幕出卡修复】画布根容器不再 clipToBounds（见上），信息页自己在
            // 一个 matchParentSize 的裁剪容器内：半滑出/收起时不会在画布左缘外露出白边。
            Box(modifier = Modifier.matchParentSize().clipToBounds()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                    .width(panelWidthDp)
                    .fillMaxHeight()
                    // progress=1（收起）→ x = -宽（藏在左屏外）；progress=0（展开）→ x = 0（贴左边缘）
                    .offset { IntOffset((-progress.value * panelWidthPx).roundToInt(), 0) }
                    .background(PANEL_BG)
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragEnd = { settlePanel() },
                            onHorizontalDrag = { change, amount ->
                                change.consume()
                                if (amount < 0f && !leaving) {
                                    scope.launch {
                                        progress.snapTo(
                                            (progress.value - amount / panelWidthPx).coerceIn(0f, 1f)
                                        )
                                    }
                                }
                            }
                        )
                    }
                ) {
                    ComponentInfoPanel(pixelFont, onGoHome = { goHome() })
                }
            }
        }
    }
}

// ==================== MCP 工具卡片列表（主画布，可上下滚动） ====================

/**
 * 主画布内容：玻璃工具卡片列表（可上下滚动查看全部）。
 * 左滑揭示：字幕整体滑出卡片（左移量 = 卡片宽 × reveal），
 * 白矩形滑入中央，形成"字幕滑出、矩形滑入"的交换。
 * glassBlock 无整卡 clip（背景/描边带 shape，只裁自身）→ 字幕滑出不被卡片裁剪。
 */
@Composable
fun McpToolCardCanvas(pixelFont: FontFamily) {
    // 【动态工具列表】卡片完全由 McpToolStore.uiState 驱动：任何 tool 只要
    // register 进 McpToolStore（内置 / godo 用户自定义 / 外部导入），
    // 都会自动出现在画布上。画布不写死任何工具名，以后加 tool 不需要改
    // 画布代码 —— 卡片内容、开关状态全部来自数据。
    val entries by McpToolStore.uiState.collectAsState()
    val blocks by ComponentBlockStore.blocks.collectAsState()
    val listScroll = rememberScrollState()
    // 记录上一次工具数量：新增 tool（数量变大）→ 自动滚到底，让新卡片立刻可见
    val previousCount = remember { mutableStateOf(entries.size) }
    LaunchedEffect(entries.size) {
        if (entries.size > previousCount.value) {
            listScroll.scrollTo(listScroll.maxValue)
        }
        previousCount.value = entries.size
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(listScroll)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (entries.isEmpty()) {
            // 空状态：一个 tool 都还没注册时给个玻璃占位提示
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassBlock()
                    .padding(vertical = 18.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "no tools registered",
                    color = Color(0xFF9A9A9A),
                    fontSize = 10.sp,
                    fontFamily = pixelFont
                )
            }
        } else {
            entries.forEach { entry ->
                McpToolCard(
                    entry = entry,
                    pixelFont = pixelFont,
                    hasBlocks = blocks.isNotEmpty(),
                    onToggle = { enabled -> McpToolStore.setEnabled(entry.name, enabled) }
                )
            }
        }
    }
}

// ==================== 单张工具玻璃卡片（支持左滑揭示白矩形） ====================

@Composable
private fun McpToolCard(
    entry: McpToolUiEntry,
    pixelFont: FontFamily,
    hasBlocks: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val density = LocalDensity.current
    val revealWidthPx = with(density) { REVEAL_WIDTH.toPx() }
    val scope = rememberCoroutineScope()
    // 揭示进度：0f = 收起，1f = 白矩形完全滑出
    val reveal = remember { Animatable(0f) }
    val blocks by ComponentBlockStore.blocks.collectAsState()
    var blockIndex by remember { mutableStateOf(0) }
    var vertAccum by remember { mutableStateOf(0f) }
    // 当前展示的块（块数量变化时取模兜底）
    val safeIndex = if (blocks.isEmpty()) 0 else blockIndex % blocks.size
    // 全部工具开关状态映射（矩形态圆点显示当前块关联工具的开关）
    val allEntries by McpToolStore.uiState.collectAsState()
    val enabledMap = allEntries.associate { it.name to it.enabled }
    // 矩形揭示模式：true = 白矩形在中心（圆点 = 当前块状态），false = 字幕态（圆点 = 工具开关）
    val revealedMode by remember { derivedStateOf { reveal.value > 0.5f } }
    val currentBlock = blocks.getOrNull(safeIndex)
    // 圆点状态：字幕态 = 当前工具开关；矩形态 = 当前块状态
    // （关联工具 → 其开关；未关联 → 块自身 lit，默认 false = 空心 → 滑到矩形时亮的自动变暗）
    val dotEnabled = if (!revealedMode) {
        entry.enabled
    } else {
        currentBlock?.let { b ->
            if (b.toolName != null) enabledMap[b.toolName] ?: false else b.lit
        } ?: false
    }
    fun settleReveal() {
        scope.launch {
            reveal.animateTo(if (reveal.value > 0.5f) 1f else 0f, tween(200))
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .glassBlock()
            // 左滑揭示手势只在「有块」时才挂载。
            // 没块时卡片完全无水平手势，右滑事件直接穿透到外层主画布 → 信息页正常滑出；
            // 有块时：左滑 = 白矩形滑入（字幕滑出）；白矩形已滑出后右滑 = 收回
            // （字幕回来、矩形滑出）；完全收起（reveal=0）时右滑不消费，穿透给外层开信息页。
            .then(
                if (hasBlocks) {
                    Modifier.pointerInput(entry.name) {
                        detectHorizontalDragGestures(
                            onDragEnd = { settleReveal() },
                            onHorizontalDrag = { change, amount ->
                                if (amount < 0f) {
                                    // 左滑：矩形滑入（字幕滑出）
                                    change.consume()
                                    scope.launch {
                                        reveal.snapTo(
                                            (reveal.value - amount / revealWidthPx).coerceIn(0f, 1f)
                                        )
                                    }
                                } else if (amount > 0f && reveal.value > 0f) {
                                    // 右滑：矩形已滑出时接管收回 —— 字幕回来、矩形滑出
                                    change.consume()
                                    scope.launch {
                                        reveal.snapTo(
                                            (reveal.value - amount / revealWidthPx).coerceIn(0f, 1f)
                                        )
                                    }
                                }
                                // 完全收起（reveal==0）时右滑不消费 → 穿透外层打开信息页
                            }
                        )
                    }
                } else {
                    Modifier
                }
            )
            // 垂直切块手势：只在「揭示开着且有块」时挂载，否则列表正常滚动
            .then(
                if (reveal.value > 0.5f && blocks.isNotEmpty()) {
                    Modifier.pointerInput(entry.name, blocks.size, reveal.value) {
                        detectVerticalDragGestures(
                            onVerticalDrag = { change, amount ->
                                change.consume()
                                vertAccum += amount
                                val threshold = with(density) { 26.dp.toPx() }
                                if (vertAccum > threshold) {
                                    vertAccum = 0f
                                    blockIndex = (blockIndex + 1) % blocks.size
                                } else if (vertAccum < -threshold) {
                                    vertAccum = 0f
                                    blockIndex = if (blockIndex - 1 < 0) blocks.size - 1 else blockIndex - 1
                                }
                            }
                        )
                    }
                } else {
                    Modifier
                }
            )
    ) {
        // 卡片实际宽度（px）：BoxWithConstraints 内容作用域内 maxWidth 绝对可用，
        // 不再依赖 onSizeChanged 时序，保证字幕/白矩形的 offset 每次都能拿到正确宽度。
        val cardWidthPx = with(density) { maxWidth.toPx() }

        // 白矩形（深灰框）：完全展开时停在卡片正中间；收起时藏在右缘外。
        // offset 公式：p=0 → x=卡片宽（右侧外不可见）；p=1 → x=(卡片宽-矩形宽)/2（正中）。
        // 只对这个容器 clipToBounds：白矩形在收拢/滑出过程中不露出卡片右缘外。
        // 裁剪绝不能加在整卡上（见 glassBlock 注释），否则左移的字幕会被一起裁掉。
        Box(modifier = Modifier.matchParentSize().clipToBounds()) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(REVEAL_WIDTH)
                    .fillMaxHeight()
                .offset {
                    IntOffset(
                        (cardWidthPx - ((cardWidthPx + revealWidthPx) / 2f) * reveal.value).roundToInt(),
                        0
                    )
                }
                    .background(Color.White)
                    .border(1.dp, REVEAL_BORDER)
                    // 点击块：展开/收起（展开后展出关联 tool 或块内容详情）
                    .pointerInput(Unit) {
                        detectTapGestures {
                            // 实时读取当前块：闭包里捕获的 currentBlock 是旧组合值，
                            // 若用它计算切换目标，第二次点击会拿到旧 expanded → 点不动。
                            // 直接从 Store 的 StateFlow 读最新块，每次点击都基于最新状态切换。
                            val liveBlocks = ComponentBlockStore.blocks.value
                            val b = liveBlocks.getOrNull(blockIndex % liveBlocks.size)
                            if (b != null) {
                                ComponentBlockStore.setExpanded(b.id, !b.expanded)
                            }
                        }
                    }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 6.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (blocks.isEmpty()) {
                        Text(
                            text = "no blocks",
                            color = Color(0xFF9A9A9A),
                            fontSize = 8.sp,
                            fontFamily = pixelFont
                        )
                    } else {
                        val b = blocks[safeIndex]
                        if (b.expanded) {
                            // ===== 展开态：展出这个块（关联工具 → 展出工具信息；普通块 → 展出内容） =====
                            val tool = allEntries.firstOrNull { it.name == b.toolName }
                            if (tool != null) {
                                // 关联了 tool：展出工具的名字/描述/params
                                Text(
                                    text = tool.name,
                                    color = Color.Black,
                                    fontSize = 11.8.sp,
                                    fontFamily = pixelFont,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = tool.description,
                                    color = Color(0xFF666666),
                                    fontSize = 8.sp,
                                    fontFamily = pixelFont,
                                    textAlign = TextAlign.Center,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "params: " + tool.parameters.joinToString(", ") { it.name },
                                    color = Color(0xFF9A9A9A),
                                    fontSize = 7.sp,
                                    fontFamily = pixelFont,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            } else {
                                // 普通块：展出块内容
                                Text(
                                    text = b.title,
                                    color = Color.Black,
                                    fontSize = 11.8.sp,
                                    fontFamily = pixelFont,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = b.content.ifBlank { "(empty)" },
                                    color = Color(0xFF666666),
                                    fontSize = 8.sp,
                                    fontFamily = pixelFont,
                                    textAlign = TextAlign.Center,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        } else {
                            // ===== 收起态：标题 + 内容 + 序号（点击展开） =====
                            Text(
                                text = b.title,
                                color = Color.Black,
                                fontSize = 11.8.sp,
                                fontFamily = pixelFont,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = b.content.ifBlank { "(empty)" },
                                color = Color(0xFF666666),
                                fontSize = 8.sp,
                                fontFamily = pixelFont,
                                textAlign = TextAlign.Center,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = if (b.expanded) "${safeIndex + 1}/${blocks.size} ▲" else "${safeIndex + 1}/${blocks.size} ▼",
                            color = Color(0xFF9A9A9A),
                            fontSize = 7.sp,
                            fontFamily = pixelFont
                        )
                    }
                }
            }
        }

        // 玻璃反光带：顶部一条淡白高光，模拟光线打在玻璃上的反射。
        // 整卡不再 clip，这里自己裁顶部两角圆角，保持玻璃轮廓不变形。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp)
                .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.22f),
                            Color.Transparent
                        )
                    )
                )
        )

        // 字幕内容（名字/描述/params）：左滑揭示时整体【滑出】卡片（圆点除外）。
        // 左移量 = 卡片宽 × reveal —— 白矩形滑到中央时，字幕已完全移出卡片。
        // 【边框层级修复】字幕自身裁剪在卡片圆角矩形内（clip）→ 字幕滑出过程中
        // 超出卡片边框的部分直接被裁掉，不盖卡片边框、边框外的页面层级高于字幕。
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .offset {
                    IntOffset(
                        (-cardWidthPx * reveal.value).roundToInt(),
                        0
                    )
                }
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(28.dp))
            // 第一行：名字（字幕极度加深，字号加大）
            Text(
                text = entry.name,
                color = Color(0xFF30363E),
                fontSize = 13.sp,
                fontFamily = pixelFont,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            // 第二行：描述（字幕极度加深）
            Text(
                text = entry.description,
                color = Color(0xFF22272E),
                fontSize = 11.sp,
                fontFamily = pixelFont,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            // 第三行：params（p 开头参数行，字幕极度加深）
            Text(
                text = "params: " + entry.parameters.joinToString(", ") { it.name },
                color = Color(0xFF1A1F24),
                fontSize = 10.sp,
                fontFamily = pixelFont,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // 状态圆点（固定不动，不随左滑移动；点它切换开关；28dp 触控区包住 14dp 圆点）
        // 开关样式：蓝灰实心圆 = 启用，空心圆 = 关闭 —— 点击在两者间来回切换。
        // 【块联动】矩形揭示时圆点 = 当前块状态（默认空心；关联工具则显示其开关），
        // 点击切换块状态；字幕态圆点 = 本工具开关，点击切换工具开关（原行为）。
        // 【重复开关】点击处理实时读取 Store 最新状态（不用闭包捕获的旧值），
        // 每次点击都切换一次，可无限次重复开关。
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 6.dp)
                .size(28.dp)
                .pointerInput(entry.name) {
                    detectTapGestures {
                        // 【重复开关修复】实时读取状态：闭包里捕获的 entry / enabledMap /
                        // safeIndex 都是旧组合值，第二次点击会算出同样的切换目标 → 圆点点不动。
                        // 每次点击都从 Store 实时读最新状态，保证可无限次重复开关。
                        val liveEntries = McpToolStore.uiState.value
                        val liveBlocks = ComponentBlockStore.blocks.value
                        val currentEnabled = liveEntries.firstOrNull { it.name == entry.name }?.enabled ?: false
                        if (reveal.value > 0.5f && liveBlocks.isNotEmpty()) {
                            // 矩形态：切换当前块的状态
                            val b = liveBlocks.getOrNull(blockIndex % liveBlocks.size)
                            if (b != null) {
                                if (b.toolName != null) {
                                    // 关联工具：切换该工具开关
                                    val tEnabled = liveEntries.firstOrNull { it.name == b.toolName }?.enabled ?: false
                                    McpToolStore.setEnabled(b.toolName, !tEnabled)
                                } else {
                                    // 普通块：切换点亮/熄灭
                                    ComponentBlockStore.setLit(b.id, !b.lit)
                                }
                            }
                        } else {
                            // 字幕态：切换本工具开关
                            onToggle(!currentEnabled)
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            StatusDot(
                enabled = dotEnabled,
                size = 14.dp
            )
        }
    }
}

// ==================== 信息页（2/3 屏，白底黑字，右滑从左边滑出 / 左滑滑回） ====================

/**
 * 信息页内容（白底黑字）：
 *  第一行标题 "Component Management"（英文），
 *  第二行搜索框（按工具名称过滤），
 *  第三行 "Tools (N)" 标题（点击折叠/展开工具列表，默认收起；三角头带旋转动画），
 *  Tools 收束块下方靠左 "+" 按钮（点击增加一个块），
 *  下面是已添加的块列表。
 */
@Composable
private fun ComponentInfoPanel(pixelFont: FontFamily, onGoHome: () -> Unit) {
    val entries by McpToolStore.uiState.collectAsState()
    val blocks by ComponentBlockStore.blocks.collectAsState()
    var query by remember { mutableStateOf("") }
    // 默认收起（工具列表初始为收束状态）
    var toolsCollapsed by remember { mutableStateOf(true) }
    // 内联添加块：true = 显示无框输入行
    var adding by remember { mutableStateOf(false) }
    var nameInput by remember { mutableStateOf("") }
    // 信息页根列滚动状态（展开后内容超高时可滚动查看）
    val panelScroll = rememberScrollState()

    fun confirmAddBlock() {
        if (nameInput.isNotBlank()) {
            val trimmed = nameInput.trim()
            // 名字与已注册工具名匹配 → 该块自动关联此 tool：
            // 滑到矩形时圆点显示其开关、点击块展开展出该工具
            val matchedTool = McpToolStore.listAllMcpTools()
                .firstOrNull { it.name.equals(trimmed, ignoreCase = true) }
            ComponentBlockStore.add(trimmed, matchedTool?.name)
            nameInput = ""
            adding = false
        }
    }
    // 展开比例：0f = 完全收起，1f = 完全展开；驱动三角旋转与列表滑出动画
    val expand = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val filtered = entries.filter { entry ->
        query.isBlank() || entry.name.contains(query.trim(), ignoreCase = true)
    }

    fun toggleTools() {
        val target = if (toolsCollapsed) 1f else 0f
        scope.launch { expand.animateTo(target, tween(240)) }
        toolsCollapsed = !toolsCollapsed
    }

    // 外层 Box：内容列可滚动 + 左下角房子图标固定不随滚动
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // 根列可滚动：给下方内容无限高度约束，列表按内容自然取高，
                // 保证展开一定完整，不会被"剩余高度"截断成只展开一部分
                .verticalScroll(panelScroll)
            .padding(horizontal = 14.dp, vertical = 14.dp)
            // 点别的地方取消添加：添加模式开启时，点击面板上任何
            // 未被输入框/按钮/列表行消费的区域 → 取消添加、清空输入。
            // 子级 pointerInput（输入框、圆形 Add、搜索框、Tools 行、✕）会先消费事件，
            // 不会误触发取消。
            .then(
                if (adding) {
                    Modifier.pointerInput(Unit) {
                        detectTapGestures {
                            adding = false
                            nameInput = ""
                        }
                    }
                } else {
                    Modifier
                }
            )
    ) {
        // 第一行：标题
        Text(
            text = "Component Management",
            color = Color.Black,
            fontSize = 14.sp,
            fontFamily = pixelFont,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(10.dp))
        // 第二行：搜索框
        ToolSearchBox(
            query = query,
            onQueryChange = { query = it },
            pixelFont = pixelFont
        )
        Spacer(Modifier.height(6.dp))
        // 第三行：Tools 列表标题（点击折叠/展开；三角头带旋转动画）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(Unit) { detectTapGestures { toggleTools() } }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Tools (${filtered.size})",
                color = Color.Black,
                fontSize = 11.sp,
                fontFamily = pixelFont,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            // 三角头：展开 = ▼（0°），收起 = ▶（-90°），随 expand 平滑旋转
            Text(
                text = "▼",
                color = Color.Black,
                fontSize = 10.sp,
                fontFamily = pixelFont,
                modifier = Modifier.graphicsLayer {
                    rotationZ = -90f * expand.value
                }
            )
        }
        // 工具列表：AnimatedVisibility 实现【展开 + 收束双向动画】：
        //   - 展开：expandVertically(expandFrom = Top) + fadeIn —— 列表从 Tools 标题处
        //     向下滑出，高度 0 → 完整内容高度（无封顶，全部工具完整展开）；
        //   - 收回：shrinkVertically(shrinkTowards = Top) + fadeOut —— 列表平滑向上收束
        //     回 0，"add block" 等下方行随之平滑上移，不再瞬间消失。
        // 【收束动画修复】animateContentSize + if 移除内容：收回的一瞬间内容直接
        // 移出组合、高度跳变 → 收束是瞬时的。AnimatedVisibility 展开/收束双向都有动画。
        // 之前弃用它是因为 heightIn(max=220dp) 封顶 + weight 导致"只展一部分"，
        // 现都已移除，根列 verticalScroll 提供无限高度，列表完整展开。
        AnimatedVisibility(
            visible = !toolsCollapsed,
            modifier = Modifier.fillMaxWidth(),
            enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                    if (filtered.isEmpty()) {
                        Text(
                            text = "no tools match",
                            color = Color(0xFF9A9A9A),
                            fontSize = 11.sp,
                            fontFamily = pixelFont,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    } else {
                        filtered.forEach { entry ->
                            InfoToolRow(entry = entry, pixelFont = pixelFont)
                        }
                    }
                }
            }

        // ===== Tools 收束块下方：靠左的 "add block" 行（点击显示内联输入，输入名字添加块） =====
        // 收起时：紧贴在 Tools 收束块正下方（上边距 2dp，无间隔）；
        // 展开时：随列表生长被顺势推下，跟在最后一个 tool 下方。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(Unit) { detectTapGestures { adding = true } }
                .padding(top = 2.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 【加号不再显现】行前导 "+" 一并移除，只保留文字入口；
            // 点击整行（含文字）仍开启内联添加。
            // 【放大修复】"add block" 字号 7.sp → 12.sp，与块名字/输入框同大，
            // 不再是小号字幕；颜色加深为深灰，入口更清晰。
            Text(
                text = "add block",
                color = Color(0xFF4A4A4A),
                fontSize = 12.sp,
                fontFamily = pixelFont,
                fontWeight = FontWeight.Bold
            )
        }

        // ===== 已添加的块列表 =====
        if (blocks.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 96.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                blocks.forEach { block ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "▫",
                            color = Color(0xFF78909C),
                            fontSize = 8.sp,
                            fontFamily = pixelFont
                        )
                        Spacer(Modifier.width(5.dp))
                        // 块字幕 11.8.sp：与输入框 / "block name"（11.8.sp）一致
                        Text(
                            text = block.title,
                            color = Color(0xFF333333),
                            fontSize = 11.8.sp,
                            fontFamily = pixelFont,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        // 行靠右侧：蓝灰叉号，点击删除该块。
                        // 【对齐修复】叉号包进 14dp 固定容器、水平垂直居中，
                        // 与内联输入行的空心圆按钮（14dp）同一大小、同一右缘位置。
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .pointerInput(block.id) {
                                    detectTapGestures { ComponentBlockStore.remove(block.id) }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "✕",
                                color = Color(0xFF78909C),
                                fontSize = 12.sp,
                                fontFamily = pixelFont,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        // ===== 内联无框输入：点 "+" 后直接在块添加位置出现，输入名字回车/点 Add 添加 =====
        if (adding) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 无框输入框（透明背景、无边框、无圆角）
                // 【大小一致修复】输入文字与占位符统一 12.sp ——
                // 与创建后块列表里的块名字（12.sp）完全一致；
                // 块字幕保持大号，不再跟着缩小。
                BasicTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = Color.Black,
                        fontSize = 11.8.sp,
                        fontFamily = pixelFont
                    ),
                    cursorBrush = SolidColor(Color(0xFF78909C)),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { confirmAddBlock() }),
                    modifier = Modifier.weight(1f),
                    decorationBox = { innerTextField ->
                        Box {
                            if (nameInput.isEmpty()) {
                                Text(
                                    text = "block name",
                                    color = Color(0xFF9A9A9A),
                                    fontSize = 11.8.sp,
                                    fontFamily = pixelFont
                                )
                            }
                            innerTextField()
                        }
                    }
                )
                Spacer(Modifier.width(8.dp))
                // 圆形 Add 按钮：纯空心黑边圆，圆内不再绘制任何加号（加号不显现）。
                // 名字为空时描边置灰禁用；点击仍可添加块。
                // 【对齐修复】缩小为 14dp —— 与块列表行右侧的删除叉号（14dp 容器）
                // 同一大小、同一右缘位置、各自行内垂直居中。
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .border(
                            1.dp,
                            if (nameInput.isBlank()) Color(0xFFC6CDD4) else Color.Black,
                            CircleShape
                        )
                        .pointerInput(Unit) {
                            detectTapGestures {
                                if (nameInput.isNotBlank()) confirmAddBlock()
                            }
                        }
                )
            }
        }
        }

        // 左下角：房子图标（点击 → 整页向上滑出屏幕回主页）
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(10.dp)
                .size(30.dp)
                .pointerInput(Unit) { detectTapGestures { onGoHome() } },
            contentAlignment = Alignment.Center
        ) {
            HouseIcon(iconSize = 20.dp)
        }
    }
}

/** 搜索框：按工具名称过滤（白底黑字） */
@Composable
private fun ToolSearchBox(
    query: String,
    onQueryChange: (String) -> Unit,
    pixelFont: FontFamily
) {
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle = TextStyle(
            color = Color.Black,
            fontSize = 12.sp,
            fontFamily = pixelFont
        ),
        cursorBrush = SolidColor(Color(0xFF78909C)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFF0F2F5))
            .border(1.dp, Color(0xFFD8DCE2), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 9.dp),
        decorationBox = { innerTextField ->
            Box {
                if (query.isEmpty()) {
                    Text(
                        text = "search tool name...",
                        color = Color(0xFF9A9A9A),
                        fontSize = 12.sp,
                        fontFamily = pixelFont
                    )
                }
                innerTextField()
            }
        }
    )
}

/** 信息页工具列表行：圆点 + 名字（点击切换开关，与主画布卡片联动；白底黑字） */
@Composable
private fun InfoToolRow(entry: McpToolUiEntry, pixelFont: FontFamily) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(entry.name) {
                detectTapGestures {
                    // 【重复开关修复】entry.enabled 是闭包捕获的旧组合值，
                    // 第二次点击会算出同样的切换目标 → 行点不动。
                    // 每次点击都从 Store 实时读最新开关，保证可重复开关。
                    val currentEnabled = McpToolStore.uiState.value
                        .firstOrNull { it.name == entry.name }?.enabled ?: false
                    McpToolStore.setEnabled(entry.name, !currentEnabled)
                }
            }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusDot(
            enabled = entry.enabled,
            size = 12.dp
        )
        Spacer(Modifier.width(9.dp))
        Text(
            text = entry.name,
            color = if (entry.enabled) Color(0xFF1A1A1A) else Color(0xFF9A9A9A),
            fontSize = 12.sp,
            fontFamily = pixelFont,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ==================== 玻璃质感公共样式 ====================

/**
 * 透明玻璃方块样式：斜向渐变半透光玻璃底（左上亮 → 右下淡）
 * + 顶部亮色描边 + 圆角。深色画布上呈现明显的玻璃质感。
 */
private fun Modifier.glassBlock(radius: Dp = 12.dp): Modifier = this
    // 【重大 BUG 修复】不能用 .clip() 包住整卡：clip 会裁掉该节点所有子级的绘制，
    // 卡片左滑揭示时字幕需要整体左移、左端移出卡片边界（露在揭示白矩形左侧），
    // 整卡 clip 会把移出卡片的部分裁掉 → 字幕被锁死在卡片内，永远无法移出卡片。
    // 现在改为：background / border 直接带 shape 绘制（只裁自身背景与描边，不裁子级）；
    // 需要裁剪的局部（揭示白矩形容器、顶部反光带）各自单独 clip。
    .background(
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.15f),
                Color.White.copy(alpha = 0.04f)
            )
        ),
        RoundedCornerShape(radius)
    )
    .border(1.dp, GLASS_BORDER, RoundedCornerShape(radius))

/**
 * 开关圆点：蓝灰实心圆 = 启用（ON）；空心圆 = 关闭（OFF）——点击来回切换。
 * Canvas 绘制：启用 = 蓝灰实心填充；关闭 = 只画一圈蓝灰描边环，内部透明
 * （透出玻璃卡底 = 空心圆），两种形态一眼可辨。
 */
@Composable
private fun StatusDot(enabled: Boolean, size: Dp) {
    Canvas(modifier = Modifier.size(size)) {
        val radius = this.size.minDimension / 2f
        val ringWidth = 1.2.dp.toPx()
        if (enabled) {
            // 启用（ON）：蓝灰实心圆
            drawCircle(color = DOT_ENABLED, radius = radius)
        } else {
            // 关闭（OFF）：空心圆 —— 蓝灰描边环，内部透明
            drawCircle(
                color = DOT_ENABLED,
                radius = radius - ringWidth / 2f,
                style = Stroke(width = ringWidth)
            )
        }
    }
}

/** 房子图标（信息页左下角，点击整页向上滑出回主页）；Canvas 线稿：屋顶 + 墙体 + 门 */
@Composable
private fun HouseIcon(iconSize: Dp, color: Color = Color.Black) {
    Canvas(modifier = Modifier.size(iconSize)) {
        val stroke = 1.4.dp.toPx()
        // 参数改名 iconSize + 显式 this.size：避免 Dp 参数遮蔽 DrawScope.size，
        // 否则 size.width 会解析到 Dp 上（1245/1246 行 width/height 编译错误的根因）
        val w = this.size.width
        val h = this.size.height
        // 扁平单线房子（无烟囱、无 3D 透视）：
        // 一条连续的折线画出「左墙 - 屋顶 - 右墙」，再补「地脚线」和「门」，
        // 全部同一细线宽、同一纯色，呈现极简扁平图标风格。
        val house = Path().apply {
            // 左墙底部 → 左屋檐 → 屋顶尖 → 右屋檐 → 右墙底部
            moveTo(w * 0.18f, h * 0.88f)
            lineTo(w * 0.18f, h * 0.45f)
            lineTo(w * 0.5f, h * 0.1f)
            lineTo(w * 0.82f, h * 0.45f)
            lineTo(w * 0.82f, h * 0.88f)
        }
        drawPath(house, color = color, style = Stroke(stroke))
        // 地脚线（左墙脚 → 右墙脚）
        drawLine(
            color = color,
            start = Offset(w * 0.18f, h * 0.88f),
            end = Offset(w * 0.82f, h * 0.88f),
            strokeWidth = stroke
        )
        // 门（扁平方形，无透视）
        drawRect(
            color = color,
            topLeft = Offset(w * 0.44f, h * 0.62f),
            size = Size(w * 0.12f, h * 0.26f),
            style = Stroke(stroke)
        )
    }
}
