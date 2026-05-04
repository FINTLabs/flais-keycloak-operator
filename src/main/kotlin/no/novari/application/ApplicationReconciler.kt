package no.novari.application

import io.javaoperatorsdk.operator.api.reconciler.Cleaner
import io.javaoperatorsdk.operator.api.reconciler.Context
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration
import io.javaoperatorsdk.operator.api.reconciler.DeleteControl
import io.javaoperatorsdk.operator.api.reconciler.Reconciler
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl
import io.javaoperatorsdk.operator.processing.retry.GradualRetry
import no.novari.application.api.v1alpha1.Application
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
        Dependent(
            WonderwallDeploymentDR::class,
            dependsOn = [DependentRef(KeycloakClientSecretDR::class)],
        ),
        Dependent(
            WonderwallServiceDR::class,
            dependsOn = [DependentRef(WonderwallDeploymentDR::class)],
        ),
        Dependent(
            TraefikIngressRouteDR::class,
            dependsOn = [DependentRef(WonderwallServiceDR::class)],
        ),
    ],
)
class ApplicationReconciler(
    private val wonderwallDeploymentDR: WonderwallDeploymentDR,
    private val keycloakClientService: KeycloakClientService,
) : Reconciler<Application>,
    Cleaner<Application> {
    override fun reconcile(
        resource: Application,
        context: Context<Application>,
    ): UpdateControl<Application> {
        context.managedWorkflowAndDependentResourceContext().reconcileManagedWorkflow()
        return UpdateControl.noUpdate()
    }

    override fun cleanup(
        resource: Application,
        context: Context<Application>,
    ): DeleteControl {
        wonderwallDeploymentDR.delete(resource, context)
        keycloakClientService.deleteClientIfExists(resource)
        return DeleteControl.defaultDelete()
    }
}
