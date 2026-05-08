package no.novari.application.api.v1alpha1

import io.fabric8.generator.annotation.Required
import io.fabric8.generator.annotation.Size
import io.fabric8.generator.annotation.ValidationRule

data class FlaisAuthenticationSpec(
    @ValidationRule(
        "self in ['fint']",
        message = "Invalid realm",
    )
    @get:Required val realm: String = "",
    @get:Required val wonderwall: WonderwallConfig = WonderwallConfig(),
    val ingress: List<Ingress> = emptyList(),
)

data class Ingress(
    @get:Required val host: String = "",
    val path: String = "",
)

data class WonderwallConfig(
    @get:Required val upstreamPort: Int = 0,
    val autoLogin: Boolean = true,
    @Size(max = 2)
    @ValidationRule(
        "self.all(scope, scope in ['profile', 'organization'])",
        message = "Invalid scope, must be one of profile, organization",
    )
    val scope: List<String> = listOf("profile"),
    @ValidationRule(
        "self in ['info', 'debug']",
        message = "Invalid logLevel, must be one of info, debug",
    )
    val logLevel: String = "info",
)
