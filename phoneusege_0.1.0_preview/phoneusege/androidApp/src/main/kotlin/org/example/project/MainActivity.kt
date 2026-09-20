package org.example.project

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import org.example.project.AndroidFileStorage
import org.example.project.CanvasFileStore
import org.example.project.AndroidFileCapturePicker
import org.example.project.KeyConfigStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

private const val LANGUAGE_PREFS_NAME = "app_language_prefs"
private const val LANGUAGE_PREFS_KEY = "selected_language_code"
private const val LANGUAGE_DEFAULT_CODE = "en"

class MainActivity : ComponentActivity() {

    // 技能管理画布：系统文件管理器选图（SAF / DocumentsUI）
    private val skillImagePickerLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri == null) {
                // 用户取消：回传空串，UI 侧保持原封面不变
                SkillImagePickerAndroid.onPicked("")
            } else {
                // 申请持久化读权限，保证应用重启后 content:// 仍可读
                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Throwable) {
                }
                SkillImagePickerAndroid.onPicked(uri.toString())
            }
        }

    // [KEEPALIVE] 通知权限（Android 13+ 前台服务通知需要）。未授权不影响服务本身运行。
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            // 授权结果不阻塞：13 以下无需权限；13+ 未授权时服务照常跑，仅通知被系统隐藏
        }

    // [KEEPALIVE] 后台执行桥：把 handleSend 的 AI 执行接到前台服务
    private var keepAliveBridge: KeepAliveBridge? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val appStorage = AndroidFileStorage(applicationContext)
        KeyConfigStore.initialize(appStorage)
        ConversationStore.initialize(appStorage)
        JsWorkspaceStore.initialize(AndroidJsWorkspaceStorage(applicationContext))
        // 任务2：启动时把代码库绑定到当前会话（每个会话拥有独立工作区）
        JsWorkspaceStore.switchConversation(ConversationStore.currentConversationId.value)
        // 长按项目竖块 → 读取项目文件 → 打包 zip → 安卓原生分享面板（App16）；
        // 第二个参数是失败反馈通道：链路任何一环断了都弹 Toast 报原因，不静默
        ProjectShareStore.initialize(AndroidProjectZipExporter(applicationContext)) { msg ->
            Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
        }
        JsRunnerStore.initialize(QuickJsRunner())
        CodeRiViewStore.initialize(AndroidCodeRiViewStorage(applicationContext))
        CmdStore.initialize(AndroidInterpreterDownloader(), AndroidHttpRequester())
        CanvasFileStore.initialize(AndroidFileCapturePicker(this))
        ImageBitmapDecodeStore.initialize(AndroidImageBitmapDecoder())
        // 技能管理画布：注入本地图片解码 + 绑定系统文件管理器选图
        SkillImagePickerAndroid.initializeDecoder(applicationContext)
        SkillImagePickerAndroid.bindLauncher {
            skillImagePickerLauncher.launch(arrayOf("image/*"))
        }
        // 画布 7/10：AI Assist 状态持久化（六张卡片 + 三个滑动调节 + 撕开白卡条目）
        AiAssistStore.initialize(appStorage)
        // 广场与 MCP 工具持久化初始化（内部同时回读自定义 Tools 爬取源网址 plaza_crawl_sources_v1）
        PlazaStore.initialize(appStorage)
        // 技能注入桥的最后一颗螺丝：AI Skills 激活状态持久化（ai_skills_config_v1.json），
        // 注入过的技能开关与提示词跨重启保留；initialize 内部会同步 loadSkills 回读存档
        SkillStore.initialize(appStorage)
        // 技能管理画布：下载的资源卡片 + 用户改写的 SKILL.md 持久化（downloaded_skill_resources_v1.json），
        // 重启后管理画布里下载过的 md 资源仍在
        DownloadedResourceStore.initialize(appStorage)
        // 通识书架持久化（book_shelf_v1.json）：整架书 + "注入 System"开关 + 整理档位跨重启保留。
        // 重启后书架原样回来、注入开关回读并重新点亮，BookLibraryStore 随之把整架书重新拼回 system 管道。
        // 必须放在 SkillStore.initialize 之后（注入回读依赖技能仓已就绪），setContent 之前（UI 回读存档）。
        BookShelfStore.initialize(appStorage)

        // ===== 功能管理画布三类状态持久化 =====
        // 竖块开关：联系方式图标显隐(绿=显示/红=隐藏)——启动固定回默认"显示"，
        // 点击态不跨重启重播(存档 contact_icon_visibility_v1.json 仅会话内写入备用)
        ContactIconStore.initialize(appStorage)
        // 小格子排列：12 张卡片所在格位 / 堆叠栈顺序跨重启恢复 function_management_card_layout_v1.json
        CardLayoutStore.initialize(appStorage)
        // 自定义 Skills 爬取源网址跨重启保持 skill_crawl_sources_v1.json
        // （Tools 爬取源已在上方 PlazaStore.initialize 内部回读）
        SkillCrawlStore.initialize(appStorage)
        // 第二个竖块：即时聊天开关(开 = 下一次新开的对话不存档)跨重启保持 chat_mode_ephemeral_v1；
        // 第一个竖块的联系方式图标显隐已在上方 ContactIconStore.initialize 内部回读
        ChatModeStore.initialize(appStorage)

        // ==================== [KEEPALIVE] 后台常驻 ====================
        // 注册 plan 工具 + 工具桥（幂等，引擎执行工具调用的前提）
        AiToolBridge.ensureInitialized()
        // 拉起前台服务：进程保活核心（通知护体 + WakeLock + START_STICKY）
        KeepAliveService.start(this)
        // 把 handleSend 的 AI 执行接到前台服务：注入后台执行器（commonMain 的 BackgroundSendHook）。
        // 注入后，handleSend 播完飞入动画即把整段执行交给服务常驻跑——
        // 调用 handleSend 的位置（App 的 onSend，无论在哪）一行都不用改。
        val bridge = KeepAliveBridge(this)
        bridge.bind(lifecycleScope)
        BackgroundSendHook.executor = BackgroundSendExecutor { state, msg, density, pixelFont, textMeasurer ->
            bridge.send(
                state = state,
                msg = msg,
                textMeasurer = textMeasurer,
                density = density,
                pixelFont = pixelFont,
                addUserBubble = false, // 用户气泡由 handleSend 的飞入动画挂上
                bypassGuard = true     // handleSend 已持有 isAiResponding，跳过桥的重入保护
            )
        }
        keepAliveBridge = bridge
        // 引导加入电池优化白名单：绕过系统/厂商激进省电（国产 ROM 杀后台主因）
        requestIgnoreBatteryOptimizations(this)
        // Android 13+ 申请通知权限（前台服务通知所需）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        // ================================================================

        val savedLanguageCode = readSavedLanguageCode(this)

        AppLanguage.initialize(savedLanguageCode)

        setContent {
            App(
                currentLanguageCode = savedLanguageCode,
                onConfirmLanguageAndExit = { newCode ->
                    saveLanguageCode(this, newCode)
                    finishAffinity()

                }
            )
        }
    }

    override fun onPause() {
        JsWorkspaceStore.flushNow()
        PlazaStore.flushNow()
        // 解释器持久化：退后台前挂起式落盘，确保状态完整写入
        lifecycleScope.launch { CodeRiViewStore.flush() }
        super.onPause()
    }

    override fun onDestroy() {
        // 释放选图回调与 Context 引用，避免泄漏
        SkillImagePickerAndroid.release()
        // [KEEPALIVE] 解绑后台桥（仅断开 UI 订阅；前台服务不受影响，继续在后台常驻）
        keepAliveBridge?.unbind()
        super.onDestroy()
    }
}

private fun readSavedLanguageCode(context: Context): String {
    val prefs = context.getSharedPreferences(LANGUAGE_PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getString(LANGUAGE_PREFS_KEY, LANGUAGE_DEFAULT_CODE) ?: LANGUAGE_DEFAULT_CODE
}

private fun saveLanguageCode(context: Context, code: String) {
    val prefs = context.getSharedPreferences(LANGUAGE_PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putString(LANGUAGE_PREFS_KEY, code).commit()
}

@Preview
@Composable
fun AppAndroidPreview() {
    App(
        currentLanguageCode = "en",
        onConfirmLanguageAndExit = {}
    )
}
