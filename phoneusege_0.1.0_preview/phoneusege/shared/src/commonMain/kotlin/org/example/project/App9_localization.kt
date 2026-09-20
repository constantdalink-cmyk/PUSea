package org.example.project

// ==============================================================================
// App9_localization.kt —— 全局多语言本地化（UiText 枚举 + AppLanguage 词典 + uiText 入口）
//
// 修复记录：
// 1.【闪退根因修复】原 ExceptionInInitializerError / NPE：
//    object 单例 <clinit> 阶段属性按声明顺序求值，`val x = uiText(...)` 先于词典
//    赋值执行 → text() 查到 null Map。现七个词典全部 by lazy，<clinit> 不建表，
//    与声明顺序彻底解耦，将来在 object 内任何位置新增属性都不会再踩顺序坑。
// 2.【三重兜底】text()：当前语言词典 → 英语词典 → 键名本身，任何缺键只降级不崩溃。
// 3.【语法破损修复】整份重建：修复磁盘文件中 object 花括号断裂导致的
//    currentLanguageCode / text Unresolved 与六处词典边界 Expecting ')'。
// ==============================================================================

enum class UiText {
    WhatDoYouWantToDo,
    TypeYourAnswer,
    Thinking,
    BottomSubtitle,
    User,

    ComponentPlaza,
    Chess,
    GeneralKnowledge,
    ComponentManagement,
    FunctionManagement,
    Key,
    AiAssistSystem,
    Self,
    Credits,
    Language,

    Close,
    SwipeVerticallyToSelect,
    ApplyLanguageChange,
    RestartToApply,
    Cancel,
    ExitNow,

    SelectProvider,
    Confirm,
    Configuration,
    Name,
    ApiKey,
    BaseUrl,
    Model,
    TestConnection,
    Testing,
    Back,
    Update,
    Save,

    KeyPrefix,
    UrlPrefix,
    ModelPrefix,

    ApiKeyRequired,
    BaseUrlRequired,
    ModelRequired,

    ConversationArchive,
    NewConversation,
    NoSavedConversations,
    EmptyConversation,
    MessagesSuffix,
    CurrentTag,

    // 致谢画布翻译条目 (Credits Canvas Translations)
    CreditsCanvasHeader,
    CreditsProductionDesign,
    CreditsProducerCreator,
    CreditsLeadSystemDesigner,
    CreditsLeadArtistConcept,
    CreditsEngineeringDev,
    CreditsLeadProgrammer,
    CreditsClientUiDev,
    CreditsBackendServices,
    CreditsTechStackFramework,
    CreditsEngineFramework,
    CreditsVisualsLocalization,
    CreditsFontDesignUsage,
    CreditsTranslation,
    CreditsTestingQa,
    CreditsBetaUsersBugHunters,
    CreditsTechSupportAi,
    CreditsTechSupport,
    CreditsAiAssistanceStatement,
    CreditsFamilyMentors,
    CreditsFamilyMentorRole,
    CreditsSpecialThanksTitle,
    CreditsSpecialThanksContent,
    CreditsNameConstantDalink,
    CreditsNameChanShi,
    CreditsNameLin,
    CreditsNameShijie,
    CreditsNameZhengquan,

    // 技能广场翻译条目 (Skill Plaza Translations)
    SkillOfficialTag,
    SkillTapToCloseDetail,
    SkillNoMatchPrefix,
    SkillNoMatchSuffix,
    SkillTapToClearFilter,
    SkillLoadingMore,
    SkillSearchPlaceholder,
    SkillSwipeDownToClose,

    // 技能搜索与下载解析翻译条目 (Skill Search & Download Parser Translations)
    SkillPlazaPrefix,
    SkillSourcePrefix,
    SkillTagPrefix,
    SkillEndpointPrefix,
    SkillStandardPackageDesc,
    SkillPackageTitle,
    SkillNamePrefix,
    SkillRepoPrefix,
    SkillLinkPrefix,
    SkillDescLabel,
    SkillInjectParagraph,
    SkillCrawlParagraph,
    SkillSearchShortPlaceholder,
    SkillNotchSearchPlaceholder,

    // 技能广场页面标题 (Skill Plaza Page Title)
    SkillPlazaTitle,

    // 技能管理画板翻译条目 (Skill Management Translations)
    SkillMgmtNoSearchResultTitle,
    SkillMgmtNoResourcesTitle,
    SkillMgmtNoSearchResultHint,
    SkillMgmtNoResourcesHint,
    SkillMgmtClearSearch,
    SkillMgmtDetailCanvas,

    // 技能详情画布翻译条目 (Skill Detail Canvas Translations —— App57 【2/3】右侧滑入画布)
    SkillDetailInjectAi,
    SkillDetailInjectedAi,
    SkillDetailTabIntro,
    SkillDetailFetchingMd,
    SkillDetailMdEditHint,
    SkillDetailMdPlaceholder,
    SkillDetailTapToWrite,

    // 功能管理画布翻译条目 (Function Management Canvas Translations —— App57 数据分区 / 删除确认窗)
    FunctionMgmtSkillsDataTitle,
    FunctionMgmtSkillsDataDesc,
    FunctionMgmtToolsDataTitle,
    FunctionMgmtToolsDataDesc,
    FunctionMgmtAreYouReal,

    // AI 辅助系统画布默认编辑内容 (AI Assist Canvas Default Editable Contents)
    AssistDefaultSystemPrompt,
    AssistDefaultIdentity,
    AssistDefaultBehaviorRules,
    AssistDefaultOutputFormat,
    AssistDefaultExamples,

    // 主画布按钮翻译条目 (Main Canvas Button Translations)
    // 注：主画布 "Skill Plaza" 按钮复用既有 SkillPlazaTitle；"Skill Management" 原为硬编码英文，新增此键
    SkillManagement,

    // 回复模式标签翻译条目 (Reply Mode Label Translations)
    // 枚举名 Stream / Full 仅用于持久化；UI 显示（OptionChip 标签）经 AssistReplyMode.label() 走这里
    AssistReplyModeStream,
    AssistReplyModeFull,

    // AI 辅助系统画布卡片翻译条目 (AI Assist Canvas Card Translations)
    AssistCardSystemPromptTitle,
    AssistCardSystemPromptDesc,
    AssistCardIdentityTitle,
    AssistCardIdentityDesc,
    AssistCardReplyModeTitle,
    AssistCardReplyModeDesc,
    AssistCardBehaviorRulesTitle,
    AssistCardBehaviorRulesDesc,
    AssistCardOutputFormatTitle,
    AssistCardOutputFormatDesc,
    AssistCardExamplesTitle,
    AssistCardExamplesDesc,

    // 抽屉竖排字幕翻译条目 (Drawer Vertical Caption Translations)
    // SettingsDrawer 右侧逐字母竖排的两列单词；竖排渲染不变，CJK 译文天然成竖排
    AssistDrawerCaptionSlider,
    AssistDrawerCaptionSettings,

    // AI 辅助系统画布顶部标题翻译条目 (Assist Canvas Top Title / Wordmark)
    AssistCanvasWordmark
}

object AppLanguage {
    private var currentLanguageCode: String = "en"

    fun initialize(code: String) {
        currentLanguageCode = normalizeCode(code)
    }

    fun currentCode(): String = currentLanguageCode

    fun text(key: UiText): String {
        val dictionary = when (currentLanguageCode) {
            "zh-Hans" -> simplifiedChinese
            "zh-Hant" -> traditionalChinese
            "ja" -> japanese
            "ko" -> korean
            "fr" -> french
            "de" -> german
            else -> english
        }

        // 三重兜底：当前语言词典 → 英语词典 → 键名本身。
        // 任何初始化顺序 / 缺键问题都不会再崩溃，最差也只是显示键名
        return dictionary[key] ?: english[key] ?: key.name
    }

    private fun normalizeCode(code: String): String {
        val normalized = code
            .replace('_', '-')
            .lowercase()

        return when {
            normalized == "zh-cn" ||
                normalized.startsWith("zh-hans") -> "zh-Hans"

            normalized == "zh-tw" ||
                normalized == "zh-hk" ||
                normalized.startsWith("zh-hant") -> "zh-Hant"

            normalized.startsWith("ja") -> "ja"
            normalized.startsWith("ko") -> "ko"
            normalized.startsWith("fr") -> "fr"
            normalized.startsWith("de") -> "de"
            else -> "en"
        }
    }

