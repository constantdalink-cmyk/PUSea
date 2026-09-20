package org.example.project

// ==============================================================================
// 画布文件 1/10 之分割 1/3：subcanvascomponentplazaloader.kt —— 数据模型 + 图片加载引擎
//
// 原 subcanvascomponentplaza.kt 按代码量均分 3 个文件，本文件为第 1 份：
// 1. 统一数据模型：PlazaToolItem / McpIconLoader；
// 2. 图片加载根治：同 URL 单飞去重 + 线程安全 LRU + 失败缓存 TTL；
//    原始 URL → Google S2 → unavatar → DuckDuckGo 错开并发竞速回退链；
// 3. SquareImageLoader 状态机（Pending 脉冲 / Ok / Failed 到期自动重探），
//    每个工具的图片是否加载完成均可确认；
// 4. 手绘标准托盘向下箭头图标 + 安装完成对勾按钮；
// 5. 扩充初始启动基底库（24 个精选工具，首屏大容量秒开）。
//
// 跨文件可见性：SquareImageLoader / HandDrawnDownloadButton 由 private 提升为
// internal，供分割 3/3 的展示卡片同模块调用。
// 依赖（同包）：分割 2/3 的 PlazaStore（iconLoader 持有者）/ PlazaImageMapper。
// ==============================================================================

import android.graphics.BitmapFactory
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import java.net.HttpURLConnection
import java.net.URL
import java.util.Collections

// ==============================================================================
// 统一数据模型
// ==============================================================================

@Serializable
data class PlazaToolItem(
    val id: String,
    val name: String,
    val description: String,
    val iconUrl: String? = null,
    val installCommand: String? = null,
    val endpoint: String? = null,
    val homepage: String? = null,
    val sourceRegistry: String = "",
    val useCount: Long = 0,
    val tags: List<String> = emptyList()
)

interface McpIconLoader {
    /**
     * @param primary    主 URL，作为单飞去重与缓存键
     * @param candidates 回退链（原始 URL + 域名级 favicon 服务），竞速探测
     */
    suspend fun load(primary: String, candidates: List<String>): ImageBitmap?

    /** 非挂起的缓存直读：轮播换卡时避免已加载图闪回字母占位 */
    fun peek(url: String): ImageBitmap? = null

    /** 强制重爬探测（绕过内存缓存，用于成功后的二次重爬校验） */
    suspend fun reload(primary: String, candidates: List<String>): ImageBitmap? = load(primary, candidates)
}

/**
 * 节流防爆型 Android 图片加载器
 *
 * 【图片加载根治】：
 * - 单飞去重：多工具共用同一 icon URL 仅竞速下载一次，等待方共享 Deferred；
 * - 错开并发竞速回退链：原始 URL → Google S2 → unavatar → DuckDuckGo，
 *   第 i 源延迟 i*700ms 启动，首个解码成功者胜出，死链不独占超时窗口；
 * - 线程安全 LRU + 失败缓存 TTL(30s)：全源失败过期自动重探，不永久空白；
 * - 在途表经 invokeOnCompletion 精确移除一次，杜绝重复打源。
 */
class DefaultAndroidMcpIconLoader : McpIconLoader {
    private val maxCacheSize = 40

    /** 失败缓存 TTL：全源失败不永久判死，过期自动重探 */
    private val errTtlMs = 30_000L

    /** 竞速错开间隔：第 i 个源延迟 i * stagger 启动 */
    private val staggerMs = 700L

    /** 单源探针超时：挂起请求到期立即判负 */
    private val probeTimeoutMs = 3_200L

    private val memoryCache: MutableMap<String, ImageBitmap> = Collections.synchronizedMap(
        object : LinkedHashMap<String, ImageBitmap>(maxCacheSize, 0.75f, true) {}
    )

    /** 失败缓存：primary -> 失败时间戳 */
    private val failCache = Collections.synchronizedMap(mutableMapOf<String, Long>())

    /** 在途下载表：primary -> 共享 Deferred（单飞去重） */
    private val inFlight = mutableMapOf<String, Deferred<ImageBitmap?>>()
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun peek(url: String): ImageBitmap? = memoryCache[url]

