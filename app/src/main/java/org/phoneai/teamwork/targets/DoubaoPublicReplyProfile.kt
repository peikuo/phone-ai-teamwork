package org.phoneai.teamwork.targets

import org.phoneai.teamwork.agent.UiNode
import org.phoneai.teamwork.agent.UiObservation
import org.phoneai.teamwork.state.*
import org.phoneai.teamwork.workflow.PayloadBuilder

/** Completion evidence for one fixed public exchange in Doubao 14.9.0. */
object DoubaoPublicReplyProfile {
    data class Verified(val userPath: String, val answerPath: String, val answer: String)
    private val allowedText = setOf(DeepSeekProbeProfile.ANSWER, "AI 生成可能有误 注意核实",
        "对话", "AI 创作", "双11问豆包", "拍题答疑")
    private val allowedDescription = setOf("返回", "创建新对话", "${DeepSeekProbeProfile.ANSWER}，打开设置页",
        "打电话", "朗读已关闭", "复制", "朗读", "点赞", "点踩", "分享", "重新生成",
        "深度思考，已关闭", "AI 创作", "双11问豆包", "拍题答疑", "相机", "语音输入", "更多面板")
    private val generationControls = setOf("停止生成", "正在生成", "生成中", "Stop generating", "Generating")
    private fun knownText(node: UiNode, payload: String) =
        node.text.isBlank() || node.text in allowedText || node.text == payload ||
            PublicEmptyProfiles.isHiddenCallLabel(node)
    private fun knownDescription(node: UiNode) =
        node.description.isBlank() || node.description in allowedDescription ||
            (node.description == DeepSeekProbeProfile.ANSWER && node.text == DeepSeekProbeProfile.ANSWER &&
                node.className == "android.widget.TextView")
    private data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int)
    private fun box(node: UiNode): Box? {
        val p = node.bounds.split(' ').mapNotNull(String::toIntOrNull)
        return if (p.size == 4 && p[0] < p[2] && p[1] < p[3]) Box(p[0], p[1], p[2], p[3]) else null
    }
    private fun assistantRoot(userPath: String, answerPath: String): String? {
        val user = userPath.split('/')
        val answer = answerPath.split('/')
        val common = user.zip(answer).takeWhile { it.first == it.second }.size
        if (common <= 3 || user.getOrNull(common) != "0" || answer.getOrNull(common) != "1") return null
        return answer.take(common + 1).joinToString("/")
    }
    fun diagnostics(task: TaskState, o: UiObservation): String {
        val payload = task.current?.payloadId?.let(task.payloads::get)
        val known = payload?.text.orEmpty()
        return "replyWindowMatches=${task.bindings["doubao"]?.initialWindowId == o.windowId}" +
            "\nreplyNodeCount=${o.nodes.size}\nreplyTruncated=${o.truncated}" +
            "\nreplyUserCount=${o.nodes.count { it.text == payload?.text && it.visible }}" +
            "\nreplyAnswerCount=${o.nodes.count { it.text == DeepSeekProbeProfile.ANSWER && it.visible }}" +
            "\nreplyCopyCount=${o.nodes.count { it.description == "复制" && it.visible }}" +
            "\nreplyRegenerateCount=${o.nodes.count { it.description == "重新生成" && it.visible }}" +
            "\nreplyUnknownTextLengths=${o.nodes.filterNot { knownText(it, known) }.map { it.text.length }}" +
            "\nreplyUnknownTextMeta=${o.nodes.filterNot { knownText(it, known) }.joinToString(";") { "${it.path}:${it.text.length}:${it.bounds}:${it.visible}:${it.className}" }}" +
            "\nreplyUnknownDescriptionLengths=${o.nodes.filterNot(::knownDescription).map { it.description.length }}" +
            "\nreplyUnknownDescriptionMeta=${o.nodes.filterNot(::knownDescription).joinToString(";") { "${it.path}:${it.description.length}:${it.bounds}:${it.visible}:${it.className}" }}"
    }
    fun verify(task: TaskState, o: UiObservation, now: Long): Verified? {
        if (task.goal != DeepSeekProbeProfile.GOAL || task.steps.size != 1 ||
            task.status != TaskStatus.RUNNING || task.current?.status != TurnStatus.SUBMITTING ||
            task.pendingIntent.isNullOrBlank() || task.participants.singleOrNull()?.id != "doubao" ||
            task.participants.single().packageName != "com.larus.nova" ||
            task.participants.single().version != "14.9.0 (14090001)" ||
            o.taskId != task.id || o.packageName != "com.larus.nova" ||
            o.appVersion != "14.9.0 (14090001)" || o.truncated || o.locked || o.obstructed ||
            now - o.at !in 0..5000) return null
        val binding = task.bindings["doubao"] ?: return null
        if (binding.taskId != task.id || binding.participantId != "doubao" ||
            binding.appVersion != o.appVersion || binding.initialWindowId != o.windowId ||
            binding.firstUserText.isNotEmpty() || binding.latestUserText.isNotEmpty()) return null
        val step = task.current ?: return null
        val payload = step.payloadId?.let(task.payloads::get) ?: return null
        if (!PayloadBuilder.matches(task, step, payload)) return null
        if (o.nodes.any { it.visible && it.enabled &&
            (it.text in generationControls || it.description in generationControls) }) return null
        if (o.nodes.count(PublicEmptyProfiles::isHiddenCallLabel) > 1 ||
            o.nodes.any { !knownText(it, payload.text) } ||
            o.nodes.any { !knownDescription(it) }) return null
        if (o.nodes.any { it.editable && it.text.isNotBlank() }) return null
        val users = o.nodes.filter { it.text == payload.text && it.visible && it.enabled &&
            !it.editable && it.className == "android.widget.TextView" }
        if (users.size != 1) return null
        val user = users.single()
        val userBox = box(user) ?: return null
        val verified = o.nodes.filter { it.text == DeepSeekProbeProfile.ANSWER && it.visible && it.enabled &&
            !it.editable && it.className == "android.widget.TextView" }.mapNotNull { answer ->
            val root = assistantRoot(user.path, answer.path) ?: return@mapNotNull null
            val answerBox = box(answer) ?: return@mapNotNull null
            if (answerBox.top <= userBox.bottom || answerBox.left >= userBox.left ||
                answerBox.right >= userBox.right) return@mapNotNull null
            val copy = o.nodes.filter { it.description == "复制" && it.path.startsWith("$root/") &&
                it.visible && it.enabled }
            val regenerate = o.nodes.filter { it.description == "重新生成" && it.path.startsWith("$root/") &&
                it.visible && it.enabled }
            if (copy.size != 1 || regenerate.size != 1 ||
                box(copy.single())?.top?.let { it > answerBox.bottom } != true ||
                box(regenerate.single())?.top?.let { it > answerBox.bottom } != true) return@mapNotNull null
            Verified(user.path, answer.path, answer.text)
        }
        return verified.singleOrNull()
    }
}