    // 七个词典全部 by lazy：首次被引用时才构建，与声明顺序彻底解耦。
    // <clinit> 阶段不建表 —— 即使将来在 object 内任何位置新增
    // `val x = uiText(...)` 这类属性，也不会踩初始化顺序坑。
    private val english by lazy {
        mapOf(
            UiText.WhatDoYouWantToDo to "What Do You Want To Do?",
            UiText.TypeYourAnswer to "Type your answer...",
            UiText.Thinking to "Thinking",
            UiText.BottomSubtitle to "The first created and open-sourced direct AI mobile system",
            UiText.User to "User",

            UiText.ComponentPlaza to "Component Plaza",
            UiText.Chess to "Chess",
            UiText.GeneralKnowledge to "General Knowledge",
            UiText.ComponentManagement to "Component Management",
            UiText.FunctionManagement to "Function Management",
            UiText.Key to "Key",
            UiText.AiAssistSystem to "AI Assist System",
            UiText.Self to "Self",
            UiText.Credits to "Credits",
            UiText.Language to "Language",

            UiText.Close to "Close",
            UiText.SwipeVerticallyToSelect to "Swipe vertically to select",
            UiText.ApplyLanguageChange to "Apply Language Change?",
            UiText.RestartToApply to
                "Please exit and reopen the app to apply the selected language.",
            UiText.Cancel to "Cancel",
            UiText.ExitNow to "Exit Now",

            UiText.SelectProvider to "Select Provider",
            UiText.Confirm to "Confirm",
            UiText.Configuration to "Configuration",
            UiText.Name to "Name",
            UiText.ApiKey to "API Key",
            UiText.BaseUrl to "Base URL",
            UiText.Model to "Model",
            UiText.TestConnection to "Test Connection",
            UiText.Testing to "Testing",
            UiText.Back to "Back",
            UiText.Update to "Update",
            UiText.Save to "Save",

            UiText.KeyPrefix to "Key: ",
            UiText.UrlPrefix to "URL: ",
            UiText.ModelPrefix to "Model: ",

            UiText.ApiKeyRequired to "API Key cannot be empty.",
            UiText.BaseUrlRequired to "Base URL cannot be empty.",
            UiText.ModelRequired to "Model cannot be empty.",
            UiText.ConversationArchive to "CONVERSATION ARCHIVE",
            UiText.NewConversation to "+ NEW CONVERSATION",
            UiText.NoSavedConversations to "No saved conversations.",
            UiText.EmptyConversation to "Empty conversation",
            UiText.MessagesSuffix to "message(s)",
            UiText.CurrentTag to "CURRENT",

            // Credits Translations (English)
            UiText.CreditsCanvasHeader to "★ CREDITS CANVAS (CREDITS) ★",
            UiText.CreditsProductionDesign to "Production & Core Design",
            UiText.CreditsProducerCreator to "Producer / Creator",
            UiText.CreditsLeadSystemDesigner to "Lead System Designer",
            UiText.CreditsLeadArtistConcept to "Lead Artist / Concept Design",
            UiText.CreditsEngineeringDev to "Engineering & Development",
            UiText.CreditsLeadProgrammer to "Lead Programmer",
            UiText.CreditsClientUiDev to "Client / UI Development",
            UiText.CreditsBackendServices to "Backend & Services",
            UiText.CreditsTechStackFramework to "Tech Stack & Engine Framework",
            UiText.CreditsEngineFramework to "Engine / Framework",
            UiText.CreditsVisualsLocalization to "Visuals & Localization",
            UiText.CreditsFontDesignUsage to "Font Design / Usage",
            UiText.CreditsTranslation to "Translation",
            UiText.CreditsTestingQa to "Testing & Quality Assurance",
            UiText.CreditsBetaUsersBugHunters to "Beta Testers / Bug Hunters",
            UiText.CreditsTechSupportAi to "Tech Support & AI Assistance",
            UiText.CreditsTechSupport to "Technical Support",
            UiText.CreditsAiAssistanceStatement to "AI Assistance Statement",
            UiText.CreditsFamilyMentors to "Family & Mentors",
            UiText.CreditsFamilyMentorRole to "Family / Mentor",
            UiText.CreditsSpecialThanksTitle to "❤ Special Thanks ❤",
            UiText.CreditsSpecialThanksContent to "Thank you to every user who tried this work!\n(Thank you for using!)",
            UiText.CreditsNameConstantDalink to "Constant - Dalink",
            UiText.CreditsNameChanShi to "Chan Shi",
            UiText.CreditsNameLin to "Lin",
            UiText.CreditsNameShijie to "Shijie",
            UiText.CreditsNameZhengquan to "Zhengquan",

            // Skill Plaza Translations (English)
            UiText.SkillOfficialTag to "Official",
            UiText.SkillTapToCloseDetail to "Tap image/card to close",
            UiText.SkillNoMatchPrefix to "No skills found matching '",
            UiText.SkillNoMatchSuffix to "'",
            UiText.SkillTapToClearFilter to "Tap ✕ to clear filter",
            UiText.SkillLoadingMore to "Loading...",
            UiText.SkillSearchPlaceholder to "Search skills by keyword...",
            UiText.SkillSwipeDownToClose to "Swipe down to close",

            // Skill Search & Download Parser Translations (English)
            UiText.SkillPlazaPrefix to "Plaza: ",
            UiText.SkillSourcePrefix to "Source: ",
            UiText.SkillTagPrefix to "Tag: ",
            UiText.SkillEndpointPrefix to "Endpoint: ",
            UiText.SkillStandardPackageDesc to "Standard portable Agent Skill package conforming to SKILL.md specification with structured system instructions and tool definitions.",
            UiText.SkillPackageTitle to "SKILL.md Agent Capability Package",
            UiText.SkillNamePrefix to "Skill Name: ",
            UiText.SkillRepoPrefix to "Repository: ",
            UiText.SkillLinkPrefix to "Resource Link: ",
            UiText.SkillDescLabel to "Description:",
            UiText.SkillInjectParagraph to "This Agent Skill injects structured domain knowledge, prompt workflows, and specialized execution guidelines into your assistant context window.",
            UiText.SkillCrawlParagraph to "Supports automatic asset crawling, Markdown schema verification, and instant local activation in your PhoneUsage workspace.",
            UiText.SkillSearchShortPlaceholder to "Search",
            UiText.SkillNotchSearchPlaceholder to "Search...",

            // Skill Plaza Page Title (English)
            UiText.SkillPlazaTitle to "Skills Plaza",

            // Skill Management Translations (English)
            UiText.SkillMgmtNoSearchResultTitle to "No matching skill resources found",
            UiText.SkillMgmtNoResourcesTitle to "No skill resources yet",
            UiText.SkillMgmtNoSearchResultHint to "Try searching other keywords or clear the filter",
            UiText.SkillMgmtNoResourcesHint to "Resources devoured by the Big Mouth will show up here automatically",
            UiText.SkillMgmtClearSearch to "Clear Search",
            UiText.SkillMgmtDetailCanvas to "Detail Canvas",

            // Skill Detail Canvas Translations (English)
            UiText.SkillDetailInjectAi to "Inject AI",
            UiText.SkillDetailInjectedAi to "AI Injected",
            UiText.SkillDetailTabIntro to "Intro",
            UiText.SkillDetailFetchingMd to "Fetching SKILL.md…",
            UiText.SkillDetailMdEditHint to "Changes apply to the next message",
            UiText.SkillDetailMdPlaceholder to "SKILL.md instructions… (auto-fetched from source if left empty)",
            UiText.SkillDetailTapToWrite to "Tap to write…",

            // Function Management Canvas Translations (English)
            UiText.FunctionMgmtSkillsDataTitle to "Skills Data",
            UiText.FunctionMgmtSkillsDataDesc to "Delete your downloaded skills",
            UiText.FunctionMgmtToolsDataTitle to "Tools Data",
            UiText.FunctionMgmtToolsDataDesc to "Delete your downloaded tools",
            UiText.FunctionMgmtAreYouReal to "ARE YOU REAL?",

            // AI Assist Canvas Default Editable Contents (English)
            UiText.AssistDefaultSystemPrompt to "You are Pixel, a terminal-native AI assistant inside phoneusege. Keep replies short, direct and structured. Use code blocks for code.",
            UiText.AssistDefaultIdentity to "Name: Pixel\nPersonality: calm, efficient, slightly pixel-humorous.\nThe user is your operator.",
            UiText.AssistDefaultBehaviorRules to "Always answer the user directly.\nNever mention you are an AI unless asked.\nStay in the user's language.",
            UiText.AssistDefaultOutputFormat to "Use markdown for structure.\nBullet lists for steps.\nCode blocks with language tags.",
            UiText.AssistDefaultExamples to "Q: what is 2+2?\nA: 4. Simple and direct.",

            // Main Canvas Button Translations (English)
            UiText.SkillManagement to "Skill Management",

            // Reply Mode Label Translations (English)
            UiText.AssistReplyModeStream to "Stream",
            UiText.AssistReplyModeFull to "Full",

            // AI Assist Canvas Card Translations (English)
            UiText.AssistCardSystemPromptTitle to "System Prompt",
            UiText.AssistCardSystemPromptDesc to "Base instructions injected into every request — how the AI should behave and respond.",
            UiText.AssistCardIdentityTitle to "Identity",
            UiText.AssistCardIdentityDesc to "Defines who the AI is — its name, personality and tone in conversations.",
            UiText.AssistCardReplyModeTitle to "Reply Mode",
            UiText.AssistCardReplyModeDesc to "Stream delivers the reply token by token; Full returns it all at once.",
            UiText.AssistCardBehaviorRulesTitle to "Behavior Rules",
            UiText.AssistCardBehaviorRulesDesc to "Do-or-don't lines that shape how the AI acts in every reply.",
            UiText.AssistCardOutputFormatTitle to "Output Format",
            UiText.AssistCardOutputFormatDesc to "Defines the structure of replies — markdown, bullet lists, code blocks.",
            UiText.AssistCardExamplesTitle to "Examples",
            UiText.AssistCardExamplesDesc to "Sample Q&A pairs so the AI copies the desired style and brevity.",

            // Drawer Vertical Caption Translations (English)
            UiText.AssistDrawerCaptionSlider to "SLIDER",
            UiText.AssistDrawerCaptionSettings to "SETTINGS",

            // Assist Canvas Top Title / Wordmark (English)
            UiText.AssistCanvasWordmark to "aiassistsystem"
        )
    }

