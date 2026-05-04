package no.novari.utils

import io.fabric8.kubernetes.api.model.Container
import io.fabric8.kubernetes.api.model.ContainerBuilder
import io.fabric8.kubernetes.api.model.ContainerPortBuilder
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder
import io.fabric8.kubernetes.api.model.apps.Deployment
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder
import io.fabric8.kubernetes.client.KubernetesClient
import no.novari.application.WONDERWALL_CONTAINER_NAME
import no.novari.application.api.v1alpha1.Application
import no.novari.application.api.v1alpha1.ApplicationSpec
import us.containo.traefik.v1alpha1.IngressRoute
import java.util.UUID

class IntegrationTestSupport(
    private val kubernetesClient: KubernetesClient,
) {
    private val createdApplications = mutableListOf<String>()
    private val createdDeployments = mutableListOf<String>()

    fun cleanup() {
        createdApplications.forEach { name ->
            kubernetesClient
                .resources(Application::class.java)
                .withName(name)
                .delete()
        }

        createdDeployments.forEach { name ->
            kubernetesClient
                .apps()
                .deployments()
                .withName(name)
                .delete()
        }

        createdApplications.clear()
        createdDeployments.clear()
    }

    fun createTargetDeployment(name: String): Deployment {
        createdDeployments += name
        return kubernetesClient
            .apps()
            .deployments()
            .resource(
                DeploymentBuilder()
                    .withMetadata(
                        ObjectMetaBuilder()
                            .withName(name)
                            .withLabels<String, String>(mapOf("app" to name))
                            .build(),
                    ).withNewSpec()
                    .withReplicas(1)
                    .withNewSelector()
                    .withMatchLabels<String, String>(mapOf("app" to name))
                    .endSelector()
                    .withNewTemplate()
                    .withNewMetadata()
                    .withLabels<String, String>(mapOf("app" to name))
                    .endMetadata()
                    .withNewSpec()
                    .withContainers(
                        ContainerBuilder()
                            .withName("app")
                            .withImage("nginx:stable")
                            .withPorts(
                                ContainerPortBuilder()
                                    .withName("app")
                                    .withContainerPort(3000)
                                    .withProtocol("TCP")
                                    .build(),
                            ).build(),
                    ).endSpec()
                    .endTemplate()
                    .endSpec()
                    .build(),
            ).serverSideApply()
    }

    fun applyApplication(
        name: String,
        hostname: String = "samtykke.vigoiks.no",
        basePath: String = "beta/rogfk-no",
        realm: String = "fint",
        upstreamPort: Int = 3000,
    ): Application {
        if (!createdApplications.contains(name)) {
            createdApplications += name
        }

        return kubernetesClient
            .resources(Application::class.java)
            .resource(
                Application().apply {
                    metadata =
                        ObjectMetaBuilder()
                            .withName(name)
                            .build()
                    spec = ApplicationSpec(hostname, basePath, realm, upstreamPort)
                },
            ).serverSideApply()
    }

    fun deleteApplication(name: String) {
        kubernetesClient
            .resources(Application::class.java)
            .withName(name)
            .delete()
        createdApplications.remove(name)
    }

    fun wonderwallContainers(name: String): List<Container> =
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
            .filter { it.name == WONDERWALL_CONTAINER_NAME }

    fun ingressRoute(name: String): IngressRoute? =
        kubernetesClient
            .resources(IngressRoute::class.java)
            .withName(name)
            .get()

    fun operatorEnv(name: String): String =
        kubernetesClient
            .apps()
            .deployments()
            .inNamespace("default")
            .list()
            .items
            .single { deployment ->
                deployment.spec
                    ?.template
                    ?.spec
                    ?.containers
                    .orEmpty()
                    .any { container ->
                        container.env.orEmpty().any { env -> env.name == name }
                    }
            }.spec
            .template
            .spec
            .containers
            .flatMap { it.env.orEmpty() }
            .single { it.name == name }
            .value

    fun uniqueName(prefix: String): String = "$prefix-${UUID.randomUUID().toString().take(8)}"
}
