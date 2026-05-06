package no.novari.utils

import io.fabric8.kubernetes.api.model.ContainerBuilder
import io.fabric8.kubernetes.api.model.ContainerPortBuilder
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder
import io.fabric8.kubernetes.api.model.apps.Deployment
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder
import io.fabric8.kubernetes.client.KubernetesClient
import no.novari.application.api.v1alpha1.FlaisAuthentication
import no.novari.application.api.v1alpha1.FlaisAuthenticationSpec
import java.util.UUID

class IntegrationTestSupport(
    private val kubernetesClient: KubernetesClient,
) {
    private val createdApplications = mutableListOf<String>()
    private val createdDeployments = mutableListOf<String>()

    fun cleanup() {
        createdApplications.forEach { name ->
            kubernetesClient
                .resources(FlaisAuthentication::class.java)
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
    ): FlaisAuthentication {
        if (!createdApplications.contains(name)) {
            createdApplications += name
        }

        return kubernetesClient
            .resources(FlaisAuthentication::class.java)
            .resource(
                FlaisAuthentication().apply {
                    metadata =
                        ObjectMetaBuilder()
                            .withName(name)
                            .build()
                    spec = FlaisAuthenticationSpec(hostname, basePath, realm)
                },
            ).serverSideApply()
    }

    fun deleteApplication(name: String) {
        kubernetesClient
            .resources(FlaisAuthentication::class.java)
            .withName(name)
            .delete()
        createdApplications.remove(name)
    }

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
