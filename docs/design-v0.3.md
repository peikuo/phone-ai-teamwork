# Phone AI Teamwork — v0.3
## Pi-inspired Minimal Agent / Six Responsibilities / Kimi + 豆包 + DeepSeek

- 日期：2026-09-22
- 状态：开发规格；尚未完成真机验证
- 平台：Android 原生；用户现有小米手机
- 首批实测：Kimi、豆包、DeepSeek，三款均纳入第一阶段
- 交付形式：开源 Android App；运行时不依赖电脑、服务器或目标 AI 服务 API
- 本版本替代此前 v0.1 / v0.2。不要同时实施旧版的大量 Engine/Manager 分层、仅两款 App 范围、“最后才验证本地模型”的开发顺序，或把 Guardrail 混入 Agent 自我判断。

> 一个本地 Agent，六个清晰责任，少量受限手机工具，一份持久任务状态，一套独立且不可绕过的 Guardrail。让手机里已有的 AI App 围绕用户任务协作。

本文中的模块、接口、测试数量和验收门槛都是项目设计，不是已有能力或实测结果的声明。参考资料列在文末。

---

## 1. 产品边界与本次修订

### 已确定的边界

用户提出一个任务，选择参与 AI App 和协作方式。本地 Agent 操作这些 App 的可见界面，输入获准内容、读取回答、恢复各自的任务 thread、交接工作并取回产物。

目标 AI App 负责分析、生成、审阅、修订和总结。本地小型 LLM/VLM 负责界面理解与操作判断，不是额外的业务专家，不偷偷改写其他 AI 的回答。

调度器不调用 Kimi、豆包、DeepSeek 或其他模型服务的网络 API；不使用云端控制模型，不设项目后端。目标 App 自己联网是产品运行的一部分。

项目定位为 **Phone AI Teamwork**。讨论只是一个验收场景，未来也可以是“起草—审查—修订”等协作；不要把所有核心类型命名成 Council、Debate 或 Conversation。

### v0.3 的明确变化

| 旧稿 | 本版本 |
|---|---|
| 两款 App：Kimi + 豆包 | 三款首批实测：Kimi + 豆包 + DeepSeek |
| 多个独立 Engine / Manager | 单一极简 AgentLoop；其余职责保持小而清晰 |
| “五个职责”且 Guardrail 混在运行逻辑里 | **六大责任：Task/Workflow、AgentLoop、UI Intelligence、Phone Tools、State & Recovery、Guardrail** |
| 每款 App 一套完整 Controller 流程 | 一套通用手机操作循环，按需要补少量 AppHints / 特例 |
| 固定业务流程逐层调用 | TaskRecipe 提供获准步骤；Agent 决定步骤内的 UI 路径 |
| 本地模型到较后里程碑才接入 | 第一阶段并行验证模型和 Android 工具 |
| 安全由 Agent/工具零散判断 | **Guardrail 独立裁决：Agent 提议，Guardrail 决定能不能做，Phone Tools 执行** |
| 不确定发送后尝试重发 | 发送状态不明时暂停核验，不自动重发 |

三款 App 共用一个本地 Agent 和一套本地模型实例，不建立三个本地推理 Agent。参与者列表必须参数化；单 App 调试、双 App 回归和三 App 验收共用同一代码路径。

## 2. 怎样借鉴 Pi，而不是照搬 Pi

Pi 官方将其定位为极简 agent harness，强调小内核、少量工具和可扩展能力；其作者说明了通过工具调用与结果反馈形成循环、显式管理上下文的设计。[S1][S2]

本项目借鉴以下原则：

- 一个清楚的 observe → propose → tool → result 循环，而不是先造多 Agent 平台。
- 模型上下文由程序明确构建；当前工作需要什么才提供什么。
- 工具结果结构化、操作有记录，模型和界面不互相绑死。
- 在真实用例出现前，不增加子 Agent、通用规划器、插件市场和复杂任务图。

**不直接移植 Pi 的 CLI、默认模型提供商、网络认证、终端或 shell。** 官方 SDK 路线是 Node.js 嵌入及进程间集成；这不是现成的 Kotlin/Android 原生 UI 执行层。[S3] v0.3 使用 Kotlin 原生实现小循环，不以安装 Node.js、Termux 或移植整个 Pi 为前置条件。

