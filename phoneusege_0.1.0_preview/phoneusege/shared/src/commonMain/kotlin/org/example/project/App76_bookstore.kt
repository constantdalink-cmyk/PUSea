package org.example.project

// ==============================================================================
// 通识书架注入桥 (BookLibraryStore)
// ------------------------------------------------------------------------------
// 把"自动整理出的通识书"变成 AI 上下文里的正规军：
//   书架内容序列化成一段长期记忆 system 片段 → 注册为一条 AiSkill(通识书架)，
//   由现成注入管道 buildActiveSkillsPromptSnippet → createSkillsSystemTurn
//   → buildSkillEnhancedContext 拼进每一次常规聊天请求的 system 词条。
//   handleSend 与 runAiReplyFlow 两条路径无需任何改动即自动吃到。
//
// 开关：用户在书本界面点"注入 System"按钮 → isEnabled 翻转 → 立即生效/撤下。
// 书架为空（还没成书）时不注入任何东西，按钮只待命。
// ==============================================================================

object BookLibraryStore {

    /** 书架技能的稳定 id：保证 sync 幂等（只更新这一条，不会越注入越多） */
    const val BOOK_SKILL_ID = "skill_book_library"

    /** 注入片段的字符总闸：书架越长越要克制，超了就留最近的册（与 MAX_API_CHARS 对齐） */
    const val MAX_SNIPPET_CHARS = 12000

    /**
     * 同步书架到技能仓库（唯一入口，幂等）：
     * - snippet 为空（书架空）→ 撤掉书架技能；
     * - 已有 → 原地更新正文与开关（内容或开关变了才写，避免无谓落盘）;
     * - 没有 → 新建一条 isEnabled=injectOn 的书架技能。
     */
    fun sync(shelfSnippet: String, injectOn: Boolean) {
        if (shelfSnippet.isBlank()) {
            SkillStore.deleteSkill(BOOK_SKILL_ID)
            return
        }

        val existing = SkillStore.skills.value.firstOrNull { it.id == BOOK_SKILL_ID }
        if (existing == null) {
            SkillStore.addSkill(
                AiSkill(
                    id = BOOK_SKILL_ID,
                    name = "general_knowledge_bookshelf",
                    displayName = "通识书架",
                    description = "系统自动整理的对话通识书（长期记忆）",
                    systemPrompt = shelfSnippet,
                    isEnabled = injectOn,
                    category = "BookLibrary",
                    tags = listOf("book-library", "long-term-memory"),
                    isBuiltIn = false
                )
            )
        } else if (existing.systemPrompt != shelfSnippet || existing.isEnabled != injectOn) {
            SkillStore.updateSkill(
                existing.copy(systemPrompt = shelfSnippet, isEnabled = injectOn)
            )
        }
    }
}
