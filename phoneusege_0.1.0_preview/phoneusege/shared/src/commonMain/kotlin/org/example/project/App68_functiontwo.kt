package org.example.project

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 主画布按钮排列总线（功能管理画布小格子 ↔ 白色主画布 12 个按钮）。
 *
 * 空间契约（修复"按钮瞎移动"）：小格子就是主画布按钮的"微缩版"，按位置 1:1 对应——
 * - 格子第 r 行左格(槽位 2r)  ↔ 主画布左列第 r 个按钮；
 * - 格子第 r 行右格(槽位 2r+1) ↔ 主画布右列第 r 个按钮；
 * - 卡片被收进底部虚线格(InStack)即"隐藏"：对应按钮不在主画布渲染；
 * - 在功能管理画布上拖拽换格/进栈/出栈 → snapshotFlow 实时写入本 Store，
 *   主画布按钮立刻跟随重排（SnapshotStateList 读即订阅，无需额外收集 flow）。
 */
object ButtonOrderStore {

    /** 主画布左列按钮：cardId 列表(上→下)，与格子左列(槽位 0,2,4,6,8,10)逐行对应 */
    val leftColumn = mutableStateListOf<Int>()

    /** 主画布右列按钮：cardId 列表(上→下)，与格子右列(槽位 1,3,5,7,9,11)逐行对应 */
    val rightColumn = mutableStateListOf<Int>()

    /** 被收进虚线格的 cardId（主画布不显示），按入栈先后保留顺序 */
    val hiddenIds = mutableStateListOf<Int>()

    /**
     * 默认摆放(与 FunctionManagementCanvas 的默认格子保持一致)：
     * 槽位 2r 放卡片 r+1、槽位 2r+1 放卡片 r+7 → 格子左列读下来 1..6、右列 7..12，
     * 即默认按钮外观与原版主画布完全一致。
     */
    fun defaultPlacement(): Map<Int, CardLocation> {
        val m = HashMap<Int, CardLocation>(Spec.CARD_COUNT)
        repeat(Spec.CARD_COUNT / 2) { r ->
            m[r + 1] = CardLocation.Slot(r * 2)
            m[r + 1 + Spec.CARD_COUNT / 2] = CardLocation.Slot(r * 2 + 1)
        }
        return m
    }

    init {
        // 主画布先于功能管理画布组合时(从未打开过功能管理画布)，先从持久化存档还原排序；
        // 无存档则默认 12 张全部显示、顺序 1..12。
        val archive = CardLayoutStore.loaded
        if (archive == null) {
            leftColumn.addAll(1..(Spec.CARD_COUNT / 2))
            rightColumn.addAll((Spec.CARD_COUNT / 2 + 1)..Spec.CARD_COUNT)
        } else {
            val locations = HashMap(defaultPlacement())
            archive.cards.forEach { e ->
                if (locations.containsKey(e.cardId)) {
                    locations[e.cardId] =
                        if (e.locType.equals("slot", ignoreCase = true)) CardLocation.Slot(e.slotIndex) else CardLocation.InStack
                }
            }
            apply(locations, archive.stackOrder.orEmpty())
        }
    }

