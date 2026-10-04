package com.example.questly.backend.push

import com.example.questly.backend.Env
import com.example.questly.backend.db.DeviceTokens
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.MulticastMessage
import com.google.firebase.messaging.Notification
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.File
import java.util.UUID

/**
 * Sends FCM push notifications. Disabled (a no-op) when no service-account credential is configured,
 * mirroring the email sender's dev fallback — the app still works, it just won't push.
 *
 * Credential source, in order: FIREBASE_CREDENTIALS (the service-account JSON itself, e.g. a Render
 * secret), else GOOGLE_APPLICATION_CREDENTIALS (a path to that JSON file for local dev).
 */
class PushSender private constructor(private val messaging: FirebaseMessaging?) {

    val enabled: Boolean get() = messaging != null

    /** Pushes a notification to every registered device of the given users. Best-effort. */
    suspend fun sendToUsers(userIds: List<UUID>, title: String, body: String) {
        val fcm = messaging ?: return
        if (userIds.isEmpty()) return
        val tokens = newSuspendedTransaction(Dispatchers.IO) {
            DeviceTokens.selectAll().where { DeviceTokens.userId inList userIds }
                .map { it[DeviceTokens.token] }
        }
        if (tokens.isEmpty()) return
        withContext(Dispatchers.IO) {
            tokens.chunked(500).forEach { chunk -> // FCM multicast caps at 500 tokens per call
                runCatching {
                    fcm.sendEachForMulticast(
                        MulticastMessage.builder()
                            .setNotification(Notification.builder().setTitle(title).setBody(body).build())
                            .addAllTokens(chunk)
                            .build(),
                    )
                }.onFailure { log.warn("FCM send failed: {}", it.message) }
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(PushSender::class.java)

        fun fromEnv(): PushSender {
            val stream = Env["FIREBASE_CREDENTIALS"]?.takeIf { it.isNotBlank() }
                ?.let { ByteArrayInputStream(it.toByteArray()) }
                ?: Env["GOOGLE_APPLICATION_CREDENTIALS"]?.takeIf { it.isNotBlank() && File(it).exists() }
                    ?.let { File(it).inputStream() }
            if (stream == null) {
                log.info("Push: FIREBASE_CREDENTIALS not set — notifications disabled")
                return PushSender(null)
            }
            val options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(stream))
                .build()
            val app = if (FirebaseApp.getApps().isEmpty()) FirebaseApp.initializeApp(options) else FirebaseApp.getInstance()
            log.info("Push: Firebase messaging enabled")
            return PushSender(FirebaseMessaging.getInstance(app))
        }
    }
}
