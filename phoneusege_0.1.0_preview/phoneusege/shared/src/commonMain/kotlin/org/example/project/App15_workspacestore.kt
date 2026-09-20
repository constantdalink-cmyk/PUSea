package org.example.project

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class JsWorkspaceEntry(
    val id: String,
    val name: String,
    val isFolder: Boolean,
    val parentId: String?
)

data class JsWorkspaceSnapshot(
    val entries: List<JsWorkspaceEntry>,
    val openedFileIds: List<String>,
    val selectedFileId: String?,
    val fileContents: Map<String, String>,
    val idCounter: Int
)

/** 画板竖块存档条目：组号（1/2/3）+ 组内块 id + 起名横线文字。 */
data class SketchBlockRecord(
    val groupNo: Int,
    val blockId: Int,
    val caption: String
)

/**
 * 画板竖块结构快照（全局持久化的数据载体，不挂会话、永远保存）：
 * blocks = 三组竖块打平的条目序列；nextBlockIds = 每组专属的下一个块编号（长度恒为 3）；
 * canvasKind = 画布身份持久化位（"BLACK_CANVAS"/"NEW_CANVAS"，重启后 restore 回存档身份）；
 * seedConsumed = 遗留兼容位（旧版"标志位"方案的一次性标记）：继承判定已改由磁盘事实
 * （hasAnyProjectContent）裁决、不再读它，保留字段只为新旧存档双向兼容。
 * JSON 编解码在平台侧 storage 实现里完成（与 JsWorkspaceSnapshot 同一分工）。
 */
data class SketchBlocksSnapshot(
    val blocks: List<SketchBlockRecord>,
    val nextBlockIds: List<Int>,
    val seedConsumed: Boolean = false,
    val canvasKind: String = "BLACK_CANVAS"
)

interface JsWorkspaceStorage {
    /** 最近一次访问分区的展示根路径（遗留兼容；新代码请用 displayRootPathFor）。 */
    val displayRootPath: String

    /** 展示根路径：返回指定持久化键（会话×画布）的真实 scripts 目录。纯路径计算，不读盘、无状态、无竞态。 */
    fun displayRootPathFor(conversationId: String): String

    suspend fun loadWorkspace(conversationId: String): JsWorkspaceSnapshot?
    suspend fun saveWorkspace(conversationId: String, snapshot: JsWorkspaceSnapshot)

    /**
     * 画板竖块结构存档（全局单份、不挂会话、永远保存）：读回 null = 从未保存过（空白三组）。
     * conversationId 参数仅为接口兼容保留，全局存档不区分会话。
     * 默认无操作实现：未适配的端口画板竖块不跨重启，工作区持久化不受影响。
     */
    suspend fun loadSketchBlocks(conversationId: String): SketchBlocksSnapshot? = null
    suspend fun saveSketchBlocks(conversationId: String, snapshot: SketchBlocksSnapshot) { }

    /**
     * 项目继承门禁（磁盘事实）：是否已存在内容非空的项目分区（项目分区是全局的、不挂会话；
     * conversationId 参数仅为接口兼容保留）。
     * 纯只读；默认 false（未适配的端口退化为"每次画布模式下首点都可继承"，Android 实现见同名 override）。
     */
    suspend fun hasAnyProjectContent(conversationId: String): Boolean = false
}

object JsWorkspaceStore {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var storage: JsWorkspaceStorage? = null
    private var saveJob: Job? = null
    private var idCounter: Int = 0

