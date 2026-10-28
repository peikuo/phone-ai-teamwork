package org.phoneai.teamwork.state

/** Minimal scheduling boundary. Production uses Room; offline tests use in-memory snapshots. */
interface AgentState {
    fun get(id: String): TaskState
    /** Atomically pause only this running revision. A late result must not overwrite user control. */
    fun pauseIfCurrent(id: String, revision: Long, reason: String, needsUser: Boolean = false): Boolean
}
