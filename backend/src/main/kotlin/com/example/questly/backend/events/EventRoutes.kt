package com.example.questly.backend.events

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
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import java.util.UUID

fun Route.eventRoutes(service: EventsService, registrations: RegistrationsService) {
    authenticate("auth-jwt") {
        route("/events") {
            // Create an event (the caller becomes the host).
            post {
                call.respond(HttpStatusCode.Created, service.create(call.userId(), call.receive()))
            }
            // Discover events near a point: ?lat=&lng=&radiusKm=&category=&limit=
            get {
                val lat = call.doubleParam("lat")
                val lng = call.doubleParam("lng")
                val radiusKm = call.request.queryParameters["radiusKm"]?.toDoubleOrNull() ?: 5.0
                val category = call.request.queryParameters["category"]?.takeIf { it.isNotBlank() }
                val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 200) ?: 50
                call.respond(service.listArea(call.userId(), lat, lng, radiusKm, category, limit))
            }
            // Events the caller hosts (registered events arrive in Milestone F).
            get("/mine") { call.respond(service.mine(call.userId())) }
            get("/{id}") { call.respond(service.get(call.userId(), call.pathUuid("id"))) }
            // Full update — host only.
            put("/{id}") { call.respond(service.update(call.userId(), call.pathUuid("id"), call.receive())) }
            // Cancel — host only.
            post("/{id}/cancel") {
                service.cancel(call.userId(), call.pathUuid("id"))
                call.respond(HttpStatusCode.NoContent)
            }

            // Registration & participation (Milestone F).
            post("/{id}/register") {
                call.respond(registrations.register(call.userId(), call.pathUuid("id")))
            }
            delete("/{id}/register") {
                registrations.unregister(call.userId(), call.pathUuid("id"))
                call.respond(HttpStatusCode.NoContent)
            }
            // Attendee roster — host only.
            get("/{id}/roster") {
                call.respond(registrations.roster(call.userId(), call.pathUuid("id")))
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

private fun ApplicationCall.doubleParam(name: String): Double =
    request.queryParameters[name]?.toDoubleOrNull()
        ?: throw ApiException(HttpStatusCode.BadRequest, "missing_param", "$name is required and must be a number")
