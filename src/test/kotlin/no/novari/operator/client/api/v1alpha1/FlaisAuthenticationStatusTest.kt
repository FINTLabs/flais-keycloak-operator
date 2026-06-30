package no.novari.operator.client.api.v1alpha1

import io.fabric8.kubernetes.api.model.Condition
import io.fabric8.kubernetes.api.model.ConditionBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import java.time.Instant

class FlaisAuthenticationStatusTest {
    @Test
    fun `appends new condition and sets transition time when unset`() {
        val status =
            FlaisAuthenticationStatus()
                .withCondition(condition(lastTransitionTime = null))

        val actual = status.conditions.single()

        assertEquals("Ready", actual.type)
        assertNotNull(actual.lastTransitionTime)
        Instant.parse(actual.lastTransitionTime)
    }

    @Test
    fun `returns same status when condition does not change`() {
        val existing =
            condition(
                status = "False",
                lastTransitionTime = OLD_TRANSITION_TIME,
            )
        val status = FlaisAuthenticationStatus(conditions = listOf(existing))
        val newCondition =
            condition(
                status = "False",
                lastTransitionTime = "2025-01-01T00:00:00Z",
            )

        val updated = status.withCondition(newCondition)

        assertSame(status, updated)
    }

    @Test
    fun `preserves transition time when existing condition status is unchanged`() {
        val existing =
            condition(
                reason = "Processing",
                message = "Started processing resource",
                observedGeneration = 1,
                status = "False",
                lastTransitionTime = OLD_TRANSITION_TIME,
            )
        val newCondition =
            condition(
                reason = "StillProcessing",
                message = "Processing is still running",
                observedGeneration = 2,
                status = "False",
                lastTransitionTime = "2025-01-01T00:00:00Z",
            )

        val updated =
            FlaisAuthenticationStatus(conditions = listOf(existing))
                .withCondition(newCondition)
        val actual = updated.conditions.single()

        assertEquals("StillProcessing", actual.reason)
        assertEquals("Processing is still running", actual.message)
        assertEquals(2, actual.observedGeneration)
        assertEquals(OLD_TRANSITION_TIME, actual.lastTransitionTime)
    }

    @Test
    fun `sets transition time when existing condition status changes`() {
        val existing =
            condition(
                status = "False",
                lastTransitionTime = OLD_TRANSITION_TIME,
            )
        val newCondition =
            condition(
                status = "True",
                lastTransitionTime = "2025-01-01T00:00:00Z",
            )
        val before = Instant.now()

        val updated =
            FlaisAuthenticationStatus(conditions = listOf(existing))
                .withCondition(newCondition)
        val actual = updated.conditions.single()
        val transitionTime = Instant.parse(actual.lastTransitionTime)

        assertEquals("True", actual.status)
        assertNotEquals(OLD_TRANSITION_TIME, actual.lastTransitionTime)
        assertFalse(transitionTime.isBefore(before))
    }

    @Test
    fun `applies multiple conditions in order`() {
        val status =
            FlaisAuthenticationStatus()
                .withConditions(
                    condition(type = "Ready", status = "False"),
                    condition(type = "Error", status = "True"),
                    condition(type = "Ready", status = "True"),
                )

        assertEquals(listOf("Ready", "Error"), status.conditions.map { it.type })
        assertEquals("True", status.conditions.single { it.type == "Ready" }.status)
        assertEquals("True", status.conditions.single { it.type == "Error" }.status)
    }

    private fun condition(
        type: String = "Ready",
        status: String = "False",
        reason: String = "Processing",
        message: String = "Processing resource",
        observedGeneration: Long = 1,
        lastTransitionTime: String? = OLD_TRANSITION_TIME,
    ): Condition =
        ConditionBuilder()
            .withType(type)
            .withStatus(status)
            .withReason(reason)
            .withMessage(message)
            .withObservedGeneration(observedGeneration)
            .apply {
                if (lastTransitionTime != null) {
                    withLastTransitionTime(lastTransitionTime)
                }
            }.build()

    private companion object {
        const val OLD_TRANSITION_TIME = "2024-01-01T00:00:00Z"
    }
}
