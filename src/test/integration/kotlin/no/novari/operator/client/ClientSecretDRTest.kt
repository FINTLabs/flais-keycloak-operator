package no.novari.operator.client

import io.fabric8.kubernetes.api.model.Secret
import io.fabric8.kubernetes.client.KubernetesClient
import no.novari.environment.OperatorEnvironment
import no.novari.environment.OperatorEnvironmentExtension
import no.novari.fixture.IntegrationTestSupport
import no.novari.keycloak.KeycloakClientNameGenerator
import no.novari.operator.MANAGED_BY_APPLICATION_LABEL_KEY
import no.novari.operator.MANAGED_BY_APPLICATION_LABEL_VALUE
import no.novari.operator.client.api.v1alpha1.FlaisAuthenticationSpec
import org.awaitility.Awaitility.await
import org.awaitility.kotlin.withPollInterval
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.Duration
import java.util.Base64

@ExtendWith(OperatorEnvironmentExtension::class)
class ClientSecretDRTest {
    @Test
    fun `reconcile creates configured client data secret`(
        env: OperatorEnvironment,
        kubernetesClient: KubernetesClient,
    ) {
        val support = IntegrationTestSupport(kubernetesClient)
        val name = support.uniqueName("client-secret")
        val secretName = "$name-client-data"
        val clientURI = "https://$name.apps.example.no"
        val redirectURI = "$clientURI/oauth/callback"
        val expectedClientId = expectedClientId(name)
        val expectedWellKnownUrl = "${env.keycloakClusterUrl()}/realms/$REALM/.well-known/openid-configuration"

        support.applyApplication(
            name = name,
            spec =
                FlaisAuthenticationSpec(
                    realm = REALM,
                    clientURI = clientURI,
                    redirectURIs = listOf(redirectURI),
                    secretName = secretName,
                ),
        )

        await()
            .withPollInterval(Duration.ofSeconds(1))
            .atMost(Duration.ofSeconds(90))
            .untilAsserted {
                val secret =
                    kubernetesClient
                        .secrets()
                        .withName(secretName)
                        .get()

                assertNotNull(secret)
                assertEquals(MANAGED_BY_APPLICATION_LABEL_VALUE, secret!!.metadata.labels[MANAGED_BY_APPLICATION_LABEL_KEY])
                assertEquals(
                    name,
                    secret.metadata
                        .ownerReferences
                        .single()
                        .name,
                )
                assertEquals(expectedClientId, secret.decodedData("KEYCLOAK_CLIENT_ID"))
                assertTrue(secret.decodedData("KEYCLOAK_CLIENT_SECRET").isNotBlank())
                assertEquals(redirectURI, secret.decodedData("KEYCLOAK_REDIRECT_URI"))
                assertEquals(
                    expectedWellKnownUrl,
                    secret.decodedData("KEYCLOAK_WELL_KNOWN_URL"),
                )
            }
    }

    @Test
    fun `reconcile deletes stale client data secret after secret name changes`(kubernetesClient: KubernetesClient) {
        val support = IntegrationTestSupport(kubernetesClient)
        val name = support.uniqueName("secret-rotation")
        val firstSecretName = "$name-first"
        val secondSecretName = "$name-second"
        val clientURI = "https://$name.apps.example.no"

        support.applyApplication(
            name = name,
            spec =
                FlaisAuthenticationSpec(
                    realm = REALM,
                    clientURI = clientURI,
                    redirectURIs = listOf("$clientURI/callback"),
                    secretName = firstSecretName,
                ),
        )

        await()
            .withPollInterval(Duration.ofSeconds(1))
            .atMost(Duration.ofSeconds(90))
            .untilAsserted {
                assertNotNull(kubernetesClient.secrets().withName(firstSecretName).get())
            }

        support.applyApplication(
            name = name,
            spec =
                FlaisAuthenticationSpec(
                    realm = REALM,
                    clientURI = clientURI,
                    redirectURIs = listOf("$clientURI/callback"),
                    secretName = secondSecretName,
                ),
        )

        await()
            .withPollInterval(Duration.ofSeconds(1))
            .atMost(Duration.ofSeconds(90))
            .untilAsserted {
                assertNotNull(kubernetesClient.secrets().withName(secondSecretName).get())
                assertNull(kubernetesClient.secrets().withName(firstSecretName).get())
            }
    }

    private fun Secret.decodedData(key: String): String {
        val encoded = data?.get(key) ?: throw AssertionError("Expected Secret data key '$key'")
        return String(Base64.getDecoder().decode(encoded))
    }

    private fun expectedClientId(name: String): String =
        KeycloakClientNameGenerator.generate(
            team = "team-platform",
            name = name,
            orgId = "novari_no",
        )

    private companion object {
        private const val REALM = "fint"
    }
}
