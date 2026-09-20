package org.example.project

// ==============================================================================
// 画布文件（拆分 3/3）：App50_3_plazadrop_logos_gestures.kt —— Logo图形、手势拦截器与气象几何绘制引擎
//
// 【施工标准与内容】：
// 1. HOUSE LOGO MARK v1（SOLID 实心挖门）—— 48×48u 网格坐标；
// 2. CRATE LOGO MARK v3.1 —— 48×48u 网格坐标，框环 + 细横线 + 居中下三角；
// 3. HandDrawnSearchIcon —— 搜索框首部极简手绘图标；
// 4. detectStealVerticalDrag —— 竖向手势策略抢占器（仅向上收回手势 acc < 0 判定）；
// 5. 气象几何绘制引擎（从 50_2 迁入）：
//    · drawHailstones（手绘冰雹菱形冰晶）
//    · drawTyphoon（国际标准双旋臂风暴）
//    · drawEarthquake（极简同心震波环）
//    · drawTrimThreeCircleCloud（修身三等圆云朵几何裁切 trimRatio = 0.24）
//    · drawRotatingSun（暖金橙黄 10 等腰三角自旋太阳）
// ==============================================================================

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

// ==============================================================================
// 竖向手势策略抢占器（专属独立命名 detectPlazaDropVerticalDrag，杜绝与 App49 发生 private/重载冲突）
// ==============================================================================

internal suspend fun PointerInputScope.detectPlazaDropVerticalDrag(
    policy: (accumulated: Float, speedPxPerSec: Float, downPosition: Offset) -> Boolean,
    onVerticalDrag: (dragAmount: Float) -> Unit,
    onDragEnd: () -> Unit = {}
) {
    val decisionThreshold = viewConfiguration.touchSlop * 1.0f
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var accumulated = 0f
        var decided = false
        var accepted = false
        var moved = false
        while (true) {
            val event = awaitPointerEvent(pass = PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) break
            val dy = change.position.y - change.previousPosition.y
            if (!decided) {
                accumulated += dy
                if (abs(accumulated) > decisionThreshold) {
                    decided = true
                    val elapsed = (change.uptimeMillis - down.uptimeMillis).coerceAtLeast(1L)
                    val speed = abs(accumulated) * 1000f / elapsed
                    accepted = policy(accumulated, speed, down.position)
                    if (!accepted) break
                    change.consume()
                    moved = true
                    onVerticalDrag(accumulated)
                    continue
                }
            }
            if (accepted && dy != 0f) {
                change.consume()
                moved = true
                onVerticalDrag(dy)
            }
        }
        if (accepted && moved) onDragEnd()
    }
}

// ==============================================================================
// 【施工 · v31】HOUSE LOGO MARK v1 · SOLID —— 下滑画布左上角（屏幕左上角）
// ==============================================================================

@Composable
internal fun HouseLogoMark(
    modifier: Modifier = Modifier,
    ink: Color = Color(0xFF1E1E1E)
) {
    Canvas(modifier = modifier) {
        val s = size.minDimension / 48f

        // 房身剪影（含檐口悬挑）
        val house = Path().apply {
            moveTo(24f * s, 4.5f * s)
            lineTo(2.5f * s, 19f * s)
            lineTo(7.5f * s, 19f * s)
            lineTo(7.5f * s, 42.5f * s)
            lineTo(40.5f * s, 42.5f * s)
            lineTo(40.5f * s, 19f * s)
            lineTo(45.5f * s, 19f * s)
            close()
        }

        // 圆顶门洞
        val door = Path().apply {
            moveTo(21.5f * s, 42.5f * s)
            lineTo(21.5f * s, 31.5f * s)
            quadraticBezierTo(21.5f * s, 29.5f * s, 23.5f * s, 29.5f * s)
            lineTo(24.5f * s, 29.5f * s)
            quadraticBezierTo(26.5f * s, 29.5f * s, 26.5f * s, 31.5f * s)
            lineTo(26.5f * s, 42.5f * s)
            close()
        }

        // EvenOdd 挖切：门洞透出画布底色
        val mark = Path().apply {
            addPath(house)
            addPath(door)
            fillType = PathFillType.EvenOdd
        }

        drawPath(mark, color = ink)
    }
}

