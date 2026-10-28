package org.phoneai.teamwork.targets

import org.phoneai.teamwork.agent.NodeRole
import org.phoneai.teamwork.agent.UiNode
import org.phoneai.teamwork.agent.UiObservation
import org.phoneai.teamwork.state.TaskState

/** Read-only, version-pinned empty-composer recognition. Neither object authorizes an effect. */
object PublicEmptyProfiles {
    private const val SET_TEXT = 2097152
    private val kimiStaticText = setOf("升级订阅", "新建项目", "PPT", "集群", "构建应用",
        "尽管问，带图也行", "内容由 AI 生成", "快速", "进阶")
    private val kimiStaticDescriptions = setOf("更多输入方式", "切换至语音输入", "关闭语音播报", "导航按钮")
    private val doubaoStaticText = setOf("有什么我能帮你的吗？", "对话", "工作", "AI 创作",
        "双11问豆包", "拍题答疑")
    private val doubaoStaticDescriptions = setOf("返回", "打电话", "朗读已关闭", "深度思考，已关闭",
        "AI 创作", "双11问豆包", "拍题答疑", "相机", "语音输入", "更多面板")
    private val kimiGreeting = Regex("嗨\\s+[^\\n]{1,30}，今天要和 Kimi 一起做点什么")

    private fun base(task: TaskState, o: UiObservation, now: Long, id: String, pkg: String, version: String) =
        task.goal == DeepSeekProbeProfile.GOAL && task.steps.size == 1 &&
            task.participants.size == 1 && task.participants.single().id == id &&
            task.participants.single().packageName == pkg && task.participants.single().version == version &&
            o.taskId == task.id && o.packageName == pkg && o.appVersion == version &&
            !o.truncated && !o.locked && !o.obstructed && now - o.at in 0..5000

    private fun usable(node: UiNode) = node.editable && node.enabled && node.visible &&
        node.text.isEmpty() && node.className == "android.widget.EditText" &&
        SET_TEXT in node.supportedActions && node.bounds.split(' ').mapNotNull(String::toIntOrNull)
            .let { it.size == 4 && it[0] < it[2] && it[1] < it[3] }
    private fun offscreenArtifact(node: UiNode): Boolean {
        val box = node.bounds.split(' ').mapNotNull(String::toIntOrNull)
        return node.path == "0/0/0/0/14/0" && !node.visible &&
            node.className == "android.widget.TextView" && node.text == "打电话" &&
            node.description.isBlank() && box.size == 4 && box[1] > box[3]
    }

    private fun classified(o: UiObservation, input: UiNode): UiObservation = o.copy(
        profileVerified = true,
        nodes = o.nodes.map { it.copy(role = if (it.path == input.path) NodeRole.INPUT else NodeRole.UNKNOWN) })

    fun kimi(task: TaskState, o: UiObservation, now: Long): UiObservation {
        if (!base(task, o, now, "kimi", "com.moonshot.kimichat", "3.1.3 (378)")) return o.copy(profileVerified = false)
        val greetings = o.nodes.filter { kimiGreeting.matches(it.text) && it.visible }
        val inputs = o.nodes.filter(::usable)
        if (greetings.size != 1 || inputs.size != 1 || o.nodes.count { it.editable } != 1 ||
            o.nodes.none { it.description == "切换至语音输入" && it.visible && it.enabled } ||
            o.nodes.none { it.description == "更多输入方式" && it.visible && it.enabled } ||
            o.nodes.none { it.text == "快速" && it.visible } ||
            o.nodes.none { it.text == "进阶" && it.visible } ||
            o.nodes.any { it.text.isNotBlank() && it.text !in kimiStaticText &&
                !kimiGreeting.matches(it.text) } ||
            o.nodes.any { it.description.isNotBlank() && it.description !in kimiStaticDescriptions })
            return o.copy(profileVerified = false)
        return classified(o, inputs.single())
    }

    fun doubao(task: TaskState, o: UiObservation, now: Long): UiObservation {
        if (!base(task, o, now, "doubao", "com.larus.nova", "14.9.0 (14090001)")) return o.copy(profileVerified = false)
        val inputs = o.nodes.filter(::usable)
        val hidden = o.nodes.filter { it.editable && !it.visible }
        if (inputs.size != 1 || o.nodes.count { it.editable } != inputs.size + hidden.size ||
            hidden.size > 1 || hidden.any { it.text.isNotEmpty() } ||
            o.nodes.count(::offscreenArtifact) > 1 ||
            o.nodes.count { it.text == "有什么我能帮你的吗？" && it.visible } != 1 ||
            o.nodes.none { it.description == "返回" && it.visible && it.enabled } ||
            o.nodes.none { it.description == "打电话" && it.visible && it.enabled } ||
            o.nodes.none { it.description == "深度思考，已关闭" && it.visible && it.enabled } ||
            o.nodes.any { it.text.isNotBlank() && it.text !in doubaoStaticText && !offscreenArtifact(it) } ||
            o.nodes.any { it.description.isNotBlank() && it.description !in doubaoStaticDescriptions })
            return o.copy(profileVerified = false)
        return classified(o, inputs.single())
    }

    /** Safe diagnostic metadata for the fixed public empty-composer experiment. */
    fun diagnostics(o: UiObservation, id: String): String {
        val text = if (id == "kimi") kimiStaticText else doubaoStaticText
        val descriptions = if (id == "kimi") kimiStaticDescriptions else doubaoStaticDescriptions
        return "editableCount=${o.nodes.count { it.editable }}" +
            "\nusableInputCount=${o.nodes.count(::usable)}" +
            "\nhiddenInputCount=${o.nodes.count { it.editable && !it.visible }}" +
            "\nunknownTextLengths=${o.nodes.filter { it.text.isNotBlank() && it.text !in text && (id != "kimi" || !kimiGreeting.matches(it.text)) }.map { it.text.length }}" +
            "\nunknownTextMeta=${o.nodes.filter { it.text.isNotBlank() && it.text !in text && (id != "kimi" || !kimiGreeting.matches(it.text)) }.joinToString(";") { "${it.path}:${it.text.length}:${it.bounds}:${it.visible}:${it.className}" }}" +
            "\noffscreenArtifactCount=${o.nodes.count(::offscreenArtifact)}" +
            "\nartifactDescriptionLengths=${o.nodes.filter { it.path == "0/0/0/0/14/0" }.map { it.description.length }}" +
            "\nvisibleCallCount=${o.nodes.count { it.description == "打电话" && it.visible && it.enabled }}" +
            "\nunknownDescriptionLengths=${o.nodes.filter { it.description.isNotBlank() && it.description !in descriptions }.map { it.description.length }}" +
            "\ngreetingCount=${o.nodes.count { if (id == "kimi") kimiGreeting.matches(it.text) && it.visible else it.text == "有什么我能帮你的吗？" && it.visible }}" +
            "\nreturnCount=${o.nodes.count { it.description == "返回" && it.visible && it.enabled }}" +
            "\nthinkingOffCount=${o.nodes.count { it.description == "深度思考，已关闭" && it.visible && it.enabled }}"
    }
}
