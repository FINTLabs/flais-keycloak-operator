package no.novari.keycloak.api.model

import no.novari.keycloak.api.annotation.KeycloakDslMarker

@KeycloakDslMarker
class PostLogoutRedirectUrisDsl(
    private val values: MutableList<String>,
) {
    fun sameAsRedirectUris() {
        values += KEYCLOAK_POST_LOGOUT_REDIRECT_URIS
    }

    fun uri(value: String) {
        values += value
    }

    fun uris(vararg values: String) {
        this.values += values
    }

    operator fun String.unaryPlus() {
        uri(this)
    }
}
