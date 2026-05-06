package no.novari.application

import io.javaoperatorsdk.operator.api.reconciler.Reconciler
import org.koin.dsl.module

fun applicationReconcilerModule() =
    module {
        single { KeycloakClientService() }

        single { KeycloakClientDR(get()) }
        single { KeycloakClientSecretDR() }

        single<Reconciler<*>> {
            ApplicationReconciler(
                keycloakClientService = get(),
            )
        }
    }
