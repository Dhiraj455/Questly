package com.example.questly.backend

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.Application
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import java.net.URI

/** JDBC URL + credentials, however the host provides them. */
private data class DbConfig(val jdbcUrl: String, val user: String, val password: String)

/**
 * Accepts either separate JDBC settings (local dev / docker-compose) or a single connection URL in
 * the `postgres://user:pass@host:port/db` form that Render, Railway, Fly, Neon, etc. hand out.
 */
private fun resolveDbConfig(): DbConfig {
    val url = Env["DATABASE_URL"]
    if (url != null && (url.startsWith("postgres://") || url.startsWith("postgresql://"))) {
        val uri = URI(url)
        val (user, pass) = uri.userInfo?.split(":", limit = 2)?.let { it[0] to it.getOrElse(1) { "" } }
            ?: ((Env["DATABASE_USER"] ?: "") to (Env["DATABASE_PASSWORD"] ?: ""))
        val port = if (uri.port != -1) uri.port else 5432
        // TLS is required by most managed Postgres providers.
        val jdbc = "jdbc:postgresql://${uri.host}:$port${uri.path}?sslmode=require"
        return DbConfig(jdbc, user, pass)
    }
    // Local default matches docker-compose (host 5433, to dodge a local Postgres on 5432).
    return DbConfig(
        jdbcUrl = url ?: "jdbc:postgresql://localhost:5433/questly",
        user = Env["DATABASE_USER"] ?: "questly",
        password = Env["DATABASE_PASSWORD"] ?: "questly",
    )
}

/**
 * Process-wide database: one Hikari pool + one Exposed [Database] for the whole JVM, created on first
 * use. A server owns a single database for its entire lifetime, so there's nothing to tear down per
 * request or per Application — the pool lives until the process exits. Connecting exactly once also
 * keeps Exposed's global default stable when several Applications run in one JVM (e.g. one per
 * integration test); re-registering/closing per Application left transactions bound to a stale or
 * closed pool.
 */
private object DatabasePool {
    @Volatile private var dataSource: HikariDataSource? = null

    @Synchronized
    fun connectOnce() {
        if (dataSource != null) return
        val config = resolveDbConfig()
        val ds = HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = config.jdbcUrl
                username = config.user
                password = config.password
                driverClassName = "org.postgresql.Driver"
                maximumPoolSize = 5
            },
        )
        Flyway.configure()
            .dataSource(ds)
            .locations("classpath:db/migration")
            .load()
            .migrate()
        Database.connect(ds)
        dataSource = ds
    }
}

/** Connects the process-wide pool (idempotent) and runs migrations on first call. */
fun Application.configureDatabase() = DatabasePool.connectOnce()
