package com.example.questly.feature.map

import app.cash.turbine.test
import com.example.questly.core.data.CHECK_IN_COOLDOWN_MILLIS
import com.example.questly.core.data.CheckInRepository
import com.example.questly.core.data.CheckInResult
import com.example.questly.core.data.CheckpointRepository
import com.example.questly.core.data.RefreshResult
import com.example.questly.core.location.LocationProvider
import com.example.questly.core.location.UserLocation
import com.example.questly.core.model.CheckIn
import com.example.questly.core.model.Checkpoint
import com.example.questly.core.model.CheckpointKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(StandardTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private val park = Checkpoint("hyde-park", "Hyde Park", "", 51.5073, -0.1657, 150.0, 50, CheckpointKind.CHALLENGE)

    private class FakeCheckpointRepo(
        cps: List<Checkpoint> = emptyList(),
        private val result: RefreshResult = RefreshResult.Success(0),
    ) : CheckpointRepository {
        val flow = MutableStateFlow(cps)
        val refreshCalls = mutableListOf<Triple<Double, Double, Double>>()
        override fun observeCheckpoints(): Flow<List<Checkpoint>> = flow
        override suspend fun refresh(lat: Double, lng: Double, radiusMeters: Double): RefreshResult {
            refreshCalls.add(Triple(lat, lng, radiusMeters))
            return result
        }
    }
    private class FakeCheckInRepo(checkIns: List<CheckIn> = emptyList()) : CheckInRepository {
        private val flow = MutableStateFlow(checkIns)
        override fun observeCheckIns(): Flow<List<CheckIn>> = flow
        override fun observePoints(): Flow<Int> = MutableStateFlow(0)
        override suspend fun recordCheckIn(checkpointId: String, userLat: Double, userLng: Double, nowMillis: Long) =
            CheckInResult.Success
    }
    private class FakeLocation(loc: UserLocation?) : LocationProvider {
        val flow = MutableStateFlow(loc)
        override fun observeLocation(): Flow<UserLocation?> = flow
    }

    // --- display behavior (existing) ---

    @Test fun withinRangeTrueWhenUserAtCheckpoint() = runTest {
        val vm = MapViewModel(FakeCheckpointRepo(listOf(park)), FakeCheckInRepo(), FakeLocation(UserLocation(51.5073, -0.1657)))
        vm.state.test {
            var s = awaitItem()
            while (s.userLocation == null || s.checkpoints.isEmpty()) s = awaitItem()
            assertTrue(s.checkpoints.single().withinRange)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun checkedInTrueForRecentCheckInAndFalseForStaleOne() = runTest {
        val now = System.currentTimeMillis()
        val recent = CheckIn("c1", "hyde-park", now, "Hyde Park", 50)
        val stale = CheckIn("c2", "hyde-park", now - CHECK_IN_COOLDOWN_MILLIS - 1, "Hyde Park", 50)

        MapViewModel(FakeCheckpointRepo(listOf(park)), FakeCheckInRepo(listOf(recent)), FakeLocation(UserLocation(51.5073, -0.1657)))
            .state.test {
                var s = awaitItem()
                while (s.checkpoints.isEmpty()) s = awaitItem()
                assertTrue(s.checkpoints.single().checkedIn)
                cancelAndIgnoreRemainingEvents()
            }

        MapViewModel(FakeCheckpointRepo(listOf(park)), FakeCheckInRepo(listOf(stale)), FakeLocation(UserLocation(51.5073, -0.1657)))
            .state.test {
                var s = awaitItem()
                while (s.checkpoints.isEmpty()) s = awaitItem()
                assertEquals(false, s.checkpoints.single().checkedIn)
                cancelAndIgnoreRemainingEvents()
            }
    }

    @Test fun checkpointsAreSortedByDistanceWithDistancePopulated() = runTest {
        val far = Checkpoint("far", "Far", "", 55.0, -3.0, 150.0, 10, CheckpointKind.CHALLENGE)
        val vm = MapViewModel(
            FakeCheckpointRepo(listOf(far, park)),
            FakeCheckInRepo(),
            FakeLocation(UserLocation(51.5073, -0.1657)),
        )
        vm.state.test {
            var s = awaitItem()
            while (s.userLocation == null || s.checkpoints.size < 2) s = awaitItem()
            assertEquals("hyde-park", s.checkpoints.first().checkpoint.id)
            assertEquals(true, s.checkpoints.all { it.distanceMeters != null })
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- re-query behavior (new) ---

    @Test fun queriesOnFirstLocationFixAtDefaultRadius() = runTest {
        val repo = FakeCheckpointRepo()
        MapViewModel(repo, FakeCheckInRepo(), FakeLocation(UserLocation(51.5, -0.16)))
        advanceUntilIdle()
        assertEquals(1, repo.refreshCalls.size)
        assertEquals(DEFAULT_RADIUS_M, repo.refreshCalls.single().third, 0.0)
    }

    @Test fun smallMoveDoesNotRequery() = runTest {
        val repo = FakeCheckpointRepo()
        val loc = FakeLocation(UserLocation(51.5, -0.16))
        MapViewModel(repo, FakeCheckInRepo(), loc)
        advanceUntilIdle()
        loc.flow.value = UserLocation(51.5005, -0.16) // ~55 m, well under 30% of 5 km
        advanceUntilIdle()
        assertEquals(1, repo.refreshCalls.size)
    }

    @Test fun largeMoveRequeries() = runTest {
        val repo = FakeCheckpointRepo()
        val loc = FakeLocation(UserLocation(51.5, -0.16))
        MapViewModel(repo, FakeCheckInRepo(), loc)
        advanceUntilIdle()
        loc.flow.value = UserLocation(51.53, -0.16) // ~3.3 km, over 30% of 5 km
        advanceUntilIdle()
        assertEquals(2, repo.refreshCalls.size)
    }

    @Test fun changingRadiusRequeriesWhileStationary() = runTest {
        val repo = FakeCheckpointRepo()
        val vm = MapViewModel(repo, FakeCheckInRepo(), FakeLocation(UserLocation(51.5, -0.16)))
        advanceUntilIdle()
        vm.setRadius(MAX_RADIUS_M)
        advanceUntilIdle()
        assertEquals(2, repo.refreshCalls.size)
        assertEquals(MAX_RADIUS_M, repo.refreshCalls.last().third, 0.0)
    }

    @Test fun manualRefreshRequeriesWhileStationary() = runTest {
        val repo = FakeCheckpointRepo()
        val vm = MapViewModel(repo, FakeCheckInRepo(), FakeLocation(UserLocation(51.5, -0.16)))
        advanceUntilIdle()
        vm.refresh()
        advanceUntilIdle()
        assertEquals(2, repo.refreshCalls.size)
    }

    @Test fun refreshErrorIsSurfacedInState() = runTest {
        val repo = FakeCheckpointRepo(result = RefreshResult.Error("no network"))
        val vm = MapViewModel(repo, FakeCheckInRepo(), FakeLocation(UserLocation(51.5, -0.16)))
        vm.state.test {
            var s = awaitItem()
            while (s.error == null) s = awaitItem()
            assertEquals("no network", s.error)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
