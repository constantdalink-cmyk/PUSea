package org.example.project

// ==============================================================================
// 画布文件 1/10 之分割 2/3：subcanvascomponentplazastore.kt —— 映射 + 解析器 + 状态仓库
//
// 原 subcanvascomponentplaza.kt 按代码量均分 3 个文件，本文件为第 2 份：
// 1. 自动补图与格式映射引擎（PlazaImageMapper：品牌库 / GitHub owner / icon.horse / 回退链）；
// 2. JSON 爬虫解析器（PlazaRegistryParser：多键名兼容解析，空结果容错）；
// 3. 状态仓库 PlazaStore：首屏 pageSize=36 / 触底 pageSize=15 节流防爆爬虫、
//    【防连续方块】切片算法（buildDisplayCycles）、工具安装注册；
// 4. 底部抓取指示区（PlazaCrawlerFooter：旋转弧常亮，贴底不闪灭）。
//
// 跨文件可见性：PlazaCrawlerFooter 由 private 提升为 internal，
// 供分割 3/3 的画布主界面同模块调用。
// 依赖：App44_mcptoolimport.kt（HttpRequester，平台注入网络能力）
//       App41_mcptool.kt（McpToolStore / McpToolParameter / McpToolResult）
//       App43_godo.kt（UserToolStore.registerMcpTool）
//       App42_androidhttprequester.kt（AndroidHttpRequester）
//       同包：分割 1/3 的 PlazaToolItem / INITIAL_BOOTSTRAP_TOOLS。
// ==============================================================================

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URL

// ==============================================================================
// 自动补图与格式映射引擎
// ==============================================================================

object PlazaImageMapper {
    fun resolveIcon(
        rawIcon: String?,
        homepage: String?,
        repo: String?,
        name: String
    ): String? {
        if (!rawIcon.isNullOrBlank() && (rawIcon.startsWith("http://") || rawIcon.startsWith("https://"))) {
            if (!rawIcon.endsWith(".svg", ignoreCase = true)) {
                return rawIcon
            }
        }

        val lowerName = name.lowercase().trim()
        when {
            lowerName.contains("github") -> return "https://github.githubassets.com/favicons/favicon.png"
            lowerName.contains("brave") -> return "https://brave.com/static-assets/images/brave-logo-sans-text.png"
            lowerName.contains("postgres") -> return "https://www.postgresql.org/media/img/about/press/elephant.png"
            lowerName.contains("slack") -> return "https://a.slack-edge.com/80588/marketing/img/meta/favicon-32.png"
            lowerName.contains("notion") -> return "https://www.notion.so/images/favicon.ico"
            lowerName.contains("puppeteer") -> return "https://user-images.githubusercontent.com/10379601/29446482-04f7036a-841f-11e7-9872-91d1fc2ea683.png"
            lowerName.contains("docker") -> return "https://www.docker.com/wp-content/uploads/2022/03/Moby-logo.png"
            lowerName.contains("python") -> return "https://www.python.org/static/favicon.ico"
            lowerName.contains("stripe") -> return "https://stripe.com/favicon.ico"
            lowerName.contains("cloudflare") -> return "https://www.cloudflare.com/favicon.ico"
            lowerName.contains("supabase") -> return "https://supabase.com/favicon/favicon-32x32.png"
            lowerName.contains("redis") -> return "https://redis.io/favicon.ico"
            lowerName.contains("openai") -> return "https://openai.com/favicon.ico"
            lowerName.contains("google") -> return "https://www.google.com/favicon.ico"
            lowerName.contains("sqlite") -> return "https://www.sqlite.org/favicon.ico"
            lowerName.contains("filesystem") || lowerName.contains("file") -> return "https://nodejs.org/static/images/favicons/favicon.png"
            lowerName.contains("memory") -> return "https://modelcontextprotocol.io/favicon.ico"
            lowerName.contains("fetch") || lowerName.contains("curl") -> return "https://curl.se/favicon.ico"
            lowerName.contains("arxiv") -> return "https://arxiv.org/favicon.ico"
            lowerName.contains("bash") || lowerName.contains("terminal") -> return "https://www.gnu.org/graphics/gnu-head-sm.png"
            lowerName.contains("weather") -> return "https://open-meteo.com/favicon.ico"
            lowerName.contains("k8s") || lowerName.contains("kubernetes") -> return "https://kubernetes.io/images/favicon.png"
            lowerName.contains("deepseek") -> return "https://www.deepseek.com/favicon.ico"
            lowerName.contains("linear") -> return "https://linear.app/favicon.ico"
            lowerName.contains("discord") -> return "https://discord.com/assets/favicon.ico"
            lowerName.contains("sentry") -> return "https://sentry.io/_assets/favicon.ico"
            lowerName.contains("gitlab") -> return "https://gitlab.com/assets/favicon.png"
            lowerName.contains("git") -> return "https://git-scm.com/favicon.ico"
        }

        val targetUrl = repo ?: homepage ?: ""
        if (targetUrl.contains("github.com/")) {
            val parts = targetUrl.substringAfter("github.com/").trim('/').split('/')
            if (parts.isNotEmpty() && parts[0].isNotBlank()) {
                val owner = parts[0]
                return "https://github.com/$owner.png"
            }
        }

        if (!homepage.isNullOrBlank() && homepage.startsWith("http")) {
            try {
                val domain = URL(homepage).host
                if (domain.isNotBlank()) {
                    return "https://icon.horse/icon/$domain"
                }
            } catch (_: Exception) {}
        }

        // 尝试从通用域名解析
        resolveDomain(homepage ?: repo, name)?.let { domain ->
            return "https://icon.horse/icon/$domain"
        }

        return null
    }

