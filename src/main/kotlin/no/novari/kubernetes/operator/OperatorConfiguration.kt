package no.novari.kubernetes.operator

import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.client.KubernetesClient
import io.javaoperatorsdk.operator.api.config.BaseConfigurationService
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration
import io.javaoperatorsdk.operator.api.config.ResolvedControllerConfiguration
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceConfigurationResolver
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceSpec
import io.javaoperatorsdk.operator.api.config.workflow.WorkflowSpec
import io.javaoperatorsdk.operator.api.monitoring.Metrics
import io.javaoperatorsdk.operator.api.reconciler.Reconciler
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResourceFactory
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition
import no.novari.kubernetes.operator.dependent.ReadyCondition
import no.novari.kubernetes.operator.dependent.ReconcileCondition
import no.novari.kubernetes.operator.workflow.DependentRef
import no.novari.kubernetes.operator.workflow.Workflow
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.qualifier.named
import java.time.Duration

typealias KCondition = Condition<*, *>

class OperatorConfiguration :
    BaseConfigurationService(),
    OperatorConfigurationOverrider,
    KoinComponent {
    private var metrics: Metrics? = null
    private var kubernetesClient: KubernetesClient? = null
    private var closeClientOnStop: Boolean? = null
    private var reconciliationTerminationTimeout: Duration? = null

    override fun setMetrics(metrics: Metrics) {
        this.metrics = metrics
    }

    override fun setKubernetesClient(client: KubernetesClient) {
        this.kubernetesClient = client
    }

    override fun setCloseClientOnStop(stopClientOnStop: Boolean) {
        this.closeClientOnStop = stopClientOnStop
    }

    override fun setReconciliationTerminationTimeout(timeout: Duration) {
        this.reconciliationTerminationTimeout = timeout
    }

    override fun reconciliationTerminationTimeout(): Duration = reconciliationTerminationTimeout ?: super.reconciliationTerminationTimeout()

    override fun getKubernetesClient(): KubernetesClient = kubernetesClient ?: get()

    override fun closeClientOnStop(): Boolean = closeClientOnStop ?: super.closeClientOnStop()

    override fun getMetrics(): Metrics = metrics ?: get()

    override fun dependentResourceFactory(): DependentResourceFactory<out ControllerConfiguration<*>, out DependentResourceSpec<*, *, *>> =
        KoinDependentResourceFactory()

    override fun <P : HasMetadata> configFor(reconciler: Reconciler<P>): ControllerConfiguration<P> {
        val config = super.configFor(reconciler) as ResolvedControllerConfiguration<P>
        val workflowAnnotation: Workflow? = reconciler::class.java.getAnnotation(Workflow::class.java)
        if (workflowAnnotation != null) {
            val specs = dependentResources(workflowAnnotation, config)
            config.setWorkflowSpec(
                object : WorkflowSpec {
                    override fun getDependentResourceSpecs(): List<DependentResourceSpec<*, *, *>> = specs

                    override fun isExplicitInvocation(): Boolean = true

                    override fun handleExceptionsInReconciler(): Boolean = false
                },
            )
        }
        return config
    }

    @Suppress("UNCHECKED_CAST")
    private fun dependentResources(
        workflow: Workflow,
        controllerConfiguration: ControllerConfiguration<*>,
    ): List<DependentResourceSpec<*, *, *>> {
        val dependents = workflow.dependents
        return dependents
            .map { dependent ->
                val qualifier = dependent.qualifier.takeIf { it.isNotEmpty() }?.let { named(it) }
                val dependentInstance =
                    getKoin()
                        .get<DependentResource<Any, HasMetadata>>(dependent.dependentClass, qualifier)
                val dependentType = dependentInstance.javaClass

                val readyCondition =
                    if (dependentInstance is ReadyCondition<*>) {
                        KCondition { _, p, c ->
                            (dependentInstance as ReadyCondition<HasMetadata>).isReady(p, c)
                        }
                    } else {
                        null
                    }

                val reconcileCondition =
                    if (dependentInstance is ReconcileCondition<*>) {
                        KCondition { _, p, c ->
                            (dependentInstance as ReconcileCondition<HasMetadata>).shouldReconcile(p, c)
                        }
                    } else {
                        null
                    }

                val dependsOn = getDependsOn(dependent.dependsOn)

                KoinDependentResourceSpec(
                    qualifier,
                    dependentType,
                    dependentInstance.name(),
                    readyCondition,
                    reconcileCondition,
                    dependsOn,
                ).also {
                    DependentResourceConfigurationResolver.configureSpecFromConfigured(
                        it,
                        controllerConfiguration,
                        dependentType,
                    )
                }
            }.toList()
    }

    private fun getDependsOn(dependsOn: Array<DependentRef>) =
        dependsOn
            .map { dependentRef ->
                val qualifier = dependentRef.qualifier.takeIf { it.isNotEmpty() }?.let { named(it) }
                val dependent =
                    getKoin()
                        .get<DependentResource<Any, HasMetadata>>(
                            dependentRef.dependentClass,
                            qualifier,
                        )

                dependent.name()
            }.toSet()
}
