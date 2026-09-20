package org.example.project

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.input.OffsetMapping

internal val BgBlack = Color(0xFF000000)
internal val FogWhite = Color.White.copy(alpha = 0.2f)
internal val ElegantGlideEasing = CubicBezierEasing(0.33f, 0.0f, 0.2f, 1.0f)
internal val PeelOffEasing = CubicBezierEasing(0.6f, 0.0f, 0.3f, 1.0f)

internal val NoOffsetMapping = object : OffsetMapping {
    override fun originalToTransformed(offset: Int): Int = 0
    override fun transformedToOriginal(offset: Int): Int = 0
}

/** 提取的扫光绘制逻辑，减少代码重复，避免在 DrawScope 内重复创建对象 */
internal fun DrawScope.drawGlowSweep(
    pathMeasure: PathMeasure,
    segPath: Path,
    sweepAngle: Float,
    glowStroke1: Stroke,
    glowStroke2: Stroke
) {
    if (sweepAngle <= 0f || sweepAngle >= 360f) return

    val totalLen = pathMeasure.length
    val t = sweepAngle / 360f
    val headDist = t * totalLen

    val trailProgress = if (t < 0.5f) t / 0.5f else (1f - t) / 0.5f
    val trailLen = totalLen * 0.5f * trailProgress

    val alphaProgress = if (t < 0.5f) t / 0.5f else (1f - t) / 0.5f
    val fadeFactor = alphaProgress * alphaProgress * (3 - 2 * alphaProgress)

    val steps = 15
    for (i in 0 until steps) {
        val fracStart = i / steps.toFloat()
        val fracEnd = (i + 1) / steps.toFloat()
        val d1 = (headDist - trailLen * (1f - fracStart)).coerceIn(0f, totalLen)
        val d2 = (headDist - trailLen * (1f - fracEnd)).coerceIn(0f, totalLen)
        if (d2 > d1) {
            segPath.reset()
            pathMeasure.getSegment(d1, d2, segPath, true)
            val alpha = fracEnd * fracEnd * fadeFactor
            drawPath(path = segPath, color = Color.White.copy(alpha = alpha * 0.35f), style = glowStroke1)
            drawPath(path = segPath, color = Color.White.copy(alpha = alpha), style = glowStroke2)
        }
    }
}