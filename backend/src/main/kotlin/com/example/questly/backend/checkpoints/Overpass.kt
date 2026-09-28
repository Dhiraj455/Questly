package com.example.questly.backend.checkpoints

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.header
import io.ktor.client.request.setBody
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

private enum class PoiKind { PARK, BEACH, VIEWPOINT, LANDMARK }

private fun pointsFor(kind: PoiKind) = when (kind) {
    PoiKind.PARK -> 50
    PoiKind.BEACH -> 75
    PoiKind.VIEWPOINT -> 60
    PoiKind.LANDMARK -> 60
}

private fun descriptionFor(kind: PoiKind) = when (kind) {
    PoiKind.PARK -> "Explore this park to earn points."
    PoiKind.BEACH -> "Visit this beach to earn points."
    PoiKind.VIEWPOINT -> "Take in this viewpoint to earn points."
    PoiKind.LANDMARK -> "Visit this landmark to earn points."
}

private const val CHECK_IN_RADIUS_M = 150.0
private const val MAX_ELEMENTS = 200

/** Great-circle distance in metres. */
fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val r = 6_371_000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val a = sin(dLat / 2).pow(2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
    return r * 2 * atan2(sqrt(a), sqrt(1 - a))
}

/** Source of quest checkpoints. Swappable so tests don't hit the real Overpass network. */
interface OverpassClient {
    suspend fun query(lat: Double, lng: Double, radiusMeters: Double): List<CheckpointDto>
    suspend fun findById(checkpointId: String): CheckpointDto?
}

/** Server-side Overpass client: fetches, classifies, and maps quest POIs to checkpoints. */
class HttpOverpassClient(
    private val endpoint: String = "https://overpass-api.de/api/interpreter",
) : OverpassClient {
    private val client = HttpClient(CIO)
    private val json = Json { ignoreUnknownKeys = true }

    /** Throws IOException on network/HTTP failure so the caller can serve cache or a 502. */
    override suspend fun query(lat: Double, lng: Double, radiusMeters: Double): List<CheckpointDto> {
        return execute(buildQuery(lat, lng, radiusMeters))
    }

    /** Fetches one stable Overpass element, allowing check-in validation without trusting the app. */
    override suspend fun findById(checkpointId: String): CheckpointDto? {
        val match = Regex("^(node|way|relation)/(\\d+)$").matchEntire(checkpointId) ?: return null
        val query = "[out:json][timeout:25];${match.groupValues[1]}(${match.groupValues[2]});out center 1;"
        return execute(query).firstOrNull()
    }

    private suspend fun execute(query: String): List<CheckpointDto> {
        val body = "data=" + query
        val response = client.post(endpoint) {
            header("User-Agent", "Questly/1.0 (backend)")
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(body)
        }
        if (!response.status.isSuccess()) {
            throw IOException("Overpass HTTP ${response.status}")
        }
        return parse(response.bodyAsText())
    }

    private fun buildQuery(lat: Double, lng: Double, radiusMeters: Double): String {
        val r = radiusMeters.toInt()
        val around = "(around:$r,$lat,$lng)"
        return """
            [out:json][timeout:25];
            (
              nwr["leisure"="park"]$around;
              nwr["natural"="beach"]$around;
              nwr["tourism"="viewpoint"]$around;
              nwr["tourism"="attraction"]$around;
              nwr["historic"]$around;
            );
            out center $MAX_ELEMENTS;
        """.trimIndent()
    }

    private fun parse(raw: String): List<CheckpointDto> =
        json.decodeFromString<OverpassResponse>(raw).elements.mapNotNull { it.toCheckpoint() }

    private fun OverpassElement.toCheckpoint(): CheckpointDto? {
        val name = tags["name"]?.takeIf { it.isNotBlank() } ?: return null
        val kind = classify(tags) ?: return null
        val latitude = lat ?: center?.lat ?: return null
        val longitude = lon ?: center?.lon ?: return null
        return CheckpointDto(
            id = "$type/$id",
            title = name,
            description = descriptionFor(kind),
            lat = latitude,
            lng = longitude,
            radiusMeters = CHECK_IN_RADIUS_M,
            points = pointsFor(kind),
            category = kind.name,
        )
    }

    private fun classify(tags: Map<String, String>): PoiKind? = when {
        tags["leisure"] == "park" -> PoiKind.PARK
        tags["natural"] == "beach" -> PoiKind.BEACH
        tags["tourism"] == "viewpoint" -> PoiKind.VIEWPOINT
        tags["tourism"] == "attraction" -> PoiKind.LANDMARK
        tags.containsKey("historic") -> PoiKind.LANDMARK
        else -> null
    }

    @Serializable
    private data class OverpassResponse(val elements: List<OverpassElement> = emptyList())

    @Serializable
    private data class OverpassElement(
        val type: String,
        val id: Long,
        val lat: Double? = null,
        val lon: Double? = null,
        val center: Center? = null,
        val tags: Map<String, String> = emptyMap(),
    ) {
        @Serializable data class Center(val lat: Double, val lon: Double)
    }
}
