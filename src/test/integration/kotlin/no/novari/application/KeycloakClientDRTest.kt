package no.novari.application

import io.fabric8.kubernetes.client.KubernetesClient
import no.novari.application.api.v1alpha1.FlaisAuthentication
import no.novari.application.api.v1alpha1.FlaisAuthenticationSpec
import no.novari.application.api.v1alpha1.Ingress
import no.novari.application.api.v1alpha1.WonderwallConfig
import no.novari.client.KcAdminClient
import no.novari.environment.OperatorEnvironment
import no.novari.environment.OperatorEnvironmentExtension
import no.novari.fixture.IntegrationTestSupport
import org.awaitility.Awaitility.await
import org.awaitility.kotlin.withPollInterval
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.Duration
import kotlin.collections.orEmpty
import kotlin.collections.toSet

@ExtendWith(OperatorEnvironmentExtension::class)
class KeycloakClientDRTest {
    @Test
    fun `reconcile new application creates client in keycloak with correct data`(
        env: OperatorEnvironment,
        kubernetesClient: KubernetesClient,
    ) {
        val support = IntegrationTestSupport(kubernetesClient)
        val name = support.uniqueName("reconcile")
        val host = "samtykke.novari.no"
        val base = "beta/rogfk-no"
        val realm = "fint"

        support.applyApplication(
            name = name,
            spec =
                FlaisAuthenticationSpec(
                    ingress = listOf(Ingress(host, base)),
                    wonderwall = WonderwallConfig(upstreamPort = 8081),
                    realm = "fint",
                ),
        )

        val (kc, realmResource) = KcAdminClient.connect(env, realm)

        kc.use {
            await()
                .withPollInterval(Duration.ofSeconds(1))
                .atMost(Duration.ofSeconds(60))
                .untilAsserted {
                    val secret =
                        kubernetesClient
                            .secrets()
                            .withName("$name-wonderwall")
                            .get()

                    assertNotNull(secret)

                    val clientRepresentation =
                        KcAdminClient.getClientRepresentation(
                            realmResource,
                            secret.metadata.annotations[WONDERWALL_CLIENT_ID_ANNOTATION]!!,
                        )
                    assertNotNull(clientRepresentation)

                    assertEquals(name, clientRepresentation!!.name)
                    assertEquals(KEYCLOAK_CLIENT_PROTOCOL, clientRepresentation.protocol)
                    assertEquals(KEYCLOAK_CLIENT_ENABLED, clientRepresentation.isEnabled)
                    assertEquals(KEYCLOAK_CLIENT_PUBLIC, clientRepresentation.isPublicClient)
                    assertEquals(KEYCLOAK_CLIENT_STANDARD_FLOW_ENABLED, clientRepresentation.isStandardFlowEnabled)
                    assertEquals(KEYCLOAK_CLIENT_DIRECT_ACCESS_GRANTS_ENABLED, clientRepresentation.isDirectAccessGrantsEnabled)
                    assertEquals(KEYCLOAK_CLIENT_SERVICE_ACCOUNTS_ENABLED, clientRepresentation.isServiceAccountsEnabled)
                    assertEquals(KEYCLOAK_CLIENT_FULL_SCOPE_ALLOWED, clientRepresentation.isFullScopeAllowed)
                    assertEquals(setOf("https://$host/$base/*"), clientRepresentation.redirectUris.orEmpty().toSet())
                    assertEquals(setOf(KEYCLOAK_WEB_ORIGIN_SAME_ORIGIN), clientRepresentation.webOrigins.orEmpty().toSet())
                    assertEquals(
                        KEYCLOAK_PKCE_CODE_CHALLENGE_METHOD,
                        clientRepresentation.attributes.orEmpty()[KEYCLOAK_PKCE_CODE_CHALLENGE_METHOD_ATTRIBUTE],
                    )
                    assertEquals(
                        KEYCLOAK_POST_LOGOUT_REDIRECT_URIS,
                        clientRepresentation.attributes.orEmpty()[KEYCLOAK_POST_LOGOUT_REDIRECT_URIS_ATTRIBUTE],
                    )
                }
        }
    }

