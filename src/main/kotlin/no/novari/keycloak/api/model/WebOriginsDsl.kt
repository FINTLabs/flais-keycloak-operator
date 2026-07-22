package no.novari.keycloak.api.model

import no.novari.keycloak.api.annotation.KeycloakDslMarker

@KeycloakDslMarker
class WebOriginsDsl(
    private val values: MutableList<String>,
) {
    fun sameOrigin() {
        values += KEYCLOAK_WEB_ORIGIN_SAME_ORIGIN
    }

    fun origin(value: String) {
        values += value
    }

    fun origins(vararg values: String) {
        this.values += values
    }

    operator fun String.unaryPlus() {
        origin(this)
    }
}
