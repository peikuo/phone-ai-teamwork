# v0.3 POC 状态 — 2026-09-22

## 最新实机里程碑 — 2026-10-29

固定英文公开题目在 DeepSeek 2.5.2 上完成一次真实收发：手机小模型定位 Send，Agent/Guardrail 核验后点击一次；App 两次观察确认用户消息和完整回答，将 `POC_OK_7319` 写入 Room，并在自家 App 任务页显示。第一次回复核验因静态输入提示词而保守拒绝，未重复发送；修正版本限定规则后，只读重试成功。证据见 [英文记录](single-exchange-experiments/02-deepseek-verified-2026-10-29.md)。Kimi、豆包、七步串联、五题复现、视频和图仍待完成。以下旧记录均为当时状态。

Kimi 3.1.3 与豆包 14.9.0 各完成一次手机端小模型只读输入框定位；Kimi 切换文字模式、豆包新建空白对话由调试者手动完成，均未写入或发送。详见 [英文记录](model-experiments/09-kimi-doubao-input-2026-10-29.md)。

在只读阶段，两款 App 的空白页通过版本限定、5 秒内有效的 profile 复核；当时尚未接入 Guardrail 写入路径。随后完成的固定公开题目写入见下段。

其后 Kimi 与豆包各完成一次固定英文公开题目的手机小模型定位、Guardrail 放行、写入与精确回读；均停在发送前。初次回读因输入框展开而拒绝，保留写入意图，未重复写入；限定新页面标签后，两次只读观察核验并通过同一事务转为 `PAUSED / STAGED`。详见 [英文记录](single-exchange-experiments/03-kimi-doubao-staged-2026-10-29.md)。这不证明两款 App 能发送或抓取回复。

Kimi 与豆包当前公开草稿页的发送控件还各取得一次手机小模型只读 `FOCUS_SEND` 正例；未点击。早期输出因省略不需要的 `payload` 键被严格解析器拒绝；仅对无 payload 的定位提议接受两键 JSON 后通过。一次 Kimi 图像实验约 90 秒超时，跨 App 切换后的调试入口不启动原因未明。详见 [英文记录](model-experiments/10-send-control-2026-10-29.md)。

之后豆包固定英文公开题目完成一次真实发送与完整回答抓取：模型定位可点击发送节点，Guardrail 放行一次点击；两次只读观察证明用户气泡、助手正文和完成控件，回答 `POC_OK_7319` 写入 Room 并显示在自家 App。初次回复 profile 因两处静态节点保守拒绝，未重复发送；修正精确规则后只读重试通过。详见 [英文记录](single-exchange-experiments/04-doubao-verified-2026-10-29.md)。Kimi 仍仅暂存；三 App 七步及五题均未打通。

## 分步测试规则（2026-09-23）

已建立 12 个版本化用例与主机 SQLite 运行库，先小步骤、再组合中步骤、最后 Kimi → DeepSeek 长步骤。真实小模型只读提议用例已接入 debug 实验入口；长用例仍因缺少 Store 中已核验的 Kimi 回答、发送与抓取适配而 BLOCKED。脚本记录 STEP_FINISHED，只有将来真实长场景通过才发 SCENARIO_COMPLETED。早期全屏截图入口因无法隔离分屏和悬浮窗，现已关闭；详见 [规则库](step-lab.md)。

Python 4 项规则测试、Android 48 个 JVM 测试、assembleDebug、verifyReleasePermissions 均通过；新版 APK 尚未安装到手机，未取得新小步骤实机成功率。

## 当前有效结论

已取得一次 DeepSeek 固定公开题的模型定位 → Agent 写入 → 116 字精确回读，尚未发送。[暂存证据](single-exchange-experiments/01-staged.txt)。后台推理曾被 LOW_MEMORY 终止，按需加载 projector 后一次成功，稳定性仍待确认。

用户已要求暂停手机依赖部分。当前新增能力台账、只读能力页、未实现动作的依赖原因、七步合成数据回放与故障注入。职责见 [能力合同](capability-contracts.md)，明确暂缓功能见 [能力门槛](capability-gates.md)，回放限制见 [离线回放](offline-replay.md)。本轮 testDebugUnitTest / assembleDebug / verifyReleasePermissions 成功，48 个 JVM 测试全部通过（含新增 9 个生产 AgentLoop 替身调度测试）。本轮不安装 APK、不操作手机、不发送消息。

以下按历史里程碑保留；其中“未写入”“尚未安装”等仅描述当时状态。当前以本节、能力台账和最新暂存记录为准。未实现真实 send/capture、历史会话恢复、多 App 自动七步。

