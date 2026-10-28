# 小模型分步测试规则库

## 2026-10-29 update

The catalog now has 13 cases. `kimi.public_draft_exact.v1` is a read-only small step: it runs the app's two-observation canonical-draft check against the already staged fixed public Kimi task. An initial run recorded `state=VERIFIED`, but Astra found that the host runner could misattribute a stale report; that run is diagnostic evidence only. The corrected runner passes and requires a unique run ID. After APK reinstall, one attempt timed out while the accessibility service was still binding. After the operator restored the service, Xiaomi's background freeze skipped a debug broadcast. The runner now briefly opens only Phone AI Teamwork and returns to Kimi, waits for the page to settle, then sends a foreground broadcast. A fully correlated real run passed with `state=VERIFIED`, `inputLength=277`, `submitted=false`, `storeChanged=false`, and zero screenshots (local SQLite run ID `28591f5fe72d4f829ef58729`). This verifies only the draft and bound empty thread at that moment. It does not authorize Kimi send or count toward the five-question demonstration. The host screenshot path remains disabled.

Two further correlated runs (`c0cebb335507474aad83bc54` and `f2d55c7eb97e4cdb8cd8f63c`) also passed within the same hour, giving the latest three runs of this specific read-only small step consecutive PASS results. They reused the same staged public draft; they are repeat observations, not three independent sends or three different questions.

更新：2026-09-23。用例目录：[`cases.json`](../scripts/step-lab/cases.json)；运行器：[`step_lab.py`](../scripts/step-lab/step_lab.py)。这是一套真实小模型实验的测试入口，不把 Codex 判断或模拟提议算作小模型成绩。当前 12 个用例：3 个已接线的只读小步骤、1 个已接线的固定公开题暂存中步骤，以及 8 个标为能力待确认的后续小步骤、中步骤和 Kimi → DeepSeek 长步骤。

## 责任与判定

| 内容 | 责任主体 | 通过证据 |
|---|---|---|
| 用例目标、来源、预期、依赖 | 测试规则 | `cases.json` 固定 ID 与版本；人工检查 setup 是否成立 |
| 页面观察 | Phone Tools | 实际包名/版本、非截断节点；App 不在前台或 setup 不符记 `BLOCKED` |
| 控件定位或暂停提议 | 手机 Qwen 小模型 | `ModelExperiment` 的原始结构化提议及耗时；从不让 Codex 代答 |
| 动作授权与效果 | Agent / Guardrail / Phone Tools | 对固定公开题，精确写入回读和 STAGED；点击回调本身不算成功 |
| 会话、用户消息、assistant 完整回答 | 版本适配核验 | 目前能力待确认；对应脚本 `BLOCKED`，不得用测试用例绕过 |
| Store 与完成事件 | State / 用例运行器 | 每次运行写 SQLite；`STEP_FINISHED` 表示该次脚本结束，只有未来长用例真实通过才发 `SCENARIO_COMPLETED` |

状态语义：`PASS`=预期证据都一致；`FAIL`=模型/执行结果与预期不符；`BLOCKED`=前置条件、版本或能力尚缺，未执行该步骤；`ERROR`=脚本/设备通信错误。前置条件不符不计入模型错误率。任何类型的一个成功样本都不自动提升整个 App 或通用模型能力。

## 小 → 中 → 长

- 小步骤分别评价 DeepSeek 输入框、发送控件以及 Kimi 语音页暂停。发送控件用 `FOCUS_SEND` **只读提议**，不执行 `SUBMIT`。
- 其它小步骤占位并可记录阻塞：小模型找 DeepSeek 入口、Kimi 完整回答、从 Store 取 Kimi 原文写入 DeepSeek、发送送达确认、DeepSeek 完整回答。
- 中步骤 `ds.public_draft.v1` 只使用固定公开测试题；它要求同设备同版本的 `ds.input.visible.v1` 最近三次连续通过。其余中步骤等各自小步骤能力确认后再接通。
- 长步骤 `kimi.answer_to_ds.v1` 的目标是：读取 Store 中**已有完整证据**的 Kimi 消息 → 小模型协助打开 DeepSeek → 程序构造来源绑定的正文并写入 → 发送与送达确认 → 取得完整 assistant 回答 → 写入 Store → 发 `SCENARIO_COMPLETED`。当前缺少 Kimi 完整来源、导航、发送和抓取核验器，脚本明确 `BLOCKED`；没有任何“假数据通过”的长步骤结果。

小步骤作为后续依赖时，需同手机、同 App 版本最近 1 小时内连续三次 `PASS`。中步骤作为依赖时需最近一次 `PASS`。这样一次偶然定位不会解锁较长流程。截图只辅助定位问题；它不替代机器可核对的正文、角色或完成证据。

## 运行与入库

```sh
python3 scripts/step-lab/step_lab.py validate
python3 scripts/step-lab/step_lab.py list
python3 scripts/step-lab/step_lab.py run ds.input.visible.v1 --notify
python3 scripts/step-lab/step_lab.py history
python3 scripts/step-lab/step_lab.py summary
python3 scripts/step-lab/step_lab.py report <run_id>
```

运行前按 `list` 对应 setup 准备目标 App 页面。运行器只接受目录中的固定用例，不接收任意外发正文。`model_proposal` 从本 App 的 AccessibilityService 取当前窗口，调用手机 Qwen 后读取调试报告；`public_stage` 会真实写入固定公开测试题并在发送前暂停，因此应确认输入框为空。`relay` 和能力待确认用例只记录阻塞，不操作手机。需安装包含本规则入口的新版 debug APK；旧 APK 不支持 `case_id`、`run_id`。

结果在本机 `captures/step-lab.sqlite` 的 `runs` 与 `events` 表，按 run_id 追踪。每次结束输出 JSON `STEP_FINISHED`；`--notify` 同时请求 macOS 本地通知。截图入口暂时只接受 `--screenshot-every 0`：之前的全屏 `screencap` 可能包含状态栏、分屏或悬浮窗，单靠前台包名检查和裁剪无法隔离，因此已移除。恢复时必须使用目标窗口截图并复核内容。数据库位于 git 忽略的 `captures/`。

模型判断的输入仍是 bounded UI tree。待目标窗口截图路径安全接通后，如需比较“树”和“截图+树”，应另设明确的 case ID 和预期；当前脚本没有自动截图或视觉能力验证。

规则自身验证：`python3 -m unittest discover -s scripts/step-lab -p 'test_*.py'`。脚本规则测试只检查目录、判定与入库，不证明 Android 交互或 Qwen 准确率。

2026-09-23 验证：12 个用例目录校验通过，Python 4 项测试通过，Android 48 个 JVM 测试全部通过；assembleDebug 和 verifyReleasePermissions 成功。新版 debug APK SHA-256：`dcef05bc25ae4b5dcf1d108b0e3ec9ab747841d10641b4d7b50a209cbd3a5122`。长用例演示记录为 BLOCKED，入库事件可查询。新版 APK 尚未安装到手机，所以新增小步骤没有实机样本。
