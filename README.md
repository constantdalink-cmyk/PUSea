<p align="center">
  <a href="./README.md"><img src="https://img.shields.io/badge/Language-简体中文-blue?style=flat-square" alt="Chinese" /></a>
  <a href="./README_EN.md"><img src="https://img.shields.io/badge/Language-English-lightgrey?style=flat-square" alt="English" /></a>
</p>

# PUSea

<table align="center">
  <tr>
    <td>
      <img width="200" alt="pixel-animation-white-bg (1)" src="https://github.com/user-attachments/assets/3d033803-bfc8-4548-8e94-801abd6b0c0c" />
    </td>
    <td valign="middle" style="padding-left: 24px;">
      <b>PUSea</b><br>
      By ZKYL 1<br>
      ZKYL Creative Lab
    </td>
  </tr>
</table>

<p align="center">
  <img src="https://img.shields.io/badge/Status-Preview-yellow?style=flat-square" alt="Preview" />
  <img src="https://img.shields.io/badge/Version-v0.1.0-blue?style=flat-square" alt="Version" />
  <img src="https://img.shields.io/badge/Platform-Mobile-3DDC84?style=flat-square&logo=android&logoColor=white" alt="Platform" />
  <img src="https://img.shields.io/badge/Protocol-OpenAI%20%7C%20Claude-8A2BE2?style=flat-square" alt="Protocols" />
  <img src="https://img.shields.io/badge/Architecture-Dual%20Workspace-orange?style=flat-square" alt="Architecture" />
  <img src="https://img.shields.io/badge/Privacy-100%25%20Local-success?style=flat-square" alt="Privacy" />
  <img src="https://img.shields.io/badge/PRs-Welcome-brightgreen?style=flat-square" alt="PRs Welcome" />
  <img src="https://img.shields.io/badge/Author-Constant%20Darlink-181717?style=flat-square&logo=github&logoColor=white" alt="Author" />
  <img src="https://img.shields.io/badge/Lab-ZKYL%20Creative%20Lab-grey?style=flat-square" alt="Lab" />
</p>

> **移动端首个软件侧完整 AI Agent 运行操作系统。**
> 这是软件，非底层内核操作系统。

<p align="center">
<a href="MANUAL.md">说明书</a> ·
<a href="DEFINES.md">本项目定义了什么</a> ·
<a href="LICENSE.md">协议</a> ·
<a href="PHILOSOPHY.md">创造哲学与思想</a> ·
<a href="POSTING-GUIDELINES.md">社区守则</a> ·
<a href="#已知缺点直面不藏">已知缺点</a> ·
<a href="#优点">优点</a> ·
<a href="#反馈">反馈</a>
</p>

由 **ZKYL Creative Lab**、**ZKYL 1** 的主导人 **Constant Darlink** 单人制作并开源。

PUSea 是一套运行于移动设备之上、完整拥有 Skills 组装与自定义 Tools 扩展、覆盖手机理论上全部合理操作、兼容 **OpenAI / Claude 双协议**、采用双工作区架构、实时化运行的软件侧完整 AI Agent 运行操作系统。

<p align="center"><b></b><img width="200" alt="pixel-animation-16x16-08-white" src="https://github.com/user-attachments/assets/d999127f-fe41-429f-9e97-4dbec5baf30f" />

补全了移动端外部 AGENT 运行操作系统的空位（移动端软件侧完整 AI Agent 运行操作系统）。

对标：OpenClaw 较老版本，DeepSeek Harness 功能层。

