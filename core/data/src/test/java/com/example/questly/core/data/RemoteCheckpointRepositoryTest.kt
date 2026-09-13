package com.example.questly.core.data

import com.example.questly.core.database.CheckpointDao
import com.example.questly.core.database.CheckpointEntity
import com.example.questly.core.network.OverpassClient
import com.example.questly.core.network.OverpassPoi
import com.example.questly.core.network.PoiKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class RemoteCheckpointRepositoryTest {

    private class FakeCheckpointDao(initial: List<CheckpointEntity> = emptyList()) : CheckpointDao {
        val stored = MutableStateFlow(initial)
        var deleteCalls = 0
        override suspend fun upsertAll(items: List<CheckpointEntity>) { stored.value = stored.value + items }
        override fun observeAll(): Flow<List<CheckpointEntity>> = stored
        override suspend fun getById(id: String) = stored.value.firstOrNull { it.id == id }
        override suspend fun deleteAll() { deleteCalls++; stored.value = emptyList() }
    }

    private class FakeOverpassClient(
        private val result: List<OverpassPoi> = emptyList(),
        private val fail: Boolean = false,
    ) : OverpassClient {
        override suspend fun query(lat: Double, lng: Double, radiusMeters: Double): List<OverpassPoi> {
            if (fail) throw IOException("boom")
            return result
        }
    }

    private val userLat = 41.90
    private val userLng = -87.60

    @Test fun refreshMapsPointsByTypeAndReplacesCache() = runTest {
        val pois = listOf(
            OverpassPoi("node/1", 41.90, -87.60, "Green Park", PoiKind.PARK),
            OverpassPoi("node/2", 41.91, -87.61, "North Beach", PoiKind.BEACH),
        )
        val dao = FakeCheckpointDao()
        val repo = RemoteCheckpointRepository(dao, FakeOverpassClient(pois))

        val result = repo.refresh(userLat, userLng, 5000.0)

        assertEquals(RefreshResult.Success(2), result)
        val park = dao.stored.value.first { it.title == "Green Park" }
        assertEquals(50, park.points)
        assertEquals(150.0, park.radiusMeters, 0.0)
        assertEquals("CHALLENGE", park.kind)
        assertEquals("PARK", park.category) // POI type preserved for icons + filtering
        assertEquals(75, dao.stored.value.first { it.title == "North Beach" }.points)
        assertEquals(1, dao.deleteCalls) // cache replaced, not appended
    }

    @Test fun refreshKeepsAllPoisInRadiusSortedByDistance() = runTest {
        // 60 parks within radius must ALL survive (sorted), so widening the radius genuinely
        // grows the result set instead of collapsing to the nearest N.
        val pois = (1..60).map { i ->
            OverpassPoi("node/$i", userLat + i * 0.0001, userLng, "Park $i", PoiKind.PARK) // all within radius
        }.shuffled()
        val dao = FakeCheckpointDao()
        val repo = RemoteCheckpointRepository(dao, FakeOverpassClient(pois))

        val result = repo.refresh(userLat, userLng, 20000.0)

        assertEquals(RefreshResult.Success(60), result)
        assertEquals(60, dao.stored.value.size)
        assertEquals("Park 1", dao.stored.value.first().title) // nearest first
        assertEquals("Park 60", dao.stored.value.last().title) // farthest last, still present
    }

    @Test fun refreshDropsPoisWhoseCenterIsOutsideRadius() = runTest {
        // Overpass 'around' matches huge features (e.g. Route 66) by geometry, but their center
        // can be far away — those must not appear as nearby quests.
        val near = OverpassPoi("node/1", userLat + 0.005, userLng, "Near Park", PoiKind.PARK) // ~555 m
        val far = OverpassPoi("node/2", userLat + 2.0, userLng, "Route 66", PoiKind.LANDMARK) // ~222 km
        val dao = FakeCheckpointDao()
        val repo = RemoteCheckpointRepository(dao, FakeOverpassClient(listOf(near, far)))

        val result = repo.refresh(userLat, userLng, 1000.0)

        assertEquals(RefreshResult.Success(1), result)
        assertEquals(listOf("Near Park"), dao.stored.value.map { it.title })
    }

    @Test fun refreshCapsAtSafetyMaxWhenOverpassFloodsResults() = runTest {
        val pois = (1..200).map { i ->
            OverpassPoi("node/$i", userLat + i * 0.001, userLng, "Park $i", PoiKind.PARK)
        }
        val dao = FakeCheckpointDao()
        val repo = RemoteCheckpointRepository(dao, FakeOverpassClient(pois))

        val result = repo.refresh(userLat, userLng, 20000.0)

        assertEquals(RefreshResult.Success(MAX_QUESTS), result)
        assertEquals(MAX_QUESTS, dao.stored.value.size)
    }

    @Test fun refreshFailureKeepsCacheAndReturnsError() = runTest {
        val existing = CheckpointEntity("keep-1", "Cached", "", 41.9, -87.6, 150.0, 50, "CHALLENGE")
        val dao = FakeCheckpointDao(listOf(existing))
        val repo = RemoteCheckpointRepository(dao, FakeOverpassClient(fail = true))

        val result = repo.refresh(userLat, userLng, 5000.0)

        assertTrue(result is RefreshResult.Error)
        assertEquals(listOf(existing), dao.stored.value) // untouched
        assertEquals(0, dao.deleteCalls) // never wiped good data
    }
}
