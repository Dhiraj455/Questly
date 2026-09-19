package com.example.questly.feature.map

import com.example.questly.core.location.UserLocation
import com.example.questly.core.model.Checkpoint

data class CheckpointUi(
    val checkpoint: Checkpoint,
    val withinRange: Boolean,
    val distanceMeters: Double?,
    // True when a check-in for this checkpoint is still within the cooldown window, so the user
    // can't check in again yet.
    val checkedIn: Boolean = false,
)

data class MapUiState(
    val userLocation: UserLocation? = null,
    val checkpoints: List<CheckpointUi> = emptyList(),
    val radiusMeters: Double = DEFAULT_RADIUS_M,
    val isLoading: Boolean = false,
    val error: String? = null,
)

const val MIN_RADIUS_M = 1_000.0
const val MAX_RADIUS_M = 20_000.0
const val DEFAULT_RADIUS_M = 5_000.0

/** A one-shot request to move the map camera. [serial] makes repeat taps re-trigger. */
data class FocusTarget(val lat: Double, val lng: Double, val serial: Int)
