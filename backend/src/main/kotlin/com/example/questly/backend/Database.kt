package com.example.questly.backend

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
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
 * Wires a Hikari connection pool to PostgreSQL, runs Flyway migrations on startup, and exposes the
 * pool to Exposed. Configuration comes from env vars so no credentials live in the repo; the
 * defaults match docker-compose.yml for local development.
 */
fun Application.configureDatabase() {
    val config = resolveDbConfig()

    val dataSource = HikariDataSource(
        HikariConfig().apply {
            jdbcUrl = config.jdbcUrl
            username = config.user
            password = config.password
            driverClassName = "org.postgresql.Driver"
            maximumPoolSize = 5
        },
    )

    Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration")
        .load()
        .migrate()

    Database.connect(dataSource)

    monitor.subscribe(ApplicationStopped) { dataSource.close() }
}
