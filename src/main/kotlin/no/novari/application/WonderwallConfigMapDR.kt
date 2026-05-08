package no.novari.application

import io.fabric8.kubernetes.api.model.ConfigMap
import io.fabric8.kubernetes.api.model.ConfigMapBuilder
import io.fabric8.kubernetes.api.model.ObjectMeta
import io.javaoperatorsdk.operator.api.config.informer.Informer
import io.javaoperatorsdk.operator.api.reconciler.Context
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent
import no.novari.application.api.v1alpha1.FlaisAuthentication
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@KubernetesDependent(
    informer = Informer(labelSelector = MANAGED_BY_APPLICATION_SELECTOR),
)
class WonderwallConfigMapDR :
    CRUDKubernetesDependentResource<ConfigMap, FlaisAuthentication>(ConfigMap::class.java),
    KoinComponent {
    private val keycloakClientService: KeycloakClientService by inject()

    override fun name(): String = "wonderwall-config"

    override fun desired(
        primary: FlaisAuthentication,
        context: Context<FlaisAuthentication>,
    ): ConfigMap =
        ConfigMapBuilder()
            .withMetadata(
                ObjectMeta().apply {
                    name = wonderwallConfigMapName(primary)
                    namespace = primary.metadata.namespace
                    labels = MANAGED_BY_APPLICATION_LABEL
                    ownerReferences = ownerReferences(primary)
                },
            ).withData<String, String>(wonderwallConfig(primary))
            .build()

    private fun wonderwallConfig(primary: FlaisAuthentication): Map<String, String> =
        mapOf(
            "WONDERWALL_OPENID_CLIENT_ID" to keycloakClientService.clientId(primary),
            "WONDERWALL_OPENID_WELL_KNOWN_URL" to wellKnownUrl(primary),
            "WONDERWALL_INGRESS" to ingressUrl(primary),
            "WONDERWALL_UPSTREAM_PORT" to "${primary.spec.wonderwall.upstreamPort}",
            "WONDERWALL_BIND_ADDRESS" to "0.0.0.0:8080",
            "WONDERWALL_AUTO_LOGIN" to "${primary.spec.wonderwall.autoLogin}",
            "WONDERWALL_OPENID_SCOPES" to openidScopes(primary),
        )

    private fun wellKnownUrl(primary: FlaisAuthentication): String {
        val baseUrl = ApplicationOperatorConfig.keycloakBaseUrl
        return "$baseUrl/realms/${primary.spec.realm}/.well-known/openid-configuration"
    }

    private fun ingressUrl(primary: FlaisAuthentication): String =
        primary.spec.ingress.joinToString(",") { ingress ->
            "https://${ingress.host.trimEnd('/')}/${ingress.path.trim('/')}"
        }

    private fun openidScopes(primary: FlaisAuthentication): String =
        primary.spec.wonderwall.scope
            .joinToString(",")
}
