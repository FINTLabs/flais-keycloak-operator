package no.novari.kubernetes.operator.dependent

import io.fabric8.kubernetes.api.model.HasMetadata
import io.javaoperatorsdk.operator.api.reconciler.Context

interface ReadyCondition<P : HasMetadata> {
    fun isReady(
        primary: P,
        context: Context<P>,
    ): Boolean
}
