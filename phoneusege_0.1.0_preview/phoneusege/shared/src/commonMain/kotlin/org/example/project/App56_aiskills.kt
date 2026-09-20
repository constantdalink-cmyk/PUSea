package org.example.project

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// ==============================================================================
// 资源卡持久化 DTO（Color 为 ULong 值类，以 Long 形式存入 JSON）
// ==============================================================================

@Serializable
private data class StoredSkillCard(
    val id: String,
    val name: String,
    val detail: String,
    val colorValue: Long,
    val imageUrl: String = "",
    val iconKey: String = "",
    val customMd: String = ""
) {
    fun toCard() = SkillCardItemData(
        id = id,
        name = name,
        detail = detail,
        color = Color(colorValue.toULong()),
        imageUrl = imageUrl,
        iconKey = iconKey,
        customMd = customMd
    )
}

private fun SkillCardItemData.toStored() = StoredSkillCard(
    id = id,
    name = name,
    detail = detail,
    colorValue = color.value.toLong(),
    imageUrl = imageUrl,
    iconKey = iconKey,
    customMd = customMd
)

// ==============================================================================
// 真实获得资源单例仓储 (DownloadedResourceStore)
// ==============================================================================

object DownloadedResourceStore {
    private const val STORAGE_KEY = "downloaded_skill_resources_v1"
    private val json = Json { ignoreUnknownKeys = true }
    private var storage: StorageProvider = MemoryStorage
    private var initialized = false
    /** 后台预取作用域：下载后立即抓 SKILL.md 正文，右画布无需点"注入 AI"就能看到真实内容 */
    private val storeScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // 真实资源列表：无任何示例种子方块，仅存放经大嘴吞噬实际下载得到的资源卡片
    val downloadedCards = mutableStateListOf<SkillCardItemData>()

    /** 启动时注入持久化存储（MainActivity），内部同步回读存档——重启后下载的资源仍在 */
    fun initialize(provider: StorageProvider) {
        storage = provider
        if (initialized) return
        initialized = true
        loadCards()
    }

    private fun loadCards() {
        val raw = storage.load(STORAGE_KEY)
        if (raw.isNullOrBlank()) return
        runCatching {
            val stored = json.decodeFromString<List<StoredSkillCard>>(raw)
            downloadedCards.clear()
            downloadedCards.addAll(stored.map { it.toCard() })
        }
    }

    private fun persistCards() {
        runCatching {
            storage.save(STORAGE_KEY, json.encodeToString(downloadedCards.map { it.toStored() }))
        }
    }

    /**
     * 解析卡片对应的在线图片 URL (优先匹配 GlobalSkillPool，其次提取 GitHub Avatar，支持全网 Skills 资源)
     */
    fun resolveImageUrlForCard(cardName: String, cardDetail: String): String {
        val matched = GlobalSkillPool.firstOrNull { it.title.equals(cardName, ignoreCase = true) }
        if (matched != null && matched.imageUrl.isNotBlank()) {
            return matched.imageUrl
        }
        if (cardDetail.contains("github.com/")) {
            val path = cardDetail.substringAfter("github.com/").substringBefore("/").substringBefore(" ").trim()
            if (path.isNotBlank()) {
                return "https://github.com/$path.png"
            }
        }
        // 不再兜底到任何第三方品牌标志：解析不到封面时返回空串——
        // 列表行/右画布显示空预留框，信息弹窗显示自带的手绘占位图
        return ""
    }

    /**
     * 当大嘴闭上吞噬技能时，触发下载并存入真实获得资源池
     * 允许重复下载：不做去重，同一 skill 资源可多次添加
     */
    fun addDownloadedCard(card: SkillCardItemData) {
        downloadedCards.add(0, card)
        persistCards()
        // 下载即预取 SKILL.md 正文并回写 customMd：右画布打开就能看到真实内容，不必先注入
        storeScope.launch {
            SkillActivationBridge.ensureSkillMd(card)
        }
    }

    /** 删除资源卡（长按 5 秒删除的回调入口），同步落盘；同时移除已注入的 AI 技能，被删资源不再注入 */
    fun removeCard(id: String) {
        val removed = downloadedCards.firstOrNull { it.id == id }
        if (downloadedCards.removeAll { it.id == id }) {
            persistCards()
            removed?.let { SkillActivationBridge.removeInjected(it) }
        }
    }

