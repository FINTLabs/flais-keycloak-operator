package no.novari.kubernetes.operator

import io.fabric8.kubernetes.client.CustomResource
import io.javaoperatorsdk.operator.api.reconciler.Context

fun Context<*>.updateStatus(resource: CustomResource<*, *>) {
    client.resource(resource).updateStatus()
}

inline fun <reified R> Context<*>.getSecondaryResource(): R? = this.getSecondaryResource<R>(R::class.java).orElse(null)

inline fun <reified R> Context<*>.getRequiredSecondaryResource(): R =
    getSecondaryResource<R>() ?: error("Missing required secondary resource")
