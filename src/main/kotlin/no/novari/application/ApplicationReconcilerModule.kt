package no.novari.application

import io.javaoperatorsdk.operator.api.reconciler.Reconciler
import org.koin.dsl.module

fun applicationReconcilerModule() =
    module {
        single { KeycloakClientService(get()) }

        single { KeycloakClientDR(get()) }
        single { WonderwallSecretDR() }

        single<Reconciler<*>> {
            ApplicationReconciler(
                keycloakClientService = get(),
            )
        }
    }
