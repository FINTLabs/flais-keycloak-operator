package no.novari.application

import io.fabric8.kubernetes.client.KubernetesClient
import no.novari.common.Constants.KC_REALM
import no.novari.extensions.OperatorEnvironmentExtension
import no.novari.utils.IntegrationTestSupport
import no.novari.utils.KcAdminClient
import no.novari.utils.OperatorEnvironment
import org.awaitility.Awaitility.await
import org.awaitility.kotlin.withPollInterval
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.Duration

@ExtendWith(OperatorEnvironmentExtension::class)
class ApplicationCleanupTest {
    @Test
    fun `delete removes managed sidecar and keycloak client`(
        env: OperatorEnvironment,
        kubernetesClient: KubernetesClient,
    ) {
        val support = IntegrationTestSupport(kubernetesClient)
        val name = support.uniqueName("delete")

        support.createTargetDeployment(name)
        support.applyApplication(name)

        await()
            .withPollInterval(Duration.ofSeconds(1))
            .atMost(Duration.ofSeconds(60))
            .untilAsserted {
                assertTrue(support.wonderwallContainers(name).isNotEmpty())
            }
        support.deleteApplication(name)

        await()
            .withPollInterval(Duration.ofSeconds(1))
            .atMost(Duration.ofSeconds(60))
            .untilAsserted {
                assertTrue(
                    support.wonderwallContainers(name).isEmpty(),
                )
            }

        val (kc, realmRes) = KcAdminClient.connect(env, KC_REALM)
        kc.use {
            await()
                .withPollInterval(Duration.ofSeconds(1))
                .atMost(Duration.ofSeconds(60))
                .untilAsserted {
                    assertNull(KcAdminClient.getClientRepresentation(realmRes, name))
                }
        }
    }
}
