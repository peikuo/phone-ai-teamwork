package org.phoneai.teamwork.state

import android.content.Context
import androidx.room.*
import com.google.gson.Gson
import org.phoneai.teamwork.workflow.PayloadBuilder
import org.phoneai.teamwork.agent.Action

@Entity(tableName = "tasks")
data class TaskRow(@PrimaryKey val id: String, val updatedAt: Long, val json: String)
@Entity(tableName = "journal", indices = [Index("taskId")])
data class JournalRow(@PrimaryKey val id: String, val taskId: String, val at: Long, val turnId: String?, val kind: String, val reason: String)
@Dao
interface StateDao {
    @Query("SELECT * FROM tasks ORDER BY updatedAt DESC") fun all(): List<TaskRow>
    @Query("SELECT * FROM tasks WHERE id = :id") fun get(id: String): TaskRow?
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun put(row: TaskRow)
    @Insert fun journal(row: JournalRow)
    @Query("SELECT * FROM journal WHERE taskId = :id ORDER BY at DESC LIMIT 80") fun journalFor(id: String): List<JournalRow>
    @Query("DELETE FROM tasks WHERE id = :id") fun deleteTask(id: String)
    @Query("DELETE FROM journal WHERE taskId = :id") fun deleteJournal(id: String)
}
@Database(entities = [TaskRow::class, JournalRow::class], version = 1, exportSchema = false)
abstract class StateDatabase : RoomDatabase() { abstract fun state(): StateDao }

// Task snapshot includes messages, payloads and bindings. One Room transaction writes snapshot + journal.
// This POC intentionally uses two tables; normalize messages when measured task sizes require it.
class TaskStore(context: Context) : AgentState {
    private val db = Room.databaseBuilder(context, StateDatabase::class.java, "teamwork.db").build()
    private val gson = Gson()
    private val live = mutableMapOf<String, TaskState>()
    @Synchronized fun all(): List<TaskState> = db.state().all().map { gson.fromJson(it.json, TaskState::class.java) }
    @Synchronized override fun get(id: String): TaskState = gson.fromJson(requireNotNull(db.state().get(id)).json, TaskState::class.java).also { live[id] = it }
    @Synchronized fun create(task: TaskState) {
        require(db.state().get(task.id) == null)
        write(task, "CREATED", "RECIPE_AND_SHARING_APPROVED")
    }
    @Synchronized fun update(id: String, expectedRevision: Long? = null, kind: String, reason: String, change: (TaskState) -> TaskState): TaskState {
        val old = get(id)
        require(expectedRevision == null || expectedRevision == old.revision) { "STATE_CHANGED" }
        val next = change(old).copy(revision = old.revision + 1)
        require(next.id == old.id)
        write(next, kind, reason)
        return next
    }
    private fun write(task: TaskState, kind: String, reason: String) {
        val event = Journal(taskId = task.id, turnId = task.current?.id, kind = kind, reason = reason)
        db.runInTransaction {
            db.state().put(TaskRow(task.id, event.at, gson.toJson(task)))
            db.state().journal(JournalRow(event.id, task.id, event.at, event.turnId, kind, reason))
        }
        live[task.id] = task
    }
    fun prepare(id: String): TaskState = update(id, kind = "PREPARED", reason = "TEMPLATE_PAYLOAD") { task ->
        require(task.status in setOf(TaskStatus.CREATED, TaskStatus.PAUSED))
        val step = requireNotNull(task.current)
        require(step.status == TurnStatus.PLANNED && task.pendingIntent == null)
        val payload = PayloadBuilder.build(task, step)
        task.copy(status = TaskStatus.PAUSED, payloads = task.payloads + (payload.id to payload),
            steps = task.steps.map { if (it.id == step.id) it.copy(status = TurnStatus.PREPARED, payloadId = payload.id) else it }, reason = "POC_PROFILE_UNVERIFIED")
    }
    fun pause(id: String, reason: String, needsUser: Boolean = false) = update(id, kind = "PAUSED", reason = reason) { task ->
        if (task.status in setOf(TaskStatus.CANCELLED, TaskStatus.COMPLETED, TaskStatus.FAILED)) task
        else task.copy(status = if (needsUser) TaskStatus.NEEDS_USER else TaskStatus.PAUSED, reason = reason)
    }
    @Synchronized override fun pauseIfCurrent(id: String, revision: Long, reason: String, needsUser: Boolean): Boolean {
        val current = get(id)
        if (current.revision != revision || current.status != TaskStatus.RUNNING) return false
        update(id, revision, "PAUSED", reason) {
            it.copy(status = if (needsUser) TaskStatus.NEEDS_USER else TaskStatus.PAUSED, reason = reason)
        }
        return true
    }
    fun cancel(id: String) = update(id, kind = "CANCELLED", reason = "EXTERNAL_EFFECTS_MAY_REMAIN") { it.copy(status = TaskStatus.CANCELLED, reason = "EXTERNAL_EFFECTS_MAY_REMAIN") }
    @Synchronized fun recover() {
        all().forEach { old ->
            val recovered = Recovery.afterProcessDeath(old)
            if (recovered != old) update(old.id, kind = "RECOVERY", reason = recovered.reason.orEmpty()) { recovered }
        }
    }

    @Synchronized fun delete(id: String) {
        require(get(id).status != TaskStatus.RUNNING)
        db.runInTransaction { db.state().deleteJournal(id); db.state().deleteTask(id) }
        live.remove(id)
    }
    // No Room access on the UI thread. Updates/cancellation cannot interleave with this short Android call.
    @Synchronized fun <T> atRevision(id: String, revision: Long, action: (TaskState) -> T): T {
        val current = requireNotNull(live[id])
        require(current.revision == revision && current.status == TaskStatus.RUNNING) { "STATE_CHANGED_BEFORE_EFFECT" }
        return action(current)
    }
    fun journal(id: String) = db.state().journalFor(id)

    // Intent is committed before a future executor calls Android. A crash leaves a recoverable pendingIntent.
    fun beginEffect(id: String, revision: Long, action: Action, intentId: String, now: Long): TaskState =
        update(id, revision, "EFFECT_INTENT", action.name) { TurnLifecycle.beginEffect(it, action, intentId, now) }
    fun confirmWrite(id: String, revision: Long, intentId: String, exactInputText: String): TaskState =
        update(id, revision, "WRITE_VERIFIED", "EXACT_INPUT_ECHO") { TurnLifecycle.confirmWrite(it, intentId, exactInputText) }
    fun confirmSend(id: String, revision: Long, intentId: String, exactUserMessage: String, evidenceId: String): TaskState =
        update(id, revision, "SEND_VERIFIED", evidenceId) { TurnLifecycle.confirmSend(it, intentId, exactUserMessage, evidenceId) }
    fun capture(id: String, revision: Long, message: Message): TaskState =
        update(id, revision, "RESPONSE_CAPTURED", "VERIFIED_MESSAGE_STORED") { TurnLifecycle.capture(it, message) }
}
