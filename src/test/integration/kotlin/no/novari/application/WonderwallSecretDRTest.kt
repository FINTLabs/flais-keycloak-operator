package no.novari.application

import io.fabric8.kubernetes.client.KubernetesClient
import no.novari.application.api.v1alpha1.FlaisAuthenticationSpec
import no.novari.application.api.v1alpha1.Ingress
import no.novari.application.api.v1alpha1.WonderwallConfig
import no.novari.extensions.OperatorEnvironmentExtension
import no.novari.utils.IntegrationTestSupport
import org.awaitility.Awaitility.await
import org.awaitility.kotlin.withPollInterval
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.Duration

@ExtendWith(OperatorEnvironmentExtension::class)
class WonderwallSecretDRTest {
    @Test
    fun `reconcile creates secret and patches with credentials`(kubernetesClient: KubernetesClient) {
        val support = IntegrationTestSupport(kubernetesClient)
        val name = support.uniqueName("reconcile")
        val host = "samtykke.novari.no"
        val base = "beta/rogfk-no"

        support.applyApplication(
            name = name,
            spec =
                FlaisAuthenticationSpec(
                    ingress = listOf(Ingress(host, base)),
                    wonderwall = WonderwallConfig(upstreamPort = 8081),
                    realm = "fint",
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

                assertNotNull(secret)

                val clientId = secret.metadata.annotations[WONDERWALL_CLIENT_ID_ANNOTATION]
                assertFalse(clientId.isNullOrBlank())

                val encodedClientSecret = secret.data[WONDERWALL_CLIENT_SECRET_KEY]
                assertFalse(encodedClientSecret.isNullOrBlank())
            }
    }
}