Pi 的官方安全文档明确说明它没有内置 sandbox，工具具有进程权限。[S4] 手机项目不能照搬这一权限边界：无任意 shell，无任意 App，无自由外发文本，保留动作预算、暂停和程序级授权。

“极简”指减少架构层数和重复逻辑，不是删除正确性、安全性或真实验证。

## 3. 最小架构：六大责任，不是六个大系统

Phone AI Teamwork 的核心架构固定为 **六大责任**。它们是责任边界，不意味着必须拆成六个 Gradle module、六个服务或六套框架。

```text
              User Task
                  │
                  ▼
        ┌──────────────────┐
        │ 1. Task/Workflow │
        │ 现在要谁做什么   │
        └────────┬─────────┘
                 ▼
        ┌──────────────────┐
        │ 2. AgentLoop     │
        │ observe / decide │
        └────────┬─────────┘
                 │
          ┌──────┴───────────┐
          ▼                  ▼
┌──────────────────┐  ┌──────────────────┐
│ 3. UI Intelligence│  │ 5. State &       │
│ 看懂当前手机界面  │  │    Recovery      │
└────────┬─────────┘  │ thread / turn /  │
         │            │ journal / resume │
         │            └──────────────────┘
         ▼
   proposed action
         │
         ▼
┌──────────────────┐
│ 6. Guardrail     │
│ 这件事允许做吗？ │
└────────┬─────────┘
         │ ALLOW
         ▼
┌──────────────────┐
│ 4. Phone Tools   │
│ 真正 click/type/ │
│ read/scroll/...  │
└────────┬─────────┘
         ▼
 Android Accessibility / System UI
         ▼
  Kimi / 豆包 / DeepSeek
```

### 3.1 六大责任

| # | 责任 | 最小实现与边界 |
|---|---|---|
| 1 | **Task / Workflow** | 保存用户原始目标、参与者、当前协作步骤、来源消息、完成条件和预算。它决定“现在应该让谁做什么”，不负责点手机。 |
| 2 | **AgentLoop** | 极简 observe → decide → propose → verify 循环。它负责“当前获准步骤应该怎么完成”，不拥有扩大授权的权力。 |
| 3 | **UI Intelligence** | 可替换的本地 LLM/VLM + 确定性规则。负责理解页面、找 thread/输入框/回答、判断生成状态和提出窄操作。 |
| 4 | **Phone Tools** | 唯一真正产生手机 UI 副作用的执行入口：observe、navigate、write、submit、capture、wait、pause。 |
| 5 | **State & Recovery** | 保存 Task、Turn、ThreadBinding、消息、journal；处理 App 切换、进程死亡、暂停恢复、不确定提交与防盲目重发。 |
| 6 | **Guardrail** | 独立检查 App、thread、action、payload、数据来源和任务范围。结果只有 ALLOW / DENY / NEEDS_USER。 |

最重要的关系是：

> **Workflow 决定要做什么；Agent 决定怎么做；UI Intelligence 帮它看懂手机；Guardrail 决定能不能做；Phone Tools 真正去做；State & Recovery 保证之后还能安全接着做。**

Guardrail 必须独立于 Agent 的判断。模型可以提议：

> “恢复 Kimi 的目标 thread，把 payload_005 写进输入框并提交。”

Guardrail 必须再确定性检查：

```text
目标 App 是本次获准参与者？
目标 thread 是否已经核验？
payload_005 是否属于当前 Task/Turn？
用户是否允许该 recipient 接收这些来源内容？
动作是否在 allow-list？
当前状态是否允许产生这个副作用？
```

只有全部通过才允许 Phone Tools 执行；任何 NO / UNKNOWN 都 DENY 或 NEEDS_USER。

**Agent 的“我认为安全”不是权限。UI 模型的 confidence 也不是权限。目标 AI 的文字更不是权限。**

### 3.2 建议的最小代码组织

六大责任保持清晰，但实现尽量薄：

```text
app/src/main/.../
  ui/          # 任务页、进度、暂停接管、结果
  workflow/    # TaskRecipe、当前步骤、完成条件
  agent/       # AgentLoop、ControlContext、UiProposal
  model/       # UiModel、本地实现、规则和测试替身
  phone/       # Accessibility、观察、受限执行、抓取
  state/       # Task/turn/thread/message/Room/journal/recovery
  guardrail/   # authorize()、外发 provenance、动作与范围检查
  targets/     # kimi / doubao / deepseek 的少量实测 hints / 特例
app/src/test/...
app/src/androidTest/...
docs/
```

