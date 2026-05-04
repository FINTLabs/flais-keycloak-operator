package no.novari.application

import io.fabric8.kubernetes.api.model.IntOrString
import io.fabric8.kubernetes.api.model.ObjectMeta
import io.fabric8.kubernetes.api.model.Service
import io.fabric8.kubernetes.api.model.ServicePort
import io.fabric8.kubernetes.api.model.ServiceSpec
import io.javaoperatorsdk.operator.api.config.informer.Informer
import io.javaoperatorsdk.operator.api.reconciler.Context
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent
import no.novari.application.api.v1alpha1.Application

@KubernetesDependent(
    informer = Informer(labelSelector = MANAGED_BY_APPLICATION_SELECTOR),
)
class WonderwallServiceDR : CRUDKubernetesDependentResource<Service, Application>(Service::class.java) {
    override fun name(): String = "wonderwall-service"

    override fun desired(
        primary: Application,
        context: Context<Application>,
    ): Service {
        val namespace = primary.metadata.namespace
        val deploymentName = primary.metadata.name

        val deployment =
            context.client
                .apps()
                .deployments()
                .inNamespace(namespace)
                .withName(deploymentName)
                .get()
                ?: error("Deployment '$deploymentName' not found in namespace '$namespace'")

        val selector =
            deployment.spec
                ?.selector
                ?.matchLabels
                ?.takeIf { it.isNotEmpty() }
                ?: error("Deployment '$deploymentName' has no selector.matchLabels")

        return Service().apply {
            metadata =
                ObjectMeta().apply {
                    name = "${primary.metadata.name}-wonderwall"
                    labels = MANAGED_BY_APPLICATION_LABEL
                    this.namespace = primary.metadata.namespace
                    ownerReferences = ownerReferences(primary)
                }

            spec =
                ServiceSpec().apply {
                    this.selector = selector
                    ports =
                        listOf(
                            ServicePort().apply {
                                name = "http"
                                port = 80
                                protocol = "TCP"
                                targetPort = IntOrString("http")
                            },
                        )
                }
        }
    }
}
