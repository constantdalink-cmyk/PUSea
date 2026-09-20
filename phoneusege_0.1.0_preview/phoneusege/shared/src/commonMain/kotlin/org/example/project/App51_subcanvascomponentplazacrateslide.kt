package org.example.project

// ==============================================================================
// 画布文件（新增）：subcanvascomponentplazacrateslide.kt —— 箱子侧滑圆角子画布
//
// 【隶属广场画布，不碰分发中心】；对应工程里的 App51 文件。
//
// 【交互与设计规范】：
// 1. 触发入口：用户点击下滑天气画布右 1/5×1/5 方块内的【箱子图标（CrateLogoMark）】；
// 2. 动效呈现：从屏幕右侧向左侧平滑伸出新画布（占屏 78% 宽度，全高贴右，左上/左下 28dp 大圆角）；
// 3. 交互闭环：
//    · 向右滑动跟手拖拽收回，滑动过半松手自动吸附；
//    · 点击左侧半透明暗黑遮罩快速关闭；
//    · 顶部左把手与独立关闭按钮；
// 4. 内容结构（箱子工具箱）：
//    · 头部：手绘箱子图标大图 + "CRATE VAULT" 品牌标题；
//    · 中部：快捷工具包清单（MCP 包裹分发、快速提取、离线包导入）；
//    · 底部：一键打包导出与清空。
// 5. 挂载方式：在广场画布 SlideDownContainer 内容末尾追加：
//        PlazaCrateSlideOverlay(pixelFont = pixelFont)
//
// 依赖：同包 PlazaStore / CrateLogoMark / GreenPeelOffEasing（外部符号）
// ==============================================================================

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ==============================================================================
// 箱子抽屉全局联动控制器（供 App50 箱子图标点击唤出）
// ==============================================================================

object PlazaCrateDrawerCoordinator {
    var isOpen by mutableStateOf(false)
        private set

    fun open() {
        isOpen = true
    }

    fun close() {
        isOpen = false
    }

    fun toggle() {
        isOpen = !isOpen
    }
}

// ==============================================================================
// 主组件：右侧向左滑入的圆角新画布（占屏 78% 宽，全高贴右）
// ==============================================================================

@Composable
internal fun PlazaCrateSlideOverlay(
    pixelFont: FontFamily,
    drawerWidthFraction: Float = 1.0f
) {
    val slideAnim = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    // 监听抽屉控制器的打开/关闭指令（毫秒级双向同步）
    LaunchedEffect(PlazaCrateDrawerCoordinator.isOpen) {
        slideAnim.animateTo(
            targetValue = if (PlazaCrateDrawerCoordinator.isOpen) 1f else 0f,
            animationSpec = tween(320, easing = GreenPeelOffEasing)
        )
    }

    fun settle(toOpen: Boolean) {
        if (toOpen) PlazaCrateDrawerCoordinator.open() else PlazaCrateDrawerCoordinator.close()
    }

    // 当抽屉打开或处于滑行动画中时呈现，占满全屏，向右拖拽顺畅收起
    if (slideAnim.value > 0.001f || PlazaCrateDrawerCoordinator.isOpen) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(100f)
        ) {
            var drawerWPx by remember { mutableStateOf(0f) }
            val density = androidx.compose.ui.platform.LocalDensity.current
            val fallbackDrawerWPx = with(density) { maxWidth.toPx() }

            // 全屏滑出的主画布主体（占据整个屏幕 100% 宽度）
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .align(Alignment.CenterEnd)
                    .onSizeChanged { drawerWPx = it.width.toFloat() }
                    .graphicsLayer {
                        val w = if (drawerWPx > 0f) drawerWPx else fallbackDrawerWPx
                        translationX = (1f - slideAnim.value) * w
                    }
                    .background(Color(0xFFFBFBFA))
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                val cur = slideAnim.value
                                if (cur < 0.65f) settle(false) else settle(true)
                            }
                        ) { change, dragAmount ->
                            change.consume()
                            val w = if (drawerWPx > 0f) drawerWPx else fallbackDrawerWPx
                            val next = (slideAnim.value - dragAmount / w).coerceIn(0f, 1f)
                            scope.launch { slideAnim.snapTo(next) }
                        }
                    }
                    .zIndex(10f)
            ) {
                CrateDrawerContent(
                    pixelFont = pixelFont,
                    onClose = { PlazaCrateDrawerCoordinator.close() }
                )
            }
        }
    }
}

