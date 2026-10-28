# 能力台账与功能依赖

## Current evidence addendum — 2026-10-29

The dated inventory below records the earlier capability assessment. Since then, one fixed public English exchange has been sent, verified, and saved in each of DeepSeek 2.5.2 and Doubao 14.9.0. This is bounded evidence for those two adapters, not a general release of SEND-01 or CAPTURE-01. Kimi 3.1.3 has a staged public draft and a read-only model send-label proposal. Its send label and direct parent reported `clickable=false`; the latest metadata observation recorded draft length only, without exact payload comparison. Kimi send therefore remains blocked. See the [DeepSeek](single-exchange-experiments/02-deepseek-verified-2026-10-29.md), [Doubao](single-exchange-experiments/04-doubao-verified-2026-10-29.md), and [Kimi geometry](model-experiments/11-kimi-send-geometry-2026-10-29.md) evidence.

A Kimi coordinate gesture requires a dedicated version-pinned policy, canonical draft and empty-thread proof, target-window and touch-region evidence, fresh re-observation before dispatch, a persisted intent, and independent message and reply verification. The current observations do not establish the touch target. The three-app seven-step flow, five public scenario repetitions, and privacy-reviewed video remain gated by these capabilities.

更新：2026-09-22。按用户要求，当前暂停手机调试。先做职责边界、确定性流程与离线回放；依赖未知手机/模型能力的具体功能，等待能力确认后再实现。本文与 `CapabilityCatalog.kt` 的条目对应，App 首页提供只读能力状态页。

“能力确认”与“功能完成”分开：能力验证只提供实现依据；通过后还需实现功能并完成集成验收。修改状态不能绕过 Guardrail。现阶段代码中的 deferred 列表全部阻塞，不提供手动开关，也不按本地测试通过自动解锁。未来凭证据审查修改台账和对应实现。

## 已有证据的范围

- WF-01：Recipe、PayloadBuilder、Guardrail、TurnLifecycle、Recovery 有离线逻辑覆盖。不是 Android / Room / 模型的验收。
- OBS-01：有真实无障碍节点和窗口截图样本，非所有 App 页面均可读。
- MODEL-01/02：真 Qwen 有合法提议及输入框定位正例；没有准确率统计，也没有输入框之外的控件判断验证。
- EXEC-01：DeepSeek **2.5.2 (273)**，人工进入并展开空白输入框，一次真模型定位 → Agent 授权 → 写入 → 完整 116 字回读成功。正文由 Workflow 生成，不由模型补写。参见 [原始指标](single-exchange-experiments/01-staged.txt)。没有发送。
- MODEL-03 / RUNTIME-01：后台推理发生一次 Android LOW_MEMORY 杀进程。改为纯节点推理不加载 projector 后，一次暂存成功（加载 5.749 s、推理 16.422 s、采样峰值 2,228,405 KiB）。这不能证明内存问题根治或后台持续运行可靠。

## 不确定能力，以及等它确认后才做的功能

| ID | 责任主体 | 不确定点 | 确认方式 | 暂缓的功能 |
|---|---|---|---|---|
| NAV-01 | Phone Tools / 各 App 适配 | 能否自动打开正确页面、处理入口与弹窗 | 每款分别采集页面和反例，确认可访问动作与正确去向 | 自动进入会话、全自动启动任务 |
| MODEL-04 | 本地模型 | 能否判断输入框之外的导航/发送控件 | 给同一模型真实标注样本，报告正负例和错误定位 | 依赖模型的导航/发送定位 |
| SEND-01 | Phone Tools | 可执行发送目标及是否实际送达 | 精确 payload 出现在当前会话 user 位置；点击回调不算证据 | 自动提交、发送失败重试逻辑 |
| THREAD-01 | App 适配核验函数 | 历史会话身份及重进页面能否可靠核实 | 同名会话、错误窗口、正文锚点的正负样本 | 历史查找、多轮续聊、恢复自动续跑 |
| CAPTURE-01 | App 适配核验函数 | assistant 角色、完整正文与明确完成信号能否取得 | 区分用户正文、生成中、停顿、错误消息、完成；对照独立原文 | 自动保存完成回答、转发给下一参与者 |
| LONG-01 | Phone Tools / App 适配 | 虚拟列表、分页/滚动、折叠、代码块是否丢失 | 跨屏原文逐字对照，检测截断与重复片段 | 长回答自动抓取、转发与综合 |
| APPS-01 | Kimi / 豆包适配 | 两款真实输入/提交/抓取是否可行 | 按各自版本独立验证；不能套用 DeepSeek 的成功 | 三 App 自动协作 |
| RUNTIME-01 | 本地 backend / Android 生命周期 | 后台推理、内存压力、取消和多步运行是否稳定 | 多次冷启动、切换、内存压力，记录次数、时延、退出原因 | 持续后台运行、无人值守七步 |
| STATE-01 | State & Recovery + 执行器交接 | Room 落盘和真实手机动作的中断边界 | 意图落盘、执行、回执各边界杀进程；核对实际发送次数 | 自动恢复续跑；默认仍暂停，不重发 |
| AI-01 | 外部 AI App | 回答是否准确、审阅是否增加价值、综合是否失真 | 使用独立事实依据与人工评分，不把控制链成功当质量结论 | 自动质量评分、“多 AI 更好”的产品承诺 |

单 App 闭环依赖 SEND-01 / THREAD-01 / CAPTURE-01；三 App 七步还依赖 NAV-01 / MODEL-04 / LONG-01 / APPS-01 / RUNTIME-01 / STATE-01。即使短回答可取回，也不能自动放开长回答功能。

## 现在可以做、已做的部分

1. 职责合同及故障归属：见 [能力合同](capability-contracts.md)。明确每个交接谁生产证据、谁消费、谁禁止越权。
2. 能力台账：纯 Kotlin 定义状态、范围、责任和验收条件；只读 UI 展示阻塞项。执行器对未实现导航/发送/抓取返回能力依赖原因码。
3. 离线回放：使用合成观察/回执串联真实 Recipe、PayloadBuilder、Guardrail、TurnLifecycle、Recovery；覆盖七步来源、逐字保存、中断、取消、错误页面、残缺输入/回答和不可信同伴指令。
4. 状态恢复：沿用未知副作用只暂停的实现；上轮已补上实验进程重启后的 INTERRUPTED 报告，不再把遗留 RUNNING 文本当进度。
5. 原有预览、暂停、取消、来源哈希、执行意图与日志继续保留。暂不添加真实 send/capture 实现，也不靠模拟数据冒充能力证据。

## 证据升级规则

能力条目必须记录：责任主体、输入模式、设备/App/模型版本、人工准备、正负例、成功/总次数、耗时、实际副作用、失败原因及可定位证据。一次实机成功只记“有限实机证据”。离线回放不得修改手机能力状态。

下一次手机工作先取得缺失能力的最小证据；确认能力后，才实现依赖功能。若失败，记录是不可行、样本不足还是当前实现问题，不自动换成 Codex 控手机来代替小模型。

## 逐步能力测试入口

新增 [分步测试规则库](step-lab.md)：小步骤先单独测手机小模型的真实提议，最近三次连续通过后才把结果用于组合步骤。`STEP_FINISHED` 是脚本完成事件；Store 中未有已确认 Kimi 回答、发送/抓取适配未通过时，Kimi→DeepSeek 长用例只记 BLOCKED，不会宣称闭环成功。