    override suspend fun load(primary: String, candidates: List<String>): ImageBitmap? {
        if (primary.isBlank()) return null
        memoryCache[primary]?.let { return it }
        failCache[primary]?.let {
            if (System.currentTimeMillis() - it < errTtlMs) return null
        }

        val deferred = synchronized(inFlight) {
            inFlight.getOrPut(primary) {
                ioScope.async { raceCandidates(candidates) }.also { d ->
                    // 完成时精确移除一次，避免各等待方重复操作在途表
                    d.invokeOnCompletion {
                        synchronized(inFlight) { inFlight.remove(primary) }
                    }
                }
            }
        }

        val result = try {
            deferred.await()
        } catch (c: CancellationException) {
            // 【关键】调用方协程被取消（如轮播换卡）时必须重抛，
            // 否则取消被吞 → 图片误判 Failed 落回字母且永不重试
            throw c
        } catch (_: Throwable) {
            null
        }

        if (result != null) {
            synchronized(memoryCache) {
                if (memoryCache.size >= maxCacheSize) {
                    memoryCache.keys.firstOrNull()?.let { memoryCache.remove(it) }
                }
                memoryCache[primary] = result
            }
            failCache.remove(primary)
        } else {
            failCache[primary] = System.currentTimeMillis()
        }
        return result
    }

    override suspend fun reload(primary: String, candidates: List<String>): ImageBitmap? {
        val key = primary.ifBlank { candidates.firstOrNull().orEmpty() }
        if (key.isBlank()) return null

        failCache.remove(key)
        val allCandidates = if (primary.isNotBlank() && !candidates.contains(primary)) {
            listOf(primary) + candidates
        } else {
            candidates
        }

        val freshBmp = try {
            raceCandidates(allCandidates)
        } catch (_: Throwable) {
            null
        }

        if (freshBmp != null) {
            synchronized(memoryCache) {
                memoryCache[key] = freshBmp
            }
        }
        return freshBmp
    }

    /**
     * 【错开并发竞速】：第 i 个源延迟 i*stagger 后启动，首个解码成功者立即胜出，
     * 其余探针随 coroutineScope 退出自动取消 —— 死链不再独占整段超时窗口。
     */
    private suspend fun raceCandidates(candidates: List<String>): ImageBitmap? {
        if (candidates.isEmpty()) return null
        return coroutineScope {
            val channel = Channel<ImageBitmap>(Channel.CONFLATED)
            val jobs = candidates.mapIndexed { i, src ->
                async {
                    delay(i * staggerMs)
                    probeOne(src)?.let { channel.trySend(it) }
                }
            }
            val allDone = async {
                jobs.forEach { it.await() }
                null
            }
            val winner = select<ImageBitmap?> {
                channel.onReceive { it }
                allDone.onAwait { it }
            }
            jobs.forEach { it.cancel() }
            winner
        }
    }

    private suspend fun probeOne(src: String): ImageBitmap? =
        withTimeoutOrNull(probeTimeoutMs) { download(src) }

    private fun download(url: String): ImageBitmap? {
        var currentUrl = url
        var redirects = 0
        while (redirects < 4) {
            var conn: HttpURLConnection? = null
            try {
                val u = URL(currentUrl)
                conn = (u.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 7000
                    readTimeout = 7000
                    instanceFollowRedirects = false
                    setRequestProperty(
                        "User-Agent",
                        "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36"
                    )
                }
                val code = conn.responseCode
                if (code in 300..399) {
                    val location = conn.getHeaderField("Location")
                    if (!location.isNullOrBlank()) {
                        currentUrl = if (location.startsWith("http")) location else URL(u, location).toString()
                        redirects++
                        conn.disconnect()
                        continue
                    }
                }
                if (code in 200..299) {
                    val bytes = conn.inputStream.use { it.readBytes() }
                    if (bytes.isNotEmpty() && bytes.size < 600_000) {
                        // 解码成功即返回，缓存写入统一收口到 load()
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            ?.asImageBitmap()
                            ?.let { return it }
                    }
                }
                break
            } catch (_: Throwable) {
                break
            } finally {
                conn?.disconnect()
            }
        }
        return null
    }
}

// ==============================================================================
// 扩充初始启动基底库（24 个精选工具，首屏大容量秒开）
// ==============================================================================