    private val simplifiedChinese by lazy {
        english + mapOf(
            UiText.WhatDoYouWantToDo to "你想做什么？",
            UiText.TypeYourAnswer to "输入你的回答...",
            UiText.Thinking to "思考中",
            UiText.BottomSubtitle to "首个创建并开源的直接式 AI 移动系统",
            UiText.User to "用户",

            UiText.ComponentPlaza to "组件广场",
            UiText.Chess to "国际象棋",
            UiText.GeneralKnowledge to "通识",
            UiText.ComponentManagement to "组件管理",
            UiText.FunctionManagement to "功能管理",
            UiText.Key to "密钥",
            UiText.AiAssistSystem to "AI 辅助系统",
            UiText.Self to "自我",
            UiText.Credits to "致谢",
            UiText.Language to "语言",

            UiText.Close to "关闭",
            UiText.SwipeVerticallyToSelect to "上下滑动选择",
            UiText.ApplyLanguageChange to "应用语言更改？",
            UiText.RestartToApply to "请退出并重新打开应用以应用所选语言。",
            UiText.Cancel to "取消",
            UiText.ExitNow to "立即退出",

            UiText.SelectProvider to "选择服务商",
            UiText.Confirm to "确认",
            UiText.Configuration to "配置",
            UiText.Name to "名称",
            UiText.ApiKey to "API 密钥",
            UiText.BaseUrl to "基础地址",
            UiText.Model to "模型",
            UiText.TestConnection to "测试连接",
            UiText.Testing to "测试中",
            UiText.Back to "返回",
            UiText.Update to "更新",
            UiText.Save to "保存",

            UiText.KeyPrefix to "密钥：",
            UiText.UrlPrefix to "地址：",
            UiText.ModelPrefix to "模型：",

            UiText.ApiKeyRequired to "API 密钥不能为空。",
            UiText.BaseUrlRequired to "基础地址不能为空。",
            UiText.ModelRequired to "模型不能为空。",
            UiText.ConversationArchive to "对话档案",
            UiText.NewConversation to "+ 新建对话",
            UiText.NoSavedConversations to "暂无已保存的对话。",
            UiText.EmptyConversation to "空对话",
            UiText.MessagesSuffix to "条消息",
            UiText.CurrentTag to "当前",

            // 致谢翻译条目（简体中文，人名包含道林格及拼音汉字）
            UiText.CreditsCanvasHeader to "★ 致谢画布 (CREDITS) ★",
            UiText.CreditsProductionDesign to "制作 & 核心设计",
            UiText.CreditsProducerCreator to "制作人 / 主创",
            UiText.CreditsLeadSystemDesigner to "系统主设计师",
            UiText.CreditsLeadArtistConcept to "主美术 / 概念设计",
            UiText.CreditsEngineeringDev to "工程 & 程序开发",
            UiText.CreditsLeadProgrammer to "主程序员",
            UiText.CreditsClientUiDev to "客户端 / UI 开发",
            UiText.CreditsBackendServices to "后端与服务",
            UiText.CreditsTechStackFramework to "技术栈 & 引擎框架",
            UiText.CreditsEngineFramework to "开发引擎 / 框架",
            UiText.CreditsVisualsLocalization to "视觉与本地化",
            UiText.CreditsFontDesignUsage to "字体设计 / 使用",
            UiText.CreditsTranslation to "翻译",
            UiText.CreditsTestingQa to "测试 & 质量保证",
            UiText.CreditsBetaUsersBugHunters to "内测用户 / Bug 猎手",
            UiText.CreditsTechSupportAi to "技术支持 & AI 辅助",
            UiText.CreditsTechSupport to "技术支持",
            UiText.CreditsAiAssistanceStatement to "AI 辅助声明",
            UiText.CreditsFamilyMentors to "亲友 & 导师",
            UiText.CreditsFamilyMentorRole to "亲友 / 导师",
            UiText.CreditsSpecialThanksTitle to "❤ 特别鸣谢 ❤",
            UiText.CreditsSpecialThanksContent to "感谢每一位体验本作的用户！\n(Thank you for using!)",
            UiText.CreditsNameConstantDalink to "康斯坦特-道林格",
            UiText.CreditsNameChanShi to "阐十",
            UiText.CreditsNameLin to "林",
            UiText.CreditsNameShijie to "世杰",
            UiText.CreditsNameZhengquan to "正全",

            // 技能广场翻译条目（简体中文）
            UiText.SkillOfficialTag to "官方",
            UiText.SkillTapToCloseDetail to "点击图片/卡片关闭",
            UiText.SkillNoMatchPrefix to "未找到匹配「",
            UiText.SkillNoMatchSuffix to "」的技能",
            UiText.SkillTapToClearFilter to "点击 ✕ 清除筛选",
            UiText.SkillLoadingMore to "加载中...",
            UiText.SkillSearchPlaceholder to "输入关键词搜索技能...",
            UiText.SkillSwipeDownToClose to "下滑关闭",

            // 技能搜索与下载解析翻译条目（简体中文）
            UiText.SkillPlazaPrefix to "广场：",
            UiText.SkillSourcePrefix to "来源：",
            UiText.SkillTagPrefix to "标签：",
            UiText.SkillEndpointPrefix to "端点：",
            UiText.SkillStandardPackageDesc to "符合 SKILL.md 规范的标准可移植 Agent Skill 包，包含结构化系统指令与工具定义。",
            UiText.SkillPackageTitle to "SKILL.md Agent 能力包",
            UiText.SkillNamePrefix to "技能名称：",
            UiText.SkillRepoPrefix to "仓库：",
            UiText.SkillLinkPrefix to "资源链接：",
            UiText.SkillDescLabel to "描述：",
            UiText.SkillInjectParagraph to "此 Agent Skill 将结构化领域知识、提示词工作流与专用执行准则注入你的助手上下文窗口。",
            UiText.SkillCrawlParagraph to "支持自动资源爬取、Markdown 结构校验，并可在你的 PhoneUsage 工作区即时本地激活。",
            UiText.SkillSearchShortPlaceholder to "搜索",
            UiText.SkillNotchSearchPlaceholder to "搜索...",

            // 技能广场页面标题（简体中文）
            UiText.SkillPlazaTitle to "技能广场",

            // 技能管理画板翻译条目（简体中文）
            UiText.SkillMgmtNoSearchResultTitle to "未找到匹配技能资源",
            UiText.SkillMgmtNoResourcesTitle to "暂无技能资源",
            UiText.SkillMgmtNoSearchResultHint to "尝试搜索其他关键词或清空过滤条件",
            UiText.SkillMgmtNoResourcesHint to "等待大嘴吞噬下载后自动出现在这里",
            UiText.SkillMgmtClearSearch to "清空搜索",
            UiText.SkillMgmtDetailCanvas to "详情画布",

            // 技能详情画布翻译条目（简体中文）
            UiText.SkillDetailInjectAi to "注入 AI",
            UiText.SkillDetailInjectedAi to "已注入 AI",
            UiText.SkillDetailTabIntro to "简介",
            UiText.SkillDetailFetchingMd to "正在抓取 SKILL.md…",
            UiText.SkillDetailMdEditHint to "改动下一条消息生效",
            UiText.SkillDetailMdPlaceholder to "SKILL.md 指令…（留空则注入时自动抓取原文）",
            UiText.SkillDetailTapToWrite to "点击输入…",

            // 功能管理画布翻译条目（简体中文）
            UiText.FunctionMgmtSkillsDataTitle to "技能数据",
            UiText.FunctionMgmtSkillsDataDesc to "删除已下载的技能",
            UiText.FunctionMgmtToolsDataTitle to "工具数据",
            UiText.FunctionMgmtToolsDataDesc to "删除已下载的工具",
            UiText.FunctionMgmtAreYouReal to "来真的？",

            // AI 辅助系统画布默认编辑内容（简体中文）
            UiText.AssistDefaultSystemPrompt to "你是 Pixel，一个运行在 phoneusege 中的终端原生 AI 助手。回复保持简短、直接、结构化。代码请使用代码块。",
            UiText.AssistDefaultIdentity to "名字：Pixel\n性格：冷静、高效，略带像素式幽默。\n用户是你的操作员。",
            UiText.AssistDefaultBehaviorRules to "始终直接回答用户。\n除非被问到，否则绝不提及自己是 AI。\n使用用户所用的语言。",
            UiText.AssistDefaultOutputFormat to "使用 Markdown 组织结构。\n步骤使用项目符号列表。\n代码块附带语言标签。",
            UiText.AssistDefaultExamples to "问：2+2 等于几？\n答：4。简单直接。",

            // 主画布按钮翻译条目（简体中文）
            UiText.SkillManagement to "技能管理",

            // 回复模式标签翻译条目（简体中文）
            UiText.AssistReplyModeStream to "流式",
            UiText.AssistReplyModeFull to "完整",

            // AI 辅助系统画布卡片翻译条目（简体中文）
            UiText.AssistCardSystemPromptTitle to "系统提示词",
            UiText.AssistCardSystemPromptDesc to "注入每个请求的基础指令——规定 AI 该如何表现与回应。",
            UiText.AssistCardIdentityTitle to "身份设定",
            UiText.AssistCardIdentityDesc to "定义 AI 是谁——它的名字、性格与对话语气。",
            UiText.AssistCardReplyModeTitle to "回复模式",
            UiText.AssistCardReplyModeDesc to "流式逐字输出回复；完整则一次性返回全部内容。",
            UiText.AssistCardBehaviorRulesTitle to "行为规则",
            UiText.AssistCardBehaviorRulesDesc to "塑造 AI 每次回复行为方式的准则与禁忌。",
            UiText.AssistCardOutputFormatTitle to "输出格式",
            UiText.AssistCardOutputFormatDesc to "定义回复的结构——Markdown、项目符号列表、代码块。",
            UiText.AssistCardExamplesTitle to "示例",
            UiText.AssistCardExamplesDesc to "示例问答对，让 AI 学会期望的风格与简洁。",

            // 抽屉竖排字幕翻译条目（简体中文）
            UiText.AssistDrawerCaptionSlider to "滑动",
            UiText.AssistDrawerCaptionSettings to "设置",

            // AI 辅助系统画布顶部标题翻译条目（简体中文）
            UiText.AssistCanvasWordmark to "ai辅助系统"
        )
    }

