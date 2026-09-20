package org.example.project

// ==============================================================================
// 画布文件 9/10：subcanvascredits.kt —— 致谢画布
// 职责：SubCanvasCredits 致谢子画布（包含团队制作人员、技术框架、内测用户、AI辅助与特别鸣谢）
// 特性：
//   1. 纯白背景画布，高清晰超大号高对比度文字排版
//   2. 顶部常显固定“致谢”声明横幅（上显，不随内容滚动滑走）
//   3. 左右两侧时不时（低频俏皮 biu~ biu~）甩出单个缤纷几何图形
//      【性能优化：采用硬件 VSYNC withFrameNanos + 原地零GC粒子池，彻底消除卡顿与掉帧】
//   4. 自动平滑向上循环滚动致谢字幕，到末尾平滑停止
//   5. 底部配有向上的箭头符号按钮（▲），点击一下整个画布向上滑出回到主页
//   6. 特别鸣谢卡片恢复精巧尺寸，末尾团队字幕署名 ZKYL @2026
// 依赖：subcanvasdispatcher.kt（SlideDownContainer，同包直接调用）
// ==============================================================================
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.random.Random

// 几何图形类型
private enum class GeometricShapeType {
    SQUARE,
    CIRCLE,
    TRIANGLE,
    DIAMOND,
    CROSS
}

// 甩出的几何粒子数据模型（低频 单体冲刺 biu~ 效果）
private class FlingShape(
    val id: Long,
    var x: Float,
    var y: Float,
    var vx: Float,        // X轴速度
    var vy: Float,        // Y轴初速度
    val size: Float,
    var rotation: Float,
    val rotSpeed: Float,
    val color: Color,
    val shapeType: GeometricShapeType,
    val isFilled: Boolean,
    var alpha: Float = 0.95f
)

@Composable
fun SubCanvasCredits(visible: Boolean, onClose: () -> Unit, pixelFont: FontFamily) {
    SlideDownContainer(visible, uiText(UiText.Credits), onClose, pixelFont) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
        ) {
            // 背景层：左右两侧时不时 biu 一个出来的几何图形（硬件VSYNC零卡顿驱动）
            GeometricShapesFlingBackground()

            // 主内容层：顶部常显声明 + 滚动超大字幕
            Column(modifier = Modifier.fillMaxSize()) {
                // 顶部固定常显致谢声明（上显）
                TopCreditsHeader(pixelFont = pixelFont)

                // 滚动字幕主体
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    CreditsContent(onClose = onClose, pixelFont = pixelFont)
                }
            }
        }
    }
}

/**
 * 顶部常显的致谢画布声明上显栏
 */
@Composable
private fun TopCreditsHeader(pixelFont: FontFamily) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF1F5F9))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = uiText(UiText.CreditsCanvasHeader),
            fontFamily = pixelFont,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF0F172A),
            textAlign = TextAlign.Center
        )
    }
}

/**
 * 左右两侧低频、间隔性甩出几何图形（biu ------ biu ------）
 */
