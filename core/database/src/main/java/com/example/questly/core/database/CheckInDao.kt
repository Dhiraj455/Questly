package com.example.questly.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CheckInDao {
    @Insert suspend fun insert(item: CheckInEntity)

    @Query("SELECT * FROM checkins ORDER BY timestampMillis DESC")
    fun observeAll(): Flow<List<CheckInEntity>>

    @Query("SELECT * FROM checkins WHERE checkpointId = :checkpointId ORDER BY timestampMillis DESC LIMIT 1")
    suspend fun lastForCheckpoint(checkpointId: String): CheckInEntity?
}
