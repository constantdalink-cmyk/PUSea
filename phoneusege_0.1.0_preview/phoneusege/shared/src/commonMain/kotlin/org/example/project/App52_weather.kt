package org.example.project

// ==============================================================================
// 画布文件（拆分 2/3）：App50_2_plazadrop_weather_canvas.kt —— 9大定版气象与灾害符号核心分发器
//
// 【全套定版天气与灾害符号体系】：
// ① 晴天（Sunny）：纯暖金橙黄大圆（#FFB800）+ 10 个等腰三角平滑匀速自旋；
// ② 多云（Partly Cloudy）：左上 10 三角太阳 + 双层修身云（下沉 +8.8dp 对齐）；
// ③ 阴天（Overcast）：纯前后双层修身三等圆云（后灰云 + 前白云）；
// ④ 下雨（Rain）：双层修身云 + 云底 4 束倾斜手绘雨丝（平滑下落）；
// ⑤ 下雪（Snow）：双层修身云 + 云底 4 朵极简手绘六角雪花（平滑自旋飘落）；
// ⑥ 雨夹雪（Sleet）：双层修身云 + 云底 2 束雨丝与 2 朵六角雪花交错下落；
// ⑦ 冰雹（Hail）/ 台风（Typhoon）/ 地震（Earthquake）/ 云朵与太阳绘制引擎移至 50_3。
// ==============================================================================

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

// ==============================================================================
// 核心绘图组件：全套 9 大天气与灾害手绘符号
// ==============================================================================