val INITIAL_BOOTSTRAP_TOOLS: List<PlazaToolItem> = listOf(
    // 12 个带高清图片的工具
    PlazaToolItem("github-mcp", "github", "GitHub repository, issues, pull requests and code search tool server.", "https://github.githubassets.com/favicons/favicon.png", "npx -y @smithery/cli install @modelcontextprotocol/server-github", sourceRegistry = "Official MCP", useCount = 28600, tags = listOf("git", "code")),
    PlazaToolItem("brave-search", "brave_search", "Brave web search engine for real-time web retrieval & summaries.", "https://brave.com/static-assets/images/brave-logo-sans-text.png", "npx -y @smithery/cli install @modelcontextprotocol/server-brave-search", sourceRegistry = "Smithery", useCount = 24200, tags = listOf("search", "web")),
    PlazaToolItem("puppeteer-mcp", "puppeteer", "Headless Chrome browser automation and web page screenshot rendering.", "https://user-images.githubusercontent.com/10379601/29446482-04f7036a-841f-11e7-9872-91d1fc2ea683.png", "npx -y @smithery/cli install @modelcontextprotocol/server-puppeteer", sourceRegistry = "Smithery", useCount = 18300, tags = listOf("browser", "automation")),
    PlazaToolItem("postgres-mcp", "postgres", "PostgreSQL database query execution and table schema inspector.", "https://www.postgresql.org/media/img/about/press/elephant.png", "npx -y @smithery/cli install @modelcontextprotocol/server-postgres", sourceRegistry = "Official MCP", useCount = 16800, tags = listOf("sql", "db")),
    PlazaToolItem("slack-mcp", "slack", "Slack channel messaging, thread inspection and workspace management.", "https://a.slack-edge.com/80588/marketing/img/meta/favicon-32.png", "npx -y @smithery/cli install @modelcontextprotocol/server-slack", sourceRegistry = "Smithery", useCount = 13900, tags = listOf("chat", "collab")),
    PlazaToolItem("notion-mcp", "notion", "Notion workspace search, page reading, database querying and block editing.", "https://www.notion.so/images/favicon.ico", "npx -y @smithery/cli install @modelcontextprotocol/server-notion", sourceRegistry = "mcp.so", useCount = 17900, tags = listOf("notes", "docs")),
    PlazaToolItem("stripe-mcp", "stripe", "Stripe payment processing, customer records and balance inspector.", "https://stripe.com/favicon.ico", "npx -y @smithery/cli install @stripe/mcp", sourceRegistry = "Smithery", useCount = 11200, tags = listOf("payment", "finance")),
    PlazaToolItem("cloudflare-mcp", "cloudflare", "Cloudflare Workers, DNS records and CDN cache purger.", "https://www.cloudflare.com/favicon.ico", "npx -y @smithery/cli install @cloudflare/mcp", sourceRegistry = "Official MCP", useCount = 14500, tags = listOf("cdn", "dns")),
    PlazaToolItem("supabase-mcp", "supabase", "Supabase backend database, auth users and storage bucket management.", "https://supabase.com/favicon/favicon-32x32.png", "npx -y @smithery/cli install @supabase/mcp", sourceRegistry = "Glama MCP", useCount = 15800, tags = listOf("db", "auth")),
    PlazaToolItem("redis-mcp", "redis", "Redis in-memory key-value cache querying, pub/sub and key expiration.", "https://redis.io/favicon.ico", "npx -y @smithery/cli install @redis/mcp", sourceRegistry = "Smithery", useCount = 9400, tags = listOf("cache", "nosql")),
    PlazaToolItem("openai-mcp", "openai_models", "Direct invocation of OpenAI reasoning and multimodal model capabilities.", "https://openai.com/favicon.ico", "npx -y @smithery/cli install @openai/mcp", sourceRegistry = "Smithery", useCount = 25600, tags = listOf("ai", "llm")),
    PlazaToolItem("docker-mcp", "docker", "Manage local Docker containers, inspect daemon logs and start images.", "https://www.docker.com/wp-content/uploads/2022/03/Moby-logo.png", "npx -y @smithery/cli install @modelcontextprotocol/server-docker", sourceRegistry = "mcp.so", useCount = 12700, tags = listOf("container", "devops")),

    // 12 个纯文本标准工具
    PlazaToolItem("filesystem-std", "filesystem", "Safe sandboxed local file read/write/list directory tool.", null, "npx -y @smithery/cli install @modelcontextprotocol/server-filesystem", sourceRegistry = "Official MCP", useCount = 26500, tags = listOf("fs", "storage")),
    PlazaToolItem("sqlite-std", "sqlite", "Inspect and query local SQLite relational databases with auto-migration.", null, "npx -y @smithery/cli install @modelcontextprotocol/server-sqlite", sourceRegistry = "Official MCP", useCount = 18400, tags = listOf("sqlite", "sql")),
    PlazaToolItem("memory-graph", "memory", "Persistent conversational memory powered by a localized knowledge graph.", null, "npx -y @smithery/cli install @modelcontextprotocol/server-memory", sourceRegistry = "Smithery", useCount = 19200, tags = listOf("memory", "graph")),
    PlazaToolItem("fetch-tool", "fetch", "Web page content fetcher and markdown converter for LLM digestion.", null, "npx -y @smithery/cli install @modelcontextprotocol/server-fetch", sourceRegistry = "Official MCP", useCount = 23800, tags = listOf("http", "html")),
    PlazaToolItem("git-cli", "git", "Local Git repository command dispatcher: diff, log, status and branch view.", null, "npx -y @smithery/cli install @modelcontextprotocol/server-git", sourceRegistry = "Official MCP", useCount = 17600, tags = listOf("vcs", "diff")),
    PlazaToolItem("arxiv-papers", "arxiv", "Search research papers on arXiv.org by title, author, category and abstract.", null, "npx -y @smithery/cli install @modelcontextprotocol/server-arxiv", sourceRegistry = "mcp.so", useCount = 11300, tags = listOf("paper", "science")),
    PlazaToolItem("curl-requester", "curl", "Universal HTTP request client with support for custom headers and payloads.", null, "npx -y @smithery/cli install @modelcontextprotocol/server-curl", sourceRegistry = "Smithery", useCount = 14100, tags = listOf("api", "network")),
    PlazaToolItem("bash-terminal", "bash", "Sandboxed command line terminal tool execution with structured output.", null, "npx -y @smithery/cli install @modelcontextprotocol/server-bash", sourceRegistry = "Official MCP", useCount = 18900, tags = listOf("cli", "system")),
    PlazaToolItem("google-maps", "google_maps", "Lookup locations, geocode addresses, and calculate navigation routes.", null, "npx -y @smithery/cli install @modelcontextprotocol/server-google-maps", sourceRegistry = "Official MCP", useCount = 13500, tags = listOf("maps", "geo")),
    PlazaToolItem("weather-api", "weather", "Real-time global weather conditions, forecasts and severe storm warnings.", null, "npx -y @smithery/cli install @modelcontextprotocol/server-weather", sourceRegistry = "Smithery", useCount = 12100, tags = listOf("weather", "forecast")),
    PlazaToolItem("k8s-cluster", "kubernetes", "Inspect Kubernetes pods, deployments, service endpoints and cluster logs.", null, "npx -y @smithery/cli install @k8s/mcp-server", sourceRegistry = "Official MCP", useCount = 11900, tags = listOf("k8s", "devops")),
    PlazaToolItem("deepseek-tool", "deepseek_code", "DeepSeek-Coder high-speed code completion and repository refactoring.", null, "npx -y @smithery/cli install @deepseek/mcp", sourceRegistry = "Glama MCP", useCount = 22100, tags = listOf("ai", "code"))
)

