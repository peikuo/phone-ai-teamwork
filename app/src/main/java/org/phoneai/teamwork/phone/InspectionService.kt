package org.phoneai.teamwork.phone

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.Display
import org.phoneai.teamwork.agent.*
import org.phoneai.teamwork.guardrail.Guardrail
import org.phoneai.teamwork.state.id
import org.phoneai.teamwork.state.Participant
import org.phoneai.teamwork.state.TaskState
import org.phoneai.teamwork.state.TaskStatus
import java.util.concurrent.atomic.AtomicLong

// Read-only, user-armed inspection. No event text is logged or persisted.
class InspectionService : AccessibilityService() {
    data class Request(val participant: Participant, val taskId: String, val deadline: Long, val screenshot: Boolean)
    data class Sample(val observation: UiObservation, val image: Bitmap?, val screenshotStatus: String)
    private val handler = Handler(Looper.getMainLooper())
    private val epoch = AtomicLong(0)
    private var request: Request? = null
    private var generation = 0L
    var lastSample: Sample? = null; private set
    var status: String = "未开启检查"; private set
    override fun onServiceConnected() { instance = this }
    override fun onInterrupt() { stopInspection() }
    override fun onDestroy() { stopInspection(); instance = null; super.onDestroy() }
    internal fun observeTask(task: TaskState): UiObservation? {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (task.status != TaskStatus.RUNNING) return null
        val participant = task.participants.singleOrNull { it.id == task.current?.participantId } ?: return null
        stopInspection()
        serviceInfo = serviceInfo.apply { packageNames = arrayOf(participant.packageName) }
        inspect(Request(participant, task.id, task.deadline, false), generation)
        return lastSample?.observation?.takeIf { it.taskId == task.id }
    }