@Composable
internal fun PlazaWeatherCanvas(
    mode: PlazaWeatherIconMode,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "weatherAnim")

    // 1. 太阳平滑匀速自旋动画（6秒/圈）
    val sunAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sunAngle"
    )

    // 2. 云朵向上呼吸微浮动（以最低点为基准 floatY <= 0）
    val floatTick by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "floatTick"
    )

    // 3. 雨丝 / 冰雹 / 雪花 / 台风时间流
    val loopTime by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "loopTime"
    )

    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height * 0.54f

        val sunColor = Color(0xFFFFB800)       // 暖金橙黄 #FFB800
        val strokeColor = Color(0xFF1E1E1E)     // 黑色手绘线条
        val backCloudColor = Color(0xFFD8D8D4)  // 后层阴云深浅灰
        val frontCloudColor = Color(0xFFFFFFFF) // 前层主云纯白
        val strokeW = 2.2.dp.toPx()             // 描边加粗，呼应大画幅

        val cloudScale = 1.90f                  // 放大除太阳和地震外的气象符号
        val floatY = (sin(floatTick * 2.0 * Math.PI).toFloat() - 1f) * 4.0.dp.toPx()
        val trimRatio = 0.24f

        // 统一云朵基准坐标（下移至 cy + 30dp，与太阳 cy+2、台风 cy+4、地震 cy+6 严格对齐）
        val precCloudCx = cx - 14.dp.toPx()
        val precCloudCy = cy + 30.dp.toPx() + floatY

        when (mode) {
            PlazaWeatherIconMode.SUNNY -> {
                // ① 晴天（Sunny）
                val sunRadius = 52.dp.toPx()
                drawRotatingSun(
                    sx = cx,
                    sy = cy + 2.dp.toPx(),
                    r = sunRadius,
                    rotDeg = sunAngle,
                    sunColor = sunColor,
                    strokeColor = strokeColor,
                    strokeWidthPx = strokeW
                )
            }
            PlazaWeatherIconMode.PARTLY_CLOUDY -> {
                // ② 多云（Partly Cloudy）
                val sunRadius = 38.dp.toPx()
                val sunX = cx - 32.dp.toPx()
                val sunY = cy - 14.dp.toPx() + floatY * 0.4f

                drawRotatingSun(
                    sx = sunX,
                    sy = sunY,
                    r = sunRadius,
                    rotDeg = sunAngle,
                    sunColor = sunColor,
                    strokeColor = strokeColor,
                    strokeWidthPx = strokeW
                )

                drawTrimThreeCircleCloud(
                    cx = cx + 32.dp.toPx(),
                    cy = cy + 10.dp.toPx() + floatY * 0.6f,
                    scale = cloudScale * 0.88f,
                    fillColor = backCloudColor,
                    strokeColor = strokeColor,
                    strokeWidthPx = strokeW,
                    trimRatio = trimRatio
                )

                drawTrimThreeCircleCloud(
                    cx = cx + 6.dp.toPx(),
                    cy = cy + 32.dp.toPx() + floatY,
                    scale = cloudScale * 1.12f,
                    fillColor = frontCloudColor,
                    strokeColor = strokeColor,
                    strokeWidthPx = strokeW,
                    trimRatio = trimRatio
                )
            }
            PlazaWeatherIconMode.OVERCAST -> {
                // ③ 阴天（Overcast）
                drawTrimThreeCircleCloud(
                    cx = cx + 30.dp.toPx(),
                    cy = cy + 6.dp.toPx() + floatY * 0.6f,
                    scale = cloudScale * 0.88f,
                    fillColor = backCloudColor,
                    strokeColor = strokeColor,
                    strokeWidthPx = strokeW,
                    trimRatio = trimRatio
                )

                drawTrimThreeCircleCloud(
                    cx = cx - 12.dp.toPx(),
                    cy = cy + 31.dp.toPx() + floatY,
                    scale = cloudScale * 1.14f,
                    fillColor = frontCloudColor,
                    strokeColor = strokeColor,
                    strokeWidthPx = strokeW,
                    trimRatio = trimRatio
                )
            }
            PlazaWeatherIconMode.RAIN -> {
                // ④ 下雨（Rain）
                drawTrimThreeCircleCloud(
                    cx = cx + 30.dp.toPx(),
                    cy = cy + 6.dp.toPx() + floatY * 0.6f,
                    scale = cloudScale * 0.88f,
                    fillColor = backCloudColor,
                    strokeColor = strokeColor,
                    strokeWidthPx = strokeW,
                    trimRatio = trimRatio
                )

                drawTrimThreeCircleCloud(
                    cx = precCloudCx,
                    cy = precCloudCy,
                    scale = cloudScale * 1.14f,
                    fillColor = frontCloudColor,
                    strokeColor = strokeColor,
                    strokeWidthPx = strokeW,
                    trimRatio = trimRatio
                )

                drawRainStreaks(
                    cloudCx = precCloudCx,
                    cloudCy = precCloudCy,
                    scale = cloudScale,
                    strokeColor = strokeColor,
                    strokeWidthPx = strokeW,
                    trimRatio = trimRatio,
                    t = loopTime
                )
            }
            PlazaWeatherIconMode.SNOW -> {
                // ⑤ 下雪（Snow）
                drawTrimThreeCircleCloud(
                    cx = cx + 30.dp.toPx(),
                    cy = cy + 6.dp.toPx() + floatY * 0.6f,
                    scale = cloudScale * 0.88f,
                    fillColor = backCloudColor,
                    strokeColor = strokeColor,
                    strokeWidthPx = strokeW,
                    trimRatio = trimRatio
                )

                drawTrimThreeCircleCloud(
                    cx = precCloudCx,
                    cy = precCloudCy,
                    scale = cloudScale * 1.14f,
                    fillColor = frontCloudColor,
                    strokeColor = strokeColor,
                    strokeWidthPx = strokeW,
                    trimRatio = trimRatio
                )

                drawSnowflakes(
                    cloudCx = precCloudCx,
                    cloudCy = precCloudCy,
                    scale = cloudScale,
                    strokeColor = strokeColor,
                    strokeWidthPx = strokeW,
                    trimRatio = trimRatio,
                    t = loopTime
                )
            }
            PlazaWeatherIconMode.SLEET -> {
                // ⑥ 雨夹雪（Sleet）
                drawTrimThreeCircleCloud(
                    cx = cx + 30.dp.toPx(),
                    cy = cy + 6.dp.toPx() + floatY * 0.6f,
                    scale = cloudScale * 0.88f,
                    fillColor = backCloudColor,
                    strokeColor = strokeColor,
                    strokeWidthPx = strokeW,
                    trimRatio = trimRatio
                )

                drawTrimThreeCircleCloud(
                    cx = precCloudCx,
                    cy = precCloudCy,
                    scale = cloudScale * 1.14f,
                    fillColor = frontCloudColor,
                    strokeColor = strokeColor,
                    strokeWidthPx = strokeW,
                    trimRatio = trimRatio
                )

                drawSleet(
                    cloudCx = precCloudCx,
                    cloudCy = precCloudCy,
                    scale = cloudScale,
                    strokeColor = strokeColor,
                    strokeWidthPx = strokeW,
                    trimRatio = trimRatio,
                    t = loopTime
                )
            }
            PlazaWeatherIconMode.HAIL -> {
                // ⑦ 冰雹（Hail）
                drawTrimThreeCircleCloud(
                    cx = cx + 30.dp.toPx(),
                    cy = cy + 6.dp.toPx() + floatY * 0.6f,
                    scale = cloudScale * 0.88f,
                    fillColor = backCloudColor,
                    strokeColor = strokeColor,
                    strokeWidthPx = strokeW,
                    trimRatio = trimRatio
                )

                drawTrimThreeCircleCloud(
                    cx = precCloudCx,
                    cy = precCloudCy,
                    scale = cloudScale * 1.14f,
                    fillColor = frontCloudColor,
                    strokeColor = strokeColor,
                    strokeWidthPx = strokeW,
                    trimRatio = trimRatio
                )

                drawHailstones(
                    cloudCx = precCloudCx,
                    cloudCy = precCloudCy,
                    scale = cloudScale,
                    strokeColor = strokeColor,
                    strokeWidthPx = strokeW,
                    trimRatio = trimRatio,
                    t = loopTime
                )
            }
            PlazaWeatherIconMode.TYPHOON -> {
                // ⑧ 台风（Typhoon）
                drawTyphoon(
                    cx = cx,
                    cy = cy + 4.dp.toPx() + floatY,
                    scale = 1.70f,
                    rotDeg = -loopTime * 105f,
                    strokeColor = strokeColor,
                    strokeWidthPx = strokeW
                )
            }
            PlazaWeatherIconMode.EARTHQUAKE -> {
                // ⑨ 地震（Earthquake）
                drawEarthquake(
                    cx = cx,
                    cy = cy + 6.dp.toPx(),
                    scale = 1.65f,
                    strokeColor = strokeColor,
                    strokeWidthPx = strokeW,
                    t = loopTime
                )
            }
        }
    }
}

