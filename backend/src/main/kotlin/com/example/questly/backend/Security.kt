package com.example.questly.backend

import com.example.questly.backend.auth.ApiError
import com.example.questly.backend.auth.ApiException
import com.example.questly.backend.auth.JwtConfig
import com.example.questly.backend.checkins.CheckInRejected
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("Security")

fun Application.configureAuthentication(jwt: JwtConfig) {
    install(Authentication) {
        jwt("auth-jwt") {
            realm = jwt.realm
            verifier(jwt.verifier)
            validate { credential ->
                if (credential.payload.subject != null) JWTPrincipal(credential.payload) else null
            }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized, ApiError("unauthorized", "Missing or invalid token"))
            }
        }
    }
}

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<CheckInRejected> { call, e ->
            call.respond(HttpStatusCode.Conflict, e.body)
        }
        exception<ApiException> { call, e ->
            call.respond(e.status, ApiError(e.code, e.message))
        }
        exception<Throwable> { call, e ->
            log.error("Unhandled error", e)
            call.respond(HttpStatusCode.InternalServerError, ApiError("internal", "Unexpected error"))
        }
    }
}
