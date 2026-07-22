package no.novari.operator.client.api.v1alpha1

import com.fasterxml.jackson.annotation.JsonInclude
import io.fabric8.kubernetes.api.model.Condition
import no.novari.kubernetes.api.withStatusCondition
import java.time.Instant

data class FlaisAuthenticationStatus(
    val observedGeneration: Long? = null,
    val clientID: String? = null,
    val synchronizationHash: String? = null,
    val synchronizationTime: Instant? = null,
    @get:JsonInclude(JsonInclude.Include.NON_EMPTY)
    val conditions: List<Condition> = emptyList(),
) {
    fun withConditions(vararg newConditions: Condition): FlaisAuthenticationStatus =
        newConditions.fold(this) { status, condition ->
            status.withCondition(condition)
        }

    fun withCondition(newCondition: Condition): FlaisAuthenticationStatus {
        val updatedConditions = conditions.withStatusCondition(newCondition)

        return if (updatedConditions == conditions) {
            this
        } else {
            copy(conditions = updatedConditions)
        }
    }
}
