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
import kotlin.test.assertTrue

class FriendsFlowTest {
    @BeforeTest fun clean() = TestDb.reset()

    private fun jsonBody(vararg pairs: Pair<String, String>) =
        pairs.joinToString(",", "{", "}") { (k, v) -> "\"$k\":\"$v\"" }

    private fun ApplicationTestBuilder.jsonClient() = createClient { install(ContentNegotiation) { json() } }

    private suspend fun signIn(client: HttpClient, email: String, name: String): String {
        client.post("/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(jsonBody("email" to email, "password" to "secret12345", "displayName" to name))
        }
        client.post("/v1/auth/verify-email") {
            contentType(ContentType.Application.Json)
            setBody(jsonBody("token" to TestDb.latestVerifyToken(email)))
        }
        val login = client.post("/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(jsonBody("email" to email, "password" to "secret12345"))
        }
        return Regex("\"accessToken\":\"([^\"]+)\"").find(login.bodyAsText())!!.groupValues[1]
    }

    private suspend fun myCode(client: HttpClient, token: String): String {
        val body = client.get("/v1/friends/code") { header("Authorization", "Bearer $token") }.bodyAsText()
        return Regex("\"code\":\"([^\"]+)\"").find(body)!!.groupValues[1]
    }

    @Test
    fun codeRequestAcceptMakesMutualFriends() = testApplication {
        application { module(emailSender = NoopEmailSender) }
        val client = jsonClient()
        val alice = signIn(client, "alice.friends@example.com", "Alice")
        val bob = signIn(client, "bob.friends@example.com", "Bob")

        val aliceCode = myCode(client, alice)

        // Bob sends Alice a request using her code.
        val sent = client.post("/v1/friends/requests") {
            header("Authorization", "Bearer $bob")
            contentType(ContentType.Application.Json)
            setBody(jsonBody("code" to aliceCode))
        }
        assertEquals(HttpStatusCode.Created, sent.status, "send request: ${sent.bodyAsText()}")

        // Alice sees the incoming request, then accepts it.
        val before = client.get("/v1/friends") { header("Authorization", "Bearer $alice") }.bodyAsText()
        assertTrue(before.contains("Bob"), "alice's incoming requests: $before")
        val requestId = Regex("\"id\":\"([^\"]+)\"").find(before)!!.groupValues[1]
        val accept = client.post("/v1/friends/requests/$requestId/accept") { header("Authorization", "Bearer $alice") }
        assertEquals(HttpStatusCode.NoContent, accept.status, "accept: ${accept.bodyAsText()}")

        // Both now list each other as a friend.
        val aliceFriends = client.get("/v1/friends") { header("Authorization", "Bearer $alice") }.bodyAsText()
        assertTrue(aliceFriends.contains("Bob"), "alice's friends: $aliceFriends")
        val bobFriends = client.get("/v1/friends") { header("Authorization", "Bearer $bob") }.bodyAsText()
        assertTrue(bobFriends.contains("Alice"), "bob's friends: $bobFriends")
    }

    @Test
    fun unknownCodeIsRejected() = testApplication {
        application { module(emailSender = NoopEmailSender) }
        val client = jsonClient()
        val alice = signIn(client, "carol.friends@example.com", "Carol")
        val res = client.post("/v1/friends/requests") {
            header("Authorization", "Bearer $alice")
            contentType(ContentType.Application.Json)
            setBody(jsonBody("code" to "QSTLY-ZZZZZZ"))
        }
        assertEquals(HttpStatusCode.Conflict, res.status)
        assertTrue(res.bodyAsText().contains("NOT_FOUND"))
    }

    @Test
    fun friendsRequireAuth() = testApplication {
        application { module(emailSender = NoopEmailSender) }
        assertEquals(HttpStatusCode.Unauthorized, jsonClient().get("/v1/friends").status)
    }
}
