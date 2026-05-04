package no.novari.application.api.v1alpha1

data class ApplicationSpec(
    val hostname: String = "",
    val basePath: String = "",
    val realm: String = "",
    val upstreamPort: Int = 3000,
    val scope: String = "profile",
    val logLevel: String = "info",
)
