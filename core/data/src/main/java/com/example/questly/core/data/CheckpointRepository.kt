package com.example.questly.core.data

import com.example.questly.core.model.Checkpoint
import kotlinx.coroutines.flow.Flow

sealed interface RefreshResult {
    data class Success(val count: Int) : RefreshResult
    data class Error(val message: String) : RefreshResult
}

interface CheckpointRepository {
    fun observeCheckpoints(): Flow<List<Checkpoint>>

    /**
     * Fetches quests near the point within [radiusMeters] and replaces the cache on success.
     * On failure the existing cache is left intact.
     */
    suspend fun refresh(lat: Double, lng: Double, radiusMeters: Double): RefreshResult
}