// ==============================================================================
// 图标加载状态机 + 手绘下载图标
// ==============================================================================

private sealed class IconUiState {
    object Pending : IconUiState()
    object Failed : IconUiState()
    data class Ok(val bitmap: ImageBitmap) : IconUiState()
}

@Composable
internal fun SquareImageLoader(tool: PlazaToolItem, isDarkBg: Boolean = false) {
    // 初始化直接读缓存（支持从 tool.iconUrl 或 candidates 优先命中）：轮播换卡/长条框下载时不闪回字母占位
    var state by remember(tool.id, tool.iconUrl) {
        val peekTarget = tool.iconUrl?.takeIf { it.isNotBlank() }
            ?: PlazaImageMapper.buildCandidates(tool).firstOrNull()
        mutableStateOf<IconUiState>(
            peekTarget
                ?.let { PlazaStore.iconLoader?.peek(it) }
                ?.let { IconUiState.Ok(it) }
                ?: IconUiState.Pending
        )
    }

    // 脉冲呼吸（pending 态可见反馈）——无条件声明，符合 Compose 规则
    val pulseTransition = rememberInfiniteTransition(label = "iconPending")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(620),
            repeatMode = RepeatMode.Reverse
        ),
        label = "iconPendingAlpha"
    )

    // 【可确认的加载状态机】：无图资源自动生成回退链 -> Pending 脉冲 -> Ok 定格 -> 必须强制重爬1次 -> 校验更新
    LaunchedEffect(tool.id, tool.iconUrl) {
        val loader = PlazaStore.iconLoader
        val candidates = PlazaImageMapper.buildCandidates(tool)
        if (loader == null || candidates.isEmpty()) {
            state = IconUiState.Failed
            return@LaunchedEffect
        }
        val primaryUrl = tool.iconUrl?.takeIf { it.isNotBlank() } ?: candidates.first()
        while (isActive) {
            state = IconUiState.Pending
            val bmp = try {
                loader.load(primaryUrl, candidates)
            } catch (c: CancellationException) {
                throw c
            } catch (_: Throwable) {
                null
            }
            if (bmp != null) {
                state = IconUiState.Ok(bmp)

                // 【关键修复：下载或初始加载成功后，必须强制重爬1次验证最新图并更新缓存】
                delay(300L)
                val reCrawledBmp = try {
                    loader.reload(primaryUrl, candidates)
                } catch (_: Throwable) {
                    null
                }
                if (reCrawledBmp != null) {
                    state = IconUiState.Ok(reCrawledBmp)
                }
                return@LaunchedEffect
            }
            state = IconUiState.Failed
            delay(PlazaImageMapper.ERR_RETRY_MS)
        }
    }

    when (val s = state) {
        is IconUiState.Ok -> {
            Image(
                bitmap = s.bitmap,
                contentDescription = tool.name,
                modifier = Modifier.size(34.dp)
            )
        }
        else -> {
            Text(
                text = tool.name.take(1).uppercase(),
                color = if (isDarkBg) Color(0xFFF5F5F5) else Color(0xFF222222),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = if (s is IconUiState.Pending) Modifier.alpha(pulseAlpha) else Modifier
            )
        }
    }
}

