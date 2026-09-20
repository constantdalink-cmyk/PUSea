package org.example.project

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.min
import kotlin.random.Random

// ==================== 1. 数据模型 ====================
data class SkillData(
    val title: String,
    val desc: String,
    val author: String = "anthropics/skills",
    val tag: String = uiText(UiText.SkillOfficialTag),
    val imageUrl: String = "",
    val repoUrl: String = "https://github.com/anthropics/skills"
)

data class SkillGridRow(
    val pairId: Int,
    val isTall: Boolean,
    val itemLeft: SkillData,
    val itemRight: SkillData
)

data class DebrisParticleState(
    val id: Int,
    val startX: Float,
    val dx: Float,
    val dy: Float,
    val durationMs: Int
)

// ==================== 2. 图片爬取与重试状态机 ====================
sealed class ImageCrawlStatus {
    object Idle : ImageCrawlStatus()
    data class Fetching(val attempt: Int) : ImageCrawlStatus()
    data class Success(val bitmap: androidx.compose.ui.graphics.ImageBitmap) : ImageCrawlStatus()
    data class Retrying(val attempt: Int, val maxAttempts: Int, val nextRetryMs: Long) : ImageCrawlStatus()
    data class Failed(val fallbackReason: String) : ImageCrawlStatus()
}

// 内存图像 Bitmap 缓存，避免重复网络请求与重复解码
object SkillImageCache {
    private val bitmapCache = java.util.concurrent.ConcurrentHashMap<String, androidx.compose.ui.graphics.ImageBitmap>()

    fun get(url: String): androidx.compose.ui.graphics.ImageBitmap? = bitmapCache[url]
    fun put(url: String, bitmap: androidx.compose.ui.graphics.ImageBitmap) {
        bitmapCache[url] = bitmap
    }
}

// ==================== 3. 真实网络图片下载与多轮断点重爬算法 ====================
object SkillImageCrawlEngine {
    private const val MAX_RETRIES = 3
    private const val BASE_DELAY_MS = 500L
    private const val MAX_DELAY_MS = 2500L

    /**
     * 针对网络中断、图片流丢包的真实重爬引擎
     */
    suspend fun fetchOrRecrawlImage(
        imageUrl: String,
        onStatusChange: (ImageCrawlStatus) -> Unit
    ) {
        if (imageUrl.isEmpty()) {
            onStatusChange(ImageCrawlStatus.Failed("Empty URL"))
            return
        }

        val cached = SkillImageCache.get(imageUrl)
        if (cached != null) {
            onStatusChange(ImageCrawlStatus.Success(cached))
            return
        }

        // 本地图片（文件管理器选中的 content:// uri 或绝对路径）：走平台解码桥接，
        // 不参与网络重爬，失败时才回落到矢量兜底。
        if (SkillLocalImageLoader.looksLocal(imageUrl)) {
            onStatusChange(ImageCrawlStatus.Fetching(1))
            val localBitmap = SkillLocalImageLoader.load(imageUrl)
            if (localBitmap != null) {
                SkillImageCache.put(imageUrl, localBitmap)
                onStatusChange(ImageCrawlStatus.Success(localBitmap))
            } else {
                onStatusChange(ImageCrawlStatus.Failed("Local image unavailable"))
            }
            return
        }

        var attempt = 1
        var success = false

        while (attempt <= MAX_RETRIES && !success) {
            onStatusChange(ImageCrawlStatus.Fetching(attempt))
            
            val bitmap = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val urlObj = java.net.URL(imageUrl)
                    val conn = urlObj.openConnection() as java.net.HttpURLConnection
                    conn.connectTimeout = 8000
                    conn.readTimeout = 8000
                    conn.instanceFollowRedirects = true
                    conn.doInput = true
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36")
                    conn.setRequestProperty("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")

                    if (conn.responseCode in 200..299) {
                        conn.inputStream.use { input ->
                            val bytes = input.readBytes()
                            if (bytes.isNotEmpty()) {
                                // 解码为 Android Bitmap 并转为 Compose ImageBitmap
                                val androidBitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                androidBitmap?.asImageBitmap()
                            } else null
                        }
                    } else null
                } catch (e: Throwable) {
                    null
                }
            }

            if (bitmap != null) {
                SkillImageCache.put(imageUrl, bitmap)
                onStatusChange(ImageCrawlStatus.Success(bitmap))
                success = true
            } else {
                if (attempt < MAX_RETRIES) {
                    val exponentialBackoff = min(MAX_DELAY_MS, BASE_DELAY_MS * (1 shl (attempt - 1)))
                    val jitter = Random.nextLong(100L)
                    val retryDelay = exponentialBackoff + jitter
                    onStatusChange(ImageCrawlStatus.Retrying(attempt, MAX_RETRIES, retryDelay))
                    delay(retryDelay)
                } else {
                    onStatusChange(ImageCrawlStatus.Failed("Image download interrupted after $MAX_RETRIES retries"))
                }
            }
            attempt++
        }
    }
}

