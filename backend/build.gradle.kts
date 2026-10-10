import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.2.10"
    kotlin("plugin.serialization") version "2.2.10"
    application
}

group = "com.example.questly"
version = "0.1.0"

repositories { mavenCentral() }

val ktor = "3.0.3"
val exposed = "0.57.0"

dependencies {
    // Ktor server
    implementation("io.ktor:ktor-server-core:$ktor")
    implementation("io.ktor:ktor-server-netty:$ktor")
    implementation("io.ktor:ktor-server-content-negotiation:$ktor")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor")
    implementation("io.ktor:ktor-server-status-pages:$ktor")
    implementation("io.ktor:ktor-server-call-logging:$ktor")
    implementation("io.ktor:ktor-server-auth:$ktor")
    implementation("io.ktor:ktor-server-auth-jwt:$ktor")
    implementation("io.ktor:ktor-server-rate-limit:$ktor")
    // Reads X-Forwarded-* so per-client rate limiting sees the real caller IP behind Render's proxy.
    implementation("io.ktor:ktor-server-forwarded-header:$ktor")
    // Real-time leaderboard pushed over WebSocket.
    implementation("io.ktor:ktor-server-websockets:$ktor")
    // Firebase Admin SDK for sending FCM push notifications (loads a service-account credential).
    implementation("com.google.firebase:firebase-admin:9.4.1")

    // Auth: JWT issuing + password hashing
    implementation("com.auth0:java-jwt:4.4.0")
    implementation("at.favre.lib:bcrypt:0.10.2")

    // HTTP client for the Brevo transactional-email API
    implementation("io.ktor:ktor-client-core:$ktor")
    implementation("io.ktor:ktor-client-cio:$ktor")
    implementation("io.ktor:ktor-client-content-negotiation:$ktor")

    // Persistence: Exposed + Hikari + PostgreSQL + Flyway migrations
    implementation("org.jetbrains.exposed:exposed-core:$exposed")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposed")
    implementation("org.jetbrains.exposed:exposed-java-time:$exposed")
    implementation("com.zaxxer:HikariCP:6.2.1")
    implementation("org.postgresql:postgresql:42.7.4")
    implementation("org.flywaydb:flyway-core:10.20.1")
    implementation("org.flywaydb:flyway-database-postgresql:10.20.1")

    implementation("ch.qos.logback:logback-classic:1.5.12")

    testImplementation("io.ktor:ktor-server-test-host:$ktor")
    testImplementation("io.ktor:ktor-client-content-negotiation:$ktor")
    testImplementation(kotlin("test"))
    // Integration tests against a real PostgreSQL in a container. 1.21.4+ negotiates Docker API
    // v1.44 (Engine 29 dropped the v1.32 that 1.21.3 hardcoded, so older TC fails with HTTP 400).
    testImplementation("org.testcontainers:postgresql:1.21.4")
}

application {
    mainClass.set("com.example.questly.backend.ApplicationKt")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

tasks.test {
    useJUnitPlatform()
    // On Windows, point Testcontainers at Docker Desktop's pipe unless DOCKER_HOST is already set
    // (e.g. to a TCP endpoint). CI/Linux/macOS auto-detect the standard socket.
    if (System.getProperty("os.name").startsWith("Windows") && System.getenv("DOCKER_HOST").isNullOrBlank()) {
        environment("DOCKER_HOST", "npipe:////./pipe/dockerDesktopLinuxEngine")
    }
}
