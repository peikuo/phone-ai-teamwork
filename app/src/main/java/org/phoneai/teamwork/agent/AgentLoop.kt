package org.phoneai.teamwork.agent

import kotlinx.coroutines.*
import org.phoneai.teamwork.model.UiModel
import org.phoneai.teamwork.phone.PhoneTools
import org.phoneai.teamwork.state.*

class AgentLoop(private val store: AgentState, private val model: UiModel, private val phone: PhoneTools,
    private val now: () -> Long = System::currentTimeMillis) {
    suspend fun stageSingleExchange(taskId: String) = cycle(taskId, true)
    suspend fun run(taskId: String) = cycle(taskId, false)

    // One bounded cycle until device capabilities justify further transitions; never auto-submit.
    private suspend fun cycle(taskId: String, stage: Boolean) {
        var ownedRevision: Long? = null
        var executing = false
        fun stop(reason: String, needsUser: Boolean = true) {
            ownedRevision?.let { store.pauseIfCurrent(taskId, it, reason, needsUser) }
        }
        fun unchanged(): TaskState? {
            val current = store.get(taskId)
            return current.takeIf { it.status == TaskStatus.RUNNING && it.revision == ownedRevision }
        }
        fun withinBudget(task: TaskState): Boolean = now() < task.deadline &&
            task.current?.let { it.actionCount < task.maxActionsPerStep } == true
        try {
            currentCoroutineContext().ensureActive()
            val task = store.get(taskId)
            if (task.status != TaskStatus.RUNNING) return
            ownedRevision = task.revision
            val step = task.current ?: run { stop("NO_CURRENT_STEP"); return }
            if (task.pendingIntent != null || step.status in setOf(TurnStatus.SUBMITTING, TurnStatus.NEEDS_USER)) {
                stop("VERIFY_EFFECT_NO_RETRY"); return
            }
            if (!withinBudget(task)) { stop("BUDGET_EXHAUSTED"); return }
            if (stage && (task.steps.size != 1 || step.status != TurnStatus.PREPARED)) {
                stop("INVALID_STAGE_STATE"); return
            }
            val o = phone.observe(task) ?: run { stop("OBSERVATION_UNAVAILABLE"); return }
            currentCoroutineContext().ensureActive()
            val observed = unchanged() ?: return
            if (!withinBudget(observed)) { stop("BUDGET_EXHAUSTED"); return }
            val participant = task.participants.singleOrNull { it.id == step.participantId }
            if (participant == null || o.taskId != task.id || o.packageName != participant.packageName ||
                o.appVersion != participant.version || o.truncated || o.locked || o.obstructed || now() - o.at !in 0..5000) {
                stop("UNSAFE_OBSERVATION"); return
            }
            val allowed = if (stage) setOf(Action.FOCUS_INPUT, Action.PAUSE) else setOf(Action.PAUSE, Action.WAIT)
            val proposal = model.propose(ControlContext(task.id, step.id, step.participantId, step.payloadId,
                allowed, task.maxActionsPerStep - step.actionCount), o)
            currentCoroutineContext().ensureActive()
            val current = unchanged() ?: return
            if (!withinBudget(current)) { stop("BUDGET_EXHAUSTED"); return }
            val p = proposal.action ?: run { stop("MODEL_NEEDS_USER"); return }
            // Independently enforce the scheduling contract even when an alternate model backend is used.
            if (p.action !in allowed || p.participantId != step.participantId || p.payloadId != null ||
                (p.action in setOf(Action.PAUSE, Action.WAIT) && p.target != null) ||
                (p.action == Action.FOCUS_INPUT && (p.target == null || o.nodes.none { o.target(it) == p.target }))) {
                stop("MODEL_PROTOCOL_VIOLATION"); return
            }
            if (p.action == Action.PAUSE) { stop("MODEL_PAUSED", false); return }
            if (!stage) { stop("RESULT_VERIFIER_REQUIRED"); return }
            executing = true
            val result = phone.execute(taskId, p.copy(action = Action.WRITE, payloadId = step.payloadId))
            currentCoroutineContext().ensureActive()
            val after = store.get(taskId)
            if (after.status != TaskStatus.RUNNING) return
            // Tool acknowledgement alone cannot advance the turn. State must contain verified readback.
            if (result.allowed && after.current?.id == step.id && after.current?.status == TurnStatus.STAGED && after.pendingIntent == null) {
                store.pauseIfCurrent(taskId, after.revision, "INPUT_STAGED_AWAITING_SEND_ADAPTER")
            } else {
                store.pauseIfCurrent(taskId, after.revision, if (result.allowed) "WRITE_VERIFICATION_REQUIRED" else "TOOL_EXECUTION_UNCONFIRMED", true)
            }
        } catch (cancel: CancellationException) {
            withContext(NonCancellable) {
                try { model.cancel() } finally {
                    val current = store.get(taskId)
                    if (current.revision == ownedRevision || (executing && current.pendingIntent != null))
                        store.pauseIfCurrent(taskId, current.revision, "INTERRUPTED_REOBSERVE")
                }
            }
            throw cancel
        } catch (_: Exception) {
            val current = store.get(taskId)
            if (current.revision == ownedRevision || (executing && current.pendingIntent != null))
                store.pauseIfCurrent(taskId, current.revision, "AGENT_ERROR_NEEDS_USER", true)
        }
    }
}
