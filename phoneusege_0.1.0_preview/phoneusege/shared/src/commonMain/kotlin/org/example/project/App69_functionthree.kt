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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

internal val Ink = Color(0xFF111111)
internal val LineGray = Color(0xFF555555)
internal val AccentBlue = Color(0xFF1677FF)
internal val HoverBlueBg = Color(0xFFEAF3FF)
internal val SlotHoverBg = Color(0x141677FF)
internal val DotGreen = Color(0xFFB8D99A)
internal val SwitchPink = Color(0xFFFFD6E2)
internal val PureWhite = Color.White

internal val ExpandEase = cubicBezier(0.25f, 1f, 0.35f, 1f)
internal val DragEase = cubicBezier(0.2f, 0.8f, 0.2f, 1f)
internal val CycleOutEase = cubicBezier(0.25f, 1f, 0.5f, 1f)
internal val CycleInEase = cubicBezier(0.2f, 0.9f, 0.3f, 1f)

private fun cubicBezier(x1: Float, y1: Float, x2: Float, y2: Float): Easing {
    val cx = 3f * x1
    val bx = 3f * (x2 - x1) - cx
    val ax = 1f - cx - bx
    val cy = 3f * y1
    val by = 3f * (y2 - y1) - cy
    val ay = 1f - cy - by
    fun sampleX(u: Float) = ((ax * u + bx) * u + cx) * u
    fun sampleY(u: Float) = ((ay * u + by) * u + cy) * u
    fun sampleDX(u: Float) = (3f * ax * u + 2f * bx) * u + cx
    return object : Easing {
        override fun transform(fraction: Float): Float {
            if (fraction <= 0f) return 0f
            if (fraction >= 1f) return 1f
            var u = fraction
            var i = 0
            while (i < 8) {
                val err = sampleX(u) - fraction
                if (abs(err) < 1e-6f) return sampleY(u)
                val d = sampleDX(u)
                if (abs(d) < 1e-6f) break
                u -= err / d
                i++
            }
            if (u < 0f || u > 1f) {
                var lo = 0f
                var hi = 1f
                u = fraction
                while (hi - lo > 1e-5f) {
                    if (sampleX(u) < fraction) lo = u else hi = u
                    u = (lo + hi) / 2f
                }
            }
            return sampleY(u)
        }
    }
}

private val OffsetConverter: TwoWayConverter<Offset, AnimationVector2D> = TwoWayConverter(
    convertToVector = { AnimationVector2D(it.x, it.y) },
    convertFromVector = { Offset(it.v1, it.v2) },
)

internal object Spec {
    const val HOLD_TIME_MS = 320L
    val MOVE_LIMIT = 10.dp
    val PAD = 22.dp
    val MAX_DIST = 75.dp
    const val STACK_STEP = 3
    const val STACK_MAX = 12
    val CANVAS_HEIGHT = 1200.dp
    val CARD_W = 88.dp
    val CARD_H = 28.dp
    const val CARD_COUNT = 12
    const val INPUT_COUNT = 16
}

// 12 个子画布按钮名：与主画布 GreenLuaSwipeCanvas 的 12 个 GreenCanvasButton 按顺序一一对应。
// 功能管理画布里的 12 张卡片轻点即打开对应画布(卡片 N ↔ 第 N 个按钮)。
internal val SubCanvasNames = listOf(
    "ComponentPlaza", "Chess", "GeneralKnowledge", "ComponentManagement",
    "FunctionManagement", "SkillPlaza", "Key", "AIAssistSystem",
    "Self", "Credits", "Language", "SkillManagement"
)

// 联系方式图标(GitHub/WeChat/X/Discord)显隐开关：绿=显示，红=隐藏。
// 由功能管理画布第一个竖块切换，主画布读取后决定底部 4 个社交图标是否渲染。
// public(不加 internal)：MainActivity 在 androidApp 模块，跨模块调用 initialize 必须可见
object ContactIconStore {
    private const val KEY = "contact_icon_visibility_v1"
    private var storage: StorageProvider? = null

    var showContactIcons by mutableStateOf(true)
        private set

