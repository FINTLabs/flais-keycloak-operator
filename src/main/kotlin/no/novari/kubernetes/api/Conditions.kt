package no.novari.kubernetes.api

import io.fabric8.kubernetes.api.model.Condition
import io.fabric8.kubernetes.api.model.ConditionBuilder
import no.novari.kubernetes.api.model.condition
import java.time.Instant

fun readyCondition(
    status: ConditionStatus,
    reason: String,
    message: String,
    generation: Long,
) = condition {
    type("Ready")
    status(status)
    reason(reason)
    message(message)
    observedGeneration(generation)
}

fun errorCondition(
    status: ConditionStatus,
    reason: String,
    message: String,
    generation: Long,
) = condition {
    type("Error")
    status(status)
    reason(reason)
    message(message)
    observedGeneration(generation)
}

fun List<Condition>.withStatusCondition(newCondition: Condition): List<Condition> {
    val existingIndex = indexOfFirst { it.type == newCondition.type }

    if (existingIndex == -1) {
        return this + newCondition.withLastTransitionTime(newCondition.lastTransitionTime ?: now())
    }

    val existingCondition = this[existingIndex]
    val updatedCondition =
        newCondition.withLastTransitionTime(
            if (existingCondition.status == newCondition.status) {
                existingCondition.lastTransitionTime
            } else {
                now()
            },
        )

    return mapIndexed { index, condition ->
        if (index == existingIndex) updatedCondition else condition
    }
}

private fun Condition.withLastTransitionTime(lastTransitionTime: String?): Condition =
    ConditionBuilder(this)
        .withLastTransitionTime(lastTransitionTime)
        .build()

private fun now(): String = Instant.now().toString()
