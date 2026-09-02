package com.example.questly.core.data

import com.example.questly.core.model.Checkpoint
import kotlinx.coroutines.flow.Flow

interface CheckpointRepository {
    fun observeCheckpoints(): Flow<List<Checkpoint>>
    suspend fun ensureSeeded()
}