    /** MainActivity 启动时注入持久化存储。
     *  注意：启动时固定回到默认"绿=显示"，不回读存档——
     *  第一个竖块的点击态(粉=隐藏)只在本次会话内生效，重启不重播，
     *  修掉"一打开就停在点击后的状态(白圆红框)"。
     *  存档仍由 setContactIconsVisible 写入，留作备用。 */
    fun initialize(provider: StorageProvider) {
        storage = provider
        showContactIcons = true
    }

    /** 设置联系方式图标显隐并立即落盘(绿=显示/红=隐藏的持久化入口)。
     *  注意:不能叫 setShowContactIcons——会与属性自动生成的 setter 撞 JVM 签名 */
    fun setContactIconsVisible(value: Boolean) {
        if (showContactIcons == value) return
        showContactIcons = value
        runCatching { storage?.save(KEY, value.toString()) }
    }
}

// 第二个竖块的聊天模式信号：
//   拨开 = 即时聊天【不存档】——接下来新开的那一次对话不落盘，用完即焚；
//   默认 = 长久聊天【仍然存档】——照常持久化。
// "不存档"只作用于新开会话的那一次对话：ConversationStore 建会话时调用
// consumeEphemeralFlag() 取走标记并立刻归位——此后新开的会话(包括在即时
// 对话期间新开)仍然存档。
// 开关状态本身跨重启持久化(chat_mode_ephemeral_v1)；标记被消耗后的归位同样落盘。
object ChatModeStore {
    private const val KEY = "chat_mode_ephemeral_v1"
    private var storage: StorageProvider? = null

    /** true = 即时聊天(不存档)已就绪，等待下一次新开会话消耗 */
    var ephemeralNext by mutableStateOf(false)
        private set

    /** MainActivity 启动时注入持久化存储，并同步回读开关状态——重启后保持上次即时/长久 */
    fun initialize(provider: StorageProvider) {
        storage = provider
        provider.load(KEY)?.let { raw ->
            raw.trim().toBooleanStrictOrNull()?.let { ephemeralNext = it }
        }
    }

    /** 竖块拨动：开 = 即时聊天(不存档)，关 = 长久聊天(仍然存档)。立即落盘。
     *  注意:不能叫 setEphemeralNext——会与属性自动生成的 setter 撞 JVM 签名
     *  （与 ContactIconStore.setContactIconsVisible 同款坑） */
    fun setEphemeralMode(value: Boolean) {
        if (ephemeralNext == value) return
        ephemeralNext = value
        runCatching { storage?.save(KEY, value.toString()) }
    }

    /** ConversationStore 新建对话时调用：返回该对话是否不存档，同时消耗标记归位(回到长久聊天，归位也落盘) */
    fun consumeEphemeralFlag(): Boolean {
        val oneShot = ephemeralNext
        if (oneShot) setEphemeralMode(false)
        return oneShot
    }
}

// 跨画布导航总线：卡片轻点时写入目标画布名，主画布观察后切换 activeSubCanvas。
internal object CanvasNavigation {
    var requested by mutableStateOf<String?>(null)
}

// 卡片排列持久化 DTO：locType = "slot"(在格子里，slotIndex 有效) 或 "stack"(在堆叠栈里)
@Serializable
data class SavedCardEntry(val cardId: Int, val locType: String, val slotIndex: Int = -1)

@Serializable
data class SavedCardLayout(val cards: List<SavedCardEntry>, val stackOrder: List<Int>)

// 小格子排列持久化：12 张卡片"在哪个格子 / 是否进了堆叠栈 / 栈内顺序"全部跨重启保留
// public(不加 internal)：MainActivity 在 androidApp 模块，跨模块调用 initialize 必须可见
object CardLayoutStore {
    private const val KEY = "function_management_card_layout_v1"
    private var storage: StorageProvider? = null
    private val json = Json { ignoreUnknownKeys = true }

    /** initialize 时同步回读的存档(null = 从未保存过，画布用默认排列) */
    var loaded: SavedCardLayout? = null
        private set

