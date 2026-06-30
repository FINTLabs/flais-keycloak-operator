package no.novari.keycloak

import no.novari.OperatorConfig
import org.keycloak.OAuth2Constants
import org.keycloak.admin.client.KeycloakBuilder
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import org.koin.dsl.onClose

fun keycloakClientModule() =
    module {
        single {
            KeycloakBuilder
                .builder()
                .serverUrl(OperatorConfig.keycloakBaseUrl)
                .realm(OperatorConfig.keycloakAdminRealm)
                .clientId(OperatorConfig.keycloakClientId)
                .clientSecret(OperatorConfig.keycloakClientSecret)
                .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
                .build()
        }.onClose {
            it?.close()
        }

        singleOf(::KeycloakClientService)
    }
