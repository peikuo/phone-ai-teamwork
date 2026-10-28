package org.phoneai.teamwork.targets

import org.phoneai.teamwork.agent.*
import org.phoneai.teamwork.state.*

/** Narrow, version-specific public probe. This is not a general DeepSeek conversation adapter. */
object DeepSeekProbeProfile {
    const val GOAL = "这是一次公开的界面收发测试。请只回复：POC_OK_7319。不要搜索，不要补充解释。"
    const val ANSWER = "POC_OK_7319"
    private val greetings = setOf("你好，有什么我能帮你的吗？", "晚上好，有什么我能帮你的吗？")
    fun diagnostics(task: TaskState, o: UiObservation): String = "accepted=${accepts(task, o)}\ngreetingCount=${o.nodes.count { it.text in greetings }}\nnewChatCount=${o.nodes.count { it.description == "开启新对话" }}\nclasses=${o.nodes.map { it.className }.distinct()}\nunknownTextLengths=${o.nodes.filter { it.text.isNotBlank() && it.text !in greetings && it.text !in setOf("深度思考", "智能搜索", "回答由 AI 生成，仅供参考", "发消息", "发消息或按住说话", task.current?.payloadId?.let(task.payloads::get)?.text) }.map { it.text.length }}"
    fun accepts(task: TaskState, o: UiObservation): Boolean = task.goal == GOAL && task.steps.size == 1 &&
        task.participants.size == 1 && task.participants.single().id == "deepseek" &&
        task.participants.single().packageName == "com.deepseek.chat" && o.packageName == "com.deepseek.chat" &&
        o.appVersion == "2.5.2 (273)" && o.taskId == task.id && !o.truncated && !o.locked && !o.obstructed

    fun empty(task: TaskState, o: UiObservation): Boolean {
        if (!accepts(task, o)) return false
        val payload = task.current?.payloadId?.let(task.payloads::get)?.text
        val allowed = greetings + setOf("深度思考", "智能搜索", "回答由 AI 生成，仅供参考", "发消息", "发消息或按住说话", payload.orEmpty())
        return o.nodes.count { it.text in greetings } == 1 && o.nodes.any { it.description == "开启新对话" } &&
            o.nodes.any { it.className == "androidx.compose.ui.platform.ComposeView" } &&
            o.nodes.all { it.text.isBlank() || it.text in allowed }
    }

    fun classify(task: TaskState, o: UiObservation): UiObservation {
        if (!empty(task, o)) return o.copy(profileVerified = false)
        val inputs = o.nodes.filter { it.editable && it.enabled && it.visible && 2097152 in it.supportedActions }
        if (inputs.size != 1) return o.copy(profileVerified = false)
        return o.copy(profileVerified = true, nodes = o.nodes.map { it.copy(role = if (it.path == inputs.single().path) NodeRole.INPUT else NodeRole.UNKNOWN) })
    }
    fun evidence(task: TaskState, o: UiObservation): ThreadEvidence? {
        val binding = task.bindings["deepseek"] ?: return null
        return if (binding.initialWindowId == o.windowId && empty(task, o))
            ThreadEvidence(task.id, "deepseek", o.id, false, false, verifiedEmptyThread = true) else null
    }
}
