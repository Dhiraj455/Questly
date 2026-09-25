package com.example.questly.backend.checkins

import com.example.questly.backend.auth.ApiException
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import java.util.UUID

fun Route.checkInRoutes(service: CheckInService) {
    authenticate("auth-jwt") {
        get("/checkins") {
            val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 100) ?: 20
            call.respond(service.history(call.userId(), limit, call.request.queryParameters["cursor"]))
        }
        post("/checkins") {
            val rawKey = call.request.headers["Idempotency-Key"]
                ?: throw ApiException(HttpStatusCode.BadRequest, "missing_idempotency_key", "Idempotency-Key is required")
            val key = try { UUID.fromString(rawKey) } catch (_: IllegalArgumentException) {
                throw ApiException(HttpStatusCode.BadRequest, "invalid_idempotency_key", "Idempotency-Key must be a UUID")
            }
            call.respond(HttpStatusCode.Created, service.create(call.userId(), key, call.receive()))
        }
        get("/points") { call.respond(service.points(call.userId())) }
    }
}

private fun ApplicationCall.userId(): UUID {
    val subject = principal<JWTPrincipal>()?.subject
        ?: throw ApiException(HttpStatusCode.Unauthorized, "unauthorized", "Missing token subject")
    return UUID.fromString(subject)
}
