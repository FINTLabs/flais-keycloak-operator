package no.novari.application

import io.fabric8.kubernetes.api.model.OwnerReferenceBuilder
import no.novari.application.api.v1alpha1.FlaisAuthentication

const val KEYCLOAK_CLIENT_SECRET_KEY = "client-secret"

fun wonderwallSecretName(primary: FlaisAuthentication): String = "${primary.metadata.name}-wonderwall"

fun ownerReferences(primary: FlaisAuthentication) =
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
