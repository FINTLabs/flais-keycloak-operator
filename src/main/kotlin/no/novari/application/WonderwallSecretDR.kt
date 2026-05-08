package no.novari.application

import io.fabric8.kubernetes.api.model.ObjectMeta
import io.fabric8.kubernetes.api.model.Secret
import io.fabric8.kubernetes.api.model.SecretBuilder
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
class WonderwallSecretDR :
    CRUDKubernetesDependentResource<Secret, FlaisAuthentication>(Secret::class.java),
    KoinComponent {
    private val keycloakClientService: KeycloakClientService by inject()

    override fun name(): String = "wonderwall-secret"

    override fun desired(
        primary: FlaisAuthentication,
        context: Context<FlaisAuthentication>,
    ): Secret {
        val clientId = keycloakClientService.clientId(primary)
        val existingClientSecret = keycloakClientService.existingWonderwallClientSecretData(primary)

        val builder =
            SecretBuilder()
                .withMetadata(
                    ObjectMeta().apply {
                        name = wonderwallSecretName(primary)
                        namespace = primary.metadata.namespace
                        labels = MANAGED_BY_APPLICATION_LABEL
                        annotations = mapOf(WONDERWALL_CLIENT_ID_ANNOTATION to clientId)
                        ownerReferences = ownerReferences(primary)
                    },
                ).withType("Opaque")

        existingClientSecret?.let {
            builder.addToData(WONDERWALL_CLIENT_SECRET_KEY, it)
        }

        return builder.build()
    }
}