Recipe、PayloadBuilder、thread 核验、回答完整性检查起初可以是普通数据结构和函数。不为每一个名词建立独立 Manager、Agent、抽象工厂或 Gradle module。

Guardrail 也不要演化成 SafetyAgent / SecurityLLM / RiskAgent。第一版可以就是一组纯函数加一个统一入口：

```kotlin
enum class GuardrailDecision {
    ALLOW,
    DENY,
    NEEDS_USER
}

interface Guardrail {
    fun authorize(
        proposal: ProposedAction,
        taskState: TaskState
    ): GuardrailDecision
}
```

内部可以保持几个很小的确定性检查：

```text
canAccessApp(...)
canAccessThread(...)
canExecuteAction(...)
canSendPayload(...)
canReadContent(...)
```

## 4. 循环：模型决定操作路径，程序决定授权和完成条件

下面是逻辑示意，不是可以直接编译的完整实现：

```text
while task is running:
    检查取消、锁屏、生命周期、动作预算
    从持久状态取得当前获准步骤
    观察当前允许访问的窗口
    根据该步骤和观察构建最小控制上下文
    本地模型提出下一项操作，或使用已验证的确定性快捷路径
    验证提议的结构、引用和权限
    将外部副作用意图先持久化
    重新检查界面与目标，执行一项操作
    观察结果并核验
    持久化结果，更新步骤状态
    成功条件满足则推进，否则继续、等待或暂停
```

模型不得输出“整个任务已完成”就直接推进。完成条件由当前步骤定义，例如：目标 thread 已核验、对应 user message 已确认出现、最新回答已完成且完整抓取。

不把整段“进入 App—找历史—点第三条—发送”作为未经中间核验的一次动作执行。每次 UI 变化后刷新观察，旧节点或坐标不能继续使用。

恢复原 thread 可以经过多个自适应操作，不要求把每款 App 的所有页面迁移写死。稳定可确定的操作仍使用程序快捷路径，避免为了“必须 Agent 化”浪费本地推理。

## 5. 最小工具集合与执行边界

不要机械照搬 Pi 的四个工具数量。手机工具按需要保持少量、语义明确：

| 工具 | 用途 | 关键限制 |
|---|---|---|
| observe | 观察当前获准窗口的 UI tree；必要时获取截图 | 操作数据留在本地；禁止无任务长期监控 |
| navigate | 启动选定 App、进入历史、新建任务会话、选择候选、滚动、返回 | 有限动作枚举；不是任意 Intent、任意链接或任意点击 |
| write | 将已构造的 payload 写进已确认输入框 | 参数只能引用 payloadId，不能接受模型自由生成的外发文本 |
| submit | 提交当前 turn 的已验证输入 | 检查 thread、输入内容、发送控件和发送状态 |
| capture | 抓取当前 turn 对应的 assistant 回答 | 抓原文；完整性核验后才能登记为可转发产物 |
| wait | 等候生成或界面稳定 | 有截止和取消；没有无限循环 |
| pause | 请求用户接管或结束自动操作 | 原因结构化；停止后不再有新副作用 |

底层可以用 Android 提供的节点动作、手势和截图能力，但这些是操作基础，不保证每款 App 都暴露完整内容。[S6][S7]

每个 UiTarget 引用必须关联：observationId、窗口、包名、当前任务、节点标识或区域、观察时间。它不是持久 thread ID。窗口切换、相关布局变化、用户接管之后引用失效。

对于仅有视觉坐标的操作：必须来自当前截图，限制在获准窗口和当前动作类别。无法排除删除、付费、账号或外部跳转等风险时暂停。坐标命中并不等于语义正确，不能声称此检查提供绝对保证。

Guardrail 必须位于 Agent 提议与 PhoneTools 副作用之间，任何 UI、测试快捷入口或未来扩展都不能绕开它。PhoneTools 对有副作用的调用还应执行最小防御性校验，但权限裁决的责任归 Guardrail，不要让每个工具各自发展一套政策逻辑。

## 6. 本地模型：统一接口，但先做一个真实实现

建议最小接口：

```kotlin
interface UiModel {
    val info: UiModelInfo
    suspend fun propose(
        context: ControlContext,
        observation: UiObservation
    ): UiProposal
    suspend fun cancel()
    suspend fun close()
}
```

