package com.example.questly.backend.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.example.questly.backend.Env
import java.time.Instant
import java.util.UUID

/** Issues and verifies short-lived HS256 access tokens. */
class JwtConfig(
    secret: String,
    private val issuer: String,
    private val audience: String,
    val accessTtlSeconds: Long,
) {
    private val algorithm = Algorithm.HMAC256(secret)

    val realm = "questly"

    val verifier: JWTVerifier =
        JWT.require(algorithm).withIssuer(issuer).withAudience(audience).build()

    fun issueAccessToken(userId: UUID, email: String): String =
        JWT.create()
            .withIssuer(issuer)
            .withAudience(audience)
            .withSubject(userId.toString())
            .withClaim("email", email)
            .withExpiresAt(Instant.now().plusSeconds(accessTtlSeconds))
            .sign(algorithm)

    companion object {
        fun fromEnv(): JwtConfig = JwtConfig(
            // Dev default only; MUST be overridden in any deployed environment.
            secret = Env["JWT_SECRET"] ?: "dev-secret-change-me",
            issuer = Env["JWT_ISSUER"] ?: "questly",
            audience = Env["JWT_AUDIENCE"] ?: "questly-app",
            accessTtlSeconds = 900, // 15 minutes
        )
    }
}
