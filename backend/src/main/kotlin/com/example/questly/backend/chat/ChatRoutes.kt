package com.example.questly.backend.chat

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
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import kotlinx.coroutines.launch
import java.util.UUID

fun Route.chatRoutes(service: ChatService, hub: ChatHub) {
    authenticate("auth-jwt") {
        route("/conversations") {
            get { call.respond(service.list(call.userId())) }
            post("/direct") {
                call.respond(service.startDirect(call.userId(), UUID.fromString(call.receive<StartDirectRequest>().userId)))
            }
            get("/{id}/messages") {
                val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 100) ?: 30
                call.respond(service.history(call.userId(), call.pathUuid("id"), call.request.queryParameters["cursor"], limit))
            }
            post("/{id}/messages") {
                call.respond(service.send(call.userId(), call.pathUuid("id"), call.receive<SendMessageRequest>().body))
            }
            post("/{id}/read") {
                service.markRead(call.userId(), call.pathUuid("id"))
                call.respond(HttpStatusCode.NoContent)
            }
            post("/{id}/mute") {
                service.mute(call.userId(), call.pathUuid("id"), call.receive<MuteRequest>().muted)
                call.respond(HttpStatusCode.NoContent)
            }
        }

        // The group chat for an event (get-or-create; must be host or registered).
        get("/events/{id}/conversation") {
            call.respond(service.eventConversation(call.userId(), call.pathUuid("id")))
        }

        route("/users/{id}/block") {
            post {
                service.block(call.userId(), call.pathUuid("id"))
                call.respond(HttpStatusCode.NoContent)
            }
            delete {
                service.unblock(call.userId(), call.pathUuid("id"))
                call.respond(HttpStatusCode.NoContent)
            }
        }

        post("/messages/{id}/report") {
            service.report(call.userId(), call.pathUuid("id"), call.receive<ReportRequest>().reason)
            call.respond(HttpStatusCode.NoContent)
        }

        // Live delivery: the client receives new messages here and sends via POST .../messages.
        webSocket("/ws/chat") {
            val userId = call.principal<JWTPrincipal>()?.subject?.let(UUID::fromString) ?: return@webSocket
            val channel = hub.register(userId)
            val sender = launch { for (payload in channel) send(Frame.Text(payload)) }
            try {
                for (frame in incoming) { /* inbound frames ignored — sending is via REST */ }
            } finally {
                sender.cancel()
                hub.unregister(userId, channel)
            }
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
