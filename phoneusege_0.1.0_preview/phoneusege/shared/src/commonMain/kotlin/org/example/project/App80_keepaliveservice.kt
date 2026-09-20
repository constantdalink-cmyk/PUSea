package org.example.project

import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

// ==============================================================================
// KeepAliveService —— 后台常驻的"外壳"（进程保活核心）
//
// 职责：把 ChatEngine（纯执行）装进一个 Android 前台服务里，使其满足：
//   1) 不主动杀后台  → startForeground + 常驻通知，进程升为 perceptible 级；
//   2) 息屏不掉线    → PARTIAL_WAKE_LOCK 持有 CPU，流式/工具执行不被挂起；
//   3) 垂死惊坐起    → START_STICKY（被系统杀掉后自动重建）+ BootReceiver（重启后拉起）。
//
// UI 通过 bindService 拿到 binder，直接调用 launchTask() 发起后台对话，
// 并 collect engine.events / engine.state 驱动界面。
// ==============================================================================
class KeepAliveService : Service() {

    /** 服务自有协程作用域：脱离 UI 生命周期独立存活（SupervisorJob 保证单任务失败不拖垮整体） */
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** 服务持有的执行引擎（前台服务进程里的唯一执行入口）。
     *  默认走 KeyConfigChatStreamProvider → KeyConfigStore 多 Key 调度，与前台行为一致。 */
    val engine = ChatEngine()

    private var wakeLock: PowerManager.WakeLock? = null

    private val binder = LocalBinder()
    inner class LocalBinder : Binder() {
        fun getService(): KeepAliveService = this@KeepAliveService
    }

    companion object {
        const val ACTION_START = "org.example.project.action.START_KEEPALIVE"
        const val ACTION_STOP = "org.example.project.action.STOP_KEEPALIVE"

        private const val WAKELOCK_TAG = "org.example.project:keepalive"

        /** 当前服务是否在前台运行（进程级标记 + 可观察流） */
        @Volatile
        var isRunning = false
            private set

        private val _runningFlow = MutableStateFlow(false)
        val runningFlow: StateFlow<Boolean> = _runningFlow

        /** 从任意位置启动前台服务（自动处理 API 版本差异） */
        fun start(context: Context) {
            val intent = Intent(context, KeepAliveService::class.java).setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /** 主动停止（仅在用户明确要求时调用 —— 我们不主动杀后台） */
        fun stop(context: Context) {
            context.startService(Intent(context, KeepAliveService::class.java).setAction(ACTION_STOP))
        }
    }

    override fun onCreate() {
        super.onCreate()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                START_NOT_STICKY
            }
            else -> {
                // 挂前台通知 = 进程升为 perceptible，系统最后才考虑回收它
                startForeground(
                    KeepAliveNotification.NOTIFICATION_ID,
                    KeepAliveNotification.build(this)
                )
                isRunning = true
                _runningFlow.value = true

                // START_STICKY：进程被系统杀掉后，系统会重新创建本服务 = "垂死病中惊坐起"
                START_STICKY
            }
        }
    }

    /** 供 UI 绑定后发起一轮后台对话任务（返回 Job，便于取消/长按重发） */
    fun launchTask(userMessage: String): Job = serviceScope.launch {
        engine.run(userMessage)
    }

    /** 持有 PARTIAL_WAKE_LOCK：息屏后 CPU 不休眠，保证流式/工具执行不被挂起。
     *  设置 2 小时上限兜底，避免异常情况下长期耗电。 */
    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG).apply {
                setReferenceCounted(false)
                if (!isHeld) acquire(2 * 60 * 60 * 1000L)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        isRunning = false
        _runningFlow.value = false
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        serviceScope.cancel()
        super.onDestroy()
    }
}

// ==============================================================================
// 电池优化白名单引导：绕过系统/厂商的激进省电策略（国产 ROM 杀后台主因）。
// 在 UI 合适时机（如首次启动）调用一次。
// ==============================================================================
fun requestIgnoreBatteryOptimizations(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(context.packageName)) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {
                // 部分 ROM 无此入口，静默失败
            }
        }
    }
}
