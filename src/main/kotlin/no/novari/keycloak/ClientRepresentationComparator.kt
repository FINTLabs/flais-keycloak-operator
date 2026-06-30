package no.novari.keycloak

import no.novari.keycloak.api.model.KEYCLOAK_ACCESS_TOKEN_LIFESPAN_ATTRIBUTE
import no.novari.keycloak.api.model.KEYCLOAK_PKCE_CODE_CHALLENGE_METHOD_ATTRIBUTE
import no.novari.keycloak.api.model.KEYCLOAK_POST_LOGOUT_REDIRECT_URIS_ATTRIBUTE
import no.novari.keycloak.api.model.KEYCLOAK_SESSION_IDLE_TIMEOUT_ATTRIBUTE
import no.novari.keycloak.api.model.KEYCLOAK_SESSION_LIFESPAN_ATTRIBUTE
import org.keycloak.representations.idm.ClientRepresentation

object ClientRepresentationComparator {
    private val managedAttributeKeys =
        setOf(
            KEYCLOAK_PKCE_CODE_CHALLENGE_METHOD_ATTRIBUTE,
            KEYCLOAK_POST_LOGOUT_REDIRECT_URIS_ATTRIBUTE,
            KEYCLOAK_ACCESS_TOKEN_LIFESPAN_ATTRIBUTE,
            KEYCLOAK_SESSION_LIFESPAN_ATTRIBUTE,
            KEYCLOAK_SESSION_IDLE_TIMEOUT_ATTRIBUTE,
        )

    fun matches(
        actual: ClientRepresentation,
        desired: ClientRepresentation,
    ): Boolean =
        actual.name == desired.name &&
            actual.protocol == desired.protocol &&
            actual.isEnabled == desired.isEnabled &&
            actual.isPublicClient == desired.isPublicClient &&
            actual.isStandardFlowEnabled == desired.isStandardFlowEnabled &&
            actual.isDirectAccessGrantsEnabled == desired.isDirectAccessGrantsEnabled &&
            actual.isServiceAccountsEnabled == desired.isServiceAccountsEnabled &&
            actual.isFullScopeAllowed == desired.isFullScopeAllowed &&
            actual.redirectUris.orEmpty().toSet() == desired.redirectUris.orEmpty().toSet() &&
            actual.webOrigins.orEmpty().toSet() == desired.webOrigins.orEmpty().toSet() &&
            actual.managedAttributes() == desired.managedAttributes()

    private fun ClientRepresentation.managedAttributes(): Map<String, String> =
        attributes.orEmpty().filterKeys { it in managedAttributeKeys }
}