    /** 失败重试间隔（略大于加载器 errTtl，确保过期后重探） */
    const val ERR_RETRY_MS = 30_800L

    /** 从 homepage、endpoint、installCommand 或工具名推导官方域名，驱动域名级 favicon 兜底服务 */
    fun resolveDomain(homepage: String?, name: String): String? {
        if (!homepage.isNullOrBlank() && homepage.startsWith("http")) {
            try {
                URL(homepage).host.takeIf { it.isNotBlank() }?.let { return it }
            } catch (_: Exception) {}
        }
        val n = name.lowercase()
        return when {
            n.contains("github") -> "github.com"
            n.contains("brave") -> "brave.com"
            n.contains("puppeteer") -> "pptr.dev"
            n.contains("postgres") -> "postgresql.org"
            n.contains("slack") -> "slack.com"
            n.contains("notion") -> "notion.so"
            n.contains("stripe") -> "stripe.com"
            n.contains("cloudflare") -> "cloudflare.com"
            n.contains("supabase") -> "supabase.com"
            n.contains("redis") -> "redis.io"
            n.contains("openai") -> "openai.com"
            n.contains("docker") -> "docker.com"
            n.contains("sqlite") -> "sqlite.org"
            n.contains("filesystem") || n.contains("file") -> "nodejs.org"
            n.contains("memory") -> "modelcontextprotocol.io"
            n.contains("fetch") || n.contains("curl") -> "curl.se"
            n.contains("arxiv") -> "arxiv.org"
            n.contains("bash") || n.contains("terminal") -> "gnu.org"
            n.contains("weather") -> "open-meteo.com"
            n.contains("k8s") || n.contains("kubernetes") -> "kubernetes.io"
            n.contains("deepseek") -> "deepseek.com"
            n.contains("linear") -> "linear.app"
            n.contains("discord") -> "discord.com"
            n.contains("sentry") -> "sentry.io"
            n.contains("gitlab") -> "gitlab.com"
            n.contains("python") -> "python.org"
            n.contains("google") -> "google.com"
            n.contains("git") -> "git-scm.com"
            else -> "github.com"
        }
    }

    /**
     * 【图标回退链】：原始 URL 优先，解析图 -> Google S2 -> unavatar -> DuckDuckGo -> icon.horse 兜底，
     * 配合加载器竞速探测，保证长条框等无图资源在下载与展示时必能获得图标并触发重爬。
     */
    fun buildCandidates(tool: PlazaToolItem): List<String> = buildList {
        tool.iconUrl?.takeIf { it.isNotBlank() }?.let { add(it) }
        val resolved = resolveIcon(null, tool.homepage, tool.endpoint, tool.name)
        if (!resolved.isNullOrBlank() && !contains(resolved)) {
            add(resolved)
        }
        val d = resolveDomain(tool.homepage ?: tool.endpoint, tool.name) ?: "github.com"
        val g = "https://www.google.com/s2/favicons?sz=128&domain=$d"
        val u = "https://unavatar.io/$d?fallback=false"
        val dd = "https://icons.duckduckgo.com/ip3/$d.ico"
        val ih = "https://icon.horse/icon/$d"
        if (!contains(g)) add(g)
        if (!contains(u)) add(u)
        if (!contains(dd)) add(dd)
        if (!contains(ih)) add(ih)
    }
}

// ==============================================================================
// JSON 爬虫解析器
// ==============================================================================

object PlazaRegistryParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String, registryName: String): List<PlazaToolItem> {
        val element = try {
            json.parseToJsonElement(text)
        } catch (_: Exception) {
            return emptyList()
        }

        val arrays = mutableListOf<kotlinx.serialization.json.JsonArray>()
        fun collect(e: kotlinx.serialization.json.JsonElement) {
            when (e) {
                is kotlinx.serialization.json.JsonArray -> arrays.add(e)
                is kotlinx.serialization.json.JsonObject -> {
                    e.forEach { (k, v) ->
                        if (k in setOf("servers", "tools", "data", "results", "items", "mcps", "list")) {
                            if (v is kotlinx.serialization.json.JsonArray) arrays.add(v)
                            else if (v is kotlinx.serialization.json.JsonObject) collect(v)
                        }
                    }
                }
                else -> {}
            }
        }
        collect(element)

