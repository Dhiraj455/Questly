package com.example.questly.core.data

import com.example.questly.core.model.Achievement
import com.example.questly.core.model.Profile
import com.example.questly.core.network.ProfileDto
import com.example.questly.core.network.QuestlyApi
import com.example.questly.core.network.TokenStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
    tokens: TokenStore,
) : ProfileRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val profile = MutableStateFlow<Profile?>(null)

    init {
        // Drop the cached profile on sign-out; refresh for the new account on sign-in.
        scope.launch {
            tokens.signedIn.collect { signedIn ->
                if (signedIn) refresh() else profile.value = null
            }
        }
    }

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