@Composable
private fun GeometricShapesFlingBackground() {
    var renderTick by remember { mutableStateOf(0L) }
    val activeShapes = remember { ArrayList<FlingShape>(8) }
    val reusablePath = remember { Path() }

    val colorPalette = remember {
        listOf(
            Color(0xFFFF2D55), // 鲜明红
            Color(0xFFFF9500), // 活力橙
            Color(0xFFFFCC00), // 明黄
            Color(0xFF007AFF), // 科技蓝
            Color(0xFF34C759), // 翠绿
            Color(0xFFAF52DE), // 靓紫
            Color(0xFF5856D6), // 靛蓝
            Color(0xFFFF2D92)  // 玫粉
        )
    }

    val shapeTypes = remember {
        listOf(
            GeometricShapeType.SQUARE,
            GeometricShapeType.CIRCLE,
            GeometricShapeType.TRIANGLE,
            GeometricShapeType.DIAMOND,
            GeometricShapeType.CROSS
        )
    }

    // 硬件 VSYNC 帧驱动循环
    LaunchedEffect(Unit) {
        var lastTimeNanos = 0L
        var nextShapeId = 0L
        var elapsedSinceSpawnNanos = 0L
        var spawnCooldownNanos = Random.nextLong(1_400_000_000L, 2_800_000_000L) // 1.4s ~ 2.8s biu 一个

        while (isActive) {
            withFrameNanos { frameTimeNanos ->
                if (lastTimeNanos == 0L) {
                    lastTimeNanos = frameTimeNanos
                }
                val dtNanos = frameTimeNanos - lastTimeNanos
                lastTimeNanos = frameTimeNanos
                val dt = (dtNanos / 1_000_000_000f).coerceIn(0f, 0.05f)

                // 1. 发射计时：低频 biu~ 一个
                elapsedSinceSpawnNanos += dtNanos
                if (elapsedSinceSpawnNanos >= spawnCooldownNanos) {
                    elapsedSinceSpawnNanos = 0L
                    spawnCooldownNanos = Random.nextLong(1_400_000_000L, 2_800_000_000L)

                    val fromLeft = Random.nextBoolean()
                    val startX = if (fromLeft) -40f else 1150f
                    val startY = Random.nextFloat() * 1600f + 100f
                    val vx = if (fromLeft) Random.nextFloat() * 240f + 360f else -(Random.nextFloat() * 240f + 360f)
                    val vy = (Random.nextFloat() - 0.5f) * 160f
                    val size = Random.nextFloat() * 28f + 24f
                    val shape = shapeTypes.random()
                    val color = colorPalette.random()
                    val isFilled = Random.nextBoolean()

                    if (activeShapes.size < 6) {
                        activeShapes.add(
                            FlingShape(
                                id = nextShapeId++,
                                x = startX,
                                y = startY,
                                vx = vx,
                                vy = vy,
                                size = size,
                                rotation = Random.nextFloat() * 360f,
                                rotSpeed = (Random.nextFloat() - 0.5f) * 320f,
                                color = color,
                                shapeType = shape,
                                isFilled = isFilled,
                                alpha = 0.88f
                            )
                        )
                    }
                }

                // 2. 原地更新物理粒子，零 GC 内存分配
                if (activeShapes.isNotEmpty()) {
                    val iterator = activeShapes.iterator()
                    while (iterator.hasNext()) {
                        val shape = iterator.next()
                        shape.x += shape.vx * dt
                        shape.y += shape.vy * dt
                        shape.vx *= (1f - 0.35f * dt)
                        shape.rotation += shape.rotSpeed * dt
                        shape.alpha -= 0.22f * dt

                        if (shape.alpha <= 0.02f || shape.x < -150f || shape.x > 1400f || shape.y < -150f || shape.y > 2400f) {
                            iterator.remove()
                        }
                    }
                }

                renderTick = frameTimeNanos
            }
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        @Suppress("UNUSED_VARIABLE")
        val tick = renderTick

        val strokeWidth = 3.dp.toPx()
        val fillStyle = Fill
        val strokeStyle = Stroke(width = strokeWidth)

        for (i in 0 until activeShapes.size) {
            val shape = activeShapes.getOrNull(i) ?: continue
            val drawColor = shape.color.copy(alpha = shape.alpha.coerceIn(0f, 1f))
            val half = shape.size / 2f
            val style = if (shape.isFilled) fillStyle else strokeStyle

            rotate(degrees = shape.rotation, pivot = Offset(shape.x, shape.y)) {
                when (shape.shapeType) {
                    GeometricShapeType.SQUARE -> {
                        drawRect(
                            color = drawColor,
                            topLeft = Offset(shape.x - half, shape.y - half),
                            size = Size(shape.size, shape.size),
                            style = style
                        )
                    }

                    GeometricShapeType.CIRCLE -> {
                        drawCircle(
                            color = drawColor,
                            radius = half,
                            center = Offset(shape.x, shape.y),
                            style = style
                        )
                    }

                    GeometricShapeType.TRIANGLE -> {
                        reusablePath.reset()
                        reusablePath.moveTo(shape.x, shape.y - half)
                        reusablePath.lineTo(shape.x + half, shape.y + half)
                        reusablePath.lineTo(shape.x - half, shape.y + half)
                        reusablePath.close()
                        drawPath(
                            path = reusablePath,
                            color = drawColor,
                            style = style
                        )
                    }

                    GeometricShapeType.DIAMOND -> {
                        reusablePath.reset()
                        reusablePath.moveTo(shape.x, shape.y - half * 1.3f)
                        reusablePath.lineTo(shape.x + half * 1.3f, shape.y)
                        reusablePath.lineTo(shape.x, shape.y + half * 1.3f)
                        reusablePath.lineTo(shape.x - half * 1.3f, shape.y)
                        reusablePath.close()
                        drawPath(
                            path = reusablePath,
                            color = drawColor,
                            style = style
                        )
                    }

                    GeometricShapeType.CROSS -> {
                        val thickness = 4.dp.toPx()
                        drawRect(
                            color = drawColor,
                            topLeft = Offset(shape.x - half, shape.y - thickness / 2),
                            size = Size(shape.size, thickness)
                        )
                        drawRect(
                            color = drawColor,
                            topLeft = Offset(shape.x - thickness / 2, shape.y - half),
                            size = Size(thickness, shape.size)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CreditsContent(onClose: () -> Unit, pixelFont: FontFamily) {
    val scrollState = rememberScrollState()

    // 自动平滑滚动：基于 VSYNC 逐帧平滑递增，滚动到最后停止
    LaunchedEffect(Unit) {
        delay(600)
        var lastTimeNanos = 0L

        while (isActive) {
            withFrameNanos { frameTimeNanos ->
                if (lastTimeNanos == 0L) {
                    lastTimeNanos = frameTimeNanos
                }
                val dt = ((frameTimeNanos - lastTimeNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
                lastTimeNanos = frameTimeNanos

                if (scrollState.maxValue > 0) {
                    if (scrollState.value < scrollState.maxValue) {
                        scrollState.dispatchRawDelta(60f * dt)
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(36.dp)
    ) {
        // 核心制作与设计 (超大字号清晰深色字幕)
        CreditSectionBlock(
            title = uiText(UiText.CreditsProductionDesign),
            items = listOf(
                uiText(UiText.CreditsProducerCreator) to uiText(UiText.CreditsNameConstantDalink),
                uiText(UiText.CreditsLeadSystemDesigner) to uiText(UiText.CreditsNameConstantDalink),
                uiText(UiText.CreditsLeadArtistConcept) to uiText(UiText.CreditsNameConstantDalink)
            ),
            pixelFont = pixelFont
        )

        // 开发与工程 (超大字号清晰深色字幕)
        CreditSectionBlock(
            title = uiText(UiText.CreditsEngineeringDev),
            items = listOf(
                uiText(UiText.CreditsLeadProgrammer) to uiText(UiText.CreditsNameConstantDalink),
                uiText(UiText.CreditsClientUiDev) to (uiText(UiText.CreditsNameConstantDalink) + "\n" + uiText(UiText.CreditsNameChanShi)),
                uiText(UiText.CreditsBackendServices) to uiText(UiText.CreditsNameConstantDalink)
            ),
            pixelFont = pixelFont
        )

        // 技术栈与引擎框架 (超大字号清晰深色字幕)
        CreditSectionBlock(
            title = uiText(UiText.CreditsTechStackFramework),
            items = listOf(
                uiText(UiText.CreditsEngineFramework) to "Kotlin, Jetpack Compose, Android"
            ),
            pixelFont = pixelFont
        )

        // 视觉、字体与本地化 (超大字号清晰深色字幕)
        CreditSectionBlock(
            title = uiText(UiText.CreditsVisualsLocalization),
            items = listOf(
                uiText(UiText.CreditsFontDesignUsage) to uiText(UiText.CreditsNameLin),
                uiText(UiText.CreditsTranslation) to uiText(UiText.CreditsNameConstantDalink)
            ),
            pixelFont = pixelFont
        )

        // 质量保证与内测用户 (超大字号清晰深色字幕)
        CreditSectionBlock(
            title = uiText(UiText.CreditsTestingQa),
            items = listOf(
                uiText(UiText.CreditsBetaUsersBugHunters) to (uiText(UiText.CreditsNameChanShi) + "\n" + uiText(UiText.CreditsNameShijie))
            ),
            pixelFont = pixelFont
        )

        // 技术支持与 AI 辅助 (超大字号清晰深色字幕)
        CreditSectionBlock(
            title = uiText(UiText.CreditsTechSupportAi),
            items = listOf(
                uiText(UiText.CreditsTechSupport) to "AI",
                uiText(UiText.CreditsAiAssistanceStatement) to "Gemini, Claude, GPT, Deepseek, Qwen, Kimi, Grok, ox"
            ),
            pixelFont = pixelFont
        )

        // 亲友 / 导师 (超大字号清晰深色字幕)
        CreditSectionBlock(
            title = uiText(UiText.CreditsFamilyMentors),
            items = listOf(
                uiText(UiText.CreditsFamilyMentorRole) to uiText(UiText.CreditsNameZhengquan)
            ),
            pixelFont = pixelFont
        )

        // 特别致谢寄语卡片（精巧尺寸，白底优雅浅暖色调）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFFFFBEB), shape = RoundedCornerShape(10.dp))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = uiText(UiText.CreditsSpecialThanksTitle),
                    fontFamily = pixelFont,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFD97706),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = uiText(UiText.CreditsSpecialThanksContent),
                    fontFamily = pixelFont,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF334155),
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
            }
        }

        // 最后的团队字幕
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "ZKYL @2026",
                fontFamily = pixelFont,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF64748B),
                textAlign = TextAlign.Center
            )
        }

        // 底部向上箭头符号（仅保留纯粹的箭头按钮）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp, bottom = 48.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "▲",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF64748B),
                textAlign = TextAlign.Center,
                modifier = Modifier.clickable(onClick = onClose)
            )
        }
    }
}

@Composable
private fun CreditSectionBlock(
    title: String,
    items: List<Pair<String, String>>,
    pixelFont: FontFamily
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 分类大标题
        Text(
            text = "— $title —",
            fontFamily = pixelFont,
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFF0284C7),
            textAlign = TextAlign.Center,
            lineHeight = 34.sp
        )

        Spacer(modifier = Modifier.height(18.dp))

        items.forEach { (role, name) ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 职责职位大字幕
                Text(
                    text = role,
                    fontFamily = pixelFont,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF64748B),
                    textAlign = TextAlign.Center,
                    lineHeight = 28.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                // 姓名人员超大字幕（纯白底上极清晰的深色黑体）
                Text(
                    text = name,
                    fontFamily = pixelFont,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF0F172A),
                    textAlign = TextAlign.Center,
                    lineHeight = 44.sp
                )
            }
        }
    }
}
