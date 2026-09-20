package com.example.questly.backend.checkpoints

import com.example.questly.backend.auth.ApiException
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import java.io.IOException

private const val MIN_RADIUS_M = 1_000.0
private const val MAX_RADIUS_M = 20_000.0

fun Route.checkpointRoutes(service: CheckpointsService) {
    authenticate("auth-jwt") {
        get("/checkpoints") {
            val lat = call.doubleParam("lat")
            val lng = call.doubleParam("lng")
            val radius = call.doubleParam("radiusMeters").coerceIn(MIN_RADIUS_M, MAX_RADIUS_M)
            val types = call.request.queryParameters["types"]
                ?.split(",")
                ?.mapNotNull { it.trim().uppercase().takeIf(String::isNotEmpty) }
                ?.toSet()
                .orEmpty()

            try {
                call.respond(service.nearby(lat, lng, radius, types))
            } catch (e: IOException) {
                throw ApiException(HttpStatusCode.BadGateway, "overpass_unavailable", "Couldn't reach the quest service")
            }
        }
    }
}

private fun io.ktor.server.application.ApplicationCall.doubleParam(name: String): Double =
    request.queryParameters[name]?.toDoubleOrNull()
        ?: throw ApiException(HttpStatusCode.BadRequest, "invalid_param", "Missing or invalid '$name'")