*“(移动的 AI VSCode 吗？有点意思”*

## 已知缺点（直面，不藏）

1. 现在流行的多 Agent 模式，没有。
   原因详情请看：
2. 无内循环机制和相关针对性治理——单次自然语言极可能无法完整制作一整个成品。
   原因详情请看：
3. 架构极不美观，逻辑分散，难以理解。
   原因详情请看：
4. 上下文修剪减负优化不全，Token 消耗大。
   原因详情请看：
5. 双端共同协作不是真实的：一人 一 AI 各一个工作区，单挑（这是目前第四个拥有此功能的 Agent 操作端）。
   原因详情请看：
6. 功能界面有空余。
   原因详情请看：
7. MCP Tools 的 CLI 快捷 Tools 大多不适配移动界面。
   原因详情请看：

> 以上便是现在的所有缺点，接下来才是优点。

## 优点

1. **绝对隐私**——我拒绝一切云端服务，除了 API。
2. 任何 Agent 的组成部分均可自定义。
3. **熄屏仍活**，长期持久完成任务。
4. 任何文件都可发送给 AI，但需要自己注意模态。
5. 体量极小，性能优化，做到了微分级——“极小，但它又极大”。
6. 近乎偏执的快捷。

> 如果说移动端软件侧完整 AI Agent 运行操作系统应该有的也算优点。。。那就是：  
> 持久化等等等，没别的了，更准确的是我不知道说什么，主要，这是常识，为什么要说？
>
> **【顺便说：请与开源/非开源 AI 代理平台概念分开，差异点：】**
> 1. **首先：** 我纯奔着外置运行时操作系统去的。
> 2. **不仅限于 WEB：** 它显然不止只能用 WEB，它与 OpenClaw 类似，完全可以合理操控整个手机。
> 3. **无需做命令适配：** 它显然不必要去做命令适配，因为它本身自己都能找怎么用命令。
> 4. **合法的掌权程序：** 它的架构显然不是一个简单的代理、中间全靠命令，而是一个危险却合法的掌权程序。
> 5. **它能保证：**
>    - **任何 App 都能理解和操作：** 尤其是游戏、复杂画布、自定义控件和受保护界面。
>    - **熄屏、锁屏后继续：** 任意软件侧内部工具操作不中断。
>    - **同屏互不干扰：** 你正常使用手机时，它同时操作同一个屏幕却互不干扰。
>    - **稳定运行：** 杀别的后台、调试服务失效、其他应用升级都不可能影响执行。
>
> ⚠️ **但记住自动判断所有危险操作的后果：** 它可能点对按钮，却误解“删除”“恢复出厂”等操作的实际影响。

<p align="center">
  <table>
    <tr>
      <td>
        <img width="230" alt="0786cee694f810568c824fcde4bc060f" src="https://github.com/user-attachments/assets/ef3de4e0-5a2e-4346-894f-fe58ea836a7b" />
      </td>
      <td>
        <img width="230" alt="238dc07a59de19fb69c497658106bdb3" src="https://github.com/user-attachments/assets/a1d708c0-16bc-4fb4-a22c-83938b27dce6" />
      </td>
      <td>
        <img width="230" alt="b8bdc70207f7b0d66846d8ac139d66d2" src="https://github.com/user-attachments/assets/c0882dd1-8198-4e52-bbaf-6da412e19d33" />
      </td>
    </tr>
  </table>
</p>

## 版本

**更好、更省、更越的 v0.2.0 即将到来。**

## 反馈

欢迎各位使用 PUSea。如遇 bug，通过 PUSea 内部的联系方式直接联系我，我会非常高兴并加以感谢。

## 文档

- 说明书（中文 / 英文）→ [`MANUAL.md`](./MANUAL.md)
- 本项目定义了什么（中文 / 英文）→ [`DEFINES.md`](./DEFINES.md)
- 协议（中文 / 英文）→ [`LICENSE.md`](./LICENSE)
- 创造哲学与思想（中文 / 英文）→ [`PHILOSOPHY.md`](./PHILOSOPHY.md)

---

> **无需 star。** 使用本项目的思想 / 代码，请在你的协议中标明：**由此项目启发诞生**。
>
> 我不能保证它能和 DeepSeek Harness 一个强度，因为它不只是因为追赶而诞生的。

【一句不相干的话】：难道 Agent 运行时系统的真实进步真的结束了吗？我认为，不然，至少，现在不然。
