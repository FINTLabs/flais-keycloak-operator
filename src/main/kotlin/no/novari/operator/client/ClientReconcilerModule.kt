package no.novari.operator.client

import io.javaoperatorsdk.operator.api.reconciler.Reconciler
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

fun clientReconcilerModule() =
    module {
        singleOf(::KeycloakClientDR)
        singleOf(::ClientSecretDR)
        singleOf(::ClientReconciler).bind<Reconciler<*>>()
    }