// ==============================================================================
// 【施工 · v36】CRATE LOGO MARK v3.1 —— 下滑画布右 1/5×1/5 方块中央
// ==============================================================================

@Composable
internal fun CrateLogoMark(
    modifier: Modifier = Modifier,
    ink: Color = Color(0xFF1E1E1E),
    onClick: (() -> Unit)? = null
) {
    val clickModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick
        )
    } else Modifier

    Canvas(modifier = modifier.then(clickModifier)) {
        val s = size.minDimension / 48f

        // 1. EvenOdd 纤细框环
        val ring = Path().apply {
            addRect(Rect(13f * s, 11f * s, 35f * s, 33f * s))
            addRect(Rect(15f * s, 13f * s, 33f * s, 31f * s))
            fillType = PathFillType.EvenOdd
        }
        drawPath(ring, color = ink)

        // 2. 贯穿细横线（直边平口）
        drawLine(
            color = ink,
            start = Offset(13f * s, 19.5f * s),
            end = Offset(35f * s, 19.5f * s),
            strokeWidth = 1.8f * s,
            cap = StrokeCap.Square
        )

        // 3. 居中下三角形
        val tri = Path().apply {
            moveTo(21f * s, 19.5f * s)
            lineTo(27f * s, 19.5f * s)
            lineTo(24f * s, 25f * s)
            close()
        }
        drawPath(tri, color = ink)
    }
}

// ==============================================================================
// 手绘搜索图标（HandDrawnSearchIcon）由同包 App49 全局统一声明（org.example.project）
// ==============================================================================

// ==============================================================================
// 手绘冰雹绘制函数
// ==============================================================================