    internal fun nodeAt(o: UiObservation, path: String): AccessibilityNodeInfo? {
        check(Looper.myLooper() == Looper.getMainLooper())
        var node = rootInActiveWindow ?: return null
        if (node.packageName?.toString() != o.packageName || node.windowId != o.windowId || epoch.get() != o.epoch) { node.recycle(); return null }
        if (path != "0" && !path.startsWith("0/")) { node.recycle(); return null }
        for (part in path.split('/').drop(1)) {
            val index = part.toIntOrNull() ?: run { node.recycle(); return null }
            val next = if (index in 0 until node.childCount) node.getChild(index) else null
            node.recycle(); node = next ?: return null
        }
        return node
    }
    fun arm(participant: Participant, screenshot: Boolean) {
        stopInspection()
        val r = Request(participant, id(), System.currentTimeMillis() + 30_000, screenshot)
        request = r
        // Limit delivered package events to the selected package; no installed-app enumeration.
        serviceInfo = serviceInfo.apply { packageNames = arrayOf(participant.packageName) }
        status = "已开启 30 秒单次检查：请手动切到 ${participant.label}。返回后查看结果。"
        val token = generation
        handler.postDelayed({ if (generation == token && request == r) inspect(r, token) }, 250)
        handler.postDelayed({ if (generation == token && request != null) { request = null; status = "检查超时，未保存屏幕内容"; generation++ } }, 30_000)
    }
    fun stopInspection() {
        generation++; request = null; epoch.incrementAndGet()
        lastSample?.image?.recycle(); lastSample = null; status = "已停止并清除临时观察"
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        epoch.incrementAndGet()
        val r = request ?: return
        if (event?.packageName?.toString() != r.participant.packageName) return
        // Wait for a short stable window, with the original 30-second deadline still enforced.
        val seenEpoch = epoch.get()
        val token = generation
        handler.postDelayed({ if (request == r && token == generation && seenEpoch == epoch.get()) inspect(r, token) }, 250)
    }
    private fun inspect(r: Request, token: Long) {
        val root = rootInActiveWindow ?: return
        val pkg = root.packageName?.toString().orEmpty()
        val locked = getSystemService(KeyguardManager::class.java).isKeyguardLocked
        if (!Guardrail().canInspect(r.participant.packageName, pkg, r.deadline, System.currentTimeMillis(), !locked)) { root.recycle(); return }
        // Do not inspect through system or foreign overlays.
        val blocked = windows.any {
            it.id != root.windowId && it.type != android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD &&
                (it.isActive || it.isFocused || it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY)
        }
        if (blocked) { root.recycle(); status = "有其他窗口遮挡，未读取"; return }
        val nodes = mutableListOf<UiNode>()
        var truncated = false
        var containsPassword = false
        fun visit(node: AccessibilityNodeInfo, path: String, depth: Int) {
            if (nodes.size >= 300 || depth > 30) { truncated = true; return }
            val rect = android.graphics.Rect(); node.getBoundsInScreen(rect)
            containsPassword = containsPassword || node.isPassword
            val text = inputText(node)
            val description = if (node.isPassword) "" else node.contentDescription?.toString().orEmpty()
            if (text.length > 2000 || description.length > 500) truncated = true
            nodes += UiNode(path, node.viewIdResourceName, text.take(2000), description.take(500), node.isEditable, node.isClickable, rect.flattenToString(),
                enabled = node.isEnabled, visible = node.isVisibleToUser, className = node.className?.toString().orEmpty(), supportedActions = node.actionList.map { it.id }.toSet())
            for (i in 0 until node.childCount) {
                if (nodes.size >= 300) { truncated = true; break }
                node.getChild(i)?.let { child -> visit(child, "$path/$i", depth + 1); child.recycle() }
            }
        }
        visit(root, "0", 0)
        val o = UiObservation(id(), r.taskId, pkg, root.windowId, epoch.get(), System.currentTimeMillis(), nodes, r.participant.version, truncated, locked, false)
        root.recycle(); request = null
        lastSample = Sample(o, null, if (r.screenshot) "截图中" else "未请求截图")
        status = "已读取 ${nodes.size} 个节点；${if (truncated) "观察被限长，不可用于自动动作" else "仅内存保存"}"
        if (r.screenshot) {
            if (containsPassword) { lastSample = Sample(o, null, "检测到密码输入框，未截图"); return }
            // Window-scoped screenshot avoids capturing the keyboard / other apps. API 30–33: fail closed.
            if (android.os.Build.VERSION.SDK_INT < 34) { lastSample = Sample(o, null, "Android 14 以下未启用窗口截图"); return }
            takeScreenshotOfWindow(o.windowId, mainExecutor, object : TakeScreenshotCallback {
                override fun onSuccess(result: ScreenshotResult) {
                    val buffer = result.hardwareBuffer
                    val stillSelected = rootInActiveWindow?.let { current ->
                        val ok = current.packageName?.toString() == pkg && current.windowId == o.windowId
                        current.recycle(); ok
                    } == true
                    if (token != generation || o.epoch != epoch.get() || !stillSelected || getSystemService(KeyguardManager::class.java).isKeyguardLocked) { buffer.close(); lastSample = if (token == generation) Sample(o, null, "界面已变化，截图丢弃") else lastSample; return }
                    val hardware = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)
                    val bitmap = hardware?.copy(Bitmap.Config.ARGB_8888, false)
                    hardware?.recycle(); buffer.close()
                    lastSample = Sample(o, bitmap, if (bitmap == null) "截图转换失败" else "当前窗口截图，仅内存保存")
                }
                override fun onFailure(errorCode: Int) { if (token == generation) lastSample = Sample(o, null, "截图不可用：$errorCode") }
            })
        }
    }
    override fun dump(fd: java.io.FileDescriptor?, writer: java.io.PrintWriter?, args: Array<out String>?) {
        if (!org.phoneai.teamwork.BuildConfig.DEBUG || writer == null) return
        writer.println("phoneai.connected=true")
        writer.println("phoneai.status=$status")
        writer.println("phoneai.armed=${request != null}")
        lastSample?.let { sample ->
            writer.println("phoneai.package=${sample.observation.packageName}")
            writer.println("phoneai.observedAt=${sample.observation.at}")
            writer.println("phoneai.nodes=${sample.observation.nodes.size}")
            writer.println("phoneai.truncated=${sample.observation.truncated}")
            writer.println("phoneai.screenshot=${sample.screenshotStatus}")
            sample.observation.nodes.filter { it.editable }.forEach {
                writer.println("phoneai.input path=${it.path} class=${it.className} textLength=${it.text.length} enabled=${it.enabled} visible=${it.visible} actions=${it.supportedActions} bounds=${it.bounds}")
            }
            if (sample.observation.packageName == "com.moonshot.kimichat") {
                sample.observation.nodes.filter { it.description == "发送讯息" }.forEach { send ->
                    writer.println("phoneai.publicSend path=${send.path} class=${send.className} clickable=${send.clickable} enabled=${send.enabled} visible=${send.visible} actions=${send.supportedActions} bounds=${send.bounds}")
                    var path = send.path
                    repeat(4) { depth ->
                        path = path.substringBeforeLast('/', "")
                        val ancestor = sample.observation.nodes.singleOrNull { it.path == path }
                        if (ancestor != null) writer.println("phoneai.publicSendAncestor depth=${depth + 1} path=${ancestor.path} class=${ancestor.className} clickable=${ancestor.clickable} enabled=${ancestor.enabled} visible=${ancestor.visible} actions=${ancestor.supportedActions} bounds=${ancestor.bounds}")
                    }
                }
            }
            if (sample.observation.packageName == "com.deepseek.chat") {
                sample.observation.nodes.filter {
                    it.text.startsWith("[Phone AI Teamwork task ") || it.text == "POC_OK_7319" ||
                        it.description in setOf("Copy", "Regenerate")
                }.forEach {
                    val kind = when {
                        it.text.startsWith("[Phone AI Teamwork task ") -> "public_prompt"
                        it.text == "POC_OK_7319" -> "public_answer"
                        else -> it.description.lowercase()
                    }
                    writer.println("phoneai.publicNode kind=$kind path=${it.path} visible=${it.visible} enabled=${it.enabled} bounds=${it.bounds}")
                }
            }
        }
    }
    companion object {
        @Volatile var instance: InspectionService? = null; private set
        internal fun inputText(node: AccessibilityNodeInfo): String = when {
            node.isPassword -> "[REDACTED]"
            node.isEditable && node.isShowingHintText -> ""
            else -> node.text?.toString().orEmpty()
        }
    }
}
