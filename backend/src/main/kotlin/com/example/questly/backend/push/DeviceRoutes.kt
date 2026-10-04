package com.example.questly.backend.push

import com.example.questly.backend.auth.ApiException
import com.example.questly.backend.db.DeviceTokens
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Serializable
data class DeviceTokenRequest(val token: String)

fun Route.deviceRoutes() {
    authenticate("auth-jwt") {
        route("/devices") {
            // Register (or reassign) an FCM token to the signed-in user.
            post {
                val token = call.receive<DeviceTokenRequest>().token.trim()
                if (token.isBlank()) throw ApiException(HttpStatusCode.BadRequest, "invalid_token", "Token is required")
                val userId = call.userId()
                newSuspendedTransaction(Dispatchers.IO) {
                    DeviceTokens.deleteWhere { DeviceTokens.token eq token }
                    DeviceTokens.insert {
                        it[DeviceTokens.token] = token
                        it[DeviceTokens.userId] = userId
                        it[createdAt] = OffsetDateTime.now(ZoneOffset.UTC)
                    }
                }
                call.respond(HttpStatusCode.NoContent)
            }
            // Unregister on sign-out so a shared device stops receiving the previous user's pushes.
            delete("/{token}") {
                val token = call.parameters["token"].orEmpty()
                newSuspendedTransaction(Dispatchers.IO) {
                    DeviceTokens.deleteWhere { DeviceTokens.token eq token }
                }
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}

private fun ApplicationCall.userId(): UUID {
    val subject = principal<JWTPrincipal>()?.subject
        ?: throw ApiException(HttpStatusCode.Unauthorized, "unauthorized", "Missing token subject")
    return UUID.fromString(subject)
}
