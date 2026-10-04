package com.example.questly.core.data

import com.example.questly.core.model.LeaderboardEntry
import com.example.questly.core.network.QuestlyApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import javax.inject.Inject
import javax.inject.Singleton

interface LeaderboardRepository {
    /** Cold stream of the live global leaderboard; reconnects on drop. */
    fun leaderboard(): Flow<List<LeaderboardEntry>>
    /** The signed-in user's id, so the UI can highlight their own row. */
    suspend fun myUserId(): String?
}

@Singleton
class RemoteLeaderboardRepository @Inject constructor(
    private val api: QuestlyApi,
) : LeaderboardRepository {

    override fun leaderboard(): Flow<List<LeaderboardEntry>> =
        api.leaderboardStream()
            .map { dto -> dto.entries.map { LeaderboardEntry(it.userId, it.displayName, it.totalPoints, it.rank) } }
            // The socket closing (idle, cold start, token refresh) isn't fatal — wait and reconnect.
            .retryWhen { _, _ -> delay(3_000); true }

    override suspend fun myUserId(): String? = runCatching { api.me().id }.getOrNull()
}
