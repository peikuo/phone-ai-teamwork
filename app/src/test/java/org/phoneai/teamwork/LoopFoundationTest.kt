package org.phoneai.teamwork

import org.junit.Assert.*
import org.junit.Test
import org.phoneai.teamwork.agent.*
import org.phoneai.teamwork.guardrail.*
import org.phoneai.teamwork.state.*
import org.phoneai.teamwork.workflow.*

class LoopFoundationTest {
    private val participant = Participant("deepseek", "fixture", "test.deepseek", "fixture")
    private val recipe = Recipe.discussion("public fixture question", listOf(participant), participant.id)
    private val originalStep = recipe.steps.first()
    private val payload = PayloadBuilder.build(recipe, originalStep)
    private val task = recipe.copy(status = TaskStatus.RUNNING, deadline = 100000,
        steps = listOf(originalStep.copy(status = TurnStatus.PREPARED, payloadId = payload.id)),
        payloads = mapOf(payload.id to payload))
    private val input = UiNode("0/1", "fixture/input", "", "", true, true, "0 0 100 100", NodeRole.INPUT)
    private val before = UiObservation("old", task.id, participant.packageName, 1, 1, 1000,
        listOf(input), participant.version, false, false, false, true)
    private val fresh = before.copy(id = "new", epoch = 3, at = 40000)
    private val focus = ProposedAction(Action.FOCUS_INPUT, participant.id, before.target(input))

    private fun rejected(block: () -> Unit) {
        try { block(); fail("Expected rejection") } catch (_: IllegalArgumentException) { }
    }

