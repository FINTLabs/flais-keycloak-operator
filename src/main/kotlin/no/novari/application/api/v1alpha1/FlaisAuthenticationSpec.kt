package no.novari.application.api.v1alpha1

data class FlaisAuthenticationSpec(
    val ingress: List<Ingress> = emptyList(),
    val wonderwall: WonderwallConfig = WonderwallConfig(),
    val realm: String = "",
)

data class Ingress(
    val host: String = "",
    val path: String = "",
)

data class WonderwallConfig(
    val autoLogin: Boolean = true,
    val upstreamPort: Int = 3000,
    val scope: String = "profile",
    val logLevel: String = "info",
)