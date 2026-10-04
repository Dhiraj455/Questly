package com.example.questly.backend.leaderboard

import io.ktor.server.auth.authenticate
import io.ktor.server.routing.Route
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val json = Json

/**
 * Streams the live global leaderboard. Sends the current snapshot on connect, then one frame per
 * update (each check-in). The collect suspends until the client disconnects.
 */
fun Route.leaderboardRoutes(hub: LeaderboardHub) {
    authenticate("auth-jwt") {
        webSocket("/leaderboard") {
            send(Frame.Text(json.encodeToString(hub.snapshot())))
            hub.updates.collect { send(Frame.Text(json.encodeToString(it))) }
        }
    }
}
