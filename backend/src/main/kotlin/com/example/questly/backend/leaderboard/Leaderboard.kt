package com.example.questly.backend.leaderboard

import com.example.questly.backend.db.CheckIns
import com.example.questly.backend.db.Users
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.sum
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

@Serializable
data class LeaderboardEntryDto(
    val userId: String,
    val displayName: String,
    val totalPoints: Int,
    val rank: Int,
)

@Serializable
data class LeaderboardDto(val entries: List<LeaderboardEntryDto>)

/** Computes the global ranking of users by total points. */
class LeaderboardService {
    suspend fun top(limit: Int): LeaderboardDto = newSuspendedTransaction(Dispatchers.IO) {
        val total = CheckIns.points.sum()
        val rows = CheckIns
            .join(Users, JoinType.INNER, CheckIns.userId, Users.id)
            .select(CheckIns.userId, Users.displayName, total)
            .groupBy(CheckIns.userId, Users.displayName)
            .orderBy(total, SortOrder.DESC)
            .limit(limit)
            .toList()
        LeaderboardDto(
            rows.mapIndexed { index, row ->
                LeaderboardEntryDto(
                    userId = row[CheckIns.userId].toString(),
                    displayName = row[Users.displayName],
                    totalPoints = row[total] ?: 0,
                    rank = index + 1,
                )
            },
        )
    }
}

/**
 * Fans out leaderboard snapshots to connected WebSocket clients. A new snapshot is emitted whenever
 * a check-in changes the standings; the replay buffer means a just-connected client gets the latest
 * immediately.
 */
class LeaderboardHub(private val service: LeaderboardService) {
    private val _updates = MutableSharedFlow<LeaderboardDto>(replay = 1, extraBufferCapacity = 16)
    val updates: SharedFlow<LeaderboardDto> = _updates.asSharedFlow()

    suspend fun snapshot(): LeaderboardDto = service.top(LIMIT)

    /** Recompute and push to all connected clients. Called after a successful check-in. */
    suspend fun broadcast() {
        _updates.emit(service.top(LIMIT))
    }

    companion object { const val LIMIT = 50 }
}
