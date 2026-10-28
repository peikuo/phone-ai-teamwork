package org.phoneai.teamwork.state

/** Only a verified, persisted synthesis response is eligible for the task result screen. */
fun TaskState.finalAnswer(): Message? {
    if (status != TaskStatus.COMPLETED || steps.isEmpty() || cursor != steps.size || pendingIntent != null) return null
    val synthesis = steps.last()
    if (synthesis.template != Template.SYNTHESIS || synthesis.status != TurnStatus.CAPTURED ||
        synthesis.participantId != summarizer) return null
    val message = messages[synthesis.responseMessageId] ?: return null
    if (message.id != synthesis.responseMessageId || message.taskId != id || message.turnId != synthesis.id || message.participantId != summarizer ||
        message.text.isBlank() || message.evidence.isBlank() ||
        message.completeness !in setOf(Completeness.COMPLETE, Completeness.USER_CONFIRMED)) return null
    val sourceMessageIds = synthesis.sourceStepIds.map { sourceId ->
        val source = steps.singleOrNull { it.id == sourceId } ?: return null
        if (source.status != TurnStatus.CAPTURED) return null
        val sourceMessageId = source.responseMessageId ?: return null
        val sourceMessage = messages[sourceMessageId] ?: return null
        if (sourceMessage.id != sourceMessageId || sourceMessage.taskId != id ||
            sourceMessage.turnId != source.id || sourceMessage.participantId != source.participantId ||
            sourceMessage.text.isBlank() || sourceMessage.evidence.isBlank() ||
            sourceMessage.completeness !in setOf(Completeness.COMPLETE, Completeness.USER_CONFIRMED)) return null
        sourceMessageId
    }
    if (message.sourceIds != sourceMessageIds) return null
    return message
}

/** Debug single-exchange tasks have one answer rather than a synthesis step. */
fun TaskState.verifiedSingleReply(): Message? {
    if (status != TaskStatus.COMPLETED || steps.size != 1 || cursor != 1 || pendingIntent != null) return null
    val step = steps.single()
    if (step.template != Template.INDEPENDENT || step.status != TurnStatus.CAPTURED) return null
    val message = messages[step.responseMessageId] ?: return null
    return message.takeIf {
        it.id == step.responseMessageId && it.taskId == id && it.turnId == step.id &&
            it.participantId == step.participantId && it.text.isNotBlank() && it.evidence.isNotBlank() &&
            it.sourceIds.isEmpty() && it.completeness in setOf(Completeness.COMPLETE, Completeness.USER_CONFIRMED)
    }
}