// ==================== 4. 健壮的真实技能图像组件 (支持动态重爬与降级) ====================
@Composable
fun RobustSkillImage(
    skill: SkillData,
    modifier: Modifier = Modifier,
    isLarge: Boolean = false,
    pixelFont: FontFamily
) {
    var status by remember(skill.imageUrl) { mutableStateOf<ImageCrawlStatus>(ImageCrawlStatus.Idle) }
    var manualRetryTrigger by remember { mutableIntStateOf(0) }

    LaunchedEffect(skill.imageUrl, manualRetryTrigger) {
        SkillImageCrawlEngine.fetchOrRecrawlImage(skill.imageUrl) { newStatus ->
            status = newStatus
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(if (isLarge) 8.dp else 6.dp))
            .background(Color(0xFFEFEFEF))
            .border(1.dp, Color(0xFFDDDDDD), RoundedCornerShape(if (isLarge) 8.dp else 6.dp)),
        contentAlignment = Alignment.Center
    ) {
        when (val currentStatus = status) {
            is ImageCrawlStatus.Fetching, is ImageCrawlStatus.Retrying -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                    modifier = Modifier.padding(2.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(if (isLarge) 20.dp else 14.dp),
                        strokeWidth = 2.dp,
                        color = Color(0xFF181717)
                    )
                    if (isLarge && currentStatus is ImageCrawlStatus.Retrying) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Retry ${currentStatus.attempt}/${currentStatus.maxAttempts}",
                            fontSize = 8.sp,
                            fontFamily = pixelFont,
                            color = Color(0xFF888888)
                        )
                    }
                }
            }
            is ImageCrawlStatus.Success -> {
                // 成功解码：渲染真实下载到的 ImageBitmap
                androidx.compose.foundation.Image(
                    bitmap = currentStatus.bitmap,
                    contentDescription = skill.title,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            is ImageCrawlStatus.Failed, ImageCrawlStatus.Idle -> {
                // 失败或未完成：展示干净的重试占位方块
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { manualRetryTrigger++ }
                        .padding(4.dp)
                ) {
                    Text(
                        text = "IMG",
                        fontSize = if (isLarge) 14.sp else 10.sp,
                        fontFamily = pixelFont,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF888888)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Reload",
                        fontSize = if (isLarge) 9.sp else 7.sp,
                        fontFamily = pixelFont,
                        color = Color(0xFFAAAAAA)
                    )
                }
            }
        }
    }
}

// ==================== 4.5 Skills 真实爬虫 (与 Tools 的 PlazaStore.runCrawlerLoop 完全对称) ====================

/** Skills 爬取源模型：来源名 + 专门收录 skills 的网站 API 地址 */
@Serializable
data class SkillCrawlSource(
    val name: String,
    val baseUrl: String
)

/**
 * 专门 skills 站点 API → SkillData 容错解析器（与 Tools 的 PlazaRegistryParser 同构的
 * 多键名兼容结构）：递归收集已知键名下的条目数组，各站点字段命名差异全部靠多键回退消化。
 */
object SkillRegistryParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String, sourceName: String): List<SkillData> {
        val element = try { json.parseToJsonElement(text) } catch (_: Exception) { return emptyList() }

        // 递归收集已知键名下的条目数组；根本身是数组（skills.sh 直返数组）时直接用
        val arrays = mutableListOf<JsonArray>()
        fun collect(e: JsonElement) {
            when (e) {
                is JsonArray -> arrays.add(e)
                is JsonObject -> e.forEach { (k, v) ->
                    if (k in setOf("skills", "items", "data", "results", "list", "agents", "entries")) {
                        if (v is JsonArray) arrays.add(v) else if (v is JsonObject) collect(v)
                    }
                }
                else -> {}
            }
        }
        collect(element)

        val list = mutableListOf<SkillData>()
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
                fun num(vararg keys: String): Long {
                    for (k in keys) {
                        val v = obj[k] ?: continue
                        try { v.jsonPrimitive.contentOrNull?.toLongOrNull()?.let { return it } } catch (_: Exception) {}
                    }
                    return 0L
                }

                val title = str("name", "slug", "title", "displayName", "skill", "id")
                if (title.isBlank()) return@forEach

                val desc = str("description", "summary", "desc", "blurb", "info")
                val author = str("owner", "author", "source", "creator", "publisher", "repository")
                val imageUrl = str("logoUrl", "logo", "iconUrl", "icon", "imageUrl", "image", "avatar", "avatarUrl")
                val homepage = str("homepage", "html_url", "url", "website", "repoUrl", "sourceUrl", "githubUrl")
                val popularity = num("installs", "downloads", "stars", "uses", "installCount")

