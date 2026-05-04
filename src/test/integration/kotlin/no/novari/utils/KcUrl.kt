package no.novari.utils

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Utility functions for constructing Keycloak-related URLs in integration tests.
 *
 * These helpers generate URLs for starting standard OpenID Connect flows
 * against the Keycloak instance.
 *
 * Notes:
 * - The values are fixed for the dev/test environment (client_id, redirect_uri, etc.).
 * - Not intended for production use; these URLs are hardcoded to match the test setup.
 */
object KcUrl {
    private fun base64UrlNoPadding(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private fun generateCodeVerifier(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return base64UrlNoPadding(bytes)
    }

    private fun codeChallengeS256(verifier: String): String {
        val sha256 =
            MessageDigest
                .getInstance("SHA-256")
                .digest(verifier.toByteArray(Charsets.US_ASCII))
        return base64UrlNoPadding(sha256)
    }

    fun authUrl(
        env: OperatorEnvironment,
        clientId: String,
        redirectUri: String,
        scope: String = "openid",
        usePkce: Boolean = true,
        pkcePlain: Boolean = false,
    ): Pair<HttpUrl, String?> {
        val base = env.keycloakServiceUrl()
        val url = "$base/realms/external/protocol/openid-connect/auth"

        val builder =
            url
                .toHttpUrl()
                .newBuilder()
                .addQueryParameter("client_id", clientId)
                .addQueryParameter("redirect_uri", redirectUri)
                .addQueryParameter("response_type", "code")
                .addQueryParameter("scope", scope)

        var codeVerifier: String? = null

        if (usePkce) {
            codeVerifier = generateCodeVerifier()
            val challenge = if (pkcePlain) codeVerifier else codeChallengeS256(codeVerifier)
            val method = if (pkcePlain) "plain" else "S256"
            builder
                .addQueryParameter("code_challenge", challenge)
                .addQueryParameter("code_challenge_method", method)
        }

        return builder.build() to codeVerifier
    }
}
