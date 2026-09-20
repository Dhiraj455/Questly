package com.example.questly.backend

import io.ktor.server.application.Application
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable

@Serializable
data class Health(val status: String, val service: String = "questly-backend", val version: String = "0.1.0")

fun Application.configureRouting() {
    routing {
        // Liveness probe — no auth, no DB dependency, safe for load balancers / uptime checks.
        get("/health") { call.respond(Health(status = "ok")) }
    }
}
