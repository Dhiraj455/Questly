package com.example.questly.core.model

data class CheckIn(
    val id: String,
    val checkpointId: String,
    val timestampMillis: Long,
    // Snapshotted at check-in time: Overpass checkpoints are an ephemeral cache, so the row must
    // carry its own title/points to survive the next refresh.
    val title: String = "",
    val points: Int = 0,
)
