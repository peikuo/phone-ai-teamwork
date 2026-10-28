package org.phoneai.teamwork

import org.junit.Assert.*
import org.junit.Test
import org.phoneai.teamwork.agent.*
import org.phoneai.teamwork.state.*
import org.phoneai.teamwork.targets.DeepSeekProbeProfile
import org.phoneai.teamwork.targets.DoubaoPublicReplyProfile
import org.phoneai.teamwork.workflow.PayloadBuilder
import org.phoneai.teamwork.workflow.Recipe

class DoubaoPublicReplyProfileTest {
    private val participant = Participant("doubao", "Doubao", "com.larus.nova", "14.9.0 (14090001)")
    private val recipe = Recipe.discussion(DeepSeekProbeProfile.GOAL, listOf(participant), participant.id)
    private val payload = PayloadBuilder.build(recipe, recipe.steps.first())
    private val task = recipe.copy(steps = listOf(recipe.steps.first().copy(status = TurnStatus.SUBMITTING,
        payloadId = payload.id)), status = TaskStatus.RUNNING, pendingIntent = "send-1",
        payloads = mapOf(payload.id to payload), bindings = mapOf("doubao" to ThreadBinding(recipe.id,
            "doubao", "", "", participant.version, initialWindowId = 7)))
    private val prefix = "0/0/0/0"
    private fun node(path: String, text: String = "", description: String = "", bounds: String = "0 0 10 10") =
        UiNode(path, null, text, description, false, false, bounds, className = "android.widget.TextView")
    private val user = node("$prefix/5/0/0/0/0/1/0/0", payload.text, bounds = "94 306 1047 960")
    private val answer = node("$prefix/5/0/0/0/1/0/0/0/0/0", DeepSeekProbeProfile.ANSWER,
        description = DeepSeekProbeProfile.ANSWER,
        bounds = "33 1004 413 1133")
    private val title = node("$prefix/3/0/2/0", DeepSeekProbeProfile.ANSWER, bounds = "397 137 652 189")
    private val copy = node("$prefix/5/0/0/0/1/0/1/0/1/0", description = "复制", bounds = "75 1166 164 1255")
    private val regenerate = node("$prefix/5/0/0/0/1/0/1/0/2/0", description = "重新生成",
        bounds = "771 1166 860 1255")
    private val observation = UiObservation("obs", task.id, participant.packageName, 7, 1, 1000,
        listOf(user, answer, title, copy, regenerate,
            node("0/0/0/0/13/0", "打电话", bounds = "489 2610 591 2358").copy(visible = false),
            node("$prefix/3/0/2", description = "POC_OK_7319，打开设置页"),
            node("$prefix/3/0/2/1", "AI 生成可能有误 注意核实")), participant.version,
        false, false, false)

    @Test fun publicUserAndAssistantRolesWithCompletionControlsPass() {
        val result = DoubaoPublicReplyProfile.verify(task, observation, 1100)
        assertEquals(user.path, result?.userPath)
        assertEquals(answer.path, result?.answerPath)
        assertEquals(DeepSeekProbeProfile.ANSWER, result?.answer)
    }

    @Test fun incompleteAmbiguousOrForeignConversationFailsClosed() {
        assertNull(DoubaoPublicReplyProfile.verify(task, observation.copy(nodes = observation.nodes - regenerate), 1100))
        assertNull(DoubaoPublicReplyProfile.verify(task, observation.copy(nodes = observation.nodes +
            node("$prefix/5/0/0/0/1/0/1/0/3", description = "停止生成")), 1100))
        assertNull(DoubaoPublicReplyProfile.verify(task, observation.copy(nodes = observation.nodes - answer), 1100))
        assertNull(DoubaoPublicReplyProfile.verify(task, observation.copy(nodes = observation.nodes +
            user.copy(path = "$prefix/5/0/0/0/2")), 1100))
        assertNull(DoubaoPublicReplyProfile.verify(task, observation.copy(nodes = observation.nodes +
            node("$prefix/5/0/0/0/2", "private message")), 1100))
        assertNull(DoubaoPublicReplyProfile.verify(task, observation.copy(nodes = observation.nodes +
            node("$prefix/5/0/0/0/2", description = "unknown control")), 1100))
        assertNull(DoubaoPublicReplyProfile.verify(task, observation.copy(nodes = observation.nodes +
            node("0/0/0/0/13/0", "abc", bounds = "489 2610 591 2358").copy(visible = false)), 1100))
        assertNull(DoubaoPublicReplyProfile.verify(task, observation.copy(windowId = 8), 1100))
        assertNull(DoubaoPublicReplyProfile.verify(task, observation.copy(appVersion = "changed"), 1100))
        assertNull(DoubaoPublicReplyProfile.verify(task, observation, 7000))
        assertNull(DoubaoPublicReplyProfile.verify(task.copy(pendingIntent = null), observation, 1100))
    }
}