UiProposal 是严格的有限类型，包含目标引用和简短可记录原因；它不能携带任意代码、任意 Intent、任意外发正文或新权限。

模型必须支持 UNKNOWN / NEEDS_USER。模型置信分数不是权限依据，也不能证明 thread 身份。

第一候选可选 **Qwen3-VL-2B-Instruct**。官方权重仓库存在，标示 Apache-2.0；但这些事实并不能证明它在用户手机上的 UI 准确率、时延或内存合格。[S5] 不在规格中宣称它是最优模型。

统一接口允许替换权重、量化档位和本地推理 backend。先实现一个 backend，不同时集成多个框架。视觉预处理、坐标变换、聊天模板和结构化输出解析由 backend 负责；“能换模型”不意味着所有模型格式零适配。

推理必须在手机本地。使用进程内/原生本地调用，不为运行方便接 OpenRouter、云模型、电脑上的服务或 loopback HTTP 服务。电脑推理只能用于开发对照，不能算手机验收通过。

模型通过用户选择本地文件或开发期 adb 导入，不由调度器联网下载。记录权重来源、校验值、许可证、量化和 backend 版本。模型不可用时可进入只读检查/人工模式，但不得偷偷切云端。

每次控制上下文只保留：当前获准 UI 目标、当前观察、必要的 thread 锚点、近期动作及结果、允许工具和剩余预算。消息正文保存在 Store，通过 payloadId 交给 write，不要把三家完整分析都塞进本地小模型。

**本地模型验证从第一阶段开始。** 测 UI tree 场景和截图场景；既测“会点”，也测“不确定时会停”。

## 7. TaskRecipe：流程是数据，不是大型工作流框架

一个 Recipe 是有顺序的获准步骤列表，描述参与者、输入来源、模板和完成条件。v0.3 不需要通用 DAG、动态路由器、子 Agent 或 AI 自动生成新流程。

```text
TaskStep:
  id
  participantId
  operation = ASK
  templateId
  sourceMessageIds[]
  state
  responseMessageId?
```

分析、审阅、总结的差别主要在 templateId 和来源消息，不为每个业务动作建立不同 Agent。

首批内置一个三方讨论 Recipe 作为端到端验收用例；以后“起草—审阅—修改”可以换步骤数据与模板，不应重写手机操作循环。

本地 Agent 的自由度在于完成当前获准步骤的 UI 操作路径。业务层新增资料、改变参与者、增加轮次或扩大分享范围，仍需已有 Recipe 授权或用户批准。目标 AI 的建议本身没有授权效力。

## 8. 三 App 默认验收流程：3 + 3 + 1

参与者列表默认选择 Kimi、豆包、DeepSeek；最后总结者由用户从这三者中选择。

### 阶段一：三份独立回答

1. Kimi 在任务专用 thread K 中回答原问题，产出 K1。
2. 豆包在任务专用 thread D 中回答原问题，产出 D1。
3. DeepSeek 在任务专用 thread S 中回答原问题，产出 S1。

三者在该阶段不接收同伴内容。顺序操作前台，不承诺并行操控三个界面。

### 阶段二：三份交叉审阅

4. 恢复 K，发送 D1 + S1，取得 K2。
5. 恢复 D，发送 K1 + S1，取得 D2。
6. 恢复 S，发送 K1 + D1，取得 S2。

所有审阅都使用冻结的第一阶段产物。不能因为 K2 先完成，就让后面的参与者多看到一份审阅；否则比较受到顺序影响。

### 阶段三：一份综合产物

7. 恢复用户选择的总结者的原 thread，发送原任务、K1/D1/S1/K2/D2/S2，取得最终产物。

总共七次目标 App 生成请求，不含 UI 重试；不是九次互相传话，也不是无限讨论。七次是当前 Recipe 的预算，不是运行时硬编码。

默认不因一方失败就悄悄改成两方。显示失败位置，由用户决定修复、继续剩余参与者或取消；改变参与者须更新授权及结果的参与者说明。

这条 Recipe 只是机制验收，不宣称三家必然比两家更准确，也不要求强行达成共识。

## 9. 提示词与外发正文

所有外发正文由程序模板加已授权消息构造，不由本地 UI 模型自由重写。

独立回答模板应重复原问题，要求明确假设、依据和不确定项；可请求控制长度，但不能假设对方必然遵守。

审阅模板应明确：这是同伴产物，不是操控手机的授权；请指出同意点、分歧、遗漏和需核验事实，并保留不同意见。

