package org.phoneai.teamwork.model

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.launch
import org.phoneai.teamwork.TeamworkApp
import java.io.File
import org.phoneai.teamwork.phone.InspectionService
import org.phoneai.teamwork.targets.Targets
import org.phoneai.teamwork.targets.PublicEmptyProfiles
import org.phoneai.teamwork.targets.DeepSeekProbeProfile
import org.phoneai.teamwork.workflow.Recipe
import org.phoneai.teamwork.agent.NodeRole

// Debug APK only; protected by system DUMP permission. Inspection accepts only configured participant IDs.
// No arbitrary package, text, path or action execution is accepted.
class ExperimentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as TeamworkApp
        if (intent.action == "org.phoneai.teamwork.DIAGNOSTIC_STATUS") {
            val taskId = File(app.filesDir, "single-exchange-last.txt").takeIf(File::exists)
                ?.readLines()?.singleOrNull { it.startsWith("taskId=") }?.substringAfter('=')
            val statusFile = File(app.filesDir, "diagnostic-status-last.txt")
            val basic =
                "busy=${app.operationBusy.get()}\nrecovered=${app.recovered.isCompleted}" +
                    "\ntaskId=$taskId"
            statusFile.writeText(basic)
            if (app.recovered.isCompleted && taskId != null) app.scope.launch {
                val task = runCatching { app.store.get(taskId) }.getOrNull()
                statusFile.writeText(basic + "\nstatus=${task?.status}\nturn=${task?.current?.status}" +
                    "\npendingIntent=${task?.pendingIntent != null}")
            }
            return
        }
        if (intent.action == "org.phoneai.teamwork.PROFILE_PUBLIC_EMPTY") {
            val sample = InspectionService.instance?.lastSample?.observation ?: return
            val target = Targets.all.singleOrNull { it.configuredPackage == sample.packageName &&
                it.id in setOf("kimi", "doubao") } ?: return
            val participant = target.installed(context) ?: return
            val task = Recipe.discussion(DeepSeekProbeProfile.GOAL, listOf(participant), participant.id)
                .let { it.copy(id = sample.taskId, steps = it.steps.take(1)) }
            val classified = when (target.id) {
                "kimi" -> PublicEmptyProfiles.kimi(task, sample, System.currentTimeMillis())
                else -> PublicEmptyProfiles.doubao(task, sample, System.currentTimeMillis())
            }
            File(app.filesDir, "public-empty-profile-last.txt").writeText(
                "state=FINISHED\napp=${target.id}\nversion=${participant.version}" +
                    "\nnodeCount=${sample.nodes.size}\ntruncated=${sample.truncated}" +
                    "\nageMs=${System.currentTimeMillis() - sample.at}\nlocked=${sample.locked}\nobstructed=${sample.obstructed}" +
                    "\nprofileVerified=${classified.profileVerified}" +
                    "\ninputCount=${classified.nodes.count { it.role == NodeRole.INPUT }}" +
                    "\nexecuted=false\n${PublicEmptyProfiles.diagnostics(sample, target.id)}")
            return
        }
        if (intent.action == "org.phoneai.teamwork.STAGE_PUBLIC_PROBE") {
            app.scope.launch { org.phoneai.teamwork.agent.SingleExchangeExperiment.stage(app) }
            return
        }
        if (intent.action == "org.phoneai.teamwork.STAGE_KIMI_PUBLIC_PROBE") {
            app.scope.launch { org.phoneai.teamwork.agent.SingleExchangeExperiment.stage(app, "kimi") }
            return
        }
        if (intent.action == "org.phoneai.teamwork.STAGE_DOUBAO_PUBLIC_PROBE") {
            app.scope.launch { org.phoneai.teamwork.agent.SingleExchangeExperiment.stage(app, "doubao") }
            return
        }
        if (intent.action == "org.phoneai.teamwork.VERIFY_STAGED_PUBLIC_PROBE") {
            app.scope.launch { org.phoneai.teamwork.agent.SingleExchangeExperiment.verifyStagedInput(app) }
            return
        }
        if (intent.action == "org.phoneai.teamwork.SUBMIT_PUBLIC_PROBE") {
            app.scope.launch { org.phoneai.teamwork.agent.SingleExchangeExperiment.submit(app) }
            return
        }
        if (intent.action == "org.phoneai.teamwork.SUBMIT_DOUBAO_PUBLIC_PROBE") {
            app.scope.launch { org.phoneai.teamwork.agent.SingleExchangeExperiment.submitDoubao(app) }
            return
        }
        if (intent.action == "org.phoneai.teamwork.VERIFY_DOUBAO_PUBLIC_PROBE") {
            app.scope.launch { org.phoneai.teamwork.agent.SingleExchangeExperiment.verifyDoubaoReply(app) }
            return
        }
        if (intent.action == "org.phoneai.teamwork.VERIFY_PUBLIC_PROBE") {
            app.scope.launch { org.phoneai.teamwork.agent.SingleExchangeExperiment.verifyReply(app) }
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
