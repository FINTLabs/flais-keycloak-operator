package no.novari.kubernetes.operator.workflow

import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource
import org.koin.core.qualifier.QualifierValue
import kotlin.reflect.KClass

annotation class Dependent(
    val dependentClass: KClass<out DependentResource<*, *>>,
    val qualifier: QualifierValue = "",
    val dependsOn: Array<DependentRef> = [],
)