// ------------------------------------------------------------------------------
// 手绘雨丝绘制函数
// ------------------------------------------------------------------------------
internal fun DrawScope.drawRainStreaks(
    cloudCx: Float,
    cloudCy: Float,
    scale: Float,
    strokeColor: Color,
    strokeWidthPx: Float,
    trimRatio: Float,
    t: Float
) {
    val dropCount = 4
    val r = 20.dp.toPx() * scale
    val cloudW = 34.dp.toPx() * scale
    val startBaseY = cloudCy + r * trimRatio + 6.dp.toPx()
    val rainLen = 8.dp.toPx() * scale
    val slantX = -3.5.dp.toPx() * scale

    for (i in 0 until dropCount) {
        val relX = -cloudW * 0.42f + (i.toFloat() / (dropCount - 1)) * cloudW * 0.84f
        val phase = ((t * 1.1f + i * 0.28f) % 1.0f + 1.0f) % 1.0f
        val startY = startBaseY + phase * 14.dp.toPx() * scale
        val endY = startY + rainLen
        val startX = cloudCx + relX
        val endX = startX + slantX

        drawLine(
            color = strokeColor,
            start = Offset(startX, startY),
            end = Offset(endX, endY),
            strokeWidth = strokeWidthPx,
            cap = StrokeCap.Round
        )
    }
}

