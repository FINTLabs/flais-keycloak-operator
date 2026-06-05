package no.novari.keycloak

import org.eclipse.jetty.http.HttpStatus
import org.keycloak.admin.client.CreatedResponseUtil
import org.keycloak.admin.client.Keycloak
import org.keycloak.admin.client.resource.RealmResource
import org.keycloak.representations.idm.ClientRepresentation

class KeycloakClientService(
    private val keycloakClient: Keycloak,
) {
    fun clientExists(
        realm: String,
        clientId: String,
    ): Boolean = findClient(realm, clientId) != null

    fun createClient(
        realm: String,
        desired: ClientRepresentation,
    ): ClientRepresentation {
        val realm = keycloakClient.realm(realm)
        val response = realm.clients().create(desired)
        response.use {
            if (it.status !in 200..299) {
                error("Failed to create Keycloak client '${desired.clientId}': HTTP ${it.status}")
            }
            val id = CreatedResponseUtil.getCreatedId(it)
            return realm.clients().get(id).toRepresentation()
        }
    }

    fun updateClient(
        realm: String,
        clientId: String,
        client: ClientRepresentation,
    ): ClientRepresentation {
        val realm = keycloakClient.realm(realm)
        realm.clients().get(clientId).update(client)
        return realm.clients().get(clientId).toRepresentation()
    }

    fun deleteClient(
        realm: String,
        clientId: String,
    ): Boolean {
        val realm = keycloakClient.realm(realm)
        val res = realm.clients().delete(clientId)
        return HttpStatus.isSuccess(res.status)
    }

    fun findClient(
        realm: String,
        clientId: String,
    ): ClientRepresentation? =
        keycloakClient
            .realm(realm)
            .clients()
            .get(clientId)
            ?.toRepresentation()

    fun getClientSecret(
        realm: String,
        clientId: String,
    ): String {
        if (!clientExists(realm, clientId)) {
            error("Keycloak client '$clientId' does not exist in realm '$realm'")
        }
        return getRealm(realm)
            .clients()
            .get(clientId)
            .secret.value
            ?: error("Keycloak client '$clientId' has no secret")
    }

    fun getRealm(realm: String): RealmResource = keycloakClient.realm(realm) ?: error("Keycloak client '$realm' does not exist")
}
