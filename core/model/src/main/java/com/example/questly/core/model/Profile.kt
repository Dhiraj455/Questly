package com.example.questly.core.model

data class Achievement(
    val id: String,
    val title: String,
    val description: String,
    val target: Int,
    val value: Int,
    val earned: Boolean,
) {
    val progress: Float get() = if (target <= 0) 1f else (value.toFloat() / target).coerceIn(0f, 1f)
}

/** The user's gamification profile: streak + totals and achievement progress. */
data class Profile(
    val currentStreakDays: Int,
    val longestStreakDays: Int,
    val totalCheckIns: Int,
    val totalPoints: Int,
    val achievements: List<Achievement>,
)
