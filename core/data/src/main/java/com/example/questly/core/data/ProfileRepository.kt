package com.example.questly.core.data

import com.example.questly.core.model.Achievement
import com.example.questly.core.model.Profile
import com.example.questly.core.network.ProfileDto
import com.example.questly.core.network.QuestlyApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

interface ProfileRepository {
    fun observeProfile(): StateFlow<Profile?>
    /** Re-fetches streak + achievements. Silent on failure — the UI keeps the last value. */
    suspend fun refresh()
}

@Singleton
class RemoteProfileRepository @Inject constructor(
    private val api: QuestlyApi,
) : ProfileRepository {
    private val profile = MutableStateFlow<Profile?>(null)
    override fun observeProfile(): StateFlow<Profile?> = profile.asStateFlow()
    override suspend fun refresh() {
        runCatching { api.profile() }.onSuccess { profile.value = it.toProfile() }
    }
}

private fun ProfileDto.toProfile() = Profile(
    currentStreakDays = stats.currentStreakDays,
    longestStreakDays = stats.longestStreakDays,
    totalCheckIns = stats.totalCheckIns,
    totalPoints = stats.totalPoints,
    achievements = achievements.map {
        Achievement(it.id, it.title, it.description, it.target, it.value, it.earned)
    },
)
