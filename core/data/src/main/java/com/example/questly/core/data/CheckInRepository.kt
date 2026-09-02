package com.example.questly.core.data

import com.example.questly.core.model.CheckIn
import kotlinx.coroutines.flow.Flow

interface CheckInRepository {
    fun observeCheckIns(): Flow<List<CheckIn>>
    fun observePoints(): Flow<Int>
    suspend fun recordCheckIn(
        checkpointId: String,
        userLat: Double,
        userLng: Double,
        nowMillis: Long,
    ): CheckInResult
}
