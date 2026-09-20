package com.example.questly.backend.auth

import org.slf4j.LoggerFactory

interface EmailSender {
    suspend fun sendVerification(toEmail: String, token: String)
    suspend fun sendPasswordReset(toEmail: String, token: String)
}

/**
 * Dev email sender: logs the token/link instead of sending. Lets the whole auth flow be exercised
 * without a real provider. Swap for a BrevoEmailSender once BREVO_API_KEY is set.
 */
class LoggingEmailSender(
    private val appBaseUrl: String = System.getenv("APP_BASE_URL") ?: "http://localhost:8080",
) : EmailSender {
    private val log = LoggerFactory.getLogger(LoggingEmailSender::class.java)

    override suspend fun sendVerification(toEmail: String, token: String) {
        log.info("[DEV EMAIL] Verify {} -> POST /v1/auth/verify-email with token={}", toEmail, token)
        log.info("[DEV EMAIL] (would link) {}/verify?token={}", appBaseUrl, token)
    }

    override suspend fun sendPasswordReset(toEmail: String, token: String) {
        log.info("[DEV EMAIL] Reset {} -> POST /v1/auth/password/reset with token={}", toEmail, token)
    }
}