    // ==================== 画布解离（新画布 ⇄ 旧黑画布，文件互不侵入） ====================
    // 持久化键分区（新画布/新新画布"永远"：不挂会话，全局单份；旧画布仍按会话）：
    //   · 旧黑画布（BLACK_CANVAS）：键 = conversationId —— 会话级，零迁移，老数据原地不动；
    //   · 新画布（NEW_CANVAS）：键 = 全局常量 KEY_GLOBAL_NEW ("__new") —— 与会话无关、永远保存；
    //   · 竖块项目：键 = KEY_SUFFIX_PROJECT + 项目 id ("__proj_{id}") —— 同样全局、永远保存；
    //   · storage 实现按键透明映射成独立子目录（js_workspace/{键}/），三类分区互为兄弟、文件天然隔离；
    //   · 地址栏 rootPath 直接取该键的真实物理路径（storage.displayRootPathFor），
    //     不伪造任何展示后缀，左滑复制拿到的就是盘上真实位置。
    // 机制整套复用现成的"会话切换"设施（内存缓存 + loadGeneration 防乱序 + 切走即落盘）：
    // 切换画布 = 切换工作区。入口唯一：CanvasIdentity.flip()（App71）翻身份后调 switchCanvas，
    // 手势站点不直接调本函数。
    // 继承：新画布内容为空时打开（首开或清空后）→ 单向克隆旧画布文件树为种
    // （连标签页/选中项/idCounter 一并继承）；旧画布分区全程只读，"旧画布不变"；
    // 新画布一旦有自己的内容，两者各自独立演进，不再回种。
    // 项目继承（同构再下沉一级）：没有创建任何竖块时创建的文件库，只继承给第一个点击的竖块。
    // 裁决不靠任何标志位——门禁 = 磁盘事实：该会话还没有任何非空项目分区时才允许种入
    // （内存缓存 + 磁盘双查，见 switchProject / anyProjectContentInCache / hasAnyProjectContent）。
    // 首个拿种的分区立即落盘，门禁对该会话永久关闭：翻转/切换/重启/快点，结构上均无二次继承。
    // 竖块结构持久化（全局维）：画板三组竖块（id + 起名 + 组计数器）跨重启保留、不挂会话、永远保存，
    // 存档 js_workspace/sketch_blocks.json（全局单份），随工作区同一写漏斗落盘（requestSave/flushNow）；
    // 切换会话不改变竖块（回读的永远是同一份全局存档）；内存态 ⇄ 快照的桥接见 SketchBlockStore（App71）。
    /** 新画布全局分区键：不挂会话、永远保存（旧黑画布键仍是会话级 conversationId）。 */
    private const val KEY_GLOBAL_NEW = "__new"
    /** 项目分区前缀：每个「新竖块」= 一个新项目，键 = __proj_ + 项目 id（全局、不挂会话、永远保存）。 */
    private const val KEY_SUFFIX_PROJECT = "__proj_"

    /** 持久化键：BLACK = 会话级 conversationId；NEW = 全局常量分区（与会话无关、永远保存）。 */
    private fun storageKey(conversationId: String, kind: CanvasKind): String =
        if (kind == CanvasKind.NEW_CANVAS) KEY_GLOBAL_NEW else conversationId

    /** 项目持久化键：__proj_ + 项目 id（全局、不挂会话、永远保存）。每个项目一块独立工作区（初始为空，互不侵入）。 */
    private fun projectKey(projectId: String): String =
        KEY_SUFFIX_PROJECT + projectId

    /**
     * 当前激活工作区的持久化键：优先项目维（点击竖块进入项目后），否则回落到画布维。
     * 所有写路径（requestSave/flushNow）与读路径（snapshotOf）统一从这里取键。
     */
    private fun activeStorageKey(conversationId: String): String =
        if (activeProject != null) projectKey(activeProject!!)
        else storageKey(conversationId, activeCanvas)

    // 展示根路径不再由 store 伪造：按持久化键向 storage.displayRootPathFor(key) 取真实物理目录
    // （旧画布 …/{conv}/scripts，新画布 …/__new/scripts，项目 …/__proj_{id}/scripts），见下方调用点。

    /**
     * store 当前持有内存数据的画布身份。
     * 与 CanvasIdentity.kind 分工：那边是"界面呈现什么身份"（显示真源），
     * 这边是"内存里装着哪个键的数据"（持久化真源）；flip() 保证两者同步切换。
     */
    private var activeCanvas: CanvasKind = CanvasKind.BLACK_CANVAS

    /**
     * 当前激活的项目 id（新新画布里点竖块进入项目后非空，否则为 null = 画布模式）。
     * 项目 = 一块独立工作区；切换会话/画布会退出项目模式。
     */
    private var activeProject: String? = null

    // 项目继承不用任何标志位（两代方案的教训）：进程内标记会被 switchCanvas / switchConversation
    // 复位，持久化标记也可能因文件版本不同步而形同虚设——两者都造成"竖块 2 继承了、竖块 1
    // 又继承"的双份继承。现方案无状态可失同步：是否允许种入，每次点击都现场问磁盘
    // （该会话有没有非空项目分区），见 switchProject 的 hasProjectContent 门禁。

