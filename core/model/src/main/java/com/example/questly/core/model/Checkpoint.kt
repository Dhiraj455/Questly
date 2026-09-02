package com.example.questly.core.model

enum class CheckpointKind { CHALLENGE, EVENT }

data class Checkpoint(
    val id: String,
    val title: String,
    val description: String,
    val lat: Double,
    val lng: Double,
    val radiusMeters: Double,
    val points: Int,
    val kind: CheckpointKind,
)