    /**
     * 一键清空全部已下载资源卡（"Skills Data 删光"的真实入口）：
     * 只落盘一次，并经 SkillActivationBridge 移除所有已注入的 AI 技能——被删资源重启后不再复活
     */
    fun clearAllCards() {
        val removed = downloadedCards.toList()
        if (removed.isEmpty()) return
        downloadedCards.clear()
        persistCards()
        removed.forEach { SkillActivationBridge.removeInjected(it) }
    }

    /**
     * 右侧详情画布就地编辑回写：更新指定资源的名称与正文内容。
     * 用户手动新建的空白卡（isBlankUserCard）允许名称为空，不做任何强制回填。
     */
    fun updateCardContent(id: String, newName: String, newDetail: String) {
        val index = downloadedCards.indexOfFirst { it.id == id }
        if (index == -1) return
        val old = downloadedCards[index]
        val isBlankUserCard = old.id.startsWith("res_blank_")
        val safeName = if (isBlankUserCard) newName else newName.trim().ifBlank { old.name }
        downloadedCards[index] = old.copy(
            name = safeName,
            detail = newDetail,
            // 空白自建卡不自动解析封面；其余卡片在原本无封面时补一次
            imageUrl = if (isBlankUserCard) old.imageUrl
            else old.imageUrl.ifBlank { resolveImageUrlForCard(safeName, newDetail) }
        )
        persistCards()
    }

    /**
     * 更新指定资源的封面图片（文件管理器选图后回写本地 uri / 路径）
     */
    fun updateCardImage(id: String, newImageUrl: String) {
        val index = downloadedCards.indexOfFirst { it.id == id }
        if (index == -1) return
        downloadedCards[index] = downloadedCards[index].copy(imageUrl = newImageUrl)
        persistCards()
    }

    /** 右画布 SKILL.md 编辑回写：用户手改的正文存入 customMd，同步落盘 */
    fun updateCardMd(id: String, newMd: String) {
        val index = downloadedCards.indexOfFirst { it.id == id }
        if (index == -1) return
        downloadedCards[index] = downloadedCards[index].copy(customMd = newMd)
        persistCards()
    }

    /**
     * 用户手动新建一个完全空白的长条框：无图片、无名称、无详情、无颜色（透明）
     * 追加到列表最末尾（最后一个长条框下面）
     */
    fun addEmptyCard(): SkillCardItemData {
        // 已删除"连续新建空白卡颜色轮转"游标机制：所有新建长条框一律默认天蓝 (0xFF4D96FF)，
        // 与卡片行末端"那一抹颜色"的默认色完全一致；仍不能给 Transparent
        // （会让末端画不出来，且点击变色时 indexOf 会错位）
        val blank = SkillCardItemData(
            id = "res_blank_${System.nanoTime()}",
            name = "",
            detail = "",
            color = Color(0xFF4D96FF),
            imageUrl = ""
        )
        downloadedCards.add(blank)
        persistCards()
        return blank
    }
}

// ==============================================================================
// 带子列持久化 (SkillRibbonStore)
// 顶部滑下画布里的 Ribbon 列：名称、阶段(绿标)、选中集合、宽度全部跨重启保留
// ==============================================================================

@Serializable
private data class StoredRibbonColumn(
    val id: String,
    val colorValue: Long,
    val name: String = "+",
    val stage: Int = 0,
    val selectedItemIds: Set<String> = emptySet(),
    val isInitial: Boolean = false
) {
    // 宽度不入库：读档用默认 78，打开画布时由均分算法按屏宽实时重排
    fun toColumn() = RibbonColumnData(
        id = id,
        color = Color(colorValue.toULong()),
        name = name,
        stage = stage,
        selectedItemIds = selectedItemIds,
        isInitial = isInitial
    )
}

private fun RibbonColumnData.toStored() = StoredRibbonColumn(
    id = id,
    colorValue = color.value.toLong(),
    name = name,
    stage = stage,
    selectedItemIds = selectedItemIds,
    isInitial = isInitial
)

