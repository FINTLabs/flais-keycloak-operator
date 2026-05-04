package no.novari.application

import io.fabric8.kubernetes.api.model.Container
import io.fabric8.kubernetes.api.model.ContainerPort
import io.fabric8.kubernetes.api.model.EnvVar
import io.fabric8.kubernetes.api.model.EnvVarSourceBuilder
import io.fabric8.kubernetes.api.model.SecretKeySelectorBuilder
import io.javaoperatorsdk.operator.api.reconciler.Context
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource
import io.javaoperatorsdk.operator.api.reconciler.dependent.ReconcileResult
import no.novari.application.api.v1alpha1.Application
import no.novari.operator.dependent.ReadyCondition

class WonderwallDeploymentDR(
    private val keycloakClientService: KeycloakClientService,
) : DependentResource<Unit, Application>,
    ReadyCondition<Application> {
    companion object {
        const val RESOURCE_NAME = "wonderwall-deployment"

        const val WONDERWALL_HTTP_PORT_NAME = "http"
        const val WONDERWALL_HTTP_PORT = 8080
        const val WONDERWALL_HTTP_PROTOCOL = "TCP"

        const val ENV_OPENID_CLIENT_ID = "WONDERWALL_OPENID_CLIENT_ID"
        const val ENV_OPENID_CLIENT_SECRET = "WONDERWALL_OPENID_CLIENT_SECRET"
        const val ENV_OPENID_WELL_KNOWN_URL = "WONDERWALL_OPENID_WELL_KNOWN_URL"
        const val ENV_INGRESS = "WONDERWALL_INGRESS"
        const val ENV_LOG_LEVEL = "WONDERWALL_LOG_LEVEL"
        const val ENV_UPSTREAM_HOST = "WONDERWALL_UPSTREAM_HOST"
        const val ENV_BIND_ADDRESS = "WONDERWALL_BIND_ADDRESS"
        const val ENV_AUTO_LOGIN = "WONDERWALL_AUTO_LOGIN"
        const val ENV_OPENID_SCOPES = "WONDERWALL_OPENID_SCOPES"

        const val LOCALHOST = "127.0.0.1"
        const val BIND_HOST = "0.0.0.0"
        const val AUTO_LOGIN_ENABLED = "true"

        const val WELL_KNOWN_OPENID_CONFIGURATION_PATH =
            ".well-known/openid-configuration"

        fun upstreamHost(port: Int): String = "$LOCALHOST:$port"

        fun bindAddress(port: Int = WONDERWALL_HTTP_PORT): String = "$BIND_HOST:$port"

        fun wellKnownUrl(
            baseUrl: String,
            realm: String,
        ): String = "${baseUrl.trimEnd('/')}/realms/$realm/$WELL_KNOWN_OPENID_CONFIGURATION_PATH"
    }

    override fun name(): String = RESOURCE_NAME

    override fun reconcile(
        primary: Application,
        context: Context<Application>,
    ): ReconcileResult<Unit?> {
        val deployment = targetDeployment(primary, context)
        val deploymentName = deployment.metadata.name

        val deploymentResource =
            context.client
                .apps()
                .deployments()
                .inNamespace(primary.metadata.namespace)
                .withName(deploymentName)

        val podSpec =
            deployment.spec?.template?.spec
                ?: error("Deployment '$deploymentName' has no pod spec")

        val desiredSidecar = desiredSidecar(primary)
        val currentContainers = podSpec.containers.orEmpty()

        val alreadyCorrect =
            currentContainers.any { it.name == desiredSidecar.name && sameContainer(it, desiredSidecar) } &&
                currentContainers.count { it.name == desiredSidecar.name } == 1

        if (!alreadyCorrect) {
            deploymentResource.edit { current ->
                val currentPodSpec =
                    current.spec?.template?.spec
                        ?: error("Deployment '$deploymentName' has no pod spec")

                val otherContainers =
                    currentPodSpec.containers
                        .orEmpty()
                        .filterNot { it.name == desiredSidecar.name }

                currentPodSpec.containers = listOf(desiredSidecar) + otherContainers
                current
            }
        }

        return ReconcileResult.noOperation(null)
    }

    fun delete(
        primary: Application,
        context: Context<Application>,
    ) {
        val existing =
            runCatching { targetDeployment(primary, context) }
                .getOrNull()
                ?: return

        val deploymentName = existing.metadata.name
        val deploymentResource =
            context.client
                .apps()
                .deployments()
                .inNamespace(primary.metadata.namespace)
                .withName(deploymentName)

        val podSpec = existing.spec?.template?.spec ?: return

        if (podSpec.containers.orEmpty().none { it.name == WONDERWALL_CONTAINER_NAME }) {
            return
        }

        deploymentResource.edit { current ->
            val currentPodSpec = current.spec?.template?.spec ?: return@edit current
            currentPodSpec.containers =
                currentPodSpec.containers
                    .orEmpty()
                    .filterNot { it.name == WONDERWALL_CONTAINER_NAME }
            current
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun resourceType(): Class<Unit?> = Unit::class.java as Class<Unit?>

    override fun isReady(
        primary: Application,
        context: Context<Application>,
    ): Boolean {
        val deployment =
            runCatching { targetDeployment(primary, context) }
                .getOrNull()
                ?: return false

        val containers =
            deployment.spec
                ?.template
                ?.spec
                ?.containers
                .orEmpty()

        return containers.any { it.name == WONDERWALL_CONTAINER_NAME }
    }

    private fun desiredSidecar(resource: Application): Container =
        Container().apply {
            name = WONDERWALL_CONTAINER_NAME
            image = ApplicationOperatorConfig.wonderwallImage

            ports =
                listOf(
                    ContainerPort().apply {
                        name = WONDERWALL_HTTP_PORT_NAME
                        containerPort = WONDERWALL_HTTP_PORT
                        protocol = WONDERWALL_HTTP_PROTOCOL
                    },
                )

            env =
                listOf(
                    env(ENV_OPENID_CLIENT_ID, keycloakClientService.clientId(resource)),
                    secretEnv(
                        ENV_OPENID_CLIENT_SECRET,
                        wonderwallSecretName(resource),
                        KEYCLOAK_CLIENT_SECRET_KEY,
                    ),
                    env(
                        ENV_OPENID_WELL_KNOWN_URL,
                        wellKnownUrl(
                            baseUrl = ApplicationOperatorConfig.keycloakWellKnownBaseUrl,
                            realm = resource.spec.realm,
                        ),
                    ),
                    env(ENV_INGRESS, keycloakClientService.ingressUrl(resource)),
                    env(ENV_LOG_LEVEL, resource.spec.logLevel),
                    env(ENV_UPSTREAM_HOST, upstreamHost(resource.spec.upstreamPort)),
                    env(ENV_BIND_ADDRESS, bindAddress()),
                    env(ENV_AUTO_LOGIN, AUTO_LOGIN_ENABLED),
                    env(ENV_OPENID_SCOPES, resource.spec.scope),
                )
        }

    private fun env(
        name: String,
        value: String,
    ) = EnvVar().apply {
        this.name = name
        this.value = value
    }

    private fun secretEnv(
        name: String,
        secretName: String,
        key: String,
    ) = EnvVar().apply {
        this.name = name
        valueFrom =
            EnvVarSourceBuilder()
                .withSecretKeyRef(
                    SecretKeySelectorBuilder()
                        .withName(secretName)
                        .withKey(key)
                        .build(),
                ).build()
    }

    private fun sameContainer(
        a: Container,
        b: Container,
    ): Boolean =
        a.name == b.name &&
            a.image == b.image &&
            normalizeEnv(a.env) == normalizeEnv(b.env) &&
            normalizePorts(a.ports) == normalizePorts(b.ports)

    private fun normalizeEnv(env: List<EnvVar>?): List<String> =
        env
            .orEmpty()
            .map { envVar ->
                val secretRef = envVar.valueFrom?.secretKeyRef
                listOfNotNull(
                    envVar.name,
                    envVar.value,
                    secretRef?.name,
                    secretRef?.key,
                ).joinToString("|")
            }.sorted()

    private fun normalizePorts(ports: List<ContainerPort>?): List<Triple<String?, Int?, String?>> =
        ports
            .orEmpty()
            .map { Triple(it.name, it.containerPort, it.protocol) }
            .sortedBy { it.first ?: "" }
}
