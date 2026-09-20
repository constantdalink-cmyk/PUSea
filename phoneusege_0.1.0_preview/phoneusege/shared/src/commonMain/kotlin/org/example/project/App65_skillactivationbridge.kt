package org.example.project

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ==============================================================================
// 技能激活桥 (SkillActivationBridge)
// ------------------------------------------------------------------------------
// 打通【管理画板的资源卡片 (SkillCardItemData)】与【AI 注入管道 (SkillStore/AiSkill)】：
//   1. 按卡片解析 SKILL.md 正文地址（详情里的 URL 优先，其次 GlobalSkillPool 同名条目）；
//   2. 真实联网抓取 SKILL.md 正文（复用图片爬虫同款 HTTP 模式）；
//   3. 解析 frontmatter（name/description）与正文指令；
//   4. 生成 AiSkill 并写入 SkillStore（systemPrompt = 正文），由现成注入管道
//      buildActiveSkillsPromptSnippet → createSkillsSystemTurn → buildSkillEnhancedContext
//      送进 AI 对话上下文。
// 激活制（而非下载即注入）：用户在右侧详情画布点"注入 AI"才生效，自主控制 token 消耗。
// ==============================================================================

/** frontmatter 解析结果 */
data class ParsedSkillMarkdown(
    val name: String,
    val description: String,
    val body: String
)

object SkillActivationBridge {

    // --------------------------------------------------------------------------
    // 1. 名称归一化：与 SkillStore.createCustomSkill 的规则保持一致，
    //    保证激活/停用/查重时能稳定命中同一条 AiSkill
    // --------------------------------------------------------------------------
    fun normalizeName(raw: String): String =
        raw.trim().lowercase().replace(Regex("[^a-z0-9_]"), "_")

    // --------------------------------------------------------------------------
    // 2. 查重与状态
    // --------------------------------------------------------------------------
    fun findInjectedSkill(card: SkillCardItemData): AiSkill? {
        val normalized = normalizeName(card.name)
        return SkillStore.skills.value.firstOrNull {
            (normalized.isNotBlank() && it.name == normalized) ||
                    it.displayName.equals(card.name, ignoreCase = true)
        }
    }

    fun isActivated(card: SkillCardItemData): Boolean =
        findInjectedSkill(card)?.isEnabled == true

    // --------------------------------------------------------------------------
    // 3. SKILL.md 地址解析
    //    优先：卡片详情里自带的资源网址（URL 下载的卡片 detail 就是原始链接），
    //          把 GitHub 页面链接换算成 raw.githubusercontent.com 正文地址；
    //    其次：GlobalSkillPool 同名条目，用 author(owner/repo) + title 拼 raw 地址。
    // --------------------------------------------------------------------------
    /**
     * 抓取该卡片的 SKILL.md 真实正文（两级解析）：
     *   1. 详情里的资源链接（URL 下载的卡片）→ raw 直链；
     *   2. 技能池同名条目 → 先顶层目录，404 再拉仓库文件树定位真实路径
     *      （anthropics/skills 等大型技能仓是嵌套分类目录，如 document-skills/docx/SKILL.md）
     */
    suspend fun fetchSkillMdContent(card: SkillCardItemData): String? {
        val urlInDetail = Regex("https?://[^\\s)\\]\"'<>]+").find(card.detail)?.value
        if (urlInDetail != null) {
            toRawSkillMdUrl(urlInDetail)?.let { direct ->
                fetchSkillMarkdown(direct)?.let { return it }
            }
        }
        val pool = GlobalSkillPool.firstOrNull { it.title.equals(card.name, ignoreCase = true) }
        if (pool != null && pool.author.isNotBlank()) {
            return fetchPoolSkillMarkdown(pool.author, pool.title)
        }
        return null
    }

    /** 顶层目录优先；失败则递归拉仓库文件树定位嵌套的 SKILL.md */
    private suspend fun fetchPoolSkillMarkdown(author: String, title: String): String? {
        fetchSkillMarkdown("https://raw.githubusercontent.com/$author/HEAD/$title/SKILL.md")
            ?.let { return it }
        val tree = fetchText("https://api.github.com/repos/$author/git/trees/HEAD?recursive=1")
            ?: return null
        val escaped = Regex.escape(title)
        val path = Regex("\"path\"\\s*:\\s*\"([^\"]*${escaped}/SKILL\\.md)\"")
            .find(tree)?.groupValues?.get(1) ?: return null
        return fetchSkillMarkdown("https://raw.githubusercontent.com/$author/HEAD/$path")
    }

    /**
     * 把各种形态的技能资源链接换算为 SKILL.md 正文的 raw 直链：
     *   - raw.githubusercontent.com/...         → 原样（缺文件名时补 SKILL.md）
     *   - github.com/{o}/{r}/blob/{branch}/{p}  → raw.githubusercontent.com/{o}/{r}/{branch}/{p}
     *   - github.com/{o}/{r}/tree/{branch}/{p}  → 目录，末尾补 /SKILL.md
     *   - github.com/{o}/{r}(/{其他路径})        → 默认分支 HEAD + 路径（缺则补 SKILL.md）
     */
    internal fun toRawSkillMdUrl(url: String): String? {
        val clean = url.trim().removeSuffix("/").substringBefore("?").substringBefore("#")
        return when {
            clean.contains("raw.githubusercontent.com/") -> {
                if (clean.endsWith(".md", ignoreCase = true)) clean else "$clean/SKILL.md"
            }
            clean.contains("github.com/") -> {
                val parts = clean.substringAfter("github.com/").split("/").filter { it.isNotBlank() }
                when {
                    parts.size >= 5 && parts[2].equals("blob", true) ->
                        "https://raw.githubusercontent.com/${parts[0]}/${parts[1]}/${parts.subList(3, parts.size).joinToString("/")}"
                    parts.size >= 4 && parts[2].equals("tree", true) ->
                        "https://raw.githubusercontent.com/${parts[0]}/${parts[1]}/${parts.subList(3, parts.size).joinToString("/")}/SKILL.md"
                    parts.size in 2..3 && parts.size == 2 ->
                        "https://raw.githubusercontent.com/${parts[0]}/${parts[1]}/HEAD/SKILL.md"
                    parts.size >= 3 ->
                        "https://raw.githubusercontent.com/${parts[0]}/${parts[1]}/HEAD/${parts.subList(2, parts.size).joinToString("/")}"
                    else -> null
                }
            }
            else -> null
        }
    }

