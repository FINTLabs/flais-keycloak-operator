package no.novari.application

import io.fabric8.kubernetes.api.model.IntOrString
import io.fabric8.kubernetes.api.model.ObjectMeta
import io.javaoperatorsdk.operator.api.config.informer.Informer
import io.javaoperatorsdk.operator.api.reconciler.Context
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent
import no.novari.application.api.v1alpha1.Application
import us.containo.traefik.v1alpha1.IngressRoute
import us.containo.traefik.v1alpha1.IngressRouteSpec
import us.containo.traefik.v1alpha1.ingressroutespec.Routes
import us.containo.traefik.v1alpha1.ingressroutespec.routes.Services

@KubernetesDependent(
    informer = Informer(labelSelector = MANAGED_BY_APPLICATION_SELECTOR),
)
class TraefikIngressRouteDR : CRUDKubernetesDependentResource<IngressRoute, Application>(IngressRoute::class.java) {
    companion object {
        const val RESOURCE_NAME = "traefik-ingress-route"
        const val ENTRYPOINT_WEB = "web"
        const val SERVICE_PORT = 80
    }

    override fun name(): String = RESOURCE_NAME

    override fun desired(
        primary: Application,
        context: Context<Application>,
    ): IngressRoute =
        IngressRoute().apply {
            metadata =
                ObjectMeta().apply {
                    name = primary.metadata.name
                    namespace = primary.metadata.namespace
                    labels = MANAGED_BY_APPLICATION_LABEL
                    ownerReferences = ownerReferences(primary)
                }

            spec =
                IngressRouteSpec().apply {
                    entryPoints = listOf(ENTRYPOINT_WEB)
                    routes =
                        listOf(
                            Routes().apply {
                                kind = Routes.Kind.RULE
                                match = ingressRouteMatch(primary)
                                services =
                                    listOf(
                                        Services().apply {
                                            kind = Services.Kind.SERVICE
                                            name = wonderwallSecretName(primary)
                                            port = IntOrString(SERVICE_PORT)
                                        },
                                    )
                            },
                        )
                }
        }

    private fun ingressRouteMatch(primary: Application): String =
        "Host(`${primary.spec.hostname.trimEnd('/')}`) && PathPrefix(`${pathPrefix(primary.spec.basePath)}`)"

    private fun pathPrefix(basePath: String): String {
        val normalized = basePath.trim('/')
        return if (normalized.isBlank()) "/" else "/$normalized"
    }
}