    fun initialize(provider: StorageProvider) {
        storage = provider
        // 读档全链路显式化：文件不存在 / 读取出错 / 解码失败三种情况分别留痕——
        // 旧实现 runCatching 静默吞掉一切，存档坏了也毫无日志，重启必然回默认
        val raw = try {
            provider.load(KEY)
        } catch (t: Throwable) {
            println("[CardLayoutStore] load threw: $t")
            null
        }
        if (raw == null) {
            println("[CardLayoutStore] init: 磁盘上没有存档(首次启动，或存档从未写入成功)")
            loaded = null
            return
        }
        val decoded = try {
            json.decodeFromString<SavedCardLayout>(raw)
        } catch (t: Throwable) {
            println("[CardLayoutStore] decode FAILED(存档被忽略!): $t raw=$raw")
            null
        }
        loaded = decoded
        println("[CardLayoutStore] init: 存档已载入 cards=${decoded?.cards?.size} stack=${decoded?.stackOrder}")
    }

    /** 画布内 snapshotFlow 监听到排列变化后调用，一次性整份落盘 */
    fun save(locations: Map<Int, CardLocation>, stackOrder: List<Int>) {
        val s = storage
        if (s == null) {
            println("[CardLayoutStore] save 被跳过: initialize 还没调用!")
            return
        }
        val cards = locations.map { (id, loc) ->
            when (loc) {
                is CardLocation.Slot -> SavedCardEntry(id, "slot", loc.index)
                CardLocation.InStack -> SavedCardEntry(id, "stack", -1)
            }
        }
        val archive = SavedCardLayout(cards, stackOrder)
        // 旧实现 runCatching 静默吞写盘异常 = 写失败零日志、重启回原位；改为显式失败留痕
        try {
            s.save(KEY, json.encodeToString(archive))
            // 同步刷新内存存档：画布 remember 若中途重建(重读 loaded)，
            // 拿到的永远是最新布局，而不是启动时的旧快照
            loaded = archive
        } catch (t: Throwable) {
            println("[CardLayoutStore] save FAILED: $t")
        }
    }
}

@Composable
fun SubCanvasFunctionManagement(
    visible: Boolean,
    onClose: () -> Unit,
    pixelFont: FontFamily,
) {
    SlideDownContainer(
        visible = visible,
        title = "Function Management",
        onClose = onClose,
        pixelFont = pixelFont,
    ) {
        FunctionManagementCanvas(pixelFont, onClose)
    }
}

sealed interface CardLocation {
    data class Slot(val index: Int) : CardLocation
    data object InStack : CardLocation
}

sealed interface DropTarget {
    data class RectTarget(val cardId: Int) : DropTarget
    data class EmptySlot(val index: Int) : DropTarget
    data object Dashed : DropTarget
}

private data class RectSnapshot(
    val slots: Map<Int, Rect>,
    val cards: Map<Int, Rect>,
    val dashed: Rect,
)

