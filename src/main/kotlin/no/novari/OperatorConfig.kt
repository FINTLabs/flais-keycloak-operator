package no.novari

object OperatorConfig {
    val keycloakBaseUrl: String =
        System.getenv("KEYCLOAK_BASE_URL")
            ?: error("Missing environment variable 'KEYCLOAK_BASE_URL'")
    val keycloakAdminRealm: String =
        System.getenv("KEYCLOAK_ADMIN_REALM") ?: "master"
    val keycloakClientId: String =
        System.getenv("KEYCLOAK_CLIENT_ID") ?: "keycloak-operator"
    val keycloakClientSecret: String =
        System.getenv("KEYCLOAK_CLIENT_SECRET") ?: error("Missing environment variable 'KEYCLOAK_CLIENT_SECRET'")
    val env: String =
        System.getenv("ENV") ?: error("Missing environment variable 'ENV'")
}
