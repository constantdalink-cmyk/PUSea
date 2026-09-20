package org.example.project

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class AndroidJsWorkspaceStorage(context: Context) : JsWorkspaceStorage {
    private val rootDir: File = File(context.filesDir, "js_workspace")

    // 任务2：会话与代码库绑定 + 画布解离。每个持久化键一个独立子目录：
    //   js_workspace/{持久化键}/scripts/*.js
    //   js_workspace/{持久化键}/metadata.json
    // 持久化键：旧黑画布 = conversationId（会话级，原格式零迁移）；新画布 = 全局常量 "__new"；
    // 竖块项目 = "__proj_{id}" —— 新画布/新新画布不挂会话、永远保存；三类分区互为兄弟目录，文件互不侵入；
    // save 里"清理游离 .js"的破坏性逻辑也被目录关进笼子——绝不会误删其他分区的文件。
    // 新键没有任何目录 → loadWorkspace 返回 null → 空工作区，什么也没有。
    private var lastConversationId: String = "conversation_1"

    /** 遗留兼容：最近一次访问分区的展示根路径。 */
    override val displayRootPath: String
        get() = displayRootPathFor(lastConversationId)

    /**
     * 持久化键的真实物理 scripts 目录（纯路径计算，不读盘、无状态）：
     * 旧画布 …/js_workspace/{conv}/scripts；新画布 …/js_workspace/__new/scripts；
     * 项目 …/js_workspace/__proj_{id}/scripts。store 用它同步地址栏，左滑复制拿到的就是盘上真实位置。
     */
    override fun displayRootPathFor(conversationId: String): String =
        File(conversationDir(conversationId), "scripts").absolutePath

    private fun conversationDir(conversationId: String): File = File(rootDir, conversationId)

    override suspend fun loadWorkspace(conversationId: String): JsWorkspaceSnapshot? =
        withContext(Dispatchers.IO) {
            lastConversationId = conversationId
            val root = conversationDir(conversationId)
            val scriptsDir = File(root, "scripts")
            val metadataFile = File(root, "metadata.json")
            root.mkdirs()
            scriptsDir.mkdirs()
            if (!metadataFile.exists()) return@withContext null

            try {
                val json = JSONObject(metadataFile.readText(Charsets.UTF_8))
                val entriesJson = json.optJSONArray("entries") ?: JSONArray()
                val entries = mutableListOf<JsWorkspaceEntry>()
                val contents = mutableMapOf<String, String>()

                for (i in 0 until entriesJson.length()) {
                    val it = entriesJson.optJSONObject(i) ?: continue
                    val id = it.optString("id", "")
                    val name = it.optString("name", "")
                    if (id.isBlank() || name.isBlank()) continue
                    val isFolder = it.optBoolean("isFolder", false)
                    val parentIdRaw = it.optString("parentId", "")
                    val parentId = if (parentIdRaw.isBlank()) null else parentIdRaw

                    entries.add(JsWorkspaceEntry(id, name, isFolder, parentId))
                    if (!isFolder) {
                        val f = File(scriptsDir, "$id.js")
                        contents[id] = if (f.exists()) f.readText(Charsets.UTF_8) else ""
                    }
                }

                val openedArr = json.optJSONArray("openedFileIds") ?: JSONArray()
                val opened = mutableListOf<String>()
                for (i in 0 until openedArr.length()) {
                    val s = openedArr.optString(i, "")
                    if (s.isNotBlank()) opened.add(s)
                }

                val selectedRaw = json.optString("selectedFileId", "")
                val selectedFileId = if (selectedRaw.isBlank()) null else selectedRaw
                val idCounter = json.optInt("idCounter", entries.size)

                JsWorkspaceSnapshot(entries, opened, selectedFileId, contents, idCounter)
            } catch (t: Throwable) {
                null
            }
        }

    override suspend fun saveWorkspace(conversationId: String, snapshot: JsWorkspaceSnapshot) {
        withContext(Dispatchers.IO) {
            lastConversationId = conversationId
            val root = conversationDir(conversationId)
            val scriptsDir = File(root, "scripts")
            val metadataFile = File(root, "metadata.json")
            root.mkdirs()
            scriptsDir.mkdirs()

            val activeFileNames = snapshot.entries.filter { !it.isFolder }.map { "${it.id}.js" }.toSet()
            scriptsDir.listFiles()?.forEach { f ->
                if (f.isFile && f.extension.lowercase() == "js" && f.name !in activeFileNames) {
                    f.delete()
                }
            }

            snapshot.entries.filter { !it.isFolder }.forEach { entry ->
                val f = File(scriptsDir, "${entry.id}.js")
                val content = snapshot.fileContents[entry.id] ?: ""
                f.writeText(content, Charsets.UTF_8)
            }

            val obj = JSONObject()
            obj.put("selectedFileId", snapshot.selectedFileId ?: "")
            obj.put("idCounter", snapshot.idCounter)

            val entriesArr = JSONArray()
            snapshot.entries.forEach { e ->
                val item = JSONObject()
                item.put("id", e.id)
                item.put("name", e.name)
                item.put("isFolder", e.isFolder)
                item.put("parentId", e.parentId ?: "")
                entriesArr.put(item)
            }
            obj.put("entries", entriesArr)

            val openedArr = JSONArray()
            snapshot.openedFileIds.forEach { openedArr.put(it) }
            obj.put("openedFileIds", openedArr)

            val tmp = File(root, "metadata.json.tmp")
            tmp.writeText(obj.toString(2), Charsets.UTF_8)
            if (metadataFile.exists()) metadataFile.delete()
            tmp.renameTo(metadataFile)
        }
    }

    // ===== 画板竖块结构持久化：js_workspace/sketch_blocks.json（全局单份） =====
    // 不挂会话、永远保存（conversationId 参数仅接口兼容，忽略）；tmp + rename 原子替换，与 metadata.json 同款写法。

    override suspend fun loadSketchBlocks(conversationId: String): SketchBlocksSnapshot? =
        withContext(Dispatchers.IO) {
            val file = File(rootDir, "sketch_blocks.json")
            if (!file.exists()) return@withContext null
            try {
                val json = JSONObject(file.readText(Charsets.UTF_8))
                val idsArr = json.optJSONArray("nextBlockIds") ?: JSONArray()
                val nextBlockIds = (0 until 3).map { i -> idsArr.optInt(i, 1).coerceAtLeast(1) }
                val blocksArr = json.optJSONArray("blocks") ?: JSONArray()
                val blocks = mutableListOf<SketchBlockRecord>()
                for (i in 0 until blocksArr.length()) {
                    val it = blocksArr.optJSONObject(i) ?: continue
                    val groupNo = it.optInt("groupNo", 0)
                    val blockId = it.optInt("blockId", 0)
                    if (groupNo !in 1..3 || blockId < 1) continue
                    blocks.add(SketchBlockRecord(groupNo, blockId, it.optString("caption", "")))
                }
                SketchBlocksSnapshot(
                    blocks,
                    nextBlockIds,
                    json.optBoolean("seedConsumed", false),
                    json.optString("canvasKind", "BLACK_CANVAS")
                )
            } catch (t: Throwable) {
                null
            }
        }

    override suspend fun saveSketchBlocks(conversationId: String, snapshot: SketchBlocksSnapshot) {
        withContext(Dispatchers.IO) {
            rootDir.mkdirs()
            val obj = JSONObject()
            val idsArr = JSONArray()
            snapshot.nextBlockIds.forEach { idsArr.put(it) }
            obj.put("nextBlockIds", idsArr)
            val blocksArr = JSONArray()
            snapshot.blocks.forEach { b ->
                val item = JSONObject()
                item.put("groupNo", b.groupNo)
                item.put("blockId", b.blockId)
                item.put("caption", b.caption)
                blocksArr.put(item)
            }
            obj.put("blocks", blocksArr)
            obj.put("seedConsumed", snapshot.seedConsumed)
            obj.put("canvasKind", snapshot.canvasKind)
            val file = File(rootDir, "sketch_blocks.json")
            val tmp = File(rootDir, "sketch_blocks.json.tmp")
            tmp.writeText(obj.toString(2), Charsets.UTF_8)
            if (file.exists()) file.delete()
            tmp.renameTo(file)
        }
    }

    // ===== 项目继承门禁：是否已有内容非空的项目分区（只读，不写） =====
    // "只继承给第一个点击的竖块"的磁盘事实裁决：项目分区 = 全局兄弟目录 __proj_{id}（不挂会话），
    // 任一目录的 metadata.json 里 entries 非空 = 已有竖块拿过内容。store 在每次点击现场问这里。
    override suspend fun hasAnyProjectContent(conversationId: String): Boolean =
        withContext(Dispatchers.IO) {
            // 项目目录前缀须与 store 的 KEY_SUFFIX_PROJECT（"__proj_"）一致；项目分区全局，conversationId 忽略
            val prefix = "__proj_"
            val dirs = rootDir.listFiles() ?: return@withContext false
            for (dir in dirs) {
                if (!dir.isDirectory || !dir.name.startsWith(prefix)) continue
                val meta = File(dir, "metadata.json")
                if (!meta.exists()) continue
                try {
                    val entries = JSONObject(meta.readText(Charsets.UTF_8)).optJSONArray("entries")
                    if (entries != null && entries.length() > 0) return@withContext true
                } catch (_: Throwable) {
                    // 损坏的存档跳过，继续看下一个目录
                }
            }
            false
        }
}