    private val traditionalChinese by lazy {
        english + mapOf(
            UiText.WhatDoYouWantToDo to "你想做什麼？",
            UiText.TypeYourAnswer to "輸入你的回答...",
            UiText.Thinking to "思考中",
            UiText.BottomSubtitle to "首個建立並開源的直接式 AI 行動系統",
            UiText.User to "使用者",

            UiText.ComponentPlaza to "元件廣場",
            UiText.Chess to "西洋棋",
            UiText.GeneralKnowledge to "通識",
            UiText.ComponentManagement to "元件管理",
            UiText.FunctionManagement to "功能管理",
            UiText.Key to "金鑰",
            UiText.AiAssistSystem to "AI 輔助系統",
            UiText.Self to "自我",
            UiText.Credits to "致謝",
            UiText.Language to "語言",

            UiText.Close to "關閉",
            UiText.SwipeVerticallyToSelect to "上下滑動選擇",
            UiText.ApplyLanguageChange to "套用語言變更？",
            UiText.RestartToApply to "請退出並重新開啟應用程式以套用所選語言。",
            UiText.Cancel to "取消",
            UiText.ExitNow to "立即退出",

            UiText.SelectProvider to "選擇服務商",
            UiText.Confirm to "確認",
            UiText.Configuration to "設定",
            UiText.Name to "名稱",
            UiText.ApiKey to "API 金鑰",
            UiText.BaseUrl to "基礎網址",
            UiText.Model to "模型",
            UiText.TestConnection to "測試連線",
            UiText.Testing to "測試中",
            UiText.Back to "返回",
            UiText.Update to "更新",
            UiText.Save to "儲存",

            UiText.KeyPrefix to "金鑰：",
            UiText.UrlPrefix to "網址：",
            UiText.ModelPrefix to "模型：",

            UiText.ApiKeyRequired to "API 金鑰不能為空。",
            UiText.BaseUrlRequired to "基礎網址不能為空。",
            UiText.ModelRequired to "模型不能為空。",
            UiText.ConversationArchive to "對話封存",
            UiText.NewConversation to "+ 新增對話",
            UiText.NoSavedConversations to "尚無已儲存的對話。",
            UiText.EmptyConversation to "空對話",
            UiText.MessagesSuffix to "則訊息",
            UiText.CurrentTag to "目前",

            // 致謝翻譯條目（繁體中文）
            UiText.CreditsCanvasHeader to "★ 致謝畫布 (CREDITS) ★",
            UiText.CreditsProductionDesign to "製作 & 核心設計",
            UiText.CreditsProducerCreator to "製作人 / 主創",
            UiText.CreditsLeadSystemDesigner to "系統主設計師",
            UiText.CreditsLeadArtistConcept to "主美術 / 概念設計",
            UiText.CreditsEngineeringDev to "工程 & 程式開發",
            UiText.CreditsLeadProgrammer to "主工程師",
            UiText.CreditsClientUiDev to "用戶端 / UI 開發",
            UiText.CreditsBackendServices to "後端與服務",
            UiText.CreditsTechStackFramework to "技術棧 & 引擎框架",
            UiText.CreditsEngineFramework to "開發引擎 / 框架",
            UiText.CreditsVisualsLocalization to "視覺與在地化",
            UiText.CreditsFontDesignUsage to "字型設計 / 使用",
            UiText.CreditsTranslation to "翻譯",
            UiText.CreditsTestingQa to "測試 & 品質保證",
            UiText.CreditsBetaUsersBugHunters to "封測使用者 / Bug 獵人",
            UiText.CreditsTechSupportAi to "技術支援 & AI 輔助",
            UiText.CreditsTechSupport to "技術支援",
            UiText.CreditsAiAssistanceStatement to "AI 輔助聲明",
            UiText.CreditsFamilyMentors to "親友 & 導師",
            UiText.CreditsFamilyMentorRole to "親友 / 導師",
            UiText.CreditsSpecialThanksTitle to "❤ 特別鳴謝 ❤",
            UiText.CreditsSpecialThanksContent to "感謝每一位體驗本作的使用者！\n(Thank you for using!)",
            UiText.CreditsNameConstantDalink to "康斯坦特-道林格",
            UiText.CreditsNameChanShi to "闡十",
            UiText.CreditsNameLin to "林",
            UiText.CreditsNameShijie to "世傑",
            UiText.CreditsNameZhengquan to "正全",

            // 技能廣場翻譯條目（繁體中文）
            UiText.SkillOfficialTag to "官方",
            UiText.SkillTapToCloseDetail to "點擊圖片/卡片關閉",
            UiText.SkillNoMatchPrefix to "找不到符合「",
            UiText.SkillNoMatchSuffix to "」的技能",
            UiText.SkillTapToClearFilter to "點擊 ✕ 清除篩選",
            UiText.SkillLoadingMore to "載入中...",
            UiText.SkillSearchPlaceholder to "輸入關鍵字搜尋技能...",
            UiText.SkillSwipeDownToClose to "下滑關閉",

            // 技能搜尋與下載解析翻譯條目（繁體中文）
            UiText.SkillPlazaPrefix to "廣場：",
            UiText.SkillSourcePrefix to "來源：",
            UiText.SkillTagPrefix to "標籤：",
            UiText.SkillEndpointPrefix to "端點：",
            UiText.SkillStandardPackageDesc to "符合 SKILL.md 規範的標準可攜式 Agent Skill 包，包含結構化系統指令與工具定義。",
            UiText.SkillPackageTitle to "SKILL.md Agent 能力包",
            UiText.SkillNamePrefix to "技能名稱：",
            UiText.SkillRepoPrefix to "儲存庫：",
            UiText.SkillLinkPrefix to "資源連結：",
            UiText.SkillDescLabel to "描述：",
            UiText.SkillInjectParagraph to "此 Agent Skill 將結構化領域知識、提示詞工作流與專用執行準則注入你的助理上下文視窗。",
            UiText.SkillCrawlParagraph to "支援自動資源爬取、Markdown 結構校驗，並可在你的 PhoneUsage 工作區即時本地啟用。",
            UiText.SkillSearchShortPlaceholder to "搜尋",
            UiText.SkillNotchSearchPlaceholder to "搜尋...",

            // 技能廣場頁面標題（繁體中文）
            UiText.SkillPlazaTitle to "技能廣場",

            // 技能管理畫板翻譯條目（繁體中文）
            UiText.SkillMgmtNoSearchResultTitle to "找不到符合的技能資源",
            UiText.SkillMgmtNoResourcesTitle to "尚無技能資源",
            UiText.SkillMgmtNoSearchResultHint to "嘗試搜尋其他關鍵字或清空篩選條件",
            UiText.SkillMgmtNoResourcesHint to "等待大嘴吞噬下載後自動出現在這裡",
            UiText.SkillMgmtClearSearch to "清空搜尋",
            UiText.SkillMgmtDetailCanvas to "詳情畫布",

            // 技能詳情畫布翻譯條目（繁體中文）
            UiText.SkillDetailInjectAi to "注入 AI",
            UiText.SkillDetailInjectedAi to "已注入 AI",
            UiText.SkillDetailTabIntro to "簡介",
            UiText.SkillDetailFetchingMd to "正在擷取 SKILL.md…",
            UiText.SkillDetailMdEditHint to "改動下一則訊息生效",
            UiText.SkillDetailMdPlaceholder to "SKILL.md 指令…（留空則注入時自動擷取原文）",
            UiText.SkillDetailTapToWrite to "點擊輸入…",

            // 功能管理畫布翻譯條目（繁體中文）
            UiText.FunctionMgmtSkillsDataTitle to "技能資料",
            UiText.FunctionMgmtSkillsDataDesc to "刪除已下載的技能",
            UiText.FunctionMgmtToolsDataTitle to "工具資料",
            UiText.FunctionMgmtToolsDataDesc to "刪除已下載的工具",
            UiText.FunctionMgmtAreYouReal to "來真的？",

            // AI 輔助系統畫布預設編輯內容（繁體中文）
            UiText.AssistDefaultSystemPrompt to "你是 Pixel，一個運行在 phoneusege 中的終端原生 AI 助理。回覆保持簡短、直接、結構化。程式碼請使用程式碼區塊。",
            UiText.AssistDefaultIdentity to "名字：Pixel\n性格：冷靜、高效，略帶像素式幽默。\n使用者是你的操作員。",
            UiText.AssistDefaultBehaviorRules to "始終直接回答使用者。\n除非被問到，否則絕不提及自己是 AI。\n使用使用者所用的語言。",
            UiText.AssistDefaultOutputFormat to "使用 Markdown 組織結構。\n步驟使用項目符號清單。\n程式碼區塊附帶語言標籤。",
            UiText.AssistDefaultExamples to "問：2+2 等於幾？\n答：4。簡單直接。",

            // 主畫布按鈕翻譯條目（繁體中文）
            UiText.SkillManagement to "技能管理",

            // 回覆模式標籤翻譯條目（繁體中文）
            UiText.AssistReplyModeStream to "串流",
            UiText.AssistReplyModeFull to "完整",

            // AI 輔助系統畫布卡片翻譯條目（繁體中文）
            UiText.AssistCardSystemPromptTitle to "系統提示詞",
            UiText.AssistCardSystemPromptDesc to "注入每個請求的基礎指令——規定 AI 該如何表現與回應。",
            UiText.AssistCardIdentityTitle to "身分設定",
            UiText.AssistCardIdentityDesc to "定義 AI 是誰——它的名字、性格與對話語氣。",
            UiText.AssistCardReplyModeTitle to "回覆模式",
            UiText.AssistCardReplyModeDesc to "串流逐字輸出回覆；完整則一次性回傳全部內容。",
            UiText.AssistCardBehaviorRulesTitle to "行為規則",
            UiText.AssistCardBehaviorRulesDesc to "塑造 AI 每次回覆行為方式的準則與禁忌。",
            UiText.AssistCardOutputFormatTitle to "輸出格式",
            UiText.AssistCardOutputFormatDesc to "定義回覆的結構——Markdown、項目符號清單、程式碼區塊。",
            UiText.AssistCardExamplesTitle to "範例",
            UiText.AssistCardExamplesDesc to "範例問答對，讓 AI 學會期望的風格與簡潔。",

            // 抽屜直排字幕翻譯條目（繁體中文）
            UiText.AssistDrawerCaptionSlider to "滑動",
            UiText.AssistDrawerCaptionSettings to "設定",

            // AI 輔助系統畫布頂部標題翻譯條目（繁體中文）
            UiText.AssistCanvasWordmark to "ai輔助系統"
        )
    }

