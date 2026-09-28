package com.example.questly.backend

import java.io.File

/**
 * Reads configuration from real environment variables first, falling back to a local .env file so
 * `./gradlew -p backend run` picks up secrets without manual exports. Real env always wins, so
 * deployed environments are unaffected. .env is git-ignored.
 */
object Env {
    private val dotenv: Map<String, String> by lazy { load() }

    // Real env wins, then -D system properties (used by tests), then a local .env file.
    operator fun get(key: String): String? = System.getenv(key) ?: System.getProperty(key) ?: dotenv[key]

    private fun load(): Map<String, String> {
        val file = listOf(File(".env"), File("backend/.env")).firstOrNull { it.exists() } ?: return emptyMap()
        return file.readLines().mapNotNull { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#") || !trimmed.contains("=")) return@mapNotNull null
            val idx = trimmed.indexOf('=')
            trimmed.substring(0, idx).trim() to trimmed.substring(idx + 1).trim()
        }.toMap()
    }
}
