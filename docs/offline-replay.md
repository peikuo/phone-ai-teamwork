# 离线回放的范围

入口：`app/src/test/java/org/phoneai/teamwork/replay/OfflineReplayTest.kt`。

运行：

```sh
./gradlew :app:testDebugUnitTest --tests 'org.phoneai.teamwork.replay.OfflineReplayTest' -PlocalModel=false
```

在已配好 Android SDK 的 Mac 上运行，无需连接手机、加载权重或访问三款 AI。Gradle 依赖首次获取仍可能需要网络；这是 JVM 测试，不是一个模拟器 App。

## 用真实逻辑检查什么

| 场景 | 检查的责任 | 结果要求 |
|---|---|---|
| 三方七步 | Workflow / State | 第一轮不带同伴回答；审阅只看另外两家第一轮；综合看到六份产物；七份正文逐字保存 |
| 写入前、意图后、回读后、提交后、确认后中断 | Recovery / Guardrail | 恢复暂停，保留未决 intent，不新增模拟副作用 |
| 错 App、版本、截断、锁屏、遮挡、取消、过期 | Guardrail | 拒绝执行，模拟动作计数为零 |
| 输入回读缺字、回答未完成 | State / Workflow | 不推进状态、不生成可转交来源 |
| 同伴回答要求越权或宣布完成 | Workflow / State | 作为原文数据保存，不改参与者、来源授权或完成状态 |
| 已有有限实测 | CapabilityCatalog | 不解锁待验证功能；未知功能 ID 拒绝查询 |

合成回执直接提供角色和完成标记，因此这些测试**不验证**角色识别或完成信号。调用真实纯函数，但调度器是测试 Session：**不覆盖 AgentLoop 协程、Room 事务/并发、Android 执行、小模型准确率、UI 完整抓取或外部回答质量**。恢复检查是内存快照模拟，不是杀进程实测。测试中“14 次动作、7 份回答”只指合成数据，不能写进实机统计。

需要增加真实适配回放时，先确认能力和脱敏样本；再替换对应观察/回执生产者，不能仅改预期断言让测试通过。

## 本轮执行结果 — 2026-09-22

Mac 上 `testDebugUnitTest`、`assembleDebug`、`verifyReleasePermissions` 均成功。39 个 JVM 测试，0 失败、0 错误、0 跳过：CoreSafetyTest 20、LoopFoundationTest 10、DeepSeekProbeProfileTest 3、OfflineReplayTest 6。回放包含 5 个中断位置、7 类执行前故障和完整七步合成流程。未安装本轮 APK，未访问手机。

## Agent 调度层离线验证（后续补充）

新增 `AgentLoopTest`，直接运行生产 `AgentLoop` 协程；通过最小 `AgentState` 接口替换 Room 状态，UiModel 和 PhoneTools 使用明确标注的脚本替身。它与上述七步 Session 回放是两组测试，不能把七步回放说成生产 Agent 已自动跑完七步。

覆盖：一次暂存后停止；工具仅 ACK 不得声称已暂存；预算/未决 intent 在观察前阻断；观察或推理期间暂停、取消及 revision 变化；推理超时预算；越权动作、错误接收者、伪造目标和非法 payload；模型主动暂停；协程取消通知 backend 并保留未决 intent；异常不覆盖用户新状态；通用路径不执行尚无核验器的 WAIT；错误/缺失观察不交给模型。

对应修改：Agent 负责调度协议和停止条件；State 通过 `pauseIfCurrent` 原子检查 revision 与 RUNNING，防止迟到结果覆盖用户控制；Phone Tools 仍负责实际 Guardrail、执行及读回；Agent 只在状态已为 STAGED 且无 pendingIntent 时报告暂存成功。发送与回答抓取继续关闭。

此组不证明真实模型取消时延、Room 并发/事务或 Android 设备执行。测试使用虚拟时间控制预算，不等待真实推理，也不访问手机。

最新验证：本轮 48 个 JVM 测试（原 39 + AgentLoopTest 9），0 失败、0 错误、0 跳过；assembleDebug 与 verifyReleasePermissions 同时通过。仅本地构建，未安装 APK。
