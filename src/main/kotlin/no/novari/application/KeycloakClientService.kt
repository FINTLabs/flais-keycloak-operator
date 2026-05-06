package no.novari.application

import no.novari.application.api.v1alpha1.FlaisAuthentication
import org.keycloak.admin.client.CreatedResponseUtil
import org.keycloak.admin.client.Keycloak
import org.keycloak.admin.client.KeycloakBuilder
import org.keycloak.representations.idm.ClientRepresentation

class KeycloakClientService {
    fun ensureClient(resource: FlaisAuthentication) {
        adminClient().use { kc ->
            val realm = kc.realm(resource.spec.realm)
            val clientId = clientId(resource)
            val existing = realm.clients().findByClientId(clientId).firstOrNull()

            if (existing == null) {
                val response = realm.clients().create(clientRepresentation(resource))
                response.use {
                    if (it.status !in 200..299) {
                        error("Failed to create Keycloak client '$clientId': HTTP ${it.status}")
                    }
                    CreatedResponseUtil.getCreatedId(it)
                }
            } else {
                val updated =
                    clientRepresentation(resource).apply {
                        id = existing.id
                    }

                realm.clients().get(existing.id).update(updated)
            }
        }
    }

    fun deleteClientIfExists(resource: FlaisAuthentication) {
        adminClient().use { kc ->
            val realm = kc.realm(resource.spec.realm)
            val existing = realm.clients().findByClientId(clientId(resource)).firstOrNull() ?: return
            realm.clients().get(existing.id).remove()
        }
    }

    fun clientSecret(resource: FlaisAuthentication): String =
        adminClient().use { kc ->
            val realm = kc.realm(resource.spec.realm)
            val existing =
                realm.clients().findByClientId(clientId(resource)).firstOrNull()
                    ?: error("Keycloak client '${clientId(resource)}' does not exist in realm '${resource.spec.realm}'")
            realm
                .clients()
                .get(existing.id)
                .secret.value
                ?: error("Keycloak client '${clientId(resource)}' has no secret")
        }

    private fun clientRepresentation(resource: FlaisAuthentication) =
        ClientRepresentation().apply {
            clientId = clientId(resource)
            name = clientId(resource)
            protocol = "openid-connect"
            isEnabled = true

            isPublicClient = false

            isStandardFlowEnabled = true
            isDirectAccessGrantsEnabled = false
            isServiceAccountsEnabled = false
            isFullScopeAllowed = false

            redirectUris = listOf(ingressUrl(resource) + "/*")
            webOrigins = listOf("+")
            attributes =
                mapOf(
                    "pkce.code.challenge.method" to "S256",
                    "post.logout.redirect.uris" to "+",
                )
        }

    fun clientId(resource: FlaisAuthentication): String = resource.metadata.name

    fun ingressUrl(resource: FlaisAuthentication): String = "https://${resource.spec.hostname.trimEnd('/')}/${resource.spec.basePath.trim('/')}"

    private fun adminClient(): Keycloak =
        KeycloakBuilder
            .builder()
            .serverUrl(requiredEnv("KEYCLOAK_BASE_URL"))
            .realm(System.getenv("KEYCLOAK_ADMIN_REALM") ?: "master")
            .clientId(System.getenv("KEYCLOAK_ADMIN_CLIENT_ID") ?: "admin-cli")
            .username(requiredEnv("KEYCLOAK_ADMIN_USERNAME"))
            .password(requiredEnv("KEYCLOAK_ADMIN_PASSWORD"))
            .build()

    private fun requiredEnv(name: String): String =
        System.getenv(name)
            ?: System.getProperty(name)
            ?: error("Missing env var or system property: $name")
}
