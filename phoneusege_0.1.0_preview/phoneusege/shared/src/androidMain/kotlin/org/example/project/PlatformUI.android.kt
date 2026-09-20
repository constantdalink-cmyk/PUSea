package org.example.project

// ==============================================================================
// PlatformUI.android.kt —— 系统 UI 沉浸式配置（Android 实际实现）
//
// 第二轮修复：本工程 shared 模块 androidMain 未引入 androidx.core 依赖
// （WindowInsetsControllerCompat / WindowCompat / WindowInsetsCompat.Type 全部
//  Unresolved reference），故改用 framework 原生 API，零新增依赖、零构建脚本改动：
//
//   · API 30+（R 及以上）：android.view.WindowInsetsController 原生实现
//       window.setDecorFitsSystemWindows(false)
//           ← 等价 LAYOUT_STABLE | LAYOUT_FULLSCREEN | LAYOUT_HIDE_NAVIGATION
//       insetsController.hide(WindowInsets.Type.systemBars())
//           ← 等价 FULLSCREEN | HIDE_NAVIGATION（状态栏 + 导航栏一起隐藏）
//       systemBarsBehavior = BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
//           ← 等价 IMMERSIVE_STICKY（边缘轻扫短暂浮现、自动收回）
//   · API 29 及以下：回退旧版 flags（行为与最初版本完全一致），
//     废弃警告在私有函数上精确 @Suppress，编译日志干净。
//
// onDispose 还原：30+ 重新 show(systemBars()) 并复位默认行为 + 恢复 decor 适配；
// 29- 还原 SYSTEM_UI_FLAG_VISIBLE。
// ==============================================================================

import android.app.Activity
import android.os.Build
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
actual fun SetupSystemUI(view: Any?) {
    val v = view as? View ?: return
    DisposableEffect(v) {
        enterImmersive(v)
        onDispose { exitImmersive(v) }
    }
}

// ------------------------------------------------------------------------------
// 进入沉浸全屏
// ------------------------------------------------------------------------------

private fun enterImmersive(v: View) {
    val window = (v.context as? Activity)?.window ?: return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        // API 30+：framework 原生 WindowInsetsController（无需 androidx.core）
        // 1. 布局延伸：内容绘制到系统栏之下（等价旧 LAYOUT_* 三兄弟）
        window.setDecorFitsSystemWindows(false)
        // 2. 隐藏状态栏 + 导航栏，并采用 IMMERSIVE_STICKY 等价行为
        window.insetsController?.apply {
            hide(WindowInsets.Type.systemBars())
            systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    } else {
        // API 29 及以下：旧版 flags 兜底，行为与最初实现 1:1
        legacyHideSystemBars(v)
    }
}

// ------------------------------------------------------------------------------
// 退出沉浸：还原系统栏（等价旧版 onDispose 的 SYSTEM_UI_FLAG_VISIBLE）
// ------------------------------------------------------------------------------

private fun exitImmersive(v: View) {
    val window = (v.context as? Activity)?.window ?: return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        window.insetsController?.apply {
            show(WindowInsets.Type.systemBars())
            systemBarsBehavior = WindowInsetsController.BEHAVIOR_DEFAULT
        }
        // 与进入时对称：恢复 decor 对系统栏的适配，避免残留影响其他界面
        window.setDecorFitsSystemWindows(true)
    } else {
        legacyRestoreSystemBars(v)
    }
}

// ------------------------------------------------------------------------------
// API 29 及以下旧版兜底：废弃 API 仅存于这两个函数，警告被精确压制
// ------------------------------------------------------------------------------

@Suppress("DEPRECATION")
private fun legacyHideSystemBars(v: View) {
    v.systemUiVisibility = (
        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
        View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
        View.SYSTEM_UI_FLAG_FULLSCREEN
    )
}

@Suppress("DEPRECATION")
private fun legacyRestoreSystemBars(v: View) {
    v.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
}

// ------------------------------------------------------------------------------
// 时间工具
// ------------------------------------------------------------------------------

actual fun getCurrentTime(): String {
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
}