        val list = mutableListOf<PlazaToolItem>()
        arrays.forEach { arr ->
            arr.forEach { item ->
                val obj = try { item.jsonObject } catch (_: Exception) { return@forEach }

                fun str(vararg keys: String): String {
                    for (k in keys) {
                        val v = obj[k] ?: continue
                        try { v.jsonPrimitive.contentOrNull?.let { if (it.isNotBlank()) return it } } catch (_: Exception) {}
                    }
                    return ""
                }
                fun strOrNull(vararg keys: String): String? = str(*keys).ifBlank { null }
                fun tags(vararg keys: String): List<String> {
                    for (k in keys) {
                        val v = obj[k] ?: continue
                        try {
                            val tagsList = v.jsonArray.mapNotNull { it.jsonPrimitive.contentOrNull }.filter { it.isNotBlank() }
                            if (tagsList.isNotEmpty()) return tagsList
                        } catch (_: Exception) {}
                    }
                    return emptyList()
                }
                fun num(vararg keys: String): Long {
                    for (k in keys) {
                        val v = obj[k] ?: continue
                        try { v.jsonPrimitive.contentOrNull?.toLongOrNull()?.let { return it } } catch (_: Exception) {}
                    }
                    return 0L
                }

                val name = str("name", "displayName", "title", "slug", "id")
                if (name.isBlank()) return@forEach

                val rawIcon = strOrNull("iconUrl", "icon", "icon_url", "logo", "logoUrl", "image")
                val homepage = strOrNull("homepage", "url", "repository", "source", "homePage")
                val repository = strOrNull("repository", "repo", "sourceUrl")

                val resolvedIcon = PlazaImageMapper.resolveIcon(rawIcon, homepage, repository, name)

                list.add(
                    PlazaToolItem(
                        id = str("id", "serverId", "slug", "qualifiedName").ifBlank { name },
                        name = name,
                        description = str("description", "summary", "info", "blurb"),
                        iconUrl = resolvedIcon,
                        installCommand = strOrNull("installCommand", "install", "command", "install_command"),
                        endpoint = strOrNull("endpoint", "url", "remoteUrl", "serverUrl"),
                        homepage = homepage,
                        sourceRegistry = registryName,
                        useCount = num("useCount", "uses", "installCount", "downloads", "stars"),
                        tags = tags("tags", "categories", "keywords")
                    )
                )
            }
        }
        return list.distinctBy { it.id + it.name }
    }
}

// ==============================================================================
// 状态仓库：【节流防爆 + 首屏大容量爬虫】
// ==============================================================================

data class PlazaDisplayCycle(
    val cycleIndex: Int,
    val layer1BoxTools: List<PlazaToolItem>,
    val layer2to6BarTools: List<PlazaToolItem>
)

/** 真实安装任务数据模型 */
data class PlazaInstallTask(
    val tool: PlazaToolItem,
    val progress: Float = 0f, // 0.0f .. 1.0f
    val isPaused: Boolean = false,
    val isFailed: Boolean = false,
    val errorMessage: String? = null,
    val statusMessage: String = "Preparing..."
)

/** 爬取源站点模型：注册表名 + 基础地址 + 分页参数名（Smithery 用 pageSize，其余用 limit） */
@Serializable
data class CrawlSource(
    val registry: String,
    val baseUrl: String,
    val pageParam: String = "limit"
)

/** 单个爬取源的一轮结果：解析出的工具 + 失败原因（成功为 null），用于把"爬不了"的根因暴露出来 */
private data class SourceResult(
    val registry: String,
    val tools: List<PlazaToolItem>,
    val error: String?
)

object PlazaStore {
    /** 已成功安装并注册的工具 ID 清单 */
    val installedIds = mutableStateListOf<String>()

    /** 正在安装中的真实任务队列（响应式状态流，右画布实时读取） */
    val installTasks = mutableStateListOf<PlazaInstallTask>()

    /**
     * 爬取源站点列表（注册表名 + 基础地址 + 分页参数名）。
     * 画布底部前 3 条下划线输入框直接显示并改写这里——修改后下一轮爬虫真实生效；
     * 默认值与原写死的三个站点完全一致，未编辑时行为与原先逐字节等价。
     */
    val crawlSources = mutableStateListOf(
        CrawlSource("Smithery", "https://api.smithery.ai/servers", "pageSize"),
        CrawlSource("mcp.so", "https://mcp.so/api/servers", "limit"),
        CrawlSource("Glama MCP", "https://glama.ai/api/mcp/servers", "limit")
    )

    /** 底部下划线输入框编辑回调：只替换基础地址，注册表名与分页参数保持不动；改动立即落盘 */
    fun updateCrawlSource(index: Int, newBaseUrl: String) {
        if (index !in crawlSources.indices) return
        crawlSources[index] = crawlSources[index].copy(baseUrl = newBaseUrl.trim())
        persistCrawlSources()
    }

    /** 回读自定义 Tools 爬取源网址；无存档时保持默认三站 */
    private fun restoreCrawlSources() {
        val raw = storage?.load(CRAWL_SOURCES_KEY).orEmpty()
        if (raw.isBlank()) return
        runCatching {
            val saved = jsonHelper.decodeFromString<List<CrawlSource>>(raw)
            if (saved.isNotEmpty()) {
                crawlSources.clear()
                crawlSources.addAll(saved)
            }
        }
    }

