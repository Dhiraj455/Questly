package com.example.questly.backend.checkpoints

import kotlinx.serialization.Serializable

/** Matches the Checkpoint schema in docs/api/openapi.yaml. */
@Serializable
data class CheckpointDto(
    val id: String,
    val title: String,
    val description: String,
    val lat: Double,
    val lng: Double,
    val radiusMeters: Double,
    val points: Int,
    val category: String, // PARK | BEACH | VIEWPOINT | LANDMARK
)
