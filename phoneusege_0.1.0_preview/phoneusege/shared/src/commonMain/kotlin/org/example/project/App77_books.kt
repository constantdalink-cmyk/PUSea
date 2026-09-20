package org.example.project

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// ==============================================================================
// 通识书架持久化 (BookShelfStore)
// ------------------------------------------------------------------------------
// 让"自动成书"的成果与开关跨重启保留，存档 book_shelf_v1.json：
//   1. 整架书：每一册 BookSpec 的序列化快照（只增不减的书架，重启后原样回来）；
//   2. 注入 System 开关：用户在书本界面点的"注入 System"按钮状态；
//   3. 整理档位：已完成总结的 60 次档位（防重启后把已覆盖的对话重复成册）。
//
// 设计：书架模型 BookSpec 定义在 com.example.bookphone 且为私有，故本 Store 以
//      "不透明 JSON 字符串"存放整架书——Store 只负责存取，不关心书的结构；
//      书的 (反)序列化由 BookPhoneScreen 的 serializeShelf/deserializeShelf 完成。
// 注入链路：重启后 injectOn 回读 → UI 按钮重新点亮 → LaunchedEffect 触发
//      BookLibraryStore.sync，把整架书重新拼回 system 管道（与 SkillStore 的
//      ai_skills_config_v1 双向一致，sync 是技能状态的唯一写入口，不会漂移）。
// ==============================================================================

object BookShelfStore {

    private const val STORAGE_KEY = "book_shelf_v1"
    private val json = Json { ignoreUnknownKeys = true }

    private var storage: StorageProvider = MemoryStorage
    private var initialized = false

    /** 存档记录：整架书快照 + 注入开关 + 整理档位（字段带默认值，老档/缺字段可平滑加载） */
    @Serializable
    private data class ShelfRecord(
        val shelfJson: String = "[]",        // 整架书的序列化快照（对 Store 不透明）
        val injectOn: Boolean = false,       // "注入 System"开关
        val lastCompiledBucket: Int = 0      // 已完成总结的 60 次档位
    )

    private var record = ShelfRecord()

    /** 启动时注入持久化存储（MainActivity），内部同步回读存档 */
    fun initialize(provider: StorageProvider) {
        storage = provider
        if (initialized) return
        initialized = true
        storage.load(STORAGE_KEY)?.let { raw ->
            runCatching { record = json.decodeFromString<ShelfRecord>(raw) }
        }
    }

    /** 整架书快照（未初始化 / 无存档 → "[]"） */
    fun loadShelfJson(): String = record.shelfJson

    /** 注入开关（未初始化 / 无存档 → false） */
    fun loadInjectOn(): Boolean = record.injectOn

    /** 整理档位（未初始化 / 无存档 → 0） */
    fun loadLastCompiledBucket(): Int = record.lastCompiledBucket

    /** 落盘：书架 / 开关 / 档位任一变化都整体覆盖写（坏写不抛，交给 runCatching 兜住） */
    fun persist(shelfJson: String, injectOn: Boolean, lastCompiledBucket: Int) {
        record = ShelfRecord(shelfJson, injectOn, lastCompiledBucket)
        runCatching { storage.save(STORAGE_KEY, json.encodeToString(record)) }
    }
}
