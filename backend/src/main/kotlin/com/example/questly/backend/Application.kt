package com.example.questly.backend

import com.example.questly.backend.auth.AuthService
import com.example.questly.backend.auth.JwtConfig
import com.example.questly.backend.auth.LoggingEmailSender
import com.example.questly.backend.auth.authRoutes
import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.route
import io.ktor.server.routing.routing

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(Netty, port = port, host = "0.0.0.0") { module() }.start(wait = true)
}

/** Full application wiring. Tests install only the pieces they need (see HealthCheckTest). */
fun Application.module() {
    configureDatabase()
    configureSerialization()
    configureStatusPages()

    val jwt = JwtConfig.fromEnv()
    configureAuthentication(jwt)
    // ponytail: manual construction (no DI). A Brevo sender replaces LoggingEmailSender once
    // BREVO_API_KEY is set; swap the one line below.
    val authService = AuthService(jwt, LoggingEmailSender())

    configureRouting() // GET /health
    routing { route("/v1") { authRoutes(authService) } }
}
