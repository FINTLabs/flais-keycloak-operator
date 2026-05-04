package no.novari.operator

import io.fabric8.kubernetes.client.KubernetesClient
import io.javaoperatorsdk.operator.api.monitoring.Metrics
import java.time.Duration

interface OperatorConfigurationOverrider {
    fun setMetrics(metrics: Metrics)

    fun setKubernetesClient(client: KubernetesClient)

    fun setCloseClientOnStop(stopClientOnStop: Boolean)

    fun setReconciliationTerminationTimeout(timeout: Duration)
}