    /**
     * 由功能管理画布的布局 snapshotFlow 调用。
     * [locations] cardId → Slot(槽位号) / InStack；[stackOrder] 虚线格堆叠栈顺序。
     */
    fun apply(locations: Map<Int, CardLocation>, stackOrder: List<Int>) {
        // 空间契约：格子第 r 行左格(槽位 2r) ↔ 主画布左列第 r 个按钮，
        //          格子第 r 行右格(槽位 2r+1) ↔ 主画布右列第 r 个按钮。
        // 用等值比较反查"槽位 s 里放着哪张卡"，不依赖 CardLocation.Slot 的内部属性名。
        fun cardAtSlot(slot: Int): Int? =
            locations.entries.firstOrNull { it.value == CardLocation.Slot(slot) }?.key

        val left = (0 until Spec.CARD_COUNT step 2).mapNotNull { cardAtSlot(it) }
        val right = (1 until Spec.CARD_COUNT step 2).mapNotNull { cardAtSlot(it) }

        val stackedSet = locations.entries
            .filter { it.value is CardLocation.InStack }
            .map { it.key }
            .toSet()
        // 存档栈序优先；存档缺失的 InStack 卡片兜底追加（防御性补全）
        val hidden = stackOrder.filter { it in stackedSet } + stackedSet.filterNot { it in stackOrder }

        leftColumn.clear()
        leftColumn.addAll(left)
        rightColumn.clear()
        rightColumn.addAll(right)
        hiddenIds.clear()
        hiddenIds.addAll(hidden)
        // 调试日志：拖拽落位后看 logcat(System.out) 是否打印新的两列顺序，确认联动正常后可删
        println("[ButtonOrderStore] left=$left right=$right hidden=$hidden")
    }
}

// ==================== 主画布按钮规格 + 动态按钮组件 ====================
// cardId 与功能管理画布卡片编号一致(顺序同 SubCanvasNames)；
// labelKey / subCanvas 即原主画布硬编码 12 个按钮的名称键与目标子画布。
private data class MainButtonSpec(val labelKey: UiText, val subCanvas: String)

private val CardButtons = mapOf(
    1 to MainButtonSpec(UiText.ComponentPlaza, "ComponentPlaza"),
    2 to MainButtonSpec(UiText.Chess, "Chess"),
    3 to MainButtonSpec(UiText.GeneralKnowledge, "GeneralKnowledge"),
    4 to MainButtonSpec(UiText.ComponentManagement, "ComponentManagement"),
    5 to MainButtonSpec(UiText.FunctionManagement, "FunctionManagement"),
    6 to MainButtonSpec(UiText.SkillPlazaTitle, "SkillPlaza"),
    7 to MainButtonSpec(UiText.Key, "Key"),
    8 to MainButtonSpec(UiText.AiAssistSystem, "AIAssistSystem"),
    9 to MainButtonSpec(UiText.Self, "Self"),
    10 to MainButtonSpec(UiText.Credits, "Credits"),
    11 to MainButtonSpec(UiText.Language, "Language"),
    12 to MainButtonSpec(UiText.SkillManagement, "SkillManagement"),
)

/**
 * 白色主画布的 12 个功能按钮(动态版)：
 * 排列顺序由本画布小格子布局实时驱动(ButtonOrderStore)，格子与按钮按位置 1:1 对应——
 * 格子第 r 行左格 ↔ 左列第 r 个按钮，右格 ↔ 右列第 r 个按钮；收进虚线格的卡片不渲染。
 * 主画布文件里只需一行 MainButtonGrid(...) 调用，替换原硬编码按钮块即可。
 */
