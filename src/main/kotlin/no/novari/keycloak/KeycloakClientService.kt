package no.novari.keycloak

import org.eclipse.jetty.http.HttpStatus
import org.keycloak.admin.client.CreatedResponseUtil
import org.keycloak.admin.client.Keycloak
import org.keycloak.admin.client.resource.ClientResource
import org.keycloak.admin.client.resource.ClientsResource
import org.keycloak.admin.client.resource.RealmResource
import org.keycloak.representations.idm.ClientRepresentation

class KeycloakClientService(
    private val keycloakClient: Keycloak,
) {
    fun createClient(
        realm: String,
        desired: ClientRepresentation,
    ): ClientRepresentation {
        val clients = getClientsResource(realm)
        val response = clients.create(desired)

        response.use {
            if (it.status !in 200..299) {
                error("Failed to create Keycloak client '${desired.clientId}': HTTP ${it.status}")
            }

            val id =
                CreatedResponseUtil.getCreatedId(it)
                    ?: error("Failed to create Keycloak client '${desired.clientId}': missing created client ID")

            return clients.get(id).toRepresentationWithSecret()
        }
    }

    fun updateClient(
        realm: String,
        clientId: String,
        client: ClientRepresentation,
    ): ClientRepresentation {
        client.ensureImmutableClientId(clientId)

        return getClientResource(realm, clientId).let {
            it.update(client)
            it.toRepresentationWithSecret()
        }
    }

    fun deleteClient(
        realm: String,
        clientId: String,
    ): Boolean =
        getClientsResource(realm).let { clients ->
            val client = clients.findByClientIdOrNull(clientId) ?: return false
            clients.delete(client.internalId(clientId)).use {
                HttpStatus.isSuccess(it.status)
            }
        }

    fun findClient(
        realm: String,
        clientId: String,
    ): ClientRepresentation? = getClientsResource(realm).findResourceByClientId(clientId)?.toRepresentationWithSecret()

    fun getClientSecret(
        realm: String,
        clientId: String,
    ): String = getClientResource(realm, clientId).secretValue(clientId)

    private fun getClientsResource(realm: String): ClientsResource = getRealmResource(realm).clients()

    private fun getClientResource(
        realm: String,
        clientId: String,
    ): ClientResource = getClientsResource(realm).getResourceByClientId(clientId)

    private fun getRealmResource(realm: String): RealmResource =
        keycloakClient.realm(realm) ?: error("Keycloak realm '$realm' does not exist")

    private fun ClientsResource.getResourceByClientId(clientId: String): ClientResource =
        findResourceByClientId(clientId) ?: error("Keycloak client '$clientId' does not exist")

    private fun ClientsResource.findResourceByClientId(clientId: String): ClientResource? =
        findByClientIdOrNull(clientId)?.let {
            get(it.internalId(clientId))
        }

    private fun ClientsResource.findByClientIdOrNull(clientId: String): ClientRepresentation? =
        findByClientId(clientId).firstOrNull { it.clientId == clientId }

    private fun ClientResource.toRepresentationWithSecret(): ClientRepresentation {
        val representation = toRepresentation()
        val clientId = representation.clientId ?: representation.id ?: error("Keycloak client representation has no client ID")
        representation.secret = secretValue(clientId)
        return representation
    }

    private fun ClientResource.secretValue(clientId: String): String = secret.value ?: error("Keycloak client '$clientId' has no secret")

    private fun ClientRepresentation.ensureImmutableClientId(clientId: String) {
        val desiredClientId = this.clientId?.takeIf { it.isNotBlank() }
        require(desiredClientId == null || desiredClientId == clientId) {
            "Keycloak client ID is immutable: cannot change '$clientId' to '$desiredClientId'"
        }

        this.clientId = clientId
    }

    private fun ClientRepresentation.internalId(clientId: String): String = id ?: error("Keycloak client '$clientId' has no internal ID")
}
