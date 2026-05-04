package no.novari

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.fabric8.kubernetes.client.KubernetesClientBuilder
import io.fabric8.kubernetes.client.utils.KubernetesSerialization
import io.javaoperatorsdk.operator.Operator
import io.javaoperatorsdk.operator.api.config.ConfigurationService
import io.javaoperatorsdk.operator.api.monitoring.Metrics
import io.javaoperatorsdk.operator.api.reconciler.Reconciler
import io.javaoperatorsdk.operator.monitoring.micrometer.MicrometerMetricsV2
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.novari.application.applicationReconcilerModule
import no.novari.operator.OperatorConfigHandler
import no.novari.operator.OperatorConfiguration
import no.novari.operator.OperatorPostConfigHandler
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.server.Jetty
import org.http4k.server.asServer
import org.koin.core.context.startKoin
import org.koin.dsl.module
import org.koin.mp.KoinPlatform.getKoin

private const val PORT = 8080

fun main() {
    startKoin { modules(applicationReconcilerModule(), baseModule) }
    startHttpServer()
    startOperator()
}

val baseModule =
    module {
        single {
            PrometheusMeterRegistry(PrometheusConfig.DEFAULT).apply {
                JvmMemoryMetrics().bindTo(this)
                JvmGcMetrics().bindTo(this)
                ProcessorMetrics().bindTo(this)
            }
        }
        single { ObjectMapper().apply { registerKotlinModule() } }
        single {
            KubernetesClientBuilder()
                .withKubernetesSerialization(KubernetesSerialization(get(), true))
                .build()
        }
        single<Metrics> {
            MicrometerMetricsV2.newBuilder(get<PrometheusMeterRegistry>()).build()
        }
        single {
            OperatorPostConfigHandler { operator ->
                getAll<Reconciler<*>>().forEach { operator.register(it) }
            }
        }
        single<ConfigurationService> {
            OperatorConfiguration().apply {
                getAll<OperatorConfigHandler>().reversed().forEach { it.accept(this) }
            }
        }
        single {
            Operator(get<ConfigurationService>()).apply {
                get<OperatorPostConfigHandler>().accept(this)
            }
        }
        single<HttpHandler> {
            val registry: PrometheusMeterRegistry = get()
            val operator: Operator = get()
            routes(
                "/metrics" bind Method.GET to { Response(Status.OK).body(registry.scrape()) },
                "/health" bind Method.GET to {
                    if (operator.runtimeInfo.isStarted) {
                        Response(Status.OK).body("OK")
                    } else {
                        Response(Status.SERVICE_UNAVAILABLE).body("NOT OK")
                    }
                },
                "/ready" bind Method.GET to {
                    if (operator.runtimeInfo.isStarted) {
                        Response(Status.OK).body("READY")
                    } else {
                        Response(Status.SERVICE_UNAVAILABLE).body("NOT READY")
                    }
                },
            )
        }
    }

fun startHttpServer() {
    val server = getKoin().get<HttpHandler>().asServer(Jetty(PORT)).start()
    Runtime.getRuntime().addShutdownHook(Thread { server.stop() })
}

fun startOperator() {
    val operator = getKoin().get<Operator>()
    Runtime.getRuntime().addShutdownHook(Thread { operator.stop() })
    operator.start()
}
