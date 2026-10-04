package com.example.questly.backend.profile

import kotlinx.serialization.Serializable

@Serializable
data class StatsDto(
    val currentStreakDays: Int,
    val longestStreakDays: Int,
    val totalCheckIns: Int,
    val totalPoints: Int,
)

@Serializable
data class AchievementDto(
    val id: String,
    val title: String,
    val description: String,
    val target: Int,
    val value: Int,
    val earned: Boolean,
)

/** The signed-in user's gamification profile: streak + totals, plus every achievement with progress. */
@Serializable
data class ProfileDto(
    val stats: StatsDto,
    val achievements: List<AchievementDto>,
)