// ==============================================================================
// 箱子全屏画布内部组件：
// 1. 放大版 CLOSE 按钮（左上角）；
// 2. 无黑块包裹纯净 CrateLogoMark（右上角，与下滑画布同款风格）；
// 3. 通栏灰色横线；
// 4. 两个栏目切换与资源列表：【已安装】与【正在安装】。
// ==============================================================================

private enum class CrateTabSection {
    INSTALLED,  // 已安装
    INSTALLING  // 正在安装
}

@Composable
private fun CrateDrawerContent(
    pixelFont: FontFamily,
    onClose: () -> Unit
) {
    var activeTab by remember { mutableStateOf(CrateTabSection.INSTALLED) }

    // 从全局数据层 PlazaStore 实时读取真实已安装与正在安装的任务队列
    val allTools = PlazaStore.allTools
    val installedTools = (PlazaStore.getInstalledTools() + allTools.filter { PlazaStore.isInstalled(it.id) }).distinctBy { it.id }
    val activeTasks = PlazaStore.installTasks

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFBFBFA))
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        // =============================================================
        // 顶部操作栏：放大版 CLOSE 设计（左）+ 无黑块纯净箱子图标（右上角）
        // =============================================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // 【放大版 CLOSE 按钮】：加大手柄圆圈与字号（› 和 CLOSE 均继续上调 0.5dp，至 -0.8dp）
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .offset(y = (-0.8).dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onClose() }
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(0xFFEDEDED), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "›",
                        color = Color(0xFF1E1E1E),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.offset(y = (-0.8).dp)
                    )
                }
                Text(
                    text = "CLOSE",
                    color = Color(0xFF1E1E1E),
                    fontSize = 13.sp,
                    fontFamily = pixelFont,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.offset(y = (-0.8).dp)
                )
            }

            // 【右上角箱子图标】：无黑块包裹，纯净底色直接呈现（与下滑画布右上角一致）
            Box(
                modifier = Modifier.size(38.dp),
                contentAlignment = Alignment.Center
            ) {
                CrateLogoMark(
                    modifier = Modifier.size(32.dp),
                    ink = Color(0xFF1E1E1E)
                )
            }
        }

        // =============================================================
        // 通栏灰色横线
        // =============================================================
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.5.dp)
                .background(Color(0xFFE2E2DF))
        )

        Spacer(modifier = Modifier.height(16.dp))

        // =============================================================
        // 栏目切换栏：【INSTALLED】与【INSTALLING】（双栏各占 50% 居中排版，零多余分区）
        // =============================================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧 50% 居中：INSTALLED 栏目标签
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { activeTab = CrateTabSection.INSTALLED }
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "INSTALLED",
                        color = if (activeTab == CrateTabSection.INSTALLED) Color(0xFF1E1E1E) else Color(0xFF888885),
                        fontSize = 13.5.sp,
                        fontFamily = pixelFont,
                        fontWeight = if (activeTab == CrateTabSection.INSTALLED) FontWeight.Bold else FontWeight.Normal
                    )
                    Box(
                        modifier = Modifier
                            .background(
                                color = if (activeTab == CrateTabSection.INSTALLED) Color(0xFF1E1E1E) else Color(0xFFEDEDED),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${installedTools.size}",
                            color = if (activeTab == CrateTabSection.INSTALLED) Color(0xFFFBFBFA) else Color(0xFF757575),
                            fontSize = 9.5.sp,
                            fontFamily = pixelFont,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.55f)
                        .height(2.5.dp)
                        .background(
                            if (activeTab == CrateTabSection.INSTALLED) Color(0xFF1E1E1E) else Color.Transparent,
                            RoundedCornerShape(2.dp)
                        )
                )
            }

            // 右侧 50% 居中：INSTALLING 栏目标签（显示真实安装任务数）
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { activeTab = CrateTabSection.INSTALLING }
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "INSTALLING",
                        color = if (activeTab == CrateTabSection.INSTALLING) Color(0xFF1E1E1E) else Color(0xFF888885),
                        fontSize = 13.5.sp,
                        fontFamily = pixelFont,
                        fontWeight = if (activeTab == CrateTabSection.INSTALLING) FontWeight.Bold else FontWeight.Normal
                    )
                    Box(
                        modifier = Modifier
                            .background(
                                color = if (activeTab == CrateTabSection.INSTALLING) Color(0xFF1E1E1E) else Color(0xFFEDEDED),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${activeTasks.size}",
                            color = if (activeTab == CrateTabSection.INSTALLING) Color(0xFFFBFBFA) else Color(0xFF757575),
                            fontSize = 9.5.sp,
                            fontFamily = pixelFont,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.55f)
                        .height(2.5.dp)
                        .background(
                            if (activeTab == CrateTabSection.INSTALLING) Color(0xFF1E1E1E) else Color.Transparent,
                            RoundedCornerShape(2.dp)
                        )
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // =============================================================
        // 栏目内容列表：呈现已安装与正在安装的工具资源包
        // =============================================================
        when (activeTab) {
            CrateTabSection.INSTALLED -> {
                if (installedTools.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "NO PACKAGES INSTALLED",
                                color = Color(0xFFA5A5A2),
                                fontSize = 11.sp,
                                fontFamily = pixelFont,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Download crates from the Tool Plaza",
                                color = Color(0xFF888885),
                                fontSize = 11.5.sp
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(installedTools, key = { it.id }) { tool ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.White, RoundedCornerShape(10.dp))
                                    .border(1.dp, Color(0xFFE8E8E5), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    // 仅在文件 51 内部严格隔离的 34dp 预留方块槽位，完全不影响外部公共 Loader 文件
                                    CrateToolIconSlot(tool = tool)

                                    Column {
                                        Text(
                                            text = tool.name,
                                            color = Color(0xFF1E1E1E),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = tool.sourceRegistry.ifBlank { "Official MCP Package" },
                                            color = Color(0xFF888885),
                                            fontSize = 10.sp,
                                            maxLines = 1
                                        )
                                    }
                                }

                                // 已安装就绪标签（刚性锁定 64dp×28dp 绝对长高，完全不随文字变动）
                                Box(
                                    modifier = Modifier
                                        .requiredWidth(64.dp)
                                        .requiredHeight(28.dp)
                                        .background(Color(0xFF3DDC84), RoundedCornerShape(6.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "✓",
                                        color = Color(0xFF1E1E1E),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }

            CrateTabSection.INSTALLING -> {
                if (activeTasks.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "NO ACTIVE INSTALLS",
                                color = Color(0xFFA5A5A2),
                                fontSize = 11.sp,
                                fontFamily = pixelFont,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "All packages are synchronized",
                                color = Color(0xFF888885),
                                fontSize = 11.5.sp
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(activeTasks, key = { it.tool.id }) { task ->
                            InstallingToolItemRow(
                                task = task,
                                pixelFont = pixelFont
                            )
                        }
                    }
                }
            }


        }
    }
}

// ==============================================================================
// 正在安装单项组件（真实任务数据驱动）：
// 1. 点一下 INSTA... 切换暂停 / 恢复；
// 2. 暂停时：字白背黑（Text White, Background #1E1E1E）；
// 3. 下载进行时：百分比从当前黄色 (#FFB800) 平滑过渡到标准安卓绿 (#3DDC84)；
// 4. 实时反映后台下载状态信息与错误重试。
// ==============================================================================

@Composable
private fun InstallingToolItemRow(
    task: PlazaInstallTask,
    pixelFont: FontFamily
) {
    val tool = task.tool
    val percent = (task.progress * 100).toInt().coerceIn(0, 100)

    // 颜色渐变算法：从黄色 (#FFB800) 到标准安卓绿 (#3DDC84)
    val dynamicColor = remember(task.progress) {
        val f = task.progress.coerceIn(0f, 1f)
        val r = (0xFF * (1f - f) + 0x3D * f) / 255f
        val g = (0xB8 * (1f - f) + 0xDC * f) / 255f
        val b = (0x00 * (1f - f) + 0x84 * f) / 255f
        Color(red = r, green = g, blue = b)
    }

    // 状态判定：失败时字白背红 ('✕')；暂停时字白背黑；下载时黄至绿渐变；完成时安卓绿 ('✓')
    val badgeBgColor = when {
        task.isFailed -> Color(0xFFE53935) // 安装失败：鲜明红
        task.isPaused -> Color(0xFF1E1E1E) // 暂停：纯黑
        percent >= 100 -> Color(0xFF3DDC84) // 完成：标准安卓绿
        else -> dynamicColor // 下载中：黄到绿动态渐变
    }
    val badgeTextColor = when {
        task.isFailed || task.isPaused -> Color(0xFFFFFFFF) // 失败与暂停：纯白文字
        else -> Color(0xFF1E1E1E) // 下载中与完成：深色文字
    }
    val badgeText = when {
        task.isFailed -> "✕ RETRY"
        task.isPaused -> "PAUSED $percent%"
        percent >= 100 -> "✓"
        else -> "$percent% INSTA..."
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(10.dp))
            .border(1.dp, Color(0xFFE8E8E5), RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            // 仅在文件 51 内部严格隔离的 34dp 预留方块槽位，完全不影响外部公共 Loader 文件
            CrateToolIconSlot(tool = tool)

            Column {
                Text(
                    text = tool.name,
                    color = Color(0xFF1E1E1E),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (task.isPaused) "Download paused by user" else task.statusMessage,
                    color = if (task.isPaused) Color(0xFF9E9E9E) else if (task.isFailed) Color(0xFFE53935) else Color(0xFF888885),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // =============================================================
        // 点一下切换暂停 / 恢复 / 失败重试按钮
        // 1. 刚性锁定 96dp×28dp 绝对长高，尺寸完全不随字幕变化；
        // 2. 失败时：字白背红 ('✕ RETRY')；暂停时：字白背黑；下载时：黄至绿渐变；完成时：安卓绿 ('✓')。
        // =============================================================
        Box(
            modifier = Modifier
                .requiredWidth(96.dp)
                .requiredHeight(28.dp)
                .background(badgeBgColor, RoundedCornerShape(6.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    PlazaStore.togglePauseInstall(tool.id)
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = badgeText,
                color = badgeTextColor,
                fontSize = if (badgeText == "✓") 14.sp else 9.sp,
                fontFamily = pixelFont,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ==============================================================================
// 文件 51 专属局部隔离组件：预留 34dp×34dp 方块槽位
// 1. 严格作为 App51 私有私有组件，绝不导出或修改公共 Loader；
// 2. 预先占据 34dp 刚性位置与浅灰圆角底色 (#EDEDEA)，图片无论在加载中还是加载完成均不引起框抖动。
// ==============================================================================

@Composable
private fun CrateToolIconSlot(
    tool: PlazaToolItem,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(34.dp)
            .background(Color(0xFFEDEDEA), RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center
    ) {
        SquareImageLoader(tool = tool, isDarkBg = false)
    }
}
