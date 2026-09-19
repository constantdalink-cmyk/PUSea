# 创造哲学与思想 / Philosophy & Thoughts

## 中文

有时候我往往会在思考一个问题，也许不是因为某些人不用电脑，或用不着，所以不学用电脑，他们也许是没有电脑。所以这同样也可能是一个测试，没有电脑的人，也许也拥有着有电脑的人一样，或者更强大的无限创造潜力。没有技术的人，也许也有无限创造潜力的机会。

**快捷与创造完全释放，是我们恒定的目标。**

基于这一出发点，PUSea 在架构设计与系统实现中遵循以下十项基本思想：

### 1. 设备可及性与创造潜力
在通用计算的发展历程中，高阶开发与复杂任务的运行环境默认建立在桌面操作系统之上。这形成了一个未被言明的假设：高效的数字化生产必须以拥有一台个人电脑为前提。
现实情况是，全球有相当体量的人群其唯一接触的计算终端是智能手机。如果 Agent 运行时系统无法在移动设备上独立、完整地运转，技术的普及就会演变为一种新的硬件排他。消除设备门槛，让无 PC 开发者与普通使用者获得等效的系统调用与逻辑构建能力，是技术公平性的基本要求。

### 2. 降低门槛：自然语言作为直接指令界面
传统软件开发依赖于对语法规则、编译器环境和终端命令的长期学习，这构成了专业人员与非专业人员之间的技术壁垒。
Agent 操作系统的核心价值，在于将自然语言从简单的“提示词（Prompt）”转变为“可执行的系统级指令”。没有系统编程经验的人，其逻辑思考与问题定义能力并不必然落后于工程师。当自然语言能够被运行时系统直接解析为工具链调用与状态更新时，创造的主动权便能回归到问题本身，而非实现的细枝末节。

### 3. 移动设备的重定义：从信息消费到主动构建
移动端软硬件长期以来主要面向信息消费进行优化：信息流推荐、触控娱乐、即时消息传递。这导致移动终端被广泛认定为“只适合轻量操作与内容消费”。
然而，现代移动设备的片上系统（SoC）、内存带宽和传感器集成度已完全具备运行复杂业务逻辑的基础。移动端所缺失的不是硬件性能，而是一套允许其进行自动化、深层文件读写及工具调度的软件侧环境。PUSea 将任务调度与工具组装引入移动端，旨在证明移动设备同样是具备完整创造能力的生产端。

### 4. 现实交互的原生性：物理场景的直接触达
桌面端计算在物理空间上是静止的，而移动端天然嵌入在人类的真实生活与工作场景之中。
手机具备相机、位置、本地传感器、即时通讯、随身文件等与现实世界紧密连接的通道。将 Agent 运行在移动端，意味着智能体不再只是在抽象的数据库中检索信息，而是能够以极近的距离感知并参与现实场景中的物理任务。随身性与多模态的自然结合，是纯桌面端运行时所不具备的客观优势。

### 5. 时间解耦与常驻后台
桌面端的工作模式通常要求操作者在场，注视屏幕并维持上下文连接。移动设备的核心使用特征则是离散、移动和随时中断。
一个实用的移动 Agent 必须具备熄屏运行的能力。系统应当允许使用者在输入目标后离开界面，由底层服务在后台完成长周期的工具循环与结果收敛。将任务的执行周期与用户的屏幕占用时间解耦，是移动端 Agent 能够真正融入日常流程的必要条件。

### 6. 对称与透明：双工作区机制
在当前的系统实现中，完全黑盒的自动化容易导致幻觉累积与脱轨，而过度碎片化的多 Agent 架构则会带来高昂的上下文开销与不透明度。
PUSea 选择双工作区架构：使用者与 AI 各自持有独立、明确且互相可见的操作空间。AI 在自身工作区内进行工具调用与过程试错，使用者在另一端保留完整的观察与随时介入权利。这是一种对等、对称且边界清晰的人机协作状态。

### 8. 极端工程克制：本地优先与微分级体积
工具的推广深度，往往由其对资源的索取程度决定：
* **微分级体量：** 系统拒绝无节制的依赖引入与庞大的跨平台运行容器，致力于将运行时开销控制在极低水平，以确保在主流配置甚至中低端设备上均能稳定运作。
* **绝对本地优先：** 除必要的大模型推理网络请求外，所有操作记录、本地技能、运行日志均固化在用户设备中。这保证了在无公网中转服务器介入的情况下，数据所有权完全归属于使用者本人。

### 9. 系统的可生长性：由用户与智能体共同扩展
一个预先写死功能的系统很快会触及边界。PUSea 的设计核心是提供标准的 Skills 与 Tools 组装规范。
系统的能力不是由开发者单方面决定的，而是允许使用者根据自身需求，编写或让 AI 自主生成新的脚本与自动化工具。当系统具备自我扩充工具集的能力时，它就从一个“固定功能的软件”演变为一个“具有生长属性的执行环境”。

### 10. 工具理性与非竞速意识
PUSea 并不以追赶大型桌面基础设施的体量为目的。它诞生的意义在于探索一个此前未被充分重视的生态分支——**移动端软件侧完整 AI Agent 运行操作系统**。我们直面系统现有的缺点，不追求浮夸的宣传，旨在为后来者提供一个可参考、可运行、可分叉（Fork）的基础底座。

---

## English

