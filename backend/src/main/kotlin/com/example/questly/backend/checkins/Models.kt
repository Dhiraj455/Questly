package com.example.questly.backend.checkins

import kotlinx.serialization.Serializable

@Serializable
data class CheckInRequest(
    val checkpointId: String,
    val lat: Double,
    val lng: Double,
    val clientTimestamp: String,
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