@Composable
fun BoxScope.MainButtonGrid(
    pixelFont: FontFamily,
    onNavigate: (String) -> Unit,
) {
    // 读 SnapshotStateList 即完成订阅：小格子一变动，这里立即重组重排。
    // 左右两列各自与格子左右两列按行 1:1 对应，拖到哪格，按钮就在哪个位置。
    val leftCards = ButtonOrderStore.leftColumn.toList()
    val rightCards = ButtonOrderStore.rightColumn.toList()

    Row(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .widthIn(max = 412.dp)
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .padding(top = 220.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            leftCards.forEach { cardId ->
                val spec = CardButtons[cardId] ?: return@forEach
                MainButtonCell(uiText(spec.labelKey), pixelFont) { onNavigate(spec.subCanvas) }
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            rightCards.forEach { cardId ->
                val spec = CardButtons[cardId] ?: return@forEach
                MainButtonCell(uiText(spec.labelKey), pixelFont) { onNavigate(spec.subCanvas) }
            }
        }
    }
}

/** 与主画布原 GreenCanvasButton 同款样式(38dp 高、黑 8% 底、1dp 描边、像素字体) */
@Composable
private fun MainButtonCell(
    text: String,
    pixelFont: FontFamily,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
            .background(Color.Black.copy(alpha = 0.08f), RoundedCornerShape(6.dp))
            .border(1.dp, Color.Black.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.Black,
            fontSize = 12.sp,
            fontFamily = pixelFont,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
internal fun FunctionManagementCanvas(pixelFont: FontFamily, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current

    val locations = remember {
        mutableStateMapOf<Int, CardLocation>().apply {
            // 默认格子 = 主画布微缩版(左列槽位 0,2,4.. 放卡片 1..6、右列槽位 1,3,5.. 放 7..12)，
            // 再用持久化存档覆盖(存档里缺的卡片保持默认格位，保证 12 张齐全)
            putAll(ButtonOrderStore.defaultPlacement())
            val saved = CardLayoutStore.loaded
            var applied = 0
            saved?.cards?.forEach { e ->
                if (containsKey(e.cardId)) {
                    // locType 大小写容错：若落盘侧写的是 "Slot"/"SLOT" 而读档侧只认 "slot"，
                    // 整份存档会被静默忽略、每次重启都回默认排列——这里放开大小写匹配
                    put(e.cardId, if (e.locType.equals("slot", ignoreCase = true)) CardLocation.Slot(e.slotIndex) else CardLocation.InStack)
                    applied++
                }
            }
            // 诊断：archive=true 但 applied=0 → 存档存在却没被用上；archive=false → 启动时没读到存档
            println("[CardLayoutStore] canvas remember: archive=${saved != null} applied=$applied/12")
        }
    }
    val stackOrder = remember {
        mutableStateListOf<Int>().apply {
            // 读档即对账：栈序只收 locations 里确实 InStack 的卡片(过滤掉存档错位残留)，
            // locations 里 InStack 但栈序缺失的补进栈尾——重启后一张卡只出现在一处，
            // 杜绝"格位里一张 + 虚线格里又冒一张同样的假方块"
            val inStackIds = locations.entries
                .filter { it.value == CardLocation.InStack }
                .map { it.key }
                .toSet()
            val savedOrder = CardLayoutStore.loaded?.stackOrder.orEmpty()
            addAll(savedOrder.filter { it in inStackIds })
            addAll(inStackIds.filterNot { it in savedOrder })
        }
    }
    val slotRects = remember { mutableStateMapOf<Int, Rect>() }
    var dashedRect by remember { mutableStateOf(Rect.Zero) }
    val inputs = remember {
        mutableStateListOf<TextFieldValue>().apply {
            repeat(Spec.INPUT_COUNT) { add(TextFieldValue()) }
            // 前八行 = Tools：前 3 条为 MCP 爬取源站点（Smithery / mcp.so / Glama 顺序），
            // 进入画布即显示真实爬取地址；编辑由 LinesBlock 实时回写 PlazaStore
            PlazaStore.crawlSources.forEachIndexed { idx, src ->
                if (idx < Spec.INPUT_COUNT) this[idx] = TextFieldValue(src.baseUrl)
            }
            // 后八行(8-15) = Skills 爬取源：显示各专门 skills 站点(ClawHub / skills.sh / SkillsMP)API 地址，编辑实时回写 SkillCrawlStore
            SkillCrawlStore.skillSources.forEachIndexed { idx, src ->
                val row = idx + 8
                if (row < Spec.INPUT_COUNT) this[row] = TextFieldValue(src.baseUrl)
            }
        }
    }

    val cycler = remember { StackCycler(scope, stackOrder, density) }
    val controller = remember {
        DragController(
            scope = scope,
            haptics = haptics,
            locations = locations,
            mutate = { id, loc -> locations[id] = loc },
            swapCards = { a, b ->
                val la = locations.getValue(a)
                val lb = locations.getValue(b)
                locations[a] = lb
                locations[b] = la
            },
            pushToStack = { id ->
                locations[id] = CardLocation.InStack
                stackOrder.remove(id)
                stackOrder.add(id)
            },
            slotRects = slotRects,
            dashedRect = { dashedRect },
            cycler = cycler,
            density = density,
        )
    }
    controller.stackProvider = stackOrder

    // 打开画布即真实爬取 skills 资源网站，爬到的技能合并进 GlobalSkillPool
    LaunchedEffect(Unit) {
        SkillCrawlStore.runSkillCrawler()
    }

    // 小格子排列持久化 + 主画布按钮排序同步：
    // 卡片位置 / 堆叠栈顺序一有变化(拖拽落位、交换、进栈、出栈、循环)就落盘(重启后原样恢复)，
    // 并实时写入 ButtonOrderStore → 白色主画布 12 个按钮按小格子排列顺序即时重排；
    // 被收进底部虚线格的卡片对应按钮不再显示。
    LaunchedEffect(Unit) {
        snapshotFlow { locations.toMap() to stackOrder.toList() }
            .collect { (locs, order) ->
                // 落盘前强制对账：虚线格栈序必须与 locations 严格一致——
                // 栈序里混着"已经回到格位"的卡片，就是虚线格冒出假方块(同一张卡两处出现)的源头；
                // InStack 但栈序缺失的卡片补进栈尾。修正结果回写 stackOrder(最多再触发一轮收敛)
                val inStackIds = locs.filterValues { it == CardLocation.InStack }.keys
                val cleanOrder = order.filter { it in inStackIds } + inStackIds.filterNot { it in order }
                if (cleanOrder != order) {
                    stackOrder.clear()
                    stackOrder.addAll(cleanOrder)
                }
                // 落盘加防护：单次写盘异常不允许掐断收集循环——
                // 否则异常之后的所有拖动都不会再持久化，重启必然回原位
                try {
                    CardLayoutStore.save(locs, cleanOrder)
                } catch (t: Throwable) {
                    println("[CardLayoutStore] save failed: $t")
                }
                ButtonOrderStore.apply(locs, cleanOrder)
            }
    }

    var rootTopLeft by remember { mutableStateOf(Offset.Zero) }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned {
                val topLeft = it.boundsInRoot().topLeft
                if (rootTopLeft != topLeft) rootTopLeft = topLeft
            }
            .background(PureWhite),
    ) {
        // 防御宿主下发无界高度约束:verticalScroll 在无界约束下会直接抛
        // IllegalStateException 导致进入页面即闪退,此处回退为有限高度。
        val scrollHeight = if (constraints.hasBoundedHeight) maxHeight else Spec.CANVAS_HEIGHT
        Box(Modifier.fillMaxWidth().height(scrollHeight).verticalScroll(scroll)) {
            Box(Modifier.fillMaxWidth().height(Spec.CANVAS_HEIGHT).padding(top = 15.dp)) {
                StaticLines()
                SettingsSubtitle(pixelFont)
                GridSection(topY = 16.dp, firstSlot = 0, locations, slotRects, controller, pixelFont)
                GridSection(topY = 148.dp, firstSlot = 6, locations, slotRects, controller, pixelFont)
                DashedSection(locations, stackOrder, cycler, controller, { if (dashedRect != it) dashedRect = it }, pixelFont, onClose)
                BottomArea(pixelFont)
                InputSection(inputs, pixelFont)
            }
        }
        GhostLayer(controller, pixelFont, rootTopLeft)
    }
}

@Composable
private fun SettingsSubtitle(pixelFont: FontFamily) {
    BasicText(
        text = "Settings",
        modifier = Modifier.offset(x = 24.dp, y = (-8).dp),
        style = TextStyle(
            fontFamily = pixelFont,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = Ink,
        ),
    )
}

@Composable
private fun StaticLines() {
    Canvas(Modifier.fillMaxWidth().height(Spec.CANVAS_HEIGHT)) {
        val stroke = 2.dp.toPx()
        val x0 = 24.dp.toPx()
        val x1 = size.width - x0
        val cx = size.width / 2f

        fun hLine(y: Dp, full: Boolean) {
            val yy = y.toPx()
            drawLine(
                Ink,
                if (full) Offset(0f, yy) else Offset(x0, yy),
                if (full) Offset(size.width, yy) else Offset(x1, yy),
                stroke,
            )
        }

        fun vLine(x: Float, top: Dp, bottom: Dp) =
            drawLine(Ink, Offset(x, top.toPx()), Offset(x, bottom.toPx()), stroke)

        hLine(14.dp, full = true)
        hLine(59.3.dp, full = false)
        hLine(102.7.dp, full = false)
        hLine(146.dp, full = false)
        hLine(191.3.dp, full = false)
        hLine(234.7.dp, full = false)
        hLine(278.dp, full = true)

        listOf(x0, cx, x1).forEach { x ->
            vLine(x, 16.dp, 146.dp)
            vLine(x, 148.dp, 278.dp)
        }
    }
}

@Composable
private fun GridSection(
    topY: Dp,
    firstSlot: Int,
    locations: Map<Int, CardLocation>,
    slotRects: MutableMap<Int, Rect>,
    controller: DragController,
    pixelFont: FontFamily,
) {
    Column(
        Modifier
            .offset(y = topY)
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .height(130.dp),
    ) {
        repeat(3) { r ->
            Row(
                Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(2) { c ->
                    val idx = firstSlot + r * 2 + c
                    val occupant = locations.entries.firstOrNull {
                        it.value == CardLocation.Slot(idx)
                    }?.key
                    SlotCell(idx, occupant, slotRects, controller, pixelFont)
                }
            }
        }
    }
}

@Composable
private fun SlotCell(
    index: Int,
    occupantId: Int?,
    slotRects: MutableMap<Int, Rect>,
    controller: DragController,
    pixelFont: FontFamily,
) {
    val hovered = controller.highlight == DropTarget.EmptySlot(index)
    Box(
        Modifier
            .size(Spec.CARD_W, Spec.CARD_H)
            .onGloballyPositioned {
                val rect = it.boundsInRoot()
                if (slotRects[index] != rect) slotRects[index] = rect
            }
            .then(
                if (hovered) Modifier
                    .background(SlotHoverBg, RoundedCornerShape(8.dp))
                    .border(1.5.dp, AccentBlue, RoundedCornerShape(8.dp))
                else Modifier,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (occupantId != null) {
            // 动画状态必须挂在"卡片"上而不是"格子"上：用 key(occupantId) 包裹，
            // 住户更换(拖拽落位/交换)时新卡的位移动画从零起算，不会继承旧卡
            // 让位动画的在途位移——消除落位后"卡片从原位滑进格子"的残影。
            // 同一张卡的让位/回位动画(occupantId 不变)不受影响。
            key(occupantId) {
                val isSource = controller.dragSourceId == occupantId
                val targeted = controller.highlight == DropTarget.RectTarget(occupantId)
                // endDrag 在松手瞬间同步清空 highlight 并提交布局，targeted 随之归 false、
                // 位移归零——落位即定格；让位动画仅在拖拽悬停期间生效
                val shift = if (targeted) controller.highlightShift else Offset.Zero
                val tx by animateFloatAsState(shift.x, tween(280, easing = DragEase), label = "shiftX")
                val ty by animateFloatAsState(shift.y, tween(280, easing = DragEase), label = "shiftY")
                CardFace(
                    id = occupantId,
                    modifier = Modifier
                        .graphicsLayer { translationX = tx; translationY = ty }
                        .dragSource(occupantId, controller, onTap = { CanvasNavigation.requested = SubCanvasNames.getOrNull(occupantId - 1) }),
                    source = isSource,
                    targeted = targeted,
                    pixelFont = pixelFont,
                )
            }
        }
    }
}

@Composable
private fun CardFace(
    id: Int,
    modifier: Modifier = Modifier,
    source: Boolean = false,
    targeted: Boolean = false,
    pixelFont: FontFamily = FontFamily.Default,
) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier
            .size(Spec.CARD_W, Spec.CARD_H)
            .graphicsLayer { alpha = if (source) 0.15f else 1f }
            .background(if (targeted) HoverBlueBg else PureWhite, shape)
            .then(if (targeted) Modifier.shadow(6.dp, shape) else Modifier)
            .border(1.dp, if (targeted) AccentBlue else Ink, shape),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = id.toString(),
            style = TextStyle(
                fontFamily = pixelFont,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Ink,
            ),
        )
        if (source) {
            Canvas(Modifier.fillMaxSize()) {
                val sw = 1.dp.toPx()
                drawRoundRect(
                    color = Ink,
                    topLeft = Offset(sw / 2, sw / 2),
                    size = Size(size.width - sw, size.height - sw),
                    cornerRadius = CornerRadius(8.dp.toPx() - sw / 2),
                    style = Stroke(sw, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
                )
            }
        }
    }
}

private fun Modifier.dragSource(
    cardId: Int,
    controller: DragController,
    onTap: () -> Unit = {},
): Modifier =
    pointerInput(cardId) {
        val moveLimitPx = controller.moveLimitPx
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            // 区分三种手势：长按→拖拽；快速轻点→导航(onTap)；提前滑动→取消
            when (awaitHold(Spec.HOLD_TIME_MS, down.position, moveLimitPx)) {
                HoldResult.TAPPED -> { onTap(); return@awaitEachGesture }
                HoldResult.MOVED -> return@awaitEachGesture
                HoldResult.HELD -> {
                    // endDrag 只允许调用一次：正常松手走 commit=true；
                    // 仅在拖拽过程抛异常时才用 finally 兜底 commit=false
                    var committed = false
                    try {
                        controller.beginDrag(cardId, down.position)
                        var change: PointerInputChange = down
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            change = event.changes.firstOrNull() ?: break
                            controller.updateDrag(change.position)
                            change.consume()
                        } while (change.pressed)
                        controller.endDrag(commit = true)
                        committed = true
                    } finally {
                        if (!committed) controller.endDrag(commit = false)
                    }
                }
            }
        }
    }