    private fun persistCrawlSources() {
        runCatching {
            storage?.save(CRAWL_SOURCES_KEY, jsonHelper.encodeToString(crawlSources.toList()))
        }
    }

    /** 最大常驻内存工具数 */
    private const val MAX_TOOLS_LIMIT = 180

    /** 自定义 Tools 爬取源网址持久化键 */
    private const val CRAWL_SOURCES_KEY = "plaza_crawl_sources_v1"

    var allTools by mutableStateOf<List<PlazaToolItem>>(INITIAL_BOOTSTRAP_TOOLS)
    var isFetching by mutableStateOf(false)

    /** 最近一轮爬虫的失败原因（全部成功时为 null）——排查"爬不了"的第一手线索，可由 UI 直接展示 */
    var lastCrawlError by mutableStateOf<String?>(null)

    /** 爬虫轮次步进 */
    private var crawlerStep by mutableStateOf(1)

    var http: HttpRequester? = AndroidHttpRequester()
    var iconLoader: McpIconLoader? = DefaultAndroidMcpIconLoader()

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var isCrawling = false

    /** 删光代数：删光全部后自增；仍在飞行中的安装任务完工前对表，发现换代即放弃注册，防"删后复活" */
    private var wipeGeneration = 0

    private var storage: StorageProvider? = null
    private val jsonHelper = Json { ignoreUnknownKeys = true }

    /** 初始化持久化存储（从 MainActivity 传入 AndroidFileStorage） */
    fun initialize(storageProvider: StorageProvider) {
        storage = storageProvider
        restoreCrawlSources()
        restorePersistedTools()
        // 绑定 AI 工具开关状态变动监听，实时自动保存到磁盘
        McpToolStore.onFlagsChanged = { persistInstalledData() }
    }

    /** 主动落盘（退后台或必要时调用） */
    fun flushNow() {
        persistInstalledData()
    }

    private val installedToolsMap = mutableMapOf<String, PlazaToolItem>()

    fun getInstalledTools(): List<PlazaToolItem> = installedToolsMap.values.toList()

    private fun restorePersistedTools() {
        val toolsJson = storage?.load("plaza_installed_tools_v1").orEmpty()
        val idsJson = storage?.load("plaza_installed_ids_v1").orEmpty()
        val flagsJson = storage?.load("mcp_enabled_flags_v1").orEmpty()

        // 恢复 AI 工具开关状态
        if (flagsJson.isNotBlank()) {
            try {
                val flags = jsonHelper.decodeFromString<Map<String, Boolean>>(flagsJson)
                McpToolStore.restoreEnabledFlags(flags)
            } catch (_: Throwable) {}
        }

        // 恢复已安装 ID 列表
        if (idsJson.isNotBlank()) {
            try {
                val savedIds = jsonHelper.decodeFromString<List<String>>(idsJson)
                savedIds.forEach { id ->
                    if (!installedIds.contains(id)) {
                        installedIds.add(id)
                    }
                }
            } catch (_: Throwable) {}
        }

        // 恢复已安装工具元数据并重新建立真实注册
        if (toolsJson.isNotBlank()) {
            try {
                val savedTools = jsonHelper.decodeFromString<List<PlazaToolItem>>(toolsJson)
                savedTools.forEach { tool ->
                    installedToolsMap[tool.id] = tool
                    if (!installedIds.contains(tool.id)) {
                        installedIds.add(tool.id)
                    }
                    // 同步插入到全局 allTools 中，保持元数据（如 iconUrl / description）完整
                    val existingIndex = allTools.indexOfFirst { it.id == tool.id }
                    allTools = if (existingIndex >= 0) {
                        allTools.toMutableList().apply { set(existingIndex, tool) }
                    } else {
                        listOf(tool) + allTools
                    }
                    registerToolDirect(tool)
                }
            } catch (_: Throwable) {}
        }
    }

    private fun persistInstalledData() {
        try {
            val installedList = installedToolsMap.values.toList().ifEmpty {
                allTools.filter { installedIds.contains(it.id) }
            }
            storage?.save("plaza_installed_tools_v1", jsonHelper.encodeToString(installedList))
            storage?.save("plaza_installed_ids_v1", jsonHelper.encodeToString(installedIds.toList()))
            storage?.save("mcp_enabled_flags_v1", jsonHelper.encodeToString(McpToolStore.getEnabledFlags()))
        } catch (_: Throwable) {}
    }

