package com.example.questly.backend

import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProfileFlowTest {
    @BeforeTest fun clean() = TestDb.reset()

    private fun body(vararg pairs: Pair<String, Any>) =
        pairs.joinToString(",", "{", "}") { (k, v) -> if (v is String) "\"$k\":\"$v\"" else "\"$k\":$v" }

    private fun ApplicationTestBuilder.jsonClient() = createClient { install(ContentNegotiation) { json() } }

    private suspend fun signedInToken(client: io.ktor.client.HttpClient, email: String): String {
        client.post("/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(body("email" to email, "password" to "secret12345", "displayName" to "Grace"))
        }
        client.post("/v1/auth/verify-email") {
            contentType(ContentType.Application.Json)
            setBody(body("token" to TestDb.latestVerifyToken(email)))
        }
        val login = client.post("/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(body("email" to email, "password" to "secret12345"))
        }
        return Regex("\"accessToken\":\"([^\"]+)\"").find(login.bodyAsText())!!.groupValues[1]
    }

    @Test
    fun profileReflectsStreakAndAchievementsAfterCheckIn() = testApplication {
        application { module(emailSender = NoopEmailSender) }
        val client = jsonClient()
        val token = signedInToken(client, "grace@example.com")

        client.post("/v1/checkins") {
            header("Authorization", "Bearer $token")
            header("Idempotency-Key", UUID.randomUUID().toString())
            contentType(ContentType.Application.Json)
            setBody(
                body(
                    "checkpointId" to TEST_CHECKPOINT.id,
                    "lat" to TEST_CHECKPOINT.lat,
                    "lng" to TEST_CHECKPOINT.lng,
                    "clientTimestamp" to OffsetDateTime.now(ZoneOffset.UTC).toString(),
                    "checkpointLat" to TEST_CHECKPOINT.lat,
                    "checkpointLng" to TEST_CHECKPOINT.lng,
                    "category" to TEST_CHECKPOINT.category,
                    "title" to TEST_CHECKPOINT.title,
                ),
            )
        }

        val profile = client.get("/v1/profile") { header("Authorization", "Bearer $token") }
        assertEquals(HttpStatusCode.OK, profile.status)
        val json = profile.bodyAsText()
        assertTrue(json.contains("\"currentStreakDays\":1"), json)
        assertTrue(json.contains("\"longestStreakDays\":1"), json)
        assertTrue(json.contains("\"totalCheckIns\":1"), json)
        assertTrue(json.contains("\"totalPoints\":60"), json)
        // First Steps earned (1 check-in); Explorer (10) not yet.
        assertTrue(Regex("\"id\":\"first_steps\"[^}]*\"earned\":true").containsMatchIn(json), json)
        assertTrue(Regex("\"id\":\"explorer\"[^}]*\"earned\":false").containsMatchIn(json), json)
    }

    @Test
    fun profileRequiresAuth() = testApplication {
        application { module(emailSender = NoopEmailSender) }
        assertEquals(HttpStatusCode.Unauthorized, jsonClient().get("/v1/profile").status)
    }
}