    /** 任务2：会话与代码库绑定。内存缓存：持久化键(会话×画布/项目) -> 该分区的工作区快照 */
    private val workspaceCache = mutableMapOf<String, JsWorkspaceSnapshot>()
    /** 切换防乱序：连续快速切换时，过期的加载协程直接丢弃结果 */
    private var loadGeneration = 0
    /** 画布身份 restore 一次性门闩：只在进程启动后的首次成功载入时生效，运行时的会话切换绝不触发 */
    private var startupRestoreDone = false

    private val _activeConversationId = MutableStateFlow<String?>(null)
    val activeConversationId: StateFlow<String?> = _activeConversationId

    private val _isLoaded = MutableStateFlow(false)
    val isLoaded: StateFlow<Boolean> = _isLoaded

    private val _rootPath = MutableStateFlow("")
    val rootPath: StateFlow<String> = _rootPath

    private val _entries = MutableStateFlow<List<JsWorkspaceEntry>>(emptyList())
    val entries: StateFlow<List<JsWorkspaceEntry>> = _entries

    private val _openedFileIds = MutableStateFlow<List<String>>(emptyList())
    val openedFileIds: StateFlow<List<String>> = _openedFileIds

    private val _selectedFileId = MutableStateFlow<String?>(null)
    val selectedFileId: StateFlow<String?> = _selectedFileId

    private val _fileContents = MutableStateFlow<Map<String, String>>(emptyMap())
    val fileContents: StateFlow<Map<String, String>> = _fileContents

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError

    fun initialize(storage: JsWorkspaceStorage) {
        this.storage = storage
        _rootPath.value = storage.displayRootPathFor("conversation_1")
        // 兜底绑定默认会话；真实当前会话由外部（MainActivity）在 ConversationStore 就绪后重新 switchConversation
        switchConversation("conversation_1")
    }

    /**
     * 任务2：会话与代码库绑定。切换会话 = 切换工作区（画布身份不变，键只换会话维）。
     *  - 先把当前会话的内存快照写入缓存并立即落盘（切走不丢修改；竖块全局存档也随同一协程顺带写入）；
     *  - 再加载目标会话的工作区：有缓存用缓存，无缓存读盘，盘上也没有（新会话）→ 空工作区，什么也没有；
     *  - 竖块结构是全局存档（不挂会话）：回读到的永远是同一份全局三组，切换会话竖块保持原样；
     *  - 加载中的旧会话不缓存不落盘，避免空状态覆盖已存数据。
     */
    fun switchConversation(conversationId: String) {
        val current = _activeConversationId.value
        if (current == conversationId) return

        saveJob?.cancel()
        val prevId = current
        if (prevId != null && _isLoaded.value) {
            val prevKey = activeStorageKey(prevId)
            val prevSnap = snapshot()
            // 竖块结构是全局存档（不区分新旧会话）：切会话这里也顺带写一次，多一个落盘点（launch 前捕获）
            val prevBlocks = SketchBlockStore.toSnapshot()
            workspaceCache[prevKey] = prevSnap
            val s = storage
            if (s != null) {
                scope.launch {
                    try {
                        s.saveWorkspace(prevKey, prevSnap)
                        s.saveSketchBlocks(prevId, prevBlocks)
                        _lastError.value = null
                    } catch (t: Throwable) {
                        _lastError.value = t.message ?: "Save failed"
                    }
                }
            }
        }

        // 切换会话 = 退出项目模式。继承门禁由磁盘事实裁决（见 switchProject），无内存状态可复位。
        activeProject = null
        _activeConversationId.value = conversationId
        val targetKey = activeStorageKey(conversationId)
        // 地址栏同步跟随持久化键的真实物理路径（纯计算，先于加载协程，无竞态）
        _rootPath.value = storage?.displayRootPathFor(targetKey) ?: ""

        val gen = ++loadGeneration
        _isLoaded.value = false
        val cached = workspaceCache[targetKey]
        scope.launch {
            try {
                val snap = cached
                    ?: storage?.loadWorkspace(targetKey)
                    ?: JsWorkspaceSnapshot(emptyList(), emptyList(), null, emptyMap(), 0)
                if (gen != loadGeneration) return@launch
                applySnapshot(snap)
                ensureSelectedTab()
                if (cached == null) workspaceCache[targetKey] = snap
                // 竖块结构回读全局存档：不区分会话，永远是同一份三组（无存档 = 空白三组）
                val blocksSnap = storage?.loadSketchBlocks(conversationId)
                SketchBlockStore.applySnapshot(blocksSnap)
                // 画布身份全局持久 · 启动 restore：重启后落回上次停留的画布——
                // 停在新画布关的 app，重开直接在新画布，文件原地可见，不会因身份复位
                // 产生"文件丢了/只持久化了一个文件"的错觉。仅首次载入生效（门闩在上、
                // flip() 运行时与存档双向同步），运行时切换会话绝不触发，无回环。
                if (!startupRestoreDone) {
                    startupRestoreDone = true
                    val savedKind = when (blocksSnap?.canvasKind) {
                        CanvasKind.NEW_CANVAS.name -> CanvasKind.NEW_CANVAS
                        else -> CanvasKind.BLACK_CANVAS
                    }
                    if (savedKind != activeCanvas) {
                        CanvasIdentity.kind = savedKind
                        switchCanvas(savedKind)
                    }
                }
                _lastError.value = null
            } catch (t: Throwable) {
                if (gen == loadGeneration) _lastError.value = t.message ?: "Load failed"
            } finally {
                if (gen == loadGeneration) _isLoaded.value = true
            }
        }
    }

