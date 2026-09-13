package com.example.questly.core.network

enum class PoiKind { PARK, BEACH, VIEWPOINT, LANDMARK }

/** A named point of interest returned by Overpass, already classified into a [PoiKind]. */
data class OverpassPoi(
    val id: String,
    val lat: Double,
    val lng: Double,
    val name: String,
    val kind: PoiKind,
)
