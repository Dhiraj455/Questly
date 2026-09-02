package com.example.questly.feature.map

import com.example.questly.core.location.UserLocation
import com.example.questly.core.model.Checkpoint

data class CheckpointUi(val checkpoint: Checkpoint, val withinRange: Boolean)

data class MapUiState(
    val userLocation: UserLocation? = null,
    val checkpoints: List<CheckpointUi> = emptyList(),
)
