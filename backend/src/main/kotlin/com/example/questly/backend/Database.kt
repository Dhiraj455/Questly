package com.example.questly.backend

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database

/**
 * Wires a Hikari connection pool to PostgreSQL, runs Flyway migrations on startup, and exposes the
 * pool to Exposed. Configuration comes from env vars so no credentials live in the repo; the
 * defaults match docker-compose.yml for local development.
 */
fun Application.configureDatabase() {
    val jdbcUrl = System.getenv("DATABASE_URL") ?: "jdbc:postgresql://localhost:5432/questly"
    val user = System.getenv("DATABASE_USER") ?: "questly"
    val pass = System.getenv("DATABASE_PASSWORD") ?: "questly"

    val dataSource = HikariDataSource(
        HikariConfig().apply {
            this.jdbcUrl = jdbcUrl
            username = user
            password = pass
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