    @Test
    fun `reconcile application with multiple ingresses adds multiple redirect uris in keycloak client`(
        env: OperatorEnvironment,
        kubernetesClient: KubernetesClient,
    ) {
        val support = IntegrationTestSupport(kubernetesClient)
        val name = support.uniqueName("multiple-ingress")
        val realm = "fint"
        val ingress =
            listOf(
                Ingress("samtykke.novari.no", "beta/rogfk-no"),
                Ingress("minside.novari.no", "prod/innlandet-no"),
                Ingress("api.novari.no", "/"),
            )

        support.applyApplication(
            name = name,
            spec =
                FlaisAuthenticationSpec(
                    ingress = ingress,
                    wonderwall = WonderwallConfig(upstreamPort = 8081),
                    realm = realm,
                ),
        )

        val (kc, realmResource) = KcAdminClient.connect(env, realm)

        kc.use {
            await()
                .withPollInterval(Duration.ofSeconds(1))
                .atMost(Duration.ofSeconds(60))
                .untilAsserted {
                    val secret =
                        kubernetesClient
                            .secrets()
                            .withName("$name-wonderwall")
                            .get()

                    assertNotNull(secret)

                    val clientRepresentation =
                        KcAdminClient.getClientRepresentation(
                            realmResource,
                            secret.metadata.annotations[WONDERWALL_CLIENT_ID_ANNOTATION]!!,
                        )

                    assertNotNull(clientRepresentation)
                    assertEquals(
                        setOf(
                            "https://samtykke.novari.no/beta/rogfk-no/*",
                            "https://minside.novari.no/prod/innlandet-no/*",
                            "https://api.novari.no/*",
                        ),
                        clientRepresentation!!.redirectUris.orEmpty().toSet(),
                    )
                }
        }
    }

    @Test
    fun `deleting application cleans up keycloak client`(
        env: OperatorEnvironment,
        kubernetesClient: KubernetesClient,
    ) {
        val support = IntegrationTestSupport(kubernetesClient)
        val name = support.uniqueName("delete-application")
        val host = "samtykke.novari.no"
        val base = "beta/rogfk-no"
        val realm = "fint"

        support.applyApplication(
            name = name,
            spec =
                FlaisAuthenticationSpec(
                    ingress = listOf(Ingress(host, base)),
                    wonderwall = WonderwallConfig(upstreamPort = 8081),
                    realm = realm,
                ),
        )

        val (kc, realmResource) = KcAdminClient.connect(env, realm)

        kc.use {
            var clientId: String? = null

            await()
                .withPollInterval(Duration.ofSeconds(1))
                .atMost(Duration.ofSeconds(60))
                .untilAsserted {
                    val secret =
                        kubernetesClient
                            .secrets()
                            .withName("$name-wonderwall")
                            .get()

                    assertNotNull(secret)

                    clientId = secret.metadata.annotations[WONDERWALL_CLIENT_ID_ANNOTATION]
                    assertNotNull(clientId)
                    assertNotNull(KcAdminClient.getClientRepresentation(realmResource, clientId!!))
                }

            kubernetesClient
                .resources(FlaisAuthentication::class.java)
                .withName(name)
                .delete()

            await()
                .withPollInterval(Duration.ofSeconds(1))
                .atMost(Duration.ofSeconds(60))
                .untilAsserted {
                    assertNull(
                        kubernetesClient
                            .resources(FlaisAuthentication::class.java)
                            .withName(name)
                            .get(),
                    )
                    assertNull(
                        kubernetesClient
                            .configMaps()
                            .withName("$name-wonderwall")
                            .get(),
                    )
                    assertNull(
                        kubernetesClient
                            .secrets()
                            .withName("$name-wonderwall")
                            .get(),
                    )
                    assertNull(KcAdminClient.getClientRepresentation(realmResource, clientId!!))
                }
        }
    }
}
