package no.novari.keycloak

import no.novari.keycloak.api.annotation.KeycloakDslMarker
import org.keycloak.representations.idm.ClientRepresentation

const val KEYCLOAK_CLIENT_PROTOCOL = "openid-connect"
const val KEYCLOAK_CLIENT_ENABLED = true
const val KEYCLOAK_CLIENT_PUBLIC = false
const val KEYCLOAK_CLIENT_STANDARD_FLOW_ENABLED = true
const val KEYCLOAK_CLIENT_DIRECT_ACCESS_GRANTS_ENABLED = false
const val KEYCLOAK_CLIENT_SERVICE_ACCOUNTS_ENABLED = false
const val KEYCLOAK_CLIENT_FULL_SCOPE_ALLOWED = false

const val KEYCLOAK_WEB_ORIGIN_SAME_ORIGIN = "+"
const val KEYCLOAK_PKCE_CODE_CHALLENGE_METHOD_ATTRIBUTE = "pkce.code.challenge.method"
const val KEYCLOAK_PKCE_CODE_CHALLENGE_METHOD = "S256"
const val KEYCLOAK_POST_LOGOUT_REDIRECT_URIS_ATTRIBUTE = "post.logout.redirect.uris"
const val KEYCLOAK_POST_LOGOUT_REDIRECT_URIS = "+"
const val KEYCLOAK_ACCESS_TOKEN_LIFESPAN_ATTRIBUTE = "access.token.lifespan"
const val KEYCLOAK_SESSION_LIFESPAN_ATTRIBUTE = "client.session.max.lifespan"
const val KEYCLOAK_SESSION_IDLE_TIMEOUT_ATTRIBUTE = "client.session.idle.timeout"

private const val KEYCLOAK_MULTI_VALUE_SEPARATOR = "##"

inline fun clientRepresentation(block: ClientRepresentationDsl.() -> Unit) = ClientRepresentationDsl().apply(block).build()

// TODO: Store managed by information like: env, operator, "owner" ref
@KeycloakDslMarker
class ClientRepresentationDsl {
    var clientId: String? = null
    var name: String? = null
    var accessTokenLifetime: Number? = null
    var sessionLifetime: Number? = null
    var sessionIdleTimeout: Number? = null

    private val redirectUris = mutableListOf<String>()
    private val webOrigins = mutableListOf(KEYCLOAK_WEB_ORIGIN_SAME_ORIGIN)
    private val postLogoutRedirectUris = mutableListOf(KEYCLOAK_POST_LOGOUT_REDIRECT_URIS)

    fun redirectUri(value: String) {
        redirectUris += value
    }

    fun redirectUris(vararg values: String) {
        redirectUris += values
    }

    fun redirectUris(values: Iterable<String>) {
        redirectUris += values
    }

    fun webOrigins(block: WebOriginsDsl.() -> Unit) {
        webOrigins.clear()
        WebOriginsDsl(webOrigins).apply(block)
    }

    fun postLogoutRedirectUris(block: PostLogoutRedirectUrisDsl.() -> Unit) {
        postLogoutRedirectUris.clear()
        PostLogoutRedirectUrisDsl(postLogoutRedirectUris).apply(block)
    }

    fun build(): ClientRepresentation {
        return ClientRepresentation().apply {
            this.clientId = this@ClientRepresentationDsl.clientId
            this.name = this@ClientRepresentationDsl.name
            protocol = KEYCLOAK_CLIENT_PROTOCOL
            isEnabled = KEYCLOAK_CLIENT_ENABLED

            isPublicClient = KEYCLOAK_CLIENT_PUBLIC

            isStandardFlowEnabled = KEYCLOAK_CLIENT_STANDARD_FLOW_ENABLED
            isDirectAccessGrantsEnabled = KEYCLOAK_CLIENT_DIRECT_ACCESS_GRANTS_ENABLED
            isServiceAccountsEnabled = KEYCLOAK_CLIENT_SERVICE_ACCOUNTS_ENABLED
            isFullScopeAllowed = KEYCLOAK_CLIENT_FULL_SCOPE_ALLOWED

            redirectUris = this@ClientRepresentationDsl.redirectUris.toList()
            webOrigins = this@ClientRepresentationDsl.webOrigins.toList()

            attributes =
                mapOf(
                    KEYCLOAK_PKCE_CODE_CHALLENGE_METHOD_ATTRIBUTE to KEYCLOAK_PKCE_CODE_CHALLENGE_METHOD,
                    KEYCLOAK_POST_LOGOUT_REDIRECT_URIS_ATTRIBUTE to
                        postLogoutRedirectUris.joinToString(KEYCLOAK_MULTI_VALUE_SEPARATOR),
                    KEYCLOAK_ACCESS_TOKEN_LIFESPAN_ATTRIBUTE to accessTokenLifetime?.toString(),
                    KEYCLOAK_SESSION_LIFESPAN_ATTRIBUTE to sessionLifetime?.toString(),
                    KEYCLOAK_SESSION_IDLE_TIMEOUT_ATTRIBUTE to sessionIdleTimeout?.toString(),
                ).filterValues { it != null }
        }
    }
}

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
