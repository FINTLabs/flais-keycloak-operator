package no.novari.utils

import com.marcnuri.helm.Helm
import com.marcnuri.helm.InstallCommand
import java.net.URI
import java.nio.file.Path

class HelmClient(
    private val kubeConfigYaml: String,
    private val namespace: String,
) {
    private val helmTimeoutSeconds = 60

    fun installChart(
        releaseName: String,
        chart: Path,
        values: Map<String, String> = emptyMap(),
        valuesFile: Path? = null,
    ) {
        installChart(
            command = Helm(chart).install(),
            releaseName = releaseName,
            values = values,
            valuesFile = valuesFile,
        )
    }

    fun installChart(
        releaseName: String,
        chart: String,
        version: String? = null,
        values: Map<String, String> = emptyMap(),
        valuesFile: Path? = null,
    ) {
        installChart(
            command =
                Helm.install(chart).apply {
                    version?.let { withVersion(it) }
                },
            releaseName = releaseName,
            values = values,
            valuesFile = valuesFile,
        )
    }

    private fun installChart(
        command: InstallCommand,
        releaseName: String,
        values: Map<String, String>,
        valuesFile: Path?,
    ) {
        command
            .withName(releaseName)
            .withNamespace(namespace)
            .waitReady()
            .withTimeout(helmTimeoutSeconds)
            .withKubeConfigContents(kubeConfigYaml)
            .apply {
                values.forEach { (key, value) ->
                    set(key, value)
                }

                valuesFile?.let {
                    withValuesFile(it)
                }
            }.call()
    }

    fun addRepository(
        repositoryName: String,
        repositoryUrl: String,
    ) {
        Helm
            .repo()
            .add()
            .withName(repositoryName)
            .withUrl(URI.create(repositoryUrl))
            .call()
    }
}