I often find myself thinking about one question: perhaps it is not that some people don't use computers, or have no need for them, that they never learn — perhaps they simply *don't have* a computer.

So this, too, might be a test: people without computers may possess the same — or perhaps even greater — boundless creative potential as those with them. People without technical backgrounds may also be given the chance for boundless creation.

**Fully unleashing swiftness and creativity — that is our constant goal.**

Rooted in this perspective, PUSea adheres to the following ten foundational principles throughout its architectural design and implementation:

### 1. Device Accessibility and Creative Potential
In the history of general computing, advanced development platforms and automated execution environments have assumed the desktop operating system as their default base. This introduces an unspoken prerequisite: productive digital creation requires owning a personal computer.
In reality, the smartphone is the only computational device owned by a vast segment of the global population. If Agent runtimes remain tethered to desktop hardware, technological progress creates an unnecessary hardware barrier. Eliminating this disparity and providing non-PC users with equivalent capabilities for system execution and logic construction is a fundamental requirement for technological equity.

### 2. Lowering Technical Barriers: Natural Language as an Execution Interface
Traditional software development relies on extended training in syntax rules, toolchains, and terminal environments, establishing a steep barrier between technical specialists and general users.
The core value of an Agent operating system is transforming natural language from passive prompts into direct, executable system-level instructions. A user without programming experience does not inherently lack the capacity for logic or problem formulation. When an engine translates natural language directly into tool dispatching and state updates, creative autonomy shifts from syntactic implementation back to the problem itself.

### 3. Redefining the Mobile Device: From Passive Consumption to Active Construction
Mobile operating systems and applications have historically prioritized information consumption: algorithm-driven feeds, micro-interactions, and instant messaging. This has reinforced the perception that smartphones are suited only for media consumption and fragmented communication.
However, modern mobile system-on-chips (SoCs), memory architectures, and integrated sensors possess more than enough capacity to execute complex operational logic. What has been absent is not hardware performance, but a software runtime environment that allows for local file operations, tool orchestration, and autonomous execution. PUSea brings task dispatching and skill assembly to mobile devices to establish them as fully capable creative environments.

### 4. Inherent Real-World Interaction: Proximity to Physical Context
Desktop computing operates from a physically stationary standpoint. In contrast, mobile devices are embedded within human daily life and physical environments.
Smartphones carry cameras, location sensors, direct messaging protocols, and personal files that link directly to the physical world. Running an Agent natively on a mobile device allows it to observe and interact with real-world contexts directly, rather than operating solely on abstract, detached data. This immediate physical context is an inherent advantage unique to mobile environments.

### 5. Decoupling Time: Screen-Off and Background Persistence
Desktop workflows typically require active user presence: sitting before a monitor, watching output streams, and maintaining operational state. Mobile usage, however, is naturally intermittent, active, and integrated into physical movement.
A practical mobile Agent must be capable of persistent screen-off operation. The system must allow users to define an objective and exit the screen while background services manage tool iterations and final delivery. Decoupling task execution from continuous display usage is essential for a mobile Agent to serve practical utility.

### 6. Symmetry and Visibility: The Dual Workspace
Completely opaque, black-box agent designs often accumulate compounding hallucinations, while overly complex multi-agent architectures introduce excessive latency and token bloat.
PUSea implements a Dual Workspace architecture: the user and the AI each operate within a distinct, visible workspace. The AI tests, iterates, and executes tools within its own scope, while the human retains full visibility and the ability to step in at any stage. This provides a balanced, clear, and disciplined human-agent collaborative state.

### 7. Protocol Neutrality and Cognitive Decoupling
An execution platform should never be locked to a single model provider or proprietary cloud ecosystem.
PUSea natively supports general standard protocols (such as OpenAI and Claude). The rationale is to decouple the runtime environment from the underlying cognitive model. The model is an evolving reasoning engine that can be exchanged as needed, while the operating system provides reliable local tool orchestration and deterministic state management. Users retain full freedom to select their preferred endpoints and manage their own credentials.

### 8. Extreme Engineering Restraint: Local-First and Minimal Footprint
The reach of any tool is bounded by its operational requirements:
* **Differential-Level Footprint:** The platform rejects unnecessary dependencies and heavy cross-platform abstraction layers. Keeping resource overhead minimal ensures reliable performance on both modern flagships and accessible, entry-level hardware.
* **Absolute Local-First Execution:** Except for model inference API requests, all action logs, dynamic tools, and local contexts remain strictly on the host device. This guarantees that data ownership and computational control remain with the individual user without intermediary servers.

### 9. System Extensibility: Co-Evolution via Skills and Tools
A system with a fixed set of features quickly reaches structural limits. PUSea is centered on standardized Skills and Tools interfaces.
Capabilities are not hard-coded exclusively by the developer; users and the Agent itself can compose new scripts and automated tools on the fly. When a runtime can dynamically expand its own toolset, it ceases to be a static application and becomes an evolving execution platform.

### 10. Instrumental Reason and Non-Competitive Exploration
PUSea was not built to mimic the scale of desktop infrastructure or engage in an ecosystem arms race. Its purpose is to demonstrate a distinct and previously under-explored domain: a **complete, software-side AI Agent operating system for mobile devices**. By addressing shortcomings directly and avoiding superficial claims, it provides an open, stable foundation for future developers to explore, adopt, and fork.
