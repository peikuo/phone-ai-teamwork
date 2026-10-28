package org.phoneai.teamwork.state

import java.security.MessageDigest
import java.util.UUID

fun id(): String = UUID.randomUUID().toString()
fun sha256(text: String): String = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
enum class TaskStatus { CREATED, RUNNING, WAITING, PAUSED, NEEDS_USER, COMPLETED, FAILED, CANCELLED }
enum class TurnStatus { PLANNED, PREPARED, STAGED, SUBMITTING, SENT_CONFIRMED, WAITING, CAPTURED, NEEDS_USER }
enum class Completeness { UNKNOWN, INCOMPLETE, COMPLETE, USER_CONFIRMED }
enum class Template { INDEPENDENT, REVIEW, SYNTHESIS }
data class Participant(val id: String, val label: String, val packageName: String, val version: String)
data class Step(val id: String, val participantId: String, val template: Template, val sourceStepIds: List<String>, val status: TurnStatus = TurnStatus.PLANNED, val payloadId: String? = null, val responseMessageId: String? = null, val actionCount: Int = 0)
data class ThreadBinding(val taskId: String, val participantId: String, val firstUserText: String, val latestUserText: String, val appVersion: String, val titleHint: String? = null, val initialWindowId: Int? = null)
data class Message(val id: String, val taskId: String, val turnId: String, val participantId: String, val text: String, val completeness: Completeness, val evidence: String, val sourceIds: List<String> = emptyList())
data class Payload(val id: String, val taskId: String, val turnId: String, val recipient: String, val sourceIds: List<String>, val text: String, val hash: String)
data class TaskState(
    val id: String, val goal: String, val participants: List<Participant>, val summarizer: String,
    val steps: List<Step>, val allowedSourcesByRecipient: Map<String, Set<String>>,
    val status: TaskStatus = TaskStatus.CREATED, val cursor: Int = 0, val revision: Long = 0,
    val bindings: Map<String, ThreadBinding> = emptyMap(), val payloads: Map<String, Payload> = emptyMap(),
    val messages: Map<String, Message> = emptyMap(), val maxActionsPerStep: Int = 40,
    val reason: String? = null, val pendingIntent: String? = null, val deadline: Long = 0,
    // Missing in older snapshots; payload content selects the legacy template when needed.
    val promptVersion: Int? = 2
) { val current: Step? get() = steps.getOrNull(cursor) }
data class Journal(val id: String = id(), val taskId: String, val turnId: String?, val kind: String, val reason: String, val at: Long = System.currentTimeMillis())
