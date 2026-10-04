package com.example.questly.backend

import com.example.questly.backend.auth.EmailSender
import org.flywaydb.core.Flyway
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.sql.DriverManager

/**
 * One shared PostgreSQL container for the whole test run. Accessing it starts the container, points
 * the app's config (via -D system properties, which Env reads) at it, and applies the migrations.
 */
object TestDb {
    val container: PostgreSQLContainer<*> = PostgreSQLContainer(DockerImageName.parse("postgres:16")).apply {
        start()
        System.setProperty("DATABASE_URL", jdbcUrl) // jdbc:postgresql://... — used as-is by resolveDbConfig
        System.setProperty("DATABASE_USER", username)
        System.setProperty("DATABASE_PASSWORD", password)
        System.setProperty("JWT_SECRET", "test-secret-not-used-in-production-000")
        // The whole suite hits the auth routes from one loopback IP within a minute; keep the
        // per-IP auth rate limit effectively unlimited so tests don't throttle each other.
        System.setProperty("AUTH_RATE_LIMIT_PER_MINUTE", "1000000")
        Flyway.configure()
            .dataSource(jdbcUrl, username, password)
            .locations("classpath:db/migration")
            .load()
            .migrate()
    }

    /** Wipes all rows so each test starts clean. */
    fun reset() {
        DriverManager.getConnection(container.jdbcUrl, container.username, container.password).use { c ->
            c.createStatement().use { it.execute("TRUNCATE checkins, refresh_tokens, email_tokens, users RESTART IDENTITY CASCADE") }
        }
    }

    /** Reads the latest verification token straight from the DB (stands in for opening the email). */
    fun latestVerifyToken(email: String): String =
        DriverManager.getConnection(container.jdbcUrl, container.username, container.password).use { c ->
            c.prepareStatement(
                "select t.token from email_tokens t join users u on u.id = t.user_id " +
                    "where u.email = ? and t.purpose = 'VERIFY' order by t.expires_at desc limit 1",
            ).use { ps ->
                ps.setString(1, email.lowercase())
                ps.executeQuery().use { rs ->
                    require(rs.next()) { "no verify token for $email" }
                    rs.getString(1)
                }
            }
        }
}

/** Swallows emails in tests (the token is read from the DB instead). */
object NoopEmailSender : EmailSender {
    override suspend fun sendVerification(toEmail: String, token: String) {}
    override suspend fun sendPasswordReset(toEmail: String, token: String) {}
}

/** The checkpoint the check-in tests send — the app supplies these fields with each check-in. */
data class TestCheckpoint(
    val id: String,
    val title: String,
    val lat: Double,
    val lng: Double,
    val category: String,
)

val TEST_CHECKPOINT = TestCheckpoint(
    id = "node/1",
    title = "Test Landmark",
    lat = 41.0,
    lng = -87.0,
    category = "LANDMARK",
)
