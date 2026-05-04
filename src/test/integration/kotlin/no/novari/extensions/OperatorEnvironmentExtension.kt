package no.novari.extensions

import io.fabric8.kubernetes.api.model.NamespaceBuilder
import io.fabric8.kubernetes.client.Config
import io.fabric8.kubernetes.client.ConfigBuilder
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientBuilder
import no.novari.utils.HelmClient
import no.novari.utils.OperatorEnvironment
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionConfigurationException
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolutionException
import org.junit.jupiter.api.extension.ParameterResolver
import java.nio.file.Paths
import java.util.UUID

/**
 * JUnit 5 extension that manages the lifecycle of an Operator environment for tests.
 *
 * Responsibilities:
 * - Starts an [OperatorEnvironment] (Keycloak + Support containers via Testcontainers/Compose) once.
 * - Stops and cleans up the environment after all tests in the class have run.
 * - Provides parameter injection: test methods can declare parameters,
 *   and JUnit will resolve it automatically using this extension.
 *
 * How it works:
 * - [beforeAll] creates and initializes components and puts them in store.
 * - [afterAll] retrieves the components from the store and cleans up.
 * - [supportsParameter] and [resolveParameter] let you write tests with parameter injection.
 *
 * This extension is intended for testing against a fixed environment.
 */
class OperatorEnvironmentExtension :
    BeforeAllCallback,
    AfterAllCallback,
    ParameterResolver {
    val namespace = ExtensionContext.Namespace.create("no.novari")
    val operatorEnvironment = "operator-environment"
    val kubernetesClient = "kubernetes-client"
    val traefikChart = "traefik/traefik"
    val traefikChartVersion = "27.0.2"

    lateinit var kubernetesNamespace: String

    override fun beforeAll(context: ExtensionContext) {
        val store = store(context)
        kubernetesNamespace = uniqueName("integration-tests")

        val env: OperatorEnvironment =
            store.get(operatorEnvironment, OperatorEnvironment::class.java) ?: OperatorEnvironment().also {
                it.start()
                store.put(operatorEnvironment, it)
            }

        val kubernetesClient: KubernetesClient =
            KubernetesClientBuilder()
                .withConfig(
                    ConfigBuilder(Config.fromKubeconfig(env.k3s.kubeConfigYaml))
                        .withNamespace(kubernetesNamespace)
                        .build(),
                ).build()
                .also {
                    store.put(kubernetesClient, it)
                }

        val helmClient = HelmClient(env.k3s.kubeConfigYaml, "default")

        kubernetesClient
            .namespaces()
            .resource(
                NamespaceBuilder()
                    .withNewMetadata()
                    .withName(kubernetesNamespace)
                    .endMetadata()
                    .build(),
            ).serverSideApply()

        val rootDir =
            System
                .getProperty("project.rootDir")
                ?.let { Paths.get(it).normalize() }
                ?: throw IllegalStateException("project.rootDir not set, could not install charts")

        if (!crdExists(kubernetesClient, "applications.novari.no")) {
            helmClient.addRepository(
                repositoryName = "traefik",
                repositoryUrl = "https://traefik.github.io/charts",
            )

            helmClient.installChart(
                releaseName = "traefik",
                chart = traefikChart,
                version = traefikChartVersion,
                valuesFile = rootDir.resolve("src/test/integration/resources/traefik/values.yaml"),
            )

            helmClient.installChart(
                releaseName = "flais-keycloak-operator-crd",
                chart = rootDir.resolve("charts/flais-keycloak-operator-crd"),
            )

            helmClient.installChart(
                releaseName = "flais-keycloak-operator",
                chart = rootDir.resolve("charts/flais-keycloak-operator"),
                values =
                    mapOf(
                        "image.repository" to System.getProperty("operator.image").substringBeforeLast(":"),
                        "image.tag" to System.getProperty("operator.image").substringAfterLast(":"),
                        "image.pullPolicy" to "Never",
                        "keycloak.baseUrl" to env.keycloakClusterUrl(),
                        "keycloak.wellKnownBaseUrl" to env.keycloakClusterUrl(),
                    ),
            )
        }
    }

    override fun afterAll(context: ExtensionContext) = Unit

    override fun supportsParameter(
        pc: ParameterContext,
        ec: ExtensionContext,
    ): Boolean {
        val t = pc.parameter.type
        return t == OperatorEnvironment::class.java ||
            t == KubernetesClient::class.java
    }

    override fun resolveParameter(
        pc: ParameterContext,
        ec: ExtensionContext,
    ): Any {
        val t = pc.parameter.type
        val s = store(ec)
        return when (t) {
            OperatorEnvironment::class.java -> {
                s.get(operatorEnvironment, OperatorEnvironment::class.java)
                    ?: throw ExtensionConfigurationException("OperatorEnvironment not initialized")
            }

            KubernetesClient::class.java -> {
                s.get(kubernetesClient, KubernetesClient::class.java)
                    ?: throw ExtensionConfigurationException("KubernetesClient not initialized")
            }

            else -> {
                throw ParameterResolutionException("Unsupported parameter: $t")
            }
        }
    }

    private fun store(context: ExtensionContext): ExtensionContext.Store = context.root.getStore(namespace)

    private fun uniqueName(prefix: String): String = "$prefix-${UUID.randomUUID().toString().take(8)}"

    private fun crdExists(
        client: KubernetesClient,
        name: String,
    ): Boolean =
        client
            .apiextensions()
            .v1()
            .customResourceDefinitions()
            .withName(name)
            .get() != null
}
