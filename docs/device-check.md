# 设备检查与三款绑定

用户已授权调试，并已执行只读设备检查和三款启动观察。结果见 `device-observations-2026-09-22.json`；以下流程供后续复现。

## 1. 记录硬件

用户启用 USB 调试并批准这台开发电脑后，在开发期记录型号、SoC、RAM、Android / ROM、屏幕分辨率和字体比例。运行时产品不依赖 adb。

```bash
adb devices -l
adb shell getprop ro.product.model
adb shell getprop ro.soc.model
adb shell getprop ro.build.version.release
adb shell getprop ro.build.fingerprint
adb shell cat /proc/meminfo
adb shell wm size
adb shell settings get system font_scale
```

不要分享未经检查的完整 dumpsys 或屏幕文件。

## 2. 逐个获取包名

手动打开 Kimi、豆包、DeepSeek 的目标页面，分别查看前台 Activity：

```bash
adb shell dumpsys activity activities
# 从当前 resumed activity 取得实际包名；不要把示例或搜索结果当作证据。
adb shell dumpsys package 实际包名
```

仅记录目标包名、versionName、versionCode，查看后清理包含其他 App 信息的临时输出。确认确实是用户选择的官方目标 App。

## 3. 有边界的包可见性

```bash
./gradlew :app:assembleDebug   -PkimiPackage=实际Kimi包名   -PdoubaoPackage=实际豆包包名   -PdeepseekPackage=实际DeepSeek包名
```

安装后在检查页核对三款版本。未绑定、未安装和不可见不会被推断为已验证。每款的自动操作 profile 默认关闭。

## 4. 单次观察入口

用户手动启用服务 → 在检查页选择一款 → 按“观察下一次窗口” → 30 秒内手动切换到目标 App → 返回并刷新。

每次最多读取 300 节点、深度 30，单节点文字上限 2000 字、描述上限 500 字；任何限长都会标记 truncated。只用于 UI 定位实验，绝不把这样的观察当成完整回答产物。节点角色全为 UNKNOWN。

Android 14+ 的截图使用窗口级 API；Android 11–13 保留 tree 观察，不使用全屏截图作为替代。发现密码输入框、遮挡、锁屏、窗口变化时不生成截图。系统自身的截图限制仍可能导致失败。

每款分别覆盖：入口、历史、新建、已有 thread、输入、发送、生成中、完成、长回答、错误/弹窗。已有每款一次入口状态观察，不是完整矩阵；没有输入、发送、抓取或恢复成功率。
