package no.novari.keycloak

import no.novari.keycloak.api.model.KEYCLOAK_ACCESS_TOKEN_LIFESPAN_ATTRIBUTE
import no.novari.keycloak.api.model.KEYCLOAK_CLIENT_DIRECT_ACCESS_GRANTS_ENABLED
import no.novari.keycloak.api.model.KEYCLOAK_CLIENT_ENABLED
import no.novari.keycloak.api.model.KEYCLOAK_CLIENT_FULL_SCOPE_ALLOWED
import no.novari.keycloak.api.model.KEYCLOAK_CLIENT_PROTOCOL
import no.novari.keycloak.api.model.KEYCLOAK_CLIENT_PUBLIC
import no.novari.keycloak.api.model.KEYCLOAK_CLIENT_SERVICE_ACCOUNTS_ENABLED
import no.novari.keycloak.api.model.KEYCLOAK_CLIENT_STANDARD_FLOW_ENABLED
import no.novari.keycloak.api.model.KEYCLOAK_PKCE_CODE_CHALLENGE_METHOD
import no.novari.keycloak.api.model.KEYCLOAK_PKCE_CODE_CHALLENGE_METHOD_ATTRIBUTE
import no.novari.keycloak.api.model.KEYCLOAK_POST_LOGOUT_REDIRECT_URIS
import no.novari.keycloak.api.model.KEYCLOAK_POST_LOGOUT_REDIRECT_URIS_ATTRIBUTE
import no.novari.keycloak.api.model.KEYCLOAK_SESSION_IDLE_TIMEOUT_ATTRIBUTE
import no.novari.keycloak.api.model.KEYCLOAK_SESSION_LIFESPAN_ATTRIBUTE
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.keycloak.representations.idm.ClientRepresentation

class ClientRepresentationComparatorTest {
    @Test
    fun `matches ignores unmanaged fields and unordered uri lists`() {
        val desired =
            clientRepresentation(
                redirectUris = listOf("https://app.example.no/callback", "https://app.example.no/logout"),
                webOrigins = listOf("https://app.example.no", "+"),
                attributes = managedAttributes(),
            )
        val actual =
            clientRepresentation(
                clientId = "server-client-id",
                redirectUris = listOf("https://app.example.no/logout", "https://app.example.no/callback"),
                webOrigins = listOf("+", "https://app.example.no"),
                attributes = managedAttributes() + mapOf("keycloak-managed" to "value"),
            )

        assertTrue(ClientRepresentationComparator.matches(actual, desired))
    }

    @Test
    fun `does not match when managed attributes differ`() {
        val desired =
            clientRepresentation(
                attributes = managedAttributes(),
            )
        val actual =
            clientRepresentation(
                attributes =
                    managedAttributes() +
                        mapOf(KEYCLOAK_ACCESS_TOKEN_LIFESPAN_ATTRIBUTE to "120"),
            )

        assertFalse(ClientRepresentationComparator.matches(actual, desired))
    }

    @Test
    fun `does not match when managed attribute is only present in actual`() {
        val desired =
            clientRepresentation(
                attributes = managedAttributes() - KEYCLOAK_ACCESS_TOKEN_LIFESPAN_ATTRIBUTE,
            )
        val actual =
            clientRepresentation(
                attributes = managedAttributes(),
            )

        assertFalse(ClientRepresentationComparator.matches(actual, desired))
    }

    @Test
    fun `does not match when owned client setting differs`() {
        val desired = clientRepresentation()
        val actual =
            clientRepresentation().apply {
                isStandardFlowEnabled = !KEYCLOAK_CLIENT_STANDARD_FLOW_ENABLED
            }

        assertFalse(ClientRepresentationComparator.matches(actual, desired))
    }

    private fun clientRepresentation(
        clientId: String? = null,
        redirectUris: List<String> = listOf("https://app.example.no/callback"),
        webOrigins: List<String> = listOf("https://app.example.no"),
        attributes: Map<String, String> = managedAttributes(),
    ): ClientRepresentation =
        ClientRepresentation().apply {
            this.clientId = clientId
            name = "client-name"
            protocol = KEYCLOAK_CLIENT_PROTOCOL
            isEnabled = KEYCLOAK_CLIENT_ENABLED
            isPublicClient = KEYCLOAK_CLIENT_PUBLIC
            isStandardFlowEnabled = KEYCLOAK_CLIENT_STANDARD_FLOW_ENABLED
            isDirectAccessGrantsEnabled = KEYCLOAK_CLIENT_DIRECT_ACCESS_GRANTS_ENABLED
            isServiceAccountsEnabled = KEYCLOAK_CLIENT_SERVICE_ACCOUNTS_ENABLED
            isFullScopeAllowed = KEYCLOAK_CLIENT_FULL_SCOPE_ALLOWED
            this.redirectUris = redirectUris
            this.webOrigins = webOrigins
            this.attributes = attributes
        }

    private companion object {
        fun managedAttributes(): Map<String, String> =
            mapOf(
                KEYCLOAK_PKCE_CODE_CHALLENGE_METHOD_ATTRIBUTE to KEYCLOAK_PKCE_CODE_CHALLENGE_METHOD,
                KEYCLOAK_POST_LOGOUT_REDIRECT_URIS_ATTRIBUTE to KEYCLOAK_POST_LOGOUT_REDIRECT_URIS,
                KEYCLOAK_ACCESS_TOKEN_LIFESPAN_ATTRIBUTE to "60",
                KEYCLOAK_SESSION_LIFESPAN_ATTRIBUTE to "3600",
                KEYCLOAK_SESSION_IDLE_TIMEOUT_ATTRIBUTE to "900",
            )
    }
}
