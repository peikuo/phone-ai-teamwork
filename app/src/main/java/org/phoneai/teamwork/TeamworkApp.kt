package org.phoneai.teamwork

import android.app.Application
import kotlinx.coroutines.*
import org.phoneai.teamwork.state.TaskStore
import org.phoneai.teamwork.model.LocalUiModel

class TeamworkApp : Application() {
    val operationBusy = java.util.concurrent.atomic.AtomicBoolean(false)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val store by lazy { TaskStore(this) }
    val model by lazy { LocalUiModel(this) }
    lateinit var recovered: Deferred<Unit>; private set
    override fun onCreate() { super.onCreate(); recovered = scope.async {
        store.recover()
        val report = java.io.File(filesDir, "single-exchange-last.txt")
        if (report.exists() && report.readLines().firstOrNull() == "state=RUNNING") {
            val taskIdLine = report.readLines().singleOrNull { it.startsWith("taskId=") }.orEmpty()
            report.writeText("state=INTERRUPTED\n$taskIdLine\nreason=PROCESS_RESTART_REOBSERVE_NO_RETRY")
        }
    } }
}
