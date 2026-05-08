package no.novari.application

import io.fabric8.kubernetes.api.model.ObjectMeta
import io.fabric8.kubernetes.api.model.SecretBuilder
import io.fabric8.kubernetes.client.KubernetesClient
import no.novari.application.api.v1alpha1.FlaisAuthentication
import org.keycloak.admin.client.CreatedResponseUtil
import org.keycloak.admin.client.Keycloak
import org.keycloak.admin.client.KeycloakBuilder
import org.keycloak.representations.idm.ClientRepresentation
import java.util.Base64
import java.util.UUID

const val KEYCLOAK_CLIENT_PROTOCOL = "openid-connect"
const val KEYCLOAK_CLIENT_ENABLED = true
const val KEYCLOAK_CLIENT_PUBLIC = false
const val KEYCLOAK_CLIENT_STANDARD_FLOW_ENABLED = true
const val KEYCLOAK_CLIENT_DIRECT_ACCESS_GRANTS_ENABLED = false
const val KEYCLOAK_CLIENT_SERVICE_ACCOUNTS_ENABLED = false
const val KEYCLOAK_CLIENT_FULL_SCOPE_ALLOWED = false

const val KEYCLOAK_WEB_ORIGIN_SAME_ORIGIN = "+"
const val KEYCLOAK_PKCE_CODE_CHALLENGE_METHOD_ATTRIBUTE = "pkce.code.challenge.method"
const val KEYCLOAK_PKCE_CODE_CHALLENGE_METHOD = "S256"
const val KEYCLOAK_POST_LOGOUT_REDIRECT_URIS_ATTRIBUTE = "post.logout.redirect.uris"
const val KEYCLOAK_POST_LOGOUT_REDIRECT_URIS = "+"