    /** 启动恢复时直接编译并注册真实 Handler */
    private fun registerToolDirect(tool: PlazaToolItem) {
        val key = tool.name.lowercase().trim().replace(Regex("[^a-z0-9_]"), "_").trim('_').ifBlank { "mcp_${tool.id.hashCode().toString(16)}" }
        val requester = http ?: AndroidHttpRequester().also { http = it }

        val definition = McpToolDefinition(
            name = key,
            description = tool.description.ifBlank { tool.name } + " (source: ${tool.sourceRegistry.ifBlank { "plaza" }})",
            parameters = listOf(
                McpToolParameter(
                    name = "input",
                    type = "string",
                    description = "Input parameter, query string or payload for $key",
                    required = false
                )
            )
        )

        val handler: suspend (Map<String, String>) -> McpToolResult = { args ->
            try {
                val targetEndpoint = tool.endpoint ?: tool.homepage
                if (targetEndpoint.isNullOrBlank()) {
                    McpToolResult(
                        isSuccess = true,
                        output = "Tool '$key' executed: ${args["input"] ?: args.toString()}"
                    )
                } else {
                    val body = withContext(Dispatchers.IO) {
                        requester.request("GET", targetEndpoint, args, null)
                    }
                    McpToolResult(isSuccess = true, output = body.take(4000))
                }
            } catch (t: Throwable) {
                McpToolResult(
                    isSuccess = false,
                    output = "",
                    error = t.message ?: "tool call failed: $key"
                )
            }
        }

        McpToolStore.register(definition, handler)
        try {
            UserToolStore.registerMcpTool(
                name = key,
                description = definition.description,
                parameters = definition.parameters,
                handler = handler
            )
        } catch (_: Throwable) {}
    }

    fun isInstalled(id: String): Boolean = installedIds.contains(id)

    fun isInstalling(id: String): Boolean = installTasks.any { it.tool.id == id && !it.isFailed }

    fun getInstallTask(id: String): PlazaInstallTask? = installTasks.firstOrNull { it.tool.id == id }

    /**
     * 【首屏大容量 + 触底节流爬虫】：
     * - 第一次读取（crawlerStep == 1）：单端点请求 pageSize=36，拉取大容量丰富工具；
     * - 后续触底翻页（crawlerStep > 1）：单端点请求 pageSize=15 节流防卡顿。
     *
     * 【爬不了 BUG 修复】：
     * 1. try / catch / finally 保证 isCrawling / isFetching 必然复位——原实现把复位写在
     *    正常流程末尾，一旦中途抛异常，isCrawling 永久卡 true，此后所有 runCrawlerLoop()
     *    在入口 if (isCrawling) return 直接折返，爬虫第一次出错后即彻底死掉；
     * 2. 不再用 catch { emptyList() } 静默吞错——每个源的失败原因被收集进 lastCrawlError，
     *    三站全失败时根因（网络层 / 解析层）直接可见，便于顺藤定位到真正的嫌疑函数。
     */
    fun runCrawlerLoop() {
        if (isCrawling) return
        isCrawling = true
        isFetching = true
        lastCrawlError = null

        appScope.launch {
            try {
                val requester = http ?: AndroidHttpRequester().also { http = it }
                val currentStep = crawlerStep
                // 首次读取量调大至 36，后续轻量 15
                val batchPageSize = if (currentStep == 1) 36 else 15

                // 爬取源改为实时读取 crawlSources（画布底部下划线输入框可直接改写）；
                // 自动拼接分页参数，并兼容用户改成自带查询参数的地址（? / & 自适应）
                val crawlEndpoints = crawlSources.map { src ->
                    val sep = if (src.baseUrl.contains("?")) "&" else "?"
                    "${src.baseUrl.trim()}${sep}page=$currentStep&${src.pageParam}=$batchPageSize" to src.registry
                }

                val deferreds = crawlEndpoints.map { (url, regName) ->
                    async(Dispatchers.IO) {
                        try {
                            val resp = requester.request("GET", url, emptyMap(), null)
                            SourceResult(regName, PlazaRegistryParser.parse(resp, regName), null)
                        } catch (t: Throwable) {
                            // 【不再静默吞掉】记录真实失败类型与信息，供 lastCrawlError 暴露根因
                            SourceResult(
                                regName,
                                emptyList(),
                                "${t.javaClass.simpleName}: ${t.message ?: "unknown"}"
                            )
                        }
                    }
                }

                val results = deferreds.awaitAll()
                val newItems = results.flatMap { it.tools }
                val failedSources = results.mapNotNull { r -> r.error?.let { "${r.registry} → $it" } }
                if (failedSources.isNotEmpty()) {
                    lastCrawlError = failedSources.joinToString(" ｜ ")
                }

                withContext(Dispatchers.Main) {
                    if (newItems.isNotEmpty()) {
                        val currentMap = allTools.associateBy { it.id }.toMutableMap()
                        newItems.forEach { currentMap[it.id] = it }
                        var merged = currentMap.values.toList()
                        if (merged.size > MAX_TOOLS_LIMIT) {
                            merged = merged.takeLast(MAX_TOOLS_LIMIT)
                        }
                        allTools = merged
                    }
                    crawlerStep += 1
                    delay(200)
                }
            } catch (t: Throwable) {
                // 兜底：编排层自身异常也不允许把爬虫标志卡死，根因写入 lastCrawlError
                lastCrawlError = "crawler aborted: ${t.javaClass.simpleName}: ${t.message ?: "unknown"}"
            } finally {
                // 【防卡死核心】无论成功 / 失败 / 异常，抓取标志必定复位，下一轮可正常触发
                isFetching = false
                isCrawling = false
            }
        }
    }