@Stable
internal class DragController(
    private val scope: CoroutineScope,
    private val haptics: HapticFeedback,
    private val locations: Map<Int, CardLocation>,
    private val mutate: (Int, CardLocation) -> Unit,
    private val swapCards: (Int, Int) -> Unit,
    private val pushToStack: (Int) -> Unit,
    private val slotRects: Map<Int, Rect>,
    private val dashedRect: () -> Rect,
    private val cycler: StackCycler,
    density: Density,
) {
    private val padPx = with(density) { Spec.PAD.toPx() }
    private val maxDistPx = with(density) { Spec.MAX_DIST.toPx() }
    private val stackStepPx = with(density) { Spec.STACK_STEP.dp.toPx() }
    private val stackMaxPx = with(density) { Spec.STACK_MAX.dp.toPx() }
    private val cardSizePx = with(density) { Size(Spec.CARD_W.toPx(), Spec.CARD_H.toPx()) }

    var dragSourceId by mutableIntStateOf(-1)
        private set
    var highlight by mutableStateOf<DropTarget?>(null)
        private set
    var highlightShift by mutableStateOf(Offset.Zero)
        private set
    var isAnimatingDrop by mutableStateOf(false)
        private set
    var ghostCardId by mutableIntStateOf(-1)
        private set

    val moveLimitPx = with(density) { Spec.MOVE_LIMIT.toPx() }

    private var follow by mutableStateOf<Offset?>(null)
    val ghostScale = Animatable(1.06f)

    val ghostVisible: Boolean get() = ghostCardId >= 0
    val ghostPos: Offset get() = follow ?: Offset.Zero

    private var grabOffset = Offset.Zero
    private var lastFollow = Offset.Zero
    private var snapshot: RectSnapshot? = null

    var stackProvider: List<Int> = emptyList()

    private fun stackOffsetPx(i: Int): Offset {
        if (i < 0) return Offset.Zero
        val off = min(i * stackStepPx, stackMaxPx)
        return Offset(off, -off)
    }

    fun beginDrag(cardId: Int, localPointer: Offset) {
        if (ghostVisible || isAnimatingDrop || cycler.isCycling) return
        val slots = slotRects.toMap()
        val dashed = dashedRect()
        val cards = locations.mapValues { (id, loc) ->
            when (loc) {
                is CardLocation.Slot -> slots[loc.index] ?: Rect.Zero
                CardLocation.InStack -> Rect(dashed.topLeft + stackOffsetPx(stackIndexOf(id)), cardSizePx)
            }
        }
        snapshot = RectSnapshot(slots, cards, dashed)
        val cardTopLeft = (cards[cardId] ?: Rect.Zero).topLeft
        dragSourceId = cardId
        ghostCardId = cardId
        grabOffset = localPointer
        lastFollow = cardTopLeft
        follow = cardTopLeft
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        updateDrag(localPointer)
    }

    private fun stackIndexOf(id: Int): Int =
        (locations[id] as? CardLocation.InStack)?.let { stackProvider.indexOf(id) } ?: -1

    fun updateDrag(localPointer: Offset) {
        val s = snapshot ?: return
        if (dragSourceId < 0) return
        val pointerInRoot = (s.cards[dragSourceId] ?: Rect.Zero).topLeft + localPointer
        lastFollow = pointerInRoot - grabOffset
        follow = lastFollow
        val target = dropTargetAt(pointerInRoot)
        if (target != highlight) {
            highlight = target
            highlightShift = when (target) {
                is DropTarget.RectTarget -> {
                    val src = s.cards[dragSourceId] ?: Rect.Zero
                    val dst = s.cards[target.cardId] ?: Rect.Zero
                    src.topLeft - dst.topLeft
                }
                else -> Offset.Zero
            }
        }
    }

    private fun dropTargetAt(p: Offset): DropTarget? {
        val s = snapshot ?: return null
        val sourceInStack = locations[dragSourceId] is CardLocation.InStack

        if (!sourceInStack && s.dashed != Rect.Zero) {
            val d = s.dashed
            if (p.x >= d.left - padPx && p.x <= d.right + padPx &&
                p.y >= d.top - padPx && p.y <= d.bottom + padPx
            ) return DropTarget.Dashed
        }

        var best: DropTarget? = null
        var bestDist = Float.MAX_VALUE
        for ((id, r) in s.cards) {
            if (id == dragSourceId || locations[id] !is CardLocation.Slot) continue
            if (p.x < r.left - padPx || p.x > r.right + padPx ||
                p.y < r.top - padPx || p.y > r.bottom + padPx
            ) continue
            val dist = hypot(p.x - r.center.x, p.y - r.center.y)
            if (dist < bestDist && dist < maxDistPx) {
                bestDist = dist
                best = DropTarget.RectTarget(id)
            }
        }
        if (best != null) return best

        bestDist = Float.MAX_VALUE
        val sourceSlot = (locations[dragSourceId] as? CardLocation.Slot)?.index
        for ((idx, r) in s.slots) {
            val occupant = locations.entries.firstOrNull { it.value == CardLocation.Slot(idx) }?.key
            if (occupant != null && occupant != dragSourceId) continue
            if (idx == sourceSlot) continue
            if (p.x < r.left - padPx || p.x > r.right + padPx ||
                p.y < r.top - padPx || p.y > r.bottom + padPx
            ) continue
            val dist = hypot(p.x - r.center.x, p.y - r.center.y)
            if (dist < bestDist && dist < maxDistPx) {
                bestDist = dist
                best = DropTarget.EmptySlot(idx)
            }
        }
        return best
    }

    fun endDrag(commit: Boolean) {
        if (dragSourceId < 0) return
        val source = dragSourceId
        val target = if (commit) highlight else null
        // 落位即定格(直接放下)：同一瞬间清状态 + 提交布局——松手时卡片直接
        // 出现在目标位置：不播幽灵飞行动画、不延迟提交、源卡片不会先全亮
        // 留在原位再跳走("瞬移回原位再过来"残影的根源就是旧实现先飞 220ms、
        // 落地才改布局，且松手瞬间源卡片已取消变暗停在原格)。
        dragSourceId = -1
        ghostCardId = -1
        highlight = null
        highlightShift = Offset.Zero
        follow = null
        snapshot = null
        isAnimatingDrop = false
        when (target) {
            DropTarget.Dashed -> pushToStack(source)
            is DropTarget.RectTarget -> swapCards(source, target.cardId)
            is DropTarget.EmptySlot -> mutate(source, CardLocation.Slot(target.index))
            null -> {}
        }
    }
}