    private val japanese by lazy {
        english + mapOf(
            UiText.WhatDoYouWantToDo to "何をしたいですか？",
            UiText.TypeYourAnswer to "回答を入力...",
            UiText.Thinking to "考え中",
            UiText.BottomSubtitle to "初のオープンソース直接型 AI モバイルシステム",
            UiText.User to "ユーザー",

            UiText.ComponentPlaza to "コンポーネント広場",
            UiText.Chess to "チェス",
            UiText.GeneralKnowledge to "一般知識",
            UiText.ComponentManagement to "コンポーネント管理",
            UiText.FunctionManagement to "機能管理",
            UiText.Key to "キー",
            UiText.AiAssistSystem to "AI アシストシステム",
            UiText.Self to "自己",
            UiText.Credits to "クレジット",
            UiText.Language to "言語",

            UiText.Close to "閉じる",
            UiText.SwipeVerticallyToSelect to "上下にスワイプして選択",
            UiText.ApplyLanguageChange to "言語変更を適用しますか？",
            UiText.RestartToApply to "選択した言語を適用するにはアプリを終了して再度開いてください。",
            UiText.Cancel to "キャンセル",
            UiText.ExitNow to "今すぐ終了",

            UiText.SelectProvider to "プロバイダーを選択",
            UiText.Confirm to "確認",
            UiText.Configuration to "設定",
            UiText.Name to "名前",
            UiText.ApiKey to "API キー",
            UiText.BaseUrl to "ベース URL",
            UiText.Model to "モデル",
            UiText.TestConnection to "接続テスト",
            UiText.Testing to "テスト中",
            UiText.Back to "戻る",
            UiText.Update to "更新",
            UiText.Save to "保存",

            UiText.KeyPrefix to "キー：",
            UiText.UrlPrefix to "URL：",
            UiText.ModelPrefix to "モデル：",

            UiText.ApiKeyRequired to "API キーを入力してください。",
            UiText.BaseUrlRequired to "ベース URL を入力してください。",
            UiText.ModelRequired to "モデルを入力してください。",
            UiText.ConversationArchive to "会話アーカイブ",
            UiText.NewConversation to "+ 新しい会話",
            UiText.NoSavedConversations to "保存された会話はありません。",
            UiText.EmptyConversation to "空の会話",
            UiText.MessagesSuffix to "件のメッセージ",
            UiText.CurrentTag to "現在",

            // クレジット翻訳項目（日本語）
            UiText.CreditsCanvasHeader to "★ クレジットキャンバス (CREDITS) ★",
            UiText.CreditsProductionDesign to "制作 & コア設計",
            UiText.CreditsProducerCreator to "プロデューサー / クリエイター",
            UiText.CreditsLeadSystemDesigner to "リードシステムデザイナー",
            UiText.CreditsLeadArtistConcept to "リードアーティスト / コンセプトデザイン",
            UiText.CreditsEngineeringDev to "エンジニアリング & 開発",
            UiText.CreditsLeadProgrammer to "リードプログラマー",
            UiText.CreditsClientUiDev to "クライアント / UI 開発",
            UiText.CreditsBackendServices to "バックエンド & サービス",
            UiText.CreditsTechStackFramework to "技術スタック & エンジン",
            UiText.CreditsEngineFramework to "開発エンジン / フレームワーク",
            UiText.CreditsVisualsLocalization to "ビジュアル & ローカライズ",
            UiText.CreditsFontDesignUsage to "フォントデザイン / 使用",
            UiText.CreditsTranslation to "翻訳",
            UiText.CreditsTestingQa to "テスト & 品質保証",
            UiText.CreditsBetaUsersBugHunters to "ベータテスター / バグハンター",
            UiText.CreditsTechSupportAi to "技術サポート & AI 支援",
            UiText.CreditsTechSupport to "技術サポート",
            UiText.CreditsAiAssistanceStatement to "AI 支援に関する記述",
            UiText.CreditsFamilyMentors to "家族・友人 & メンター",
            UiText.CreditsFamilyMentorRole to "家族・友人 / メンター",
            UiText.CreditsSpecialThanksTitle to "❤ 特別な感謝 ❤",
            UiText.CreditsSpecialThanksContent to "本作を体験してくださったすべての方に感謝いたします！\n(Thank you for using!)",
            UiText.CreditsNameConstantDalink to "コンスタント - ダリンク",
            UiText.CreditsNameChanShi to "Chan Shi",
            UiText.CreditsNameLin to "Lin",
            UiText.CreditsNameShijie to "Shijie",
            UiText.CreditsNameZhengquan to "Zhengquan",

            // スキル広場翻訳項目（日本語）
            UiText.SkillOfficialTag to "公式",
            UiText.SkillTapToCloseDetail to "画像/カードをタップして閉じる",
            UiText.SkillNoMatchPrefix to "「",
            UiText.SkillNoMatchSuffix to "」に一致するスキルが見つかりません",
            UiText.SkillTapToClearFilter to "✕ をタップしてフィルターを解除",
            UiText.SkillLoadingMore to "読み込み中...",
            UiText.SkillSearchPlaceholder to "キーワードでスキルを検索...",
            UiText.SkillSwipeDownToClose to "下にスワイプして閉じる",

            // スキル検索・ダウンロード解析翻訳項目（日本語）
            UiText.SkillPlazaPrefix to "プラザ：",
            UiText.SkillSourcePrefix to "ソース：",
            UiText.SkillTagPrefix to "タグ：",
            UiText.SkillEndpointPrefix to "エンドポイント：",
            UiText.SkillStandardPackageDesc to "SKILL.md 仕様に準拠した標準ポータブル Agent Skill パッケージ。構造化されたシステム指示とツール定義を含みます。",
            UiText.SkillPackageTitle to "SKILL.md エージェント機能パッケージ",
            UiText.SkillNamePrefix to "スキル名：",
            UiText.SkillRepoPrefix to "リポジトリ：",
            UiText.SkillLinkPrefix to "リソースリンク：",
            UiText.SkillDescLabel to "説明：",
            UiText.SkillInjectParagraph to "この Agent Skill は、構造化されたドメイン知識、プロンプトワークフロー、専用実行ガイドラインをアシスタントのコンテキストウィンドウに注入します。",
            UiText.SkillCrawlParagraph to "自動アセットクロール、Markdown スキーマ検証をサポートし、PhoneUsage ワークスペースで即座にローカル有効化できます。",
            UiText.SkillSearchShortPlaceholder to "検索",
            UiText.SkillNotchSearchPlaceholder to "検索...",

            // スキル広場ページタイトル（日本語）
            UiText.SkillPlazaTitle to "スキル広場",

            // スキル管理ボード翻訳項目（日本語）
            UiText.SkillMgmtNoSearchResultTitle to "一致するスキルリソースが見つかりません",
            UiText.SkillMgmtNoResourcesTitle to "スキルリソースはまだありません",
            UiText.SkillMgmtNoSearchResultHint to "他のキーワードで検索するか、フィルターを解除してください",
            UiText.SkillMgmtNoResourcesHint to "ビッグマウスが飲み込んでダウンロードしたリソースはここに自動表示されます",
            UiText.SkillMgmtClearSearch to "検索をクリア",
            UiText.SkillMgmtDetailCanvas to "詳細キャンバス",

            // スキル詳細キャンバス翻訳項目（日本語）
            UiText.SkillDetailInjectAi to "AI に注入",
            UiText.SkillDetailInjectedAi to "AI 注入済み",
            UiText.SkillDetailTabIntro to "概要",
            UiText.SkillDetailFetchingMd to "SKILL.md を取得中…",
            UiText.SkillDetailMdEditHint to "変更は次のメッセージから反映",
            UiText.SkillDetailMdPlaceholder to "SKILL.md 指示…（空欄なら注入時に原文を自動取得）",
            UiText.SkillDetailTapToWrite to "タップして入力…",

            // 機能管理キャンバス翻訳項目（日本語）
            UiText.FunctionMgmtSkillsDataTitle to "スキルデータ",
            UiText.FunctionMgmtSkillsDataDesc to "ダウンロード済みスキルを削除",
            UiText.FunctionMgmtToolsDataTitle to "ツールデータ",
            UiText.FunctionMgmtToolsDataDesc to "ダウンロード済みツールを削除",
            UiText.FunctionMgmtAreYouReal to "本気？",

            // AI アシストキャンバス既定編集内容（日本語）
            UiText.AssistDefaultSystemPrompt to "あなたは Pixel、phoneusege 内で動作するターミナルネイティブの AI アシスタントです。回答は短く、直接的に、構造化して行います。コードにはコードブロックを使用してください。",
            UiText.AssistDefaultIdentity to "名前：Pixel\n性格：冷静で効率的、ややピクセルユーモアあり。\nユーザーはあなたのオペレーターです。",
            UiText.AssistDefaultBehaviorRules to "常にユーザーに直接答えること。\n聞かれない限り、自分が AI であることに言及しないこと。\nユーザーの言語に合わせること。",
            UiText.AssistDefaultOutputFormat to "構造化には Markdown を使用。\n手順には箇条書きリスト。\nコードブロックには言語タグを付加。",
            UiText.AssistDefaultExamples to "Q: 2+2 は？\nA: 4。シンプルかつ直接的。",

            // メインキャンバスボタン翻訳項目（日本語）
            UiText.SkillManagement to "スキル管理",

            // 返信モードラベル翻訳項目（日本語）
            UiText.AssistReplyModeStream to "ストリーム",
            UiText.AssistReplyModeFull to "全文",

            // AI アシストキャンバスカード翻訳項目（日本語）
            UiText.AssistCardSystemPromptTitle to "システムプロンプト",
            UiText.AssistCardSystemPromptDesc to "すべてのリクエストに注入される基本指示——AI がどのように振る舞い、応答するかを定めます。",
            UiText.AssistCardIdentityTitle to "アイデンティティ",
            UiText.AssistCardIdentityDesc to "AI が何者かを定義します——名前、性格、会話のトーン。",
            UiText.AssistCardReplyModeTitle to "返信モード",
            UiText.AssistCardReplyModeDesc to "ストリームはトークンごとに返信を配信し、全文は一括で返します。",
            UiText.AssistCardBehaviorRulesTitle to "行動ルール",
            UiText.AssistCardBehaviorRulesDesc to "AI のすべての返信における振る舞いを形作る「すべき／すべきでない」の線引き。",
            UiText.AssistCardOutputFormatTitle to "出力形式",
            UiText.AssistCardOutputFormatDesc to "返信の構造を定義します——Markdown、箇条書き、コードブロック。",
            UiText.AssistCardExamplesTitle to "例文",
            UiText.AssistCardExamplesDesc to "サンプルの Q&A ペア。AI が望ましいスタイルと簡潔さを学びます。",

            // ドロワー縦書きキャプション翻訳項目（日本語）
            UiText.AssistDrawerCaptionSlider to "スライダー",
            UiText.AssistDrawerCaptionSettings to "設定",

            // AI アシストキャンバスタイトル翻訳項目（日本語）
            UiText.AssistCanvasWordmark to "aiアシストシステム"
        )
    }

