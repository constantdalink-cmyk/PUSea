package org.example.project

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat

// ==============================================================================
// KeepAliveNotification —— 前台服务的常驻通知（Android 8.0+ 必须先建渠道）。
// 低优先级、无角标、ongoing：用户感知弱，但对系统宣告"我在干活，别杀我"。
// ==============================================================================
object KeepAliveNotification {

    const val CHANNEL_ID = "org.example.project.KEEPALIVE"
    const val NOTIFICATION_ID = 20260214

    fun build(context: Context): Notification {
        ensureChannel(context)
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("后台常驻运行中")
            .setContentText("AI 执行引擎保持在线 · 息屏不掉线")
            // 保护壳图标：接入真实项目时替换为应用自有 smallIcon
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "后台常驻",
                        NotificationManager.IMPORTANCE_LOW
                    ).apply {
                        description = "保持 AI 执行引擎在后台与息屏时存活"
                        setShowBadge(false)
                    }
                )
            }
        }
    }
}
