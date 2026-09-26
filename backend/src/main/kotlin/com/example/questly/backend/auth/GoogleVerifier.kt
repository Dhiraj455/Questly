package com.example.questly.backend.auth

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Identity extracted from a verified Google ID token. */
data class GoogleIdentity(val email: String, val displayName: String)

/**
 * Verifies a Google ID token via Google's tokeninfo endpoint and checks it was minted for our
 * Web client. tokeninfo returns an error for expired/tampered tokens, so a 200 plus the audience
 * check is sufficient.
 *
 * ponytail: tokeninfo is a network round-trip per sign-in. Offline JWKS signature verification is
 * the upgrade if sign-in volume grows.
 */
class GoogleVerifier(private val expectedClientId: String) {
    private val client = HttpClient(CIO) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }

    @Serializable
    private data class TokenInfo(
        val aud: String? = null,
        val sub: String? = null,
        val email: String? = null,
        val email_verified: String? = null,
        val name: String? = null,
        val iss: String? = null,
    )

    suspend fun verify(idToken: String): GoogleIdentity {
        if (expectedClientId.isBlank()) {
            throw ApiException(HttpStatusCode.ServiceUnavailable, "google_not_configured", "Google sign-in isn't configured")
        }
        val response = client.get("https://oauth2.googleapis.com/tokeninfo") {
            url { parameters.append("id_token", idToken) }
        }
        if (!response.status.isSuccess()) {
            throw ApiException(HttpStatusCode.Unauthorized, "invalid_google_token", "Google sign-in failed")
        }
        val info: TokenInfo = response.body()
        if (info.aud != expectedClientId) {
            throw ApiException(HttpStatusCode.Unauthorized, "wrong_audience", "Token wasn't issued for this app")
        }
        if (info.iss != "accounts.google.com" && info.iss != "https://accounts.google.com") {
            throw ApiException(HttpStatusCode.Unauthorized, "invalid_issuer", "Token issuer is not Google")
        }
        val email = info.email
        if (email.isNullOrBlank() || info.email_verified != "true") {
            throw ApiException(HttpStatusCode.Unauthorized, "email_unverified", "Google account email is not verified")
        }
        return GoogleIdentity(email = email, displayName = info.name?.takeIf { it.isNotBlank() } ?: email.substringBefore("@"))
    }
}
