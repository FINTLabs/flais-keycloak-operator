package no.novari.operator

import io.javaoperatorsdk.operator.api.config.ControllerConfiguration
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceSpec
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResourceFactory
import org.koin.core.component.KoinComponent
import org.koin.core.qualifier.Qualifier
import kotlin.reflect.KClass

class KoinDependentResourceFactory<
        C : ControllerConfiguration<*>,
        D : DependentResourceSpec<*, *, *>,
        > :
    DependentResourceFactory<C, D>,
    KoinComponent {

    private val knownDependents =
        mutableMapOf<Pair<KClass<*>, Qualifier?>, DependentResource<*, *>>()

    override fun createFrom(
        spec: D,
        controllerConfiguration: C,
    ): DependentResource<*, *> {
        val koinSpec = spec as? KoinDependentResourceSpec<*, *>
            ?: throw IllegalStateException(
                "${spec.javaClass.canonicalName} cannot be instantiated. Not KoinDependentResourceSpec",
            )

        val clazz: KClass<*> = koinSpec.dependentResourceClass.kotlin
        val qualifier: Qualifier? = koinSpec.qualifier
        val key = clazz to qualifier

        return knownDependents.getOrPut(key) {
            val dependent =
                getKoin().get<Any>(clazz = clazz, qualifier = qualifier) as DependentResource<*, *>

            configure(dependent, spec, controllerConfiguration)

            dependent
        }
    }
}