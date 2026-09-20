package org.example.project

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

// ==============================================================================
// BootReceiver —— "垂死病中惊坐起"的第二道保险：
//   - 开机完成（BOOT_COMPLETED）→ 自动拉起前台服务；
//   - 应用被覆盖安装/更新（MY_PACKAGE_REPLACED）→ 同样拉起。
// 配合 KeepAliveService 的 START_STICKY（被杀即重建），形成完整的自愈闭环。
// 需在 AndroidManifest 注册并声明 RECEIVE_BOOT_COMPLETED 权限。
// ==============================================================================
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                KeepAliveService.start(context)
            }
        }
    }
}
