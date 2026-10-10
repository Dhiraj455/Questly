package com.example.questly.backend

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EventRegistrationFlowTest {
    @BeforeTest fun clean() = TestDb.reset()

    private val future = "2999-01-01T18:00:00Z"
    private val lat = 41.0
    private val lng = -87.0

    private fun ApplicationTestBuilder.jsonClient() = createClient { install(ContentNegotiation) { json() } }

    private suspend fun signIn(client: HttpClient, email: String, name: String): String {
        client.post("/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"$email","password":"secret12345","displayName":"$name"}""")
        }
        client.post("/v1/auth/verify-email") {
            contentType(ContentType.Application.Json)
            setBody("""{"token":"${TestDb.latestVerifyToken(email)}"}""")
        }
        val login = client.post("/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"$email","password":"secret12345"}""")
        }
        return Regex("\"accessToken\":\"([^\"]+)\"").find(login.bodyAsText())!!.groupValues[1]
    }

    private suspend fun createEvent(
        client: HttpClient,
        token: String,
        registration: String = "FREE",
        capacity: Int? = null,
    ): String {
        val cap = capacity?.let { ""","capacity":$it""" } ?: ""
        val body = """
            {"title":"Trail Hike","category":"PARK","lat":$lat,"lng":$lng,"startsAt":"$future",
             "visibility":"PUBLIC","registrationType":"$registration"$cap}
        """.trimIndent()
        val res = client.post("/v1/events") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        assertEquals(HttpStatusCode.Created, res.status, res.bodyAsText())
        return Regex("\"id\":\"([^\"]+)\"").find(res.bodyAsText())!!.groupValues[1]
    }

    private suspend fun register(client: HttpClient, token: String, id: String): HttpResponse =
        client.post("/v1/events/$id/register") { header("Authorization", "Bearer $token") }

    @Test
    fun registerShowsGoingAndCountsTheAttendee() = testApplication {
        application { module(emailSender = NoopEmailSender) }
        val client = jsonClient()
        val host = signIn(client, "host.reg@example.com", "Host")
        val guest = signIn(client, "guest.reg@example.com", "Guest")
        val id = createEvent(client, host)

        val res = register(client, guest, id)
        assertEquals(HttpStatusCode.OK, res.status, res.bodyAsText())
        assertTrue(res.bodyAsText().contains("REGISTERED"))

        // The guest now sees their own status + the count on the event.
        val one = client.get("/v1/events/$id") { header("Authorization", "Bearer $guest") }.bodyAsText()
        assertTrue(one.contains("\"viewerStatus\":\"REGISTERED\""), one)
        assertTrue(one.contains("\"registeredCount\":1"), one)
    }

    @Test
    fun capacityOverflowGoesToWaitlistThenPromotesOnCancel() = testApplication {
        application { module(emailSender = NoopEmailSender) }
        val client = jsonClient()
        val host = signIn(client, "host.cap@example.com", "Host")
        val a = signIn(client, "a.cap@example.com", "A")
        val b = signIn(client, "b.cap@example.com", "B")
        val id = createEvent(client, host, capacity = 1)

        assertTrue(register(client, a, id).bodyAsText().contains("REGISTERED"))
        // Second registrant overflows capacity -> waitlisted.
        assertTrue(register(client, b, id).bodyAsText().contains("WAITLISTED"))

        // A cancels; B is auto-promoted into the freed spot.
        val leave = client.delete("/v1/events/$id/register") { header("Authorization", "Bearer $a") }
        assertEquals(HttpStatusCode.NoContent, leave.status)

        val bView = client.get("/v1/events/$id") { header("Authorization", "Bearer $b") }.bodyAsText()
        assertTrue(bView.contains("\"viewerStatus\":\"REGISTERED\""), bView)
    }

    @Test
    fun hostSeesRosterButGuestsCannot() = testApplication {
        application { module(emailSender = NoopEmailSender) }
        val client = jsonClient()
        val host = signIn(client, "host.ros@example.com", "Host")
        val guest = signIn(client, "guest.ros@example.com", "Guest")
        val id = createEvent(client, host)
        register(client, guest, id)

        val roster = client.get("/v1/events/$id/roster") { header("Authorization", "Bearer $host") }
        assertEquals(HttpStatusCode.OK, roster.status, roster.bodyAsText())
        assertTrue(roster.bodyAsText().contains("Guest"), roster.bodyAsText())

        val denied = client.get("/v1/events/$id/roster") { header("Authorization", "Bearer $guest") }
        assertEquals(HttpStatusCode.Conflict, denied.status)
        assertTrue(denied.bodyAsText().contains("NOT_HOST"))
    }

    @Test
    fun registeringForANonRegistrationEventIsRejected() = testApplication {
        application { module(emailSender = NoopEmailSender) }
        val client = jsonClient()
        val host = signIn(client, "host.none@example.com", "Host")
        val guest = signIn(client, "guest.none@example.com", "Guest")
        val id = createEvent(client, host, registration = "NONE")

        val res = register(client, guest, id)
        assertEquals(HttpStatusCode.Conflict, res.status)
        assertTrue(res.bodyAsText().contains("NO_REGISTRATION"))
    }

    @Test
    fun paidRegistrationIsDeferredToMilestoneG() = testApplication {
        application { module(emailSender = NoopEmailSender) }
        val client = jsonClient()
        val host = signIn(client, "host.paid@example.com", "Host")
        val guest = signIn(client, "guest.paid@example.com", "Guest")
        val id = client.post("/v1/events") {
            header("Authorization", "Bearer $host")
            contentType(ContentType.Application.Json)
            setBody(
                """{"title":"Gala","category":"LANDMARK","lat":$lat,"lng":$lng,"startsAt":"$future",
                   "registrationType":"PAID","priceCents":2000,"currency":"USD"}""".trimIndent(),
            )
        }.bodyAsText().let { Regex("\"id\":\"([^\"]+)\"").find(it)!!.groupValues[1] }

        val res = register(client, guest, id)
        assertEquals(HttpStatusCode.Conflict, res.status)
        assertTrue(res.bodyAsText().contains("PAYMENT_REQUIRED"))
    }
}
