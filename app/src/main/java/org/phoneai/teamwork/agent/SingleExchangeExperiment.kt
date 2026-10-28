package org.phoneai.teamwork.agent

import kotlinx.coroutines.*
import org.phoneai.teamwork.TeamworkApp
import org.phoneai.teamwork.guardrail.Guardrail
import org.phoneai.teamwork.phone.GuardedPhoneTools
import org.phoneai.teamwork.state.*
import org.phoneai.teamwork.targets.*
import org.phoneai.teamwork.workflow.Recipe
import java.io.File

/** Developer/user-invoked, fixed public task. No arbitrary outgoing text accepted by debug entry points. */
object SingleExchangeExperiment {
    suspend fun stage(app: TeamworkApp) {
        if (!app.operationBusy.compareAndSet(false, true)) return
        var taskId: String? = null
        var diagnostics = ""
        val report = File(app.filesDir, "single-exchange-last.txt")
        try {
            app.recovered.await()
            val participant = requireNotNull(Targets.all.single { it.id == "deepseek" }.installed(app))
            require(participant.version == "2.5.2 (273)") { "UNVERIFIED_VERSION" }
            val recipe = Recipe.discussion(DeepSeekProbeProfile.GOAL, listOf(participant), participant.id)
            val task = recipe.copy(steps = recipe.steps.take(1), maxActionsPerStep = 4)
            taskId = task.id
            app.store.create(task); app.store.prepare(task.id)
            var active = app.store.update(task.id, kind = "STARTED", reason = "PUBLIC_SINGLE_EXCHANGE_PROBE") {
                it.copy(status = TaskStatus.RUNNING, deadline = System.currentTimeMillis() + 180000)
            }
            report.writeText("state=RUNNING\ntaskId=${task.id}\nphase=OBSERVE_AND_LOCATE")
            val phone = GuardedPhoneTools(app.store, Guardrail())
            var observation: UiObservation? = null
            repeat(20) {
                if (observation == null) { observation = phone.observe(active); if (observation == null) delay(500) }
            }
            val o = requireNotNull(observation) { "OBSERVATION_UNAVAILABLE" }
            diagnostics = DeepSeekProbeProfile.diagnostics(active, o)
            require(DeepSeekProbeProfile.empty(active, o)) { "EMPTY_PAGE_ANCHORS_REQUIRED" }
            require(o.profileVerified) { "WRITABLE_INPUT_REQUIRED" }
            require(o.nodes.single { it.role == NodeRole.INPUT }.text.isEmpty()) { "VERIFIED_EMPTY_INPUT_REQUIRED" }
            active = app.store.update(task.id, active.revision, "THREAD_BOUND", "VERIFIED_EMPTY_WINDOW") {
                it.copy(bindings = mapOf(participant.id to ThreadBinding(it.id, participant.id, "", "", participant.version, initialWindowId = o.windowId)))
            }
            AgentLoop(app.store, app.model, phone).stageSingleExchange(active.id)
            val result = app.store.get(task.id)
            report.writeText("state=${result.status}\ntaskId=${task.id}\nturn=${result.current?.status}\nreason=${result.reason}\npendingIntent=${result.pendingIntent != null}\n${app.model.lastMetrics}\nsubmitted=false")
        } catch (e: Exception) {
            val reason = e.message?.takeIf { it.matches(Regex("[A-Z_]+")) } ?: "SINGLE_EXCHANGE_ERROR"
            taskId?.let { app.store.pause(it, reason, true) }
            report.writeText("state=ERROR\ntaskId=$taskId\nreason=$reason\n$diagnostics")
        } finally {
            try { withContext(NonCancellable) { app.model.close() } }
            finally { app.operationBusy.set(false) }
        }
    }
}
