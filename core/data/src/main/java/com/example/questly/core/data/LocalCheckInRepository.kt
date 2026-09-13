package com.example.questly.core.data

import com.example.questly.core.database.CheckInDao
import com.example.questly.core.database.CheckInEntity
import com.example.questly.core.database.CheckpointDao
import com.example.questly.core.model.CheckIn
import com.example.questly.core.model.distanceMeters
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject

class LocalCheckInRepository @Inject constructor(
    private val checkpointDao: CheckpointDao,
    private val checkInDao: CheckInDao,
) : CheckInRepository {

    override fun observeCheckIns(): Flow<List<CheckIn>> =
        checkInDao.observeAll().map { rows -> rows.map { it.toModel() } }

    override fun observePoints(): Flow<Int> =
        // Sum the points snapshotted on each check-in, so a later checkpoint-cache refresh can't
        // change past earnings.
        checkInDao.observeAll().map { checkIns -> checkIns.sumOf { it.points } }

    override suspend fun recordCheckIn(
        checkpointId: String,
        userLat: Double,
        userLng: Double,
        nowMillis: Long,
    ): CheckInResult {
        val cp = checkpointDao.getById(checkpointId) ?: return CheckInResult.UnknownCheckpoint
        if (distanceMeters(userLat, userLng, cp.lat, cp.lng) > cp.radiusMeters) return CheckInResult.TooFar
        val last = checkInDao.lastForCheckpoint(checkpointId)
        if (last != null && nowMillis - last.timestampMillis < CHECK_IN_COOLDOWN_MILLIS) return CheckInResult.OnCooldown
        checkInDao.insert(
            CheckInEntity(
                id = UUID.randomUUID().toString(),
                checkpointId = checkpointId,
                timestampMillis = nowMillis,
                title = cp.title,
                points = cp.points,
            ),
        )
        return CheckInResult.Success
    }
}
