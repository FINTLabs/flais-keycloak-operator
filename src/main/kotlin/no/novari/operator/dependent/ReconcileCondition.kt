package no.novari.operator.dependent

import io.fabric8.kubernetes.api.model.HasMetadata
import io.javaoperatorsdk.operator.api.reconciler.Context

interface ReconcileCondition<P : HasMetadata> {
    fun shouldReconcile(
        primary: P,
        context: Context<P>,
    ): Boolean
}