    /**
     * 画布解离：切换画布 = 切换工作区（机制与 switchConversation 完全同构，只换键的画布维）。
     *  - 先把当前内存态按"旧画布键"写缓存并立即落盘（切走不丢修改）；
     *  - 再加载"新画布键"的工作区：缓存 → 盘 → 空工作区；
     *  - 继承：加载结果为空 且 本次是 旧→新 且 旧画布有内容 → 克隆旧画布快照为种
     *   （单向；旧画布全程只读不变；新画布有自己的内容后各自独立，不再回种）；
     *  - 旧黑画布键 = 原 conversationId，故此升级对存量数据零迁移、零破坏。
     * 由 CanvasIdentity.flip()（App71）统一调用，手势站点不直接调本函数。
     */
    fun switchCanvas(kind: CanvasKind) {
        if (kind == activeCanvas) return

        saveJob?.cancel()
        val convId = _activeConversationId.value
        // 继承种子：在翻转身份前捕获当前（旧画布）快照。
        // 仅 旧→新 方向可用作种子；新→旧 绝不回种（旧画布不变）。
        var seedFromOld: JsWorkspaceSnapshot? = null
        if (convId != null && _isLoaded.value) {
            val prevKey = activeStorageKey(convId)
            val prevSnap = snapshot()
            // 竖块全局存档（含画布身份位）也随切换落盘：flip() 已先更新内存态，launch 前捕获
            val prevBlocks = SketchBlockStore.toSnapshot()
            if (kind == CanvasKind.NEW_CANVAS && activeCanvas == CanvasKind.BLACK_CANVAS) {
                seedFromOld = prevSnap
            }
            workspaceCache[prevKey] = prevSnap
            val s = storage
            if (s != null) {
                scope.launch {
                    try {
                        s.saveWorkspace(prevKey, prevSnap)
                        s.saveSketchBlocks(convId, prevBlocks)
                        _lastError.value = null
                    } catch (t: Throwable) {
                        _lastError.value = t.message ?: "Save failed"
                    }
                }
            }
        }

        // 切换画布 = 退出项目模式。继承门禁看项目分区的磁盘事实（见 switchProject），
        // 没有任何内存状态，画布翻转天然无法影响它——"竖块 2 继承后翻转再点竖块 1"永不二次继承。
        activeProject = null
        activeCanvas = kind
        if (convId == null) return
        val targetKey = storageKey(convId, kind)
        // 地址栏同步切到新画布真实物理路径（全局分区 …/__new/scripts；翻回旧画布则是 …/{conv}/scripts）
        _rootPath.value = storage?.displayRootPathFor(targetKey) ?: ""

        val gen = ++loadGeneration
        _isLoaded.value = false
        val cached = workspaceCache[targetKey]
        val seed = seedFromOld
        scope.launch {
            try {
                val stored = cached ?: storage?.loadWorkspace(targetKey)
                var seeded = false
                val snap = when {
                    // 目标分区已有自己的内容 → 正常加载，继承绝不覆盖
                    stored != null && stored.entries.isNotEmpty() -> stored
                    // 继承：目标为空 且 旧→新 且 旧画布有内容 → 克隆旧画布快照为种。
                    // 快照是不可变结构，与旧键缓存共享实例天然安全；idCounter 随源延续，
                    // 新画布里续建文件编号接续不撞车。
                    seed != null && seed.entries.isNotEmpty() -> {
                        seeded = true
                        seed
                    }
                    stored != null -> stored
                    else -> JsWorkspaceSnapshot(emptyList(), emptyList(), null, emptyMap(), 0)
                }
                if (gen != loadGeneration) return@launch
                applySnapshot(snap)
                ensureSelectedTab()
                // 只缓存非空快照：空分区保持"未出生"，旧画布之后长了文件，继承仍可触发
                if (cached == null && snap.entries.isNotEmpty()) workspaceCache[targetKey] = snap
                _lastError.value = null
                if (seeded) {
                    // 继承到的文件树立刻落盘：__new 分区当场实体化，
                    // 防止随后快速切回或退后台时继承态丢失。
                    requestSave(0L)
                }
            } catch (t: Throwable) {
                if (gen == loadGeneration) _lastError.value = t.message ?: "Load failed"
            } finally {
                if (gen == loadGeneration) _isLoaded.value = true
            }
        }
    }