object SkillRibbonStore {
    private const val STORAGE_KEY = "skill_ribbon_columns_v1"
    private val json = Json { ignoreUnknownKeys = true }
    private var storage: StorageProvider = MemoryStorage
    private var initialized = false

    fun initialize(provider: StorageProvider) {
        storage = provider
        initialized = true
    }

    /** 读取持久化的带子列；无存档/为空返回 null（调用方用默认 5 列兜底） */
    fun loadColumns(): List<RibbonColumnData>? {
        if (!initialized) return null
        val raw = storage.load(STORAGE_KEY) ?: return null
        return runCatching {
            json.decodeFromString<List<StoredRibbonColumn>>(raw).map { it.toColumn() }
        }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    fun saveColumns(columns: List<RibbonColumnData>) {
        if (!initialized) return
        runCatching {
            storage.save(STORAGE_KEY, json.encodeToString(columns.map { it.toStored() }))
        }
    }
}

// ==============================================================================
// AI Skill 数据模型与定义
// ==============================================================================

@Serializable
data class SkillParameter(
    val name: String,
    val type: String = "string",
    val description: String = "",
    val required: Boolean = false
)

@Serializable
data class AiSkill(
    val id: String,
    val name: String,
    val displayName: String,
    val description: String,
    val systemPrompt: String,
    val isEnabled: Boolean = true,
    val category: String = "General",
    val tags: List<String> = emptyList(),
    val parameters: List<SkillParameter> = emptyList(),
    val isBuiltIn: Boolean = false
)

// ==============================================================================
// 技能仓库与持久化管理 (SkillStore)
// ==============================================================================

object SkillStore {
    private const val STORAGE_KEY = "ai_skills_config_v1"
    private val json = Json { ignoreUnknownKeys = true }
    private var storage: StorageProvider = MemoryStorage
    private var initialized = false

    // 无任何内置示例技能：仅存放用户手动创建/导入的真实技能
    private val defaultSkills = emptyList<AiSkill>()

    private val _skills = MutableStateFlow<List<AiSkill>>(emptyList())
    val skills: StateFlow<List<AiSkill>> = _skills.asStateFlow()

    fun initialize(provider: StorageProvider) {
        storage = provider
        if (initialized) return
        initialized = true
        loadSkills()
    }

    private fun loadSkills() {
        val storedRaw = storage.load(STORAGE_KEY)
        if (!storedRaw.isNullOrBlank()) {
            try {
                val loaded = json.decodeFromString<List<AiSkill>>(storedRaw)
                val builtInMap = defaultSkills.associateBy { it.id }
                // 清除已不再随包内置的旧种子技能，保留用户自定义技能与当前内置技能的开关状态
                val merged = loaded
                    .filter { saved -> !saved.isBuiltIn || builtInMap.containsKey(saved.id) }
                    .map { saved ->
                        builtInMap[saved.id]?.copy(isEnabled = saved.isEnabled) ?: saved
                    }
                val existingIds = merged.map { it.id }.toSet()
                val missingBuiltIns = defaultSkills.filter { it.id !in existingIds }
                _skills.value = merged + missingBuiltIns
                return
            } catch (_: Exception) { }
        }
        _skills.value = defaultSkills
        persistSkills()
    }

    private fun persistSkills() {
        try {
            storage.save(STORAGE_KEY, json.encodeToString(_skills.value))
        } catch (_: Exception) { }
    }

    fun getActiveSkills(): List<AiSkill> =
        _skills.value.filter { it.isEnabled }

    fun toggleSkill(id: String) {
        _skills.value = _skills.value.map {
            if (it.id == id) it.copy(isEnabled = !it.isEnabled) else it
        }
        persistSkills()
    }

    fun setSkillEnabled(id: String, enabled: Boolean) {
        _skills.value = _skills.value.map {
            if (it.id == id) it.copy(isEnabled = enabled) else it
        }
        persistSkills()
    }

    fun addSkill(skill: AiSkill) {
        _skills.value = _skills.value + skill
        persistSkills()
    }

    fun updateSkill(skill: AiSkill) {
        _skills.value = _skills.value.map { if (it.id == skill.id) skill else it }
        persistSkills()
    }

