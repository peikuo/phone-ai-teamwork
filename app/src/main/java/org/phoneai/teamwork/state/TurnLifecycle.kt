package org.phoneai.teamwork.state

import org.phoneai.teamwork.agent.Action
import org.phoneai.teamwork.workflow.PayloadBuilder

/** Deterministic state transitions. Callers must supply independently verified device evidence.
 * These functions neither authorize an action nor infer a successful device effect.
 */
object TurnLifecycle {
    private fun payload(task: TaskState): Payload {
        val step = requireNotNull(task.current)
        val payload = task.payloads.getValue(requireNotNull(step.payloadId))
        require(PayloadBuilder.matches(task, step, payload)) { "INVALID_PAYLOAD" }
        return payload
    }

    private fun running(task: TaskState) {
        require(task.status == TaskStatus.RUNNING) { "TASK_NOT_RUNNING" }
    }

    private fun replace(task: TaskState, step: Step) = task.steps.map { if (it.id == step.id) step else it }

    fun beginEffect(task: TaskState, action: Action, intentId: String, now: Long): TaskState {
        running(task)
        require(intentId.isNotBlank() && task.pendingIntent == null) { "UNRESOLVED_INTENT" }
        require(now < task.deadline) { "BUDGET_EXHAUSTED" }
        val step = requireNotNull(task.current)
        require(step.actionCount < task.maxActionsPerStep) { "BUDGET_EXHAUSTED" }
        payload(task)
        val nextStatus = when (action) {
            Action.WRITE -> { require(step.status == TurnStatus.PREPARED); TurnStatus.PREPARED }
            Action.SUBMIT -> { require(step.status == TurnStatus.STAGED); TurnStatus.SUBMITTING }
            else -> error("UNSUPPORTED_EFFECT")
        }
        return task.copy(pendingIntent = intentId, reason = "${action}_INTENT_RECORDED",
            steps = replace(task, step.copy(status = nextStatus, actionCount = step.actionCount + 1)))
    }

    fun confirmWrite(task: TaskState, intentId: String, exactInputText: String): TaskState {
        running(task)
        require(task.pendingIntent == intentId && intentId.isNotBlank()) { "INTENT_MISMATCH" }
        val step = requireNotNull(task.current)
        require(step.status == TurnStatus.PREPARED)
        require(exactInputText == payload(task).text) { "INPUT_NOT_EXACT" }
        return task.copy(pendingIntent = null, reason = "INPUT_ECHO_VERIFIED",
            steps = replace(task, step.copy(status = TurnStatus.STAGED)))
    }

    fun confirmSend(task: TaskState, intentId: String, exactUserMessage: String, evidenceId: String): TaskState {
        running(task)
        require(task.pendingIntent == intentId && intentId.isNotBlank()) { "INTENT_MISMATCH" }
        val step = requireNotNull(task.current)
        require(step.status == TurnStatus.SUBMITTING && evidenceId.matches(Regex("[A-Za-z0-9_-]{1,80}")))
        require(exactUserMessage == payload(task).text) { "SENT_MESSAGE_NOT_EXACT" }
        // The caller verifies thread + USER role + position. Text equality alone is not that proof.
        return task.copy(pendingIntent = null, reason = "SENT_EVIDENCE_VERIFIED",
            steps = replace(task, step.copy(status = TurnStatus.SENT_CONFIRMED)))
    }

    fun capture(task: TaskState, message: Message): TaskState {
        running(task)
        require(task.pendingIntent == null) { "UNRESOLVED_INTENT" }
        val step = requireNotNull(task.current)
        require(step.status in setOf(TurnStatus.SENT_CONFIRMED, TurnStatus.WAITING)) { "SEND_NOT_CONFIRMED" }
        require(message.taskId == task.id && message.turnId == step.id && message.participantId == step.participantId)
        require(message.id.isNotBlank() && message.id !in task.messages && message.text.isNotBlank() && message.evidence.isNotBlank())
        require(message.completeness in setOf(Completeness.COMPLETE, Completeness.USER_CONFIRMED)) { "INCOMPLETE_RESPONSE" }
        require(message.sourceIds == payload(task).sourceIds) { "SOURCE_MISMATCH" }
        val nextCursor = task.cursor + 1
        val complete = nextCursor == task.steps.size
        // Completion/role evidence is checked by the caller; a model saying 'done' cannot call this.
        return task.copy(steps = replace(task, step.copy(status = TurnStatus.CAPTURED, responseMessageId = message.id)),
            messages = task.messages + (message.id to message), cursor = nextCursor,
            status = if (complete) TaskStatus.COMPLETED else TaskStatus.PAUSED,
            reason = if (complete) "ALL_TURNS_CAPTURED" else "NEXT_STEP_REQUIRES_PREPARATION")
    }
}
