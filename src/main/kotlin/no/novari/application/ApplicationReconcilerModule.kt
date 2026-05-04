package no.novari.application

import io.javaoperatorsdk.operator.api.reconciler.Reconciler
import org.koin.dsl.module

fun applicationReconcilerModule() =
    module {
        single { KeycloakClientService() }

        single { KeycloakClientDR(get()) }
        single { KeycloakClientSecretDR() }
        single { WonderwallDeploymentDR(get()) }
        single { WonderwallServiceDR() }
        single { TraefikIngressRouteDR() }

        single<Reconciler<*>> {
            ApplicationReconciler(
                wonderwallDeploymentDR = get(),
                keycloakClientService = get(),
            )
        }
    }
