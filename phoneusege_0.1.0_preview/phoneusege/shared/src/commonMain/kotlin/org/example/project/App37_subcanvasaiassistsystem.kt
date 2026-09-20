package org.example.project

// ==============================================================================
// 画布文件 7/10 —— 场景层（下册 2/2）：相反滑块（撕开）自足模块
//
// 代码解离：场景层原为单文件（约 990 行），现按项目"平均解耦"惯例均分为
// 两个同包文件（≈ 1:1）：
//   1) SubCanvasAIAssistSystem.kt           —— 场景层（下册）：撕开滑块模块（本文）
//      EntryRound（private，文件可见）+ ReverseSlider（internal，同模块可见）
//   2) App46_subcanvasaiassistsystemone.kt  —— 场景层（上册）：画布主体 + 入口
//      UpArrow + AIAssistSystemContent + fun SubCanvasAIAssistSystem
// 依赖方向：上册调用下册（AIAssistSystemContent → ReverseSlider），单向无环；
// 两文件合起来依旧只依赖基础层（Theme）与组件层（Components），不反向依赖。
//
// 跨文件可见性说明：
//   - ReverseSlider 被上册 AIAssistSystemContent 调用 → private 提升为 internal；
//   - EntryRound 仅在 ReverseSlider 内部使用 → 保持 private（文件可见），不外泄。
//
// ⚠ 两文件是一个整体：拷入项目时【两个都要拷、且各自唯一】，
//   否则 Redeclaration: class EntryRound / Conflicting overloads 会再次出现。
//
// 本文内容（自足模块：拉出 / 撕开 / 白块全部状态内聚在本组件）：
//   - 拉出、撕开、白块出现动画与多轮机制（撕完一轮白卡留在原地，
//     下方再出现一个贴屏幕左边的新黑滑块，带"从最左边滑出来"的入场动画）
//   - 几何计算全部委托基础层 computeTearMetrics（纯函数引擎）；
//     本组件只负责手势、状态与绘制
//   - 出现时机由场景层通过 extraShow 传入（黑块盖住到位后淡入）
//   - 轮次条目经 AiAssistStore 持久化（空白白卡不落盘、不恢复）
// ==============================================================================

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ==================== 相反滑块（撕开）====================
// 自足模块：拉出 / 撕开 / 白块全部状态内聚在本组件。
// - 几何计算全部委托基础层的 computeTearMetrics（纯函数引擎），
//   本组件只负责手势、状态与绘制；
// - 出现时机由场景层通过 extraShow 传入（黑块盖住到位后淡入）。
//
// 多轮机制：撕完一轮后，白卡留在原地，下方再出现一个贴在屏幕左边的
// 新黑滑块（可继续拉出/撕开，再产生下一张白卡）。每一轮（含第一轮）
// 的黑滑块都带"从屏幕最左边滑出来"的入场动画。

/** 一轮撕开的条目状态：撕完（revealed）后成为留在原地的一张白卡 */
private class EntryRound {
    var revealed = false
    var title = ""
    var description = ""
    var text = ""
    var expanded = false

    /** 持久化快照（展开状态是瞬时 UI 态，不落盘） */
    fun toData() = AiAssistEntry(title = title, description = description, text = text)

    /** 是否写了内容：标题 / 说明 / 正文全空 = 用户撕开但什么都没写 → 不持久化 */
    fun hasContent() = title.isNotEmpty() || description.isNotEmpty() || text.isNotEmpty()
}

