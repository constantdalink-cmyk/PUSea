package org.example.project

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 项目 zip 里的一个可导出条目：
 * path = zip 内相对路径（含文件夹层级，如 "工具/爬虫.js"），目录条目以 "/" 结尾；
 * content = 文件内容（UTF-8 文本；目录条目无意义）。
 */
data class ProjectFilePayload(
    val path: String,
    val content: String,
    val isDirectory: Boolean = false
)

/**
 * 平台侧「打包 zip + 系统分享」执行器（Android 实现：AndroidProjectZipExporter）。
 * common 侧只负责把项目文件表组装出来；zip 生成与 ACTION_SEND 系统分享面板全在平台侧完成。
 */
interface ProjectZipExporter {
    suspend fun exportAndShare(zipName: String, files: List<ProjectFilePayload>)
}

/**
 * 项目 zip 分享中枢——长按竖块 → 读取该项目全部文件 → 打包 zip → 安卓原生分享面板。
 * 文件来源：JsWorkspaceStore.loadProjectSnapshot(projectId)（项目的全局分区 __proj_{id}，
 * 含文件夹层级与文件内容）；调度走主线程（平台侧要 Toast / startActivity）。
 *
 * 自诊断契约：整条链路任何失败都不静默——
 *  · feedback（平台侧传 Toast）负责 common 层失败（未接线 / 项目无文件 / 异常）；
 *  · 平台执行器负责端上失败（URI 产出失败 / 启动分享失败 / 空文件表）；
 *  · 长按触发了却连第一条"正在打包…"都没出现 = 长按入口不在当前安装版本里（App71 未同步）。
 * 接线：MainActivity initialize（exporter + feedback 两个参数）；长按入口在竖块单元（App71）。
 */
object ProjectShareStore {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var exporter: ProjectZipExporter? = null
    private var feedback: ((String) -> Unit)? = null

    /** 启动接线：exporter = 平台打包分享实现；feedback = 可见反馈通道（Android 传 Toast）。 */
    fun initialize(exporter: ProjectZipExporter, feedback: (String) -> Unit) {
        this.exporter = exporter
        this.feedback = feedback
    }

    /**
     * 长按竖块入口：projectId = 组号_块号（与项目工作区同一套键）；
     * displayName = 竖块起名（留空回退 Project_{id}）作 zip 文件名。
     */
    fun shareProject(projectId: String, displayName: String) {
        val name = displayName.trim().ifBlank { "Project_$projectId" }
        val e = exporter
        if (e == null) {
            feedback?.invoke("分享未接线：MainActivity 未调用 ProjectShareStore.initialize")
            return
        }
        feedback?.invoke("正在打包 $name …")
        scope.launch {
            try {
                val snap = JsWorkspaceStore.loadProjectSnapshot(projectId)
                val files = buildPayloads(snap)
                if (files.isEmpty()) {
                    feedback?.invoke(
                        "项目 $name 还没有文件：先点开它建点内容再长按分享" +
                            "（第一个点开的竖块会继承画布文件库）"
                    )
                    return@launch
                }
                e.exportAndShare(name, files)
            } catch (t: Throwable) {
                feedback?.invoke("分享失败：${t.message ?: t.javaClass.simpleName}")
            }
        }
    }

    /**
     * 把项目文件树展开成 zip 条目：文件夹落目录条目（尾 "/"），文件保留原名；
     * 路径按层级拼接，不同文件夹里的同名文件不撞条目。
     */
    private fun buildPayloads(snap: JsWorkspaceSnapshot?): List<ProjectFilePayload> {
        if (snap == null) return emptyList()
        val byId = snap.entries.associateBy { it.id }
        fun pathOf(entry: JsWorkspaceEntry): String {
            val segments = ArrayDeque<String>()
            var cur: JsWorkspaceEntry? = entry
            while (cur != null) {
                segments.addFirst(cur.name)
                cur = cur.parentId?.let { byId[it] }
            }
            return segments.joinToString("/")
        }
        return snap.entries.map { entry ->
            if (entry.isFolder) {
                ProjectFilePayload(pathOf(entry) + "/", "", isDirectory = true)
            } else {
                ProjectFilePayload(pathOf(entry), snap.fileContents[entry.id] ?: "")
            }
        }
    }
}
