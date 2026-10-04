package com.example.questly.backend.profile

import com.example.questly.backend.auth.ApiException
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import java.util.UUID

fun Route.profileRoutes(service: ProfileService) {
    authenticate("auth-jwt") {
        get("/profile") { call.respond(service.profile(call.userId())) }
    }
}

private fun ApplicationCall.userId(): UUID {
    val subject = principal<JWTPrincipal>()?.subject
        ?: throw ApiException(HttpStatusCode.Unauthorized, "unauthorized", "Missing token subject")
    return UUID.fromString(subject)
}
