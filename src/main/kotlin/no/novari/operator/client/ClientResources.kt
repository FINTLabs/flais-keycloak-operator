package no.novari.operator.client

import io.fabric8.kubernetes.api.model.OwnerReferenceBuilder
import no.novari.operator.client.api.v1alpha1.FlaisAuthentication

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
