package org.phoneai.teamwork.state

object Recovery {
    fun afterProcessDeath(task: TaskState): TaskState {
        if (task.status in setOf(TaskStatus.CANCELLED, TaskStatus.COMPLETED, TaskStatus.FAILED)) return task
        val uncertain = task.pendingIntent != null || task.current?.status == TurnStatus.SUBMITTING
        return if (uncertain || task.status in setOf(TaskStatus.RUNNING, TaskStatus.WAITING))
            task.copy(status = TaskStatus.NEEDS_USER, reason = if (uncertain) "VERIFY_EFFECT_NO_RETRY" else "FRESH_OBSERVATION_REQUIRED")
        else task
    }
}
