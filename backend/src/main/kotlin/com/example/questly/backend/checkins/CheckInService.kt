package com.example.questly.backend.checkins

import com.example.questly.backend.auth.ApiException
import com.example.questly.backend.checkpoints.CheckpointsService
import com.example.questly.backend.checkpoints.distanceMeters
import com.example.questly.backend.db.CheckIns
import io.ktor.http.HttpStatusCode
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.sum
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.math.ceil

private const val COOLDOWN_SECONDS = 60 * 60L
private const val MAX_CLIENT_CLOCK_SKEW_SECONDS = 5 * 60L

/**
 * Validates a check-in against the checkpoint returned by Overpass, then writes the immutable
 * server ledger. The client never sends the title or point value, so it cannot inflate rewards.
 */
class CheckInService(private val checkpoints: CheckpointsService) {
    private fun now() = OffsetDateTime.now(ZoneOffset.UTC)

    suspend fun create(userId: UUID, key: UUID, request: CheckInRequest): CheckInDto {
        if (!request.lat.isFinite() || !request.lng.isFinite() || request.lat !in -90.0..90.0 || request.lng !in -180.0..180.0) {
            throw ApiException(HttpStatusCode.BadRequest, "invalid_location", "Latitude or longitude is invalid")
        }
        val clientTime = try {
            OffsetDateTime.parse(request.clientTimestamp)
        } catch (_: Exception) {
            throw ApiException(HttpStatusCode.BadRequest, "invalid_timestamp", "Client timestamp must be ISO-8601")
        }
        val current = now()
        if (kotlin.math.abs(java.time.Duration.between(clientTime, current).seconds) > MAX_CLIENT_CLOCK_SKEW_SECONDS) {
            reject("IMPLAUSIBLE", "Device time is too far from server time")
        }

        // Return a prior result before calling Overpass. This makes network retries safe and cheap.
        existingForKey(userId, key)?.let { return it }

        val checkpoint = try {
            checkpoints.resolve(request.checkpointId)
        } catch (_: IOException) {
            throw ApiException(HttpStatusCode.BadGateway, "overpass_unavailable", "Couldn't verify the checkpoint right now")
        } ?: reject("UNKNOWN_CHECKPOINT", "Checkpoint no longer exists")
        if (distanceMeters(request.lat, request.lng, checkpoint.lat, checkpoint.lng) > checkpoint.radiusMeters) {
            reject("TOO_FAR", "You need to be closer to this checkpoint")
        }

        return newSuspendedTransaction(Dispatchers.IO) {
            // Another request may have inserted while Overpass was loading.
            existingForKeyInTransaction(userId, key)?.let { return@newSuspendedTransaction it }
            val last = CheckIns.selectAll()
                .where { (CheckIns.userId eq userId) and (CheckIns.checkpointId eq checkpoint.id) }
                .orderBy(CheckIns.createdAt to org.jetbrains.exposed.sql.SortOrder.DESC)
                .limit(1)
                .firstOrNull()
            if (last != null) {
                val seconds = java.time.Duration.between(last[CheckIns.createdAt], current).seconds
                if (seconds < COOLDOWN_SECONDS) {
                    reject("ON_COOLDOWN", "This checkpoint is cooling down", ceil((COOLDOWN_SECONDS - seconds) / 1.0).toInt())
                }
            }

            val id = UUID.randomUUID()
            CheckIns.insert {
                it[CheckIns.id] = id
                it[CheckIns.userId] = userId
                it[checkpointId] = checkpoint.id
                it[title] = checkpoint.title
                it[points] = checkpoint.points
                it[clientLat] = request.lat
                it[clientLng] = request.lng
                it[clientTimestamp] = clientTime
                it[createdAt] = current
                it[idempotencyKey] = key
            }
            CheckInDto(id.toString(), checkpoint.id, checkpoint.title, checkpoint.points, current.toString())
        }
    }

    suspend fun history(userId: UUID, limit: Int, cursor: String?): CheckInPage = newSuspendedTransaction(Dispatchers.IO) {
        val offset = cursor?.toLongOrNull()?.takeIf { it >= 0 } ?: 0L
        val rows = CheckIns.selectAll().where { CheckIns.userId eq userId }
            .orderBy(CheckIns.createdAt to org.jetbrains.exposed.sql.SortOrder.DESC, CheckIns.id to org.jetbrains.exposed.sql.SortOrder.DESC)
            .limit(limit + 1, offset).toList()
        val items = rows.take(limit).map(::toDto)
        CheckInPage(items, if (rows.size > limit) (offset + limit).toString() else null)
    }

    suspend fun points(userId: UUID): PointsDto = newSuspendedTransaction(Dispatchers.IO) {
        val total = CheckIns.points.sum()
        val value = CheckIns.select(total).where { CheckIns.userId eq userId }.firstOrNull()?.get(total) ?: 0
        PointsDto(value)
    }

    private suspend fun existingForKey(userId: UUID, key: UUID): CheckInDto? = newSuspendedTransaction(Dispatchers.IO) {
        existingForKeyInTransaction(userId, key)
    }

    private fun existingForKeyInTransaction(userId: UUID, key: UUID): CheckInDto? = CheckIns.selectAll()
        .where { (CheckIns.userId eq userId) and (CheckIns.idempotencyKey eq key) }
        .limit(1).firstOrNull()?.let(::toDto)

    private fun toDto(row: org.jetbrains.exposed.sql.ResultRow) = CheckInDto(
        row[CheckIns.id].toString(), row[CheckIns.checkpointId], row[CheckIns.title], row[CheckIns.points], row[CheckIns.createdAt].toString(),
    )

    private fun reject(reason: String, message: String, retryAfter: Int? = null): Nothing {
        throw CheckInRejected(CheckInRejection(reason, message, retryAfter))
    }
}
