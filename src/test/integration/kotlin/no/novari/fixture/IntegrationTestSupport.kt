package no.novari.fixture

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder
import io.fabric8.kubernetes.client.KubernetesClient
import no.novari.operator.ORG_ID
import no.novari.operator.TEAM
import no.novari.operator.client.api.v1alpha1.FlaisAuthentication
import no.novari.operator.client.api.v1alpha1.FlaisAuthenticationSpec
import java.util.UUID

class IntegrationTestSupport(
    private val kubernetesClient: KubernetesClient,
) {
    fun applyApplication(
        name: String,
        spec: FlaisAuthenticationSpec,
    ): FlaisAuthentication {
        return kubernetesClient
            .resource(
                FlaisAuthentication().apply {
                    metadata =
                        ObjectMetaBuilder()
                            .withName(name)
                            .withLabels<String, String>(
                                mapOf(
                                    TEAM to "team-platform",
                                    ORG_ID to "novari_no",
                                ),
                            ).build()
                    this.spec = spec
                },
            ).serverSideApply()
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
