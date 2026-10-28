# 单 App 闭环开发进度 — 2026-09-22

目标：手机小模型定位 → 程序授权后写入 → 发送证据 → 完整回答保存。当前第一段定位与写入回读已有一次实测，发送与抓取待能力验证，不能称为闭环成功。

## 最新结果与当前暂停点

2026-09-22 已取得一次本地模型定位 → Agent 授权 → 写入 → 精确回读的真实成功。DeepSeek 2.5.2 (273)，人工展开空白输入框，116 字完整正文被写入，状态 STAGED、pendingIntent=false，未发送。指标见 [01-staged.txt](single-exchange-experiments/01-staged.txt)。加载 5.749 s、推理 16.422 s，采样 PSS 峰值 2,228,405 KiB。

此前 profile 因未收录不可见的固定提示“回答由 AI 生成，仅供参考”而拒绝页面；按实际内容补充。另一次后台推理被 Android LOW_MEMORY 终止；纯节点推理改为不加载 projector，随后取得上述成功，稳定性仍未知。报告在进程重启时会从遗留 RUNNING 改为 INTERRUPTED，任务仍由 Room 恢复逻辑暂停。

用户随后要求先做不依赖手机的工作。手机调试暂停，暂存正文未发送；发送控件只做过观察，发送/送达/抓取功能等能力确认后再实现。本轮新 APK 仅在 Mac 构建，未安装，不能将当前 build 产物哈希当成该实机样本的版本证明。

## 已接通的代码

- SingleExchangeExperiment 只接受预置公开题目：`这是一次公开的界面收发测试。请只回复：POC_OK_7319。不要搜索，不要补充解释。` 实际正文由 PayloadBuilder 加任务标记和模板生成，不接收调试广播中的任意外发文字。
- 调试者先打开 DeepSeek 空白页并展开输入框。只接受 2.5.2 (273)、已观察的页面锚点、唯一可编辑且支持 SET_TEXT 的节点和空白输入。未知文字/弹窗/既有草稿均不能初始化这个实验。
- AgentLoop.stageSingleExchange 调用手机 LocalUiModel，仅允许 FOCUS_INPUT / PAUSE；程序使用模型选择的节点引用，提供预先构造的 payloadId。
- GuardedPhoneTools 是唯一 ACTION_SET_TEXT 调用点。先获取新观察，比较全部节点与窗口信息，重新绑定引用；Guardrail 检查任务、payload、节点角色以及空白会话证据。
- Room 事务先提交写入 intent。实际 Android 调用前再次取新观察，在 TaskStore 的短同步区检查当前 revision/status；Guardrail 仅接受与刚提交 intent 完全匹配的状态，其他 pendingIntent 不放行。再核对原生节点类型、内容、描述、bounds、可见/启用状态和 SET_TEXT 能力。
- 写入回调不算成功。重新观察并逐字核对完整输入正文后，才记 STAGED；失败或不确定保留 intent 并暂停。成功也停在 `INPUT_STAGED_AWAITING_SEND_ADAPTER`，不发送。
- 调试入口为受 DUMP 权限保护的 `org.phoneai.teamwork.STAGE_PUBLIC_PROBE`，与只读 RUN_EXPERIMENT 区分；它会尝试写入上述固定公开题目，不会提交。仅限已有用户调试授权时调用。发布版无此广播入口。

目前 DeepSeekProbeProfile 只用于此固定公开题目的空白页暂存；不是通用 AppHints，也不表示已验收发送、历史会话或回答抓取。

## 后续能力验证顺序（当前不执行）

1. 先确认手机现有草稿与任务状态，不覆盖或重复暂存；本次 116 字回读已通过。
2. 检查 files/single-exchange-last.txt、任务记录和精确输入回读结果；若失败先定位，不用 adb 输入正文来冒充 App 写入成功。
3. 在正文暂存后观察真实发送控件，增加有证据的发送适配；发送前重新核验正文/会话并落盘提交意图，发送后必须核对 user message 角色、位置和完整正文。
4. 观察生成中/完成页面，补足角色和完整性核验；静止一段时间不能单独作为完成证据。取回回答后再计为单次闭环成功。
5. 记录人工准备部分；首次单次成功也不代表自主进入会话、重启恢复、长回答或三方七步通过。

本轮临时本地 UI XML 已清理；设备断开，手机上的 /data/local/tmp/phone-loop.xml 待重连后删除（空白测试页的观察，不含任务回答）。
