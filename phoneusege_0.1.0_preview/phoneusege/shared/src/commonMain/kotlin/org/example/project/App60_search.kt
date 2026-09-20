package org.example.project

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

// ==============================================================================
// 1. 真实技能搜索与 URL 解析引擎 (SkillSearchAndParserEngine)
// ==============================================================================

object SkillSearchAndParserEngine {

    /**
     * 关键词搜索：在已下载资源与全局 Skills 仓库中进行多维度匹配 (名称、描述、作者、标签)
     */
    fun searchSkills(
        query: String,
        poolList: List<SkillData>,
        downloadedList: List<SkillCardItemData>
    ): List<SkillCardItemData> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return downloadedList

        // 如果用户输入的是 URL，先返回已下载列表（不破坏列表渲染，弹窗由自动检测处理）
        if (isSkillResourceUrl(trimmed)) {
            return downloadedList
        }

        val lower = trimmed.lowercase()

        // 1. 搜索已下载的资源
        val matchedDownloaded = downloadedList.filter {
            it.name.lowercase().contains(lower) || it.detail.lowercase().contains(lower)
        }

        // 2. 搜索全局 Skills 池中尚未下载的匹配项并临时转换展示
        val matchedFromPool = poolList.filter {
            it.title.lowercase().contains(lower) ||
                    it.desc.lowercase().contains(lower) ||
                    it.tag.lowercase().contains(lower) ||
                    it.author.lowercase().contains(lower)
        }.map {
            SkillCardItemData(
                id = "search_pool_${it.title.hashCode()}",
                name = it.title,
                detail = "[${uiText(UiText.SkillPlazaPrefix)}${it.tag}] ${it.desc}",
                color = Color(0xFF4D96FF)
            )
        }

