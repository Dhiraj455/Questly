package com.example.questly.backend

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EventsFlowTest {
    @BeforeTest fun clean() = TestDb.reset()

    // Far enough in the future to always pass the "upcoming" discovery filter.
    private val future = "2999-01-01T18:00:00Z"
    private val lat = 41.0
    private val lng = -87.0

    private fun ApplicationTestBuilder.jsonClient() = createClient { install(ContentNegotiation) { json() } }

    private fun registerBody(email: String, name: String) =
        """{"email":"$email","password":"secret12345","displayName":"$name"}"""

    private suspend fun signIn(client: HttpClient, email: String, name: String): String {
        client.post("/v1/auth/register") { contentType(ContentType.Application.Json); setBody(registerBody(email, name)) }
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

    private fun eventJson(
        title: String,
        visibility: String = "PUBLIC",
        registrationType: String = "NONE",
        extra: String = "",
    ) = """
        {"title":"$title","category":"LANDMARK","lat":$lat,"lng":$lng,
         "startsAt":"$future","visibility":"$visibility","registrationType":"$registrationType"$extra}
    """.trimIndent()

    private suspend fun create(client: HttpClient, token: String, json: String): HttpResponse =
        client.post("/v1/events") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(json)
        }

    private fun idOf(body: String) = Regex("\"id\":\"([^\"]+)\"").find(body)!!.groupValues[1]

    @Test
    fun hostCreatesEventThenSeesItInMineAndNearby() = testApplication {
        application { module(emailSender = NoopEmailSender) }
        val client = jsonClient()
        val host = signIn(client, "host.events@example.com", "Host")

        val created = create(client, host, eventJson("Sunset Meetup"))
        assertEquals(HttpStatusCode.Created, created.status, created.bodyAsText())
        val id = idOf(created.bodyAsText())

        val mine = client.get("/v1/events/mine") { header("Authorization", "Bearer $host") }.bodyAsText()
        assertTrue(mine.contains("Sunset Meetup"), "mine: $mine")

        val nearby = client.get("/v1/events?lat=$lat&lng=$lng&radiusKm=5") {
            header("Authorization", "Bearer $host")
        }.bodyAsText()
        assertTrue(nearby.contains(id), "nearby: $nearby")
        assertTrue(nearby.contains("distanceMeters"), "nearby should carry distance: $nearby")

        val one = client.get("/v1/events/$id") { header("Authorization", "Bearer $host") }
        assertEquals(HttpStatusCode.OK, one.status)
        assertTrue(one.bodyAsText().contains("\"isHost\":true"))
    }

    @Test
    fun privateEventIsHiddenFromOthers() = testApplication {
        application { module(emailSender = NoopEmailSender) }
        val client = jsonClient()
        val host = signIn(client, "owner.events@example.com", "Owner")
        val other = signIn(client, "stranger.events@example.com", "Stranger")

        val id = idOf(create(client, host, eventJson("Secret Spot", visibility = "PRIVATE")).bodyAsText())

        val nearby = client.get("/v1/events?lat=$lat&lng=$lng") { header("Authorization", "Bearer $other") }.bodyAsText()
        assertFalse(nearby.contains(id), "private event leaked into discovery: $nearby")

        val direct = client.get("/v1/events/$id") { header("Authorization", "Bearer $other") }
        assertEquals(HttpStatusCode.NotFound, direct.status, "private event should 404 for non-host")
    }

    @Test
    fun nonHostCannotCancel() = testApplication {
        application { module(emailSender = NoopEmailSender) }
        val client = jsonClient()
        val host = signIn(client, "h2.events@example.com", "H2")
        val other = signIn(client, "o2.events@example.com", "O2")

        val id = idOf(create(client, host, eventJson("Public Walk")).bodyAsText())

        val cancel = client.post("/v1/events/$id/cancel") { header("Authorization", "Bearer $other") }
        assertEquals(HttpStatusCode.Conflict, cancel.status)
        assertTrue(cancel.bodyAsText().contains("NOT_HOST"), cancel.bodyAsText())

        // The host can cancel, and it then drops out of discovery.
        val hostCancel = client.post("/v1/events/$id/cancel") { header("Authorization", "Bearer $host") }
        assertEquals(HttpStatusCode.NoContent, hostCancel.status)
        val nearby = client.get("/v1/events?lat=$lat&lng=$lng") { header("Authorization", "Bearer $host") }.bodyAsText()
        assertFalse(nearby.contains(id), "cancelled event still in discovery: $nearby")
    }

    @Test
    fun paidEventWithoutPriceIsRejected() = testApplication {
        application { module(emailSender = NoopEmailSender) }
        val client = jsonClient()
        val host = signIn(client, "paid.events@example.com", "Paid")

        val bad = create(client, host, eventJson("Ticketed", registrationType = "PAID"))
        assertEquals(HttpStatusCode.BadRequest, bad.status, bad.bodyAsText())

        val good = create(
            client, host,
            eventJson("Ticketed", registrationType = "PAID", extra = ""","priceCents":1500,"currency":"USD""""),
        )
        assertEquals(HttpStatusCode.Created, good.status, good.bodyAsText())
        assertTrue(good.bodyAsText().contains("\"priceCents\":1500"))
    }

    @Test
    fun hostCanUpdateTitle() = testApplication {
        application { module(emailSender = NoopEmailSender) }
        val client = jsonClient()
        val host = signIn(client, "upd.events@example.com", "Upd")
        val id = idOf(create(client, host, eventJson("Before")).bodyAsText())

        val updated = client.put("/v1/events/$id") {
            header("Authorization", "Bearer $host")
            contentType(ContentType.Application.Json)
            setBody(eventJson("After"))
        }
        assertEquals(HttpStatusCode.OK, updated.status, updated.bodyAsText())
        assertTrue(updated.bodyAsText().contains("After"))
    }

    @Test
    fun eventsRequireAuth() = testApplication {
        application { module(emailSender = NoopEmailSender) }
        assertEquals(HttpStatusCode.Unauthorized, jsonClient().get("/v1/events?lat=$lat&lng=$lng").status)
    }
}
