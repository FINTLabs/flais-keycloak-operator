package no.novari.application

import io.javaoperatorsdk.operator.api.reconciler.Cleaner
import io.javaoperatorsdk.operator.api.reconciler.Context
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration
import io.javaoperatorsdk.operator.api.reconciler.DeleteControl
import io.javaoperatorsdk.operator.api.reconciler.Reconciler
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl
import io.javaoperatorsdk.operator.processing.retry.GradualRetry
import no.novari.application.api.v1alpha1.FlaisAuthentication
import no.novari.operator.workflow.Dependent
import no.novari.operator.workflow.DependentRef
import no.novari.operator.workflow.Workflow

@GradualRetry(maxAttempts = 3)
@ControllerConfiguration
@Workflow(
    [
        Dependent(KeycloakClientDR::class),
        Dependent(
            KeycloakClientSecretDR::class,
            dependsOn = [DependentRef(KeycloakClientDR::class)],
        ),
    ],
)
class ApplicationReconciler(
    private val keycloakClientService: KeycloakClientService,
) : Reconciler<FlaisAuthentication>,
    Cleaner<FlaisAuthentication> {
    override fun reconcile(
        resource: FlaisAuthentication,
        context: Context<FlaisAuthentication>,
    ): UpdateControl<FlaisAuthentication> {
        context.managedWorkflowAndDependentResourceContext().reconcileManagedWorkflow()
        return UpdateControl.noUpdate()
    }

    override fun cleanup(
        resource: FlaisAuthentication,
        context: Context<FlaisAuthentication>,
    ): DeleteControl {
        keycloakClientService.deleteClientIfExists(resource)
        return DeleteControl.defaultDelete()
    }
}
