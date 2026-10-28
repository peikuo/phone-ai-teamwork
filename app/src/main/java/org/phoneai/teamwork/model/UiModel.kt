package org.phoneai.teamwork.model

import org.phoneai.teamwork.agent.*

data class UiModelInfo(val backend: String, val local: Boolean, val vision: Boolean)
interface UiModel {
    val info: UiModelInfo
    suspend fun propose(context: ControlContext, observation: UiObservation): UiProposal
    suspend fun cancel()
    suspend fun close()
}