internal class CardAnim {
    val offset = Animatable(Offset.Zero, OffsetConverter)
    val rotation = Animatable(0f)
    val z = Animatable(1f)
}

@Stable
internal class StackCycler(
    private val scope: CoroutineScope,
    private val stackOrder: MutableList<Int>,
    density: Density,
) {
    var isCycling by mutableStateOf(false)
        private set

    private val stepPx = with(density) { Spec.STACK_STEP.dp.toPx() }
    private val maxPx = with(density) { Spec.STACK_MAX.dp.toPx() }
    private val flyOutPx = with(density) { Offset(54.dp.toPx(), -18.dp.toPx()) }
    private val flyBackPx = with(density) { Offset(-54.dp.toPx(), 14.dp.toPx()) }

    val anims = mutableStateMapOf<Int, CardAnim>()

    fun baseOffset(i: Int): Offset {
        val off = min(i * stepPx, maxPx)
        return Offset(off, -off)
    }

    private fun anim(id: Int) = anims.getOrPut(id) { CardAnim() }

    fun cycle(dir: Int) {
        if (isCycling || stackOrder.size < 2) return
        isCycling = true
        val n = stackOrder.size
        scope.launch {
            stackOrder.forEachIndexed { i, id ->
                val a = anim(id)
                a.offset.snapTo(baseOffset(i))
                a.rotation.snapTo(0f)
                a.z.snapTo((i + 1).toFloat())
            }
            if (dir == 1) {
                val topId = stackOrder[n - 1]
                val a = anim(topId)
                a.z.snapTo(60f)
                launch { a.rotation.animateTo(8f, tween(250, easing = CycleOutEase)) }
                a.offset.animateTo(flyOutPx, tween(250, easing = CycleOutEase))
                delay(240)
                stackOrder.removeAt(n - 1)
                stackOrder.add(0, topId)
                a.z.snapTo(1f)
                val jobs = stackOrder.mapIndexed { i, id ->
                    if (id == topId) null
                    else launch { anim(id).offset.animateTo(baseOffset(i), tween(280, easing = CycleInEase)) }
                }
                a.rotation.animateTo(0f, tween(280, easing = CycleInEase))
                a.offset.animateTo(Offset.Zero, tween(280, easing = CycleInEase))
                jobs.forEach { it?.join() }
            } else {
                val bottomId = stackOrder[0]
                val a = anim(bottomId)
                launch { a.rotation.animateTo(-8f, tween(250, easing = CycleOutEase)) }
                a.offset.animateTo(flyBackPx, tween(250, easing = CycleOutEase))
                delay(240)
                stackOrder.removeAt(0)
                stackOrder.add(bottomId)
                a.z.snapTo(60f)
                val topOff = baseOffset(n - 1)
                val jobs = stackOrder.mapIndexed { i, id ->
                    if (id == bottomId) null
                    else launch { anim(id).offset.animateTo(baseOffset(i), tween(280, easing = CycleInEase)) }
                }
                a.rotation.animateTo(0f, tween(280, easing = CycleInEase))
                a.offset.animateTo(topOff, tween(280, easing = CycleInEase))
                jobs.forEach { it?.join() }
            }
            delay(290)
            anims.clear()
            isCycling = false
        }
    }
}
