package org.example.project

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Android actual：画布持久化存储（SharedPreferences 实现，零额外依赖）。
 * 存两块数据：组件管理画布的块列表、工具开关状态 —— App 重启后自动恢复。
 */
@Composable
actual fun rememberCanvasStorage(): CanvasStorage {
    val context = LocalContext.current
    return remember(context) { AndroidCanvasStorage(context) }
}

private class AndroidCanvasStorage(context: Context) : CanvasStorage {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("canvas_persistence", Context.MODE_PRIVATE)

    override fun load(key: String): String? = prefs.getString(key, null)

    override fun save(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }
}