## 历史：输入框提议实验

已下载并导入 Qwen3-VL-2B-Instruct Q4_K_M + Q8_0 projector。最终 APK（约 13 MiB）已安装，SHA-256：`c2bda9c204a6bb6860f3c89282a95ea0100ded3a50287748d9ce3e2e238d891d`。构建参数：`-PchinaMirror=true -PphoneCpuArch=armv8.2-a+dotprod+fp16+i8mm`，native 启用 -O3；assembleDebug 和 verifyReleasePermissions 成功。

DeepSeek 展开输入框后，手机小模型通过节点输入定位成功（26.305 s），同一快照加入截图后仍定位成功（37.004 s）；采样 PSS 峰值分别约 2.13 / 2.19 GiB。冷加载 7.951 s。输入框展开由调试者手动完成，没有执行模型提议。之前的通用构建超时记录也保留。详见 [完整实验记录](model-experiments/README.md)。

结论：本地模型定位链路已经有实机正例，延迟仍偏慢，自动协作流程未完成。

## 本轮交付

- 单 Android app 模块，Kotlin + Room；六责代码约 900 行，不含测试和文档。
- 默认三参与者 3+3+1 Recipe；单 / 双参与者共用同一路径，步骤来源冻结。
- 独立 Guardrail 纯函数：任务/预算/窗口/版本/target/thread/recipient/payload/来源检查。
- Room tasks + journal 单一持久化权威；任务草案、payload 预览、暂停、取消、删除；恢复遇 SUBMITTING / pendingIntent 时只暂停。
- 三款单次观察页、临时 UI tree 与 Android 14+ 窗口截图入口。
- UiModel 接口和 llama.cpp JNI 实验代码；权重与 mmproj 导入、SHA-256、元数据、有限提议解析、时延和 PSS 采样入口。
- 20 个 CoreSafetyTest + 8 个 LoopFoundationTest JVM 回归均已通过，0 失败 / 0 跳过。真实 Android 动作与 Room 崩溃事务仍未验收。

## 最初观察版的构建和安装证据（历史）

构建任务：`assembleDebug`、`compileDebugUnitTestKotlin`、`verifyReleasePermissions`。
参数：`-PlocalModel=false -PchinaMirror=true`。构建结果：SUCCESSFUL。

APK：`app/build/outputs/apk/debug/app-debug.apk`，约 5.7 MiB。
SHA-256：`9754e03296b390d622c93547d82248a10bb16cae7f890dc1d7cf12edf29a90b1`。
已通过 adb 安装到连接的小米设备并启动主页面。设备查询确认版本 `0.3.0-poc (1)`。首页 UI tree 仅包含本项目包名和预期功能入口；`run-as` 查询确认已创建 teamwork.db / WAL / SHM。

`aapt2 dump permissions` 未列出任何 uses-permission；release 合并 Manifest 检查通过。未生成发布签名 APK，未发布到应用商店。

本次使用阿里云构建镜像解决依赖速度；Kotlin Gradle 插件 JAR 的 SHA-1 与 Maven Central 公布值匹配：`f2928b44391e91d349790c08ce4152888e4abeaf`。项目默认仍为官方仓库，镜像可选。

## 三款实际观察

设备：`23013RK75C` / `SM8475` / Android 15 / 系统可见 RAM 7,354,276 KiB / 物理 1440×3200、有效 1080×2400 / 字体比例 1.0。

| App | 实际包名 / 版本 | 已观察 | 未验证 |
|---|---|---|---|
| Kimi | com.moonshot.kimichat / 2.6.1 (308) | 首页 62 节点、17 可点击；语音输入模式，无 EditText，无 resource-id；截图有键盘切换入口 | 文本模式、写入、发送、thread 恢复、长回答完整取回 |
| 豆包 | com.larus.nova / 14.9.0 (14090001) | 功能介绍弹窗 9 节点、3 可点击；可识别 close_btn，聊天被遮挡 | 弹窗后的输入、发送、thread 恢复、完整回答 |
| DeepSeek | com.deepseek.chat / 1.7.8 (175) | 更新弹窗 10 节点、1 可点击；只有升级动作，聊天页被阻挡 | 更新后全部交互与恢复 |

每款仅一次入口观察，不是 20–30 样本验证。未输入或发送任何目标 AI 任务内容。没有七步协作完成率。

证据方法为 ADB 启动 + UIAutomator 压缩树 + 临时截图，**不等于已验证本 App 的 AccessibilityService**。原始屏幕资料不进入仓库；只保存脱敏汇总 `device-observations-2026-09-22.json`。

