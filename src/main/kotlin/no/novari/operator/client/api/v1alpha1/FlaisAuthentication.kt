package no.novari.operator.client.api.v1alpha1

import io.fabric8.kubernetes.api.model.Namespaced
import io.fabric8.kubernetes.client.CustomResource
import io.fabric8.kubernetes.model.annotation.Group
import io.fabric8.kubernetes.model.annotation.Kind
import io.fabric8.kubernetes.model.annotation.Version
import no.novari.kubernetes.api.model.metadata
import no.novari.operator.utils.createHash

@Group("novari.no")
@Version("v1alpha1")
@Kind("FlaisAuthentication")
class FlaisAuthentication :
    CustomResource<FlaisAuthenticationSpec, FlaisAuthenticationStatus>(),
    Namespaced {
    override fun initStatus(): FlaisAuthenticationStatus {
        return FlaisAuthenticationStatus()
    }
}

fun FlaisAuthentication.withStatusPatch(status: FlaisAuthenticationStatus): FlaisAuthentication {
    return FlaisAuthentication().apply {
        metadata =
            metadata {
                name(this@withStatusPatch.metadata.name)
                namespace(this@withStatusPatch.metadata.namespace)
            }
        this.status = status
    }
}

fun FlaisAuthentication.resourceHash(): String {
    val values =
        mapOf(
            "spec" to spec,
            "labels" to metadata.labels,
            "changeCause" to metadata.annotations?.get("kubernetes.io/change-cause"),
        )
    return createHash(values)
}

fun FlaisAuthentication.keycloakClientId(): String = metadata.uid ?: error("FlaisAuthentication '${metadata.name}' has no Kubernetes UID")

fun FlaisAuthentication.generation(): Long = metadata.generation