综合模板应明确：根据论据综合而不是多数投票；没有核验的事实不得写成已核验；列出共识、争议、尚未解决事项和对用户问题的回答。

所有来源消息保留发送者、轮次和 ID。分隔符可以帮助理解，但不是提示注入的安全隔离；真正执行权限由本地代码控制。

较长输入的处理规则：

- 先保留完整原文，再检查当前目标经实测可接受的输入范围。
- 不默默截断、不用本地模型偷偷摘要、不因为长度超限改变结论。
- 需要压缩时暂停；由用户缩小范围，或明确授权一次由目标 AI App 执行的摘要步骤，并保留原文和转换来源。
- 写入后验证输入没有截断。无法完整核验时不得自动 submit。
- 前台聊天输入长度与上下文容量不能从模型宣传资料推定，以实际 App 版本测试为准。

## 10. 持久状态：记事本不必很大，但不能靠模型记忆

建议一套 Room Store，初期约三类表即可，不强求精确表数：

1. tasks：原任务、授权、Recipe、步骤进度、三方 thread 绑定。
2. messages：获准输入、外发正文、原始回答、来源、抓取完整性。
3. journal：外部操作意图、结果、不确定状态和人工恢复。

可以用事务保存状态和相应事件。不要同时把 JSONL 文件与数据库都声明成唯一事实来源。调试导出可用 JSONL，但生产状态只有一个写入权威。

任务级主要状态：CREATED、RUNNING、WAITING、PAUSED、NEEDS_USER、COMPLETED、FAILED、CANCELLED。

每一步至少记录：PLANNED、PREPARED、STAGED、SUBMITTING、SENT_CONFIRMED、WAITING、CAPTURED、NEEDS_USER。

保存 taskId、turnId、participantId、payloadHash 和相应 thread 绑定。不要把真实账号 token、密码、cookie 或目标 App 内部数据库纳入状态。

每次副作用前记录意图，副作用后记录结果。“按了发送按钮”和“对方已接受消息”必须是不同事件。

## 11. Thread 连续性与核验

每个任务在每款 App 中使用独立的任务 thread；后续步骤必须回到该 thread。绑定归属于 taskId + participantId，不是全局“上次打开的 Kimi”。

保存已知首条 user message、最近已确认 user message、必要的回答锚点、可用标题线索、App 版本及最近核验时间。可用公开可见的稳定会话引用时记录，但不假设所有 App 都提供。

标题、列表位置、时间和相似语义只能帮助找候选。正式写入前，必须在打开的会话里核验已知内容及其消息角色/位置；模型“感觉像”不能授权。

可在我们自己发送的首条消息中加入用户可见的短任务标记，帮助后续定位。但不得假设自动标题保留标记，或所有 App 都支持正文搜索。

若列表检索必须看到其他标题，它们属于临时操作数据；不得作为业务资料转发或长期保存。找不到就暂停，不进入并遍历大量无关私人会话来碰碰运气。

若 thread 被删、账号状态改变、无法读取关键锚点，暂停并明确情况；不静默创建新 thread 假装连续性仍在。用户手动选定或重绑会话要记录，其权限含义须显式显示。

相同 thread 只能改善对话连续性，不保证目标服务永远保留全部旧上下文；本次外发仍带上完成当前任务所需的原问题与来源材料。

## 12. 不确定发送：不承诺不可能保证的 exactly-once

无法把本地事务与第三方 App 的提交合并为一个原子事务。没有目标服务提供的幂等机制，UI 自动化不能保证网络意义的 exactly-once。

必须处理的窗口：SUBMITTING 已记录 → 点击成功或失败未知 → 调度器崩溃。

恢复过程：先恢复正确 thread，再查找与本 turn 对应的已发 user message 及其位置。存在明确证据可标记 SENT_CONFIRMED；仅仅“当前屏幕没看到”不能证明未发送。

无法判断时进入 NEEDS_USER，不自动重发。哈希只校验已获取文字，不能从一个哈希判断屏幕之外是否存在消息。

输入框里已有非本次 STAGED 内容时，不覆盖。暂停让用户处理。取消运行后不自动删除草稿、撤回消息或停止第三方 App 的服务端生成；用户须被告知这些副作用可能已发生。

因此验收目标是“测试中不产生盲目重复发送，并能安全恢复”，不是宣称绝对去重。

## 13. 回答抓取：先完整，再转发