    // --------------------------------------------------------------------------
    // 4. 正文抓取（真实联网，与图片爬虫同款 HttpURLConnection 模式）
    // --------------------------------------------------------------------------
    suspend fun fetchSkillMarkdown(url: String): String? =
        fetchText(url, "text/markdown,text/plain,*/*;q=0.8")

    /** 通用文本抓取（GitHub API 文件树等场景用默认 Accept，避免 406） */
    suspend fun fetchText(url: String, accept: String = "*/*"): String? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36"
            )
            conn.setRequestProperty("Accept", accept)
            if (conn.responseCode in 200..299) {
                conn.inputStream.use { it.readBytes().decodeToString() }
            } else null
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    // --------------------------------------------------------------------------
    // 5. frontmatter 解析：切出 --- 之间的 name/description，其余为正文指令
    // --------------------------------------------------------------------------
    fun parseSkillMarkdown(raw: String): ParsedSkillMarkdown {
        var name = ""
        var description = ""
        var body = raw

        if (raw.trimStart().startsWith("---")) {
            val start = raw.indexOf("---")
            val end = raw.indexOf("\n---", start + 3)
            if (end > start) {
                val frontMatter = raw.substring(start + 3, end)
                body = raw.substring(end + 4).trimStart('\r', '\n')
                frontMatter.lines().forEach { line ->
                    val colon = line.indexOf(':')
                    if (colon > 0) {
                        val key = line.substring(0, colon).trim().lowercase()
                        val value = line.substring(colon + 1).trim()
                            .removeSurrounding("\"").removeSurrounding("'")
                        when (key) {
                            "name" -> name = value
                            "description" -> description = value
                        }
                    }
                }
            }
        }
        return ParsedSkillMarkdown(name, description, body)
    }

    // --------------------------------------------------------------------------
    // 6. 激活 / 停用
    //    激活：已存在 → 直接开启开关；不存在 → 抓正文 → 解析 → addSkill 入库
    //    停用：只关开关不删除，再次激活免重抓
    //    返回值：错误信息；null 表示成功
    // --------------------------------------------------------------------------
    suspend fun activate(card: SkillCardItemData): String? {
        if (card.name.isBlank()) return "卡片没有名称，无法注入"

        findInjectedSkill(card)?.let { existing ->
            SkillStore.setSkillEnabled(existing.id, true)
            return null
        }

        // 确保有 SKILL.md 正文：customMd（下载预抓/用户手改）优先，为空才联网抓取并回写卡片
        val freshCard = DownloadedResourceStore.downloadedCards.firstOrNull { it.id == card.id } ?: card
        val md = ensureSkillMd(freshCard)
            ?: return "SKILL.md 抓取失败：无来源链接或网络不可用（可在 SKILL.md 页手写）"

        addInjectedSkill(freshCard, md, freshCard.detail)
        return null
    }

    /**
     * 确保卡片拥有 SKILL.md 正文：customMd 已有则直接返回；否则联网抓取真实正文并回写卡片。
     * 三个调用点：下载时预取（addDownloadedCard）、右画布打开兜底、注入前校验。
     * 幂等：并发调用时先完成的回写会让后到的直接命中缓存，不会重复抓取。
     * 返回正文；失败返回 null。
     */
    suspend fun ensureSkillMd(card: SkillCardItemData): String? {
        if (card.id.startsWith("res_blank_")) return null
        val fresh = DownloadedResourceStore.downloadedCards.firstOrNull { it.id == card.id } ?: card
        if (fresh.customMd.isNotBlank()) return fresh.customMd
        val raw = fetchSkillMdContent(fresh) ?: return null
        val body = parseSkillMarkdown(raw).body.ifBlank { raw }
        DownloadedResourceStore.updateCardMd(fresh.id, body)
        return body
    }

    /** 资源卡被删除时同步移除已注入的 AI 技能：被删资源不再进 AI 上下文 */
    fun removeInjected(card: SkillCardItemData) {
        findInjectedSkill(card)?.let { SkillStore.deleteSkill(it.id) }
    }

    private fun addInjectedSkill(card: SkillCardItemData, systemPrompt: String, description: String) {
        val skill = AiSkill(
            id = "skill_injected_${System.currentTimeMillis()}",
            name = normalizeName(card.name).ifBlank { "injected_skill_${System.currentTimeMillis()}" },
            displayName = card.name,
            description = description,
            systemPrompt = systemPrompt,
            isEnabled = true,
            category = "Injected",
            tags = listOf("skill-bridge"),
            isBuiltIn = false
        )
        SkillStore.addSkill(skill)
    }

    fun deactivate(card: SkillCardItemData) {
        findInjectedSkill(card)?.let { SkillStore.setSkillEnabled(it.id, false) }
    }
}