    /**
     * 项目解离：点击新新画布里的竖块 = 进入该项目的工作区（机制与 switchConversation 同构）。
     * 每个竖块（除加号母体）= 一个新项目，键 = "__proj_" + projectId（全局、不挂会话、永远保存）。
     *  - 先把当前（画布或上一个项目）的内存态写缓存并落盘（切走不丢修改）；
     *  - 再加载目标项目的工作区：缓存 → 盘 → 空工作区（新项目第一次打开天然空白）；
     *  - 项目继承（"没有创建任何竖块时创建的文件库，只继承给第一个点击的竖块"）：
     *    门禁 = 磁盘事实，不靠标志位——目标项目为空 且 画布有文件 且 该会话还没有任何
     *    非空项目分区（缓存 + 磁盘双查）才单向克隆画布文件树为种（连标签页/选中项/
     *    idCounter 一并继承；画布分区全程不变）。首个拿种的分区立即落盘、门禁永久关闭：
     *    翻转画布/切换会话/重启/连续快点，结构上都不可能二次继承，其余竖块各自空白；
     *  - 进入项目后，编辑器/资源管理器的所有读写都落到该项目键，与画布/其他项目互不侵入。
     * 由 SketchPadPanel 的竖块点击（App14 接线）调用；切换会话/画布会退出项目模式。
     */
    fun switchProject(projectId: String) {
        val convId = _activeConversationId.value ?: return
        if (activeProject == projectId) return

        saveJob?.cancel()
        // 项目继承种子：仅在"还没进过任何项目"（activeProject == null = 画布模式）时捕获画布快照——
        // 对应需求：没有创建任何竖块时在画布里创建的文件库，继承到第一个创建的竖块。
        // 从项目里再开别的项目（activeProject != null）不取种，种子永远来自画布。
        // 取到种也不等于种入：还要过磁盘事实门禁（该会话还没有任何非空项目分区）这一关。
        var seedFromCanvas: JsWorkspaceSnapshot? = null
        if (_isLoaded.value) {
            val prevKey = activeStorageKey(convId)
            val prevSnap = snapshot()
            val prevBlocks = SketchBlockStore.toSnapshot()
            if (activeProject == null) {
                seedFromCanvas = prevSnap
            }
            workspaceCache[prevKey] = prevSnap
            val s = storage
            if (s != null) {
                scope.launch {
                    try {
                        s.saveWorkspace(prevKey, prevSnap)
                        s.saveSketchBlocks(convId, prevBlocks)
                        _lastError.value = null
                    } catch (t: Throwable) {
                        _lastError.value = t.message ?: "Save failed"
                    }
                }
            }
        }

        activeProject = projectId
        val targetKey = activeStorageKey(convId)
        // 地址栏同步切到项目真实物理路径（全局分区 …/__proj_{id}/scripts）
        _rootPath.value = storage?.displayRootPathFor(targetKey) ?: ""

        val gen = ++loadGeneration
        _isLoaded.value = false
        val cached = workspaceCache[targetKey]
        val seed = seedFromCanvas
        scope.launch {
            try {
                val stored = cached ?: storage?.loadWorkspace(targetKey)
                // 继承门禁（磁盘事实，无任何标志位）：该会话是否已有非空项目分区。
                // 先查进程内缓存（同步，堵住同进程竞态窗口），再查磁盘（覆盖重启/冷启动）。
                // 首个拿种的分区在下面立即落盘 + 写缓存，此门禁对该会话从此永久为假。
                val hasProjectContent = anyProjectContentInCache() ||
                    (storage?.hasAnyProjectContent(convId) ?: false)
                var seeded = false
                val snap = when {
                    // 目标项目已有自己的内容 → 正常加载，继承绝不覆盖
                    stored != null && stored.entries.isNotEmpty() -> stored
                    // 项目继承（结构上只给第一个点击的竖块）：目标为空 且 画布有文件 且
                    // 该会话还没有任何非空项目分区 → 单向克隆画布文件树为种
                    // （连标签页/选中项/idCounter 一并继承；画布分区全程不变；
                    // 快照不可变，与画布键缓存共享实例天然安全）。
                    seed != null && seed.entries.isNotEmpty() && !hasProjectContent -> {
                        seeded = true
                        seed
                    }
                    stored != null -> stored
                    else -> JsWorkspaceSnapshot(emptyList(), emptyList(), null, emptyMap(), 0)
                }
                if (gen != loadGeneration) return@launch
                applySnapshot(snap)
                ensureSelectedTab()
                // 只缓存非空快照：空项目保持"未出生"，与 switchCanvas 的空分区策略一致
                if (cached == null && snap.entries.isNotEmpty()) workspaceCache[targetKey] = snap
                _lastError.value = null
                if (seeded) {
                    // 继承到的文件树立刻落盘：项目分区当场实体化——磁盘门禁随即对本会话
                    // 永久关闭，之后点的其他竖块（哪怕同一秒）都拿不到种。
                    requestSave(0L)
                }
            } catch (t: Throwable) {
                if (gen == loadGeneration) _lastError.value = t.message ?: "Load failed"
            } finally {
                if (gen == loadGeneration) _isLoaded.value = true
            }
        }
    }

