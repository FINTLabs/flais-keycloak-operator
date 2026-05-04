package no.novari.utils

import org.keycloak.admin.client.Keycloak
import org.keycloak.admin.client.KeycloakBuilder
import org.keycloak.admin.client.resource.RealmResource
import org.keycloak.representations.idm.ClientRepresentation

/**
 * Utility wrapper around the Keycloak Admin Client used in tests.
 * Provides convenience functions for connecting to the realm, managing users and organization membership,
 * and performing on-demand tasks
 */
object KcAdminClient {
    private const val ADMIN_REALM = "master"
    private const val ADMIN_CLIENT_ID = "admin-cli"

    fun connect(
        env: OperatorEnvironment,
        realm: String,
    ): Pair<Keycloak, RealmResource> {
        val kc =
            KeycloakBuilder
                .builder()
                .serverUrl(env.keycloakServiceUrl())
                .realm(ADMIN_REALM)
                .clientId(ADMIN_CLIENT_ID)
                .username(env.keycloakAdminUser)
                .password(env.keycloakAdminPassword)
                .build()
        return kc to kc.realm(realm)
    }

    fun getClientRepresentation(
        realm: RealmResource,
        clientId: String,
    ): ClientRepresentation? =
        realm
            .clients()
            .findByClientId(clientId)
            .firstOrNull()
            ?.let { realm.clients().get(it.id).toRepresentation() }
}
