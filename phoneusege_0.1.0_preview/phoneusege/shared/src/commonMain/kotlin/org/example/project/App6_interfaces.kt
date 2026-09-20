package org.example.project

/**
 * 测试 API 配置的连接结果。
 *
 * @property isSuccess 是否连接成功
 * @property message 适合直接显示在 UI 上的结果
 * @property statusCode HTTP 状态码；网络错误时为 null
 */
data class ConnectionTestResult(
    val isSuccess: Boolean,
    val message: String,
    val statusCode: Int? = null
)

/**
 * 使用指定配置执行一次最小测试请求。
 *
 * Android 端将在 androidMain 中提供实际实现。
 */
expect suspend fun testKeyConnection(
    config: SavedKeyConfig
): ConnectionTestResult

/**
 * 一条聊天历史记录。
 *
 * @property attachments 用户消息携带的附件（画布上传的图片等）。
 *   OpenAI 请求会转成 content parts 的 image_url；Anthropic 转成 image 块；
 *   非图片附件不进 content parts（正文里已有路径说明，模型可用工具读取）。
 */
data class ChatTurn(
    val role: String,   // "system" | "user" | "assistant"
    val content: String,
    val attachments: List<ChatAttachment> = emptyList()
)

/**
 * 随聊天消息发送的附件（目前支持图片多模态）。
 *
 * @property fileName 原始文件名（用于猜测 MIME）
 * @property mimeType 如 image/png、image/jpeg
 * @property bytes 原始字节（由平台层负责 base64 编码进 payload）
 */
data class ChatAttachment(
    val fileName: String,
    val mimeType: String,
    val bytes: ByteArray
)

/**
 * 聊天请求结果。
 *
 * @property isSuccess 是否成功
 * @property reply 成功时的 AI 回复文本；失败时为 null
 * @property errorMessage 失败时的错误描述；成功时为 null
 * @property statusCode HTTP 状态码
 */
data class ChatReplyResult(
    val isSuccess: Boolean,
    val reply: String? = null,
    val errorMessage: String? = null,
    val statusCode: Int? = null
)

/**
 * 用指定配置发起一次非流式聊天请求。
 *
 * Android 端将在 androidMain 中提供实际实现。
 */
expect suspend fun requestChat(
    config: SavedKeyConfig,
    history: List<ChatTurn>
): ChatReplyResult
/**
 * 流式聊天请求结果。
 *
 * @property isSuccess 是否成功
 * @property fullText 最终拼接得到的完整文本；流中断时可能包含已收到的部分文本
 * @property errorMessage 失败时的错误描述；成功时为 null
 * @property statusCode HTTP 状态码
 */
data class ChatStreamResult(
    val isSuccess: Boolean,
    val fullText: String = "",
    val errorMessage: String? = null,
    val statusCode: Int? = null
)

/**
 * 用指定配置发起一次流式聊天请求。
 *
 * onDelta 每收到一段文本增量时调用。
 * SSE 解析由平台 actual 实现负责，UI 层不解析 SSE。
 */
expect suspend fun requestChatStream(
    config: SavedKeyConfig,
    history: List<ChatTurn>,
    onDelta: suspend (String) -> Unit
): ChatStreamResult


/**
 * 主机探活结果。
 *
 * @property reachable 是否收到了任何 HTTP 响应（即主机存活）
 * @property statusCode 收到的 HTTP 状态码；无响应时为 null
 * @property errorMessage 探活失败原因；成功时为 null
 */
data class HostProbeResult(
    val reachable: Boolean,
    val statusCode: Int? = null,
    val errorMessage: String? = null
)

/**
 * 对 baseUrl 的 origin（scheme+host+port）发一个 HEAD 请求，
 * 只判断主机是否可达，不带 API Key，不带请求体，不消耗 token。
 *
 * Android 端将在 androidMain 中提供实际实现。
 */
expect suspend fun probeHttpOrigin(baseUrl: String): HostProbeResult
