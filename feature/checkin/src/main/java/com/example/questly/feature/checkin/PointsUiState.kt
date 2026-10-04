package com.example.questly.feature.checkin

import com.example.questly.core.model.CheckIn
import com.example.questly.core.model.Profile

data class PointsUiState(
    val totalPoints: Int = 0,
    val history: List<CheckIn> = emptyList(),
    val profile: Profile? = null,
)
