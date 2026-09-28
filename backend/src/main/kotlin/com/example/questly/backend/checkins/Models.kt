package com.example.questly.backend.checkins

import kotlinx.serialization.Serializable

@Serializable
data class CheckInRequest(
    val checkpointId: String,
    // The user's current location.
    val lat: Double,
    val lng: Double,
    val clientTimestamp: String,
    // The checkpoint's own location + category, supplied by the app (which fetched it from Overpass).
    // The server validates distance against these and derives the point value from [category] — it
    // never trusts a client-sent point total. See pointsForCategory().
    val checkpointLat: Double,
    val checkpointLng: Double,
    val category: String,
    val title: String,
)

@Serializable
data class CheckInDto(
    val id: String,
    val checkpointId: String,
    val title: String,
    val points: Int,
    val timestamp: String,
)

@Serializable
data class CheckInPage(val items: List<CheckInDto>, val nextCursor: String? = null)

@Serializable
data class PointsDto(val total: Int)

@Serializable
data class CheckInRejection(
    val reason: String,
    val message: String,
    val retryAfterSeconds: Int? = null,
)

/** Routed as a 409 with the contract's CheckInRejection body, rather than a generic error. */
class CheckInRejected(val body: CheckInRejection) : RuntimeException(body.message)
