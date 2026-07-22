package no.novari.operator.client

import io.javaoperatorsdk.operator.api.reconciler.Context
import io.javaoperatorsdk.operator.api.reconciler.dependent.Deleter
import io.javaoperatorsdk.operator.processing.ResourceIDMapper.singleResourceResourceIDMapper
import io.javaoperatorsdk.operator.processing.dependent.Creator
import io.javaoperatorsdk.operator.processing.dependent.Matcher
import io.javaoperatorsdk.operator.processing.dependent.Updater
import io.javaoperatorsdk.operator.processing.dependent.external.PerResourcePollingDependentResource
import no.novari.keycloak.ClientRepresentationComparator
import no.novari.keycloak.KeycloakClientNameGenerator
import no.novari.keycloak.KeycloakClientService
import no.novari.keycloak.api.model.clientRepresentation
import no.novari.operator.client.api.v1alpha1.FlaisAuthentication
import no.novari.operator.client.api.v1alpha1.keycloakClientId
import org.keycloak.representations.idm.ClientRepresentation
import org.koin.core.component.KoinComponent
import kotlin.time.Duration.Companion.days
import kotlin.time.toJavaDuration

class KeycloakClientDR(
    private val keycloakClientService: KeycloakClientService,
) : PerResourcePollingDependentResource<ClientRepresentation, FlaisAuthentication, String>(
        ClientRepresentation::class.java,
    ),
    Creator<ClientRepresentation, FlaisAuthentication>,
    Updater<ClientRepresentation, FlaisAuthentication>,
    Deleter<FlaisAuthentication>,
    KoinComponent {
    override fun name(): String = "keycloak-client"

    init {
        setResourceIDMapper(singleResourceResourceIDMapper())
        pollingPeriod = 7.days.toJavaDuration()
    }

    override fun desired(
        primary: FlaisAuthentication,
        context: Context<FlaisAuthentication>,
    ): ClientRepresentation =
        clientRepresentation {
            clientId = primary.keycloakClientId()
            name = KeycloakClientNameGenerator.generate(primary)

            webOrigins {
                origin(primary.spec.clientURI)
            }
            redirectUris(primary.spec.redirectURIs)
            postLogoutRedirectUris {
                if (primary.spec.postLogoutRedirectURIs.isNotEmpty()) {
                    uris(*primary.spec.postLogoutRedirectURIs.toTypedArray())
                } else {
                    sameAsRedirectUris()
                }
            }

            sessionLifetime = primary.spec.sessionLifetime
            sessionIdleTimeout = primary.spec.sessionIdleTimeout
            accessTokenLifetime = primary.spec.accessTokenLifetime
        }

    override fun create(
        desired: ClientRepresentation,
        primary: FlaisAuthentication,
        context: Context<FlaisAuthentication>,
    ): ClientRepresentation = keycloakClientService.createClient(primary.spec.realm, desired)

    override fun update(
        actual: ClientRepresentation,
        desired: ClientRepresentation,
        primary: FlaisAuthentication,
        context: Context<FlaisAuthentication>,
    ): ClientRepresentation = keycloakClientService.updateClient(primary.spec.realm, actual.clientId, desired)

    override fun match(
        actual: ClientRepresentation,
        primary: FlaisAuthentication,
        context: Context<FlaisAuthentication>,
    ): Matcher.Result<ClientRepresentation> {
        val desired = desired(primary, context)
        return Matcher.Result.computed(
            ClientRepresentationComparator.matches(actual, desired),
            desired,
        )
    }

    override fun delete(
        primary: FlaisAuthentication,
        context: Context<FlaisAuthentication>,
    ) {
        keycloakClientService.deleteClient(primary.spec.realm, primary.keycloakClientId())
    }

    override fun fetchResources(primary: FlaisAuthentication): Set<ClientRepresentation> =
        keycloakClientService
            .findClient(primary.spec.realm, primary.keycloakClientId())
            ?.let { setOf(it) }
            ?: emptySet()
}
