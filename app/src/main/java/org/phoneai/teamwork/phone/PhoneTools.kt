package org.phoneai.teamwork.phone

import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.phoneai.teamwork.agent.*
import org.phoneai.teamwork.guardrail.*
import org.phoneai.teamwork.state.*
import org.phoneai.teamwork.targets.DeepSeekProbeProfile

interface PhoneTools {
    suspend fun observe(task: TaskState): UiObservation?
    suspend fun execute(taskId: String, proposal: ProposedAction): ToolResult
}
data class ToolResult(val allowed: Boolean, val reason: String)

/** Only this class performs Android effects. The initial adapter permits one fixed public probe. */
class GuardedPhoneTools(private val store: TaskStore, private val guardrail: Guardrail) : PhoneTools {
    private var lastObservation: UiObservation? = null
    override suspend fun observe(task: TaskState): UiObservation? = withContext(Dispatchers.Main) {
        InspectionService.instance?.observeTask(task)?.let { DeepSeekProbeProfile.classify(task, it) }.also { lastObservation = it }
    }
    private fun pause(taskId: String, reason: String): ToolResult {
        store.pause(taskId, reason, true)
        return ToolResult(false, reason)
    }
    override suspend fun execute(taskId: String, proposal: ProposedAction): ToolResult {
        val before = store.get(taskId)
        if (proposal.action == Action.PAUSE) { store.pause(taskId, "AGENT_PAUSE"); return ToolResult(true, "PAUSED") }
        if (proposal.action !in setOf(Action.WRITE, Action.SUBMIT)) {
            val feature = when (proposal.action) {
                Action.SUBMIT, Action.CAPTURE -> "SINGLE_EXCHANGE"
                Action.LAUNCH, Action.HISTORY, Action.NEW_THREAD, Action.SELECT_THREAD, Action.BACK -> "AUTO_ENTER"
                Action.SCROLL -> "FORWARD_LONG"
                else -> null
            }
            return pause(taskId, feature?.let(org.phoneai.teamwork.capability.CapabilityCatalog::blockedReason)
                ?: "DEVICE_ACTION_NOT_VALIDATED")
        }
        val previous = lastObservation ?: return pause(taskId, "OBSERVATION_REQUIRED")
        val fresh = observe(before) ?: return pause(taskId, "OBSERVATION_UNAVAILABLE")
        val rebound = TargetRebinder.rebind(proposal, previous, fresh, System.currentTimeMillis())
        val action = rebound.action ?: return pause(taskId, rebound.reason)
        val verdict = guardrail.authorize(action, before, fresh, DeepSeekProbeProfile.evidence(before, fresh), System.currentTimeMillis())
        if (verdict.decision != GuardrailDecision.ALLOW) return pause(taskId, verdict.reason)
        val intent = id()
        val recorded = store.beginEffect(taskId, before.revision, action.action, intent, System.currentTimeMillis())
        val result = withContext(Dispatchers.Main) {
            store.atRevision(taskId, recorded.revision) { current ->
                val service = InspectionService.instance ?: return@atRevision ToolResult(false, "SERVICE_UNAVAILABLE")
                val raw = service.observeTask(current) ?: return@atRevision ToolResult(false, "WINDOW_CHANGED_BEFORE_EFFECT")
                val instant = DeepSeekProbeProfile.classify(current, raw)
                val latest = TargetRebinder.rebind(action, fresh, instant, System.currentTimeMillis()).action
                    ?: return@atRevision ToolResult(false, "SCREEN_CHANGED_BEFORE_EFFECT")
                val permitted = guardrail.authorizeRecordedEffect(latest, before, current, intent, instant,
                    DeepSeekProbeProfile.evidence(current, instant), System.currentTimeMillis())
                if (permitted.decision != GuardrailDecision.ALLOW) return@atRevision ToolResult(false, permitted.reason)
                val node = service.nodeAt(instant, requireNotNull(latest.target).nodePath)
                    ?: return@atRevision ToolResult(false, "NODE_GONE")
                try {
                    val expected = instant.nodes.single { it.path == latest.target.nodePath }
                    val bounds = android.graphics.Rect().also(node::getBoundsInScreen).flattenToString()
                    if (!node.isEnabled || !node.isVisibleToUser || InspectionService.inputText(node) != expected.text ||
                        node.className?.toString().orEmpty() != expected.className || node.viewIdResourceName != expected.resourceId ||
                        node.contentDescription?.toString().orEmpty() != expected.description || bounds != expected.bounds ||
                        (action.action == Action.WRITE && (!node.isEditable || expected.role != NodeRole.INPUT ||
                            node.actionList.none { it.id == AccessibilityNodeInfo.ACTION_SET_TEXT })) ||
                        (action.action == Action.SUBMIT && (!node.isClickable || expected.role != NodeRole.SEND ||
                            node.actionList.none { it.id == AccessibilityNodeInfo.ACTION_CLICK })))
                        return@atRevision ToolResult(false, "LIVE_NODE_MISMATCH")
                    if (action.action == Action.SUBMIT) {
                        val ok = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        ToolResult(ok, if (ok) "SUBMIT_REQUESTED_AWAITING_EVIDENCE" else "SUBMIT_RESULT_UNCERTAIN")
                    } else {
                        val text = current.payloads.getValue(requireNotNull(latest.payloadId)).text
                        val ok = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,
                            Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) })
                        ToolResult(ok, if (ok) "WRITE_REQUESTED" else "WRITE_NOT_ACKNOWLEDGED")
                    }
                } finally { node.recycle() }
            }
        }
        if (!result.allowed) return pause(taskId, result.reason)
        if (proposal.action == Action.SUBMIT) return result
        repeat(10) {
            delay(200)
            val current = store.get(taskId)
            if (current.status != TaskStatus.RUNNING) return ToolResult(false, "TASK_STOPPED")
            val readback = observe(current)
            val input = readback?.nodes?.singleOrNull { it.role == NodeRole.INPUT }
            if (readback != null && DeepSeekProbeProfile.evidence(current, readback)?.verifiedEmptyThread == true &&
                input?.text == current.payloads.getValue(requireNotNull(current.current?.payloadId)).text) {
                store.confirmWrite(taskId, current.revision, intent, input!!.text)
                return ToolResult(true, "WRITE_ECHO_VERIFIED")
            }
        }
        return pause(taskId, "WRITE_RESULT_UNCERTAIN")
    }
}
