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

class CheckInFlowTest {
    @BeforeTest fun clean() = TestDb.reset()

    private fun body(vararg pairs: Pair<String, Any>) =
        pairs.joinToString(",", "{", "}") { (k, v) -> if (v is String) "\"$k\":\"$v\"" else "\"$k\":$v" }

    private val now get() = OffsetDateTime.now(ZoneOffset.UTC).toString()

    /** Registers + verifies + logs in a user and returns an access token. */
    private suspend fun signedInToken(client: io.ktor.client.HttpClient, email: String): String {
        client.post("/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(body("email" to email, "password" to "secret12345", "displayName" to "Carol"))
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

    private fun ApplicationTestBuilder.jsonClient() = createClient { install(ContentNegotiation) { json() } }

    private suspend fun checkIn(
        client: io.ktor.client.HttpClient,
        token: String,
        key: String,
        lat: Double,
        lng: Double,
        timestamp: String = OffsetDateTime.now(ZoneOffset.UTC).toString(),
    ) = client.post("/v1/checkins") {
        header("Authorization", "Bearer $token")
        header("Idempotency-Key", key)
        contentType(ContentType.Application.Json)
        setBody(body("checkpointId" to TEST_CHECKPOINT.id, "lat" to lat, "lng" to lng, "clientTimestamp" to timestamp))
    }

    @Test
    fun checkInAwardsPointsAndIsIdempotent() = testApplication {
        application { module(overpass = FakeOverpass(listOf(TEST_CHECKPOINT)), emailSender = NoopEmailSender) }
        val client = jsonClient()
        val token = signedInToken(client, "carol@example.com")
        val key = UUID.randomUUID().toString()

        val first = checkIn(client, token, key, TEST_CHECKPOINT.lat, TEST_CHECKPOINT.lng)
        assertEquals(HttpStatusCode.Created, first.status)

        val points = client.get("/v1/points") { header("Authorization", "Bearer $token") }
        assertTrue(points.bodyAsText().contains("\"total\":60"))

        // Same idempotency key -> no double award.
        val replay = checkIn(client, token, key, TEST_CHECKPOINT.lat, TEST_CHECKPOINT.lng)
        assertEquals(HttpStatusCode.Created, replay.status)
        val points2 = client.get("/v1/points") { header("Authorization", "Bearer $token") }
        assertTrue(points2.bodyAsText().contains("\"total\":60"))

        val history = client.get("/v1/checkins") { header("Authorization", "Bearer $token") }
        assertTrue(history.bodyAsText().contains(TEST_CHECKPOINT.title))
    }

    @Test
    fun cooldownRejectsASecondCheckIn() = testApplication {
        application { module(overpass = FakeOverpass(listOf(TEST_CHECKPOINT)), emailSender = NoopEmailSender) }
        val client = jsonClient()
        val token = signedInToken(client, "dave@example.com")

        assertEquals(HttpStatusCode.Created, checkIn(client, token, UUID.randomUUID().toString(), TEST_CHECKPOINT.lat, TEST_CHECKPOINT.lng).status)
        val second = checkIn(client, token, UUID.randomUUID().toString(), TEST_CHECKPOINT.lat, TEST_CHECKPOINT.lng)
        assertEquals(HttpStatusCode.Conflict, second.status)
        assertTrue(second.bodyAsText().contains("ON_COOLDOWN"))
    }

    @Test
    fun tooFarIsRejected() = testApplication {
        application { module(overpass = FakeOverpass(listOf(TEST_CHECKPOINT)), emailSender = NoopEmailSender) }
        val client = jsonClient()
        val token = signedInToken(client, "erin@example.com")

        val far = checkIn(client, token, UUID.randomUUID().toString(), 0.0, 0.0)
        assertEquals(HttpStatusCode.Conflict, far.status)
        assertTrue(far.bodyAsText().contains("TOO_FAR"))
    }

    @Test
    fun staleClientClockIsRejected() = testApplication {
        application { module(overpass = FakeOverpass(listOf(TEST_CHECKPOINT)), emailSender = NoopEmailSender) }
        val client = jsonClient()
        val token = signedInToken(client, "frank@example.com")

        val stale = checkIn(client, token, UUID.randomUUID().toString(), TEST_CHECKPOINT.lat, TEST_CHECKPOINT.lng, "2020-01-01T00:00:00Z")
        assertEquals(HttpStatusCode.Conflict, stale.status)
        assertTrue(stale.bodyAsText().contains("IMPLAUSIBLE"))
    }

    @Test
    fun checkInRequiresAuth() = testApplication {
        application { module(overpass = FakeOverpass(listOf(TEST_CHECKPOINT)), emailSender = NoopEmailSender) }
        val client = jsonClient()
        val res = client.post("/v1/checkins") {
            header("Idempotency-Key", UUID.randomUUID().toString())
            contentType(ContentType.Application.Json)
            setBody(body("checkpointId" to TEST_CHECKPOINT.id, "lat" to TEST_CHECKPOINT.lat, "lng" to TEST_CHECKPOINT.lng, "clientTimestamp" to now))
        }
        assertEquals(HttpStatusCode.Unauthorized, res.status)
    }
}
