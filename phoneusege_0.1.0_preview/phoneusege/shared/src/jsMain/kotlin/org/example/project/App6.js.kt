package org.example.project

/**
 * 非 Android 平台的占位实现。
 * 当前只保证编译通过；真正的测试连接仅在 Android 生效。
 */
actual suspend fun testKeyConnection(
    config: SavedKeyConfig
): ConnectionTestResult {
    return ConnectionTestResult(
        isSuccess = false,
        message = "Test Connection is not supported on this platform yet",
        statusCode = null
    )
}

actual suspend fun requestChat(
    config: SavedKeyConfig,
    history: List<ChatTurn>
): ChatReplyResult {
    return ChatReplyResult(
        isSuccess = false,
        errorMessage = "Chat is not supported on this platform yet"
    )
}

actual suspend fun probeHttpOrigin(baseUrl: String): HostProbeResult {
    return HostProbeResult(
        reachable = false,
        errorMessage = "Host probe is not supported on this platform yet"
    )
}
