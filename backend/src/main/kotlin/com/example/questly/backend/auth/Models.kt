package com.example.questly.backend.auth

import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable

// Request/response DTOs mirroring docs/api/openapi.yaml.

@Serializable
data class RegisterRequest(val email: String, val password: String, val displayName: String)

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class VerifyEmailRequest(val token: String)

@Serializable
data class RefreshRequest(val refreshToken: String)

@Serializable
data class LogoutRequest(val refreshToken: String)

@Serializable
data class TokenPair(val accessToken: String, val refreshToken: String, val expiresInSeconds: Long)

@Serializable
data class UserDto(
    val id: String,
    val email: String,
    val displayName: String,
    val emailVerified: Boolean,
    val createdAt: String,
)

@Serializable
data class ApiError(val code: String, val message: String)

/** Thrown by services to map cleanly to an HTTP response in StatusPages. */
class ApiException(val status: HttpStatusCode, val code: String, override val message: String) : RuntimeException(message)
