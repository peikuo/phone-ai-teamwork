# Phone AI Teamwork — v0.3 POC

一个 Android 原生、本地运行的手机 AI App 协作实验。架构以 [v0.3 规格](docs/design-v0.3.md) 为唯一基线。

**当前已实测一次 DeepSeek 固定公开题的本地模型定位、Agent 写入和精确回读，尚未发送或完成协作闭环。** 人工打开输入页；真模型推理 16.422 秒；不代表通用模型定位或多 App 能力。已有一次后台 LOW_MEMORY 失败，持续运行仍未知。当前按用户要求暂停手机调试，先完善职责、能力台账与离线规则。

先读：[分步测试脚本与入库](docs/step-lab.md) · [谁负责什么](docs/capability-contracts.md) · [未知能力与暂缓功能](docs/capability-gates.md) · [离线回放边界](docs/offline-replay.md) · [实机暂存证据](docs/single-exchange-experiments/01-staged.txt)。

## 当前可以做什么

- 查看能力责任、证据范围和待验证功能依赖；无解锁开关。
- 在 debug 构建中按版本化脚本运行本地小模型的只读定位测试；运行结果与低分辨率缩略图记录在 git 忽略的 `captures/`。
- 保存任务、参与者和分享范围；同一 Recipe 生成单 / 双 / 三参与者步骤。默认三款产生 3+3+1 七步。
- 预览程序生成的第一步完整 payload、步骤来源和 journal；暂停、取消、删除本地记录。
- 在实际包名配置完成、用户开启无障碍服务后，为 Kimi / 豆包 / DeepSeek 分别主动开启 30 秒单次 UI tree 观察。Android 14+ 可请求当前窗口截图；不记录到磁盘，不读取其他 App。
- 导入本地 GGUF 和匹配的 mmproj，校验用户提供的 SHA-256 并保存来源、许可证、量化信息。
- 通过进程内 JNI 调用一个 llama.cpp 模型实例，执行 UI tree 或 UI tree + 截图的受限提议实验。记录加载耗时、单步耗时、250ms 采样 PSS 峰值和解析状态。native 构建与手机推理已跑通一次明确输入框的定位；完整自动协作、稳定性和成功率仍未验证。

## 当前不会做什么

常规任务的自动导航 / 发送 / 回答抓取 / thread 恢复均未启用。新增开发专用的 DeepSeek 固定公开测试题暂存路径：小模型定位 → 新观察重新绑定 → Guardrail → 意图落盘 → 再核验 → ACTION_SET_TEXT → 精确回读；在发送前暂停。此路径已有一次实机暂存正例，不是通用写入能力。`AppHints` 仍无通用已验证版本；只读模型实验保持只读。详见 [单 App 闭环进度](docs/single-exchange.md)。

这是有实际存储、观察和模型接口的 M0 + M1 实验起点，不是 M2/M3 完成。写入已有 intent 与执行前重验；发送、thread 角色/位置核验和完整回答抓取，等能力确认后再实现。

## 构建

开发环境：JDK 17+（本机使用 Android Studio JBR 21）、Android SDK 36、Gradle 8.14.3、AGP 8.13.0、Kotlin 2.1.20、Room 2.7.2。最低 Android 11 / API 30；native 目标 arm64-v8a。

```bash
# 观察和任务 UI 构建：不包含推理动态库，页面会明确提示
./gradlew :app:assembleDebug :app:verifyReleasePermissions -PlocalModel=false

# 完整本地 backend：先准备固定版本源码及 SDK 的 NDK/CMake
bash scripts/bootstrap-native.sh
# 整包下载较慢时，可改用逐文件哈希校验下载：
# python3 scripts/fetch-native-source.py
# 通过 Android Studio SDK Manager 安装 NDK 27.2.12479018、CMake 3.22.1
./gradlew :app:assembleDebug :app:verifyReleasePermissions
```

默认三款包名取自本次真实设备：`com.moonshot.kimichat`、`com.larus.nova`、`com.deepseek.chat`，见 [观察记录](docs/device-observations-2026-09-22.json)。其他安装渠道可按 [设备检查说明](docs/device-check.md) 获取实际包名，并传入 `-PkimiPackage=... -PdoubaoPackage=... -PdeepseekPackage=...` 覆盖；不枚举所有已装应用。

国内网络可增加 `-PchinaMirror=true`，使用阿里云 Google/Maven Central/Gradle 插件镜像；官方仓库仍是默认配置。镜像仅用于开发构建，不进入手机运行时。

本地模型构建默认启用。`-PlocalModel=false` 只用于开发检查工具链，不能作为“本地模型已验证”的交付。

## 六个责任

各角色的输入/输出、权限边界、当前实现状态、能力编号和故障归属见 [角色能力与验收边界](docs/capability-contracts.md)。本地控制模型与三款外部 AI 的业务角色分别定义，便于逐层验收。

| 目录 | 职责 |
|---|---|
| `workflow` | 固定 Recipe、冻结来源、程序模板 payload |
| `agent` | 单一循环和有限提议协议 |
| `model` | 单实例、本地 JNI 推理、结构化解析 |
| `phone` | 用户主动观察、Guardrail 统一执行入口与受限暂存实现 |
| `state` | Room 事务、任务快照、journal、恢复暂停 |
| `guardrail` | 独立确定性任务 / App / thread / target / 来源 / 接收者检查 |

任务快照包含消息与 payload；初期使用 tasks + journal 两张 Room 表。数据库是唯一事实来源，不另用 JSONL 作为状态源。

## 数据边界

本地识别和协作状态无需额外云端服务器。将来启用的协作会把授权任务内容交给所选 AI App，并受其服务规则约束。

Manifest 不包含 INTERNET / QUERY_ALL_PACKAGES；合并 release Manifest 有构建检查。应用数据排除云备份和设备迁移。截图 / UI tree 仅在内存，模型诊断文件仅保存时间、内存、动作枚举及节点路径，不保存屏幕或模型输出原文。任务和模型导入文件保存在私有目录。

无网络权限不是跨 App 数据绝不外流的保证。删除本地任务不删除第三方聊天。手动开启无障碍权限、模型文件获取与模型许可证确认由用户完成。

更多：[实现状态](docs/poc-status.md) · [模型实验](docs/local-model.md) · [威胁模型](docs/threat-model.md) · [后续验证清单](docs/verification-plan.md)

## 许可证

项目新增代码使用 MIT，见 [LICENSE](LICENSE)。llama.cpp 及第三方依赖保留自身许可证。模型权重不随项目分发。公开发布前仍需核查三款目标 App 的条款和分发要求。
