package org.phoneai.teamwork

import org.junit.Assert.*
import org.junit.Test
import org.phoneai.teamwork.agent.*
import org.phoneai.teamwork.guardrail.*
import org.phoneai.teamwork.state.*
import org.phoneai.teamwork.workflow.*

class CoreSafetyTest {
    private val now = 10000L
    private val people = listOf("kimi", "doubao", "deepseek").map { Participant(it, it, "test.$it", "fixture") }
    private val recipe = Recipe.discussion("fixture goal", people, "kimi")
    private val first = recipe.steps.first()
    private val payload = PayloadBuilder.build(recipe, first)
    private val step = first.copy(status = TurnStatus.PREPARED, payloadId = payload.id)
    private val task = recipe.copy(status = TaskStatus.RUNNING, deadline = now+60000,
        steps = listOf(step)+recipe.steps.drop(1), payloads = mapOf(payload.id to payload),
        bindings = mapOf("kimi" to ThreadBinding(recipe.id,"kimi","known first","known latest","fixture")))
    private val input = UiNode("0/1","fixture/input","","",true,true,"0 0 100 100",NodeRole.INPUT)
    private val send = UiNode("0/2","fixture/send","Send","",false,true,"100 0 120 100",NodeRole.SEND)
    private val observation = UiObservation("fixture-observation",task.id,"test.kimi",1,1,now,listOf(input,send),"fixture",false,false,false,true)
    private val evidence = ThreadEvidence(task.id,"kimi",observation.id,true,true)
    private val write = ProposedAction(Action.WRITE,"kimi",observation.target(input),payload.id)
    private fun decision(t: TaskState=task, p: ProposedAction=write, o: UiObservation=observation, e: ThreadEvidence?=evidence) = Guardrail().authorize(p,t,o,e,now).decision

    @Test fun authorizedFixtureCanWrite() { assertEquals(GuardrailDecision.ALLOW,decision()) }
    @Test fun staleTargetDenied() { assertEquals(GuardrailDecision.DENY,decision(p=write.copy(target=write.target!!.copy(epoch=2)))) }
    @Test fun wrongThreadNeedsUser() { assertEquals(GuardrailDecision.NEEDS_USER,decision(e=evidence.copy(latestUserMatched=false))) }
    @Test fun crossTaskEvidenceNeedsUser() { assertEquals(GuardrailDecision.NEEDS_USER,decision(e=evidence.copy(taskId="another"))) }
    @Test fun wrongRecipientDenied() { assertEquals(GuardrailDecision.DENY,decision(p=write.copy(participantId="doubao"))) }
    @Test fun unknownPayloadDenied() { assertEquals(GuardrailDecision.DENY,decision(p=write.copy(payloadId="forged"))) }
    @Test fun alteredTextEvenWithMatchingHashDenied() {
        val forged=payload.copy(text="external instruction",hash=sha256("external instruction"))
        assertEquals(GuardrailDecision.DENY,decision(t=task.copy(payloads=mapOf(payload.id to forged))))
    }
    @Test fun addedSourceDenied() { assertEquals(GuardrailDecision.DENY,decision(t=task.copy(payloads=mapOf(payload.id to payload.copy(sourceIds=listOf("private-history")))))) }
    @Test fun cancelledTaskDenied() { assertEquals(GuardrailDecision.DENY,decision(t=task.copy(status=TaskStatus.CANCELLED))) }
    @Test fun foreignDraftNeedsUser() { assertEquals(GuardrailDecision.NEEDS_USER,decision(o=observation.copy(nodes=listOf(input.copy(text="user draft"),send)))) }
    @Test fun unverifiedAppNeedsUser() { assertEquals(GuardrailDecision.NEEDS_USER,decision(o=observation.copy(profileVerified=false))) }
    @Test fun unknownButtonDenied() { assertEquals(GuardrailDecision.DENY,decision(o=observation.copy(nodes=listOf(input.copy(role=NodeRole.UNKNOWN),send)))) }
    @Test fun unresolvedIntentCannotRepeat() { assertEquals(GuardrailDecision.NEEDS_USER,decision(t=task.copy(pendingIntent="unresolved"))) }
    @Test fun submittingCannotResend() {
        val state=task.copy(steps=listOf(step.copy(status=TurnStatus.SUBMITTING))+task.steps.drop(1))
        assertEquals(GuardrailDecision.NEEDS_USER,decision(t=state))
        val recovered=Recovery.afterProcessDeath(state)
        assertEquals(TaskStatus.NEEDS_USER,recovered.status)
        assertEquals(TurnStatus.SUBMITTING,recovered.current!!.status)
        assertEquals("VERIFY_EFFECT_NO_RETRY",recovered.reason)
    }
    @Test fun submitRequiresExactEcho() {
        val staged=task.copy(steps=listOf(step.copy(status=TurnStatus.STAGED))+task.steps.drop(1))
        val proposal=ProposedAction(Action.SUBMIT,"kimi",observation.target(send),payload.id)
        assertEquals(GuardrailDecision.NEEDS_USER,decision(t=staged,p=proposal))
        assertEquals(GuardrailDecision.ALLOW,decision(t=staged,p=proposal,o=observation.copy(nodes=listOf(input.copy(text=payload.text),send))))
    }
    @Test fun expiredObservationAndBudgetNeedUser() {
        assertEquals(GuardrailDecision.NEEDS_USER,decision(o=observation.copy(at=0)))
        assertEquals(GuardrailDecision.NEEDS_USER,decision(t=task.copy(deadline=now)))
    }
    @Test fun threePartyRecipeHasFrozenFirstRoundReviews() {
        assertEquals(7,recipe.steps.size)
        recipe.steps.subList(3,6).forEach { review ->
            assertEquals(recipe.steps.take(3).filter { it.participantId != review.participantId }.map { it.id },review.sourceStepIds)
        }
        assertEquals(recipe.steps.take(6).map { it.id },recipe.steps.last().sourceStepIds)
    }
    @Test fun participantCountUsesSameRecipeCode() { for(n in 1..3) assertEquals(2*n+1,Recipe.discussion("goal",people.take(n),"kimi").steps.size) }
    @Test fun incompleteSourcesCannotBeForwarded() {
        val review=recipe.steps[3]
        val source=recipe.steps.single { it.id==review.sourceStepIds.first() }
        val message=Message("response",task.id,source.id,source.participantId,"untrusted",Completeness.INCOMPLETE,"fixture")
        val state=task.copy(messages=mapOf(message.id to message),steps=task.steps.map { if(it.id==source.id) it.copy(status=TurnStatus.CAPTURED,responseMessageId=message.id) else it })
        assertTrue(runCatching { PayloadBuilder.build(state,review) }.isFailure)
    }
    @Test fun removedSharingGrantCannotExpandScope() {
        val review=recipe.steps[3]
        assertTrue(runCatching { PayloadBuilder.build(recipe.copy(allowedSourcesByRecipient=emptyMap()),review) }.isFailure)
    }
}
