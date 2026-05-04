package no.novari.application

import io.javaoperatorsdk.operator.api.reconciler.Context
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource
import io.javaoperatorsdk.operator.api.reconciler.dependent.ReconcileResult
import no.novari.application.api.v1alpha1.Application
import no.novari.operator.dependent.ReadyCondition

class KeycloakClientDR(
    private val keycloakClientService: KeycloakClientService,
) : DependentResource<Unit, Application>,
    ReadyCondition<Application> {
    override fun name(): String = "keycloak-client"

    override fun reconcile(
        primary: Application,
        context: Context<Application>,
    ): ReconcileResult<Unit?> {
        keycloakClientService.ensureClient(primary)
        return ReconcileResult.noOperation(null)
    }

    @Suppress("UNCHECKED_CAST")
    override fun resourceType(): Class<Unit?> = Unit::class.java as Class<Unit?>

    override fun isReady(
        primary: Application,
        context: Context<Application>,
    ): Boolean = true
}
