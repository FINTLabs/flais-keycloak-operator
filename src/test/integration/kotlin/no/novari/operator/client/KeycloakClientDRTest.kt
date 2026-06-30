package no.novari.operator.client

import io.fabric8.kubernetes.client.KubernetesClient
import no.novari.environment.OperatorEnvironment
import no.novari.environment.OperatorEnvironmentExtension
import no.novari.fixture.IntegrationTestSupport
import no.novari.keycloak.KEYCLOAK_ACCESS_TOKEN_LIFESPAN_ATTRIBUTE
import no.novari.keycloak.KEYCLOAK_CLIENT_DIRECT_ACCESS_GRANTS_ENABLED
import no.novari.keycloak.KEYCLOAK_CLIENT_ENABLED
import no.novari.keycloak.KEYCLOAK_CLIENT_FULL_SCOPE_ALLOWED
import no.novari.keycloak.KEYCLOAK_CLIENT_PROTOCOL
import no.novari.keycloak.KEYCLOAK_CLIENT_PUBLIC
import no.novari.keycloak.KEYCLOAK_CLIENT_SERVICE_ACCOUNTS_ENABLED
import no.novari.keycloak.KEYCLOAK_CLIENT_STANDARD_FLOW_ENABLED
import no.novari.keycloak.KEYCLOAK_PKCE_CODE_CHALLENGE_METHOD
import no.novari.keycloak.KEYCLOAK_PKCE_CODE_CHALLENGE_METHOD_ATTRIBUTE
import no.novari.keycloak.KEYCLOAK_POST_LOGOUT_REDIRECT_URIS_ATTRIBUTE
import no.novari.keycloak.KEYCLOAK_SESSION_IDLE_TIMEOUT_ATTRIBUTE
import no.novari.keycloak.KEYCLOAK_SESSION_LIFESPAN_ATTRIBUTE
import no.novari.keycloak.KeycloakClientNameGenerator
import no.novari.keycloak.client.KcAdminClient
import no.novari.operator.client.api.v1alpha1.FlaisAuthentication
import no.novari.operator.client.api.v1alpha1.FlaisAuthenticationSpec
import org.awaitility.Awaitility.await
import org.awaitility.kotlin.withPollInterval
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.keycloak.representations.idm.ClientRepresentation
import java.time.Duration
import java.util.UUID

private const val REALM = "fint"

@ExtendWith(OperatorEnvironmentExtension::class)
class KeycloakClientDRTest {
    @Test
    fun `reconcile new application creates client in keycloak with correct data`(
        env: OperatorEnvironment,
        kubernetesClient: KubernetesClient,
    ) {
        val support = IntegrationTestSupport(kubernetesClient)
        val name = support.uniqueName("keycloak-client")
        val clientURI = "https://$name.apps.example.no"
        val redirectURIs =
            listOf(
                "$clientURI/oauth/callback",
                "$clientURI/silent-renew",
            )
        val postLogoutRedirectURIs =
            listOf(
                "$clientURI/logout",
                "$clientURI/signed-out",
            )
        val expectedClientName = expectedClientName(name)

        support.applyApplication(
            name = name,
            spec =
                FlaisAuthenticationSpec(
                    realm = REALM,
                    clientURI = clientURI,
                    redirectURIs = redirectURIs,
                    postLogoutRedirectURIs = postLogoutRedirectURIs,
                    accessTokenLifetime = 300,
                    sessionLifetime = 3600,
                    sessionIdleTimeout = 600,
                ),
        )

        val (kc, realmResource) = KcAdminClient.connect(env, REALM)

        kc.use {
            await()
                .withPollInterval(Duration.ofSeconds(1))
                .atMost(Duration.ofSeconds(90))
                .untilAsserted {
                    val resource = getFlaisAuthentication(kubernetesClient, name)
                    val readyCondition =
                        resource.status
                            ?.conditions
                            ?.single { condition -> condition.type == "Ready" }

                    val clientId = resource.status?.clientID ?: error("Expected client ID")
                    assertEquals(resource.metadata.uid, clientId)
                    assertValidUuid(clientId)
                    assertEquals("True", readyCondition?.status)

                    val clientRepresentation =
                        KcAdminClient.getClientRepresentation(realmResource, clientId)
                            ?: error("Expected Keycloak client '$clientId'")
                    assertClient(
                        clientRepresentation = clientRepresentation,
                        clientId = clientId,
                        name = expectedClientName,
                        clientURI = clientURI,
                        redirectURIs = redirectURIs,
                        postLogoutRedirectURIs = postLogoutRedirectURIs,
                        accessTokenLifetime = 300,
                        sessionLifetime = 3600,
                        sessionIdleTimeout = 600,
                    )
                }
        }
    }

