package no.novari.keycloak.client

import com.marcnuri.helm.Helm
import com.marcnuri.helm.InstallCommand
import java.nio.file.Path

class HelmClient(
    private val kubeConfigYaml: String,
    private val kubeconfigPath: Path,
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

    private fun installChart(
        command: InstallCommand,
        releaseName: String,
        values: Map<String, String>,
        valuesFile: Path?,
    ) {
        command
            .withKubeConfig(kubeconfigPath)
            .withKubeConfigContents(kubeConfigYaml)
            .withName(releaseName)
            .withNamespace(namespace)
            .waitReady()
            .withTimeout(helmTimeoutSeconds)
            .apply {
                values.forEach { (key, value) ->
                    set(key, value)
                }

                valuesFile?.let {
                    withValuesFile(it)
                }
            }.call()
    }
}
