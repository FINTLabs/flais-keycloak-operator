package no.novari.operator.client

import io.fabric8.kubernetes.api.model.ObjectMeta
import io.fabric8.kubernetes.api.model.Secret
import io.fabric8.kubernetes.api.model.SecretBuilder
import io.javaoperatorsdk.operator.api.config.informer.Informer
import io.javaoperatorsdk.operator.api.reconciler.Context
import io.javaoperatorsdk.operator.api.reconciler.dependent.ReconcileResult
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent
import no.novari.OperatorConfig
import no.novari.kubernetes.operator.getSecondaryResource
import no.novari.operator.MANAGED_BY_APPLICATION_LABEL
import no.novari.operator.MANAGED_BY_APPLICATION_SELECTOR
import no.novari.operator.client.api.v1alpha1.FlaisAuthentication
import org.keycloak.representations.idm.ClientRepresentation

@KubernetesDependent(
    informer = Informer(labelSelector = MANAGED_BY_APPLICATION_SELECTOR),
)
class ClientSecretDR :
    CRUDKubernetesDependentResource<Secret, FlaisAuthentication>(
        Secret::class.java,
    ) {
    override fun name(): String = "client-secret"

    override fun reconcile(
        primary: FlaisAuthentication,
        context: Context<FlaisAuthentication>,
    ): ReconcileResult<Secret> {
        val result = super.reconcile(primary, context)
        deleteStaleSecrets(primary, context)
        return result
    }

    override fun desired(
        primary: FlaisAuthentication,
        context: Context<FlaisAuthentication>,
    ): Secret {
        val client = context.getSecondaryResource<ClientRepresentation>() ?: error("Could not retrieve client")
        val builder =
            SecretBuilder()
                .withMetadata(
                    ObjectMeta().apply {
                        name = getSecretName(primary)
                        namespace = primary.metadata.namespace
                        labels = MANAGED_BY_APPLICATION_LABEL
                        ownerReferences = ownerReferences(primary)
                    },
                ).withStringData<String, String>(
                    mapOf(
                        "KEYCLOAK_CLIENT_ID" to client.clientId,
                        "KEYCLOAK_CLIENT_SECRET" to client.secret,
                        "KEYCLOAK_REDIRECT_URI" to client.redirectUris.first(),
                        "KEYCLOAK_WELL_KNOWN_URL" to getWellKnownUrl(primary),
                    ),
                )

        return builder.build()
    }

    private fun getWellKnownUrl(primary: FlaisAuthentication): String =
        "${OperatorConfig.keycloakBaseUrl}/realms/${primary.spec.realm}/.well-known/openid-configuration"

    private fun deleteStaleSecrets(
        primary: FlaisAuthentication,
        context: Context<FlaisAuthentication>,
    ) {
        val desiredName = getSecretName(primary)

        context
            .getSecondaryResources(Secret::class.java)
            .filter { it.metadata.name != desiredName }
            .forEach { context.client.resource(it).delete() }
    }

    private fun getSecretName(primary: FlaisAuthentication): String = primary.spec.secretName ?: primary.metadata.name
}
