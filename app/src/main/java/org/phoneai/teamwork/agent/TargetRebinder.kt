package org.phoneai.teamwork.agent

/** Re-observe after slow inference. A changed screen needs a new proposal, never a timestamp patch. */
object TargetRebinder {
    fun rebind(proposal: ProposedAction, before: UiObservation, fresh: UiObservation, now: Long): UiProposal {
        fun stop(reason: String) = UiProposal(null, reason)
        val target = proposal.target ?: return stop("REBIND_TARGET_REQUIRED")
        if (before.truncated || before.locked || before.obstructed || fresh.truncated || fresh.locked || fresh.obstructed ||
            now - fresh.at !in 0..5000 || fresh.at < before.at || fresh.epoch < before.epoch || fresh.id == before.id)
            return stop("REBIND_UNSAFE_OBSERVATION")
        if (before.taskId != fresh.taskId || before.packageName != fresh.packageName ||
            before.appVersion != fresh.appVersion || before.windowId != fresh.windowId)
            return stop("REBIND_WINDOW_CHANGED")
        val oldNode = before.nodes.singleOrNull { it.path == target.nodePath }
            ?: return stop("REBIND_UNKNOWN_TARGET")
        if (target != before.target(oldNode)) return stop("REBIND_FORGED_TARGET")
        // Roles are independently assigned by the trusted version-specific verifier, not the model.
        // Everything actually observed must stay identical, including surrounding messages and controls.
        if (before.nodes.map { it.copy(role = NodeRole.UNKNOWN) } != fresh.nodes.map { it.copy(role = NodeRole.UNKNOWN) })
            return stop("REBIND_SCREEN_CHANGED")
        val newNode = fresh.nodes.singleOrNull { it.path == target.nodePath }
            ?: return stop("REBIND_UNKNOWN_TARGET")
        if (!fresh.profileVerified || !newNode.enabled || !newNode.visible)
            return stop("REBIND_PROFILE_OR_NODE_UNVERIFIED")
        // This is still a proposal. Guardrail must authorize it immediately before the tool acts.
        return UiProposal(proposal.copy(target = fresh.target(newNode)), "REBOUND_NOT_AUTHORIZED")
    }
}