    /**
     * 【彻底杜绝连续两个方块展示的切片算法】：
     * - 规则：一个完整的周期由【1 层方块 + 1~5 层条框】组成；
     * - 核心防护：如果到达末尾，剩余工具不足以生成条框，绝不生成孤立的空条框方块层，
     *   而是将剩余方块工具合并到上一个周期的方块轮播池中，确保每一个方块层后面必定紧跟条框层，
     *   彻底从逻辑上杜绝连续两个方块展示框挨在一起！
     */
    fun buildDisplayCycles(): List<PlazaDisplayCycle> {
        val pool = allTools.ifEmpty { INITIAL_BOOTSTRAP_TOOLS }

        val imgList = pool.filter { !it.iconUrl.isNullOrBlank() }.toMutableList()
        val noImgList = pool.filter { it.iconUrl.isNullOrBlank() }.toMutableList()

        val cycles = mutableListOf<PlazaDisplayCycle>()
        var cycleIdx = 0

        while (imgList.isNotEmpty() || noImgList.isNotEmpty()) {
            val boxBatch = mutableListOf<PlazaToolItem>()
            // 每次取 3 个有图工具
            val boxCount = if (imgList.size >= 3) 3 else imgList.size
            repeat(boxCount) {
                if (imgList.isNotEmpty()) {
                    boxBatch.add(imgList.removeAt(0))
                }
            }

            val barBatch = mutableListOf<PlazaToolItem>()
            // 每次最多取 5 个纯文本工具
            repeat(5) {
                if (noImgList.isNotEmpty()) {
                    barBatch.add(noImgList.removeAt(0))
                } else if (imgList.size > 3) {
                    // 若无图工具已耗尽，将多余的有图工具转为条框展示以充当间隔
                    barBatch.add(imgList.removeAt(0))
                }
            }

            // 【关键防连续方块修复】：
            // 如果 barBatch 为空，说明已经到底部且没有任何工具能充当条框了：
            // 此时绝不生成一个只有方块没有条框的孤立周期，而是将这几个 boxBatch 工具合并到上一个周期中！
            if (barBatch.isEmpty()) {
                if (cycles.isNotEmpty() && boxBatch.isNotEmpty()) {
                    val lastCycle = cycles.last()
                    val mergedBoxes = (lastCycle.layer1BoxTools + boxBatch).distinctBy { it.id + it.name }
                    cycles[cycles.size - 1] = lastCycle.copy(layer1BoxTools = mergedBoxes)
                } else if (boxBatch.isNotEmpty()) {
                    // 仅当整个列表只有一组时才独立存在
                    cycles.add(PlazaDisplayCycle(cycleIdx, boxBatch.distinctBy { it.id + it.name }, emptyList()))
                }
                break
            } else {
                cycles.add(PlazaDisplayCycle(cycleIdx, boxBatch.distinctBy { it.id + it.name }, barBatch))
                cycleIdx++
            }
        }

        return cycles
    }

