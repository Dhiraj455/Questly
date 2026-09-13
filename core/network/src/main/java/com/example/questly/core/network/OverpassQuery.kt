package com.example.questly.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Builds a single Overpass QL query fetching all quest-worthy POI types within [radiusMeters]. */
fun buildOverpassQuery(lat: Double, lng: Double, radiusMeters: Double): String {
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

/** Parses an Overpass JSON response into named, classified POIs. Unnamed or unclassifiable elements are dropped. */
fun parseOverpassJson(json: String): List<OverpassPoi> =
    lenientJson.decodeFromString<OverpassResponse>(json).elements.mapNotNull { it.toPoi() }

// Upper bound on elements Overpass returns. Set above the repository's quest cap so the RADIUS,
// not this number, decides how many quests come back within a given area.
private const val MAX_ELEMENTS = 200

private val lenientJson = Json { ignoreUnknownKeys = true }

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

private fun OverpassElement.toPoi(): OverpassPoi? {
    val name = tags["name"]?.takeIf { it.isNotBlank() } ?: return null
    val kind = classify(tags) ?: return null
    val latitude = lat ?: center?.lat ?: return null
    val longitude = lon ?: center?.lon ?: return null
    return OverpassPoi(id = "$type/$id", lat = latitude, lng = longitude, name = name, kind = kind)
}

private fun classify(tags: Map<String, String>): PoiKind? = when {
    tags["leisure"] == "park" -> PoiKind.PARK
    tags["natural"] == "beach" -> PoiKind.BEACH
    tags["tourism"] == "viewpoint" -> PoiKind.VIEWPOINT
    tags["tourism"] == "attraction" -> PoiKind.LANDMARK
    tags.containsKey("historic") -> PoiKind.LANDMARK
    else -> null
}