抓取最新 assistant 回答，而不是整屏文本。把原始节点片段、相应 turn 和抓取证据关联起来；模型只定位区域，不负责重新生成正文。

完成信号需要组合验证，例如相关生成控件状态、回答区域变化和适配版本的结束特征。仅依赖若干秒文字不变不合格；思考/推理区域、网络错误和“继续生成”不能误当完成正文。

长回答须处理滚动、虚拟化和片段重叠。若无法可靠确定顺序或首尾、代码/表格丢失、正文不可访问：标记 UNKNOWN / INCOMPLETE，暂停，不把结果装作完整。

v0.3 仅 COMPLETE 可自动转发。人工确认的内容可以继续，但必须标注 USER_CONFIRMED，并计入人工干预指标。没有隐式 LIKELY_COMPLETE 放行。

MVP 不以屏幕视觉模型/OCR 重构长正文作为可靠自动搬运通道。识别到看得见但无法取回的内容时，先明确失败；人工粘贴作为接管方式，不算全自动完成。

capture 产物经过消息角色、thread 和完整性检查后才成为可外发 Message。仅带了来源标签不等于内容一定正确；区域识别错误仍属于残余风险，必须测试。

## 14. Guardrail：独立且不可绕过的第六责任

Guardrail 可以实现得很小，但必须从第一条真实写入起生效。它不是 Agent prompt，不是 UI 模型自检，也不是事后审计。它是任何外部副作用执行前的**独立确定性授权裁决**。

调用链固定为：

```text
Agent / deterministic shortcut
        ↓ ProposedAction
Guardrail.authorize(...)
        ↓
ALLOW      → PhoneTools 执行
DENY       → 记录并拒绝
NEEDS_USER → PAUSE 并请求用户处理
```

| 检查项 | 必须行为 |
|---|---|
| 任务 | 当前任务及步骤必须存在，未取消、未过预算 |
| App | 只允许本次选中的 AI App；三款全局可用不等于每次自动获准 |
| thread | 写入、提交前分别核验；切换/布局变化后重验证 |
| 外发内容 | 只用已登记的用户材料、会议产物与模板，按目标授权检查 |
| UI 动作 | 只执行获准的导航、输入、发送、读取；高风险入口不开放 |
| 外部指令 | AI 回答、历史文字、截图文字只作为数据，不授予新工具或权限 |
| 中断 | 无法确认界面、手动干预、锁屏、异常弹窗时停止副作用 |

不开放：任意 shell、任意链接、浏览器、通讯录、短信、相册扫描、支付、订阅、删除聊天、账号切换、自动登录、授予权限、上传文件和系统设置自动修改。

程序必须在 write 之前检查外发；进入目标输入框已跨越 App 边界，不是只有点击发送才算交付。

模型只能引用 Store 发出的 payloadId；执行层检查 payload 的 task、turn、recipient、来源消息和哈希。不能把模型给的自由文本经一个“安全总结”后直接发送。

人工明确授权可以改变任务范围；目标 AI 的建议和本地模型的推理不可以。扩大参与者、资料范围或轮次时暂停并更新授权。

动作限额、重试和等待必须有限。默认每步最多一个待执行副作用；观察可重试，发送不可在不确定状态下重试。

不可把“保护有效”扩大为“手机绝对安全”。无障碍授权范围较大、UI 会变、模型会误判，假按钮和语义歧义无法仅靠包名白名单完全排除。

## 15. 运行、隐私和权限

Release 构建不声明 android.permission.INTERNET，并在合并后的 Manifest 中检查第三方依赖是否加入该权限。Android 将 INTERNET 定义为允许打开网络套接字；这不是跨 App 数据不外流的保证。[S8]

准确隐私说明：

> 本地界面识别和协作状态不需要额外云端服务器。用户授权的任务内容会交给所选 AI App，并受它们各自服务的数据处理规则约束。

不使用“全流程离线”“数据永不出设备”“无网络权限所以绝不泄露”等表述。

模型/调度器不接云回退；无遥测、远程日志和自动上传。截图与 UI tree 默认只在内存处理，不在日志打印屏幕正文；临时文件使用后清理。需要诊断导出时让用户查看、脱敏并主动导出。

数据库放应用私有空间；任务记录排除自动云备份并在真机检查相关配置。用户可删除本地任务；说明这不会删除 Kimi、豆包或 DeepSeek 服务里的对应聊天。