@Composable
private fun HandDrawnDownloadIcon(
    isInstalled: Boolean,
    color: Color = Color(0xFF262626),
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.5.dp.toPx()

        if (isInstalled) {
            val path = Path().apply {
                moveTo(w * 0.22f, h * 0.52f)
                lineTo(w * 0.44f, h * 0.74f)
                lineTo(w * 0.80f, h * 0.28f)
            }
            drawPath(
                path = path,
                color = color,
                style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        } else {
            val trayLeft = w * 0.20f
            val trayRight = w * 0.80f
            val trayBottom = h * 0.82f
            val trayTop = h * 0.50f

            drawLine(color, Offset(trayLeft, trayTop), Offset(trayLeft, trayBottom), stroke, StrokeCap.Round)
            drawLine(color, Offset(trayLeft, trayBottom), Offset(trayRight, trayBottom), stroke, StrokeCap.Round)
            drawLine(color, Offset(trayRight, trayTop), Offset(trayRight, trayBottom), stroke, StrokeCap.Round)

            val arrowTopY = h * 0.16f
            val arrowTipY = h * 0.62f
            val arrowCenterX = w * 0.50f

            drawLine(color, Offset(arrowCenterX, arrowTopY), Offset(arrowCenterX, arrowTipY), stroke, StrokeCap.Round)
            drawLine(color, Offset(arrowCenterX - w * 0.18f, arrowTipY - h * 0.16f), Offset(arrowCenterX, arrowTipY), stroke, StrokeCap.Round)
            drawLine(color, Offset(arrowCenterX + w * 0.18f, arrowTipY - h * 0.16f), Offset(arrowCenterX, arrowTipY), stroke, StrokeCap.Round)
        }
    }
}

@Composable
internal fun HandDrawnDownloadButton(
    isInstalled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(
                color = if (isInstalled) Color(0xFF222222) else Color.White,
                shape = RoundedCornerShape(3.dp)
            )
            .border(
                width = 1.dp,
                color = if (isInstalled) Color(0xFF222222) else Color(0xFFD8D8D8),
                shape = RoundedCornerShape(3.dp)
            )
            .clickable(enabled = !isInstalled) { onClick() }
            .padding(2.dp),
        contentAlignment = Alignment.Center
    ) {
        HandDrawnDownloadIcon(
            isInstalled = isInstalled,
            color = if (isInstalled) Color(0xFFF5F5F5) else Color(0xFF262626),
            modifier = Modifier.size(18.dp)
        )
    }
}