/** 长按手势的三种结局：按住(可拖拽) / 快速松手(轻点) / 提前滑动(取消) */
private enum class HoldResult { HELD, TAPPED, MOVED }

private suspend fun AwaitPointerEventScope.awaitHold(
    timeoutMillis: Long,
    startPos: Offset,
    moveLimitPx: Float,
): HoldResult {
    val result = withTimeoutOrNull(timeoutMillis) {
        var pressing = true
        while (pressing) {
            val event = awaitPointerEvent(PointerEventPass.Main)
            val change = event.changes.firstOrNull() ?: return@withTimeoutOrNull HoldResult.TAPPED
            if (!change.pressed) return@withTimeoutOrNull HoldResult.TAPPED
            if ((change.position - startPos).getDistance() > moveLimitPx) {
                change.consume()
                return@withTimeoutOrNull HoldResult.MOVED
            }
        }
        HoldResult.HELD
    }
    // 超时仍未松手也未滑动 = 长按成立
    return result ?: HoldResult.HELD
}

private fun Modifier.doubleTapClose(onDoubleTap: () -> Unit): Modifier =
    pointerInput(Unit) {
        val timeout = viewConfiguration.doubleTapTimeoutMillis
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            var pressing = true
            while (pressing) {
                val change = awaitPointerEvent(PointerEventPass.Main).changes.firstOrNull() ?: break
                if (!change.pressed) pressing = false
            }
            val second: Boolean? = withTimeoutOrNull(timeout) {
                var down = false
                while (!down) {
                    val change = awaitPointerEvent(PointerEventPass.Main).changes.firstOrNull() ?: continue
                    if (change.pressed) down = true
                }
                true
            }
            if (second == null) return@awaitEachGesture
            var holding = true
            while (holding) {
                val change = awaitPointerEvent(PointerEventPass.Main).changes.firstOrNull() ?: break
                if (!change.pressed) holding = false
            }
            onDoubleTap()
        }
    }

