package org.example.project

data class MessageData(
    val id: Long,
    val text: String,
    val time: String,
    val heightDp: Float,
    val isLanded: Boolean = true,
    val isUser: Boolean = true,
    val isThinking: Boolean = false,
    val skipRevealAnimation: Boolean = false
)

internal class FlightMetricsCache(
    val startXPx: Float, val startYPx: Float, val startWPx: Float, val startHPx: Float,
    val endXPx: Float, val endYPx: Float, val endWPx: Float, val endHPx: Float,
    val arcOffsetMax: Float, val cornerRadiusPx: Float
)