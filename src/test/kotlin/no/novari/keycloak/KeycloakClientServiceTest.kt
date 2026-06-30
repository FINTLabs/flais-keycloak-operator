package no.novari.keycloak

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import jakarta.ws.rs.core.Response
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.keycloak.admin.client.Keycloak
import org.keycloak.admin.client.resource.ClientResource
import org.keycloak.admin.client.resource.ClientsResource
import org.keycloak.admin.client.resource.RealmResource
import org.keycloak.representations.idm.ClientRepresentation
import org.keycloak.representations.idm.CredentialRepresentation
import java.net.URI

class KeycloakClientServiceTest {
    private val keycloakClient = mockk<Keycloak>()
    private val realm = mockk<RealmResource>()
    private val clients = mockk<ClientsResource>()
    private val clientResource = mockk<ClientResource>()

    private val service = KeycloakClientService(keycloakClient)

    @BeforeEach
    fun setUp() {
        every { keycloakClient.realm(REALM) } returns realm
        every { realm.clients() } returns clients
    }

    @Test
    fun `create client returns created client with secret`() {
        val desired = ClientRepresentation().apply { clientId = CLIENT_ID }

        every { clients.create(desired) } returns createdResponse()
        every { clients.get(INTERNAL_ID) } returns clientResource
        every { clientResource.toRepresentation() } returns clientRepresentation()
        every { clientResource.secret } returns credential()

        val actual = service.createClient(REALM, desired)

        assertEquals(CLIENT_ID, actual.clientId)
        assertEquals(SECRET, actual.secret)
        verify {
            clients.create(desired)
            clients.get(INTERNAL_ID)
            clientResource.secret
        }
    }

    @Test
    fun `update client resolves internal id from client id`() {
        val desired = ClientRepresentation()

        every { clients.findByClientId(CLIENT_ID) } returns listOf(clientRepresentation())
        every { clients.get(INTERNAL_ID) } returns clientResource
        every { clientResource.update(desired) } just Runs
        every { clientResource.toRepresentation() } returns clientRepresentation()
        every { clientResource.secret } returns credential()

        val actual = service.updateClient(REALM, CLIENT_ID, desired)

        assertEquals(CLIENT_ID, desired.clientId)
        assertEquals(SECRET, actual.secret)
        verify {
            clients.findByClientId(CLIENT_ID)
            clients.get(INTERNAL_ID)
            clientResource.update(desired)
        }
    }

    @Test
    fun `update client rejects changed desired client id`() {
        val desired = ClientRepresentation().apply { clientId = UPDATED_CLIENT_ID }

        val error =
            assertThrows(IllegalArgumentException::class.java) {
                service.updateClient(REALM, CLIENT_ID, desired)
            }

        assertEquals("Keycloak client ID is immutable: cannot change '$CLIENT_ID' to '$UPDATED_CLIENT_ID'", error.message)
        verify(exactly = 0) {
            clients.findByClientId(any())
            clientResource.update(any())
        }
    }

    @Test
    fun `delete client resolves internal id from client id`() {
        every { clients.findByClientId(CLIENT_ID) } returns listOf(clientRepresentation())
        every { clients.delete(INTERNAL_ID) } returns Response.noContent().build()

        assertTrue(service.deleteClient(REALM, CLIENT_ID))
        verify {
            clients.findByClientId(CLIENT_ID)
            clients.delete(INTERNAL_ID)
        }
    }

    @Test
    fun `delete client returns false when client id is not found`() {
        every { clients.findByClientId(CLIENT_ID) } returns emptyList()

        assertFalse(service.deleteClient(REALM, CLIENT_ID))
        verify(exactly = 0) { clients.delete(any()) }
    }

    private fun createdResponse(): Response =
        Response
            .created(URI.create("http://keycloak/admin/realms/$REALM/clients/$INTERNAL_ID"))
            .build()

    private fun clientRepresentation(
        id: String = INTERNAL_ID,
        clientId: String? = CLIENT_ID,
    ): ClientRepresentation =
        ClientRepresentation().apply {
            this.id = id
            this.clientId = clientId
        }

    private fun credential(): CredentialRepresentation =
        CredentialRepresentation().apply {
            value = SECRET
        }

    private companion object {
        private const val REALM = "fint"
        private const val CLIENT_ID = "client-id"
        private const val UPDATED_CLIENT_ID = "updated-client-id"
        private const val INTERNAL_ID = "internal-id"
        private const val SECRET = "secret"
    }
}
