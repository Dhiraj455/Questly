package com.example.questly.backend

import com.example.questly.backend.auth.AuthService
import com.example.questly.backend.auth.BrevoEmailSender
import com.example.questly.backend.auth.EmailSender
import com.example.questly.backend.auth.GoogleVerifier
import com.example.questly.backend.auth.JwtConfig
import com.example.questly.backend.auth.LoggingEmailSender
import com.example.questly.backend.auth.authRoutes
import com.example.questly.backend.checkins.CheckInService
import com.example.questly.backend.checkins.checkInRoutes
import com.example.questly.backend.friends.FriendsService
import com.example.questly.backend.friends.friendRoutes
import com.example.questly.backend.leaderboard.LeaderboardHub
import com.example.questly.backend.leaderboard.LeaderboardService
import com.example.questly.backend.leaderboard.leaderboardRoutes
import com.example.questly.backend.profile.ProfileService
import com.example.questly.backend.profile.profileRoutes
import com.example.questly.backend.push.PushSender
import com.example.questly.backend.push.deviceRoutes
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.forwardedheaders.XForwardedHeaders
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import kotlin.time.Duration.Companion.minutes

fun main() {
    val port = Env["PORT"]?.toIntOrNull() ?: 8080
    embeddedServer(Netty, port = port, host = "0.0.0.0") { module() }.start(wait = true)
}

private val AUTH_RATE_LIMIT = RateLimitName("auth")

/**
 * Full application wiring. [emailSender] is injectable so tests can supply a fake (see the
 * Testcontainers tests); production uses Brevo (or the logging stub). The app fetches checkpoints
 * from Overpass directly — this host's datacenter IP is blocked by public Overpass servers — so the
 * backend has no Overpass dependency; check-ins validate against the checkpoint the app sends.
 */
fun Application.module(
    emailSender: EmailSender = defaultEmailSender(),
) {
    configureDatabase()
    configureSerialization()
    configureStatusPages()

    // Behind Render's proxy the socket peer is the proxy, so trust X-Forwarded-* for the caller IP
    // that per-client rate limiting keys on.
    install(XForwardedHeaders)
    // Real-time leaderboard transport. Ping keepalive is left at defaults; clients reconnect anyway.
    install(WebSockets)
    install(RateLimit) {
        // Throttle the unauthenticated auth surface (login/register/verify/google) per client IP to
        // blunt brute-force and signup spam. Generous enough never to bite normal use.
        register(AUTH_RATE_LIMIT) {
            // Configurable so tests (which make many auth calls from one loopback IP) can raise it.
            val perMinute = Env["AUTH_RATE_LIMIT_PER_MINUTE"]?.toIntOrNull() ?: 20
            rateLimiter(limit = perMinute, refillPeriod = 1.minutes)
            requestKey { call -> call.request.origin.remoteHost }
        }
    }

    val jwt = JwtConfig.fromEnv()
    configureAuthentication(jwt)
    val authService = AuthService(jwt, emailSender, GoogleVerifier(Env["GOOGLE_WEB_CLIENT_ID"] ?: ""))
    val leaderboardHub = LeaderboardHub(LeaderboardService())
    val profileService = ProfileService()
    val friendsService = FriendsService()
    val pushSender = PushSender.fromEnv()
    val checkInService = CheckInService(onCheckIn = { userId, title, points ->
        leaderboardHub.broadcast()
        // Notify the user's friends that they just checked in.
        if (pushSender.enabled) {
            val friends = friendsService.friendIdsOf(userId)
            if (friends.isNotEmpty()) {
                val name = friendsService.displayNameOf(userId) ?: "A friend"
                pushSender.sendToUsers(friends, "$name checked in", "$name earned $points points at $title")
            }
        }
    })

    configureRouting() // GET /health
    routing {
        route("/v1") {
            rateLimit(AUTH_RATE_LIMIT) {
                authRoutes(authService)
            }
            checkInRoutes(checkInService)
            profileRoutes(profileService)
            friendRoutes(friendsService)
            leaderboardRoutes(leaderboardHub)
            deviceRoutes()
        }
    }
}

/** Uses Brevo when BREVO_API_KEY + BREVO_SENDER_EMAIL are set; otherwise logs the link (dev). */
private fun Application.defaultEmailSender(): EmailSender {
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
