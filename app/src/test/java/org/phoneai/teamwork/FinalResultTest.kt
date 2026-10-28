package org.phoneai.teamwork

import org.junit.Assert.*
import org.junit.Test
import org.phoneai.teamwork.state.*
import org.phoneai.teamwork.workflow.*

class FinalResultTest {
    private val participant = Participant("deepseek", "DeepSeek", "com.deepseek.chat", "fixture")
    private val recipe = Recipe.discussion("What is 2 + 2?", listOf(participant), participant.id)
    private val steps = recipe.steps.mapIndexed { index, step ->
        step.copy(status = TurnStatus.CAPTURED, responseMessageId = "answer-$index")
    }
    private val messages = steps.mapIndexed { index, step ->
        val sources = if (index == steps.lastIndex) steps.dropLast(1).map { it.responseMessageId!! } else emptyList()
        val message = Message("answer-$index", recipe.id, step.id, participant.id,
            if (index == steps.lastIndex) "The answer is four." else "Four.",
            Completeness.COMPLETE, "verified-$index", sources)
        message.id to message
    }.toMap()
    private val completed = recipe.copy(steps = steps, messages = messages,
        status = TaskStatus.COMPLETED, cursor = steps.size)

    @Test fun onlyVerifiedSynthesisAppearsAsFinalAnswer() {
        assertEquals("The answer is four.", completed.finalAnswer()?.text)
        assertNull(completed.copy(status = TaskStatus.PAUSED).finalAnswer())
        assertNull(completed.copy(cursor = 2).finalAnswer())
        assertNull(completed.copy(pendingIntent = "uncertain-send").finalAnswer())
        val final = messages.getValue("answer-2")
        for (invalid in listOf(
            final.copy(id = "forged"),
            final.copy(completeness = Completeness.INCOMPLETE),
            final.copy(turnId = steps.first().id),
            final.copy(sourceIds = listOf("wrong-source")),
            final.copy(evidence = "")
        )) {
            assertNull(completed.copy(messages = messages + (final.id to invalid)).finalAnswer())
        }
        val source = messages.getValue("answer-0")
        assertNull(completed.copy(messages = messages - source.id).finalAnswer())
        for (invalid in listOf(
            source.copy(id = "forged"),
            source.copy(taskId = "other-task"),
            source.copy(turnId = steps.last().id),
            source.copy(participantId = "other-app"),
            source.copy(completeness = Completeness.INCOMPLETE),
            source.copy(evidence = "")
        )) {
            assertNull(completed.copy(messages = messages + (source.id to invalid)).finalAnswer())
        }
    }

    @Test fun allThreePayloadTemplatesAskForEnglish() {
        val first = recipe.steps[0]
        val firstPayload = PayloadBuilder.build(recipe, first)
        assertTrue(firstPayload.text.contains("Answer the original question in English"))
        val withFirst = recipe.copy(
            steps = recipe.steps.map { if (it.id == first.id) it.copy(status = TurnStatus.CAPTURED, responseMessageId = "answer-0") else it },
            messages = mapOf("answer-0" to messages.getValue("answer-0")))
        val review = recipe.steps[1]
        assertTrue(PayloadBuilder.build(withFirst, review).text.contains("Review the peer responses below in English"))
        val withSources = recipe.copy(steps = steps, messages = messages)
        assertTrue(PayloadBuilder.build(withSources, withSources.steps.last()).text.contains("Synthesize the evidence in English"))
    }

    @Test fun singlePublicExchangeCanShowItsVerifiedReply() {
        val single = recipe.copy(steps = listOf(steps.first()), messages = mapOf("answer-0" to messages.getValue("answer-0")),
            status = TaskStatus.COMPLETED, cursor = 1)
        assertEquals("Four.", single.verifiedSingleReply()?.text)
        assertNull(single.finalAnswer())
        assertNull(single.copy(messages = mapOf("answer-0" to messages.getValue("answer-0").copy(
            completeness = Completeness.INCOMPLETE))).verifiedSingleReply())
    }

    @Test fun legacySnapshotKeepsItsOriginalPayloadTemplate() {
        val gson = com.google.gson.Gson()
        val savedBeforeUpgrade = gson.toJson(recipe.copy(promptVersion = null))
        val restored = gson.fromJson(savedBeforeUpgrade, TaskState::class.java)
        assertNull(restored.promptVersion)
        val original = PayloadBuilder.build(restored, restored.steps.first())
        assertTrue(original.text.contains("独立回答原问题"))
        val prepared = restored.copy(
            steps = listOf(restored.steps.first().copy(status = TurnStatus.PREPARED, payloadId = original.id)) + restored.steps.drop(1),
            payloads = mapOf(original.id to original))
        assertTrue(PayloadBuilder.matches(prepared, prepared.steps.first(), original))
        assertFalse(PayloadBuilder.matches(prepared.copy(promptVersion = 2), prepared.steps.first(), original))
        val englishBeforeVersionField = recipe.copy(promptVersion = null)
        val englishPayload = PayloadBuilder.build(recipe, recipe.steps.first())
        val transitional = englishBeforeVersionField.copy(
            steps = listOf(englishBeforeVersionField.steps.first().copy(status = TurnStatus.STAGED, payloadId = englishPayload.id)) +
                englishBeforeVersionField.steps.drop(1),
            payloads = mapOf(englishPayload.id to englishPayload))
        assertTrue(PayloadBuilder.matches(transitional, transitional.steps.first(), englishPayload))
    }
}
