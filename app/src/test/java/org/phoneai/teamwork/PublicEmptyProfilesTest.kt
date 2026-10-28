package org.phoneai.teamwork

import org.junit.Assert.*
import org.junit.Test
import org.phoneai.teamwork.agent.*
import org.phoneai.teamwork.state.Participant
import org.phoneai.teamwork.state.ThreadBinding
import org.phoneai.teamwork.state.TurnStatus
import org.phoneai.teamwork.targets.DeepSeekProbeProfile
import org.phoneai.teamwork.targets.PublicEmptyProfiles
import org.phoneai.teamwork.workflow.PayloadBuilder
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
        assertTrue(PublicEmptyProfiles.doubao(task, obs.copy(nodes = nodes + node("0/0/0/0/13/0", "打电话", visible = false,
            bounds = "489 2610 591 2358")), 1100).profileVerified)
        assertFalse(PublicEmptyProfiles.doubao(task, obs.copy(nodes = nodes + node("0/0/0/0/12/0", "打电话", visible = false,
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

    @Test fun pendingWriteReadbackRequiresExactCanonicalPayloadAndBoundWindow() {
        val initial = task("doubao", "com.larus.nova", "14.9.0 (14090001)")
        val step = initial.steps.single().copy(status = TurnStatus.PREPARED)
        val prepared = initial.copy(steps = listOf(step))
        val payload = PayloadBuilder.build(prepared, step)
        val active = prepared.copy(steps = listOf(step.copy(payloadId = payload.id)),
            payloads = mapOf(payload.id to payload), pendingIntent = "write-intent",
            bindings = mapOf("doubao" to ThreadBinding(prepared.id, "doubao", "", "",
                "14.9.0 (14090001)", initialWindowId = 5)))
        val nodes = listOf(node("0/0", "有什么我能帮你的吗？"), node("0/1", desc = "返回"),
            node("0/2", desc = "打电话"), node("0/3", desc = "深度思考，已关闭"),
            node("0/3a", desc = "全屏输入"), node("0/3b", desc = "发送"),
            node("0/0/0/0/13/0", "打电话", visible = false, bounds = "489 2610 591 2358"),
            node("0/4", editable = true, visible = false, bounds = "32 2179 32 2179", actions = setOf(2097152)),
            node("0/5", payload.text, editable = true, bounds = "32 2191 816 2315", actions = setOf(2097152)))
        val obs = observation(active.id, "com.larus.nova", "14.9.0 (14090001)", nodes)
        val classified = PublicEmptyProfiles.doubao(active, obs, 1100)
        assertTrue(classified.profileVerified)
        assertEquals("0/5", classified.nodes.single { it.role == NodeRole.INPUT }.path)
        assertNotNull(PublicEmptyProfiles.evidence(active, classified))
        assertNull(PublicEmptyProfiles.evidence(active, classified.copy(windowId = 6)))
        assertFalse(PublicEmptyProfiles.doubao(active.copy(payloads = emptyMap()), obs, 1100).profileVerified)
        assertFalse(PublicEmptyProfiles.doubao(active, obs.copy(nodes = nodes.map {
            if (it.path == "0/5") it.copy(text = "foreign draft") else it }), 1100).profileVerified)
        assertFalse(PublicEmptyProfiles.doubao(active.copy(pendingIntent = null), obs, 1100).profileVerified)
    }

    @Test fun kimiExpandedComposerOnlyAllowsCanonicalPendingDraft() {
        val initial = task("kimi", "com.moonshot.kimichat", "3.1.3 (378)")
        val step = initial.steps.single().copy(status = TurnStatus.PREPARED)
        val prepared = initial.copy(steps = listOf(step))
        val payload = PayloadBuilder.build(prepared, step)
        val active = prepared.copy(steps = listOf(step.copy(payloadId = payload.id)),
            payloads = mapOf(payload.id to payload), pendingIntent = "write-intent")
        val nodes = listOf(node("0/0", "嗨  Test，今天要和 Kimi 一起做点什么"),
            node("0/1", "快速"), node("0/2", "进阶"), node("0/3", desc = "切换至语音输入"),
            node("0/4", desc = "更多输入方式"), node("0/5", desc = "输入区域扩展"),
            node("0/6", desc = "发送讯息"),
            node("0/7", payload.text, editable = true, bounds = "74 1902 927 2126", actions = setOf(2097152)))
        val obs = observation(active.id, "com.moonshot.kimichat", "3.1.3 (378)", nodes)
        assertTrue(PublicEmptyProfiles.kimi(active, obs, 1100).profileVerified)
        assertFalse(PublicEmptyProfiles.kimi(active.copy(pendingIntent = null), obs, 1100).profileVerified)
        assertFalse(PublicEmptyProfiles.kimi(active, obs.copy(nodes = nodes.map {
            if (it.path == "0/7") it.copy(text = "foreign draft") else it }), 1100).profileVerified)
        assertFalse(PublicEmptyProfiles.kimi(active, obs.copy(nodes = nodes + node("0/8", desc = "private conversation")), 1100).profileVerified)
        val staged = active.copy(pendingIntent = null,
            steps = listOf(active.steps.single().copy(status = TurnStatus.STAGED)))
        val classified = PublicEmptyProfiles.kimi(staged, obs, 1100)
        assertTrue(classified.profileVerified)
        assertFalse(classified.nodes.any { it.role == NodeRole.SEND })
    }

    @Test fun doubaoStagedDraftMapsOnlyUniqueClickableSendControl() {
        val initial = task("doubao", "com.larus.nova", "14.9.0 (14090001)")
        val step = initial.steps.single().copy(status = TurnStatus.PREPARED)
        val prepared = initial.copy(steps = listOf(step))
        val payload = PayloadBuilder.build(prepared, step)
        val staged = prepared.copy(steps = listOf(step.copy(status = TurnStatus.STAGED, payloadId = payload.id)),
            payloads = mapOf(payload.id to payload))
        val input = node("0/5", payload.text, editable = true, bounds = "32 2006 932 2326", actions = setOf(2097152))
        val send = node("0/6", desc = "发送", bounds = "932 2179 1048 2326", actions = setOf(16))
            .copy(className = "android.widget.ImageView", clickable = true)
        val nodes = listOf(node("0/0", "有什么我能帮你的吗？"), node("0/1", desc = "返回"),
            node("0/2", desc = "打电话"), node("0/3", desc = "深度思考，已关闭"),
            node("0/4", desc = "全屏输入"),
            node("0/0/0/0/13/0", "打电话", visible = false, bounds = "489 2610 591 2358"),
            input, send)
        val obs = observation(staged.id, "com.larus.nova", "14.9.0 (14090001)", nodes)
        val classified = PublicEmptyProfiles.doubao(staged, obs, 1100)
        assertTrue(classified.profileVerified)
        assertEquals(send.path, classified.nodes.single { it.role == NodeRole.SEND }.path)
        assertNotNull(PublicEmptyProfiles.doubaoSendTarget(staged, classified, classified.target(classified.nodes.single { it.role == NodeRole.SEND })))
        assertNull(PublicEmptyProfiles.doubaoSendTarget(staged, classified, classified.target(classified.nodes.single { it.role == NodeRole.INPUT })))
        assertFalse(PublicEmptyProfiles.doubao(staged, obs.copy(nodes = nodes + send.copy(path = "0/7")), 1100).nodes.any { it.role == NodeRole.SEND })
        assertFalse(PublicEmptyProfiles.doubao(staged, obs.copy(nodes = nodes.map { if (it.path == send.path) it.copy(clickable = false) else it }), 1100).nodes.any { it.role == NodeRole.SEND })
        assertFalse(PublicEmptyProfiles.doubao(staged, obs.copy(nodes = nodes.map { if (it.path == send.path) it.copy(bounds = "200 100 316 247") else it }), 1100).nodes.any { it.role == NodeRole.SEND })
        assertFalse(PublicEmptyProfiles.doubao(staged, obs.copy(nodes = nodes.map { if (it.path == send.path) it.copy(bounds = "1932 2179 2048 2326") else it }), 1100).nodes.any { it.role == NodeRole.SEND })
        assertFalse(PublicEmptyProfiles.doubao(staged, obs.copy(nodes = nodes.map { if (it.path == input.path) it.copy(text = "foreign") else it }), 1100).profileVerified)
    }
}