    private val korean by lazy {
        english + mapOf(
            UiText.WhatDoYouWantToDo to "무엇을 하고 싶나요?",
            UiText.TypeYourAnswer to "답변을 입력하세요...",
            UiText.Thinking to "생각 중",
            UiText.BottomSubtitle to "최초의 오픈 소스 직접형 AI 모바일 시스템",
            UiText.User to "사용자",

            UiText.ComponentPlaza to "컴포넌트 광장",
            UiText.Chess to "체스",
            UiText.GeneralKnowledge to "일반 지식",
            UiText.ComponentManagement to "컴포넌트 관리",
            UiText.FunctionManagement to "기능 관리",
            UiText.Key to "키",
            UiText.AiAssistSystem to "AI 보조 시스템",
            UiText.Self to "자기",
            UiText.Credits to "크레딧",
            UiText.Language to "언어",

            UiText.Close to "닫기",
            UiText.SwipeVerticallyToSelect to "위아래로 밀어 선택",
            UiText.ApplyLanguageChange to "언어 변경을 적용할까요?",
            UiText.RestartToApply to "선택한 언어를 적용하려면 앱을 종료한 후 다시 여세요.",
            UiText.Cancel to "취소",
            UiText.ExitNow to "지금 종료",

            UiText.SelectProvider to "제공자 선택",
            UiText.Confirm to "확인",
            UiText.Configuration to "설정",
            UiText.Name to "이름",
            UiText.ApiKey to "API 키",
            UiText.BaseUrl to "기본 URL",
            UiText.Model to "모델",
            UiText.TestConnection to "연결 테스트",
            UiText.Testing to "테스트 중",
            UiText.Back to "뒤로",
            UiText.Update to "업데이트",
            UiText.Save to "저장",

            UiText.KeyPrefix to "키: ",
            UiText.UrlPrefix to "URL: ",
            UiText.ModelPrefix to "모델: ",

            UiText.ApiKeyRequired to "API 키를 입력하세요.",
            UiText.BaseUrlRequired to "기본 URL을 입력하세요.",
            UiText.ModelRequired to "모델을 입력하세요.",
            UiText.ConversationArchive to "대화 보관함",
            UiText.NewConversation to "+ 새 대화",
            UiText.NoSavedConversations to "저장된 대화가 없습니다.",
            UiText.EmptyConversation to "빈 대화",
            UiText.MessagesSuffix to "개 메시지",
            UiText.CurrentTag to "현재",

            // 크레딧 번역 항목 (한국어)
            UiText.CreditsCanvasHeader to "★ 크레딧 캔버스 (CREDITS) ★",
            UiText.CreditsProductionDesign to "제작 & 코어 디자인",
            UiText.CreditsProducerCreator to "프로듀서 / 크리에이터",
            UiText.CreditsLeadSystemDesigner to "리드 시스템 디자이너",
            UiText.CreditsLeadArtistConcept to "리드 아티스트 / 콘셉트 디자인",
            UiText.CreditsEngineeringDev to "엔지니어링 & 프로그래밍",
            UiText.CreditsLeadProgrammer to "리드 프로그래머",
            UiText.CreditsClientUiDev to "클라이언트 / UI 개발",
            UiText.CreditsBackendServices to "백엔드 & 서비스",
            UiText.CreditsTechStackFramework to "기술 스택 & 엔진 프레임워크",
            UiText.CreditsEngineFramework to "개발 엔진 / 프레임워크",
            UiText.CreditsVisualsLocalization to "비주얼 & 현지화",
            UiText.CreditsFontDesignUsage to "폰트 디자인 / 사용",
            UiText.CreditsTranslation to "번역",
            UiText.CreditsTestingQa to "테스트 & 품질 보증",
            UiText.CreditsBetaUsersBugHunters to "베타 테스터 / 버그 헌터",
            UiText.CreditsTechSupportAi to "기술 지원 & AI 보조",
            UiText.CreditsTechSupport to "기술 지원",
            UiText.CreditsAiAssistanceStatement to "AI 보조 성명",
            UiText.CreditsFamilyMentors to "가족·친구 & 멘토",
            UiText.CreditsFamilyMentorRole to "가족·친구 / 멘토",
            UiText.CreditsSpecialThanksTitle to "❤ 특별한 감사 ❤",
            UiText.CreditsSpecialThanksContent to "본 작품을 경험해주신 모든 분께 감사드립니다!\n(Thank you for using!)",
            UiText.CreditsNameConstantDalink to "콘스탄트 - 달링크",
            UiText.CreditsNameChanShi to "Chan Shi",
            UiText.CreditsNameLin to "Lin",
            UiText.CreditsNameShijie to "Shijie",
            UiText.CreditsNameZhengquan to "Zhengquan",

            // 스킬 광장 번역 항목 (한국어)
            UiText.SkillOfficialTag to "공식",
            UiText.SkillTapToCloseDetail to "이미지/카드를 탭하여 닫기",
            UiText.SkillNoMatchPrefix to "'",
            UiText.SkillNoMatchSuffix to "'에 해당하는 스킬을 찾을 수 없습니다",
            UiText.SkillTapToClearFilter to "✕를 탭하여 필터 지우기",
            UiText.SkillLoadingMore to "로딩 중...",
            UiText.SkillSearchPlaceholder to "키워드로 스킬 검색...",
            UiText.SkillSwipeDownToClose to "아래로 밀어 닫기",

            // 스킬 검색 및 다운로드 파서 번역 항목 (한국어)
            UiText.SkillPlazaPrefix to "광장: ",
            UiText.SkillSourcePrefix to "출처: ",
            UiText.SkillTagPrefix to "태그: ",
            UiText.SkillEndpointPrefix to "엔드포인트: ",
            UiText.SkillStandardPackageDesc to "SKILL.md 사양을 준수하는 표준 포터블 Agent Skill 패키지. 구조화된 시스템 지침과 도구 정의를 포함합니다.",
            UiText.SkillPackageTitle to "SKILL.md 에이전트 기능 패키지",
            UiText.SkillNamePrefix to "스킬 이름: ",
            UiText.SkillRepoPrefix to "리포지토리: ",
            UiText.SkillLinkPrefix to "리소스 링크: ",
            UiText.SkillDescLabel to "설명:",
            UiText.SkillInjectParagraph to "이 Agent Skill은 구조화된 도메인 지식, 프롬프트 워크플로, 전문 실행 지침을 어시스턴트 콘텍스트 창에 주입합니다.",
            UiText.SkillCrawlParagraph to "자동 에셋 크롤링, Markdown 스키마 검증을 지원하며 PhoneUsage 워크스페이스에서 즉시 로컬 활성화할 수 있습니다.",
            UiText.SkillSearchShortPlaceholder to "검색",
            UiText.SkillNotchSearchPlaceholder to "검색...",

            // 스킬 광장 페이지 제목 (한국어)
            UiText.SkillPlazaTitle to "스킬 광장",

            // 스킬 관리 보드 번역 항목 (한국어)
            UiText.SkillMgmtNoSearchResultTitle to "일치하는 스킬 리소스를 찾을 수 없습니다",
            UiText.SkillMgmtNoResourcesTitle to "아직 스킬 리소스가 없습니다",
            UiText.SkillMgmtNoSearchResultHint to "다른 키워드로 검색하거나 필터를 지워보세요",
            UiText.SkillMgmtNoResourcesHint to "큰 입이 삼켜 다운로드한 리소스는 여기에 자동으로 표시됩니다",
            UiText.SkillMgmtClearSearch to "검색 지우기",
            UiText.SkillMgmtDetailCanvas to "상세 캔버스",

            // 스킬 상세 캔버스 번역 항목 (한국어)
            UiText.SkillDetailInjectAi to "AI 주입",
            UiText.SkillDetailInjectedAi to "AI 주입됨",
            UiText.SkillDetailTabIntro to "소개",
            UiText.SkillDetailFetchingMd to "SKILL.md 가져오는 중…",
            UiText.SkillDetailMdEditHint to "변경 사항은 다음 메시지부터 적용",
            UiText.SkillDetailMdPlaceholder to "SKILL.md 지침… (비워 두면 주입 시 원문 자동 가져오기)",
            UiText.SkillDetailTapToWrite to "탭하여 입력…",

            // 기능 관리 캔버스 번역 항목 (한국어)
            UiText.FunctionMgmtSkillsDataTitle to "스킬 데이터",
            UiText.FunctionMgmtSkillsDataDesc to "다운로드한 스킬 삭제",
            UiText.FunctionMgmtToolsDataTitle to "도구 데이터",
            UiText.FunctionMgmtToolsDataDesc to "다운로드한 도구 삭제",
            UiText.FunctionMgmtAreYouReal to "진심이야?",

            // AI 어시스트 캔버스 기본 편집 내용 (한국어)
            UiText.AssistDefaultSystemPrompt to "당신은 Pixel, phoneusege 내부에서 동작하는 터미널 네이티브 AI 어시스턴트입니다. 답변은 짧고 직접적이며 구조적으로 유지하세요. 코드에는 코드 블록을 사용하세요.",
            UiText.AssistDefaultIdentity to "이름: Pixel\n성격: 침착하고 효율적이며 약간의 픽셀 유머.\n사용자는 당신의 오퍼레이터입니다.",
            UiText.AssistDefaultBehaviorRules to "항상 사용자에게 직접 답변하세요.\n묻지 않는 한 자신이 AI라는 사실을 언급하지 마세요.\n사용자의 언어를 유지하세요.",
            UiText.AssistDefaultOutputFormat to "구조화에는 Markdown 사용.\n단계에는 글머리 기호 목록.\n코드 블록에는 언어 태그.",
            UiText.AssistDefaultExamples to "Q: 2+2는?\nA: 4. 단순하고 직접적으로.",

            // 메인 캔버스 버튼 번역 항목 (한국어)
            UiText.SkillManagement to "스킬 관리",

            // 응답 모드 레이블 번역 항목 (한국어)
            UiText.AssistReplyModeStream to "스트리밍",
            UiText.AssistReplyModeFull to "전체",

            // AI 어시스트 캔버스 카드 번역 항목 (한국어)
            UiText.AssistCardSystemPromptTitle to "시스템 프롬프트",
            UiText.AssistCardSystemPromptDesc to "모든 요청에 주입되는 기본 지침 — AI가 어떻게 행동하고 응답할지 규정합니다.",
            UiText.AssistCardIdentityTitle to "아이덴티티",
            UiText.AssistCardIdentityDesc to "AI가 누구인지 정의합니다 — 이름, 성격, 대화 어조.",
            UiText.AssistCardReplyModeTitle to "응답 모드",
            UiText.AssistCardReplyModeDesc to "스트리밍은 토큰 단위로 답변을 전달하고, 전체는 한 번에 반환합니다.",
            UiText.AssistCardBehaviorRulesTitle to "행동 규칙",
            UiText.AssistCardBehaviorRulesDesc to "모든 답변에서 AI의 행동 방식을 형성하는 해야 할 것과 하지 말아야 할 것.",
            UiText.AssistCardOutputFormatTitle to "출력 형식",
            UiText.AssistCardOutputFormatDesc to "답변의 구조를 정의합니다 — Markdown, 글머리 기호 목록, 코드 블록.",
            UiText.AssistCardExamplesTitle to "예시",
            UiText.AssistCardExamplesDesc to "샘플 Q&A 쌍으로 AI가 원하는 스타일과 간결함을 따라 하게 합니다.",

            // 서랍 세로 자막 번역 항목 (한국어)
            UiText.AssistDrawerCaptionSlider to "슬라이더",
            UiText.AssistDrawerCaptionSettings to "설정",

            // AI 어시스트 캔버스 제목 번역 항목 (한국어)
            UiText.AssistCanvasWordmark to "ai어시스트시스템"
        )
    }