@Composable
private fun GhostLayer(controller: DragController, pixelFont: FontFamily, rootTopLeft: Offset) {
    if (!controller.ghostVisible) return
    val pos = controller.ghostPos - rootTopLeft
    val scale = controller.ghostScale.value
    Box(
        Modifier
            .offset { IntOffset(pos.x.roundToInt(), pos.y.roundToInt()) }
            .zIndex(9999f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = 0.95f
                shadowElevation = 12.dp.toPx()
                shape = RoundedCornerShape(8.dp)
            },
    ) {
        CardFace(id = controller.ghostCardId, pixelFont = pixelFont)
    }
}

@Composable
private fun DashedSection(
    locations: Map<Int, CardLocation>,
    stackOrder: List<Int>,
    cycler: StackCycler,
    controller: DragController,
    reportDashedRect: (Rect) -> Unit,
    pixelFont: FontFamily,
    onClose: () -> Unit,
) {
    val hovered = controller.highlight == DropTarget.Dashed
    Row(
        Modifier.offset(y = 298.dp).fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Chevron(direction = -1, enabled = !cycler.isCycling) { cycler.cycle(-1) }

        Box(
            Modifier
                .size(Spec.CARD_W, Spec.CARD_H)
                .onGloballyPositioned { reportDashedRect(it.boundsInRoot()) }
                .doubleTapClose(onClose)
                .background(if (hovered) HoverBlueBg else Color.Transparent, RoundedCornerShape(8.dp)),
        ) {
            DashedBorder(highlight = hovered)
            stackOrder.forEachIndexed { i, id ->
                // 只渲染真正 InStack 的卡片：若控制器在落位过渡期把"已进格子"的卡片
                // 临时挂在栈序里、借虚线格的位置动画播"回原位→滑过来"的残影，
                // 或存档栈序错位——这里直接跳过。虚线格永远不渲染分身，
                // 这也是可见侧最后一条位移动画通道
                if (locations[id] != CardLocation.InStack) return@forEachIndexed
                val a = cycler.anims[id]
                val off = a?.offset?.value ?: cycler.baseOffset(i)
                val isSource = controller.dragSourceId == id
                CardFace(
                    id = id,
                    modifier = Modifier
                        .offset { IntOffset(off.x.roundToInt(), off.y.roundToInt()) }
                        .zIndex(a?.z?.value ?: (i + 1).toFloat())
                        .graphicsLayer {
                            rotationZ = a?.rotation?.value ?: 0f
                            alpha = if (isSource) 0.15f else 1f
                            shadowElevation = 4.dp.toPx()
                            shape = RoundedCornerShape(8.dp)
                        }
                        // 虚线格(堆叠栈)里的卡片：只保留长按拖拽，轻点不跳子画布
                        // （小格子里的卡片轻点跳转保持不变）
                        .dragSource(id, controller),
                    source = isSource,
                    pixelFont = pixelFont,
                )
            }
        }

        Chevron(direction = 1, enabled = !cycler.isCycling) { cycler.cycle(1) }
    }
}

@Composable
private fun DashedBorder(highlight: Boolean) {
    val color = if (highlight) AccentBlue else Ink
    Canvas(Modifier.fillMaxSize()) {
        val sw = 1.5.dp.toPx()
        val r = 8.dp.toPx()
        if (highlight) {
            val ring = 3.dp.toPx()
            drawRoundRect(
                color = AccentBlue.copy(alpha = 0.2f),
                topLeft = Offset(-ring / 2, -ring / 2),
                size = Size(size.width + ring, size.height + ring),
                cornerRadius = CornerRadius(r + ring / 2),
                style = Stroke(ring),
            )
        }
        drawRoundRect(
            color = color,
            topLeft = Offset(sw / 2, sw / 2),
            size = Size(size.width - sw, size.height - sw),
            cornerRadius = CornerRadius(r - sw / 2),
            style = Stroke(sw, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
        )
    }
}
