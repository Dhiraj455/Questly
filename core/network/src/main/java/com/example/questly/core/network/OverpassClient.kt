package com.example.questly.core.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject

interface OverpassClient {
    /** Fetches quest-worthy POIs within [radiusMeters] of the point. Throws on network/HTTP failure. */
    suspend fun query(lat: Double, lng: Double, radiusMeters: Double): List<OverpassPoi>
}

class OverpassClientImpl @Inject constructor() : OverpassClient {
    override suspend fun query(lat: Double, lng: Double, radiusMeters: Double): List<OverpassPoi> =
        withContext(Dispatchers.IO) {
            val body = "data=" + URLEncoder.encode(buildOverpassQuery(lat, lng, radiusMeters), "UTF-8")
            // A single instance rate-limits or times out on heavier (wider-radius) queries, which
            // surfaced as an intermittent "Couldn't refresh". Fall over to the next public mirror
            // before giving up; a phone's network reaches all of them.
            var lastError: Exception? = null
            for (endpoint in ENDPOINTS) {
                try {
                    return@withContext fetch(endpoint, body)
                } catch (e: IOException) {
                    lastError = e
                }
            }
            throw IOException("All Overpass endpoints failed", lastError)
        }

    private fun fetch(endpoint: String, body: String): List<OverpassPoi> {
        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 10_000
            readTimeout = 25_000
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            // Overpass usage policy requires an identifying User-Agent; anonymous/default-Java
            // UA requests get throttled or blocked (429/403).
            setRequestProperty("User-Agent", USER_AGENT)
        }
        return try {
            conn.outputStream.use { it.write(body.toByteArray()) }
            val code = conn.responseCode
            if (code !in 200..299) {
                // Drain the error body so the failure message says *why* (e.g. rate limit).
                val detail = conn.errorStream?.bufferedReader()?.use { it.readText() }?.take(200).orEmpty()
                throw IOException("Overpass HTTP $code${if (detail.isNotBlank()) ": $detail" else ""}")
            }
            val json = conn.inputStream.bufferedReader().use { it.readText() }
            parseOverpassJson(json)
        } finally {
            conn.disconnect()
        }
    }

    private companion object {
        // Global full-planet Overpass instances, tried in order. No key required.
        val ENDPOINTS = listOf(
            "https://overpass-api.de/api/interpreter",
            "https://overpass.kumi.systems/api/interpreter",
            "https://overpass.private.coffee/api/interpreter",
        )
        const val USER_AGENT = "Questly/1.0 (Android)"
    }
}