// ------------------------------------------------------------------------------
// 手绘六角雪花绘制函数
// ------------------------------------------------------------------------------
internal fun DrawScope.drawSnowflakes(
    cloudCx: Float,
    cloudCy: Float,
    scale: Float,
    strokeColor: Color,
    strokeWidthPx: Float,
    trimRatio: Float,
    t: Float
) {
    val flakeCount = 4
    val r = 20.dp.toPx() * scale
    val cloudW = 34.dp.toPx() * scale
    val startBaseY = cloudCy + r * trimRatio + 6.dp.toPx()
    val flakeR = 3.2.dp.toPx() * scale

    for (i in 0 until flakeCount) {
        val relX = -cloudW * 0.42f + (i.toFloat() / (flakeCount - 1)) * cloudW * 0.84f
        val phase = ((t * 0.6f + i * 0.25f) % 1.0f + 1.0f) % 1.0f
        val startY = startBaseY + phase * 15.dp.toPx() * scale
        val swayX = sin((t * 1.5f + i * 1.5f).toDouble()).toFloat() * 2.0.dp.toPx() * scale
        val curX = cloudCx + relX + swayX
        val rotDeg = (t * (if (i % 2 == 0) 65f else -65f) + i * 45f) % 360f

        rotate(degrees = rotDeg, pivot = Offset(curX, startY)) {
            for (k in 0 until 3) {
                val aRad = k * (Math.PI / 3.0)
                val cosA = cos(aRad).toFloat()
                val sinA = sin(aRad).toFloat()
                drawLine(
                    color = strokeColor,
                    start = Offset(curX - flakeR * cosA, startY - flakeR * sinA),
                    end = Offset(curX + flakeR * cosA, startY + flakeR * sinA),
                    strokeWidth = strokeWidthPx * 0.82f,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

// ------------------------------------------------------------------------------
// 手绘雨夹雪绘制函数
// ------------------------------------------------------------------------------
internal fun DrawScope.drawSleet(
    cloudCx: Float,
    cloudCy: Float,
    scale: Float,
    strokeColor: Color,
    strokeWidthPx: Float,
    trimRatio: Float,
    t: Float
) {
    val dropCount = 4
    val r = 20.dp.toPx() * scale
    val cloudW = 34.dp.toPx() * scale
    val startBaseY = cloudCy + r * trimRatio + 6.dp.toPx()
    val rainLen = 7.dp.toPx() * scale
    val slantX = -3.dp.toPx() * scale
    val flakeR = 2.8.dp.toPx() * scale

    for (i in 0 until dropCount) {
        val relX = -cloudW * 0.42f + (i.toFloat() / (dropCount - 1)) * cloudW * 0.84f
        val isSnow = i % 2 == 1

        if (isSnow) {
            val phase = ((t * 0.65f + i * 0.32f) % 1.0f + 1.0f) % 1.0f
            val startY = startBaseY + phase * 15.dp.toPx() * scale
            val swayX = sin((t * 1.5f + i * 1.8f).toDouble()).toFloat() * 1.8.dp.toPx() * scale
            val curX = cloudCx + relX + swayX
            val rotDeg = (t * 60f + i * 40f) % 360f

            rotate(degrees = rotDeg, pivot = Offset(curX, startY)) {
                for (k in 0 until 3) {
                    val aRad = k * (Math.PI / 3.0)
                    val cosA = cos(aRad).toFloat()
                    val sinA = sin(aRad).toFloat()
                    drawLine(
                        color = strokeColor,
                        start = Offset(curX - flakeR * cosA, startY - flakeR * sinA),
                        end = Offset(curX + flakeR * cosA, startY + flakeR * sinA),
                        strokeWidth = strokeWidthPx * 0.82f,
                        cap = StrokeCap.Round
                    )
                }
            }
        } else {
            val phase = ((t * 1.1f + i * 0.28f) % 1.0f + 1.0f) % 1.0f
            val startY = startBaseY + phase * 14.dp.toPx() * scale
            val endY = startY + rainLen
            val startX = cloudCx + relX
            val endX = startX + slantX

            drawLine(
                color = strokeColor,
                start = Offset(startX, startY),
                end = Offset(endX, endY),
                strokeWidth = strokeWidthPx,
                cap = StrokeCap.Round
            )
        }
    }
}
