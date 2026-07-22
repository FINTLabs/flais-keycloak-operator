package no.novari.operator.client.api.v1alpha1

import io.fabric8.generator.annotation.Required
import io.fabric8.generator.annotation.Size
import io.fabric8.generator.annotation.ValidationRule

data class FlaisAuthenticationSpec(
    // TODO: Create validation webhook for this to make it dynamically configurable
    @ValidationRule(
        "self in ['fint']",
        message = "Invalid realm",
    )
    @ValidationRule(
        "self == oldSelf",
        message = "Realm is immutable",
    )
    @get:Required val realm: String = "",
    @get:Required val clientURI: String = "",
    @get:Required
    @get:Size(min = 1, max = 10)
    val redirectURIs: List<String> = emptyList(),
    val postLogoutRedirectURIs: List<String> = emptyList(),
    val accessTokenLifetime: Int? = null,
    val sessionLifetime: Int? = null,
    val sessionIdleTimeout: Int? = null,
    val secretName: String? = null,
)
