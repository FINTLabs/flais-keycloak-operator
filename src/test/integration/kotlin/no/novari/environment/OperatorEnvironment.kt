package no.novari.environment

import org.testcontainers.containers.ComposeContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.containers.wait.strategy.WaitAllStrategy
import org.testcontainers.k3s.K3sContainer
import org.testcontainers.utility.DockerImageName
import org.testcontainers.utility.MountableFile
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import kotlin.io.path.writeText

class OperatorEnvironment(
    baseComposeFile: File = File("./docker-compose.yaml"),
    testComposeFile: File = File("./docker-compose.test.yaml"),
) : AutoCloseable {
    val keycloakAdminUser: String = "admin"
    val keycloakAdminPassword: String = "admin"

    val debugKubeconfigPath: Path =
        Path.of(System.getProperty(K3S_KUBECONFIG_PATH_PROPERTY, DEFAULT_KUBECONFIG_PATH))

    private val operatorImageTar: String =
        System.getProperty(OPERATOR_IMAGE_TAR_PROPERTY)
            ?: error("Missing required system property: $OPERATOR_IMAGE_TAR_PROPERTY")

    private val compose: ComposeContainer =
        createComposeContainer(baseComposeFile, testComposeFile)

    val k3s: K3sContainer =
        createK3sContainer()

    fun start() {
        compose.start()
        k3s.start()

        writeKubeconfigForDebugging()
    }

    override fun close() {
        k3s.stop()
        compose.stop()
    }

    fun keycloakServiceUrl(): String {
        val host = compose.getServiceHost(KEYCLOAK_SERVICE_NAME, KEYCLOAK_HTTP_PORT)
        val port = compose.getServicePort(KEYCLOAK_SERVICE_NAME, KEYCLOAK_HTTP_PORT)

        return "http://$host:$port"
    }

    fun keycloakClusterUrl(): String {
        val keycloakContainer =
            compose
                .getContainerByServiceName(KEYCLOAK_CONTAINER_NAME)
                .orElseGet {
                    compose
                        .getContainerByServiceName(KEYCLOAK_SERVICE_NAME)
                        .orElseThrow { IllegalStateException("Could not find Keycloak container") }
                }

        val network =
            keycloakContainer
                .containerInfo
                .networkSettings
                .networks[DOCKER_NETWORK_NAME]
                ?: error("Keycloak container is not attached to the $DOCKER_NETWORK_NAME network")

        return "http://${network.ipAddress}:$KEYCLOAK_HTTP_PORT"
    }

    private fun createComposeContainer(
        baseComposeFile: File,
        testComposeFile: File,
    ): ComposeContainer =
        ComposeContainer(baseComposeFile, testComposeFile).apply {
            withBuild(true)

            withEnv("KEYCLOAK_VERSION", System.getenv("KEYCLOAK_VERSION"))

            withExposedService(KEYCLOAK_SERVICE_NAME, KEYCLOAK_MANAGEMENT_PORT)
            withExposedService(KEYCLOAK_SERVICE_NAME, KEYCLOAK_HTTP_PORT)

            waitingFor(KEYCLOAK_SERVICE_NAME, keycloakWaitStrategy())
        }

    private fun createK3sContainer(): K3sContainer {
        val kubernetesVersion =
            System.getenv("TEST_KUBERNETES_VERSION")?.let { "$it-k3s1" } ?: DEFAULT_KUBERNETES_VERSION

        val kubernetesImage =
            System.getenv("TEST_KUBERNETES_IMAGE") ?: DEFAULT_KUBERNETES_IMAGE

        return K3sContainer(DockerImageName.parse("$kubernetesImage:$kubernetesVersion")).apply {
            withCommand("server")
            withNetworkMode(DOCKER_NETWORK_NAME)
            withCopyFileToContainer(
                MountableFile.forHostPath(operatorImageTar),
                K3S_OPERATOR_IMAGE_PATH,
            )
        }
    }

    private fun keycloakWaitStrategy(): WaitAllStrategy =
        WaitAllStrategy()
            .withStrategy(
                Wait
                    .forHttp("/health/ready")
                    .forPort(KEYCLOAK_MANAGEMENT_PORT)
                    .withStartupTimeout(KEYCLOAK_STARTUP_TIMEOUT),
            ).withStrategy(
                Wait
                    .forHttp("/")
                    .forPort(KEYCLOAK_HTTP_PORT)
                    .withStartupTimeout(KEYCLOAK_STARTUP_TIMEOUT),
            )

    private fun writeKubeconfigForDebugging() {
        Files.createDirectories(debugKubeconfigPath.parent)

        debugKubeconfigPath.writeText(k3s.kubeConfigYaml)

        println("Wrote k3s kubeconfig to: ${debugKubeconfigPath.toAbsolutePath()}")
    }

    private companion object {
        private const val KEYCLOAK_SERVICE_NAME = "keycloak"
        private const val KEYCLOAK_CONTAINER_NAME = "keycloak-1"

        private const val KEYCLOAK_HTTP_PORT = 8080
        private const val KEYCLOAK_MANAGEMENT_PORT = 9000

        private const val DOCKER_NETWORK_NAME = "flais-keycloak-operator"

        private const val DEFAULT_KUBERNETES_IMAGE = "rancher/k3s"
        private const val DEFAULT_KUBERNETES_VERSION = "latest"

        private const val OPERATOR_IMAGE_TAR_PROPERTY = "operator.image.tar"
        private const val K3S_KUBECONFIG_PATH_PROPERTY = "k3s.kubeconfig.path"

        private const val DEFAULT_KUBECONFIG_PATH = "build/debug/kubeconfig.yaml"
        private const val K3S_OPERATOR_IMAGE_PATH = "/var/lib/rancher/k3s/agent/images/operator-image.tar"

        private val KEYCLOAK_STARTUP_TIMEOUT: Duration = Duration.ofMinutes(15)
    }
}
