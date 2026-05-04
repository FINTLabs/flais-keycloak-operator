import com.marcnuri.helm.Helm
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction
import java.net.URI

abstract class InstallOperatorTask : DefaultTask() {

    @get:InputFile
    abstract val kubeConfig: RegularFileProperty

    @get:Input
    abstract val namespace: Property<String>

    @get:Input
    abstract val image: Property<String>

    @get:InputDirectory
    abstract val crdChartDir: DirectoryProperty

    @get:InputDirectory
    abstract val operatorChartDir: DirectoryProperty

    @get:Input
    abstract val chartVersion: Property<String>

    @TaskAction
    fun install() {
        val kubeConfigYaml = kubeConfig.get().asFile.readText()
        val namespace = namespace.get()
        val chartVersion = chartVersion.get()
        val image = image.get()
        val imageRepository = image.substringBeforeLast(":")
        val imageTag = image.substringAfterLast(":")
        val helmTimeoutSeconds = 60

        val tempChartsDir = temporaryDir.resolve("charts")
        tempChartsDir.deleteRecursively()

        listOf(
            crdChartDir.get().asFile to "flais-keycloak-operator-crd",
            operatorChartDir.get().asFile to "flais-keycloak-operator",
        ).forEach { (src, name) ->
            val dest = tempChartsDir.resolve(name)
            src.copyRecursively(dest)
            val chartYaml = dest.resolve("Chart.yaml")
            chartYaml.writeText(
                chartYaml.readText()
                    .replace(Regex("^version:.*", RegexOption.MULTILINE), "version: $chartVersion")
                    .replace(Regex("^appVersion:.*", RegexOption.MULTILINE), "appVersion: \"$chartVersion\"")
            )
        }

        fun installChart(
            releaseName: String,
            chart: String,
            version: String? = null,
            values: Map<String, String> = emptyMap(),
        ) {
            Helm.upgrade(chart)
                .apply { version?.let { withVersion(it) } }
                .withName(releaseName)
                .withNamespace(namespace)
                .waitReady()
                .withTimeout(helmTimeoutSeconds)
                .withKubeConfigContents(kubeConfigYaml)
                .apply { values.forEach { (k, v) -> set(k, v) } }
                .call()
        }

        fun installChart(
            releaseName: String,
            chart: java.nio.file.Path,
            values: Map<String, String> = emptyMap(),
        ) {
            Helm(chart)
                .upgrade()
                .withName(releaseName)
                .withNamespace(namespace)
                .waitReady()
                .withTimeout(helmTimeoutSeconds)
                .withKubeConfigContents(kubeConfigYaml)
                .apply { values.forEach { (k, v) -> set(k, v) } }
                .call()
        }

        Helm.repo().add()
            .withName("traefik")
            .withUrl(URI.create("https://traefik.github.io/charts"))
            .call()

        installChart(
            releaseName = "traefik",
            chart = "traefik/traefik",
            version = "27.0.2",
            values = mapOf(
                "service.spec.externalTrafficPolicy" to "Local",
                "ports.web.forwardedHeaders.insecure" to "true",
            ),
        )

        installChart(
            releaseName = "flais-keycloak-operator-crd",
            chart = tempChartsDir.resolve("flais-keycloak-operator-crd").toPath(),
        )

        installChart(
            releaseName = "flais-keycloak-operator",
            chart = tempChartsDir.resolve("flais-keycloak-operator").toPath(),
            values = mapOf(
                "image.repository" to imageRepository,
                "image.tag" to imageTag,
                "image.pullPolicy" to "Never",
            ),
        )

        println("Operator installed")
    }
}