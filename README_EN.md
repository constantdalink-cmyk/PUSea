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

> **The first complete software-side AI Agent operating system on mobile.**
> This is application software, not a low-level kernel operating system.

<p align="center">
<a href="MANUAL.md">Manual</a> ·
<a href="DEFINES.md">What This Project Defines</a> ·
<a href="LICENSE.md">License</a> ·
<a href="PHILOSOPHY.md">Philosophy & Thoughts</a> ·
<a href="POSTING-GUIDELINES.md">Community Guidelines</a> ·
<a href="#known-limitations-face-them-directly-hide-nothing">Known Limitations</a> ·
<a href="#advantages">Advantages</a> ·
<a href="#feedback">Feedback</a>
</p>

Solely developed and open-sourced by **Constant Darlink**, lead of **ZKYL Creative Lab** and **ZKYL 1**.

PUSea is a complete, software-side AI Agent operating system running on mobile devices. It features full Skills assembly and custom Tools extensions, covers theoretically all reasonable mobile operations, supports **both OpenAI and Claude protocols**, adopts a Dual Workspace architecture, and runs in real time.

<p align="center"><b></b><img width="200" alt="pixel-animation-16x16-white89" src="https://github.com/user-attachments/assets/84cc37d0-1cc5-42fa-acf5-b1c84373c856" />

Fills the vacant spot of external AGENT operating systems on mobile (a complete software-side AI Agent operating system on mobile devices).

Benchmark targets: Earlier versions of OpenClaw, DeepSeek Harness functional layer.

*"(A mobile AI VSCode? Interesting.)"*

## Known Limitations (Face Them Directly, Hide Nothing)

1. Popular multi-agent patterns? None.
   Details & Reasons:
2. No inner-loop mechanism and targeted regulation — a single natural language input will likely not generate an entire completed product in one shot.
   Details & Reasons:
3. The architecture is far from aesthetic, logic is scattered, and difficult to comprehend.
   Details & Reasons:
4. Context trimming and load shedding optimizations are incomplete; high Token consumption.
   Details & Reasons:
5. Dual-end collaboration is not true co-presence: 1 human and 1 AI, one workspace each, head-to-head (this is currently the 4th Agent client to feature this).
   Details & Reasons:
6. Unoccupied space in the feature UI.
   Details & Reasons:
7. Most MCP Tools / CLI shortcut Tools are not tailored for mobile interfaces.
   Details & Reasons:

> Above are all the current limitations; next come the advantages.

## Advantages

1. **Absolute Privacy** — I refuse all cloud services, except for the model API.
2. Any component of an Agent can be customized.
3. **Alive When Screen-Off** — long-running, persistent task execution.
4. Any file can be sent to the AI, though you need to manage modalities yourself.
5. Minimal footprint, performance optimized down to the differential level — *"Extremely small, yet extremely vast."*
6. Near-obsessive swiftness.

> If features that a complete software-side AI Agent operating system on mobile is supposed to have by default count as "advantages"... then sure:  
> Persistence, etc. Nothing else. More accurately, I don't even know what else to list—it is baseline common sense; why state the obvious?
>
> **[By the way: Please distinguish this from open-source or proprietary AI agent platform concepts. The differences are:]**
> 1. **First and foremost:** My sole objective has always been an external runtime operating system.
> 2. **Beyond the browser:** It is obviously not confined to web environments. Similar to OpenClaw, it can reasonably and comprehensively control the entire mobile device.
> 3. **Zero manual adaptation:** Command-level adaptation is unnecessary; the system can determine how to utilize and construct commands on its own.
> 4. **An authoritative program:** Its architecture is clearly not a simple proxy relying on surface-level commands, but a dangerous yet fully legitimate authoritative program.
> 5. **Guaranteed capabilities:**
>    - **Universal app comprehension and operation:** Works across any application—especially games, complex canvases, custom UI components, and protected interfaces.
>    - **Screen-off & lock-screen persistence:** Continues internal software-side tool operations uninterrupted after the screen turns off or locks.
>    - **Non-interfering concurrency:** Operates concurrently on the device while you use the phone normally, without mutual interference.
>    - **Resilient execution:** Aggressive background task killing, detached debugging daemons, or updates to other apps will not disrupt its execution.
>
> ⚠️ **A critical reminder regarding autonomous dangerous operations:** It may click the exact right button, yet fundamentally misunderstand the real-world impact of destructive actions such as "Delete" or "Factory Reset."

<p align="center">
  <table>
    <tr>
      <td align="center">
      <img width="220" alt="bc4198c6093c370150bc73ee596bf444" src="https://github.com/user-attachments/assets/440afaaf-bb63-4dc0-8ebb-f6148ed526a5" />
      </td>
      <td align="center">
         <img width="220" alt="2ad2b1dc20c8d7d61fa5d966f030bf56" src="https://github.com/user-attachments/assets/67b5c5e5-c9af-4048-b9d3-bcb6803d7862" />
      </td>
      <td align="center">
        <img width="220" alt="b8bdc70207f7b0d66846d8ac139d66d2" src="https://github.com/user-attachments/assets/79ddff95-2139-4669-b375-4ef347306446" />
      </td>
    </tr>
  </table>
</p>

## Releases

**A better, more efficient, and more capable v0.2.0 is coming soon.**

## Feedback

Everyone is welcome to use PUSea. If you encounter any bugs, contact me directly via the contact information inside PUSea. I will be very glad and appreciative.

## Documentation

- Manual (Chinese / English) → [`MANUAL.md`](./MANUAL.md)
- What This Project Defines (Chinese / English) → [`DEFINES.md`](./DEFINES.md)
- License (Chinese / English) → [`LICENSE.md`](./LICENSE)
- Creation Philosophy & Thoughts (Chinese / English) → [`PHILOSOPHY.md`](./PHILOSOPHY.md)

---

> **No need to star.** If you use the ideas or code from this project, please state in your license: **Inspired by this project**.
>
> I cannot guarantee it matches the strength of DeepSeek Harness, because it was not created merely to chase after anything.

[An unrelated note]: Has real progress in Agent runtime systems truly come to an end? I don't think so. At least, not right now.