    /**
     * 继承门禁的进程内快速通道：缓存里是否已有非空项目分区（同步，无 IO）。
     * 与磁盘查询（hasAnyProjectContent）双查：缓存堵住同进程快速连点的竞态窗口，
     * 磁盘覆盖重启/冷启动。项目分区是全局的（键前缀 = KEY_SUFFIX_PROJECT，与 projectKey 同构），无会话维。
     */
    private fun anyProjectContentInCache(): Boolean =
        workspaceCache.any { (key, snap) ->
            key.startsWith(KEY_SUFFIX_PROJECT) && snap.entries.isNotEmpty()
        }

    private fun applySnapshot(s: JsWorkspaceSnapshot) {
        _entries.value = s.entries
        // 载入卫生：过滤指向已不存在条目的陈旧 id（被删文件的历史开页记录），
        // 防止标签栏出现点了没反应的"鬼标签"
        val validIds = s.entries.map { it.id }.toSet()
        _openedFileIds.value = s.openedFileIds.filter { it in validIds }
        _selectedFileId.value = s.selectedFileId?.takeIf { it in validIds }
        _fileContents.value = s.fileContents
        idCounter = s.idCounter
    }

    /**
     * 载入完成后的标签栏兜底：正在编辑的文件（selectedFileId）若不在开页列表里
     * （陈旧存档/异常路径），自动补回标签栏并落盘——重启后至少"重启前正在编辑的那个文件"
     * 一定还在编辑器上方的快捷小块里，不会只剩资源管理器里找得到。
     */
    private fun ensureSelectedTab() {
        val sel = _selectedFileId.value ?: return
        if (sel in _openedFileIds.value) return
        if (_entries.value.none { it.id == sel && !it.isFolder }) return
        _openedFileIds.value = _openedFileIds.value + sel
        requestSave(0L)
    }

    /**
     * 读取指定项目的工作区快照（长按竖块 → 打包 zip 分享用，见 App16）：
     * 键 = projectKey(projectId)（全局分区 __proj_{id}，与当前画布/会话无关）；
     * null = 该项目还没有盘上数据（空项目）。
     */
    suspend fun loadProjectSnapshot(projectId: String): JsWorkspaceSnapshot? =
        storage?.loadWorkspace(projectKey(projectId))

