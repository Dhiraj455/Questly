package com.example.questly.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CheckpointDao {
    @Upsert suspend fun upsertAll(items: List<CheckpointEntity>)

    @Query("SELECT * FROM checkpoints")
    fun observeAll(): Flow<List<CheckpointEntity>>

    @Query("SELECT * FROM checkpoints WHERE id = :id")
    suspend fun getById(id: String): CheckpointEntity?

    @Query("DELETE FROM checkpoints")
    suspend fun deleteAll()
}
