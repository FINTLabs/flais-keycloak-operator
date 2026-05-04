package no.novari.application

import java.lang.System

object ApplicationOperatorConfig {
    val wonderwallImage: String =
        System.getenv("WONDERWALL_IMAGE") ?: error("Missing environment variable 'WONDERWALL_IMAGE'")
    val keycloakWellKnownBaseUrl: String =
        System.getenv("KEYCLOAK_WELL_KNOWN_BASE_URL")
            ?: error("Missing environment variable 'KEYCLOAK_WELL_KNOWN_BASE_URL'")
}
