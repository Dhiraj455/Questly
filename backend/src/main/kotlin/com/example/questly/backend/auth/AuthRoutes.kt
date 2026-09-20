package com.example.questly.backend.auth

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import java.util.UUID

fun Route.authRoutes(service: AuthService) {
    route("/auth") {
        post("/register") {
            service.register(call.receive())
            call.respond(HttpStatusCode.Accepted)
        }
        post("/verify-email") {
            call.respond(HttpStatusCode.OK, service.verifyEmail(call.receive()))
        }
        post("/login") {
            call.respond(HttpStatusCode.OK, service.login(call.receive()))
        }
        post("/refresh") {
            call.respond(HttpStatusCode.OK, service.refresh(call.receive()))
        }
        post("/logout") {
            service.logout(call.receive())
            call.respond(HttpStatusCode.NoContent)
        }

        authenticate("auth-jwt") {
            get("/me") {
                val userId = call.userId()
                call.respond(HttpStatusCode.OK, service.getUser(userId))
            }
            delete("/me") {
                service.deleteUser(call.userId())
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}

private fun io.ktor.server.application.ApplicationCall.userId(): UUID {
    val subject = principal<JWTPrincipal>()?.subject
        ?: throw ApiException(HttpStatusCode.Unauthorized, "unauthorized", "Missing token subject")
    return UUID.fromString(subject)
}
