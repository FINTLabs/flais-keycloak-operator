package no.novari.application

import io.fabric8.kubernetes.client.KubernetesClient
import no.novari.extensions.OperatorEnvironmentExtension
import no.novari.utils.IntegrationTestSupport
import org.awaitility.Awaitility.await
import org.awaitility.kotlin.withPollInterval
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.Duration

@ExtendWith(OperatorEnvironmentExtension::class)
class TraefikIngressRouteTest {
    @AfterEach
    fun cleanup(kubernetesClient: KubernetesClient) {
        val support = IntegrationTestSupport(kubernetesClient)
        support.cleanup()
    }

    @Test
    fun `application reconcile creates traefik ingressroute`(kubernetesClient: KubernetesClient) {
        val support = IntegrationTestSupport(kubernetesClient)

        val name = support.uniqueName("traefik")
        support.createTargetDeployment(name)
        support.applyApplication(name)

        await()
            .withPollInterval(Duration.ofSeconds(1))
            .atMost(Duration.ofSeconds(60))
            .untilAsserted {
                val ingressRoute = support.ingressRoute(name)
                assertNotNull(ingressRoute)

                assertEquals(name, ingressRoute!!.metadata.name)
                assertEquals(kubernetesClient.namespace, ingressRoute.metadata.namespace)
                assertEquals("application-operator", ingressRoute.metadata.labels["app.kubernetes.io/managed-by"])
                assertEquals(
                    name,
                    ingressRoute.metadata.ownerReferences
                        .single()
                        .name,
                )

                assertEquals(listOf("web"), ingressRoute.spec.entryPoints)
                assertEquals(1, ingressRoute.spec.routes.size)

                val route = ingressRoute.spec.routes.single()
                assertEquals("Rule", route.kind.name)
                assertEquals("Host(`samtykke.vigoiks.no`) && PathPrefix(`/beta/rogfk-no`)", route.match)
                assertEquals("$name-wonderwall", route.services.single().name)
                assertEquals(
                    80,
                    route.services
                        .single()
                        .port.value,
                )
            }
    }

    @Test
    fun `application reconcile updates traefik ingressroute`(kubernetesClient: KubernetesClient) {
        val support = IntegrationTestSupport(kubernetesClient)

        val name = support.uniqueName("traefik")
        support.createTargetDeployment(name)
        support.applyApplication(name)

        await()
            .withPollInterval(Duration.ofSeconds(1))
            .atMost(Duration.ofSeconds(60))
            .untilAsserted {
                assertNotNull(support.ingressRoute(name))
            }

        support.applyApplication(
            name = name,
            hostname = "changed.vigoiks.no/",
            basePath = "/changed/path/",
        )

        await()
            .withPollInterval(Duration.ofSeconds(1))
            .atMost(Duration.ofSeconds(60))
            .untilAsserted {
                val ingressRoute = support.ingressRoute(name)
                assertNotNull(ingressRoute)
                assertEquals(
                    "Host(`changed.vigoiks.no`) && PathPrefix(`/changed/path`)",
                    ingressRoute!!
                        .spec.routes
                        .single()
                        .match,
                )
            }
    }

    @Test
    fun `application delete removes traefik ingressroute`(kubernetesClient: KubernetesClient) {
        val support = IntegrationTestSupport(kubernetesClient)

        val name = support.uniqueName("traefik")
        support.createTargetDeployment(name)
        support.applyApplication(name)

        await()
            .withPollInterval(Duration.ofSeconds(1))
            .atMost(Duration.ofSeconds(60))
            .untilAsserted {
                assertNotNull(support.ingressRoute(name))
            }

        support.deleteApplication(name)

        await()
            .withPollInterval(Duration.ofSeconds(1))
            .atMost(Duration.ofSeconds(60))
            .untilAsserted {
                assertEquals(null, support.ingressRoute(name))
            }
    }
}