    /**
     * 任务3：取指定会话的工作区快照（copycode 用）。
     * 缓存优先；目标是当前会话时直接取当前内存态；否则读盘（无数据返回 null）。
     * 画布解离后按"当前画布身份"取对应分区：新画布开着就取新画布的文件树。
     */
    suspend fun snapshotOf(conversationId: String): JsWorkspaceSnapshot? {
        val key = activeStorageKey(conversationId)
        workspaceCache[key]?.let { return it }
        if (conversationId == _activeConversationId.value) return snapshot()
        return storage?.loadWorkspace(key)
    }

    /**
     * 任务3：把外部快照整体应用到当前工作区（copycode 用），并立即持久化。
     * 数据为不可变结构，复制后与源会话互不影响；idCounter 取源会话继续递增。
     * 画布解离后应用到"当前画布"分区，并落入当前画布的持久化键。
     */
    fun applyExternalSnapshot(s: JsWorkspaceSnapshot) {
        applySnapshot(s)
        _lastError.value = null
        requestSave(0L)
    }

    fun getEntry(id: String): JsWorkspaceEntry? = _entries.value.firstOrNull { it.id == id }

    fun createFile(parentId: String?) {
        createFileNamed(parentId, "")
    }

    /** 生成默认文件名：Mew_File_1, Mew_File_2 ...（无后缀，编号从小往大填补空缺） */
    private fun generateDefaultFileName(parentId: String?): String {
        val used = _entries.value
            .filter { it.parentId == parentId && !it.isFolder }
            .mapNotNull {
                Regex("""Mew_File_(\d+)""").matchEntire(it.name)?.groupValues?.get(1)?.toIntOrNull()
            }
            .toSet()
        var n = 1
        while (n in used) n++
        return "Mew_File_$n"
    }

    fun createFileNamed(parentId: String?, name: String) {
        if (!_isLoaded.value) return
        val finalName = if (name.isNotBlank()) name.trim() else generateDefaultFileName(parentId)
        idCounter += 1
        val newId = "e_$idCounter"
        val siblings = _entries.value.filter { it.parentId == parentId }.map { it.name }.toSet()
        var candidate = finalName
        var seq = 1
        while (candidate in siblings) {
            seq++
            candidate = finalName + "_" + seq
        }
        _entries.value = _entries.value + JsWorkspaceEntry(newId, candidate, false, parentId)
        _fileContents.value = _fileContents.value + (newId to "")
        if (newId !in _openedFileIds.value) {
            _openedFileIds.value = _openedFileIds.value + newId
        }
        _selectedFileId.value = newId
        requestSave(0L)
    }

    /** 生成默认文件夹名：Mew_Folder_1, Mew_Folder_2 ...（无后缀，编号从小往大填补空缺） */
    private fun generateDefaultFolderName(parentId: String?): String {
        val used = _entries.value
            .filter { it.parentId == parentId && it.isFolder }
            .mapNotNull {
                Regex("""Mew_Folder_(\d+)""").matchEntire(it.name)?.groupValues?.get(1)?.toIntOrNull()
            }
            .toSet()
        var n = 1
        while (n in used) n++
        return "Mew_Folder_$n"
    }

    fun createFolder(parentId: String?, requestedName: String) {
        if (!_isLoaded.value) return
        idCounter += 1
        val newId = "f_$idCounter"
        val siblings = _entries.value.filter { it.parentId == parentId }.map { it.name }.toSet()
        val cleaned = requestedName.trim().ifBlank { generateDefaultFolderName(parentId) }
        val name = uniqueName(cleaned, siblings)
        _entries.value = _entries.value + JsWorkspaceEntry(newId, name, true, parentId)
        requestSave(0L)
    }

    fun openFile(id: String) {
        val e = getEntry(id) ?: return
        if (e.isFolder) return
        if (id !in _openedFileIds.value) {
            _openedFileIds.value = _openedFileIds.value + id
        }
        _selectedFileId.value = id
        requestSave(0L)
    }

    fun closeTab(id: String) {
        _openedFileIds.value = _openedFileIds.value - id
        if (_selectedFileId.value == id) {
            _selectedFileId.value = _openedFileIds.value.firstOrNull()
        }
        requestSave(0L)
    }

    fun updateContent(id: String, text: String) {
        val e = getEntry(id) ?: return
        if (e.isFolder) return
        _fileContents.value = _fileContents.value + (id to text)
        requestSave(220L)
    }