class KeycloakClientService(
    private val kubernetesClient: KubernetesClient,
) {
    fun ensureClient(resource: FlaisAuthentication) {
        adminClient().use { kc ->
            val realm = kc.realm(resource.spec.realm)
            val clientId = clientId(resource)
            val existing = realm.clients().findByClientId(clientId).firstOrNull()

            if (existing == null) {
                val response = realm.clients().create(clientRepresentation(resource, clientId))
                response.use {
                    if (it.status !in 200..299) {
                        error("Failed to create Keycloak client '$clientId': HTTP ${it.status}")
                    }
                    CreatedResponseUtil.getCreatedId(it)
                }
            } else {
                val desired = clientRepresentation(resource, clientId)

                if (needsUpdate(existing, desired)) {
                    realm.clients().get(existing.id).update(desired)
                }
            }
        }
    }

    private fun needsUpdate(
        existing: ClientRepresentation,
        desired: ClientRepresentation,
    ): Boolean =
        existing.name != desired.name ||
            existing.protocol != desired.protocol ||
            existing.isEnabled != desired.isEnabled ||
            existing.isPublicClient != desired.isPublicClient ||
            existing.isStandardFlowEnabled != desired.isStandardFlowEnabled ||
            existing.isDirectAccessGrantsEnabled != desired.isDirectAccessGrantsEnabled ||
            existing.isServiceAccountsEnabled != desired.isServiceAccountsEnabled ||
            existing.isFullScopeAllowed != desired.isFullScopeAllowed ||
            existing.redirectUris.orEmpty().toSet() != desired.redirectUris.orEmpty().toSet() ||
            existing.webOrigins.orEmpty().toSet() != desired.webOrigins.orEmpty().toSet()

    fun deleteClientIfExists(resource: FlaisAuthentication) {
        adminClient().use { kc ->
            val realm = kc.realm(resource.spec.realm)
            val existing = realm.clients().findByClientId(existingClientId(resource)).firstOrNull() ?: return
            realm.clients().get(existing.id).remove()
        }
    }

    fun clientSecret(resource: FlaisAuthentication): String =
        adminClient().use { kc ->
            val realm = kc.realm(resource.spec.realm)
            val clientId = clientId(resource)
            val existing =
                realm.clients().findByClientId(clientId).firstOrNull()
                    ?: error("Keycloak client '$clientId' does not exist in realm '${resource.spec.realm}'")
            realm
                .clients()
                .get(existing.id)
                .secret.value
                ?: error("Keycloak client '$clientId' has no secret")
        }

    fun existingWonderwallClientSecretData(resource: FlaisAuthentication): String? =
        wonderwallSecretResource(resource)
            .get()
            ?.data
            ?.get(WONDERWALL_CLIENT_SECRET_KEY)

    fun syncWonderwallSecretValue(resource: FlaisAuthentication) {
        val secretResource = wonderwallSecretResource(resource)
        val existingSecret = secretResource.get() ?: return
        val clientId = clientId(resource)
        val clientSecret = clientSecret(resource)
        val encodedClientSecret = Base64.getEncoder().encodeToString(clientSecret.toByteArray())

        val desiredAnnotations =
            existingSecret.metadata.annotations.orEmpty() +
                mapOf(WONDERWALL_CLIENT_ID_ANNOTATION to clientId)
        val desiredData =
            existingSecret.data.orEmpty() +
                mapOf(WONDERWALL_CLIENT_SECRET_KEY to encodedClientSecret)

        if (existingSecret.metadata.annotations == desiredAnnotations && existingSecret.data == desiredData) {
            return
        }

        existingSecret.metadata.annotations = desiredAnnotations
        existingSecret.data = desiredData
        secretResource.patch(existingSecret)
    }

    private fun clientRepresentation(
        resource: FlaisAuthentication,
        clientId: String,
    ) = ClientRepresentation().apply {
        this.clientId = clientId
        name = resource.metadata.name
        protocol = KEYCLOAK_CLIENT_PROTOCOL
        isEnabled = KEYCLOAK_CLIENT_ENABLED

        isPublicClient = KEYCLOAK_CLIENT_PUBLIC

        isStandardFlowEnabled = KEYCLOAK_CLIENT_STANDARD_FLOW_ENABLED
        isDirectAccessGrantsEnabled = KEYCLOAK_CLIENT_DIRECT_ACCESS_GRANTS_ENABLED
        isServiceAccountsEnabled = KEYCLOAK_CLIENT_SERVICE_ACCOUNTS_ENABLED
        isFullScopeAllowed = KEYCLOAK_CLIENT_FULL_SCOPE_ALLOWED

        redirectUris = ingressUrl(resource)
        webOrigins = listOf(KEYCLOAK_WEB_ORIGIN_SAME_ORIGIN)
        attributes =
            mapOf(
                KEYCLOAK_PKCE_CODE_CHALLENGE_METHOD_ATTRIBUTE to KEYCLOAK_PKCE_CODE_CHALLENGE_METHOD,
                KEYCLOAK_POST_LOGOUT_REDIRECT_URIS_ATTRIBUTE to KEYCLOAK_POST_LOGOUT_REDIRECT_URIS,
            )
    }

    fun clientId(resource: FlaisAuthentication): String {
        val existingSecret = wonderwallSecretResource(resource).get()
        val existingClientId = existingSecret?.metadata?.annotations?.get(WONDERWALL_CLIENT_ID_ANNOTATION)

        if (!existingClientId.isNullOrBlank()) {
            return existingClientId
        }

        val generatedClientId = UUID.randomUUID().toString()

        if (existingSecret == null) {
            val secret =
                SecretBuilder()
                    .withMetadata(
                        ObjectMeta().apply {
                            name = wonderwallSecretName(resource)
                            namespace = resource.metadata.namespace
                            labels = MANAGED_BY_APPLICATION_LABEL
                            annotations = mapOf(WONDERWALL_CLIENT_ID_ANNOTATION to generatedClientId)
                            ownerReferences = ownerReferences(resource)
                        },
                    ).withType("Opaque")
                    .build()

            kubernetesClient
                .secrets()
                .inNamespace(resource.metadata.namespace)
                .resource(secret)
                .create()
        } else {
            existingSecret.metadata.annotations =
                existingSecret.metadata.annotations.orEmpty() + mapOf(WONDERWALL_CLIENT_ID_ANNOTATION to generatedClientId)
            wonderwallSecretResource(resource).patch(existingSecret)
        }

        return generatedClientId
    }

    fun existingClientId(resource: FlaisAuthentication): String? =
        wonderwallSecretResource(resource)
            .get()
            ?.metadata
            ?.annotations
            ?.get(WONDERWALL_CLIENT_ID_ANNOTATION)
            ?.takeIf { it.isNotBlank() }

    fun ingressUrl(resource: FlaisAuthentication): List<String> =
        resource.spec.ingress.map { ingress ->
            val host = ingress.host.trimEnd('/')
            val base = ingress.path.trim('/')

            if (base.isBlank()) {
                "https://$host/*"
            } else {
                "https://$host/$base/*"
            }
        }

    private fun wonderwallSecretResource(resource: FlaisAuthentication) =
        kubernetesClient
            .secrets()
            .inNamespace(resource.metadata.namespace)
            .withName(wonderwallSecretName(resource))

    private fun adminClient(): Keycloak =
        KeycloakBuilder
            .builder()
            .serverUrl(ApplicationOperatorConfig.keycloakBaseUrl)
            .realm(ApplicationOperatorConfig.keycloakAdminRealm)
            .clientId(ApplicationOperatorConfig.keycloakAdminClientId)
            .username(ApplicationOperatorConfig.keycloakAdminUsername)
            .password(ApplicationOperatorConfig.keycloakAdminPassword)
            .build()
}