internal fun DrawScope.drawHailstones(
    cloudCx: Float,
    cloudCy: Float,
    scale: Float,
    strokeColor: Color,
    strokeWidthPx: Float,
    trimRatio: Float,
    t: Float
) {
    val hailCount = 4
    val r = 20.dp.toPx() * scale
    val cloudW = 34.dp.toPx() * scale
    val startBaseY = cloudCy + r * trimRatio + 6.dp.toPx()
    val hailSize = 2.8.dp.toPx() * scale

    for (i in 0 until hailCount) {
        val relX = -cloudW * 0.42f + (i.toFloat() / (hailCount - 1)) * cloudW * 0.84f
        val phase = ((t * 1.3f + i * 0.31f) % 1.0f + 1.0f) % 1.0f
        val curY = startBaseY + phase * 16.dp.toPx() * scale
        val curX = cloudCx + relX - phase * 2.5.dp.toPx() * scale

        val hailPath = Path().apply {
            moveTo(curX, curY - hailSize * 1.15f)
            lineTo(curX + hailSize * 0.85f, curY)
            lineTo(curX, curY + hailSize * 1.15f)
            lineTo(curX - hailSize * 0.85f, curY)
            close()
        }

        drawPath(path = hailPath, color = Color.White)
        drawPath(
            path = hailPath,
            color = strokeColor,
            style = Stroke(
                width = strokeWidthPx * 0.9f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}

// ==============================================================================
// 手绘台风绘制函数
// ==============================================================================

internal fun DrawScope.drawTyphoon(
    cx: Float,
    cy: Float,
    scale: Float,
    rotDeg: Float,
    strokeColor: Color,
    strokeWidthPx: Float
) {
    val eyeR = 7.5.dp.toPx() * scale
    val armR = 24.dp.toPx() * scale

    rotate(degrees = rotDeg, pivot = Offset(cx, cy)) {
        // 1. 中心风暴眼小圆
        drawCircle(
            color = Color.White,
            radius = eyeR,
            center = Offset(cx, cy)
        )
        drawCircle(
            color = strokeColor,
            radius = eyeR,
            center = Offset(cx, cy),
            style = Stroke(width = strokeWidthPx)
        )

        // 2. 双旋臂螺旋手绘弧
        for (side in 0 until 2) {
            rotate(degrees = side * 180f, pivot = Offset(cx, cy)) {
                val armPath = Path().apply {
                    moveTo(cx, cy - eyeR)
                    cubicTo(
                        cx + armR * 0.45f, cy - eyeR * 1.2f,
                        cx + armR * 0.95f, cy - armR * 0.5f,
                        cx + armR * 0.72f, cy - armR * 0.92f
                    )
                }
                drawPath(
                    path = armPath,
                    color = strokeColor,
                    style = Stroke(
                        width = strokeWidthPx,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )

                // 外围轻快风弧
                val arcPath = Path().apply {
                    arcTo(
                        rect = Rect(cx - armR * 1.18f, cy - armR * 1.18f, cx + armR * 1.18f, cy + armR * 1.18f),
                        startAngleDegrees = -135f,
                        sweepAngleDegrees = 72f,
                        forceMoveTo = true
                    )
                }
                drawPath(
                    path = arcPath,
                    color = strokeColor,
                    style = Stroke(
                        width = strokeWidthPx * 0.85f,
                        cap = StrokeCap.Round
                    )
                )
            }
        }
    }
}

// ==============================================================================
// 手绘地震绘制函数
// ==============================================================================

internal fun DrawScope.drawEarthquake(
    cx: Float,
    cy: Float,
    scale: Float,
    strokeColor: Color,
    strokeWidthPx: Float,
    t: Float
) {
    val shakeX = sin((t * 15f).toDouble()).toFloat() * 0.7.dp.toPx() * scale
    val shakeY = cos((t * 17f).toDouble()).toFloat() * 0.4.dp.toPx() * scale

    val epicenterX = cx + shakeX * 0.6f
    val epicenterY = cy + shakeY * 0.4f

    // 4 圈同心震波环脉冲扩散
    for (i in 0 until 4) {
        val phase = ((t * 0.70f + i.toFloat() / 4f) % 1.0f + 1.0f) % 1.0f
        val r = 5.dp.toPx() * scale + phase * 34.dp.toPx() * scale
        val alpha = (1f - phase).coerceIn(0.02f, 0.90f)

        drawCircle(
            color = strokeColor.copy(alpha = alpha),
            radius = r,
            center = Offset(epicenterX, epicenterY),
            style = Stroke(width = strokeWidthPx * (1.25f - phase * 0.45f))
        )
    }

    // 震中实心黑点
    drawCircle(
        color = strokeColor,
        radius = 3.6.dp.toPx() * scale,
        center = Offset(epicenterX, epicenterY)
    )
}

// ==============================================================================
// 定版：极简修身三等圆标准云朵（trimRatio = 0.24）
// ==============================================================================

internal fun DrawScope.drawTrimThreeCircleCloud(
    cx: Float,
    cy: Float,
    scale: Float,
    fillColor: Color,
    strokeColor: Color,
    strokeWidthPx: Float,
    trimRatio: Float = 0.24f
) {
    val r = 20.dp.toPx() * scale
    val d = 17.dp.toPx() * scale
    val h = 13.2.dp.toPx() * scale
    val b = r * trimRatio

    val angleRBottom = Math.asin((b / r).toDouble().coerceIn(-0.99, 0.99))
    val angleLBottom = Math.PI - angleRBottom

    val distMR = Math.hypot(d.toDouble(), h.toDouble())
    val halfAngleMR = Math.acos((distMR / (2 * r)).coerceIn(-0.99, 0.99))
    val angleRToM = Math.atan2(-h.toDouble(), -d.toDouble())
    val angleRTop = angleRToM + halfAngleMR
    val angleMToR = Math.atan2(h.toDouble(), d.toDouble())
    val angleMRight = angleMToR - halfAngleMR

    val angleMLeft = -Math.PI - angleMRight
    val angleLTop = -Math.PI - angleRTop

    val startX = (cx - d + Math.cos(angleLBottom) * r).toFloat()
    val startY = cy + b
    val endX = (cx + d + Math.cos(angleRBottom) * r).toFloat()

    fun ccwSweep(from: Double, to: Double): Float {
        var delta = to - from
        while (delta > 0) delta -= Math.PI * 2
        return (delta * 180 / Math.PI).toFloat()
    }

    val path = Path().apply {
        moveTo(startX, startY)
        lineTo(endX, startY)
        arcTo(
            rect = Rect(cx + d - r, cy - r, cx + d + r, cy + r),
            startAngleDegrees = (angleRBottom * 180 / Math.PI).toFloat(),
            sweepAngleDegrees = ccwSweep(angleRBottom, angleRTop),
            forceMoveTo = false
        )
        arcTo(
            rect = Rect(cx - r, cy - h - r, cx + r, cy - h + r),
            startAngleDegrees = (angleMRight * 180 / Math.PI).toFloat(),
            sweepAngleDegrees = ccwSweep(angleMRight, angleMLeft),
            forceMoveTo = false
        )
        arcTo(
            rect = Rect(cx - d - r, cy - r, cx - d + r, cy + r),
            startAngleDegrees = (angleLTop * 180 / Math.PI).toFloat(),
            sweepAngleDegrees = ccwSweep(angleLTop, angleLBottom),
            forceMoveTo = false
        )
        close()
    }

    drawPath(path = path, color = fillColor)
    drawPath(
        path = path,
        color = strokeColor,
        style = Stroke(
            width = strokeWidthPx,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )
}

// ==============================================================================
// 定版：暖金橙黄旋转太阳（10 个等腰三角）
// ==============================================================================

internal fun DrawScope.drawRotatingSun(
    sx: Float,
    sy: Float,
    r: Float,
    rotDeg: Float,
    sunColor: Color,
    strokeColor: Color,
    strokeWidthPx: Float
) {
    val triangleCount = 10
    val rayGap = 3.dp.toPx()
    val rayH = r * 0.42f
    val halfBaseAngleRad = (Math.PI / triangleCount) * 0.44

    rotate(degrees = rotDeg, pivot = Offset(sx, sy)) {
        for (i in 0 until triangleCount) {
            val angleRad = (i * 2.0 * Math.PI) / triangleCount

            val tipDist = r + rayGap + rayH
            val baseDist = r + rayGap

            val tipX = sx + (cos(angleRad) * tipDist).toFloat()
            val tipY = sy + (sin(angleRad) * tipDist).toFloat()

            val b1X = sx + (cos(angleRad - halfBaseAngleRad) * baseDist).toFloat()
            val b1Y = sy + (sin(angleRad - halfBaseAngleRad) * baseDist).toFloat()

            val b2X = sx + (cos(angleRad + halfBaseAngleRad) * baseDist).toFloat()
            val b2Y = sy + (sin(angleRad + halfBaseAngleRad) * baseDist).toFloat()

            val trianglePath = Path().apply {
                moveTo(tipX, tipY)
                lineTo(b1X, b1Y)
                lineTo(b2X, b2Y)
                close()
            }

            drawPath(path = trianglePath, color = sunColor)
            drawPath(
                path = trianglePath,
                color = strokeColor,
                style = Stroke(
                    width = strokeWidthPx,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }

    drawCircle(
        color = sunColor,
        radius = r,
        center = Offset(sx, sy)
    )
    drawCircle(
        color = strokeColor,
        radius = r,
        center = Offset(sx, sy),
        style = Stroke(width = strokeWidthPx)
    )
}
