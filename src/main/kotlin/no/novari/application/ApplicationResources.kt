package no.novari.application

import io.fabric8.kubernetes.api.model.OwnerReferenceBuilder
import io.fabric8.kubernetes.api.model.apps.Deployment
import io.javaoperatorsdk.operator.api.reconciler.Context
import no.novari.application.api.v1alpha1.Application

const val WONDERWALL_CONTAINER_NAME = "wonderwall"
const val KEYCLOAK_CLIENT_SECRET_KEY = "client-secret"

class TargetDeploymentNotFoundException(
    deploymentName: String,
    namespace: String,
) : IllegalStateException("Target Deployment '$deploymentName' not found in namespace '$namespace'")

fun targetDeploymentName(primary: Application): String = primary.metadata.name

fun targetDeployment(
    primary: Application,
    context: Context<Application>,
): Deployment {
    val namespace = primary.metadata.namespace
    val deploymentName = targetDeploymentName(primary)

    return context.client
        .apps()
        .deployments()
        .inNamespace(namespace)
        .withName(deploymentName)
        .get()
        ?: throw TargetDeploymentNotFoundException(deploymentName, namespace)
}

fun wonderwallSecretName(primary: Application): String = "${primary.metadata.name}-wonderwall"

fun ownerReferences(primary: Application) =
    listOf(
        OwnerReferenceBuilder()
            .withApiVersion(primary.apiVersion)
            .withKind(primary.kind)
            .withName(primary.metadata.name)
            .withUid(primary.metadata.uid)
            .withController(true)
            .withBlockOwnerDeletion(true)
            .build(),
    )
