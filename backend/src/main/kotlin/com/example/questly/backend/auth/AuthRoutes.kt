package com.example.questly.backend.auth

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
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
        // Human-clickable link from the verification email (GET), renders a confirmation page.
        get("/verify-email") {
            val token = call.request.queryParameters["token"]
                ?: throw ApiException(HttpStatusCode.BadRequest, "missing_token", "Missing token")
            service.confirmEmail(token)
            call.respondText(
                "<html><body style=\"font-family:sans-serif;text-align:center;padding:48px\">" +
                    "<h2>Email verified ✅</h2><p>You can return to the Questly app and sign in.</p></body></html>",
                ContentType.Text.Html,
            )
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
