package com.example.questly.core.data

import com.example.questly.core.database.CheckInDao
import com.example.questly.core.database.CheckInEntity
import com.example.questly.core.database.CheckpointDao
import com.example.questly.core.database.CheckpointEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalCheckInRepositoryTest {

    private val park = CheckpointEntity("park-1", "Park", "", 51.5, -0.12, 100.0, 50, "CHALLENGE")

    private class FakeCheckpointDao(private val items: List<CheckpointEntity>) : CheckpointDao {
        override suspend fun upsertAll(items: List<CheckpointEntity>) {}
        override fun observeAll(): Flow<List<CheckpointEntity>> = MutableStateFlow(items)
        override suspend fun getById(id: String) = items.firstOrNull { it.id == id }
    }

    private class FakeCheckInDao : CheckInDao {
        val rows = MutableStateFlow<List<CheckInEntity>>(emptyList())
        override suspend fun insert(item: CheckInEntity) { rows.value = rows.value + item }
        override fun observeAll(): Flow<List<CheckInEntity>> = rows
        override suspend fun lastForCheckpoint(checkpointId: String) =
            rows.value.filter { it.checkpointId == checkpointId }.maxByOrNull { it.timestampMillis }
    }

    private fun repo(checkInDao: FakeCheckInDao = FakeCheckInDao()) =
        LocalCheckInRepository(FakeCheckpointDao(listOf(park)), checkInDao)

    @Test fun checkInInsideRadiusSucceeds() = runTest {
        val result = repo().recordCheckIn("park-1", 51.5, -0.12, nowMillis = 10_000L)
        assertEquals(CheckInResult.Success, result)
    }

    @Test fun checkInOutsideRadiusIsTooFar() = runTest {
        val result = repo().recordCheckIn("park-1", 52.5, -0.12, nowMillis = 10_000L)
        assertEquals(CheckInResult.TooFar, result)
    }

    @Test fun secondCheckInWithinCooldownIsRejected() = runTest {
        val dao = FakeCheckInDao()
        val r = repo(dao)
        r.recordCheckIn("park-1", 51.5, -0.12, nowMillis = 10_000L)
        val second = r.recordCheckIn("park-1", 51.5, -0.12, nowMillis = 10_000L + 1)
        assertEquals(CheckInResult.OnCooldown, second)
    }

    @Test fun pointsSumOnlyCountSuccessfulCheckIns() = runTest {
        val dao = FakeCheckInDao()
        val r = repo(dao)
        r.recordCheckIn("park-1", 51.5, -0.12, nowMillis = 10_000L)
        val points = r.observePoints().first()
        assertEquals(50, points)
    }

    @Test fun unknownCheckpointReported() = runTest {
        val result = repo().recordCheckIn("nope", 51.5, -0.12, nowMillis = 10_000L)
        assertEquals(CheckInResult.UnknownCheckpoint, result)
    }
}
