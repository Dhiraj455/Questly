package com.example.questly.core.data

import com.example.questly.core.model.Checkpoint
import kotlinx.coroutines.flow.Flow

interface CheckpointRepository {
    fun observeCheckpoints(): Flow<List<Checkpoint>>
    suspend fun ensureSeeded()

    /** Seed demo challenges around a location so the map is never empty. */
    suspend fun ensureSeededNear(lat: Double, lng: Double)
}