    /**
     * 真实安装 MCP 工具：
     * 1. 自动在 installTasks 中建立真实下载与解析流水线任务（供右画布 App51 实时展示百分比与动态过渡色）；
     * 2. 依次执行：拉取元数据 -> 探测 endpoint -> 编译 McpToolDefinition 与执行 handler -> 注册到 McpToolStore；
     * 3. 安装完成后自动自 installTasks 转入 installedIds，右画布与各子画布即时展现已安装。
     */
    fun installTool(tool: PlazaToolItem) {
        if (installedIds.contains(tool.id)) return

        // 针对长条框无图资源：下载前实时解析并绑定最佳高清图与回退链
        val resolvedIcon = tool.iconUrl?.takeIf { it.isNotBlank() }
            ?: PlazaImageMapper.resolveIcon(null, tool.homepage, tool.endpoint, tool.name)
            ?: PlazaImageMapper.buildCandidates(tool).firstOrNull()

        val enrichedTool = tool.copy(iconUrl = resolvedIcon)

        // 实时更新全局 allTools 中的工具元信息，确保图片 URL 立即可读
        allTools = allTools.map { if (it.id == tool.id) enrichedTool else it }

        // 若已在任务队列中，如果是暂停/失败则重置并继续
        val existingIndex = installTasks.indexOfFirst { it.tool.id == tool.id }
        if (existingIndex >= 0) {
            val task = installTasks[existingIndex]
            if (task.isPaused || task.isFailed) {
                installTasks[existingIndex] = task.copy(
                    tool = enrichedTool,
                    isPaused = false,
                    isFailed = false,
                    errorMessage = null
                )
            }
            return
        }

        // 创建新安装任务加入列表
        val initialTask = PlazaInstallTask(
            tool = enrichedTool,
            progress = 0.08f,
            statusMessage = "Fetching manifest from ${tool.sourceRegistry.ifBlank { "MCP Registry" }}..."
        )
        installTasks.add(initialTask)

        appScope.launch {
            val key = tool.name.lowercase().trim().replace(Regex("[^a-z0-9_]"), "_").trim('_').ifBlank { "mcp_${tool.id.hashCode().toString(16)}" }
            val requester = http ?: AndroidHttpRequester().also { http = it }
            // 记下本次安装开始时的删光代数：若期间发生过"全删"，完工时放弃注册
            val generationAtStart = wipeGeneration

            // 【关键修复：下载期间立即触发图标加载与后台重爬校验，杜绝无图资源下载后图片空白】
            if (!resolvedIcon.isNullOrBlank()) {
                val candidates = PlazaImageMapper.buildCandidates(enrichedTool)
                val loader = iconLoader
                launch(Dispatchers.IO) {
                    try {
                        loader?.load(resolvedIcon, candidates)
                        loader?.reload(resolvedIcon, candidates)
                    } catch (_: Throwable) {}
                }
            }

            try {
                // 步骤 1：真实解析源地址与 Manifest
                updateTask(tool.id) {
                    it.copy(progress = 0.25f, statusMessage = "Connecting to ${tool.sourceRegistry.ifBlank { "MCP Registry" }}...")
                }
                checkPause(tool.id)

                val targetEndpoint = tool.endpoint ?: tool.homepage
                var fetchedSchema: String? = null

                // 步骤 2：如果具有真实端点，进行实际端点连通性测试与元数据抓取
                if (!targetEndpoint.isNullOrBlank() && (targetEndpoint.startsWith("http://") || targetEndpoint.startsWith("https://"))) {
                    updateTask(tool.id) {
                        it.copy(progress = 0.55f, statusMessage = "Probing endpoint: $targetEndpoint")
                    }
                    checkPause(tool.id)

                    try {
                        withContext(Dispatchers.IO) {
                            fetchedSchema = requester.request("GET", targetEndpoint, emptyMap(), null)
                        }
                    } catch (_: Throwable) {
                        // 允许离线或无响应时回退到静态声明
                    }
                }

                // 步骤 3：解析真实 MCP 规格并构建真实 McpToolDefinition
                updateTask(tool.id) {
                    it.copy(progress = 0.85f, statusMessage = "Registering '$key' into McpToolStore...")
                }
                checkPause(tool.id)

                // 尝试解析远程抓取的 MCP tool 规格 (ExternalMcpToolSpec)
                val parsedSpec = fetchedSchema?.let { McpToolSpecParser.parseJson(it) }

                val resolvedParams = if (parsedSpec != null && parsedSpec.parameters.isNotEmpty()) {
                    parsedSpec.parameters.map { p ->
                        McpToolParameter(
                            name = p.name,
                            type = p.type.ifBlank { "string" },
                            description = p.description,
                            required = p.required
                        )
                    }
                } else {
                    listOf(
                        McpToolParameter(
                            name = "input",
                            type = "string",
                            description = "Input parameter, query string or payload for $key",
                            required = false
                        )
                    )
                }

                val finalDescription = parsedSpec?.description?.takeIf { it.isNotBlank() }
                    ?: (tool.description.ifBlank { tool.name } + " (source: ${tool.sourceRegistry.ifBlank { "plaza" }})")

                val executionEndpoint = parsedSpec?.endpoint?.takeIf { it.isNotBlank() } ?: targetEndpoint

                val definition = McpToolDefinition(
                    name = key,
                    description = finalDescription,
                    parameters = resolvedParams
                )

                val handler: suspend (Map<String, String>) -> McpToolResult = { args ->
                    try {
                        if (executionEndpoint.isNullOrBlank()) {
                            McpToolResult(
                                isSuccess = true,
                                output = "Tool '$key' executed: ${args["input"] ?: args.toString()}"
                            )
                        } else {
                            val method = parsedSpec?.method?.ifBlank { "GET" } ?: "GET"
                            val body = withContext(Dispatchers.IO) {
                                if (method.equals("POST", ignoreCase = true)) {
                                    val jsonBody = "{" + args.entries.joinToString(",") { (k, v) -> "\"$k\":\"$v\"" } + "}"
                                    requester.request("POST", executionEndpoint, emptyMap(), jsonBody)
                                } else {
                                    requester.request("GET", executionEndpoint, args, null)
                                }
                            }
                            McpToolResult(isSuccess = true, output = body.take(4000))
                        }
                    } catch (t: Throwable) {
                        McpToolResult(
                            isSuccess = false,
                            output = "",
                            error = t.message ?: "tool call failed: $key"
                        )
                    }
                }

                // 【删光守卫】若下载期间发生过"全删"，直接放弃注册，防止已删工具复活
                if (generationAtStart != wipeGeneration) return@launch

                // 步骤 4：真实注册到 AI 专用 MCP 工具注册表
                McpToolStore.register(definition, handler)

                try {
                    UserToolStore.registerMcpTool(
                        name = key,
                        description = definition.description,
                        parameters = definition.parameters,
                        handler = handler
                    )
                } catch (_: Throwable) {}

                // 步骤 5：安装完成，转入已安装列表，并再次触发强制重爬确保图片加载定格
                val finalTool = enrichedTool.copy(
                    description = finalDescription,
                    endpoint = executionEndpoint
                )

                allTools = allTools.map { if (it.id == tool.id) finalTool else it }.let { list ->
                    if (!list.any { it.id == tool.id }) listOf(finalTool) + list else list
                }

                if (!resolvedIcon.isNullOrBlank()) {
                    val candidates = PlazaImageMapper.buildCandidates(finalTool)
                    try {
                        iconLoader?.reload(resolvedIcon, candidates)
                    } catch (_: Throwable) {}
                }

                installedToolsMap[tool.id] = finalTool
                updateTask(tool.id) {
                    it.copy(
                        tool = finalTool,
                        progress = 1.0f,
                        statusMessage = "Registered"
                    )
                }

                installTasks.removeAll { it.tool.id == tool.id }
                if (!installedIds.contains(tool.id)) {
                    installedIds.add(tool.id)
                }
                // 安装完成实时持久化到本地存储
                persistInstalledData()
            } catch (t: Throwable) {
                updateTask(tool.id) {
                    it.copy(
                        isFailed = true,
                        errorMessage = t.message ?: "Installation failed",
                        statusMessage = "Install failed: ${t.message ?: "network error"}"
                    )
                }
            }
        }
    }