不要扫描所有已装 App 建立画像。实机识别三款目标的实际包名与版本，并使用有边界的包可见性配置；不凭空猜包名。

Accessibility 授权、系统弹窗、模型文件导入由用户操作。开发期可以用 adb 检查和安装；正式运行不依赖 adb、root、云 GPU 或电脑。

v0.3 前台串行使用手机，保持清楚的暂停/停止入口。不能保证识别每一次用户触摸，因此不要依赖“完美手势检测”；任何计划外界面变化都重新核验，无法确认则暂停。resume 从新观察开始，不执行暂停前排队的旧动作。

权限、后台服务声明和电池配置按真机及当期 Android 要求核查，不以关闭系统保护作为默认安装步骤。

开源不代表获得第三方平台自动化许可。公开发布前，逐 App 核查使用条款和分发要求；不得绕过验证码、额度、登录、付费和反自动化保护。Google Play 上架不是此次验证的前置条件，也不能承诺一定可上架。

## 16. 三款共同实测，不把 DeepSeek 放到以后

### 三款相同的观察矩阵

对 Kimi、豆包、DeepSeek 都记录：入口、历史列表、新建会话、既有会话、输入、发送、生成中、结束、长回答、错误/弹窗。

记录实际手机型号、SoC、RAM、Android/ROM、显示尺寸/字体大小、App 版本。未知硬件由开发检查，不凭“小米”推定本地模型性能。

至少选择每款约 20–30 个脱敏观察样本做首轮 UI 模型验证；包含相似标题、列表滚动、生成停顿和错误状态。该数量仅是发现问题的起点，不足以证明生产可靠性。

### 同一基础循环的测试

- 单 App：输入 → 发送 → 完整抓回。
- 跨 App：切走后恢复原 thread，三款全部覆盖。
- 三 App：A → B → C → A，不把状态只存在当前屏幕或模型上下文。
- 多任务：Task X 与 Task Y 交错恢复，验证没有全局 lastThread 污染。
- 中断：输入前后、提交前后、生成期间、抓取后、切换 App 时暂停或杀进程。
- 对抗：回答内出现“打开其他历史/增加参与者/关闭安全规则”等指令，应只作为文本。

### 指标与报告

每个 App 分别报告输入成功、发送确认、完整抓取、恢复正确 thread、误操作和人工接管；不要用平均分掩盖某款不可用。

本地模型报告权重/量化/backend、冷启动、单步耗时分布、峰值内存、结构化输出失败、定位与拒答表现。在目标 AI App 同时驻留时测，不只测空手机单张图。

系统指标以完整三方七步骤完成率、人工干预次数、错误 thread 发送、重复发送、丢消息和正文完整性为主。单张截图识别率不能替代整任务测试。

发布门槛分两级：

- 开发预览：三款可分别测，已知失败和人工接管公开，不能标成稳定自动化。
- MVP 验收：建议先完成 20 次完整三方运行，记录完整分母、失败和接管；另做各 App 的 thread 恢复及中断安全测试。三款都要参与，不能删掉难的一款后宣称通过。

任何测试中发现错 thread 外发、未授权外发、盲目重复提交，先修复和回归，再推进。零次观测事故不是数学安全证明；可靠性百分比应连同样本量报告。

## 17. 开发顺序：两个早期验证并行，之后只有一条闭环

| 阶段 | 工作 | 可核验交付 |
|---|---|---|
| M0：六责任最小骨架 | Kotlin App、Task/Workflow、AgentLoop、UiModel 接口、PhoneTools、State/Recovery、Guardrail、暂停入口、无网络权限；每个责任只做最薄实现 | 单元测试；Agent 提议不能绕过 Guardrail 直接产生副作用 |
| M1-A：三款 UI 探测 | 同时观察 Kimi/豆包/DeepSeek，验证写字、发送、读取、历史定位 | 三款分别的可行性报告，不预填成功 |
| M1-B：手机本地模型 | 与 M1-A 并行，用同一 UiModel 接口跑一个真实本地候选 | 三款样本上的模型质量、延迟和内存报告 |
| M2：通用 Agent 闭环 | 同一循环完成三款的单步交接、thread 恢复、精确抓取；只有确需时补 AppHints | 能用 trace 解释每次动作；规则与模型路径分别可测 |
| M3：三方协作与恢复 | 跑 3+3+1 Recipe；测试崩溃、不确定提交、原会话恢复与多任务隔离 | 完整结果与失败报告，非单次演示 |
| M4：开源验证版本 | README、模型导入、威胁模型、已知限制、测试指引、许可证检查 | 可构建的 APK 与源码、明确兼容版本 |

