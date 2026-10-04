package com.example.questly.backend.profile

import com.example.questly.backend.db.CheckIns
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

/**
 * Derives streaks + achievements from the check-in ledger (no extra tables): a check-in day is a
 * calendar day in UTC on which the user has at least one check-in.
 */
class ProfileService {

    suspend fun profile(userId: UUID): ProfileDto = newSuspendedTransaction(Dispatchers.IO) {
        val rows = CheckIns.selectAll().where { CheckIns.userId eq userId }
            .map { it[CheckIns.createdAt] to it[CheckIns.points] }

        val totalCheckIns = rows.size
        val totalPoints = rows.sumOf { it.second }
        val days = rows
            .map { it.first.atZoneSameInstant(ZoneOffset.UTC).toLocalDate() }
            .toSortedSet()

        val stats = StatsDto(
            currentStreakDays = currentStreak(days, LocalDate.now(ZoneOffset.UTC)),
            longestStreakDays = longestStreak(days),
            totalCheckIns = totalCheckIns,
            totalPoints = totalPoints,
        )
        ProfileDto(stats, achievements(stats))
    }

    /** Longest run of consecutive calendar days present in [days]. */
    private fun longestStreak(days: Set<LocalDate>): Int {
        if (days.isEmpty()) return 0
        var longest = 1
        var run = 1
        var previous: LocalDate? = null
        for (day in days) { // sorted ascending (TreeSet)
            if (previous != null) {
                run = if (day == previous.plusDays(1)) run + 1 else 1
            }
            longest = maxOf(longest, run)
            previous = day
        }
        return longest
    }

    /**
     * Consecutive days ending today — or yesterday, so a streak isn't counted broken until a whole
     * day is missed. Returns 0 if the user hasn't checked in today or yesterday.
     */
    private fun currentStreak(days: Set<LocalDate>, today: LocalDate): Int {
        var anchor = when {
            days.contains(today) -> today
            days.contains(today.minusDays(1)) -> today.minusDays(1)
            else -> return 0
        }
        var streak = 0
        while (days.contains(anchor)) {
            streak++
            anchor = anchor.minusDays(1)
        }
        return streak
    }

    private fun achievements(stats: StatsDto): List<AchievementDto> = CATALOG.map { def ->
        val value = when (def.metric) {
            Metric.COUNT -> stats.totalCheckIns
            Metric.POINTS -> stats.totalPoints
            Metric.STREAK -> stats.longestStreakDays
        }
        AchievementDto(
            id = def.id,
            title = def.title,
            description = def.description,
            target = def.target,
            value = value.coerceAtMost(def.target),
            earned = value >= def.target,
        )
    }

    private enum class Metric { COUNT, POINTS, STREAK }

    private data class Def(val id: String, val title: String, val description: String, val metric: Metric, val target: Int)

    private companion object {
        val CATALOG = listOf(
            Def("first_steps", "First Steps", "Complete your first check-in", Metric.COUNT, 1),
            Def("explorer", "Explorer", "Check in 10 times", Metric.COUNT, 10),
            Def("adventurer", "Adventurer", "Check in 50 times", Metric.COUNT, 50),
            Def("pathfinder", "Pathfinder", "Check in 100 times", Metric.COUNT, 100),
            Def("collector", "Collector", "Earn 100 points", Metric.POINTS, 100),
            Def("high_roller", "High Roller", "Earn 500 points", Metric.POINTS, 500),
            Def("point_master", "Point Master", "Earn 2000 points", Metric.POINTS, 2000),
            Def("on_a_roll", "On a Roll", "Reach a 3-day streak", Metric.STREAK, 3),
            Def("week_warrior", "Week Warrior", "Reach a 7-day streak", Metric.STREAK, 7),
            Def("unstoppable", "Unstoppable", "Reach a 30-day streak", Metric.STREAK, 30),
        )
    }
}