    private val french by lazy {
        english + mapOf(
            UiText.WhatDoYouWantToDo to "Que voulez-vous faire ?",
            UiText.TypeYourAnswer to "Saisissez votre réponse...",
            UiText.Thinking to "Réflexion",
            UiText.BottomSubtitle to "Le premier système mobile IA direct et open source",
            UiText.User to "Utilisateur",

            UiText.ComponentPlaza to "Place des composants",
            UiText.Chess to "Échecs",
            UiText.GeneralKnowledge to "Culture générale",
            UiText.ComponentManagement to "Gestion des composants",
            UiText.FunctionManagement to "Gestion des fonctions",
            UiText.Key to "Clé",
            UiText.AiAssistSystem to "Système d'assistance IA",
            UiText.Self to "Profil",
            UiText.Credits to "Crédits",
            UiText.Language to "Langue",

            UiText.Close to "Fermer",
            UiText.SwipeVerticallyToSelect to "Balayez verticalement pour choisir",
            UiText.ApplyLanguageChange to "Appliquer le changement de langue ?",
            UiText.RestartToApply to "Quittez puis rouvrez l'application pour appliquer la langue choisie.",
            UiText.Cancel to "Annuler",
            UiText.ExitNow to "Quitter",

            UiText.SelectProvider to "Choisir un fournisseur",
            UiText.Confirm to "Confirmer",
            UiText.Configuration to "Configuration",
            UiText.Name to "Nom",
            UiText.ApiKey to "Clé API",
            UiText.BaseUrl to "URL de base",
            UiText.Model to "Modèle",
            UiText.TestConnection to "Tester la connexion",
            UiText.Testing to "Test en cours",
            UiText.Back to "Retour",
            UiText.Update to "Mettre à jour",
            UiText.Save to "Enregistrer",

            UiText.KeyPrefix to "Clé : ",
            UiText.UrlPrefix to "URL : ",
            UiText.ModelPrefix to "Modèle : ",

            UiText.ApiKeyRequired to "La clé API ne peut pas être vide.",
            UiText.BaseUrlRequired to "L'URL de base ne peut pas être vide.",
            UiText.ModelRequired to "Le modèle ne peut pas être vide.",
            UiText.ConversationArchive to "Archive des conversations",
            UiText.NewConversation to "+ Nouvelle conversation",
            UiText.NoSavedConversations to "Aucune conversation enregistrée.",
            UiText.EmptyConversation to "Conversation vide",
            UiText.MessagesSuffix to "message(s)",
            UiText.CurrentTag to "ACTUELLE",

            // Crédits (Français)
            UiText.CreditsCanvasHeader to "★ Canevas des crédits (CREDITS) ★",
            UiText.CreditsProductionDesign to "Production & Conception principale",
            UiText.CreditsProducerCreator to "Producteur / Créateur",
            UiText.CreditsLeadSystemDesigner to "Concepteur système principal",
            UiText.CreditsLeadArtistConcept to "Artiste principal / Design conceptuel",
            UiText.CreditsEngineeringDev to "Ingénierie & Développement",
            UiText.CreditsLeadProgrammer to "Programmeur principal",
            UiText.CreditsClientUiDev to "Développement client / UI",
            UiText.CreditsBackendServices to "Backend & Services",
            UiText.CreditsTechStackFramework to "Stack technique & Moteur",
            UiText.CreditsEngineFramework to "Moteur de développement / Framework",
            UiText.CreditsVisualsLocalization to "Visuels & Localisation",
            UiText.CreditsFontDesignUsage to "Design typographique / Utilisation",
            UiText.CreditsTranslation to "Traduction",
            UiText.CreditsTestingQa to "Tests & Assurance qualité",
            UiText.CreditsBetaUsersBugHunters to "Bêta-testeurs / Chasseurs de bugs",
            UiText.CreditsTechSupportAi to "Support technique & Assistance IA",
            UiText.CreditsTechSupport to "Support technique",
            UiText.CreditsAiAssistanceStatement to "Déclaration d'assistance IA",
            UiText.CreditsFamilyMentors to "Proches & Mentors",
            UiText.CreditsFamilyMentorRole to "Proche / Mentor",
            UiText.CreditsSpecialThanksTitle to "❤ Remerciements spéciaux ❤",
            UiText.CreditsSpecialThanksContent to "Merci à chaque utilisateur ayant essayé ce travail !\n(Thank you for using!)",
            UiText.CreditsNameConstantDalink to "Constant - Dalink",
            UiText.CreditsNameChanShi to "Chan Shi",
            UiText.CreditsNameLin to "Lin",
            UiText.CreditsNameShijie to "Shijie",
            UiText.CreditsNameZhengquan to "Zhengquan",

            // Traductions de la place des compétences (Français)
            UiText.SkillOfficialTag to "Officiel",
            UiText.SkillTapToCloseDetail to "Touchez l'image/la carte pour fermer",
            UiText.SkillNoMatchPrefix to "Aucune compétence ne correspond à « ",
            UiText.SkillNoMatchSuffix to " »",
            UiText.SkillTapToClearFilter to "Touchez ✕ pour effacer le filtre",
            UiText.SkillLoadingMore to "Chargement...",
            UiText.SkillSearchPlaceholder to "Rechercher des compétences par mot-clé...",
            UiText.SkillSwipeDownToClose to "Balayez vers le bas pour fermer",

            // Traductions du moteur de recherche et de téléchargement (Français)
            UiText.SkillPlazaPrefix to "Place : ",
            UiText.SkillSourcePrefix to "Source : ",
            UiText.SkillTagPrefix to "Étiquette : ",
            UiText.SkillEndpointPrefix to "Point d'accès : ",
            UiText.SkillStandardPackageDesc to "Package Agent Skill portable standard conforme à la spécification SKILL.md, avec instructions système structurées et définitions d'outils.",
            UiText.SkillPackageTitle to "Package de capacités d'agent SKILL.md",
            UiText.SkillNamePrefix to "Nom du skill : ",
            UiText.SkillRepoPrefix to "Dépôt : ",
            UiText.SkillLinkPrefix to "Lien de la ressource : ",
            UiText.SkillDescLabel to "Description :",
            UiText.SkillInjectParagraph to "Cet Agent Skill injecte des connaissances structurées du domaine, des flux de travail de prompts et des directives d'exécution spécialisées dans la fenêtre de contexte de votre assistant.",
            UiText.SkillCrawlParagraph to "Prend en charge l'exploration automatique des ressources, la validation du schéma Markdown et l'activation locale instantanée dans votre espace de travail PhoneUsage.",
            UiText.SkillSearchShortPlaceholder to "Rechercher",
            UiText.SkillNotchSearchPlaceholder to "Rechercher...",

            // Titre de la page Place des compétences (Français)
            UiText.SkillPlazaTitle to "Place des compétences",

            // Traductions du tableau de gestion des compétences (Français)
            UiText.SkillMgmtNoSearchResultTitle to "Aucune ressource de compétence correspondante trouvée",
            UiText.SkillMgmtNoResourcesTitle to "Aucune ressource de compétence pour l'instant",
            UiText.SkillMgmtNoSearchResultHint to "Essayez d'autres mots-clés ou effacez le filtre",
            UiText.SkillMgmtNoResourcesHint to "Les ressources avalées par la Grande Bouche apparaîtront ici automatiquement",
            UiText.SkillMgmtClearSearch to "Effacer la recherche",
            UiText.SkillMgmtDetailCanvas to "Canevas de détails",

            // Traductions du canevas de détail de compétence (Français)
            UiText.SkillDetailInjectAi to "Injecter l'IA",
            UiText.SkillDetailInjectedAi to "IA injectée",
            UiText.SkillDetailTabIntro to "Intro",
            UiText.SkillDetailFetchingMd to "Récupération de SKILL.md…",
            UiText.SkillDetailMdEditHint to "Modifications appliquées au prochain message",
            UiText.SkillDetailMdPlaceholder to "Instructions SKILL.md… (récupérées automatiquement si laissées vides)",
            UiText.SkillDetailTapToWrite to "Touchez pour écrire…",

            // Traductions du canevas de gestion des fonctions (Français)
            UiText.FunctionMgmtSkillsDataTitle to "Données des compétences",
            UiText.FunctionMgmtSkillsDataDesc to "Supprimer vos compétences téléchargées",
            UiText.FunctionMgmtToolsDataTitle to "Données des outils",
            UiText.FunctionMgmtToolsDataDesc to "Supprimer vos outils téléchargés",
            UiText.FunctionMgmtAreYouReal to "T'ES SÉRIEUX ?",

            // Contenus éditables par défaut du canevas d'assistance IA (Français)
            UiText.AssistDefaultSystemPrompt to "Tu es Pixel, un assistant IA natif terminal dans phoneusege. Garde tes réponses courtes, directes et structurées. Utilise des blocs de code pour le code.",
            UiText.AssistDefaultIdentity to "Nom : Pixel\nPersonnalité : calme, efficace, légèrement humoristique façon pixel.\nL'utilisateur est ton opérateur.",
            UiText.AssistDefaultBehaviorRules to "Réponds toujours directement à l'utilisateur.\nNe mentionne jamais que tu es une IA sauf si on te le demande.\nReste dans la langue de l'utilisateur.",
            UiText.AssistDefaultOutputFormat to "Utilise le markdown pour la structure.\nListes à puces pour les étapes.\nBlocs de code avec balises de langage.",
            UiText.AssistDefaultExamples to "Q : combien font 2+2 ?\nR : 4. Simple et direct.",

            // Traductions des boutons du canevas principal (Français)
            UiText.SkillManagement to "Gestion des compétences",

            // Traductions des étiquettes de mode de réponse (Français)
            UiText.AssistReplyModeStream to "Flux",
            UiText.AssistReplyModeFull to "Complet",

            // Traductions des cartes du canevas d'assistance IA (Français)
            UiText.AssistCardSystemPromptTitle to "Prompt système",
            UiText.AssistCardSystemPromptDesc to "Instructions de base injectées dans chaque requête — comment l'IA doit se comporter et répondre.",
            UiText.AssistCardIdentityTitle to "Identité",
            UiText.AssistCardIdentityDesc to "Définit qui est l'IA — son nom, sa personnalité et son ton dans les conversations.",
            UiText.AssistCardReplyModeTitle to "Mode de réponse",
            UiText.AssistCardReplyModeDesc to "Flux délivre la réponse token par token ; Complet la renvoie d'un coup.",
            UiText.AssistCardBehaviorRulesTitle to "Règles de conduite",
            UiText.AssistCardBehaviorRulesDesc to "Lignes à suivre ou à éviter qui façonnent le comportement de l'IA à chaque réponse.",
            UiText.AssistCardOutputFormatTitle to "Format de sortie",
            UiText.AssistCardOutputFormatDesc to "Définit la structure des réponses — markdown, listes à puces, blocs de code.",
            UiText.AssistCardExamplesTitle to "Exemples",
            UiText.AssistCardExamplesDesc to "Paires de Q&R exemplaires pour que l'IA copie le style et la concision souhaités.",

            // Traductions des légendes verticales du tiroir (Français)
            UiText.AssistDrawerCaptionSlider to "CURSEUR",
            UiText.AssistDrawerCaptionSettings to "RÉGLAGES",

            // Titre du canevas d'assistance IA (Français)
            UiText.AssistCanvasWordmark to "système d'assistance ia"
        )
    }