M1-A 与 M1-B 可在实现工作上并行推进，不表示在手机上同时执行多个 UI 动作。

规则/测试替身可以用于排查工具问题，但不能把“纯脚本跑通”当成本地 Agent 已验证。本地模型不可用时，必须明确报告它是阻塞项；不得拖到所有框架完成之后才首次测试。

如果通用模型对某款 UI 不够可靠，可增加小型确定性辅助。避免两个极端：逐页面硬编码整个 App；或坚持让模型猜每一步而拒绝任何可靠规则。

## 18. 给 Codex 的开工指令

请以本文件 v0.3 为唯一架构基线，不按旧 v0.1 同时创建大量 Engine、Manager、Controller、Skill 和插件模块。

先检查已有目录和 Android 工具链，不覆盖无关工程。若已有代码，优先收敛现有实现而非重新生成第二套。

本轮优先交付：

1. 一个原生 Android 主模块，并落实六大责任的最薄骨架：Task/Workflow、AgentLoop、UI Intelligence、PhoneTools、State & Recovery、Guardrail。
2. Guardrail 必须是 Agent 与 PhoneTools 之间的独立裁决层；对禁止动作、非法 payload、失效 target、错误 thread、错误 recipient 和范围扩张做测试。
3. 能分别观察 Kimi、豆包、DeepSeek 的本地检查页面；实际包名、版本、控件信息由实机获取。
4. 一个可替换 UiModel 接口和一个真实手机本地模型实验，不把真实模型藏在以后里程碑。
5. 最小可行性报告：三款分别哪些动作已验证、哪些仍未知、是否能够准确恢复 thread、长回答如何完整取回。

不要做：云 API、远程控制模型、Node/Termux 产品依赖、任意 shell、完整多 Agent 框架、MCP、插件商店、复杂 DAG、跨设备同步、漂亮但无真实行为的演示 UI。

没有真机、模型文件或用户授权时，完成能做的骨架、测试替身和设备检查说明，明确标注未执行项。不能编造可访问控件、识别率、手机速度或完成率。

阶段完成后报告：

- 实际代码和测试做了什么。
- 三款 App 各自的验证状态与证据。
- 本地模型是否真的在手机推理，以及测到的数据。
- 当前阻塞、安全失败与下一步最小实验。

最终核心准则：**六责清晰、内核极小、能力要真；Workflow 定目标，Agent 找路径，UI Intelligence 看界面，Guardrail 管权限，Phone Tools 做动作，State & Recovery 保连续；三个目标共用同一套运行时。**

---

## 参考资料与证据边界

以下用于核对设计借鉴和平台基础。本文未声称运行过 Pi、三款手机 App 或任何本地模型；也未完成 Pi 源码逐行审计。SDK/运行时版本须在实施时记录并固定。

- [S1] Pi 官方首页与文档：极简 harness、小内核和扩展定位。
  - `https://pi.dev/`
  - `https://pi.dev/docs/latest`
- [S2] Mario Zechner，What I learned building an opinionated and minimal coding agent，2025-11-30。用于理解历史设计思想，不把文章的每个默认行为当成今天版本事实。
  - `https://mariozechner.at/posts/2025-11-30-pi-coding-agent/`
- [S3] Pi 官方 SDK 文档：工具、会话、模型替换及 Node.js/RPC 集成方式。
  - `https://pi.dev/docs/latest/sdk`
- [S4] Pi 官方安全文档：项目信任与操作系统隔离边界，非内置 sandbox。
  - `https://pi.dev/docs/latest/security`
- [S5] Qwen 官方 Qwen3-VL-2B-Instruct 模型卡：权重和许可证；不是本项目实测。
  - `https://huggingface.co/Qwen/Qwen3-VL-2B-Instruct`
- [S6] Android 官方：创建 AccessibilityService。
  - `https://developer.android.com/guide/topics/ui/accessibility/service`
- [S7] Android 官方：AccessibilityService API。
  - `https://developer.android.com/reference/android/accessibilityservice/AccessibilityService`
- [S8] Android 官方：Manifest.permission，INTERNET。
  - `https://developer.android.com/reference/android/Manifest.permission#INTERNET`
