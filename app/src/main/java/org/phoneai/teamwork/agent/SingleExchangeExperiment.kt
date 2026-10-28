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
    /** Read-only exact-draft check for the one staged public Kimi task. */
    suspend fun inspectKimiDraft(app: TeamworkApp, runId: String) {
        require(runId.matches(Regex("[A-Za-z0-9_-]{1,64}"))) { "INVALID_RUN_ID" }
        if (!app.operationBusy.compareAndSet(false, true)) return
        val report = File(app.filesDir, "kimi-draft-check-last.txt")
        try {
            app.recovered.await()
            val task = app.store.all().single { it.goal == DeepSeekProbeProfile.GOAL &&
                it.participants.singleOrNull()?.id == "kimi" && it.participants.single().packageName == "com.moonshot.kimichat" &&
                it.participants.single().version == "3.1.3 (378)" && it.steps.size == 1 &&
                it.status == TaskStatus.PAUSED && it.current?.status == TurnStatus.STAGED && it.pendingIntent == null }
            val payload = requireNotNull(task.current?.payloadId?.let(task.payloads::get)) { "PAYLOAD_MISSING" }
            require(org.phoneai.teamwork.workflow.PayloadBuilder.matches(task, task.current!!, payload)) { "PAYLOAD_MISMATCH" }
            val observing = task.copy(status = TaskStatus.RUNNING, deadline = System.currentTimeMillis() + 30000)
            val phone = GuardedPhoneTools(app.store, Guardrail())
            val first = requireNotNull(phone.observe(observing)) { "OBSERVATION_UNAVAILABLE" }
            val input = first.nodes.singleOrNull { it.role == NodeRole.INPUT }
            require(first.profileVerified && input?.text == payload.text &&
                PublicEmptyProfiles.evidence(observing, first)?.verifiedEmptyThread == true) { "KIMI_DRAFT_NOT_EXACT" }
            delay(750)
            val unchanged = app.store.get(task.id)
            require(unchanged == task) { "STATE_CHANGED" }
            val second = requireNotNull(phone.observe(observing)) { "OBSERVATION_UNAVAILABLE" }
            val secondInput = second.nodes.singleOrNull { it.role == NodeRole.INPUT }
            require(second.profileVerified && secondInput?.text == payload.text &&
                PublicEmptyProfiles.evidence(observing, second)?.verifiedEmptyThread == true &&
                first.id != second.id && first.windowId == second.windowId &&
                input.path == secondInput.path && input.bounds == secondInput.bounds) { "KIMI_DRAFT_NOT_STABLE" }
            require(app.store.get(task.id) == task) { "STATE_CHANGED" }
            report.writeText("state=VERIFIED\nrun_id=$runId\nparticipant=kimi\ninputLength=${payload.text.length}" +
                "\ninputHash=${payload.hash}\nsubmitted=false\nstoreChanged=false")
        } catch (e: Exception) {
            val reason = e.message?.takeIf { it.matches(Regex("[A-Z0-9_]+")) } ?: "KIMI_DRAFT_CHECK_ERROR"
            report.writeText("state=ERROR\nrun_id=$runId\nreason=$reason\nsubmitted=false\nstoreChanged=false")
        } finally { app.operationBusy.set(false) }
    }
    /** Read-only complete-reply capture after the single fixed public Doubao send. */
    suspend fun verifyDoubaoReply(app: TeamworkApp) {
        if (!app.operationBusy.compareAndSet(false, true)) return
        val report = File(app.filesDir, "single-exchange-last.txt")
        var taskId: String? = null
        var diagnostics = ""
        try {
            app.recovered.await()
            taskId = report.readLines().single { it.startsWith("taskId=") }.substringAfter('=')
            val task = app.store.get(taskId)
            val participant = task.participants.singleOrNull()
            require(task.goal == DeepSeekProbeProfile.GOAL && task.steps.size == 1 &&
                task.status == TaskStatus.NEEDS_USER && task.current?.status == TurnStatus.SUBMITTING &&
                !task.pendingIntent.isNullOrBlank() && participant?.id == "doubao" &&
                participant.packageName == "com.larus.nova" && participant.version == "14.9.0 (14090001)") {
                "UNCERTAIN_SEND_REQUIRED"
            }
            val payload = task.current?.payloadId?.let(task.payloads::get)
            require(payload != null && org.phoneai.teamwork.workflow.PayloadBuilder.matches(task, task.current!!, payload)) {
                "PAYLOAD_MISMATCH"
            }
            val active = app.store.update(task.id, task.revision, "VERIFY_STARTED", "OBSERVE_DOUBAO_REPLY") {
                it.copy(status = TaskStatus.RUNNING, deadline = System.currentTimeMillis() + 30000)
            }
            report.writeText("state=RUNNING\ntaskId=$taskId\nphase=VERIFY_DOUBAO_REPLY")
            val phone = GuardedPhoneTools(app.store, Guardrail())
            val first = requireNotNull(phone.observe(active)) { "OBSERVATION_UNAVAILABLE" }
            diagnostics = DoubaoPublicReplyProfile.diagnostics(active, first)
            val firstProof = requireNotNull(DoubaoPublicReplyProfile.verify(active, first, System.currentTimeMillis())) {
                "REPLY_PROFILE_UNVERIFIED"
            }
            delay(750)
            val unchanged = app.store.get(task.id)
            require(unchanged.status == TaskStatus.RUNNING && unchanged.revision == active.revision &&
                unchanged.pendingIntent == active.pendingIntent) { "STATE_CHANGED" }
            val second = requireNotNull(phone.observe(unchanged)) { "OBSERVATION_UNAVAILABLE" }
            diagnostics = DoubaoPublicReplyProfile.diagnostics(unchanged, second)
            val secondProof = requireNotNull(DoubaoPublicReplyProfile.verify(unchanged, second, System.currentTimeMillis())) {
                "REPLY_PROFILE_UNVERIFIED"
            }
            require(firstProof == secondProof && first.id != second.id) { "REPLY_CHANGED_DURING_VERIFY" }
            val message = Message(id(), task.id, requireNotNull(unchanged.current).id, "doubao", secondProof.answer,
                Completeness.COMPLETE, "doubao-public-reply-${second.id}", payload.sourceIds)
            val completed = app.store.completeVerifiedExchange(task.id, unchanged.revision,
                requireNotNull(unchanged.pendingIntent), payload.text, id(), message)
            report.writeText("state=${completed.status}\ntaskId=$taskId\nparticipant=doubao" +
                "\nturn=${completed.steps.single().status}\nreason=${completed.reason}" +
                "\npendingIntent=${completed.pendingIntent != null}\nanswerLength=${message.text.length}" +
                "\nanswerHash=${sha256(message.text)}\nsubmitted=verified\nsource=doubao")
        } catch (e: Exception) {
            val reason = e.message?.takeIf { it.matches(Regex("[A-Z0-9_]+")) } ?: "DOUBAO_REPLY_VERIFY_ERROR"
            taskId?.let { runCatching { app.store.pause(it, reason, true) } }
            report.writeText("state=ERROR\ntaskId=$taskId\nreason=$reason\nsubmitted=unverified\n$diagnostics")
        } finally { app.operationBusy.set(false) }
    }
    /** One fixed public Doubao send. A click acknowledgement never confirms delivery. */
    suspend fun submitDoubao(app: TeamworkApp) {
        if (!app.operationBusy.compareAndSet(false, true)) return
        val report = File(app.filesDir, "single-exchange-last.txt")
        var taskId: String? = null
        try {
            app.recovered.await()
            val previous = report.readLines()
            require(previous.firstOrNull() == "state=PAUSED" && "turn=STAGED" in previous &&
                "pendingIntent=false" in previous && "submitted=false" in previous) { "STAGED_PROBE_REQUIRED" }
            taskId = previous.single { it.startsWith("taskId=") }.substringAfter('=')
            val task = app.store.get(taskId)
            val participant = task.participants.singleOrNull()
            require(task.goal == DeepSeekProbeProfile.GOAL && task.steps.size == 1 &&
                task.status == TaskStatus.PAUSED && task.current?.status == TurnStatus.STAGED &&
                task.pendingIntent == null && participant?.id == "doubao" &&
                participant.packageName == "com.larus.nova" && participant.version == "14.9.0 (14090001)") {
                "STAGED_PROBE_REQUIRED"
            }
            val payload = task.current?.payloadId?.let(task.payloads::get)
            require(payload != null && org.phoneai.teamwork.workflow.PayloadBuilder.matches(task, task.current!!, payload)) {
                "PAYLOAD_MISMATCH"
            }
            val binding = task.bindings["doubao"]
            require(binding?.initialWindowId != null && binding.firstUserText.isEmpty() &&
                binding.latestUserText.isEmpty()) { "THREAD_NOT_BOUND" }
            val active = app.store.update(task.id, task.revision, "STARTED", "PUBLIC_DOUBAO_SEND") {
                it.copy(status = TaskStatus.RUNNING, deadline = System.currentTimeMillis() + 180000)
            }
            report.writeText("state=RUNNING\ntaskId=$taskId\nphase=LOCATE_DOUBAO_SEND")
            val phone = GuardedPhoneTools(app.store, Guardrail())
            val observation = requireNotNull(phone.observe(active)) { "OBSERVATION_UNAVAILABLE" }
            require(observation.profileVerified &&
                PublicEmptyProfiles.evidence(active, observation)?.verifiedEmptyThread == true &&
                observation.nodes.singleOrNull { it.role == NodeRole.INPUT }?.text == payload.text &&
                observation.nodes.count { it.role == NodeRole.SEND } == 1) { "STAGED_INPUT_OR_SEND_UNVERIFIED" }
            val proposal = app.model.propose(ControlContext(active.id, active.current!!.id, "doubao", null,
                setOf(Action.FOCUS_SEND, Action.PAUSE), active.maxActionsPerStep - active.current!!.actionCount), observation)
            val current = app.store.get(taskId)
            require(current.status == TaskStatus.RUNNING && current.revision == active.revision &&
                current.pendingIntent == null) { "STATE_CHANGED" }
            val action = proposal.action
            require(action?.action == Action.FOCUS_SEND && action.participantId == "doubao" &&
                action.payloadId == null && action.target != null) { "MODEL_SEND_TARGET_REQUIRED" }
            val target = requireNotNull(PublicEmptyProfiles.doubaoSendTarget(current, observation, action.target)) {
                "SEND_LABEL_NOT_ACTIONABLE"
            }
            val result = phone.execute(taskId, ProposedAction(Action.SUBMIT, "doubao", target, payload.id))
            val after = app.store.get(taskId)
            if (after.status == TaskStatus.RUNNING) app.store.pause(taskId, "SEND_EFFECT_REQUIRES_VERIFICATION", true)
            val paused = app.store.get(taskId)
            report.writeText("state=${paused.status}\ntaskId=$taskId\nparticipant=doubao" +
                "\nturn=${paused.current?.status}\nreason=${paused.reason}" +
                "\npendingIntent=${paused.pendingIntent != null}\nclickAcknowledged=${result.allowed}" +
                "\n${app.model.lastMetrics}\nsubmitted=unverified")
        } catch (e: Exception) {
            val reason = e.message?.takeIf { it.matches(Regex("[A-Z0-9_]+")) } ?: "DOUBAO_SEND_ERROR"
            taskId?.let { runCatching { app.store.pause(it, reason, true) } }
            report.writeText("state=ERROR\ntaskId=$taskId\nreason=$reason\nsubmitted=unverified")
        } finally {
            try { withContext(NonCancellable) { app.model.close() } }
            finally { app.operationBusy.set(false) }
        }
    }
    /** Read-only recovery for a fixed public WRITE whose UI effect was not confirmed in time. */
    suspend fun verifyStagedInput(app: TeamworkApp) {
        if (!app.operationBusy.compareAndSet(false, true)) return
        val report = File(app.filesDir, "single-exchange-last.txt")
        var taskId: String? = null
        var diagnostics = ""
        try {
            app.recovered.await()
            taskId = report.readLines().single { it.startsWith("taskId=") }.substringAfter('=')
            val task = app.store.get(taskId)
            val participant = requireNotNull(task.participants.singleOrNull()) { "UNCERTAIN_WRITE_REQUIRED" }
            require(task.goal == DeepSeekProbeProfile.GOAL && task.steps.size == 1 &&
                task.status == TaskStatus.NEEDS_USER && task.current?.status == TurnStatus.PREPARED &&
                !task.pendingIntent.isNullOrBlank() && participant.id in setOf("kimi", "doubao") &&
                participant.packageName == (if (participant.id == "kimi") "com.moonshot.kimichat" else "com.larus.nova") &&
                participant.version == (if (participant.id == "kimi") "3.1.3 (378)" else "14.9.0 (14090001)")) {
                "UNCERTAIN_WRITE_REQUIRED"
            }
            val payload = task.current?.payloadId?.let(task.payloads::get)
            require(payload != null && org.phoneai.teamwork.workflow.PayloadBuilder.matches(task, task.current!!, payload)) {
                "PAYLOAD_MISMATCH"
            }
            val active = app.store.update(task.id, task.revision, "VERIFY_STARTED", "OBSERVE_STAGED_INPUT") {
                it.copy(status = TaskStatus.RUNNING, deadline = System.currentTimeMillis() + 30000)
            }
            report.writeText("state=RUNNING\ntaskId=$taskId\nphase=VERIFY_STAGED_INPUT")
            val phone = GuardedPhoneTools(app.store, Guardrail())
            val first = requireNotNull(phone.observe(active)) { "OBSERVATION_UNAVAILABLE" }
            diagnostics = PublicEmptyProfiles.diagnostics(first, participant.id)
            val firstInput = first.nodes.singleOrNull { it.role == NodeRole.INPUT }
            require(first.profileVerified && firstInput?.text == payload.text &&
                PublicEmptyProfiles.evidence(active, first)?.verifiedEmptyThread == true) { "STAGED_INPUT_NOT_EXACT" }
            delay(750)
            val unchanged = app.store.get(task.id)
            require(unchanged.status == TaskStatus.RUNNING && unchanged.revision == active.revision &&
                unchanged.pendingIntent == active.pendingIntent) { "STATE_CHANGED" }
            val second = requireNotNull(phone.observe(unchanged)) { "OBSERVATION_UNAVAILABLE" }
            diagnostics = PublicEmptyProfiles.diagnostics(second, participant.id)
            val secondInput = second.nodes.singleOrNull { it.role == NodeRole.INPUT }
            require(second.profileVerified && secondInput?.text == payload.text &&
                PublicEmptyProfiles.evidence(unchanged, second)?.verifiedEmptyThread == true &&
                second.id != first.id && second.windowId == first.windowId &&
                secondInput.path == firstInput.path && secondInput.bounds == firstInput.bounds) { "STAGED_INPUT_NOT_STABLE" }
            val staged = app.store.confirmWriteAndPause(task.id, unchanged.revision,
                requireNotNull(unchanged.pendingIntent), payload.text)
            report.writeText("state=${staged.status}\ntaskId=$taskId\nparticipant=${participant.id}" +
                "\nturn=${staged.current?.status}\nreason=${staged.reason}" +
                "\npendingIntent=${staged.pendingIntent != null}\ninputLength=${payload.text.length}" +
                "\ninputHash=${payload.hash}\nsubmitted=false")
        } catch (e: Exception) {
            val reason = e.message?.takeIf { it.matches(Regex("[A-Z0-9_]+")) } ?: "STAGED_VERIFY_ERROR"
            taskId?.let { runCatching { app.store.pause(it, reason, true) } }
            report.writeText("state=ERROR\ntaskId=$taskId\nreason=$reason\nsubmitted=false\n$diagnostics")
        } finally { app.operationBusy.set(false) }
    }
    suspend fun verifyReply(app: TeamworkApp) {
        if (!app.operationBusy.compareAndSet(false, true)) return
        val report = File(app.filesDir, "single-exchange-last.txt")
        var taskId: String? = null
        var diagnostics = ""
        try {
            app.recovered.await()
            val previous = report.readLines()
            // The report may be ERROR or INTERRUPTED after a failed read-only verification.
            // Persisted SUBMITTING + pending intent is the authority; this path never clicks.
            taskId = previous.single { it.startsWith("taskId=") }.substringAfter('=')
            val task = app.store.get(taskId)
            require(task.goal == DeepSeekProbeProfile.GOAL && task.steps.size == 1 &&
                task.status == TaskStatus.NEEDS_USER && task.current?.status == TurnStatus.SUBMITTING &&
                !task.pendingIntent.isNullOrBlank() && task.participants.singleOrNull()?.id == "deepseek" &&
                task.participants.single().packageName == "com.deepseek.chat" &&
                task.participants.single().version == "2.5.2 (273)") { "UNCERTAIN_SEND_REQUIRED" }
            val payload = task.current?.payloadId?.let(task.payloads::get)
            require(payload != null && org.phoneai.teamwork.workflow.PayloadBuilder.matches(task, task.current!!, payload)) {
                "PAYLOAD_MISMATCH"
            }
            val active = app.store.update(task.id, task.revision, "VERIFY_STARTED", "OBSERVE_PUBLIC_REPLY") {
                it.copy(status = TaskStatus.RUNNING, deadline = System.currentTimeMillis() + 30000)
            }
            report.writeText("state=RUNNING\ntaskId=$taskId\nphase=VERIFY_SEND_AND_REPLY")
            val phone = GuardedPhoneTools(app.store, Guardrail())
            val first = requireNotNull(phone.observe(active)) { "OBSERVATION_UNAVAILABLE" }
            diagnostics = DeepSeekPublicReplyProfile.diagnostics(active, first)
            val firstProof = requireNotNull(DeepSeekPublicReplyProfile.verify(active, first, System.currentTimeMillis())) {
                "REPLY_PROFILE_UNVERIFIED"
            }
            delay(750)
            val unchanged = app.store.get(task.id)
            require(unchanged.status == TaskStatus.RUNNING && unchanged.revision == active.revision &&
                unchanged.pendingIntent == active.pendingIntent) { "STATE_CHANGED" }
            val second = requireNotNull(phone.observe(unchanged)) { "OBSERVATION_UNAVAILABLE" }
            diagnostics = DeepSeekPublicReplyProfile.diagnostics(unchanged, second)
            val secondProof = requireNotNull(DeepSeekPublicReplyProfile.verify(unchanged, second, System.currentTimeMillis())) {
                "REPLY_PROFILE_UNVERIFIED"
            }
            require(firstProof == secondProof && first.id != second.id) { "REPLY_CHANGED_DURING_VERIFY" }
            val message = Message(id(), task.id, requireNotNull(unchanged.current).id, "deepseek", secondProof.answer,
                Completeness.COMPLETE, "deepseek-public-reply-${second.id}", payload.sourceIds)
            val completed = app.store.completeVerifiedExchange(task.id, unchanged.revision,
                requireNotNull(unchanged.pendingIntent), payload.text, id(), message)
            report.writeText("state=${completed.status}\ntaskId=$taskId\nturn=${completed.steps.single().status}" +
                "\nreason=${completed.reason}\npendingIntent=${completed.pendingIntent != null}" +
                "\nanswerLength=${message.text.length}\nanswerHash=${sha256(message.text)}" +
                "\nsubmitted=verified\nsource=deepseek")
        } catch (e: Exception) {
            val reason = e.message?.takeIf { it.matches(Regex("[A-Z0-9_]+")) } ?: "REPLY_VERIFY_ERROR"
            taskId?.let { runCatching { app.store.pause(it, reason, true) } }
            report.writeText("state=ERROR\ntaskId=$taskId\nreason=$reason\nsubmitted=unverified\n$diagnostics")
        } finally { app.operationBusy.set(false) }
    }
    suspend fun submit(app: TeamworkApp) {
        if (!app.operationBusy.compareAndSet(false, true)) return
        val report = File(app.filesDir, "single-exchange-last.txt")
        var taskId: String? = null
        var diagnostics = ""
        try {
            app.recovered.await()
            val previous = report.readLines()
            val retryAfterPreflight = previous.firstOrNull() == "state=ERROR" &&
                "reason=STAGED_INPUT_NOT_EXACT" in previous && "submitted=unverified" in previous
            require((previous.firstOrNull() == "state=PAUSED" && "turn=STAGED" in previous &&
                "pendingIntent=false" in previous && "submitted=false" in previous) || retryAfterPreflight) {
                "STAGED_PROBE_REQUIRED"
            }
            taskId = previous.single { it.startsWith("taskId=") }.substringAfter('=')
            val task = app.store.get(taskId)
            require(task.goal == DeepSeekProbeProfile.GOAL && task.steps.size == 1 &&
                task.participants.size == 1 && task.participants.single().id == "deepseek" &&
                task.participants.single().packageName == "com.deepseek.chat" &&
                task.participants.single().version == "2.5.2 (273)" &&
                task.status == (if (retryAfterPreflight) TaskStatus.NEEDS_USER else TaskStatus.PAUSED) &&
                task.current?.status == TurnStatus.STAGED &&
                task.pendingIntent == null) { "STAGED_PROBE_REQUIRED" }
            val payload = task.payloads.getValue(requireNotNull(task.current?.payloadId))
            require(org.phoneai.teamwork.workflow.PayloadBuilder.matches(task, task.current!!, payload)) { "PAYLOAD_MISMATCH" }
            val bound = task.bindings["deepseek"]
            require(bound?.initialWindowId != null && bound.firstUserText.isEmpty() &&
                bound.latestUserText.isEmpty()) { "THREAD_NOT_BOUND" }
            var active = app.store.update(task.id, task.revision, "STARTED", "PUBLIC_PROBE_SEND") {
                it.copy(status = TaskStatus.RUNNING, deadline = System.currentTimeMillis() + 180000)
            }
            report.writeText("state=RUNNING\ntaskId=$taskId\nphase=LOCATE_SEND")
            val phone = GuardedPhoneTools(app.store, Guardrail())
            var observation = requireNotNull(phone.observe(active)) { "OBSERVATION_UNAVAILABLE" }
            if (bound.initialWindowId != observation.windowId) {
                require(DeepSeekProbeProfile.canRebindEmptyWindow(active, observation)) { "EMPTY_THREAD_REBIND_DENIED" }
                active = app.store.update(task.id, active.revision, "THREAD_REBOUND", "VERIFIED_EMPTY_WINDOW") {
                    it.copy(bindings = it.bindings + ("deepseek" to bound.copy(initialWindowId = observation.windowId)))
                }
                observation = requireNotNull(phone.observe(active)) { "OBSERVATION_UNAVAILABLE" }
            }
            val editable = observation.nodes.filter { it.editable }
            diagnostics = DeepSeekProbeProfile.diagnostics(active, observation) +
                "\nprofileVerified=${observation.profileVerified}\neditableCount=${editable.size}" +
                "\ninputLength=${editable.singleOrNull()?.text?.length ?: -1}" +
                "\ninputHashMatches=${editable.singleOrNull()?.text?.let(::sha256) == payload.hash}" +
                "\npayloadLength=${payload.text.length}\nthreadEvidence=${DeepSeekProbeProfile.evidence(active, observation) != null}"
            require(observation.profileVerified && DeepSeekProbeProfile.evidence(active, observation) != null &&
                observation.nodes.singleOrNull { it.role == NodeRole.INPUT }?.text == payload.text) { "STAGED_INPUT_NOT_EXACT" }
            val proposal = app.model.propose(ControlContext(active.id, active.current!!.id, "deepseek", null,
                setOf(Action.FOCUS_SEND, Action.PAUSE), active.maxActionsPerStep - active.current!!.actionCount), observation)
            val current = app.store.get(taskId)
            require(current.status == TaskStatus.RUNNING && current.revision == active.revision &&
                current.pendingIntent == null) { "STATE_CHANGED" }
            val modelAction = proposal.action
            require(modelAction?.action == Action.FOCUS_SEND && modelAction.participantId == "deepseek" &&
                modelAction.payloadId == null && modelAction.target != null) { "MODEL_SEND_TARGET_REQUIRED" }
            val sendTarget = requireNotNull(DeepSeekProbeProfile.sendTarget(current, observation, modelAction.target)) {
                "SEND_LABEL_NOT_ACTIONABLE"
            }
            val result = phone.execute(taskId, ProposedAction(Action.SUBMIT, "deepseek", sendTarget, payload.id))
            val after = app.store.get(taskId)
            if (after.status == TaskStatus.RUNNING) app.store.pause(taskId, "SEND_EFFECT_REQUIRES_VERIFICATION", true)
            val paused = app.store.get(taskId)
            report.writeText("state=${paused.status}\ntaskId=$taskId\nturn=${paused.current?.status}\nreason=${paused.reason}\npendingIntent=${paused.pendingIntent != null}\nclickAcknowledged=${result.allowed}\n${app.model.lastMetrics}\nsubmitted=unverified")
        } catch (e: Exception) {
            val reason = e.message?.takeIf { it.matches(Regex("[A-Z0-9_]+")) } ?: "SEND_EXPERIMENT_ERROR"
            taskId?.let { runCatching { app.store.pause(it, reason, true) } }
            report.writeText("state=ERROR\ntaskId=$taskId\nreason=$reason\nsubmitted=unverified\n$diagnostics")
        } finally {
            try { withContext(NonCancellable) { app.model.close() } }
            finally { app.operationBusy.set(false) }
        }
    }
    suspend fun stage(app: TeamworkApp, participantId: String = "deepseek") {
        if (!app.operationBusy.compareAndSet(false, true)) return
        var taskId: String? = null
        var diagnostics = ""
        val report = File(app.filesDir, "single-exchange-last.txt")
        try {
            app.recovered.await()
            val expectedVersion = when (participantId) {
                "deepseek" -> "2.5.2 (273)"
                "kimi" -> "3.1.3 (378)"
                "doubao" -> "14.9.0 (14090001)"
                else -> error("UNAUTHORIZED_APP")
            }
            val participant = requireNotNull(Targets.all.single { it.id == participantId }.installed(app))
            require(participant.version == expectedVersion) { "UNVERIFIED_VERSION" }
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
            diagnostics = if (participantId == "deepseek") DeepSeekProbeProfile.diagnostics(active, o)
                else PublicEmptyProfiles.diagnostics(o, participantId)
            require(if (participantId == "deepseek") DeepSeekProbeProfile.empty(active, o) else o.profileVerified) {
                "EMPTY_PAGE_ANCHORS_REQUIRED"
            }
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