        // 去重合并
        val existingNames = matchedDownloaded.map { it.name.lowercase() }.toSet()
        val uniquePoolMatches = matchedFromPool.filterNot { it.name.lowercase() in existingNames }
        return matchedDownloaded + uniquePoolMatches
    }

    /**
     * 校验并自动识别外部 Skills 资源网址
     * 规则：只会解析属于 skills 的资源（包含 github.com/.../skills, SKILL.md, raw.githubusercontent...skills, .skill 等）
     */
    fun isSkillResourceUrl(url: String): Boolean {
        val lower = url.trim().lowercase()
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) return false

        return lower.contains("skill") ||
                lower.contains("agent-skill") ||
                lower.contains("mcp") ||
                lower.endsWith(".skill") ||
                lower.endsWith("skill.md") ||
                lower.contains("/skills/")
    }

    /**
     * 解析该 skills URL 的标题、详细简介和封面图片 URL
     */
    fun resolveSkillInfoFromUrl(url: String): Triple<String, String, String> {
        val cleanUrl = url.trim()
        // 先检查是否与 GlobalSkillPool 中已知技能匹配
        val matchedPool = GlobalSkillPool.firstOrNull { skill ->
            cleanUrl.contains(skill.title, ignoreCase = true)
        }

        if (matchedPool != null) {
            return Triple(
                matchedPool.title,
                "${matchedPool.desc}\n\n• ${uiText(UiText.SkillSourcePrefix)}${matchedPool.author}\n• ${uiText(UiText.SkillTagPrefix)}${matchedPool.tag}\n• ${uiText(UiText.SkillEndpointPrefix)}$cleanUrl\n\n${uiText(UiText.SkillStandardPackageDesc)}",
                // 不再兜底到任何第三方品牌标志：技能池未配图时封面留空，
                // 弹窗 34dp 框走自带矢量兜底，下载后的卡片显示空预留框
                matchedPool.imageUrl
            )
        }

        // 提取技能名称
        val pathSegment = cleanUrl
            .substringBefore("?")
            .substringBefore("#")
            .removeSuffix("/")
            .substringAfterLast("/")
            .removeSuffix(".md")
            .removeSuffix(".git")
            .removeSuffix(".skill")
            .let {
                if (it.equals("SKILL", ignoreCase = true)) {
                    cleanUrl.substringBeforeLast("/").substringAfterLast("/")
                } else it
            }
            .ifBlank { "agent-custom-skill" }

        val prettyName = pathSegment
            .replace("-", " ")
            .replace("_", " ")
            .split(" ")
            .joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }

        // 提取作者/组织和在线头像
        val author = if (cleanUrl.contains("github.com/")) {
            cleanUrl.substringAfter("github.com/").substringBefore("/")
        } else {
            "remote-agent-skills"
        }
        // GitHub 链接仍加载对应作者的真实头像；非 GitHub / 提取不到作者时封面留空，
        // 不再兜底到任何第三方品牌标志
        val imageUrl = if (author != "remote-agent-skills" && author.isNotBlank()) {
            "https://github.com/$author.png"
        } else {
            ""
        }

        val fullDesc = buildString {
            appendLine(uiText(UiText.SkillPackageTitle))
            appendLine()
            appendLine("${uiText(UiText.SkillNamePrefix)}$prettyName")
            appendLine("${uiText(UiText.SkillRepoPrefix)}github.com/$author")
            appendLine("${uiText(UiText.SkillLinkPrefix)}$cleanUrl")
            appendLine()
            appendLine(uiText(UiText.SkillDescLabel))
            appendLine(uiText(UiText.SkillInjectParagraph))
            appendLine()
            appendLine(uiText(UiText.SkillCrawlParagraph))
        }.trim()

        return Triple(prettyName, fullDesc, imageUrl)
    }

    /**
     * 真实下载并存入资源仓储
     */
    fun downloadSkillFromUrl(url: String): SkillCardItemData {
        val cleanUrl = url.trim()
        val (title, _, imageUrl) = resolveSkillInfoFromUrl(cleanUrl)

        val palette = listOf(
            Color(0xFF2ECC71),
            Color(0xFF3DDC84),
            Color(0xFF4D96FF),
            Color(0xFF00CEC9),
            Color(0xFF9B59B6),
            Color(0xFFFF7675)
        )
        val cardColor = palette[Math.abs(title.hashCode()) % palette.size]

        // 允许重复下载：使用纳秒 + 随机数确保每次生成的 id 绝对唯一
        val uniqueSuffix = "${System.nanoTime()}_${Random.nextInt(100000, 999999)}"
        val newResource = SkillCardItemData(
            id = "res_url_${uniqueSuffix}",
            name = title,
            detail = cleanUrl,
            color = cardColor,
            imageUrl = imageUrl
        )

        DownloadedResourceStore.addDownloadedCard(newResource)
        return newResource
    }
}

// ==============================================================================
// 2. 自动弹出【淡入效果】的较高方块下载预览器 (AutoSkillDownloadModalBox)
// ==============================================================================