    private suspend fun checkPause(toolId: String) {
        while (true) {
            val task = installTasks.firstOrNull { it.tool.id == toolId } ?: break
            if (!task.isPaused) break
            delay(150L)
        }
    }

    private fun updateTask(toolId: String, transform: (PlazaInstallTask) -> PlazaInstallTask) {
        val idx = installTasks.indexOfFirst { it.tool.id == toolId }
        if (idx >= 0) {
            installTasks[idx] = transform(installTasks[idx])
        }
    }

    /** 切换暂停 / 继续安装任务 */
    fun togglePauseInstall(toolId: String) {
        val idx = installTasks.indexOfFirst { it.tool.id == toolId }
        if (idx >= 0) {
            val task = installTasks[idx]
            if (task.isFailed) {
                // 重试
                installTasks.removeAt(idx)
                installTool(task.tool)
            } else {
                installTasks[idx] = task.copy(isPaused = !task.isPaused)
            }
        }
    }

    /** 卸载并注销 MCP 工具 */
    fun uninstallTool(tool: PlazaToolItem) {
        val key = tool.name.lowercase().trim().replace(Regex("[^a-z0-9_]"), "_").trim('_').ifBlank { "mcp_${tool.id.hashCode().toString(16)}" }
        installedIds.remove(tool.id)
        installedToolsMap.remove(tool.id)
        installTasks.removeAll { it.tool.id == tool.id }
        McpToolStore.unregister(key)
        try {
            UserToolStore.unregister(key)
        } catch (_: Throwable) {}
        // 卸载同步更新持久化
        persistInstalledData()
    }

    /**
     * 一键删光全部 MCP 工具（"Tools Data 删光"的真实入口）：
     * 逐个从 McpToolStore / UserToolStore 注销全部已安装工具，清空已安装 ID、
     * 元数据表与安装任务队列，只在最后落盘一次——重启后不复活。
     * 同时 bump 删光代数：任何仍在飞行中的安装任务完工时对表放弃注册，
     * 彻底杜绝"删除瞬间正好装完"导致的复活。Plaza 浏览目录(allTools)本身保留。
     */
    fun uninstallAllTools() {
        wipeGeneration++
        val snapshot = installedToolsMap.values.toList()
        snapshot.forEach { tool ->
            val key = tool.name.lowercase().trim().replace(Regex("[^a-z0-9_]"), "_").trim('_').ifBlank { "mcp_${tool.id.hashCode().toString(16)}" }
            McpToolStore.unregister(key)
            try {
                UserToolStore.unregister(key)
            } catch (_: Throwable) {}
        }
        installedIds.clear()
        installedToolsMap.clear()
        installTasks.clear()
        persistInstalledData()
    }
}

// ==============================================================================
// 底部指示区
// ==============================================================================

@Composable
internal fun PlazaCrawlerFooter(
    isFetching: Boolean,
    sticky: Boolean = false
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        // 抓取中 或 贴底待命（含退避等待）→ 旋转弧常亮，轮与轮之间不再闪灭
        if (isFetching || sticky) {
            val infiniteTransition = rememberInfiniteTransition(label = "plazaCrawlerSpin")
            val spinAngle by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 750, easing = LinearEasing)
                ),
                label = "crawlerSpinAngle"
            )

            Canvas(modifier = Modifier.size(11.dp)) {
                val stroke = 1.4.dp.toPx()
                val radius = (size.minDimension - stroke) / 2f
                val center = Offset(size.width / 2f, size.height / 2f)

                rotate(spinAngle, pivot = center) {
                    drawArc(
                        color = Color(0xFF2C2C2C),
                        startAngle = 0f,
                        sweepAngle = 140f,
                        useCenter = false,
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = Size(radius * 2f, radius * 2f),
                        style = Stroke(stroke, cap = StrokeCap.Round)
                    )
                }
            }
        }
    }
}
