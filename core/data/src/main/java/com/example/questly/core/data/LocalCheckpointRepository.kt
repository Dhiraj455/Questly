package com.example.questly.core.data

import com.example.questly.core.database.CheckpointDao
import com.example.questly.core.model.Checkpoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class LocalCheckpointRepository @Inject constructor(
    private val checkpointDao: CheckpointDao,
) : CheckpointRepository {
    override fun observeCheckpoints(): Flow<List<Checkpoint>> =
        checkpointDao.observeAll().map { rows -> rows.map { it.toModel() } }

    override suspend fun ensureSeeded() {
        checkpointDao.upsertAll(SEED_CHECKPOINTS)
    }
}