    @Test fun unchangedScreenCanRebindButStillNeedsGuardrail() {
        val proposal = TargetRebinder.rebind(focus, before, fresh, 40010)
        assertEquals("REBOUND_NOT_AUTHORIZED", proposal.reason)
        assertEquals(fresh.target(input), proposal.action!!.target)
        assertEquals(GuardrailDecision.ALLOW, Guardrail().authorize(proposal.action!!, task, fresh, null, 40010).decision)
        assertEquals(GuardrailDecision.DENY, Guardrail().authorize(proposal.action!!, task.copy(status = TaskStatus.CANCELLED), fresh, null, 40010).decision)
    }
    @Test fun surroundingTextChangeRequiresNewProposal() {
        val contextNode = input.copy(path = "0/2", editable = false, text = "old")
        val old = before.copy(nodes = listOf(input, contextNode))
        val next = fresh.copy(nodes = listOf(input, contextNode.copy(text = "new")))
        assertNull(TargetRebinder.rebind(focus, old, next, 40010).action)
    }
    @Test fun forgedTargetWindowSwitchAndStaleRefreshAreRejected() {
        assertNull(TargetRebinder.rebind(focus.copy(target = focus.target!!.copy(taskId = "wrong")), before, fresh, 40010).action)
        assertNull(TargetRebinder.rebind(focus, before, fresh.copy(windowId = 2), 40010).action)
        assertNull(TargetRebinder.rebind(focus, before, fresh, 46000).action)
        assertNull(TargetRebinder.rebind(focus, before, before, 1001).action)
    }
    @Test fun disabledHiddenOrUnverifiedTargetsCannotBePromoted() {
        for (node in listOf(input.copy(enabled = false), input.copy(visible = false))) {
            val old = before.copy(nodes = listOf(node))
            val next = fresh.copy(nodes = listOf(node))
            assertNull(TargetRebinder.rebind(focus.copy(target = old.target(node)), old, next, 40010).action)
            assertEquals(GuardrailDecision.NEEDS_USER, Guardrail().authorize(focus.copy(target = next.target(node)), task, next, null, 40010).decision)
        }
        assertNull(TargetRebinder.rebind(focus, before, fresh.copy(profileVerified = false), 40010).action)
    }
    @Test fun writeRequiresExactEchoAndMatchingIntent() {
        val writing = TurnLifecycle.beginEffect(task, Action.WRITE, "write-1", 2000)
        rejected { TurnLifecycle.confirmWrite(writing, "other", payload.text) }
        rejected { TurnLifecycle.confirmWrite(writing, "write-1", payload.text + " altered") }
        val staged = TurnLifecycle.confirmWrite(writing, "write-1", payload.text)
        assertEquals(TurnStatus.STAGED, staged.current!!.status)
        assertNull(staged.pendingIntent)
        assertEquals(1, staged.current!!.actionCount)
    }
    @Test fun crashAfterSendIntentNeverBecomesRetryable() {
        val staged = task.copy(steps = listOf(task.current!!.copy(status = TurnStatus.STAGED)))
        val submitting = TurnLifecycle.beginEffect(staged, Action.SUBMIT, "send-1", 2000)
        assertEquals(TurnStatus.SUBMITTING, submitting.current!!.status)
        val recovered = Recovery.afterProcessDeath(submitting)
        assertEquals(TaskStatus.NEEDS_USER, recovered.status)
        assertEquals("send-1", recovered.pendingIntent)
        rejected { TurnLifecycle.beginEffect(submitting, Action.SUBMIT, "send-2", 2001) }
        rejected { TurnLifecycle.confirmSend(submitting, "send-1", "wrong echo", "proof-1") }
    }
    @Test fun incompleteWrongTurnAndUnconfirmedMessagesCannotComplete() {
        val message = Message("answer", task.id, task.current!!.id, participant.id, "answer", Completeness.COMPLETE, "proof-2", payload.sourceIds)
        rejected { TurnLifecycle.capture(task, message) }
        val staged = task.copy(steps = listOf(task.current!!.copy(status = TurnStatus.STAGED)))
        val submitting = TurnLifecycle.beginEffect(staged, Action.SUBMIT, "send-1", 2000)
        val sent = TurnLifecycle.confirmSend(submitting, "send-1", payload.text, "proof-1")
        rejected { TurnLifecycle.capture(sent, message.copy(completeness = Completeness.INCOMPLETE)) }
        rejected { TurnLifecycle.capture(sent, message.copy(turnId = "wrong")) }
        rejected { TurnLifecycle.capture(sent, message.copy(sourceIds = listOf("unapproved"))) }
        val completed = TurnLifecycle.capture(sent, message)
        assertEquals(TaskStatus.COMPLETED, completed.status)
        assertEquals(TurnStatus.CAPTURED, completed.steps.single().status)
        assertEquals("answer", completed.steps.single().responseMessageId)
        rejected { TurnLifecycle.capture(completed, message) }
    }
    @Test fun cancelledExpiredAndOverBudgetTasksCannotStartEffects() {
        rejected { TurnLifecycle.beginEffect(task.copy(status = TaskStatus.CANCELLED), Action.WRITE, "x", 2000) }
        rejected { TurnLifecycle.beginEffect(task, Action.WRITE, "x", 100000) }
        rejected { TurnLifecycle.beginEffect(task.copy(maxActionsPerStep = 0), Action.WRITE, "x", 2000) }
    }
    @Test fun initialWriteRequiresExplicitEmptyWindowEvidence() {
        val bound = task.copy(bindings = mapOf(participant.id to ThreadBinding(task.id, participant.id, "", "", participant.version, initialWindowId = 1)))
        val p = ProposedAction(Action.WRITE, participant.id, fresh.target(input), payload.id)
        val empty = ThreadEvidence(task.id, participant.id, fresh.id, false, false, true)
        assertEquals(GuardrailDecision.ALLOW, Guardrail().authorize(p, bound, fresh, empty, 40010).decision)
        assertEquals(GuardrailDecision.NEEDS_USER, Guardrail().authorize(p, bound, fresh, empty.copy(verifiedEmptyThread = false), 40010).decision)
        assertEquals(GuardrailDecision.NEEDS_USER, Guardrail().authorize(p, bound.copy(bindings = mapOf(participant.id to bound.bindings.getValue(participant.id).copy(initialWindowId = 2))), fresh, empty, 40010).decision)
    }
    @Test fun recordedEffectOnlyAcceptsItsExactCommittedTransition() {
        val bound = task.copy(bindings = mapOf(participant.id to ThreadBinding(task.id, participant.id, "", "", participant.version, initialWindowId = 1)))
        val p = ProposedAction(Action.WRITE, participant.id, fresh.target(input), payload.id)
        val e = ThreadEvidence(task.id, participant.id, fresh.id, false, false, true)
        val recorded = TurnLifecycle.beginEffect(bound, Action.WRITE, "write-1", 40010).copy(revision = bound.revision + 1)
        val guard = Guardrail()
        assertEquals(GuardrailDecision.ALLOW, guard.authorizeRecordedEffect(p, bound, recorded, "write-1", fresh, e, 40020).decision)
        assertEquals(GuardrailDecision.DENY, guard.authorizeRecordedEffect(p, bound, recorded.copy(status = TaskStatus.CANCELLED), "write-1", fresh, e, 40020).decision)
        assertEquals(GuardrailDecision.DENY, guard.authorizeRecordedEffect(p, bound, recorded, "other", fresh, e, 40020).decision)
        assertEquals(GuardrailDecision.DENY, guard.authorizeRecordedEffect(p, bound, recorded.copy(revision = 100), "write-1", fresh, e, 40020).decision)
        assertNotEquals(GuardrailDecision.ALLOW, guard.authorize(p, recorded, fresh, e, 40020).decision)
    }
}
