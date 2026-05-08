package no.novari.application

import io.fabric8.kubernetes.api.model.apps.Deployment
import io.fabric8.kubernetes.client.KubernetesClient
import no.novari.environment.OperatorEnvironmentExtension
import org.awaitility.Awaitility.await
import org.awaitility.kotlin.withPollInterval
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.Duration
import java.util.concurrent.TimeUnit

@ExtendWith(OperatorEnvironmentExtension::class)
class OperatorChartTest {
    @Test
    fun `operator should be installed by helm and running`(kubernetesClient: KubernetesClient) {
        val releaseName = "flais-keycloak-operator"

        await()
            .withPollInterval(Duration.ofSeconds(1))
            .atMost(Duration.ofSeconds(60))
            .untilAsserted {
                val operatorDeployment: Deployment? =
                    kubernetesClient
                        .apps()
                        .deployments()
                        .inNamespace("default")
                        .withLabel("app.kubernetes.io/name", releaseName)
                        .list()
                        .items
                        .firstOrNull { deployment ->
                            deployment.metadata.labels["app.kubernetes.io/managed-by"] == "Helm"
                        }

                assertNotNull(
                    operatorDeployment,
                )

                val readyDeployment =
                    kubernetesClient
                        .apps()
                        .deployments()
                        .inNamespace(operatorDeployment!!.metadata.namespace)
                        .withName(operatorDeployment.metadata.name)
                        .waitUntilCondition(
                            { deployment ->
                                val status = deployment.status
                                val desiredReplicas = deployment.spec.replicas ?: 1

                                status != null &&
                                    (status.availableReplicas ?: 0) >= desiredReplicas &&
                                    (status.readyReplicas ?: 0) >= desiredReplicas
                            },
                            2,
                            TimeUnit.MINUTES,
                        )

                assertNotNull(
                    assertNotNull(readyDeployment),
                )
            }
    }
}
