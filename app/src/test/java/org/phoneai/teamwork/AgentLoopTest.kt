package org.phoneai.teamwork

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.phoneai.teamwork.agent.*
import org.phoneai.teamwork.model.*
import org.phoneai.teamwork.phone.*
import org.phoneai.teamwork.state.*
import org.phoneai.teamwork.workflow.*

/** Real AgentLoop with scripted model/tools and memory state; no inference or Android effects. */
class AgentLoopTest {
    private class Memory(var task: TaskState) : AgentState {
        override fun get(id: String) = task.also { require(it.id == id) }
        override fun pauseIfCurrent(id: String, revision: Long, reason: String, needsUser: Boolean): Boolean {
            if (get(id).revision != revision || task.status != TaskStatus.RUNNING) return false
            task = task.copy(revision = revision + 1, status = if (needsUser) TaskStatus.NEEDS_USER else TaskStatus.PAUSED, reason = reason)
            return true
        }
        fun change(status: TaskStatus) { task = task.copy(status = status, revision = task.revision + 1, reason = "USER_CHANGE") }
    }
    private class Fixture {
        var clock = 1000L
        val recipe = Recipe.discussion("public", listOf(Participant("p", "P", "fixture.p", "1")), "p")
        val payload = PayloadBuilder.build(recipe, recipe.steps.first())
        val store = Memory(recipe.copy(steps = listOf(recipe.steps.first().copy(status = TurnStatus.PREPARED, payloadId = payload.id)),
            payloads = mapOf(payload.id to payload), status = TaskStatus.RUNNING, deadline = 10000))
        var modelCalls = 0; var toolCalls = 0; var cancelCalls = 0; var observeCalls = 0
        val node = UiNode("0/1", "input", "", "", true, true, "0 0 100 100", NodeRole.INPUT)
        val observation = UiObservation("o", store.task.id, "fixture.p", 1, 1, clock, listOf(node), "1", false, false, false, true)
        var onObserve: suspend () -> UiObservation? = { observation }
        var onPropose: suspend (ControlContext) -> UiProposal = { UiProposal(ProposedAction(Action.FOCUS_INPUT, "p", observation.target(node)), "SCRIPTED") }
        var onExecute: suspend (ProposedAction) -> ToolResult = {
            assertEquals(Action.WRITE, it.action); assertEquals(payload.id, it.payloadId)
            store.task = TurnLifecycle.beginEffect(store.task, Action.WRITE, "write", clock).copy(revision = store.task.revision + 1)
            store.task = TurnLifecycle.confirmWrite(store.task, "write", payload.text).copy(revision = store.task.revision + 1)
            ToolResult(true, "WRITE_ECHO_VERIFIED")
        }
        val model = object : UiModel {
            override val info = UiModelInfo("SCRIPTED_NOT_A_MODEL", true, false)
            override suspend fun propose(context: ControlContext, observation: UiObservation): UiProposal { modelCalls++; return onPropose(context) }
            override suspend fun cancel() { cancelCalls++ }
            override suspend fun close() {}
        }
        val phone = object : PhoneTools {
            override suspend fun observe(task: TaskState): UiObservation? { observeCalls++; return onObserve() }
            override suspend fun execute(taskId: String, proposal: ProposedAction): ToolResult { toolCalls++; return onExecute(proposal) }
        }
        val agent = AgentLoop(store, model, phone) { clock }
        suspend fun stage() = agent.stageSingleExchange(store.task.id)
    }
    @Test fun actualAgentStagesOnlyAfterStateContainsExactReadback() = runBlocking {
        val f = Fixture(); f.stage()
        assertEquals(1, f.modelCalls); assertEquals(1, f.toolCalls)
        assertEquals(TurnStatus.STAGED, f.store.task.current!!.status)
        assertEquals("INPUT_STAGED_AWAITING_SEND_ADAPTER", f.store.task.reason)
        assertEquals(TaskStatus.PAUSED, f.store.task.status)
    }
    @Test fun acknowledgementWithoutVerificationCannotClaimStaged() = runBlocking {
        val f = Fixture(); f.onExecute = { ToolResult(true, "ACK_ONLY") }; f.stage()
        assertEquals(TurnStatus.PREPARED, f.store.task.current!!.status)
        assertEquals("WRITE_VERIFICATION_REQUIRED", f.store.task.reason)
    }
    @Test fun expiredBudgetAndPendingIntentStopBeforeObservationOrModel() = runBlocking {
        for (kind in listOf("time", "actions", "intent")) {
            val f = Fixture()
            f.store.task = when (kind) {
                "time" -> f.store.task.copy(deadline = f.clock)
                "actions" -> f.store.task.copy(maxActionsPerStep = 0)
                else -> f.store.task.copy(pendingIntent = "unresolved")
            }
            f.stage(); assertEquals(0, f.observeCalls); assertEquals(0, f.modelCalls); assertEquals(0, f.toolCalls)
        }
    }
    @Test fun stateChangeDuringObserveOrInferenceIsPreserved() = runBlocking {
        for (phase in listOf("observe", "model")) for (status in listOf(TaskStatus.CANCELLED, TaskStatus.PAUSED, TaskStatus.RUNNING)) {
            val f = Fixture()
            if (phase == "observe") f.onObserve = { f.store.change(status); f.observation }
            else f.onPropose = { f.store.change(status); UiProposal(ProposedAction(Action.FOCUS_INPUT, "p", f.observation.target(f.node)), "SCRIPTED") }
            f.stage(); assertEquals(0, f.toolCalls); assertEquals(status, f.store.task.status)
            assertEquals("USER_CHANGE", f.store.task.reason)
            if (phase == "observe") assertEquals(0, f.modelCalls)
        }
    }
    @Test fun inferenceExpiryInvalidProtocolAndPauseNeverReachTools() = runBlocking {
        for (kind in listOf("expiry", "submit", "recipient", "payload", "forged", "pause", "invalid")) {
            val f = Fixture()
            f.onPropose = {
                if (kind == "expiry") f.clock = 10000
                val p = ProposedAction(Action.FOCUS_INPUT, "p", f.observation.target(f.node))
                UiProposal(when (kind) {
                    "submit" -> p.copy(action = Action.SUBMIT)
                    "recipient" -> p.copy(participantId = "other")
                    "payload" -> p.copy(payloadId = "invented")
                    "forged" -> p.copy(target = p.target!!.copy(windowId = 99))
                    "pause" -> ProposedAction(Action.PAUSE, "p")
                    "invalid" -> null
                    else -> p
                }, "SCRIPTED")
            }
            f.stage(); assertEquals(kind, 0, f.toolCalls)
            assertNotEquals(TaskStatus.RUNNING, f.store.task.status)
        }
    }
    @Test fun coroutineCancellationStopsBackendAndPreservesUncertainIntent() = runBlocking {
        for (duringTool in listOf(false, true)) {
            val f = Fixture(); val entered = CompletableDeferred<Unit>()
            if (duringTool) f.onExecute = {
                f.store.task = TurnLifecycle.beginEffect(f.store.task, Action.WRITE, "uncertain", f.clock).copy(revision = 1)
                entered.complete(Unit); awaitCancellation()
            } else f.onPropose = { entered.complete(Unit); awaitCancellation() }
            val job = launch { f.stage() }; entered.await(); job.cancelAndJoin()
            assertEquals(1, f.cancelCalls)
            assertEquals(TaskStatus.PAUSED, f.store.task.status)
            assertEquals("INTERRUPTED_REOBSERVE", f.store.task.reason)
            assertEquals(if (duringTool) "uncertain" else null, f.store.task.pendingIntent)
        }
    }
    @Test fun failuresDoNotOverwriteNewerUserStateAndDoNotRetry() = runBlocking {
        val f = Fixture(); f.onPropose = { f.store.change(TaskStatus.CANCELLED); error("scripted failure") }; f.stage()
        assertEquals(TaskStatus.CANCELLED, f.store.task.status); assertEquals("USER_CHANGE", f.store.task.reason)
        val g = Fixture(); g.onExecute = { ToolResult(false, "UNCONFIRMED") }; g.stage()
        assertEquals(1, g.toolCalls); assertEquals("TOOL_EXECUTION_UNCONFIRMED", g.store.task.reason)
    }
    @Test fun genericRunDoesNotExecuteWaitOrUnlockUnverifiedFeatures() = runBlocking {
        val f = Fixture(); f.onPropose = { c ->
            assertEquals(setOf(Action.PAUSE, Action.WAIT), c.allowed)
            UiProposal(ProposedAction(Action.WAIT, "p"), "SCRIPTED")
        }
        f.agent.run(f.store.task.id)
        assertEquals(0, f.toolCalls); assertEquals("RESULT_VERIFIER_REQUIRED", f.store.task.reason)
    }
    @Test fun wrongOrMissingObservationIsRejectedBeforeModel() = runBlocking {
        for (kind in listOf("missing", "wrong", "stale", "truncated")) {
            val f = Fixture(); f.onObserve = { when(kind) {
                "missing" -> null
                "wrong" -> f.observation.copy(packageName = "foreign")
                "stale" -> f.observation.copy(at = -10000)
                else -> f.observation.copy(truncated = true)
            } }
            f.stage(); assertEquals(0, f.modelCalls); assertEquals(0, f.toolCalls)
        }
    }
}
