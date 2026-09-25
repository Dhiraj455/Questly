package com.example.questly.backend

import com.example.questly.backend.auth.AuthService
import com.example.questly.backend.auth.BrevoEmailSender
import com.example.questly.backend.auth.EmailSender
import com.example.questly.backend.auth.JwtConfig
import com.example.questly.backend.auth.LoggingEmailSender
import com.example.questly.backend.auth.authRoutes
import com.example.questly.backend.checkpoints.CheckpointsService
import com.example.questly.backend.checkpoints.OverpassClient
import com.example.questly.backend.checkpoints.checkpointRoutes
import com.example.questly.backend.checkins.CheckInService
import com.example.questly.backend.checkins.checkInRoutes
import io.ktor.server.application.Application
import io.ktor.server.application.log
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.route
import io.ktor.server.routing.routing

fun main() {
    val port = Env["PORT"]?.toIntOrNull() ?: 8080
    embeddedServer(Netty, port = port, host = "0.0.0.0") { module() }.start(wait = true)
}

/** Full application wiring. Tests install only the pieces they need (see HealthCheckTest). */
fun Application.module() {
    configureDatabase()
    configureSerialization()
    configureStatusPages()

    val jwt = JwtConfig.fromEnv()
    configureAuthentication(jwt)
    val authService = AuthService(jwt, emailSender())
    // One CheckpointsService so check-in validation reuses the same cache the map query warms.
    val checkpointsService = CheckpointsService(OverpassClient())
    val checkInService = CheckInService(checkpointsService)

    configureRouting() // GET /health
    routing {
        route("/v1") {
            authRoutes(authService)
            checkpointRoutes(checkpointsService)
            checkInRoutes(checkInService)
        }
    }
}

/** Uses Brevo when BREVO_API_KEY + BREVO_SENDER_EMAIL are set; otherwise logs the link (dev). */
private fun Application.emailSender(): EmailSender {
    val apiKey = Env["BREVO_API_KEY"]
    val sender = Env["BREVO_SENDER_EMAIL"]
    return if (!apiKey.isNullOrBlank() && !sender.isNullOrBlank()) {
        log.info("Email: using Brevo transactional sender")
        BrevoEmailSender(
            apiKey = apiKey,
            senderEmail = sender,
            senderName = Env["BREVO_SENDER_NAME"] ?: "Questly",
        )
    } else {
        log.info("Email: BREVO_* not set, using dev LoggingEmailSender")
        LoggingEmailSender()
    }
}
