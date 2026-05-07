package no.novari.application

import java.lang.System

object ApplicationOperatorConfig {
    val keycloakBaseUrl: String =
        System.getenv("KEYCLOAK_BASE_URL")
            ?: error("Missing environment variable 'KEYCLOAK_WELL_KNOWN_BASE_URL'")
    val keycloakAdminRealm: String =
        System.getenv("KEYCLOAK_ADMIN_REALM") ?: "master"
    val keycloakAdminClientId: String =
        System.getenv("KEYCLOAK_ADMIN_CLIENT_ID") ?: "admin-cli"
    val keycloakAdminUsername: String =
        System.getenv("KEYCLOAK_ADMIN_USERNAME") ?: error("Missing environment variable 'KEYCLOAK_ADMIN_USERNAME'")
    val keycloakAdminPassword: String =
        System.getenv("KEYCLOAK_ADMIN_PASSWORD") ?: error("Missing environment variable 'KEYCLOAK_ADMIN_PASSWORD'")
}
