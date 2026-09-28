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
import io.ktor.server.testing.testApplication
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuthFlowTest {
    @BeforeTest fun clean() = TestDb.reset()

    private fun jsonBody(vararg pairs: Pair<String, String>) =
        pairs.joinToString(",", "{", "}") { (k, v) -> "\"$k\":\"$v\"" }

    @Test
    fun registerVerifyLoginAndMe() = testApplication {
        application { module(overpass = FakeOverpass(emptyList()), emailSender = NoopEmailSender) }
        val client = createClient { install(ContentNegotiation) { json() } }
        val email = "alice@example.com"

        val register = client.post("/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(jsonBody("email" to email, "password" to "secret12345", "displayName" to "Alice"))
        }
        assertEquals(HttpStatusCode.Accepted, register.status)

        // Login before verifying is rejected.
        val early = client.post("/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(jsonBody("email" to email, "password" to "secret12345"))
        }
        assertEquals(HttpStatusCode.Forbidden, early.status)

        // Verify with the emailed token, then the account is active.
        val verify = client.post("/v1/auth/verify-email") {
            contentType(ContentType.Application.Json)
            setBody(jsonBody("token" to TestDb.latestVerifyToken(email)))
        }
        assertEquals(HttpStatusCode.OK, verify.status)
        assertTrue(verify.bodyAsText().contains("accessToken"))

        val login = client.post("/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(jsonBody("email" to email, "password" to "secret12345"))
        }
        assertEquals(HttpStatusCode.OK, login.status)
        val access = Regex("\"accessToken\":\"([^\"]+)\"").find(login.bodyAsText())!!.groupValues[1]

        val me = client.get("/v1/auth/me") { header("Authorization", "Bearer $access") }
        assertEquals(HttpStatusCode.OK, me.status)
        assertTrue(me.bodyAsText().contains(email))

        val noToken = client.get("/v1/auth/me")
        assertEquals(HttpStatusCode.Unauthorized, noToken.status)
    }

    @Test
    fun wrongPasswordIsRejected() = testApplication {
        application { module(overpass = FakeOverpass(emptyList()), emailSender = NoopEmailSender) }
        val client = createClient { install(ContentNegotiation) { json() } }
        val email = "bob@example.com"
        client.post("/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(jsonBody("email" to email, "password" to "secret12345", "displayName" to "Bob"))
        }
        client.post("/v1/auth/verify-email") {
            contentType(ContentType.Application.Json)
            setBody(jsonBody("token" to TestDb.latestVerifyToken(email)))
        }
        val bad = client.post("/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(jsonBody("email" to email, "password" to "wrong-password"))
        }
        assertEquals(HttpStatusCode.Unauthorized, bad.status)
    }
}