    fun deleteSkill(id: String) {
        _skills.value = _skills.value.filterNot { it.id == id && !it.isBuiltIn }
        persistSkills()
    }

    /** 清空全部用户自建技能（内置技能不动），同步落盘；预留给 "Tools Data" 删除入口接线 */
    fun deleteAllCustomSkills() {
        if (_skills.value.none { !it.isBuiltIn }) return
        _skills.value = _skills.value.filter { it.isBuiltIn }
        persistSkills()
    }

    fun createCustomSkill(
        displayName: String,
        name: String,
        description: String,
        systemPrompt: String,
        category: String = "Custom",
        tags: List<String> = emptyList()
    ): AiSkill {
        val normalizedName = name.trim().lowercase().replace(Regex("[^a-z0-9_]"), "_").ifBlank { "custom_skill_${System.currentTimeMillis()}" }
        val newSkill = AiSkill(
            id = "skill_custom_${System.currentTimeMillis()}",
            name = normalizedName,
            displayName = displayName.ifBlank { normalizedName },
            description = description,
            systemPrompt = systemPrompt,
            isEnabled = true,
            category = category,
            tags = tags,
            isBuiltIn = false
        )
        addSkill(newSkill)
        return newSkill
    }

    // ==============================================================================
    // 注入 System Prompt 与 上下文构建
    // ==============================================================================

    /**
     * 生成当前所有已激活技能的系统提示词片段，用于拼接到 AI 的 System 角色中
     */
    fun buildActiveSkillsPromptSnippet(): String {
        val active = getActiveSkills()
        if (active.isEmpty()) return ""

        return buildString {
            appendLine("=== ACTIVATED AI SKILLS ===")
            appendLine("You have the following specialized skills activated. Adhere to their instructions whenever applicable:")
            active.forEachIndexed { index, skill ->
                appendLine("${index + 1}. [${skill.displayName} (${skill.name})]")
                appendLine("   Description: ${skill.description}")
                appendLine("   Instructions:")
                appendLine(skill.systemPrompt.prependIndent("     "))
                appendLine()
            }
            appendLine("===========================")
        }
    }
}

// ==============================================================================
// 技能独立 System 词条生成与注入工具函数 (用户编辑与技能提示词完全独立)
// ==============================================================================

/**
 * 创建专属于 AI Skills 的独立 System 词条 (不侵入、不污染用户自编辑的 System 词条)
 */
fun createSkillsSystemTurn(): ChatTurn? {
    val snippet = SkillStore.buildActiveSkillsPromptSnippet()
    return if (snippet.isNotBlank()) {
        ChatTurn(role = "system", content = snippet)
    } else {
        null
    }
}

/**
 * 兼容性合并助手 (如果特定场景需要文本合并)
 */
fun injectSkillsIntoSystemPrompt(baseSystemPrompt: String): String {
    val skillsSnippet = SkillStore.buildActiveSkillsPromptSnippet()
    if (skillsSnippet.isBlank()) return baseSystemPrompt
    return if (baseSystemPrompt.isBlank()) {
        skillsSnippet
    } else {
        "$baseSystemPrompt\n\n$skillsSnippet"
    }
}

/**
 * 构建带有独立 Skills System 词条的请求上下文：
 * 保证【用户的内容在第一位】：
 * 1) 用户自编辑的 System 词条处于绝对第一位 (Index 0)；
 * 2) AI Skills 独立 System 词条紧随其后作为扩展层；
 * 3) 后续跟随用户与 AI 的历史对话内容。
 */
fun buildSkillEnhancedContext(baseHistory: List<ChatTurn>): List<ChatTurn> {
    val userSystem = baseHistory.firstOrNull { it.role == "system" }
    val conversationTurns = baseHistory.filterNot { it.role == "system" }
    val skillsTurn = createSkillsSystemTurn()

    return buildList {
        // 第一位：用户自定义 System 词条（拥有最高优先级）
        if (userSystem != null) {
            add(userSystem)
        }
        // 第二位：AI Skills 独立扩展 System 词条
        if (skillsTurn != null) {
            add(skillsTurn)
        }
        // 后续：用户与 AI 对话历史（包含用户最新消息）
        addAll(conversationTurns)
    }
}