// internal（原 private）：代码解离后由上册 AIAssistSystemContent 跨文件调用，
// 同模块可见即可；EntryRound 仍 private，仅本文件内使用
@Composable
internal fun ReverseSlider(
    visible: Boolean,
    extraShow: Boolean,
    pixelFont: FontFamily,
    scrollState: ScrollState
) {
    // 淡入（跟随抽屉黑块盖住到位的时机，由场景层传入）
    val extraAlpha = remember(extraShow) { Animatable(if (extraShow) 1f else 0f) }
    LaunchedEffect(extraShow) {
        extraAlpha.animateTo(if (extraShow) 1f else 0f, tween(500))
    }

    // 轮次列表：已撕完的白卡轮 + 当前交互轮（最后一项，未撕完）。
    // 每撕完一轮 → 当前轮 revealed 并 append 新轮，新黑滑块出现。
    // 启动时从 AiAssistStore 恢复已撕完的白卡条目（标题 / 说明 / 正文），
    // 恢复的轮直接标记 revealed（展开状态是瞬时 UI 态，不恢复）。
    // ⚠ 空白白卡不恢复：用户撕开但什么都没写（标题 / 说明 / 正文全空）
    // 的条目不加载 —— 下次打开时不会出现一张空白卡
    val rounds = remember {
        val restored = AiAssistStore.state.entries
            .filter { e -> e.title.isNotEmpty() || e.description.isNotEmpty() || e.text.isNotEmpty() }
            .map { e ->
                EntryRound().apply {
                    revealed = true
                    title = e.title
                    description = e.description
                    text = e.text
                }
            }
        mutableStateListOf<EntryRound>().apply {
            addAll(restored)
            add(EntryRound())
        }
    }
    val activeRound = rounds.last()

    // 撕开白卡条目持久化：revealed 轮的内容（标题 / 说明 / 正文）随输入落盘。
    // LaunchedEffect 的键是 List<AiAssistEntry>，按结构性相等比较 ——
    // 内容没变不会重复写盘，内容变了（输入 / 新撕一轮）立即写。
    // ⚠ 只保存写了内容的轮：什么都没写的空白白卡不落盘（下次加载不出现）
    val entriesToSave = rounds
        .filter { it.revealed && it.hasContent() }
        .map { it.toData() }
    LaunchedEffect(entriesToSave) {
        AiAssistStore.update { it.copy(entries = entriesToSave) }
    }

    // 拉出进度：0 = 收拢（黑块向左探出 24dp）；1 = 完全拉出
    // （黑块右缘到竖线 —— 与上面抽屉块同样的位置）
    val extend = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    // 撕开状态（相反重叠主干）：
    // - tear = 撕完动画进度（镜像层揭起/微缩用）；
    // - tearSlide = 镜像黑滑块向左滑动的进度（0 = 贴住原位；1 = 完全滑出
    //   到最左缘 x=0）。双向可滑：往回拖 → 增大（撕），向前拖 → 减小（贴回）；
    // - 每轮条目内容（标题 / 说明 / 内容 / 展开）存在 EntryRound 里
    val tear = remember { Animatable(0f) }
    val tearSlide = remember { Animatable(0f) }
    // 往回划的累计位移（像素）：新黑块从第一个像素起就与清除块一起 1:1 跟手
    val pullAccum = remember { Animatable(0f) }
    // 是否已完全拉出：拉出到位后等待用户点击
    var hasReachedFull by remember { mutableStateOf(false) }
    // 撕开激活（点划后出现新黑块）：点划（tap）或点击并滑动（按下+拖动）
    // 都会激活；激活后立即跟手，无进入动画门槛
    var mirrorActivated by remember { mutableStateOf(false) }
    // 最后一次拖动的方向（true = 向右/贴回）：松手判定用 ——
    // 往回撕到 98% 以上后，如果最后一下是往右划，不应撕出（应贴回）
    var lastDragRight by remember { mutableStateOf(false) }
    // 首轮标记：只有"撕完旧轮后再出现"的新黑块才需要停顿 2 秒再入场；
    // 启动恢复（已有撕完白卡）时的新黑块应直接滑入，不再空等
    var isFirstEntry by remember { mutableStateOf(true) }
    // 当前轮是否已就绪：撕完后的停顿期间拖拽层禁用、黑块 / 槽位 / 预览白卡
    // 全部隐藏 —— 防止旧轮残留状态把新轮内容提前闪出来（重复闪一次动画内容）
    var roundArmed by remember { mutableStateOf(false) }

    // 白块出现动画：白块与黑色滑块重叠（藏在黑块后面同一位置）。
    // 黑滑块完全拉出（hasReachedFull）后白块出现；黑滑块被用户撕回后白块留在原地。
    // 双向动画：完全拉出 → 白块淡入；用户未点击就拖回 → 白块退回隐藏
    // （同步让槽位淡入回来，避免"拉出时槽位永远透明"的图层缺口）
    val cardAppear = remember { Animatable(0f) }
    LaunchedEffect(hasReachedFull) {
        cardAppear.animateTo(
            if (hasReachedFull) 1f else 0f,
            tween(320, easing = FastOutSlowInEasing)
        )
    }

    // 黑块滑出动画：每一轮（含第一轮）黑滑块从屏幕最左边滑出来。
    // slideIn 0 → 黑块完全在屏幕外（-52dp）；1 → 默认探出位（-24dp）
    // ⚠ 修复"黑块直接出现"：滑入动画必须等区块【可见之后】才开始 ——
    // 原来只 key rounds.size，动画在首次组合时（extraShow 还没变 true、
    // 整块 alpha=0 看不见）就已经播完；等抽屉盖住、区块淡入时黑块已经
    // 停在探出位，看起来就是"直接出现"。现在 key (extraShow, rounds.size)：
    // extraShow 变 true 才允许播放；首轮还等区块淡入（500ms）完成后再
    // 从屏幕最左边划出，划出动画完整可见
    val slideIn = remember { Animatable(1f) }
    // 上一次区块可见状态：false → true 的那一次（区块刚淡入）要等淡入完成
    var prevExtraShow by remember { mutableStateOf(false) }
    LaunchedEffect(extraShow, rounds.size) {
        if (!extraShow) {
            // 区块不可见：黑块停在原位，滑入动画不播放
            prevExtraShow = false
            slideIn.snapTo(1f)
            return@LaunchedEffect
        }
        // 区块刚淡入（首次可见）：等淡入（tween 500）结束再滑入，
        // 划出动画才完整可见；撕完旧轮后的新一轮（区块已可见）不用等
        val waitFadeMs = if (prevExtraShow) 0L else 520L
        prevExtraShow = true
        // 新一轮（撕完旧轮之后）：延迟 2 秒再出现新的黑滑块
        // （旧镜像块揭起飞走的动画早已结束，留出停顿让用户看到撕完结果）；
        // 首轮 / 启动恢复时不等，直接滑入
        if (!isFirstEntry) delay(2000)
        isFirstEntry = false
        // 重置单轮状态机，让新轮从零开始
        extend.snapTo(0f)
        tear.snapTo(0f)
        tearSlide.snapTo(0f)
        pullAccum.snapTo(0f)
        hasReachedFull = false
        mirrorActivated = false
        lastDragRight = false
        cardAppear.snapTo(0f)
        // 黑块先整体移到屏幕外（-52dp），等区块淡入完成后再开始滑入
        slideIn.snapTo(0f)
        if (waitFadeMs > 0L) delay(waitFadeMs)
        // 新轮就绪：允许交互（停顿期间拖拽层保持禁用）
        roundArmed = true
        // 从屏幕最左边划出（-52dp → -24dp 探出位）
        slideIn.animateTo(1f, spring(dampingRatio = 0.82f, stiffness = 240f))
    }

    Spacer(Modifier.height(14.dp))
    Box(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = extraAlpha.value }
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // 容器宽度先取为局部变量，供内层 Box 使用
            // （内层 Box 的 BoxScope 无法隐式访问外层的 maxWidth）
            val containerWidth = maxWidth

            // 完全拉出的像素量：黑块从起始位置（向左探出 24dp，右缘在 28dp）
            // 移到右缘刚好在白块的最右边（containerWidth − 16dp）。
            // 行程 = (containerWidth − 16 − 28) = containerWidth − 44dp。
            // toPx 需要 Density 上下文 —— BoxWithConstraintsScope 不提供，
            // 这里用 LocalDensity 显式提供
            val density = LocalDensity.current
            val maxDragPx = with(density) {
                (containerWidth - 44.dp).toPx()
            }.coerceAtLeast(1f)
            val maxDragPxHolder = rememberUpdatedState(maxDragPx)

            // 镜像黑滑块行程 = 从初始位置（原黑块最右，左缘 = 原黑块右缘
            // containerWidth−16dp）到屏幕最左缘 [0, 52] —— 新黑块可以一直
            // 拉到最左屏。行程 = containerWidth − 16dp
            val tearTravelPx = with(density) {
                (containerWidth - 16.dp).toPx()
            }.coerceAtLeast(1f)
            val tearTravelPxHolder = rememberUpdatedState(tearTravelPx)

            // 清除块冻结位置：1.1/4 处（14.3）再往左移一点点（2px）= 16.3px。
            // 在此之前清除区 1:1 跟手，之后冻结保持遮挡
            val eraseFreezePx = with(density) { ERASE_FREEZE_AT.toPx() }.coerceAtLeast(1f)
            val eraseFreezePxHolder = rememberUpdatedState(eraseFreezePx)
            // 清除块解锁点：新黑滑块【最右】（W+36−p）到达原黑块 2/3 处
            // （W−33.33）→ p = 4/3×52 = 69.33。此前清除区一直冻结
            val eraseResumePx = with(density) {
                ERASE_RESUME_DP.toPx()
            }.coerceAtLeast(1f)
            val eraseResumePxHolder = rememberUpdatedState(eraseResumePx)
            // 白矩形（槽位）开始向左收缩的条件：新黑滑块最右完全到达
            // 原滑块最左（overDead ≥ 104 = 2×52）才开始收缩
            val trackShrinkAtPx = with(density) {
                TRACK_SHRINK_AT_DP.toPx()
            }.coerceAtLeast(1f)
            val trackShrinkAtPxHolder = rememberUpdatedState(trackShrinkAtPx)

                // 撕完：当前轮白卡留在原地（revealed），镜像层滑出可视化区域 + 揭起；
                // 同时追加一个新轮 —— 下方再出现一个贴在屏幕左边的黑滑块（带滑入动画）
                val completeTear: () -> Unit = {
                    val round = rounds.last()
                    if (!round.revealed) {
                        round.revealed = true
                        // ⚠ 修复"重复闪一次动画内容"：撕完瞬间立刻复位新轮状态 ——
                        // 原来 hasReachedFull / cardAppear 要等 LaunchedEffect(rounds.size)
                        // 里 delay(2000) 之后才复位；停顿 2 秒期间残留 true：
                        // 新轮位置会立刻闪出一张空白预览白卡（撕开内容动画提前闪一次），
                        // 2 秒后才消失；拉出新块时白卡又淡入一次 —— 内容动画重复闪
                        hasReachedFull = false
                        roundArmed = false
                        isFirstEntry = false
                        scope.launch { cardAppear.snapTo(0f) }
                        rounds.add(EntryRound())
                        scope.launch {
                            launch {
                                tearSlide.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
                            }
                            launch {
                                tear.animateTo(1f, tween(340, easing = FastOutSlowInEasing))
                            }
                        }
                    }
                }

            // 往回划处理：新黑块从第一个像素就与清除块一起 1:1 跟手；
            // tearSlide = pullAccum / tearTravel（拉满 tearTravel = W−16
            // 即到最左缘 x=0）→ 撕完
            // ⚠ pullAccum 和 tearSlide 必须在【同一个协程】里顺序 snapTo ——
            // 若分两个 launch，两者相差一帧：清除区 C 段（跟 tearSlide）会比
            // 手指慢一帧，造成"清除块滑动时有延迟"的感觉
            fun handlePullBack(delta: Float) {
                // 记录最后一次拖动方向：向右（delta > 0）= 贴回意图，
                // 向左（delta < 0）= 撕开意图 —— 松手判定以此区分
                if (delta > 0f) lastDragRight = true
                else if (delta < 0f) lastDragRight = false
                val newAccum = (pullAccum.value - delta).coerceAtLeast(0f)
                scope.launch {
                    pullAccum.snapTo(newAccum)
                    val desired = (newAccum / tearTravelPxHolder.value)
                        .coerceIn(0f, 1f)
                    tearSlide.snapTo(desired)
                }
                if (newAccum >= tearTravelPxHolder.value) {
                    completeTear()
                }
            }

            // 拖拽主干：
            // - 未完全拉出：正常拉出/收回（extend 跟随手指）
            // - 完全拉出后：镜像黑滑块（tearSlide）跟随手指双向滑动 ——
            //   往回拖 = 撕（镜像层向左滑），向前拖 = 贴回（镜像层向右滑回原位）
            // - 撕过 90%（tearSlide ≥ 0.9，宽容判定）或甩速 < -400 直接撕完
            // - ⚠ 回弹：松手时没划完（没到完全拉出 / 没撕完）→ 自己滑回原位
            //   （tween 不过冲，不会多弹一下）；不再停在半路、也不"过半就自动拉满"
            val extDragState = rememberDraggableState { delta ->
                when {
                    activeRound.revealed -> {
                        // 已撕完：忽略后续拖动
                    }
                    hasReachedFull && !mirrorActivated -> {
                        // 拉到终点：原黑块锁死不动。
                        // 点击并滑动（按下+拖动）也激活镜像层：
                        // - 往回拖（delta < 0）→ 激活 + 立即跟手
                        // - 向前拖（delta > 0）→ 只激活、不延伸
                        if (delta < 0f) {
                            mirrorActivated = true
                            handlePullBack(delta)
                        } else if (delta > 0f) {
                            mirrorActivated = true
                        }
                    }
                    mirrorActivated -> {
                        // 已激活：立即跟手（无动画门槛）——
                        // 往回拖 → 累计左移（撕）；向前拖 → 累计回退（贴回）
                        handlePullBack(delta)
                    }
                    else -> {
                        // 普通滑块：只有未拉满时 extend 才跟随手指。
                        // 一旦到终点，hasReachedFull = true 且不再重置 ——
                        // 原黑块从此锁死在终点。
                        // ⚠ 宽容判定：拖过 90% 即算"划完"锁定（原来 98% 太苛刻，
                        // 差一点没到就整段弹回，容易误触回弹）
                        val target = (extend.value + delta / maxDragPxHolder.value).coerceIn(0f, 1f)
                        if (target >= 0.9f) {
                            hasReachedFull = true
                            scope.launch {
                                extend.stop()
                                extend.snapTo(1f)
                            }
                        } else {
                            scope.launch {
                                extend.stop()
                                extend.snapTo(target)
                            }
                        }
                    }
                }
            }

            // ==================== 轮次渲染 ====================
            // 撕完的白卡留在原地（逐张淡入放大）；当前交互轮在最后（黑滑块、
            // 槽位、镜像块与拖拽层）。撕完旧轮后，新黑滑块出现在白卡下方，
            // 从屏幕最左边滑出来 —— 可继续拉出/撕开，再产生下一张白卡。
            Column(Modifier.fillMaxWidth()) {
                // —— 已撕完的轮次：白卡列表 ——
                var isFirst = true
                rounds.forEach { round ->
                    if (round.revealed) {
                        Spacer(Modifier.height(if (isFirst) 14.dp else 10.dp))
                        isFirst = false
                        val appear = remember(round) { Animatable(0f) }
                        LaunchedEffect(round) {
                            appear.animateTo(1f, tween(320, easing = FastOutSlowInEasing))
                        }
                        CustomEntryCard(
                            expanded = round.expanded,
                            onToggle = { round.expanded = !round.expanded },
                            title = round.title,
                            onTitleChange = { round.title = it },
                            description = round.description,
                            onDescriptionChange = { round.description = it },
                            text = round.text,
                            onTextChange = { round.text = it },
                            appear = appear.value,
                            scrollState = scrollState,
                            pixelFont = pixelFont
                        )
                    }
                }

                // —— 当前交互轮（未撕完）：预览白卡 + 拖拽层 ——
                if (!activeRound.revealed) {
                    Spacer(Modifier.height(if (isFirst) 14.dp else 10.dp))
                    Box(Modifier.fillMaxWidth()) {
                        // 预览白卡：藏在黑块后面同一位置（先绘制 = 在黑块下方）——
                        // 黑滑块完全拉出（hasReachedFull）后淡入 + 轻微放大；
                        // 撕完瞬间由"已撕完轮次"列表接管渲染，白卡位置视觉连续；
                        // roundArmed 保证撕完停顿期间不闪出空白预览卡
                        if (hasReachedFull && roundArmed) {
                            CustomEntryCard(
                                expanded = activeRound.expanded,
                                onToggle = { activeRound.expanded = !activeRound.expanded },
                                title = activeRound.title,
                                onTitleChange = { activeRound.title = it },
                                description = activeRound.description,
                                onDescriptionChange = { activeRound.description = it },
                                text = activeRound.text,
                                onTextChange = { activeRound.text = it },
                                appear = cardAppear.value,
                                scrollState = scrollState,
                                pixelFont = pixelFont
                            )
                        }

            // 拖拽层：覆盖在黑块前面的全宽区域 —— 撕完后禁用（enabled = false），
            // 让藏在后面、留在原地的白块可以点击和填写
            // 按压不闪暗：indication = null 去掉 ripple —— 拖拽层是全宽长条，
            // 原来按空白处（白槽位）/ 按黑滑块那一长块，都会整条闪暗一下
            val dragLayerInteraction = remember { MutableInteractionSource() }
            Box(
                Modifier
                    .zIndex(1f)
                    .fillMaxWidth()
                    // 触发方式一：纯点击（tap）→ 新的黑块（镜像层）出现
                    // 触发方式二：点击并滑动（按下+拖动）→ 见 extDragState
                    // 的 hasReachedFull 分支，同样触发出现
                    // （clickable 在 draggable 之前：点击被识别、拖动仍正常）
                    .clickable(
                        interactionSource = dragLayerInteraction,
                        indication = null,
                        enabled = !activeRound.revealed && roundArmed
                    ) {
                        if (hasReachedFull && !mirrorActivated && !activeRound.revealed) {
                            mirrorActivated = true
                        }
                    }
                    .draggable(
                        orientation = Orientation.Horizontal,
                        state = extDragState,
                        enabled = !activeRound.revealed && roundArmed,
                        onDragStopped = { velocity ->
                            if (!activeRound.revealed) {
                                when {
                                    // 撕完判定（镜像层激活后）—— 只有"向左意图"才撕出：
                                    // 1) 快速往回甩（velocity < -400）→ 撕完；
                                    // 2) 已撕过 90%（宽容判定，原来 98% 太苛刻）且最后一下
                                    //    不是往右划（!lastDragRight）→ 撕完；
                                    // ⚠ 修复：往回撕到一定程度后瞬间往右划 ——
                                    //    快速向右甩不再触发撕出，而是走下方"回弹贴回"分支
                                    mirrorActivated &&
                                        (velocity < -400f ||
                                            (tearSlide.value >= 0.9f && !lastDragRight)) -> {
                                        completeTear()
                                    }
                                    // ⚠ 回弹动画：镜像层已激活但没撕完（含快速往右甩）→
                                    // 自己滑回贴回原位 —— tearSlide / pullAccum 并行弹回 0，
                                    // 黑块回到"完全拉出、等待点击"的状态。
                                    // ⚠ 修复"新黑块多弹了一下"：不能用欠阻尼 spring ——
                                    // 弹回 0 时会冲过头再荡回来（overshoot）；
                                    // 用 tween + FastOutSlowIn：丝滑归位、绝不过冲。
                                    // 动画期间若重新拖动，handlePullBack 的 snapTo 会接管
                                    mirrorActivated -> {
                                        scope.launch {
                                            launch {
                                                tearSlide.animateTo(
                                                    0f,
                                                    tween(260, easing = FastOutSlowInEasing)
                                                )
                                            }
                                            launch {
                                                pullAccum.animateTo(
                                                    0f,
                                                    tween(260, easing = FastOutSlowInEasing)
                                                )
                                            }
                                        }
                                    }
                                    // 已完全拉出（未激活镜像）：锁死不动，等待点击
                                    hasReachedFull -> {
                                    }
                                    else -> {
                                        // ⚠ 回弹动画：没划完（未到完全拉出）→ 自己滑回原位，
                                        // 黑块回到默认探出位（-24dp），等下次再拉；
                                        // 同样用 tween 不过冲（spring 欠阻尼会多弹一下）
                                        scope.launch {
                                            extend.animateTo(
                                                0f,
                                                tween(260, easing = FastOutSlowInEasing)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    )
            ) {
                // ==================== 几何（px / dp，与 HTML 版一一对应） ====================
                // 全部位置与裁剪量委托基础层几何引擎 computeTearMetrics
                val blockWidthPx = with(density) { BLOCK_W_DP.toPx() }
                val metrics = computeTearMetrics(
                    containerWidthPx = with(density) { containerWidth.toPx() },
                    extend = extend.value,
                    tearSlide = tearSlide.value,
                    pullAccum = pullAccum.value,
                    tearTravelPx = tearTravelPxHolder.value,
                    eraseFreezePx = eraseFreezePxHolder.value,
                    eraseResumePx = eraseResumePxHolder.value,
                    trackShrinkAtPx = trackShrinkAtPxHolder.value,
                    blockWidthPx = blockWidthPx,
                    revealed = activeRound.revealed,
                    density = density
                )
                val mirrorLeftDp = with(density) { metrics.mirrorLeftPx.toDp() }
                val trackWidthDp = with(density) { metrics.trackWidthPx.toDp() }

                // 白矩形（槽位）：宽度与收缩规则由几何引擎算出，
                // 外观由组件层 TrackSlot 承担；撕完时随 tear 淡出
                if (trackWidthDp > 0.dp && !activeRound.revealed && roundArmed) {
                    TrackSlot(
                        modifier = Modifier
                            .zIndex(0f)
                            .align(Alignment.CenterStart),
                        trackWidth = trackWidthDp,
                        fade = tear.value
                    )
                }

                // 原黑色滑块（z3）：无条件一直渲染 —— 不因清除量、不因撕完而卸载。
                // 起始位置向左探出 24dp，拉出时从 -24dp 移到右缘刚好在白块的最右边；
                // 左缘直角、右缘圆角 12dp；清除效果 = clipRect 只裁剪本图层。
                // 外形与裁剪统一由组件层 TearBlock 承担（与镜像块共用）。
                // 滑入动画：slideIn 0 → 额外左移 28dp（黑块完全在屏幕外 -52dp），
                // 1 → 默认探出位 -24dp —— 即"从屏幕最左边滑出来"
                val slideOffset = (-28).dp * (1f - slideIn.value)
                val blockOffsetX = (-24).dp + (containerWidth - 44.dp) * extend.value + slideOffset
                // roundArmed 门控：撕完停顿期间原黑块也隐藏，
                // 避免旧轮残留的 tearSlide / pullAccum 把新轮黑块提前擦出残影
                if (roundArmed) {
                    TearBlock(
                        modifier = Modifier
                            .zIndex(3f)
                            .align(Alignment.CenterStart),
                        offsetX = blockOffsetX,
                        erasePx = metrics.erasePx,
                        shape = RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp),
                        arrowRight = true
                    )
                }

                // ==================== 撕开主干：新黑块（镜像层） ====================
                // 黑色 52×76、左圆角、左箭头，永远高于原黑块（z4 > 原黑块 z3）。
                // 初始位置 = 原黑块最右（左缘 = 原黑块右缘 W−16，区间 [W−16, W+36]，
                // 超出容器右缘的部分被裁剪）；随手指 1:1 左移，可一直拉到最左缘 x=0。
                // 清除效果 = clipRect 只裁剪本图层（mirrorErasePx）：
                //   A 全裁（不可见）、B 右半仍被遮（从右往左渐进露出）、C 完全露出；
                // 撕完揭起 + 淡出（旋转 + 上移 + 微缩）飞走
                // ⚠ drawWithContent 必须在 background 之前（更外层）——
                // 这样 clipRect 才连黑色背景一起裁剪；若放在 background 之后，
                // 背景先绘制不被裁剪，新黑块永远全黑可见（APK 上"没遮住"的 bug）
                if (mirrorActivated) {
                    // 撕完揭起 + 淡出（旋转 + 上移 + 微缩）在 graphicsLayer；
                    // 外形与裁剪复用组件层 TearBlock（左圆角、左箭头）
                    TearBlock(
                        modifier = Modifier
                            .zIndex(4f)
                            .align(Alignment.CenterStart)
                            .graphicsLayer {
                                // 撕完揭起 + 淡出：旋转 + 上移 + 微缩
                                alpha = 1f - tear.value
                                rotationZ = -tear.value * 14f
                                translationY = -tear.value * 20f
                                scaleX = 1f - tear.value * 0.06f
                                scaleY = 1f - tear.value * 0.06f
                            },
                        offsetX = mirrorLeftDp,
                        erasePx = metrics.mirrorErasePx,
                        shape = RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp),
                        arrowRight = false
                    )
                }
            }
                        } // 当前轮内层 Box 结束
                    } // 当前轮 if 结束
                } // 轮次 Column 结束
        }
    }
    Spacer(Modifier.height(14.dp))
}
