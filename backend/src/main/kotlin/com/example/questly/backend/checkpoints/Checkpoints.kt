package com.example.questly.backend.checkpoints

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Shared checkpoint rules. Discovery is done on-device (the app queries Overpass directly, since
 * public Overpass servers block this host's datacenter IP), so the backend no longer fetches POIs;
 * these helpers are what server-side check-in validation needs.
 */

enum class PoiKind { PARK, BEACH, VIEWPOINT, LANDMARK }

private fun pointsFor(kind: PoiKind) = when (kind) {
    PoiKind.PARK -> 50
    PoiKind.BEACH -> 75
    PoiKind.VIEWPOINT -> 60
    PoiKind.LANDMARK -> 60
}

/**
 * Server-authoritative point value for a checkpoint [category] (PARK/BEACH/VIEWPOINT/LANDMARK),
 * or null if the category is unknown. Check-in awards are computed from this table, never from a
 * value the client sends, so rewards can't be inflated.
 */
fun pointsForCategory(category: String): Int? =
    runCatching { PoiKind.valueOf(category.uppercase()) }.getOrNull()?.let(::pointsFor)

/** How close the user must be to a checkpoint to check in. */
const val CHECK_IN_RADIUS_M = 150.0

/** Great-circle distance in metres. */
fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val r = 6_371_000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val a = sin(dLat / 2).pow(2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
    return r * 2 * atan2(sqrt(a), sqrt(1 - a))
}
