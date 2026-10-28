package org.phoneai.teamwork

import org.junit.Assert.*
import org.junit.Test
import org.phoneai.teamwork.agent.Action
import org.phoneai.teamwork.state.*
import org.phoneai.teamwork.workflow.PayloadBuilder
import org.phoneai.teamwork.workflow.Recipe

class WriteRecoveryTest {
    @Test fun verifiedPendingWriteBecomesPausedAndStagedInOneTransition() {
        val participant = Participant("kimi", "Kimi", "com.moonshot.kimichat", "3.1.3 (378)")
        val task = Recipe.discussion("This is a public test.", listOf(participant), "kimi")
            .let { it.copy(steps = it.steps.take(1), status = TaskStatus.RUNNING, deadline = 2000) }
        val step = task.steps.single().copy(status = TurnStatus.PREPARED)
        val prepared = task.copy(steps = listOf(step))
        val payload = PayloadBuilder.build(prepared, step)
        val withPayload = prepared.copy(steps = listOf(step.copy(payloadId = payload.id)),
            payloads = mapOf(payload.id to payload))
        val pending = TurnLifecycle.beginEffect(withPayload, Action.WRITE, "intent", 1000)
        val staged = TurnLifecycle.confirmWriteAndPause(pending, "intent", payload.text)
        assertEquals(TaskStatus.PAUSED, staged.status)
        assertEquals(TurnStatus.STAGED, staged.current?.status)
        assertNull(staged.pendingIntent)
        assertEquals("INPUT_STAGED_AWAITING_SEND_ADAPTER", staged.reason)
        assertThrows(IllegalArgumentException::class.java) {
            TurnLifecycle.confirmWriteAndPause(pending, "intent", "wrong text")
        }
    }
}
