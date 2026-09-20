package org.example.project

/**
 * 发送箭头的状态机（发送 / 暂停 / 继续 三合一）：
 *
 *  Idle  ──发送──>  Pop（右弹一下）──>  Spin（顺时针转圈 = AI 输出中）
 *   ^                                     |  点一下：逆时针转回原位 = Paused（暂停）
 *   |                                     v
 *   └──── AI 回复结束：逆时针回到原位 ── Paused ──再点一下──> Spin（顺时针转起 = 继续）
 *
 *  Paused 态长按：停止并重发 —— 掐掉上一轮请求，用输入框新内容顶掉上一条消息，
 *  经 resendReplacingLast 重新走 Idle → Pop → Spin 全流程
 *
 * Pop→Spin 由 InputArea 的动画协程推进；
 * 进入 Spin = isOutputPaused 置 false，进入 Paused = isOutputPaused 置 true；
 * handleSend 结束时把 phase 置回 Idle 触发回卷动画。
 */
enum class ArrowPhase {
    /** 静止原位：白色 ➔，点击 = 发送 */
    Idle,

    /** 发送瞬间：向右弹一下（~0.5s），随后自动转入 Spin */
    Pop,

    /** AI 输出中：顺时针无限转圈；点击 = 暂停 */
    Spin,

    /** 已暂停：逆时针转回原位后停住；点击 = 继续（回到 Spin） */
    Paused
}
