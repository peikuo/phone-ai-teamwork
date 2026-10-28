package org.phoneai.teamwork.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import android.widget.*
import kotlinx.coroutines.*
import org.json.JSONObject
import org.phoneai.teamwork.TeamworkApp
import org.phoneai.teamwork.BuildConfig
import org.phoneai.teamwork.agent.*
import org.phoneai.teamwork.phone.InspectionService
import org.phoneai.teamwork.state.*
import org.phoneai.teamwork.targets.Targets
import org.phoneai.teamwork.workflow.Recipe
import java.io.File
import java.security.MessageDigest

class MainActivity : Activity() {
    private val app get() = application as TeamworkApp
    private lateinit var content: LinearLayout
    private val busy get() = app.operationBusy
    private var page = "home"
    private var importRequest: ImportRequest? = null
    private data class ImportRequest(val file: String, val source: String, val license: String, val quant: String, val expectedHash: String)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        home()
    }
    override fun onResume() { super.onResume(); if (page == "inspect") inspectPage() }
    private fun frame(title: String) {
        val scroll = ScrollView(this)
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 48, 24, 32) }
        scroll.addView(content)
        scroll.setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        setContentView(scroll)
        text(title, 24f)
        if (page != "home") button("返回首页") { home() }
    }
    private fun text(value: String, size: Float = 15f): TextView = TextView(this).apply { text = value; textSize = size; setTextColor(Color.rgb(28, 38, 48)); setPadding(0, 12, 0, 12); setTextIsSelectable(true); content.addView(this) }
    private fun button(label: String, action: () -> Unit): Button = Button(this).apply { text = label; setOnClickListener { action() }; content.addView(this) }
    private fun toast(value: String) { Toast.makeText(this, value, Toast.LENGTH_LONG).show() }
    private fun work(action: suspend () -> Unit) {
        app.scope.launch {
            try { app.recovered.await(); action() }
            catch (_: Throwable) { runOnUiThread { if (!isDestroyed) toast("操作未完成；未执行自动写入或发送。") } }
        }
    }
    private fun home() {
        page = "home"; frame("Phone AI Teamwork")
        text("v0.3 · 开发 POC", 18f)
        text("当前支持任务草案、三款 App 单次观察、本地模型提议实验，以及固定公开 DeepSeek 单轮收发验证。完整协作流程仍在开发。")
        text("本地识别和状态无需额外云端服务器。未来启用协作后，获准内容会交给所选 AI App，并受各自数据处理规则约束。")
        button("创建协作任务 / 预览 Recipe") { newTask() }
        button("能力状态与待验证功能") { capabilityPage() }
        button("任务记录与 payload 预览") { tasksPage() }
        button("Kimi / 豆包 / DeepSeek 检查") { inspectPage() }
        button("本地模型导入与实验") { modelPage() }
        button("停止检查并取消模型推理") {
            InspectionService.instance?.stopInspection()
            work { app.model.cancel(); app.store.all().filter { it.status in setOf(TaskStatus.RUNNING, TaskStatus.WAITING) }.forEach { app.store.pause(it.id, "USER_PAUSED") } }
            toast("已请求停止；不会撤回第三方 App 中已有的消息或草稿。")
        }
    }
    private fun capabilityPage() {
        page = "capabilities"; frame("能力状态与待验证功能")
        text("有限实测仅适用于记录的 App、版本和页面。离线回放不证明手机操作或模型识别成功。")
        val catalog = org.phoneai.teamwork.capability.CapabilityCatalog
        catalog.capabilities.forEach { c ->
            text("${c.label} · ${c.level.label}", 18f)
            text("责任：${c.owner}（${c.id}）\n范围：${c.scope}\n待验收：${c.acceptance}")
        }
        text("等待能力确认后再实现的功能", 20f)
        catalog.deferred.forEach { f ->
            text("${f.label}：暂缓\n依赖：${catalog.blockers(f.id).joinToString { it.label }}")
        }
    }
    private fun newTask() {
        page = "new"; frame("创建任务草案")
        val goal = EditText(this).apply { hint = "你的原始问题"; minLines = 3; gravity = android.view.Gravity.TOP; content.addView(this) }
        val choices = Targets.all.map { target -> target to CheckBox(this).apply { text = target.label; isChecked = true; content.addView(this) } }
        text("选择总结者（必须同时选为参与者）")
        val summary = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, Targets.all.map { it.label }); content.addView(this) }
        val consent = CheckBox(this).apply { text = "授权选中参与者接收原问题；审阅者接收其他参与者第一轮产物；总结者接收所有产物。当前只保存草案，不外发。"; content.addView(this) }
        button("保存任务和分享范围") {
            val selected = choices.filter { it.second.isChecked }.map { it.first }
            val summarizer = Targets.all[summary.selectedItemPosition].id
            if (goal.text.isBlank() || selected.isEmpty() || summarizer !in selected.map { it.id } || !consent.isChecked) { toast("请填写任务、选择参与者和总结者，并确认分享范围。"); return@button }
            val originalGoal = goal.text.toString()
            val participants = selected.map { it.installed(this) ?: Participant(it.id, it.label, it.configuredPackage, "UNVERIFIED") }
            work { val task = Recipe.discussion(originalGoal, participants, summarizer); app.store.create(task); app.store.prepare(task.id); runOnUiThread { if (!isDestroyed) showTask(task.id) } }
        }
    }
    private fun tasksPage() {
        page = "tasks"; frame("本地任务")
        work {
            val tasks = app.store.all()
            runOnUiThread {
                if (isDestroyed || page != "tasks") return@runOnUiThread
                if (tasks.isEmpty()) text("尚无任务")
                tasks.forEach { task -> button("${task.goal.take(48)} · ${task.status}") { showTask(task.id) } }
            }
        }
    }
    private fun showTask(taskId: String) {
        page = "task:$taskId"; frame("任务与来源")
        work {
            val task = app.store.get(taskId); val events = app.store.journal(taskId)
            runOnUiThread {
                if (isDestroyed || page != "task:$taskId") return@runOnUiThread
                text(task.goal, 20f)
                text("${task.status} · ${task.reason.orEmpty()}\n任务 ${task.id}\n当前步骤 ${minOf(task.cursor+1, task.steps.size)}/${task.steps.size}")
                val answer = task.finalAnswer() ?: task.verifiedSingleReply()
                answer?.let {
                    text("${if (task.steps.size == 1) "Verified test response" else "Final answer"} · ${task.participants.singleOrNull { p -> p.id == answer.participantId }?.label ?: answer.participantId}", 20f)
                    text(answer.text, 18f).apply {
                        setBackgroundColor(Color.rgb(238, 245, 250))
                        setPadding(16, 20, 16, 20)
                    }
                    text(if (task.steps.size == 1) "Saved in Phone AI Teamwork · verified app response"
                        else "Saved in Phone AI Teamwork · ${answer.sourceIds.size} verified source responses")
                }
                if (task.status == TaskStatus.COMPLETED && answer == null)
                    text("Final answer unavailable: stored result requires verification.")
                task.steps.forEachIndexed { i, s -> text("${i+1}. ${s.participantId} · ${s.template} · ${s.status}\n来源步骤：${s.sourceStepIds.map { source -> task.steps.indexOfFirst { it.id == source } + 1 }}") }
                task.current?.payloadId?.let { task.payloads[it] }?.let { payload ->
                    button("查看完整待发正文（仅本地）") { AlertDialog.Builder(this).setTitle("${payload.recipient} / ${payload.hash.take(12)}").setMessage(payload.text).setPositiveButton("关闭", null).show() }
                }
                button("暂停") { work { app.store.pause(taskId, "USER_PAUSED"); runOnUiThread { if (!isDestroyed) showTask(taskId) } } }
                button("取消任务") { work { app.store.cancel(taskId); runOnUiThread { if (!isDestroyed) showTask(taskId) } } }
                text("取消或删除本地任务不会删除目标 AI App 内的聊天、草稿或服务端生成。")
                button("删除本地记录") { AlertDialog.Builder(this).setMessage("删除这个任务的本地正文与 journal？第三方聊天不受影响。").setNegativeButton("保留", null).setPositiveButton("删除") { _, _ -> work { app.store.delete(taskId); runOnUiThread { if (!isDestroyed) tasksPage() } } }.show() }
                text("Journal（不含屏幕正文）", 18f)
                events.forEach { text("${it.kind} · ${it.reason}") }
            }
        }
    }
    private fun inspectPage() {
        page = "inspect"; frame("三款 App 单次观察")
        text("只观察你选中的 App，最长 30 秒、一次窗口。请先手动打开要检查的页面。不会点击或输入。控件角色目前全部视为 UNKNOWN。")
        text("默认包名来自本次实机。其他渠道安装可通过构建参数覆盖；未绑定项不会扫描其他 App。")
        text("Accessibility：${if (InspectionService.instance == null) "未连接" else "已连接"}")
        button("打开无障碍设置（由你手动授权）") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        val screenshot = CheckBox(this).apply { text = "同时取当前窗口截图（Android 14+，仅内存）"; content.addView(this) }
        Targets.all.forEach { target ->
            val participant = target.installed(this)
            text("${target.label}\n包名：${target.configuredPackage}\n版本：${participant?.version ?: "未绑定 / 未安装 / 不可见，未知"}", 17f)
            button("观察 ${target.label} 下一次窗口") {
                if (participant == null) { toast("请先按 docs/device-check.md 配置实际包名并重新构建。"); return@button }
                val service = InspectionService.instance ?: run { toast("请先手动开启无障碍服务"); return@button }
                service.arm(participant, screenshot.isChecked); toast(service.status)
            }
        }
        button("停止并清除内存观察") { InspectionService.instance?.stopInspection(); inspectPage() }
        button("刷新检查结果") { inspectPage() }
        val service = InspectionService.instance
        text(service?.status ?: "未连接服务")
        service?.lastSample?.let { sample ->
            val o = sample.observation
            text("${o.packageName} · ${o.appVersion}\nwindow=${o.windowId} nodes=${o.nodes.size} truncated=${o.truncated}\n${sample.screenshotStatus}\n此历史观察仅供检查，不可作为当前执行 target。")
            button("查看节点原文（本地弹窗）") { AlertDialog.Builder(this).setTitle("临时观察 / 不外发").setMessage(o.nodes.joinToString("\n") { "${it.path} ${it.resourceId.orEmpty()} [${it.bounds}]\n${it.text} ${it.description}" }).setPositiveButton("关闭", null).show() }
            button("对这份观察做本地模型提议") { modelPage() }
        }
    }
    private fun modelPage() {
        page = "model"; frame("本地模型实验")
        text("${app.model.info.backend}\nAPK backend：${if (BuildConfig.LOCAL_MODEL) "已编入（未代表已运行）" else "未编入"}\n候选：Qwen3-VL-2B-Instruct GGUF + 匹配 mmproj。模型文件不随 APK 分发。")
        text("两个文件均由你从可信来源取得，确认许可证后导入。输入预期 SHA-256，用于逐字节校验。")
        fun input(hint: String) = EditText(this).apply { this.hint = hint; content.addView(this) }
        val source = input("权重来源 URL / 版本")
        val license = input("许可证，例如 Apache-2.0（以实际文件为准）")
        val quant = input("量化，例如 Q4_K_M / projector F16")
        val hash = input("所选文件的 SHA-256（64 位十六进制）")
        fun choose(file: String) {
            val digest = hash.text.toString().trim().lowercase()
            if (source.text.isBlank() || license.text.isBlank() || quant.text.isBlank() || !digest.matches(Regex("[a-f0-9]{64}"))) { toast("请填写来源、许可证、量化和预期 SHA-256"); return }
            if (busy.get()) { toast("请等待当前导入或实验结束"); return }
            importRequest = ImportRequest(file, source.text.toString(), license.text.toString(), quant.text.toString(), digest)
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "*/*" }, 71)
        }
        button("选择 / 替换模型 GGUF") { choose("model.gguf") }
        button("选择 / 替换视觉投影 mmproj GGUF") { choose("mmproj.gguf") }
        val directory = File(filesDir, "models")
        for (file in listOf("model.gguf", "mmproj.gguf")) {
            val metadata = File(directory, "$file.json")
            text("$file：${if (File(directory, file).exists()) "已导入" else "未导入"}\n${if (metadata.exists()) metadata.readText() else "尚无来源记录"}")
        }
        text("实验目标：让小模型找当前可用的文字输入框；没有、被遮挡或不确定则暂停。提议只读，不执行动作。截图长边最多 1024。实验结果保留本地耗时与内存采样，不保存模型输出原文。")
        fun experiment(withImage: Boolean) {
            if (busy.get()) { toast("已有操作正在执行"); return }
            text("正在本地加载 / 推理，可点击取消。模型首次加载可能较慢。")
            work {
                val report = org.phoneai.teamwork.model.ModelExperiment.run(app, withImage)
                runOnUiThread { if (!isDestroyed) AlertDialog.Builder(this).setTitle("手机本地实验结果").setMessage(report).setPositiveButton("关闭", null).show() }
            }
        }

        button("运行 UI tree 提议实验") { experiment(false) }
        button("运行 UI tree + 窗口截图实验") { experiment(true) }
        button("取消推理") { work { app.model.cancel() }; toast("已请求取消；不产生手机动作") }
        button("卸载模型实例（保留文件）") { work { app.model.close(); runOnUiThread { if (!isDestroyed) toast("模型实例已释放") } } }
        val report = File(filesDir, "model-experiment-last.txt")
        text(if (report.exists()) report.readText() else "尚未执行手机推理，没有时延或准确率数据。")
    }
    @Deprecated("Native Activity document result")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 71) return
        val request = importRequest; importRequest = null
        val uri = data?.data
        if (resultCode != RESULT_OK || request == null || uri == null) return
        if (!busy.compareAndSet(false, true)) { toast("已有操作正在执行，请稍后重选文件"); return }
        toast("正在复制到应用私有目录并校验 SHA-256")
        work {
            val dir = File(filesDir, "models").apply { mkdirs() }
            val tmp = File(dir, "${request.file}.part")
            try {
                app.model.close()
                val digest = MessageDigest.getInstance("SHA-256")
                var length = 0L
                contentResolver.openInputStream(uri).use { stream ->
                    requireNotNull(stream)
                    tmp.outputStream().use { out ->
                        val buffer = ByteArray(1024*1024)
                        while (true) {
                            val n = stream.read(buffer); if (n < 0) break
                            length += n; require(length <= 6L*1024*1024*1024) { "FILE_TOO_LARGE" }
                            digest.update(buffer,0,n); out.write(buffer,0,n)
                        }
                        out.fd.sync()
                    }
                }
                require(length > 16)
                val magic = tmp.inputStream().use { stream -> ByteArray(4).also { require(stream.read(it) == 4) } }
                require(magic.contentEquals("GGUF".toByteArray()))
                val actual = digest.digest().joinToString("") { "%02x".format(it) }
                require(actual == request.expectedHash) { "HASH_MISMATCH" }
                val metadata = JSONObject().put("source", request.source).put("license", request.license).put("quantization", request.quant).put("sha256", actual).put("bytes", length).put("backend", app.model.info.backend)
                check(tmp.renameTo(File(dir,request.file)))
                File(dir, "${request.file}.json").writeText(metadata.toString(2))
                runOnUiThread { if (!isDestroyed) { toast("已导入并通过 SHA-256 校验；尚未推理"); modelPage() } }
            } finally { tmp.delete(); busy.set(false) }
        }
    }
}
