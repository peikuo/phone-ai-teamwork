package org.phoneai.teamwork.targets

import android.content.Context
import org.phoneai.teamwork.BuildConfig
import org.phoneai.teamwork.state.Participant
import org.phoneai.teamwork.agent.*

data class TargetApp(val id: String, val label: String, val configuredPackage: String) {
    fun installed(context: Context): Participant? = runCatching {
        if (configuredPackage.startsWith("org.phoneai.unbound.")) return null
        val info = context.packageManager.getPackageInfo(configuredPackage, 0)
        Participant(id, label, configuredPackage, "${info.versionName} (${info.longVersionCode})")
    }.getOrNull()
}
object Targets {
    val all = listOf(TargetApp("kimi", "Kimi", BuildConfig.KIMI_PACKAGE), TargetApp("doubao", "豆包", BuildConfig.DOUBAO_PACKAGE), TargetApp("deepseek", "DeepSeek", BuildConfig.DEEPSEEK_PACKAGE))
}
// No guessed selectors, package names, roles or completion signals. Device evidence must precede promotion.
object AppHints {
    fun verified(packageName: String, version: String): Boolean = false
    fun threadEvidence(o: UiObservation): ThreadEvidence? = null
}