@Composable
fun AutoSkillDownloadModalBox(
    url: String,
    visible: Boolean,
    pixelFont: FontFamily = FontFamily.Default,
    onDismiss: () -> Unit,
    onDownloadConfirmed: (SkillCardItemData) -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(280, easing = CubicBezierEasing(0.25f, 1f, 0.5f, 1f))),
        exit = fadeOut(animationSpec = tween(220))
    ) {
        val coroutineScope = rememberCoroutineScope()
        var isDownloading by remember(url) { mutableStateOf(false) }
        var isSuccessDownloaded by remember(url) { mutableStateOf(false) }

        val (skillName, skillDesc, skillImageUrl) = remember(url) {
            SkillSearchAndParserEngine.resolveSkillInfoFromUrl(url)
        }

        // 34dp 预留图框的真实网络爬虫状态
        var imageStatus by remember(url, skillImageUrl) {
            mutableStateOf<ImageCrawlStatus>(ImageCrawlStatus.Idle)
        }
        var manualRetryTrigger by remember { mutableIntStateOf(0) }

        LaunchedEffect(skillImageUrl, manualRetryTrigger) {
            SkillImageCrawlEngine.fetchOrRecrawlImage(skillImageUrl) { newStatus ->
                imageStatus = newStatus
            }
        }

        // 全屏半透明遮罩背景
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x8A000000))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    if (!isDownloading) onDismiss()
                }
                .zIndex(300f),
            contentAlignment = Alignment.Center
        ) {
            // 一个较高的方块从屏幕中心弹出【淡入】—— 调高方块高度
            Box(
                modifier = Modifier
                    .width(264.dp)
                    .height(440.dp)
                    .clickable(enabled = false) {} // 阻止点击穿透到背景
                    .shadow(16.dp, RoundedCornerShape(22.dp))
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color.White)
                    .border(2.dp, Color(0xFF181717), RoundedCornerShape(22.dp))
                    .padding(horizontal = 18.dp, vertical = 16.dp)
            ) {
                // ==================== 方块顶部居中的取消按钮：圆+斜45°加号 ====================
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 4.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFF0F0F0))
                        .border(1.5.dp, Color(0xFF222222), CircleShape)
                        .clickable {
                            if (!isDownloading) onDismiss()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // 斜着 45 度的加号当取消按钮
                    Canvas(
                        modifier = Modifier
                            .size(13.dp)
                            .rotate(45f)
                    ) {
                        val strokeW = 2.2f.dp.toPx()
                        val pColor = Color(0xFF181717)
                        // 水平线
                        drawLine(
                            color = pColor,
                            start = Offset(0f, size.height / 2f),
                            end = Offset(size.width, size.height / 2f),
                            strokeWidth = strokeW,
                            cap = StrokeCap.Round
                        )
                        // 垂直线
                        drawLine(
                            color = pColor,
                            start = Offset(size.width / 2f, 0f),
                            end = Offset(size.width / 2f, size.height),
                            strokeWidth = strokeW,
                            cap = StrokeCap.Round
                        )
                    }
                }

                // ==================== 主体内容列（顶部居中取消按钮已占 32dp 空间） ====================
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 40.dp, bottom = 56.dp), // 顶部给居中取消按钮，底部给下载图标
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 已删除顶部绿点 + SKILL RESOURCE DETECTED 字幕
                    // ==================== 方块上部有一个预留 34dp 的方块用于加载爬取资源中的图片，展示 ====================
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .shadow(2.dp, RoundedCornerShape(10.dp))
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFF4F4F4))
                            .border(1.5.dp, Color(0xFF222222), RoundedCornerShape(10.dp))
                            .clickable { manualRetryTrigger++ },
                        contentAlignment = Alignment.Center
                    ) {
                        when (val currentStatus = imageStatus) {
                            is ImageCrawlStatus.Fetching, is ImageCrawlStatus.Retrying -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = Color(0xFF181717)
                                )
                            }
                            is ImageCrawlStatus.Success -> {
                                Image(
                                    bitmap = currentStatus.bitmap,
                                    contentDescription = skillName,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            is ImageCrawlStatus.Failed, ImageCrawlStatus.Idle -> {
                                // 手绘精巧的几何技能魔块矢量兜底
                                Canvas(modifier = Modifier.size(20.dp)) {
                                    val w = size.width
                                    val h = size.height
                                    val primaryColor = Color(0xFF2ECC71)
                                    val darkColor = Color(0xFF222222)
                                    val strokeW = 1.6f.dp.toPx()
                                    val hexPath = Path().apply {
                                        moveTo(w * 0.5f, h * 0.08f)
                                        lineTo(w * 0.92f, h * 0.32f)
                                        lineTo(w * 0.92f, h * 0.72f)
                                        lineTo(w * 0.5f, h * 0.95f)
                                        lineTo(w * 0.08f, h * 0.72f)
                                        lineTo(w * 0.08f, h * 0.32f)
                                        close()
                                    }
                                    drawPath(hexPath, color = primaryColor)
                                    drawPath(hexPath, color = darkColor, style = Stroke(width = strokeW))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // ==================== 预留框往下就放这个 skills 的名称和简介 ====================
                    Text(
                        text = skillName,
                        fontFamily = pixelFont,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF111111),
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // 分割装饰线
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .height(2.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(Color(0xFF222222))
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 简介可滑动，避免过多导致省略
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFF9F9F9))
                            .border(1.dp, Color(0xFFE8E8E8), RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = skillDesc,
                                fontFamily = pixelFont,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Normal,
                                color = Color(0xFF2C2C2C),
                                lineHeight = 16.sp,
                                maxLines = Int.MAX_VALUE // 避免过多导致省略，配合滑动展示完整内容
                            )
                        }
                    }
                }

                // ==================== 方块底部居中处的手绘下载图标（已删除黑色圆形背景，改为纯黑色图标） ====================
                val downloadScale by animateFloatAsState(
                    targetValue = if (isDownloading) 0.88f else 1f,
                    animationSpec = tween(180, easing = FastOutSlowInEasing)
                )

                // 仅作为可点击命中区（隐形矩形），不再显示任何黑色填充圆
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 6.dp)
                        .size(48.dp)
                        .clickable(enabled = !isDownloading && !isSuccessDownloaded) {
                            coroutineScope.launch {
                                isDownloading = true
                                delay(320) // 模拟下载入库平滑感
                                val downloadedCard = SkillSearchAndParserEngine.downloadSkillFromUrl(url)
                                isDownloading = false
                                isSuccessDownloaded = true
                                delay(450)
                                onDownloadConfirmed(downloadedCard)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isDownloading) {
                        // 下载中：使用纯黑色细线圆环旋转
                        CircularProgressIndicator(
                            modifier = Modifier.size(26.dp),
                            strokeWidth = 2.4.dp,
                            color = Color(0xFF181717)
                        )
                    } else if (isSuccessDownloaded) {
                        // 已下载完成：纯黑色手绘勾号
                        Canvas(
                            modifier = Modifier
                                .size(26.dp)
                                .scale(downloadScale)
                        ) {
                            val strokeW = 3.0f.dp.toPx()
                            val pColor = Color(0xFF181717)
                            drawLine(
                                color = pColor,
                                start = Offset(size.width * 0.20f, size.height * 0.54f),
                                end = Offset(size.width * 0.44f, size.height * 0.76f),
                                strokeWidth = strokeW,
                                cap = StrokeCap.Round
                            )
                            drawLine(
                                color = pColor,
                                start = Offset(size.width * 0.44f, size.height * 0.76f),
                                end = Offset(size.width * 0.82f, size.height * 0.26f),
                                strokeWidth = strokeW,
                                cap = StrokeCap.Round
                            )
                        }
                    } else {
                        // 默认：纯黑色手绘艺术质感下载图标 —— 向下粗箭头 + 底部承接托盘（无背景圆）
                        Canvas(
                            modifier = Modifier
                                .size(30.dp)
                                .scale(downloadScale)
                        ) {
                            val strokeW = 3.0f.dp.toPx()
                            val pColor = Color(0xFF181717)
                            val cx = size.width / 2f

                            // 1) 箭杆竖线
                            drawLine(
                                color = pColor,
                                start = Offset(cx, size.height * 0.14f),
                                end = Offset(cx, size.height * 0.64f),
                                strokeWidth = strokeW,
                                cap = StrokeCap.Round
                            )
                            // 2) 箭头左翼
                            drawLine(
                                color = pColor,
                                start = Offset(cx - size.width * 0.24f, size.height * 0.42f),
                                end = Offset(cx, size.height * 0.64f),
                                strokeWidth = strokeW,
                                cap = StrokeCap.Round
                            )
                            // 3) 箭头右翼
                            drawLine(
                                color = pColor,
                                start = Offset(cx + size.width * 0.24f, size.height * 0.42f),
                                end = Offset(cx, size.height * 0.64f),
                                strokeWidth = strokeW,
                                cap = StrokeCap.Round
                            )
                            // 4) 手绘风格底部托盘 (U形底座)
                            val trayPath = Path().apply {
                                moveTo(size.width * 0.14f, size.height * 0.66f)
                                lineTo(size.width * 0.14f, size.height * 0.86f)
                                lineTo(size.width * 0.86f, size.height * 0.86f)
                                lineTo(size.width * 0.86f, size.height * 0.66f)
                            }
                            drawPath(
                                path = trayPath,
                                color = pColor,
                                style = Stroke(
                                    width = strokeW,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==============================================================================
// 3. 通用搜索框组件 (自动解析输入中的 skills 资源网址并淡入弹出下载框)
// ==============================================================================

@Composable
fun SkillSearchBarWithParser(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onExecuteKeywordSearch: (String) -> Unit,
    pixelFont: FontFamily = FontFamily.Default,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    // 严格分离：仅在【长按搜索图标】时才解析并弹出高方块下载弹窗；绝不因输入文本自动弹出
    var activeParsedModalUrl by remember { mutableStateOf<String?>(null) }

    Row(
        modifier = modifier
            .background(Color(0xFFF2F2F2), RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 放大镜图标：
        //   - 点击 = 永远执行关键词搜索（如果输的是网址，点击也走关键词搜索！）
        //   - 长按 = 触发网址解析并淡入弹出高方块下载框
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(Color(0xFFE4E4E4))
                .pointerInput(searchQuery) {
                    detectTapGestures(
                        onTap = {
                            // 单击：严格只执行关键词搜索，绝不解析网址或弹窗
                            onExecuteKeywordSearch(searchQuery)
                            focusManager.clearFocus()
                        },
                        onLongPress = {
                            // 必须严格校验：只有输入真正满足 skills 资源网址规范时才解析弹窗；
                            // 乱打字或空字符串长按决不假装解析出资源提供下载！
                            val trimmedUrl = searchQuery.trim()
                            if (SkillSearchAndParserEngine.isSkillResourceUrl(trimmedUrl)) {
                                focusManager.clearFocus()
                                activeParsedModalUrl = trimmedUrl
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Text(text = "🔍", fontSize = 12.sp)
        }
        Spacer(modifier = Modifier.width(6.dp))
        BasicTextField(
            value = searchQuery,
            onValueChange = { newText ->
                onSearchQueryChange(newText)
            },
            singleLine = true,
            textStyle = TextStyle(
                fontFamily = pixelFont,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF1A1A1A)
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                // 键盘回车也只走关键词搜索
                onExecuteKeywordSearch(searchQuery)
                focusManager.clearFocus()
            }),
            modifier = Modifier.weight(1f),
            decorationBox = { innerTextField ->
                Box(modifier = Modifier.fillMaxWidth()) {
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = uiText(UiText.SkillSearchShortPlaceholder),
                            fontFamily = pixelFont,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.W500,
                            color = Color(0xFF888888)
                        )
                    }
                    innerTextField()
                }
            }
        )
        if (searchQuery.isNotEmpty()) {
            Text(
                text = "✕",
                fontSize = 11.sp,
                color = Color(0xFF888888),
                fontFamily = pixelFont,
                modifier = Modifier
                    .clickable {
                        onSearchQueryChange("")
                        onExecuteKeywordSearch("")
                    }
                    .padding(horizontal = 4.dp)
            )
        }
    }

    // 只有长按后才以【淡入效果】弹出较高的方块下载框
    val modalUrl = activeParsedModalUrl
    AutoSkillDownloadModalBox(
        url = modalUrl ?: "",
        visible = modalUrl != null,
        pixelFont = pixelFont,
        onDismiss = {
            activeParsedModalUrl = null
        },
        onDownloadConfirmed = {
            activeParsedModalUrl = null
            onSearchQueryChange("")
            onExecuteKeywordSearch("")
        }
    )
}

// ==============================================================================
// 4. 右下角收纳凹槽专属滑出搜索框组件 (ManagementNotchSearchBar)
// ==============================================================================

@Composable
fun ManagementNotchSearchBar(
    searchBarWidth: Dp,
    searchQuery: String,
    pixelFont: FontFamily,
    onSearchQueryChange: (String) -> Unit,
    onPerformSearch: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (searchBarWidth <= 2.dp) return

    val focusManager = LocalFocusManager.current
    val searchFocusRequester = remember { FocusRequester() }
    var isSearchFocused by remember { mutableStateOf(false) }
    val searchBarBorderColor by animateColorAsState(
        targetValue = if (isSearchFocused) Color(0xFF2ECC71) else Color(0xFF1A1A1A),
        animationSpec = tween(220, easing = FastOutSlowInEasing)
    )

    // 严格分离：仅在【长按搜索图标】时才解析网址并淡入弹出高方块下载弹窗；输入框输入绝不自动弹出
    var activeParsedModalUrl by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = modifier
            .width(searchBarWidth)
            .height(42.dp)
            .clipToBounds()
            // 已移除搜索框底部阴影
            .clip(RoundedCornerShape(21.dp))
            .background(Color.White)
            .border(2.dp, searchBarBorderColor, RoundedCornerShape(21.dp))
            .clickable { searchFocusRequester.requestFocus() },
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .padding(start = 14.dp, end = 14.dp)
        ) {
            // 放大镜图标：
            //   - 单击 = 关键词搜索（即使搜索框中输入的是网址，点击也纯粹走关键词搜索！）
            //   - 长按 = 解析当前网址并以【淡入效果】弹出较高方块下载弹窗
            Canvas(
                modifier = Modifier
                    .size(18.dp)
                    .pointerInput(searchQuery) {
                        detectTapGestures(
                            onTap = {
                                // 点击只触发关键词搜索，决不弹出解析下载方块
                                onPerformSearch(searchQuery)
                                focusManager.clearFocus()
                            },
                            onLongPress = {
                                // 必须严格校验：只有输入真正满足 skills 资源网址规范时才解析弹窗；
                                // 乱打字或空字符串长按决不假装解析出资源提供下载！
                                val trimmedUrl = searchQuery.trim()
                                if (SkillSearchAndParserEngine.isSkillResourceUrl(trimmedUrl)) {
                                    focusManager.clearFocus()
                                    activeParsedModalUrl = trimmedUrl
                                }
                            }
                        )
                    }
            ) {
                val strokeW = 2.2f.dp.toPx()
                val pColor = Color(0xFF1A1A1A)
                drawCircle(
                    color = pColor,
                    radius = size.width * 0.32f,
                    center = Offset(size.width * 0.42f, size.height * 0.42f),
                    style = Stroke(width = strokeW)
                )
                drawLine(
                    pColor,
                    Offset(size.width * 0.64f, size.height * 0.64f),
                    Offset(size.width * 0.9f, size.height * 0.9f),
                    strokeWidth = strokeW,
                    cap = StrokeCap.Round
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            BasicTextField(
                value = searchQuery,
                onValueChange = { newText ->
                    onSearchQueryChange(newText)
                },
                singleLine = true,
                textStyle = TextStyle(
                    fontFamily = pixelFont,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.W500,
                    color = Color(0xFF111111)
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    // 软键盘回车同样只走关键词搜索
                    onPerformSearch(searchQuery)
                    focusManager.clearFocus()
                }),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(searchFocusRequester)
                    .onFocusChanged { isSearchFocused = it.isFocused },
                decorationBox = { innerTextField ->
                    Box(modifier = Modifier.fillMaxWidth()) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = uiText(UiText.SkillNotchSearchPlaceholder),
                                fontFamily = pixelFont,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.W500,
                                color = Color(0xFF888888)
                            )
                        }
                        innerTextField()
                    }
                }
            )
            if (searchQuery.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEEEEEE))
                        .clickable {
                            onSearchQueryChange("")
                            onPerformSearch("")
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "×",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF555555)
                    )
                }
            }
        }
    }

    // 只有长按搜索图标后才以【淡入效果】弹出较高的方块下载框
    val modalUrl = activeParsedModalUrl
    AutoSkillDownloadModalBox(
        url = modalUrl ?: "",
        visible = modalUrl != null,
        pixelFont = pixelFont,
        onDismiss = {
            activeParsedModalUrl = null
        },
        onDownloadConfirmed = {
            activeParsedModalUrl = null
            onSearchQueryChange("")
            onPerformSearch("")
        }
    )
}