## 模型与自动执行的真实状态

用户随后授权下载模型并实测。Qwen 官方 Qwen3-VL-2B-Instruct-GGUF Q4_K_M 和 Q8_0 projector 已下载并导入手机，电脑/手机 SHA-256 校验一致，见 model-lock.json。包含 native JNI 的 APK 已构建安装，并由手机自身进程执行真实推理；结果及失败均记录在 [model-experiments/README.md](model-experiments/README.md)。不能把本机 Codex 的界面分析算成小模型能力。

AgentLoop / GuardedPhoneTools 是骨架。AppHints 没有已验证版本；真实 navigate/write/submit/capture 尚未接入；自动运行入口没有开放。首次 thread 绑定、角色/位置核验、完整抓取、真实副作用 intent/结果事务、恢复发送证据 UI 都待实现。

没有为了演示开通绕过 Guardrail 的发送入口。当前失败策略是 NEEDS_USER，不能把这一点当成安全性已充分验证。

## 当前下一步

1. 先根据实机推理结果评估定位能力和交互延迟，扩大真实正例、遮挡、错误页样本。
2. 完成 Kimi 文本模式、豆包弹窗和 DeepSeek 输入入口的可执行目标绑定，不能把截图理解直接视为点击授权。
3. 三款都取得足够样本，再实现第一条受控写入 / 提交路径。

## 无障碍授权后的复核

用户已手动开启服务。更新诊断版 APK 后服务成功重新连接，无需再次授予。通过 App 的单次观察入口取得 Kimi 2.6.1 的 62 个节点，truncated=false；Android 15 窗口截图成功，均仅驻留内存；完成后 armed=false，没有持续监控。未写入或发送内容。

这次证据来自 App 自身的 AccessibilityService，而非 UIAutomator。debug 构建增加了仅输出连接状态、包名、节点数量和截图状态的 dumpsys 钩子，不输出节点正文或图片，release 不输出这些诊断。UIAutomator 可能干扰服务连接，因此不能用它在观察过程中读取本 App 的结果。

检查页通过 PackageManager 确认 DeepSeek 已更新为 2.5.2 (273)。旧版 1.7.8 的更新弹窗属于前一次观察，不代表新版状态；新版首页已取得实际观察，但无 editable 节点；屏幕上有“发消息或按住说话”入口。具体模型判断与延迟见实验记录。


## 单 App 闭环基础推进（设备暂未连接）

- TargetRebinder：重新采样后对照任务、App/版本、窗口以及完整节点信息；屏幕改变、隐藏/禁用目标、未知 profile 或伪造引用时拒绝。重新绑定不是授权，仍必须经过 Guardrail。
- InspectionService：补充 enabled、visible、className、supportedActions 观察信息；Guardrail 拒绝隐藏/禁用节点。
- TurnLifecycle + TaskStore：写入意图 → 精确输入回读 → 提交意图 → 精确 user message 确认 → 完整回答保存。意图落盘接口、修订号检查和 journal 复用 Room 事务；中断时保留 pendingIntent，不盲目重发。
- confirmSend / capture 的调用方仍必须独立核验会话、角色、位置和完成证据；这些转换函数不是设备核验器。执行器与核验器未接通，没有启用自动发送。
- 验证：testDebugUnitTest、assembleDebug、verifyReleasePermissions 均成功。测试覆盖慢推理后的重绑定、上下文变化、隐藏/禁用节点、精确回读、提交中断、完整性和来源错误；不把规则测试当真实发消息成功。
- 新 APK SHA-256：`0d1d0c41c07fb87b309885b64ca4a9ec563e42af76970c505793683783c8df2d`。当前 build/outputs 为此版本，尚未安装到手机；上方 c2bda9... 是此前实机定位版。
- 本轮 ADB 设备列表为空，无法核对 DeepSeek 当前发送控件与完成信号；未发送任何测试消息。下一步需重新连接手机后接通并验证单 App 收发闭环。


## 再次连接与受控暂存实现

ADB 曾恢复，成功安装上节的基础 APK 并观察 DeepSeek 空白输入页；随后 USB 调试连接再次中断。新增公开固定题目的模型定位 → 重新核验 → Guardrail → 写入意图 → Android SET_TEXT → 精确回读路径，默认在发送前暂停。代码、30 个 JVM 回归和 APK 构建均通过；该路径尚未安装/实机触发，没有发送消息。最新 APK 与下一步详见 [单 App 闭环进度](single-exchange.md)。上文“执行器未接通”等描述为此前里程碑，不代表本次代码实现状态；实际设备闭环仍未通过。
