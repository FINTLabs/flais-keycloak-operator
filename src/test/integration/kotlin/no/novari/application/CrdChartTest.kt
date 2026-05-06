package no.novari.application

import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition
import io.fabric8.kubernetes.client.KubernetesClient
import no.novari.extensions.OperatorEnvironmentExtension
import org.awaitility.Awaitility.await
import org.awaitility.kotlin.withPollInterval
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.Duration

@ExtendWith(OperatorEnvironmentExtension::class)
class CrdChartTest {
    @Test
    fun `crds should be installed`(kubernetesClient: KubernetesClient) {
        await()
            .withPollInterval(Duration.ofSeconds(1))
            .atMost(Duration.ofSeconds(60))
            .untilAsserted {
                val crds: MutableList<CustomResourceDefinition?> =
                    kubernetesClient
                        .apiextensions()
                        .v1()
                        .customResourceDefinitions()
                        .list()
                        .items
                        .stream()
                        .filter({ crd -> "novari.no" == crd.spec.group })
                        .filter({ crd -> "FlaisApplication" == crd.spec.names.kind })
                        .toList()

                assertTrue(crds.isNotEmpty())
            }
    }
}