    @Test
    fun `reconcile updated application updates existing keycloak client`(
        env: OperatorEnvironment,
        kubernetesClient: KubernetesClient,
    ) {
        val support = IntegrationTestSupport(kubernetesClient)
        val name = support.uniqueName("keycloak-update")
        val initialClientURI = "https://$name.initial.example.no"
        val updatedClientURI = "https://$name.updated.example.no"
        val expectedClientName = expectedClientName(name)
        var createdClientId: String? = null

        support.applyApplication(
            name = name,
            spec =
                FlaisAuthenticationSpec(
                    realm = REALM,
                    clientURI = initialClientURI,
                    redirectURIs = listOf("$initialClientURI/callback"),
                    accessTokenLifetime = 120,
                ),
        )

        val (kc, realmResource) = KcAdminClient.connect(env, REALM)

        kc.use {
            await()
                .withPollInterval(Duration.ofSeconds(1))
                .atMost(Duration.ofSeconds(90))
                .untilAsserted {
                    val resource = getFlaisAuthentication(kubernetesClient, name)
                    val clientId = resource.status?.clientID ?: error("Expected client ID")
                    assertEquals(resource.metadata.uid, clientId)
                    assertValidUuid(clientId)
                    createdClientId = clientId

                    val clientRepresentation =
                        KcAdminClient.getClientRepresentation(realmResource, clientId)
                            ?: error("Expected Keycloak client '$clientId'")
                    assertEquals(setOf("$initialClientURI/callback"), clientRepresentation.redirectUris.orEmpty().toSet())
                }

            val clientId = createdClientId ?: error("Expected client ID")
            val updatedRedirectURIs =
                listOf(
                    "$updatedClientURI/oauth/callback",
                    "$updatedClientURI/silent-renew",
                )
            val updatedPostLogoutRedirectURIs = listOf("$updatedClientURI/logout")

            support.applyApplication(
                name = name,
                spec =
                    FlaisAuthenticationSpec(
                        realm = REALM,
                        clientURI = updatedClientURI,
                        redirectURIs = updatedRedirectURIs,
                        postLogoutRedirectURIs = updatedPostLogoutRedirectURIs,
                        accessTokenLifetime = 600,
                        sessionLifetime = 7200,
                        sessionIdleTimeout = 900,
                    ),
            )

            await()
                .withPollInterval(Duration.ofSeconds(1))
                .atMost(Duration.ofSeconds(90))
                .untilAsserted {
                    val resource = getFlaisAuthentication(kubernetesClient, name)
                    assertEquals(resource.metadata.uid, clientId)
                    assertEquals(clientId, resource.status?.clientID)

                    val clientRepresentation =
                        KcAdminClient.getClientRepresentation(realmResource, clientId)
                            ?: error("Expected Keycloak client '$clientId'")
                    assertClient(
                        clientRepresentation = clientRepresentation,
                        clientId = clientId,
                        name = expectedClientName,
                        clientURI = updatedClientURI,
                        redirectURIs = updatedRedirectURIs,
                        postLogoutRedirectURIs = updatedPostLogoutRedirectURIs,
                        accessTokenLifetime = 600,
                        sessionLifetime = 7200,
                        sessionIdleTimeout = 900,
                    )
                }
        }
    }

    private fun assertClient(
        clientRepresentation: ClientRepresentation,
        clientId: String,
        name: String,
        clientURI: String,
        redirectURIs: List<String>,
        postLogoutRedirectURIs: List<String>,
        accessTokenLifetime: Int,
        sessionLifetime: Int,
        sessionIdleTimeout: Int,
    ) {
        assertEquals(clientId, clientRepresentation.clientId)
        assertEquals(name, clientRepresentation.name)
        assertEquals(KEYCLOAK_CLIENT_PROTOCOL, clientRepresentation.protocol)
        assertEquals(KEYCLOAK_CLIENT_ENABLED, clientRepresentation.isEnabled)
        assertEquals(KEYCLOAK_CLIENT_PUBLIC, clientRepresentation.isPublicClient)
        assertEquals(KEYCLOAK_CLIENT_STANDARD_FLOW_ENABLED, clientRepresentation.isStandardFlowEnabled)
        assertEquals(KEYCLOAK_CLIENT_DIRECT_ACCESS_GRANTS_ENABLED, clientRepresentation.isDirectAccessGrantsEnabled)
        assertEquals(KEYCLOAK_CLIENT_SERVICE_ACCOUNTS_ENABLED, clientRepresentation.isServiceAccountsEnabled)
        assertEquals(KEYCLOAK_CLIENT_FULL_SCOPE_ALLOWED, clientRepresentation.isFullScopeAllowed)
        assertEquals(redirectURIs.toSet(), clientRepresentation.redirectUris.orEmpty().toSet())
        assertEquals(setOf(clientURI), clientRepresentation.webOrigins.orEmpty().toSet())
        assertEquals(KEYCLOAK_PKCE_CODE_CHALLENGE_METHOD, clientRepresentation.attributes[KEYCLOAK_PKCE_CODE_CHALLENGE_METHOD_ATTRIBUTE])
        assertEquals(
            postLogoutRedirectURIs.joinToString("##"),
            clientRepresentation.attributes[KEYCLOAK_POST_LOGOUT_REDIRECT_URIS_ATTRIBUTE],
        )
        assertEquals(accessTokenLifetime.toString(), clientRepresentation.attributes[KEYCLOAK_ACCESS_TOKEN_LIFESPAN_ATTRIBUTE])
        assertEquals(sessionLifetime.toString(), clientRepresentation.attributes[KEYCLOAK_SESSION_LIFESPAN_ATTRIBUTE])
        assertEquals(sessionIdleTimeout.toString(), clientRepresentation.attributes[KEYCLOAK_SESSION_IDLE_TIMEOUT_ATTRIBUTE])
    }

    private fun getFlaisAuthentication(
        kubernetesClient: KubernetesClient,
        name: String,
    ): FlaisAuthentication =
        kubernetesClient
            .resources(FlaisAuthentication::class.java)
            .withName(name)
            .get()

    private fun assertValidUuid(value: String) {
        UUID.fromString(value)
    }

    private fun expectedClientName(name: String): String =
        KeycloakClientNameGenerator.generate(
            team = "team-platform",
            name = name,
            orgId = "novari_no",
        )
}
