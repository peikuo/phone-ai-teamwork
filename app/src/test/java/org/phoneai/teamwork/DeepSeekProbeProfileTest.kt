package org.phoneai.teamwork

import org.junit.Assert.*
import org.junit.Test
import org.phoneai.teamwork.agent.*
import org.phoneai.teamwork.state.*
import org.phoneai.teamwork.targets.DeepSeekProbeProfile
import org.phoneai.teamwork.workflow.Recipe

class DeepSeekProbeProfileTest {
    private val participant = Participant("deepseek", "DeepSeek", "com.deepseek.chat", "2.5.2 (273)")
    private val recipe = Recipe.discussion(DeepSeekProbeProfile.GOAL, listOf(participant), participant.id)
    private val task = recipe.copy(steps = recipe.steps.take(1))
    private fun node(path: String, text: String = "", description: String = "") =
        UiNode(path, null, text, description, false, false, "0 0 100 100")
    private val input = node("0/3").copy(editable = true, supportedActions = setOf(2097152))
    private fun observation(greeting: String) = UiObservation("fixture", task.id, participant.packageName, 1, 1, 1000,
        listOf(node("0").copy(className = "androidx.compose.ui.platform.ComposeView"),
            node("0/1", greeting), node("0/2", description = "开启新对话"), input,
            node("0/4", "回答由 AI 生成，仅供参考").copy(visible = false)), participant.version, false, false, false)

    @Test fun observedDayAndEveningGreetingsAreAccepted() {
        for (greeting in listOf("你好，有什么我能帮你的吗？", "晚上好，有什么我能帮你的吗？")) {
            val o = DeepSeekProbeProfile.classify(task, observation(greeting))
            assertTrue(o.profileVerified)
            assertEquals(NodeRole.INPUT, o.nodes.single { it.path == input.path }.role)
        }
    }
    @Test fun unfamiliarPageDraftAndChangedVersionAreRejected() {
        val o = observation("你好，有什么我能帮你的吗？")
        assertFalse(DeepSeekProbeProfile.empty(task, observation("Unknown page")))
        assertFalse(DeepSeekProbeProfile.empty(task, o.copy(nodes = o.nodes + node("0/4", "unexpected dialog"))))
        assertFalse(DeepSeekProbeProfile.empty(task, o.copy(nodes = o.nodes.map { if (it.path == input.path) it.copy(text = "private draft") else it })))
        assertFalse(DeepSeekProbeProfile.empty(task, o.copy(nodes = o.nodes + node("0/5", "hidden conversation").copy(visible = false))))
        assertFalse(DeepSeekProbeProfile.empty(task, o.copy(appVersion = "different")))
        assertFalse(DeepSeekProbeProfile.empty(task.copy(goal = "arbitrary task"), o))
    }
    @Test fun ambiguousOrNonWritableInputIsNotVerified() {
        val o = observation("你好，有什么我能帮你的吗？")
        assertFalse(DeepSeekProbeProfile.classify(task, o.copy(nodes = o.nodes + input.copy(path = "0/4"))).profileVerified)
        assertFalse(DeepSeekProbeProfile.classify(task, o.copy(nodes = o.nodes.map { it.copy(supportedActions = emptySet()) })).profileVerified)
    }
}
