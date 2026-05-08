package no.novari.application

import io.fabric8.kubernetes.client.KubernetesClient
import no.novari.application.api.v1alpha1.FlaisAuthenticationSpec
import no.novari.application.api.v1alpha1.Ingress
import no.novari.application.api.v1alpha1.WonderwallConfig
import no.novari.common.Constants.KC_REALM
import no.novari.environment.OperatorEnvironmentExtension
import no.novari.fixture.IntegrationTestSupport
import org.awaitility.Awaitility.await
import org.awaitility.kotlin.withPollInterval
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.Duration

@ExtendWith(OperatorEnvironmentExtension::class)
class WonderwallConfigMapDRTest {
    @Test
    fun `reconcile creates configmap and patches with changes`(kubernetesClient: KubernetesClient) {
        val support = IntegrationTestSupport(kubernetesClient)
        val name = support.uniqueName("reconcile")
        val host = "samtykke.novari.no"
        val base = "beta/rogfk-no"
        val upstreamPort = 8081
        val realm = "fint"

        val resource =
            support.applyApplication(
                name = name,
                spec =
                    FlaisAuthenticationSpec(
                        ingress = listOf(Ingress(host, base)),
                        wonderwall = WonderwallConfig(upstreamPort = upstreamPort),
                        realm = realm,
                    ),
            )

        await()
            .withPollInterval(Duration.ofSeconds(1))
            .atMost(Duration.ofSeconds(60))
            .untilAsserted {
                val secret =
                    kubernetesClient
                        .secrets()
                        .withName("$name-wonderwall")
                        .get()

                val configMap =
                    kubernetesClient
                        .configMaps()
                        .withName("$name-wonderwall")
                        .get()

                assertNotNull(configMap)
                assertNotNull(secret)

                val expectedBaseUrl = support.operatorEnv("KEYCLOAK_BASE_URL")

                assertEquals(secret.metadata.annotations[WONDERWALL_CLIENT_ID_ANNOTATION], configMap.data["WONDERWALL_OPENID_CLIENT_ID"])
                assertEquals(
                    "$expectedBaseUrl/realms/$KC_REALM/.well-known/openid-configuration",
                    configMap.data["WONDERWALL_OPENID_WELL_KNOWN_URL"],
                )
                assertEquals("https://$host/$base", configMap.data["WONDERWALL_INGRESS"])
                assertEquals("$upstreamPort", configMap.data["WONDERWALL_UPSTREAM_PORT"])
                assertEquals("0.0.0.0:8080", configMap.data["WONDERWALL_BIND_ADDRESS"])
                assertEquals("true", configMap.data["WONDERWALL_AUTO_LOGIN"])
                assertEquals("profile", configMap.data["WONDERWALL_OPENID_SCOPES"])
                assertEquals(wonderwallConfigMapName(resource), configMap.metadata.name)
            }
    }

    @Test
    fun `deleting configmap recreates it`(kubernetesClient: KubernetesClient) {
        val support = IntegrationTestSupport(kubernetesClient)
        val name = support.uniqueName("recreate-configmap")
        val host = "samtykke.novari.no"
        val base = "beta/rogfk-no"
        val upstreamPort = 8081
        val realm = "fint"

        support.applyApplication(
            name = name,
            spec =
                FlaisAuthenticationSpec(
                    ingress = listOf(Ingress(host, base)),
                    wonderwall = WonderwallConfig(upstreamPort = upstreamPort),
                    realm = realm,
                ),
        )

        var originalUid: String? = null

        await()
            .withPollInterval(Duration.ofSeconds(1))
            .atMost(Duration.ofSeconds(60))
            .untilAsserted {
                val configMap =
                    kubernetesClient
                        .configMaps()
                        .withName("$name-wonderwall")
                        .get()

                assertNotNull(configMap)
                originalUid = configMap.metadata.uid
            }

        kubernetesClient
            .configMaps()
            .withName("$name-wonderwall")
            .delete()

        await()
            .withPollInterval(Duration.ofSeconds(1))
            .atMost(Duration.ofSeconds(60))
            .untilAsserted {
                val secret =
                    kubernetesClient
                        .secrets()
                        .withName("$name-wonderwall")
                        .get()

                val configMap =
                    kubernetesClient
                        .configMaps()
                        .withName("$name-wonderwall")
                        .get()

                assertNotNull(configMap)
                assertNotNull(secret)
                assertNotEquals(originalUid, configMap.metadata.uid)
                assertEquals(secret.metadata.annotations[WONDERWALL_CLIENT_ID_ANNOTATION], configMap.data["WONDERWALL_OPENID_CLIENT_ID"])
                assertEquals("https://$host/$base", configMap.data["WONDERWALL_INGRESS"])
                assertEquals("$upstreamPort", configMap.data["WONDERWALL_UPSTREAM_PORT"])
            }
    }
}
