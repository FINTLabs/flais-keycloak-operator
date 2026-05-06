package no.novari.application.api.v1alpha1

data class FlaisAuthenticationSpec(
    val hostname: String = "",
    val basePath: String = "",
    val realm: String = "",
    val scope: String = "profile",
    val logLevel: String = "info",
)
