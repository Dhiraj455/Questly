package com.example.questly.backend.auth

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

/**
 * Sends verification / reset emails through Brevo's transactional email API (POST /v3/smtp/email).
 * Credentials come from env (BREVO_API_KEY, BREVO_SENDER_EMAIL); the sender address must be a
 * verified sender in the Brevo account.
 */
class BrevoEmailSender(
    private val apiKey: String,
    private val senderEmail: String,
    private val senderName: String = "Questly",
    private val appBaseUrl: String = com.example.questly.backend.Env["APP_BASE_URL"] ?: "http://localhost:8081",
) : EmailSender {
    private val log = LoggerFactory.getLogger(BrevoEmailSender::class.java)
    private val client = HttpClient(CIO) { install(ContentNegotiation) { json() } }

    @Serializable
    private data class Contact(val email: String, val name: String? = null)

    @Serializable
    private data class SendEmailRequest(
        val sender: Contact,
        val to: List<Contact>,
        val subject: String,
        val htmlContent: String,
    )

    override suspend fun sendVerification(toEmail: String, token: String) {
        val link = "$appBaseUrl/v1/auth/verify-email?token=$token"
        send(
            toEmail,
            subject = "Verify your Questly email",
            html = """
                <p>Welcome to Questly!</p>
                <p>Please confirm your email address by tapping the button below.</p>
                <p><a href="$link" style="display:inline-block;padding:10px 18px;background:#0f766e;color:#fff;border-radius:8px;text-decoration:none">Verify my email</a></p>
                <p>If the button doesn't work, open this link:<br>$link</p>
            """.trimIndent(),
        )
    }

    override suspend fun sendPasswordReset(toEmail: String, token: String) {
        send(
            toEmail,
            subject = "Reset your Questly password",
            html = "<p>Use this token to reset your password: <b>$token</b></p>",
        )
    }

    private suspend fun send(toEmail: String, subject: String, html: String) {
        val response = client.post("https://api.brevo.com/v3/smtp/email") {
            header("api-key", apiKey)
            contentType(ContentType.Application.Json)
            setBody(
                SendEmailRequest(
                    sender = Contact(senderEmail, senderName),
                    to = listOf(Contact(toEmail)),
                    subject = subject,
                    htmlContent = html,
                ),
            )
        }
        if (response.status.isSuccess()) {
            log.info("Brevo accepted email to {} ({})", toEmail, response.status)
        } else {
            // Don't fail registration if email delivery hiccups; log for diagnosis.
            log.error("Brevo send failed ({}): {}", response.status, response.bodyAsText())
        }
    }
}
