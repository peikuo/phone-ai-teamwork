package org.phoneai.teamwork

import org.junit.Assert.*
import org.junit.Test
import org.phoneai.teamwork.agent.Action
import org.phoneai.teamwork.agent.ControlContext
import org.phoneai.teamwork.model.modelOutputKeysAllowed

class ModelOutputKeysTest {
    private fun context(allowed: Set<Action>, payload: String? = null) =
        ControlContext("task", "step", "kimi", payload, allowed, 1)

    @Test fun optionalPayloadOnlyForPayloadFreeLocatorProposals() {
        val twoKeys = setOf("action", "node")
        val threeKeys = twoKeys + "payload"
        assertTrue(modelOutputKeysAllowed(twoKeys, context(setOf(Action.FOCUS_SEND, Action.PAUSE))))
        assertTrue(modelOutputKeysAllowed(twoKeys, context(setOf(Action.FOCUS_INPUT, Action.PAUSE))))
        assertTrue(modelOutputKeysAllowed(threeKeys, context(setOf(Action.FOCUS_SEND, Action.PAUSE))))
        assertFalse(modelOutputKeysAllowed(twoKeys, context(setOf(Action.SUBMIT, Action.PAUSE), "payload")))
        assertFalse(modelOutputKeysAllowed(twoKeys, context(setOf(Action.WRITE, Action.PAUSE), "payload")))
        assertFalse(modelOutputKeysAllowed(twoKeys + "reason", context(setOf(Action.FOCUS_SEND, Action.PAUSE))))
        assertFalse(modelOutputKeysAllowed(setOf("action"), context(setOf(Action.FOCUS_SEND, Action.PAUSE))))
    }
}
