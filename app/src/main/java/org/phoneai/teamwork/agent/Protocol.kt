package org.phoneai.teamwork.agent

import org.phoneai.teamwork.state.*

enum class Action { LAUNCH, HISTORY, NEW_THREAD, SELECT_THREAD, SCROLL, BACK, FOCUS_INPUT, FOCUS_SEND, WRITE, SUBMIT, CAPTURE, WAIT, PAUSE }
enum class NodeRole { UNKNOWN, INPUT, SEND, HISTORY, NEW_THREAD, THREAD_CANDIDATE, USER_MESSAGE, ASSISTANT_MESSAGE, SCROLL_REGION }
data class UiTarget(val observationId: String, val taskId: String, val packageName: String, val windowId: Int, val nodePath: String, val epoch: Long, val observedAt: Long)
data class UiNode(val path: String, val resourceId: String?, val text: String, val description: String, val editable: Boolean, val clickable: Boolean, val bounds: String, val role: NodeRole = NodeRole.UNKNOWN, val enabled: Boolean = true, val visible: Boolean = true, val className: String = "", val supportedActions: Set<Int> = emptySet())
data class UiObservation(val id: String, val taskId: String, val packageName: String, val windowId: Int, val epoch: Long, val at: Long, val nodes: List<UiNode>, val appVersion: String, val truncated: Boolean, val locked: Boolean, val obstructed: Boolean, val profileVerified: Boolean = false) {
    fun target(node: UiNode) = UiTarget(id, taskId, packageName, windowId, node.path, epoch, at)
}
data class ProposedAction(val action: Action, val participantId: String, val target: UiTarget? = null, val payloadId: String? = null)
data class UiProposal(val action: ProposedAction?, val reason: String)
data class ControlContext(val taskId: String, val turnId: String, val participantId: String, val payloadId: String?, val allowed: Set<Action>, val remainingActions: Int)
// Evidence is derived by the trusted version-specific verifier, never parsed from model output.
data class ThreadEvidence(val taskId: String, val participantId: String, val observationId: String, val firstUserMatched: Boolean, val latestUserMatched: Boolean, val verifiedEmptyThread: Boolean = false)
