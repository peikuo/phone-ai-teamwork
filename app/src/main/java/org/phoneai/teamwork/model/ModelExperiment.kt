package org.phoneai.teamwork.model

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.phoneai.teamwork.TeamworkApp
import org.phoneai.teamwork.agent.*
import org.phoneai.teamwork.phone.InspectionService
import org.phoneai.teamwork.targets.Targets
import java.io.File

// The UI and debug receiver use this exact read-only path. No PhoneTools reference exists here.
object ModelExperiment {
    suspend fun run(app: TeamworkApp, withImage: Boolean, caseId: String = "input", runId: String = "manual"): String {
        require(runId.matches(Regex("[A-Za-z0-9_-]{1,64}"))) { "INVALID_RUN_ID" }
        require(caseId in setOf("input", "send")) { "UNKNOWN_CASE" }
        check(app.operationBusy.compareAndSet(false, true)) { "EXPERIMENT_BUSY" }
        var bitmap: Bitmap? = null
        try {
            val sample = withContext(Dispatchers.Main) {
                val s = checkNotNull(InspectionService.instance?.lastSample) { "OBSERVATION_REQUIRED" }
                if (withImage) bitmap = checkNotNull(s.image) { "SCREENSHOT_REQUIRED" }.copy(Bitmap.Config.ARGB_8888, false)
                s
            }
            val participant = Targets.all.single { it.configuredPackage == sample.observation.packageName }
            val context = ControlContext(sample.observation.taskId, "inspection-only", participant.id, null,
                if (caseId == "send") setOf(Action.FOCUS_SEND, Action.PAUSE) else setOf(Action.FOCUS_INPUT, Action.PAUSE), 1)
            File(app.filesDir, "model-experiment-last.txt").writeText("state=RUNNING\nrun_id=$runId\ncase=$caseId\npackage=${sample.observation.packageName}\nimage=$withImage")
            val p = app.model.experiment(context, sample.observation, bitmap)
            val selected = sample.observation.nodes.singleOrNull { it.path == p.action?.target?.nodePath }
            val report = "state=FINISHED\nrun_id=$runId\ncase=$caseId\n${app.model.lastMetrics}\nproposal=${p.action?.action ?: "NEEDS_USER"}\nnode=${p.action?.target?.nodePath ?: "none"}\nselected_editable=${selected?.editable}\nsend_label_match=${selected?.description == "发送" || selected?.text == "发送"}\nselected_bounds=${selected?.bounds}\neditable_nodes=${sample.observation.nodes.count { it.editable }}\n未执行任何动作；这不是 Guardrail 放行。"
            File(app.filesDir, "model-experiment-last.txt").writeText(report)
            val history = File(app.filesDir, "model-experiments").apply { mkdirs() }
            File(history, "${System.currentTimeMillis()}.txt").writeText(report)
            return report
        } finally { bitmap?.recycle(); app.operationBusy.set(false) }
    }
}
