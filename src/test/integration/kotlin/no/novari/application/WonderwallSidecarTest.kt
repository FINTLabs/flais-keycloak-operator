package no.novari.application

import io.fabric8.kubernetes.client.KubernetesClient
import no.novari.application.WonderwallDeploymentDR.Companion.AUTO_LOGIN_ENABLED
import no.novari.application.WonderwallDeploymentDR.Companion.BIND_HOST
import no.novari.application.WonderwallDeploymentDR.Companion.ENV_AUTO_LOGIN
import no.novari.application.WonderwallDeploymentDR.Companion.ENV_BIND_ADDRESS
import no.novari.application.WonderwallDeploymentDR.Companion.ENV_INGRESS
import no.novari.application.WonderwallDeploymentDR.Companion.ENV_LOG_LEVEL
import no.novari.application.WonderwallDeploymentDR.Companion.ENV_OPENID_CLIENT_ID
import no.novari.application.WonderwallDeploymentDR.Companion.ENV_OPENID_CLIENT_SECRET
import no.novari.application.WonderwallDeploymentDR.Companion.ENV_OPENID_SCOPES
import no.novari.application.WonderwallDeploymentDR.Companion.ENV_OPENID_WELL_KNOWN_URL
import no.novari.application.WonderwallDeploymentDR.Companion.ENV_UPSTREAM_HOST
import no.novari.application.WonderwallDeploymentDR.Companion.LOCALHOST
import no.novari.application.WonderwallDeploymentDR.Companion.WONDERWALL_HTTP_PORT
import no.novari.application.WonderwallDeploymentDR.Companion.WONDERWALL_HTTP_PORT_NAME
import no.novari.application.WonderwallDeploymentDR.Companion.wellKnownUrl
import no.novari.extensions.OperatorEnvironmentExtension
import no.novari.utils.IntegrationTestSupport
import org.awaitility.Awaitility.await
import org.awaitility.kotlin.withPollInterval
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.extension.ExtendWith
import java.time.Duration

@ExtendWith(OperatorEnvironmentExtension::class)
class WonderwallSidecarTest {
    @AfterEach
    fun cleanup(kubernetesClient: KubernetesClient) {
        val support = IntegrationTestSupport(kubernetesClient)
        support.cleanup()
    }

    @Test
    fun `application reconcile injects wonderwall sidecar`(kubernetesClient: KubernetesClient) {
        val support = IntegrationTestSupport(kubernetesClient)

        val name = support.uniqueName("sidecar")
        support.createTargetDeployment(name)
        val application = support.applyApplication(name)

        await()
            .withPollInterval(Duration.ofSeconds(1))
            .atMost(Duration.ofSeconds(60))
            .untilAsserted {
                val sidecar =
                    kubernetesClient
                        .apps()
                        .deployments()
                        .withName(name)
                        .get()
                        ?.spec
                        ?.template
                        ?.spec
                        ?.containers
                        .orEmpty()
                        .single { it.name == WONDERWALL_CONTAINER_NAME }
                assertNotNull(sidecar)

                assertEquals(support.operatorEnv("WONDERWALL_IMAGE"), sidecar.image)
                assertEquals(WONDERWALL_HTTP_PORT, sidecar.ports.single { it.name == WONDERWALL_HTTP_PORT_NAME }.containerPort)

                val envVars = sidecar.env.associateBy { it.name }
                assertEquals(name, envVars[ENV_OPENID_CLIENT_ID]?.value)
                assertEquals(
                    wellKnownUrl(
                        baseUrl = support.operatorEnv("KEYCLOAK_WELL_KNOWN_BASE_URL"),
                        realm = application.spec.realm,
                    ),
                    envVars[ENV_OPENID_WELL_KNOWN_URL]?.value,
                )
                assertEquals(
                    "https://${application.spec.hostname.trimEnd('/')}/${application.spec.basePath.trim('/')}",
                    envVars[ENV_INGRESS]?.value,
                )
                assertEquals("$LOCALHOST:${application.spec.upstreamPort}", envVars[ENV_UPSTREAM_HOST]?.value)
                assertEquals("$BIND_HOST:$WONDERWALL_HTTP_PORT", envVars[ENV_BIND_ADDRESS]?.value)
                assertEquals(AUTO_LOGIN_ENABLED, envVars[ENV_AUTO_LOGIN]?.value)
                assertEquals(application.spec.scope, envVars[ENV_OPENID_SCOPES]?.value)
                assertEquals(application.spec.logLevel, envVars[ENV_LOG_LEVEL]?.value)

                val secretRef = envVars[ENV_OPENID_CLIENT_SECRET]?.valueFrom?.secretKeyRef
                assertEquals("$name-wonderwall", secretRef?.name)
                assertEquals(KEYCLOAK_CLIENT_SECRET_KEY, secretRef?.key)
            }
    }

    @Test
    fun `application reconcile updates sidecar without duplicating it`(kubernetesClient: KubernetesClient) {
        val support = IntegrationTestSupport(kubernetesClient)

        val name = support.uniqueName("sidecar")
        support.createTargetDeployment(name)
        support.applyApplication(name)

        await()
            .withPollInterval(Duration.ofSeconds(1))
            .atMost(Duration.ofSeconds(60))
            .untilAsserted {
                assertEquals(1, support.wonderwallContainers(name).size)
            }

        support.applyApplication(
            name = name,
            basePath = "/changed/path/",
            upstreamPort = 4000,
        )

        await()
            .withPollInterval(Duration.ofSeconds(1))
            .atMost(Duration.ofSeconds(60))
            .untilAsserted {
                val containers = support.wonderwallContainers(name)
                assertEquals(
                    1,
                    containers.size,
                )
                val envVars = containers.single().env.associateBy { it.name }
                assertEquals("https://samtykke.vigoiks.no/changed/path", envVars[ENV_INGRESS]?.value)
                assertEquals("127.0.0.1:4000", envVars[ENV_UPSTREAM_HOST]?.value)
            }
    }
}
