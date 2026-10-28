package org.phoneai.teamwork

import org.junit.Assert.*
import org.junit.Test
import org.phoneai.teamwork.agent.*
import org.phoneai.teamwork.state.Participant
import org.phoneai.teamwork.targets.DeepSeekProbeProfile
import org.phoneai.teamwork.targets.PublicEmptyProfiles
import org.phoneai.teamwork.workflow.Recipe

class PublicEmptyProfilesTest {
    private fun task(id: String, pkg: String, version: String) =
        Recipe.discussion(DeepSeekProbeProfile.GOAL, listOf(Participant(id, id, pkg, version)), id)
            .let { it.copy(steps = it.steps.take(1)) }
    private fun node(path: String, text: String = "", desc: String = "", editable: Boolean = false,
        visible: Boolean = true, bounds: String = "1 1 20 20", actions: Set<Int> = emptySet()) =
        UiNode(path, null, text, desc, editable, false, bounds, enabled = true, visible = visible,
            className = if (editable) "android.widget.EditText" else "android.widget.TextView",
            supportedActions = actions)
    private fun observation(taskId: String, pkg: String, version: String, nodes: List<UiNode>) =
        UiObservation("obs", taskId, pkg, 5, 1, 1000, nodes, version, false, false, false)

    @Test fun kimiRequiresTextModeEmptyComposerAndNoConversation() {
        val task = task("kimi", "com.moonshot.kimichat", "3.1.3 (378)")
        val input = node("0/4", editable = true, bounds = "74 1462 1006 1588", actions = setOf(2097152))
        val nodes = listOf(node("0/0", "嗨  Test，今天要和 Kimi 一起做点什么"),
            node("0/1", "快速"), node("0/2", "进阶"), node("0/3", desc = "切换至语音输入"),
            node("0/5", desc = "更多输入方式"), input)
        val obs = observation(task.id, "com.moonshot.kimichat", "3.1.3 (378)", nodes)
        val classified = PublicEmptyProfiles.kimi(task, obs, 1100)
        assertTrue(classified.profileVerified)
        assertEquals(input.path, classified.nodes.single { it.role == NodeRole.INPUT }.path)
        assertFalse(PublicEmptyProfiles.kimi(task, obs.copy(nodes = nodes.filterNot { it.description == "切换至语音输入" }), 1100).profileVerified)
        assertFalse(PublicEmptyProfiles.kimi(task, obs.copy(nodes = nodes + node("0/6", "previous conversation")), 1100).profileVerified)
        assertFalse(PublicEmptyProfiles.kimi(task, obs.copy(nodes = nodes + input.copy(path = "0/6")), 1100).profileVerified)
        assertFalse(PublicEmptyProfiles.kimi(task, obs.copy(nodes = nodes.map { if (it.path == input.path) it.copy(text = "private draft") else it }), 1100).profileVerified)
        assertFalse(PublicEmptyProfiles.kimi(task, obs.copy(appVersion = "3.1.4"), 1100).profileVerified)
        assertFalse(PublicEmptyProfiles.kimi(task, obs.copy(nodes = nodes + node("0/7", desc = "private chat")), 1100).profileVerified)
        assertFalse(PublicEmptyProfiles.kimi(task, obs, 7000).profileVerified)
    }

    @Test fun doubaoNeverPromotesHiddenOrForeignDraft() {
        val task = task("doubao", "com.larus.nova", "14.9.0 (14090001)")
        val hidden = node("0/6", editable = true, visible = false, bounds = "32 2179 32 2179", actions = setOf(2097152))
        val visible = node("0/7", editable = true, bounds = "32 2191 816 2315", actions = setOf(2097152))
        val nodes = listOf(node("0/0", "有什么我能帮你的吗？"), node("0/1", desc = "返回"),
            node("0/1a", desc = "打电话"),
            node("0/2", desc = "深度思考，已关闭"), hidden, visible)
        val obs = observation(task.id, "com.larus.nova", "14.9.0 (14090001)", nodes)
        val classified = PublicEmptyProfiles.doubao(task, obs, 1100)
        assertTrue(classified.profileVerified)
        assertEquals(visible.path, classified.nodes.single { it.role == NodeRole.INPUT }.path)
        assertFalse(PublicEmptyProfiles.doubao(task, obs.copy(nodes = nodes.map { if (it.path == visible.path) it.copy(visible = false) else it }), 1100).profileVerified)
        assertFalse(PublicEmptyProfiles.doubao(task, obs.copy(nodes = nodes + visible.copy(path = "0/8")), 1100).profileVerified)
        assertFalse(PublicEmptyProfiles.doubao(task, obs.copy(nodes = nodes.map { if (it.path == visible.path) it.copy(text = "private draft") else it }), 1100).profileVerified)
        assertFalse(PublicEmptyProfiles.doubao(task, obs.copy(nodes = nodes + node("0/8", "old answer")), 1100).profileVerified)
        assertTrue(PublicEmptyProfiles.doubao(task, obs.copy(nodes = nodes + node("0/0/0/0/14/0", "打电话", visible = false,
            bounds = "489 2610 591 2358")), 1100).profileVerified)
        assertFalse(PublicEmptyProfiles.doubao(task, obs.copy(nodes = nodes + node("0/0/0/0/14/0", "abc", visible = false,
            bounds = "489 2610 591 2358")), 1100).profileVerified)
        assertFalse(PublicEmptyProfiles.doubao(task, obs.copy(nodes = nodes + node("0/0/0/0/14/0", "打电话", visible = false,
            bounds = "489 2100 591 2158")), 1100).profileVerified)
        assertFalse(PublicEmptyProfiles.doubao(task, obs.copy(nodes = nodes + node("0/0/0/0/14/0", "打电话", visible = false,
            bounds = "489 2158 591 2158")), 1100).profileVerified)
        assertFalse(PublicEmptyProfiles.doubao(task, obs.copy(nodes = nodes +
            listOf(node("0/0/0/0/14/0", "打电话", visible = false, bounds = "489 2610 591 2358"),
                node("0/0/0/0/14/0", "打电话", visible = false, bounds = "489 2610 591 2358"))), 1100).profileVerified)
        assertFalse(PublicEmptyProfiles.doubao(task, obs.copy(nodes = nodes.map { if (it.path == hidden.path) it.copy(text = "hidden draft") else it }), 1100).profileVerified)
        assertFalse(PublicEmptyProfiles.doubao(task, obs.copy(appVersion = "14.9.1"), 1100).profileVerified)
        assertFalse(PublicEmptyProfiles.doubao(task, obs.copy(nodes = nodes + node("0/8", desc = "private chat")), 1100).profileVerified)
        assertFalse(PublicEmptyProfiles.doubao(task, obs, 7000).profileVerified)
    }
}
