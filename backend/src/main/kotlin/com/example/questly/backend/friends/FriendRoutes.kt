package com.example.questly.backend.friends

import com.example.questly.backend.auth.ApiException
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
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

fun Route.friendRoutes(service: FriendsService) {
    authenticate("auth-jwt") {
        route("/friends") {
            get { call.respond(service.list(call.userId())) }
            get("/code") { call.respond(service.myCode(call.userId())) }
            post("/requests") {
                service.sendRequest(call.userId(), call.receive<AddFriendRequest>().code)
                call.respond(HttpStatusCode.Created)
            }
            post("/requests/{id}/accept") {
                service.accept(call.userId(), call.pathUuid("id"))
                call.respond(HttpStatusCode.NoContent)
            }
            post("/requests/{id}/decline") {
                service.decline(call.userId(), call.pathUuid("id"))
                call.respond(HttpStatusCode.NoContent)
            }
            delete("/{userId}") {
                service.unfriend(call.userId(), call.pathUuid("userId"))
                call.respond(HttpStatusCode.NoContent)
            }
        }
        get("/feed") {
            val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 100) ?: 20
            call.respond(service.feed(call.userId(), limit, call.request.queryParameters["cursor"]))
        }
    }
}

private fun ApplicationCall.userId(): UUID {
    val subject = principal<JWTPrincipal>()?.subject
        ?: throw ApiException(HttpStatusCode.Unauthorized, "unauthorized", "Missing token subject")
    return UUID.fromString(subject)
}

private fun ApplicationCall.pathUuid(name: String): UUID = try {
    UUID.fromString(parameters[name])
} catch (_: Exception) {
    throw ApiException(HttpStatusCode.BadRequest, "invalid_id", "$name must be a UUID")
}