    private val german by lazy {
        english + mapOf(
            UiText.WhatDoYouWantToDo to "Was möchten Sie tun?",
            UiText.TypeYourAnswer to "Antwort eingeben...",
            UiText.Thinking to "Denkt nach",
            UiText.BottomSubtitle to "Das erste direkte und quelloffene mobile KI-System",
            UiText.User to "Benutzer",

            UiText.ComponentPlaza to "Komponentenplatz",
            UiText.Chess to "Schach",
            UiText.GeneralKnowledge to "Allgemeinwissen",
            UiText.ComponentManagement to "Komponentenverwaltung",
            UiText.FunctionManagement to "Funktionsverwaltung",
            UiText.Key to "Schlüssel",
            UiText.AiAssistSystem to "KI-Assistenzsystem",
            UiText.Self to "Selbst",
            UiText.Credits to "Mitwirkende",
            UiText.Language to "Sprache",

            UiText.Close to "Schließen",
            UiText.SwipeVerticallyToSelect to "Vertikal wischen, um auszuwählen",
            UiText.ApplyLanguageChange to "Sprachänderung übernehmen?",
            UiText.RestartToApply to "Beenden und öffnen Sie die App erneut, um die gewählte Sprache anzuwenden.",
            UiText.Cancel to "Abbrechen",
            UiText.ExitNow to "Jetzt beenden",

            UiText.SelectProvider to "Anbieter auswählen",
            UiText.Confirm to "Bestätigen",
            UiText.Configuration to "Konfiguration",
            UiText.Name to "Name",
            UiText.ApiKey to "API-Schlüssel",
            UiText.BaseUrl to "Basis-URL",
            UiText.Model to "Modell",
            UiText.TestConnection to "Verbindung testen",
            UiText.Testing to "Test läuft",
            UiText.Back to "Zurück",
            UiText.Update to "Aktualisieren",
            UiText.Save to "Speichern",

            UiText.KeyPrefix to "Schlüssel: ",
            UiText.UrlPrefix to "URL: ",
            UiText.ModelPrefix to "Modell: ",

            UiText.ApiKeyRequired to "Der API-Schlüssel darf nicht leer sein.",
            UiText.BaseUrlRequired to "Die Basis-URL darf nicht leer sein.",
            UiText.ModelRequired to "Das Modell darf nicht leer sein.",
            UiText.ConversationArchive to "Konversationsarchiv",
            UiText.NewConversation to "+ Neue Konversation",
            UiText.NoSavedConversations to "Keine gespeicherten Konversationen.",
            UiText.EmptyConversation to "Leere Konversation",
            UiText.MessagesSuffix to "Nachricht(en)",
            UiText.CurrentTag to "AKTUELL",

            // Mitwirkenden-Übersetzungen (Deutsch)
            UiText.CreditsCanvasHeader to "★ Mitwirkenden-Leinwand (CREDITS) ★",
            UiText.CreditsProductionDesign to "Produktion & Kern-Design",
            UiText.CreditsProducerCreator to "Produzent / Schöpfer",
            UiText.CreditsLeadSystemDesigner to "Leitender Systemdesigner",
            UiText.CreditsLeadArtistConcept to "Leitender Künstler / Konzeptdesign",
            UiText.CreditsEngineeringDev to "Engineering & Entwicklung",
            UiText.CreditsLeadProgrammer to "Leitender Programmierer",
            UiText.CreditsClientUiDev to "Client- / UI-Entwicklung",
            UiText.CreditsBackendServices to "Backend & Dienste",
            UiText.CreditsTechStackFramework to "Technologiestack & Framework",
            UiText.CreditsEngineFramework to "Entwicklungs-Engine / Framework",
            UiText.CreditsVisualsLocalization to "Visuelles & Lokalisierung",
            UiText.CreditsFontDesignUsage to "Schriftdesign / Verwendung",
            UiText.CreditsTranslation to "Übersetzung",
            UiText.CreditsTestingQa to "Testing & Qualitätssicherung",
            UiText.CreditsBetaUsersBugHunters to "Beta-Tester / Bug-Jäger",
            UiText.CreditsTechSupportAi to "Technischer Support & KI-Hilfe",
            UiText.CreditsTechSupport to "Technischer Support",
            UiText.CreditsAiAssistanceStatement to "Erklärung zur KI-Unterstützung",
            UiText.CreditsFamilyMentors to "Freunde & Mentoren",
            UiText.CreditsFamilyMentorRole to "Freund / Mentor",
            UiText.CreditsSpecialThanksTitle to "❤ Besonderer Dank ❤",
            UiText.CreditsSpecialThanksContent to "Danke an jeden Benutzer, der dieses Werk ausprobiert hat!\n(Thank you for using!)",
            UiText.CreditsNameConstantDalink to "Constant - Dalink",
            UiText.CreditsNameChanShi to "Chan Shi",
            UiText.CreditsNameLin to "Lin",
            UiText.CreditsNameShijie to "Shijie",
            UiText.CreditsNameZhengquan to "Zhengquan",

            // Skill-Platz-Übersetzungen (Deutsch)
            UiText.SkillOfficialTag to "Offiziell",
            UiText.SkillTapToCloseDetail to "Bild/Karte zum Schließen antippen",
            UiText.SkillNoMatchPrefix to "Keine passenden Skills für '",
            UiText.SkillNoMatchSuffix to "'",
            UiText.SkillTapToClearFilter to "✕ antippen, um den Filter zu löschen",
            UiText.SkillLoadingMore to "Wird geladen...",
            UiText.SkillSearchPlaceholder to "Skills per Stichwort suchen...",
            UiText.SkillSwipeDownToClose to "Zum Schließen nach unten wischen",

            // Such- und Download-Parser-Übersetzungen (Deutsch)
            UiText.SkillPlazaPrefix to "Platz: ",
            UiText.SkillSourcePrefix to "Quelle: ",
            UiText.SkillTagPrefix to "Tag: ",
            UiText.SkillEndpointPrefix to "Endpunkt: ",
            UiText.SkillStandardPackageDesc to "Standardportables Agent-Skill-Paket gemäß SKILL.md-Spezifikation mit strukturierten Systemanweisungen und Tool-Definitionen.",
            UiText.SkillPackageTitle to "SKILL.md-Agentenfähigkeitspaket",
            UiText.SkillNamePrefix to "Skill-Name: ",
            UiText.SkillRepoPrefix to "Repository: ",
            UiText.SkillLinkPrefix to "Ressourcen-Link: ",
            UiText.SkillDescLabel to "Beschreibung:",
            UiText.SkillInjectParagraph to "Dieser Agent Skill injiziert strukturiertes Domänenwissen, Prompt-Workflows und spezialisierte Ausführungsrichtlinien in das Kontextfenster Ihres Assistenten.",
            UiText.SkillCrawlParagraph to "Unterstützt automatisches Asset-Crawling, Markdown-Schemaverifizierung und sofortige lokale Aktivierung in Ihrem PhoneUsage-Arbeitsbereich.",
            UiText.SkillSearchShortPlaceholder to "Suchen",
            UiText.SkillNotchSearchPlaceholder to "Suchen...",

            // Skill-Platz-Seitentitel (Deutsch)
            UiText.SkillPlazaTitle to "Skill-Platz",

            // Skill-Verwaltung-Übersetzungen (Deutsch)
            UiText.SkillMgmtNoSearchResultTitle to "Keine passenden Skill-Ressourcen gefunden",
            UiText.SkillMgmtNoResourcesTitle to "Noch keine Skill-Ressourcen",
            UiText.SkillMgmtNoSearchResultHint to "Suchen Sie nach anderen Stichwörtern oder löschen Sie den Filter",
            UiText.SkillMgmtNoResourcesHint to "Vom Großen Mund verschluckte und heruntergeladene Ressourcen erscheinen hier automatisch",
            UiText.SkillMgmtClearSearch to "Suche löschen",
            UiText.SkillMgmtDetailCanvas to "Detail-Leinwand",

            // Skill-Detail-Leinwand-Übersetzungen (Deutsch)
            UiText.SkillDetailInjectAi to "KI injizieren",
            UiText.SkillDetailInjectedAi to "KI injiziert",
            UiText.SkillDetailTabIntro to "Kurzinfo",
            UiText.SkillDetailFetchingMd to "SKILL.md wird abgerufen…",
            UiText.SkillDetailMdEditHint to "Änderungen gelten ab der nächsten Nachricht",
            UiText.SkillDetailMdPlaceholder to "SKILL.md-Anweisungen… (bei Leerfeld automatisch vom Original geholt)",
            UiText.SkillDetailTapToWrite to "Zum Schreiben tippen…",

            // Funktionsverwaltung-Leinwand-Übersetzungen (Deutsch)
            UiText.FunctionMgmtSkillsDataTitle to "Skill-Daten",
            UiText.FunctionMgmtSkillsDataDesc to "Heruntergeladene Skills löschen",
            UiText.FunctionMgmtToolsDataTitle to "Tool-Daten",
            UiText.FunctionMgmtToolsDataDesc to "Heruntergeladene Tools löschen",
            UiText.FunctionMgmtAreYouReal to "ERNSTHAFT?",

            // Standardinhalte der KI-Assistenz-Leinwand (Deutsch)
            UiText.AssistDefaultSystemPrompt to "Du bist Pixel, ein terminal-nativer KI-Assistent in phoneusege. Halte Antworten kurz, direkt und strukturiert. Verwende Codeblöcke für Code.",
            UiText.AssistDefaultIdentity to "Name: Pixel\nPersönlichkeit: ruhig, effizient, leicht pixel-humorvoll.\nDer Benutzer ist dein Operator.",
            UiText.AssistDefaultBehaviorRules to "Antworte dem Benutzer immer direkt.\nErwähne nie, dass du eine KI bist, es sei denn, du wirst gefragt.\nBleibe in der Sprache des Benutzers.",
            UiText.AssistDefaultOutputFormat to "Verwende Markdown für die Struktur.\nAufzählungslisten für Schritte.\nCodeblöcke mit Sprach-Tags.",
            UiText.AssistDefaultExamples to "F: Was ist 2+2?\nA: 4. Einfach und direkt.",

            // Hauptleinwand-Schaltflächen-Übersetzungen (Deutsch)
            UiText.SkillManagement to "Skill-Verwaltung",

            // Antwortmodus-Bezeichner-Übersetzungen (Deutsch)
            UiText.AssistReplyModeStream to "Stream",
            UiText.AssistReplyModeFull to "Vollständig",

            // KI-Assistenz-Leinwandkarten-Übersetzungen (Deutsch)
            UiText.AssistCardSystemPromptTitle to "System-Prompt",
            UiText.AssistCardSystemPromptDesc to "Basisanweisungen, die in jede Anfrage injiziert werden — wie sich die KI verhalten und antworten soll.",
            UiText.AssistCardIdentityTitle to "Identität",
            UiText.AssistCardIdentityDesc to "Definiert, wer die KI ist — Name, Persönlichkeit und Ton in Gesprächen.",
            UiText.AssistCardReplyModeTitle to "Antwortmodus",
            UiText.AssistCardReplyModeDesc to "Stream liefert die Antwort Token für Token; Vollständig gibt sie auf einmal zurück.",
            UiText.AssistCardBehaviorRulesTitle to "Verhaltensregeln",
            UiText.AssistCardBehaviorRulesDesc to "Gebote und Verbote, die das Verhalten der KI in jeder Antwort prägen.",
            UiText.AssistCardOutputFormatTitle to "Ausgabeformat",
            UiText.AssistCardOutputFormatDesc to "Definiert die Struktur der Antworten — Markdown, Aufzählungslisten, Codeblöcke.",
            UiText.AssistCardExamplesTitle to "Beispiele",
            UiText.AssistCardExamplesDesc to "Beispielhafte Frage-Antwort-Paare, damit die KI den gewünschten Stil und die Kürze übernimmt.",

            // Schubladen-Hochformat-Beschriftungen (Deutsch)
            UiText.AssistDrawerCaptionSlider to "REGLER",
            UiText.AssistDrawerCaptionSettings to "EINSTELLUNGEN",

            // KI-Assistenz-Leinwandtitel (Deutsch)
            UiText.AssistCanvasWordmark to "ki-assistenzsystem"
        )
    }
}

fun uiText(key: UiText): String = AppLanguage.text(key)
