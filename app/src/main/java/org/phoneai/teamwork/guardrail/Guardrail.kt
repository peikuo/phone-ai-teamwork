package org.phoneai.teamwork.guardrail

import org.phoneai.teamwork.agent.*
import org.phoneai.teamwork.state.*
import org.phoneai.teamwork.workflow.PayloadBuilder

enum class GuardrailDecision { ALLOW, DENY, NEEDS_USER }
data class Verdict(val decision: GuardrailDecision, val reason: String)
class Guardrail {
    private fun deny(reason: String) = Verdict(GuardrailDecision.DENY, reason)
    private fun ask(reason: String) = Verdict(GuardrailDecision.NEEDS_USER, reason)
    fun authorize(p: ProposedAction, t: TaskState, o: UiObservation?, evidence: ThreadEvidence?, now: Long): Verdict {
        if (t.status != TaskStatus.RUNNING) return deny("TASK_NOT_RUNNING")
        val step = t.current ?: return deny("NO_CURRENT_STEP")
        if (p.action == Action.PAUSE) return Verdict(GuardrailDecision.ALLOW, "USER_CONTROL")
        if (t.deadline <= now || step.actionCount >= t.maxActionsPerStep) return ask("BUDGET_EXHAUSTED")
        if (t.pendingIntent != null) return ask("UNRESOLVED_INTENT")
        val participant = t.participants.singleOrNull { it.id == p.participantId } ?: return deny("UNAUTHORIZED_APP")
        if (participant.id != step.participantId) return deny("WRONG_RECIPIENT")
        if (step.status in setOf(TurnStatus.SUBMITTING, TurnStatus.NEEDS_USER)) return ask("UNCERTAIN_TURN")
        if (p.action == Action.LAUNCH) {
            if (p.target != null || p.payloadId != null) return deny("INVALID_LAUNCH")
            return Verdict(GuardrailDecision.ALLOW, "SELECTED_APP")
        }
        if (o == null || o.taskId != t.id || o.packageName != participant.packageName || o.appVersion != participant.version) return ask("WRONG_WINDOW_OR_VERSION")
        if (o.locked || o.obstructed || o.truncated || now - o.at !in 0..5000) return ask("UNSAFE_OBSERVATION")
        if (p.action == Action.WAIT) {
            if (p.target != null || p.payloadId != null) return deny("INVALID_WAIT")
            return Verdict(GuardrailDecision.ALLOW, "BOUNDED_WAIT")
        }
        if (!o.profileVerified) return ask("APP_PROFILE_UNVERIFIED")
        val target = p.target ?: return deny("MISSING_TARGET")
        if (target != o.nodes.find { it.path == target.nodePath }?.let(o::target)) return deny("STALE_OR_FORGED_TARGET")
        val node = o.nodes.single { it.path == target.nodePath }
        if (!node.enabled || !node.visible) return ask("TARGET_NOT_INTERACTIVE")
        val role = when(p.action) {
            Action.WRITE, Action.FOCUS_INPUT -> NodeRole.INPUT; Action.SUBMIT -> NodeRole.SEND
            Action.HISTORY -> NodeRole.HISTORY; Action.NEW_THREAD -> NodeRole.NEW_THREAD
            Action.SELECT_THREAD -> NodeRole.THREAD_CANDIDATE; Action.SCROLL -> NodeRole.SCROLL_REGION
            Action.CAPTURE -> NodeRole.ASSISTANT_MESSAGE
            else -> return ask("UNVERIFIED_NAVIGATION")
        }
        if (node.role != role) return deny("UNAUTHORIZED_NODE_ROLE")
        if (p.action in setOf(Action.WRITE, Action.SUBMIT, Action.CAPTURE)) {
            val binding = t.bindings[participant.id] ?: return ask("THREAD_NOT_BOUND")
            if (binding.taskId != t.id || binding.participantId != participant.id || binding.appVersion != participant.version) return deny("WRONG_THREAD_BINDING")
            if (evidence == null || evidence.taskId != t.id || evidence.participantId != participant.id || evidence.observationId != o.id) return ask("THREAD_NOT_VERIFIED")
            if (binding.firstUserText.isEmpty() || binding.latestUserText.isEmpty()) {
                if (binding.firstUserText.isNotEmpty() || binding.latestUserText.isNotEmpty() || !evidence.verifiedEmptyThread ||
                    binding.initialWindowId != o.windowId || t.cursor != 0 || t.messages.isNotEmpty() ||
                    p.action !in setOf(Action.WRITE, Action.SUBMIT)) return ask("EMPTY_THREAD_NOT_VERIFIED")
            } else if (!evidence.firstUserMatched || !evidence.latestUserMatched) return ask("THREAD_NOT_VERIFIED")
        }
        if (p.action == Action.NEW_THREAD && participant.id in t.bindings) return deny("THREAD_ALREADY_BOUND")
        if (p.action in setOf(Action.WRITE, Action.SUBMIT)) {
            val payload = t.payloads[p.payloadId] ?: return deny("UNKNOWN_PAYLOAD")
            if (step.payloadId != payload.id || !PayloadBuilder.matches(t, step, payload)) return deny("ILLEGAL_PAYLOAD_OR_PROVENANCE")
            val input = o.nodes.singleOrNull { it.role == NodeRole.INPUT } ?: return ask("AMBIGUOUS_INPUT")
            if (p.action == Action.WRITE) {
                if (step.status != TurnStatus.PREPARED) return deny("INVALID_WRITE_STATE")
                if (input.text.isNotEmpty() && input.text != payload.text) return ask("FOREIGN_DRAFT")
            } else {
                if (step.status != TurnStatus.STAGED) return deny("INVALID_SUBMIT_STATE")
                if (input.text != payload.text) return ask("INPUT_NOT_EXACT")
            }
        } else if (p.payloadId != null) return deny("UNEXPECTED_PAYLOAD")
        if (p.action == Action.CAPTURE && step.status !in setOf(TurnStatus.SENT_CONFIRMED, TurnStatus.WAITING)) return deny("UNCONFIRMED_SEND")
        return Verdict(GuardrailDecision.ALLOW, "AUTHORIZED")
    }
    fun canInspect(selected: String, actual: String, deadline: Long, now: Long, unlocked: Boolean): Boolean = selected == actual && now <= deadline && unlocked

    fun authorizeRecordedEffect(p: ProposedAction, before: TaskState, current: TaskState, intentId: String,
        o: UiObservation, evidence: ThreadEvidence?, now: Long): Verdict {
        // Only the exact intent just committed by this executor is eligible. Old/unresolved intents stay blocked.
        val expected = runCatching { TurnLifecycle.beginEffect(before, p.action, intentId, now).copy(revision = before.revision + 1) }.getOrNull()
        if (expected == null || current != expected) return deny("RECORDED_INTENT_STATE_CHANGED")
        return authorize(p, before, o, evidence, now)
    }
}
