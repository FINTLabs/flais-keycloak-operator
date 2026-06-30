package no.novari.kubernetes.operator

import io.fabric8.kubernetes.api.model.HasMetadata
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceSpec
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResourceConfig
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition
import org.koin.core.qualifier.Qualifier

class KoinDependentResourceSpec<R, P : HasMetadata>(
    val qualifier: Qualifier?,
    dependentResourceClass: Class<out DependentResource<R, P>>,
    name: String,
    readyCondition: Condition<*, *>? = null,
    reconcileCondition: Condition<*, *>? = null,
    dependsOn: Set<String> = emptySet(),
) : DependentResourceSpec<R, P, KubernetesDependentResourceConfig<*>>(
        dependentResourceClass,
        name,
        dependsOn,
        readyCondition,
        reconcileCondition,
        null,
        null,
        null,
    )
