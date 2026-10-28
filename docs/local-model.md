# 手机本地模型实验

## 固定实现

- backend：llama.cpp commit `7ab4ee7baad2d920464cbacfad4f4b07cf111fd2`，MIT。
- 进程内 JNI，CPU，最多 4 推理线程；一个模型实例，串行加载 / 推理 / 释放。native 调试构建使用 -O3。默认 armv8-a 通用编译；本次手机所有 CPU 的 /proc/cpuinfo 均有 asimddp/asimdhp/i8mm，因此可以用 `-PphoneCpuArch=armv8.2-a+dotprod+fp16+i8mm` 编译专用 APK；这个 APK 不宣称兼容缺少这些指令的手机。
- 已下载 Qwen 官方 Qwen3-VL-2B-Instruct-GGUF：Q4_K_M 权重（1,107,409,952 字节）+ Q8_0 projector（445,053,216 字节），Apache-2.0。通过 ModelScope 官方仓库下载，文件 SHA-256 与 Hugging Face 官方仓库公开值一致，手机私有目录复验一致；具体版本与哈希见 [model-lock.json](model-lock.json)。
- 使用模型内 chat template、libmtmd 视觉预处理；UI tree 同一入口。
- context 4096、最大输出 256 token、输出最多 4096 字节。超出预算或结构化输出不合法返回 NEEDS_USER，不截断成合法结果。
- 截图长边最多 1024，libmtmd 的 image token 上限 1024；当前只返回节点引用，不启用视觉坐标点击。未定义/验证坐标映射就不能扩展成坐标执行。
- load 和 generate 配置了可取消回调和时间边界，但 native 某些工作段未必即时响应。是否能及时取消必须在手机上确认；取消期间不允许动作执行。
- 无 HTTP、无 loopback 服务、无下载、无云回退。不安装 Node 或 Termux。

## 如何做第一次真实实验

1. 构建包含 native backend 的 APK。
2. 从可信来源获取配套模型与投影文件，确认许可证、量化与公开校验值。
3. 在模型页面分别填写来源、许可证、量化和预期 SHA-256，选择文件导入。
4. 用检查页取得任意一款的真实观察；回到模型页运行 tree 实验，再运行截图实验。
5. 分别记录 backend / 文件元数据、加载耗时、单步耗时、250ms 采样 PSS 峰值、结构解析结果。
6. 三款各取得约 20–30 份脱敏样本后，再评估定位/拒答；错误、相似标题和生成停顿样本不能缺失。

实验记录保存到私有 files/model-experiment-last.txt 和 files/model-experiments/，仅含运行元数据及有限动作提议，不保存屏幕正文。报告中的采样 PSS 峰值不是系统精确峰值。调试版提供受 android.permission.DUMP 保护的广播入口，和 UI 共用 ModelExperiment 路径，可对三款已配置参与者中的指定一款触发同样的单次观察、对已经取得的观察运行模型或取消，不能执行模型动作。发布版不包含此广播入口。

若 GitHub 整包下载缓慢，可运行 `python3 scripts/fetch-native-source.py`，按固定 commit 逐文件取得 CPU 编译所需源码，并核对官方 Git blob 哈希。国内构建镜像只用于电脑下载，手机推理不联网。

UI tree prompt 仅包含至多 60 个可点击/可编辑节点的短标签，不提供完整业务材料。截图可能包含业务文本，但只留本地，且不成为 payload 来源。模型输出只解析 action/node/payload 三个字段，输出不得创建新授权。

## 参考

- [llama.cpp Android 构建](https://github.com/ggml-org/llama.cpp/blob/7ab4ee7baad2d920464cbacfad4f4b07cf111fd2/docs/android.md)
- [固定版本 mtmd API](https://github.com/ggml-org/llama.cpp/blob/7ab4ee7baad2d920464cbacfad4f4b07cf111fd2/tools/mtmd/mtmd.h)
- [Qwen 官方模型卡](https://huggingface.co/Qwen/Qwen3-VL-2B-Instruct)

这些资料用于实现选择，不是用户手机性能证据。
