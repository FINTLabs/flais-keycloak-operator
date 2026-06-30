package no.novari.kubernetes.api.model

import io.fabric8.kubernetes.api.model.Condition
import io.fabric8.kubernetes.api.model.ConditionBuilder
import no.novari.kubernetes.api.ConditionStatus
import no.novari.kubernetes.api.annotation.KubernetesDslMarker
import java.time.Instant

fun condition(block: ConditionDsl.() -> Unit): Condition {
    return ConditionDsl().apply(block).build()
}

@KubernetesDslMarker
class ConditionDsl {
    private val builder = ConditionBuilder()

    fun type(type: String) {
        builder.withType(type)
    }

    fun reason(reason: String) {
        builder.withReason(reason)
    }

    fun status(status: ConditionStatus) {
        builder.withStatus(status.toString())
    }

    fun message(message: String) {
        builder.withMessage(message)
    }

    fun observedGeneration(observedGeneration: Long) {
        builder.withObservedGeneration(observedGeneration)
    }

    fun lastTransitionTime(lastTransitionTime: Instant) {
        builder.withLastTransitionTime(lastTransitionTime.toString())
    }

    fun lastTransitionTime(lastTransitionTime: String) {
        builder.withLastTransitionTime(lastTransitionTime)
    }

    internal fun build(): Condition = builder.build()
}
