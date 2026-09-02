package com.example.questly.feature.checkin

import com.example.questly.core.model.CheckIn

data class PointsUiState(
    val totalPoints: Int = 0,
    val history: List<CheckIn> = emptyList(),
)