    fun renameEntry(id: String, newName: String) {
        val e = getEntry(id) ?: return
        val siblings = _entries.value.filter { it.parentId == e.parentId && it.id != id }.map { it.name }.toSet()
        val trimmed = newName.trim().ifBlank { e.name }
        // FIX: 原代码 `if (!e.isFolder && !trimmed.endsWith(".js")) "$trimmed.js" else trimmed`
        // 会对所有文件强制追加 .js 后缀，导致 Mew_File_1 被改写成 Mew_File_1.js。
        // 现在按用户输入原样命名（仅去重），不再强制补后缀。
        val unique = uniqueName(trimmed, siblings)
        _entries.value = _entries.value.map { if (it.id == id) it.copy(name = unique) else it }
        requestSave(0L)
    }

    fun deleteEntry(id: String) {
        val toRemove = mutableSetOf<String>()
        collectDescendants(id, toRemove)
        toRemove.add(id)
        _entries.value = _entries.value.filter { it.id !in toRemove }
        _fileContents.value = _fileContents.value - toRemove
        _openedFileIds.value = _openedFileIds.value - toRemove
        if (_selectedFileId.value in toRemove) {
            _selectedFileId.value = _openedFileIds.value.firstOrNull()
        }
        requestSave(0L)
    }

    private fun collectDescendants(parentId: String, out: MutableSet<String>) {
        _entries.value.filter { it.parentId == parentId }.forEach {
            out.add(it.id)
            if (it.isFolder) collectDescendants(it.id, out)
        }
    }

    private fun uniqueName(base: String, siblings: Set<String>): String {
        if (base !in siblings) return base
        var i = 2
        while (true) {
            val candidate = "${base}_$i"
            if (candidate !in siblings) return candidate
            i++
        }
    }

    fun flushNow() {
        saveJob?.cancel()
        val s = storage ?: return
        val id = _activeConversationId.value ?: return
        // 分区切换中途（新分区未载完）内存里还是旧分区的数据，此刻落盘会把旧数据
        // 写进新分区的键（串区污染）。旧分区在切换时已由 save-previous 落盘，跳过不丢数据。
        if (!_isLoaded.value) return
        val key = activeStorageKey(id)
        val snap = snapshot()
        val blocks = SketchBlockStore.toSnapshot()
        saveJob = scope.launch {
            try {
                s.saveWorkspace(key, snap)
                s.saveSketchBlocks(id, blocks)   // 退后台前竖块结构一并落盘
                _lastError.value = null
            } catch (t: Throwable) {
                _lastError.value = t.message ?: "Save failed"
            }
        }
    }

    /**
     * 画板竖块结构变更的落盘触发（加号产出竖块 / 起名横线输入）：竖块是纯 UI 态、
     * 不经过工作区变更，故由 App71 画板在每次改动后显式调这里挂上防抖落盘；
     * 走 requestSave 同一漏斗 = 竖块存档与工作区同一时机写入，flushNow/onPause 一并覆盖。
     */
    fun notifySketchBlocksChanged() {
        requestSave(220L)
    }

    /**
     * 唯一写漏斗：所有变更操作（创建/重命名/删除/编辑/开关标签…）都收口到这里。
     * 画布解离点：落盘键 = storageKey(当前会话, activeCanvas)——在 launch 前捕获，
     * 防抖窗口内即使身份被切换（switchCanvas 会先 cancel 本 job），也不会写错分区。
     */
    private fun requestSave(delayMs: Long) {
        val s = storage ?: return
        val id = _activeConversationId.value ?: return
        val key = activeStorageKey(id)
        val snap = snapshot()
        // 竖块结构是全局存档（不挂会话）：与工作区同一漏斗、同一时机写入
        // （saveSketchBlocks 的 conversationId 参数仅接口兼容，实现不区分）。launch 前捕获当前态。
        val blocks = SketchBlockStore.toSnapshot()
        saveJob?.cancel()
        saveJob = scope.launch {
            try {
                if (delayMs > 0L) delay(delayMs)
                s.saveWorkspace(key, snap)
                s.saveSketchBlocks(id, blocks)
                _lastError.value = null
            } catch (t: Throwable) {
                _lastError.value = t.message ?: "Save failed"
            }
        }
    }

    private fun snapshot() = JsWorkspaceSnapshot(
        entries = _entries.value,
        openedFileIds = _openedFileIds.value,
        selectedFileId = _selectedFileId.value,
        fileContents = _fileContents.value,
        idCounter = idCounter
    )
}
