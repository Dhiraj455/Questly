package com.example.questly.backend

import io.ktor.client.HttpClient
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
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChatFlowTest {
    @BeforeTest fun clean() = TestDb.reset()

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

    private suspend fun userId(client: HttpClient, token: String): String =
        Regex("\"id\":\"([^\"]+)\"").find(
            client.get("/v1/auth/me") { header("Authorization", "Bearer $token") }.bodyAsText(),
        )!!.groupValues[1]

    private suspend fun befriend(client: HttpClient, a: String, b: String) {
        val aCode = Regex("\"code\":\"([^\"]+)\"").find(
            client.get("/v1/friends/code") { header("Authorization", "Bearer $a") }.bodyAsText(),
        )!!.groupValues[1]
        client.post("/v1/friends/requests") {
            header("Authorization", "Bearer $b")
            contentType(ContentType.Application.Json)
            setBody("""{"code":"$aCode"}""")
        }
        val reqId = Regex("\"id\":\"([^\"]+)\"").find(
            client.get("/v1/friends") { header("Authorization", "Bearer $a") }.bodyAsText(),
        )!!.groupValues[1]
        client.post("/v1/friends/requests/$reqId/accept") { header("Authorization", "Bearer $a") }
    }

    private suspend fun startDirect(client: HttpClient, token: String, otherId: String): String =
        Regex("\"id\":\"([^\"]+)\"").find(
            client.post("/v1/conversations/direct") {
                header("Authorization", "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody("""{"userId":"$otherId"}""")
            }.bodyAsText(),
        )!!.groupValues[1]

    private suspend fun send(client: HttpClient, token: String, convId: String, body: String) =
        client.post("/v1/conversations/$convId/messages") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"body":"$body"}""")
        }

    @Test
    fun friendsCanDmAndSeeUnread() = testApplication {
        application { module(emailSender = NoopEmailSender) }
        val client = jsonClient()
        val alice = signIn(client, "alice.chat@example.com", "Alice")
        val bob = signIn(client, "bob.chat@example.com", "Bob")
        befriend(client, alice, bob)
        val bobId = userId(client, bob)

        val conv = startDirect(client, alice, bobId)
        assertEquals(HttpStatusCode.OK, send(client, alice, conv, "Hey Bob!").status)

        // Bob sees the message in history and an unread count in his inbox.
        val history = client.get("/v1/conversations/$conv/messages") { header("Authorization", "Bearer $bob") }.bodyAsText()
        assertTrue(history.contains("Hey Bob!"), history)
        val inbox = client.get("/v1/conversations") { header("Authorization", "Bearer $bob") }.bodyAsText()
        assertTrue(inbox.contains("\"unreadCount\":1"), inbox)
        assertTrue(inbox.contains("Alice"), inbox) // DM titled with the other person's name
    }

    @Test
    fun cannotDmANonFriend() = testApplication {
        application { module(emailSender = NoopEmailSender) }
        val client = jsonClient()
        val alice = signIn(client, "a2.chat@example.com", "Alice")
        val bob = signIn(client, "b2.chat@example.com", "Bob")
        val bobId = userId(client, bob)

        val res = client.post("/v1/conversations/direct") {
            header("Authorization", "Bearer $alice")
            contentType(ContentType.Application.Json)
            setBody("""{"userId":"$bobId"}""")
        }
        assertEquals(HttpStatusCode.Conflict, res.status)
        assertTrue(res.bodyAsText().contains("NOT_FRIENDS"))
    }

    @Test
    fun blockHidesTheSendersMessages() = testApplication {
        application { module(emailSender = NoopEmailSender) }
        val client = jsonClient()
        val alice = signIn(client, "a3.chat@example.com", "Alice")
        val bob = signIn(client, "b3.chat@example.com", "Bob")
        befriend(client, alice, bob)
        val aliceId = userId(client, alice)
        val bobId = userId(client, bob)
        val conv = startDirect(client, alice, bobId)
        send(client, alice, conv, "before block")

        // Bob blocks Alice; her later message is hidden from Bob's history.
        assertEquals(
            HttpStatusCode.NoContent,
            client.post("/v1/users/$aliceId/block") { header("Authorization", "Bearer $bob") }.status,
        )
        send(client, alice, conv, "after block")
        val bobHistory = client.get("/v1/conversations/$conv/messages") { header("Authorization", "Bearer $bob") }.bodyAsText()
        assertFalse(bobHistory.contains("after block"), bobHistory)
        assertFalse(bobHistory.contains("before block"), bobHistory)
    }

    @Test
    fun eventGroupChatReachesRegistrants() = testApplication {
        application { module(emailSender = NoopEmailSender) }
        val client = jsonClient()
        val host = signIn(client, "host.chat@example.com", "Host")
        val guest = signIn(client, "guest.chat@example.com", "Guest")
        val eventId = Regex("\"id\":\"([^\"]+)\"").find(
            client.post("/v1/events") {
                header("Authorization", "Bearer $host")
                contentType(ContentType.Application.Json)
                setBody("""{"title":"Group Hike","category":"PARK","lat":41.0,"lng":-87.0,"startsAt":"2999-01-01T18:00:00Z","registrationType":"FREE"}""")
            }.bodyAsText(),
        )!!.groupValues[1]
        client.post("/v1/events/$eventId/register") { header("Authorization", "Bearer $guest") }

        // Host opens the event chat and posts; the registered guest sees it.
        val conv = Regex("\"id\":\"([^\"]+)\"").find(
            client.get("/v1/events/$eventId/conversation") { header("Authorization", "Bearer $host") }.bodyAsText(),
        )!!.groupValues[1]
        send(client, host, conv, "Meet at the trailhead")
        val guestHistory = client.get("/v1/conversations/$conv/messages") { header("Authorization", "Bearer $guest") }.bodyAsText()
        assertTrue(guestHistory.contains("Meet at the trailhead"), guestHistory)
    }

    @Test
    fun strangerCannotJoinEventChat() = testApplication {
        application { module(emailSender = NoopEmailSender) }
        val client = jsonClient()
        val host = signIn(client, "h4.chat@example.com", "Host")
        val stranger = signIn(client, "s4.chat@example.com", "Stranger")
        val eventId = Regex("\"id\":\"([^\"]+)\"").find(
            client.post("/v1/events") {
                header("Authorization", "Bearer $host")
                contentType(ContentType.Application.Json)
                setBody("""{"title":"Private Hike","category":"PARK","lat":41.0,"lng":-87.0,"startsAt":"2999-01-01T18:00:00Z","registrationType":"FREE"}""")
            }.bodyAsText(),
        )!!.groupValues[1]

        val res = client.get("/v1/events/$eventId/conversation") { header("Authorization", "Bearer $stranger") }
        assertEquals(HttpStatusCode.Conflict, res.status)
        assertTrue(res.bodyAsText().contains("NOT_A_MEMBER"))
    }

    @Test
    fun requireAuth() = testApplication {
        application { module(emailSender = NoopEmailSender) }
        assertEquals(HttpStatusCode.Unauthorized, jsonClient().get("/v1/conversations").status)
    }
}
