package com.example.questly.feature.map

import com.example.questly.core.location.UserLocation
import com.example.questly.core.model.Checkpoint

data class CheckpointUi(
    val checkpoint: Checkpoint,
    val withinRange: Boolean,
    val distanceMeters: Double?,
)

data class MapUiState(
    val userLocation: UserLocation? = null,
    val checkpoints: List<CheckpointUi> = emptyList(),
)

/** A one-shot request to move the map camera. [serial] makes repeat taps re-trigger. */
data class FocusTarget(val lat: Double, val lng: Double, val serial: Int)