                list.add(
                    SkillData(
                        title = title,
                        desc = desc.ifBlank { "Crawled from $sourceName" },
                        author = author.ifBlank { sourceName },
                        tag = if (popularity > 0) "${formatCount(popularity)} installs" else "Crawled",
                        imageUrl = imageUrl,
                        repoUrl = homepage
                    )
                )
            }
        }
        return list.distinctBy { it.title }
    }

    private fun formatCount(n: Long): String = when {
        n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0)
        n >= 1_000 -> "%.1fK".format(n / 1_000.0)
        else -> n.toString()
    }
}

/**
 * Skills 爬取仓库。注意：刻意不叫 SkillStore——那个名字已被 SkillDataStores.kt 里
 * 管理 AI 技能开关的 object 占用，同包重名会编译冲突。
 */
object SkillCrawlStore {
    /**
     * Skills 爬取源列表：三个专门收录 skills 的网站（不用 GitHub）——
     * ClawHub（OpenClaw 公共注册表，文档化公开只读 API）/
     * skills.sh（Vercel 官方 skills 目录，CLI 同款匿名端点）/
     * SkillsMP（1.5M 技能库，匿名 50 次/天）。
     * Function Management 画布后八行下划线直接显示并改写这里。
     */
    val skillSources = mutableStateListOf(
        SkillCrawlSource("ClawHub", "https://clawhub.ai/api/v1/skills?limit=30&sort=downloads"),
        SkillCrawlSource("skills.sh", "https://skills.sh/api/search?q=agent&limit=30"),
        SkillCrawlSource("SkillsMP", "https://skillsmp.com/api/v1/skills/search?q=agent&limit=30&sortBy=stars")
    )

    private val crawlScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var isCrawling = false

    private const val SOURCES_KEY = "skill_crawl_sources_v1"
    private var storage: StorageProvider? = null
    private val jsonHelper = Json { ignoreUnknownKeys = true }

    /** MainActivity 启动时注入持久化存储，并同步回读自定义 Skills 爬取源网址 */
    fun initialize(provider: StorageProvider) {
        storage = provider
        val raw = provider.load(SOURCES_KEY).orEmpty()
        if (raw.isBlank()) return
        runCatching {
            val saved = jsonHelper.decodeFromString<List<SkillCrawlSource>>(raw)
            if (saved.isNotEmpty()) {
                skillSources.clear()
                skillSources.addAll(saved)
            }
        }
    }

    /** 画布底部下划线编辑回调：只替换爬取源地址，来源名保持不动；改动立即落盘 */
    fun updateSkillSource(index: Int, newBaseUrl: String) {
        if (index !in skillSources.indices) return
        skillSources[index] = skillSources[index].copy(baseUrl = newBaseUrl.trim())
        runCatching {
            storage?.save(SOURCES_KEY, jsonHelper.encodeToString(skillSources.toList()))
        }
    }

    /**
     * 真实爬取 skills 资源网站：逐个读取 skillSources → 请求各专门 skills 站点 API →
     * SkillRegistryParser 解析 → 按 title 去重填充进 GlobalSkillPool。
     * 池内无任何写死数据，技能全部来自真实爬取；isCrawling 防并发重入。
     */
    fun runSkillCrawler() {
        if (isCrawling) return
        isCrawling = true
        crawlScope.launch {
            val crawled = mutableListOf<SkillData>()
            skillSources.toList().forEach { src ->
                if (src.baseUrl.isBlank()) return@forEach
                val text = withContext(Dispatchers.IO) { fetchText(src.baseUrl) }
                if (text != null) crawled += SkillRegistryParser.parse(text, src.name)
            }
            if (crawled.isNotEmpty()) {
                val known = GlobalSkillPool.map { it.title }.toMutableSet()
                crawled.forEach { skill ->
                    if (known.add(skill.title)) GlobalSkillPool.add(skill)
                }
            }
            isCrawling = false
        }
    }

    private fun fetchText(url: String): String? = try {
        val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36")
        conn.setRequestProperty("Accept", "application/json")
        if (conn.responseCode in 200..299) conn.inputStream.use { it.readBytes().decodeToString() } else null
    } catch (_: Throwable) { null }
}

// ==================== 5. Agent Skills 资源池（纯真实爬取，不内置任何写死数据） ====================
// 池子初始为空，完全由 SkillCrawlStore.runSkillCrawler() 从 ClawHub / skills.sh / SkillsMP
// 实时爬取并按 title 去重填充；离线或三站全部失败时为空即是真实状态，不做假数据兜底。
// mutableStateListOf 保证爬取结果一回来就即时驱动技能广场 UI 重组。
val GlobalSkillPool = mutableStateListOf<SkillData>()
