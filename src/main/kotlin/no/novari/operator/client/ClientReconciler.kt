package no.novari.operator.client

import io.javaoperatorsdk.operator.AggregatedOperatorException
import io.javaoperatorsdk.operator.OperatorException
import io.javaoperatorsdk.operator.api.reconciler.Context
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration
import io.javaoperatorsdk.operator.api.reconciler.ErrorStatusUpdateControl
import io.javaoperatorsdk.operator.api.reconciler.Reconciler
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl
import io.javaoperatorsdk.operator.processing.retry.GradualRetry
import no.novari.kubernetes.api.ConditionStatus
import no.novari.kubernetes.api.errorCondition
import no.novari.kubernetes.api.readyCondition
import no.novari.kubernetes.operator.updateStatus
import no.novari.kubernetes.operator.workflow.Dependent
import no.novari.kubernetes.operator.workflow.DependentRef
import no.novari.kubernetes.operator.workflow.Workflow
import no.novari.operator.client.api.v1alpha1.FlaisAuthentication
import no.novari.operator.client.api.v1alpha1.FlaisAuthenticationStatus
import no.novari.operator.client.api.v1alpha1.generation
import no.novari.operator.client.api.v1alpha1.keycloakClientId
import no.novari.operator.client.api.v1alpha1.resourceHash
import no.novari.operator.client.api.v1alpha1.withStatusPatch
import no.novari.operator.utils.withLoggingContext
import java.time.Instant

@GradualRetry(maxAttempts = 1)
@ControllerConfiguration
@Workflow(
    [
        Dependent(
            KeycloakClientDR::class,
        ),
        Dependent(
            ClientSecretDR::class,
            dependsOn = [
                DependentRef(KeycloakClientDR::class),
            ],
        ),
    ],
)
class ClientReconciler : Reconciler<FlaisAuthentication> {
    override fun reconcile(
        resource: FlaisAuthentication,
        context: Context<FlaisAuthentication>,
    ): UpdateControl<FlaisAuthentication> =
        withLoggingContext(loggingContext(resource)) {
            prepare(resource, context)

            val result = context.managedWorkflowAndDependentResourceContext().reconcileManagedWorkflow()
            if (result.allDependentResourcesReady()) {
                UpdateControl.patchStatus(
                    resource.withStatusPatch(
                        FlaisAuthenticationStatus(
                            clientID = resource.keycloakClientId(),
                            synchronizationHash = resource.resourceHash(),
                            synchronizationTime = Instant.now(),
                        ).withConditions(
                            readyCondition(
                                ConditionStatus.True,
                                "Synchronized",
                                "Resource is up-to-date",
                                resource.generation(),
                            ),
                            errorCondition(
                                ConditionStatus.False,
                                "Synchronized",
                                "Processing completed without errors",
                                resource.generation(),
                            ),
                        ),
                    ),
                )
            } else {
                UpdateControl.noUpdate()
            }
        }

    override fun updateErrorStatus(
        resource: FlaisAuthentication,
        context: Context<FlaisAuthentication>,
        e: Exception,
    ): ErrorStatusUpdateControl<FlaisAuthentication> =
        withLoggingContext(loggingContext(resource)) {
            ErrorStatusUpdateControl.patchStatus(reconciliationFailureStatus(resource, e))
        }

    private fun prepare(
        resource: FlaisAuthentication,
        context: Context<FlaisAuthentication>,
    ) {
        context.updateStatus(
            resource.withStatusPatch(
                (resource.status ?: FlaisAuthenticationStatus())
                    .withCondition(
                        readyCondition(
                            ConditionStatus.False,
                            "Processing",
                            "Started processing resource",
                            resource.generation(),
                        ),
                    ),
            ),
        )
    }

    private fun reconciliationFailureStatus(
        resource: FlaisAuthentication,
        error: Exception,
    ): FlaisAuthentication {
        val message =
            if (error is OperatorException && error.cause is AggregatedOperatorException) {
                (error.cause as AggregatedOperatorException)
                    .aggregatedExceptions.values
                    .first()
                    .message
            } else {
                error.message
            } ?: "Unknown error"

        return resource.withStatusPatch(
            (resource.status ?: FlaisAuthenticationStatus())
                .copy(clientID = resource.keycloakClientId())
                .withCondition(
                    errorCondition(
                        ConditionStatus.True,
                        "Failed",
                        message,
                        resource.generation(),
                    ),
                ),
        )
    }

    private fun loggingContext(resource: FlaisAuthentication): Map<String, String> =
        mapOf(
            "keycloak.realm" to resource.spec.realm,
        )
}
