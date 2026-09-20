package org.example.project

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.asImageBitmap

// ==============================================================================
// 【shared/src/androidMain/kotlin/org/example/project/App64_addispatcher.kt】
//
// 只使用纯 android.* API（Context / Uri / BitmapFactory），
// 不引用 androidx.activity.*，因此 shared 模块无需新增任何依赖。
//
// 分工：
//   - 本文件：图片解码 + 选图请求中转（不知道怎么拉起选择器）
//   - MainActivity（androidApp 模块，已有 activity-compose）：
//       负责 registerForActivityResult，并通过 bindLauncher / onPicked 接线
// ==============================================================================

object SkillImagePickerAndroid {

    /** 由 MainActivity 注入：真正拉起系统文件管理器的动作 */
    private var launchAction: (() -> Unit)? = null

    /** 当前等待结果的回调（来自 commonMain 的 SkillImagePicker.pick） */
    private var pendingCallback: ((String) -> Unit)? = null

    private var appContext: Context? = null

    /**
     * 第一步：注入图片解码能力（在 MainActivity.onCreate 里调用）
     * 让 content:// / file:// / 绝对路径 的本地图片能被渲染出来
     */
    fun initializeDecoder(context: Context) {
        val ctx = context.applicationContext
        appContext = ctx

        SkillLocalImageLoader.loader = { path ->
            if (path.isBlank()) {
                null
            } else {
                try {
                    val stream = if (path.startsWith("content://") || path.startsWith("file://")) {
                        ctx.contentResolver.openInputStream(Uri.parse(path))
                    } else {
                        java.io.FileInputStream(path)
                    }
                    stream?.use { input ->
                        val bmp = BitmapFactory.decodeStream(input)
                        bmp?.asImageBitmap()
                    }
                } catch (e: Throwable) {
                    null
                }
            }
        }
    }

    /**
     * 第二步：绑定"拉起系统文件管理器"的具体动作（由 MainActivity 传入 launcher.launch）
     */
    fun bindLauncher(action: () -> Unit) {
        launchAction = action

        // 绑定后，commonMain 的选图请求才真正生效
        SkillImagePicker.handler = { onPicked ->
            val act = launchAction
            if (act == null) {
                onPicked("")
            } else {
                pendingCallback = onPicked
                try {
                    act()
                } catch (e: Throwable) {
                    pendingCallback = null
                    onPicked("")
                }
            }
        }
    }

    /**
     * 第三步：MainActivity 在 ActivityResult 回调里调用，把选中的 uri 回传
     * 用户取消时传空串，UI 侧会保持原封面不变
     */
    fun onPicked(uriString: String) {
        val callback = pendingCallback
        pendingCallback = null
        callback?.invoke(uriString)
    }

    /** Activity 销毁时释放引用 */
    fun release() {
        pendingCallback = null
        launchAction = null
        appContext = null
        SkillImagePicker.handler = null
        SkillLocalImageLoader.loader = null
    }
}
