package org.phoneai.teamwork.model

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.launch
import org.phoneai.teamwork.TeamworkApp
import java.io.File
import org.phoneai.teamwork.phone.InspectionService
import org.phoneai.teamwork.targets.Targets

// Debug APK only; protected by system DUMP permission. Inspection accepts only configured participant IDs.
// No arbitrary package, text, path or action execution is accepted.
class ExperimentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as TeamworkApp
        if (intent.action == "org.phoneai.teamwork.STAGE_PUBLIC_PROBE") {
            app.scope.launch { org.phoneai.teamwork.agent.SingleExchangeExperiment.stage(app) }
            return
        }
        if (intent.action == "org.phoneai.teamwork.ARM_INSPECTION") {
            val target = Targets.all.singleOrNull { it.id == intent.getStringExtra("participant") } ?: return
            val participant = target.installed(context) ?: return
            InspectionService.instance?.arm(participant, intent.getBooleanExtra("with_image", false))
            return
        }
        if (intent.action == "org.phoneai.teamwork.CANCEL_EXPERIMENT") {
            app.scope.launch { app.model.cancel() }; return
        }
        if (intent.action != "org.phoneai.teamwork.RUN_EXPERIMENT") return
        app.scope.launch {
            try { ModelExperiment.run(app, intent.getBooleanExtra("with_image", false), intent.getStringExtra("case_id") ?: "input", intent.getStringExtra("run_id") ?: "manual") }
            catch (e: Exception) {
                val code = e.message?.takeIf { it.matches(Regex("[A-Z_]+")) } ?: "EXPERIMENT_ERROR"
                if (code != "EXPERIMENT_BUSY") File(app.filesDir, "model-experiment-last.txt").writeText("state=ERROR\nerror=$code")
            }
        }
    }
}
