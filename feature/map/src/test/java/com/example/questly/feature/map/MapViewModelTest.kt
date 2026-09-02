package com.example.questly.feature.map

import app.cash.turbine.test
import com.example.questly.core.data.CheckInRepository
import com.example.questly.core.data.CheckInResult
import com.example.questly.core.data.CheckpointRepository
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

    private class FakeCheckpointRepo(cps: List<Checkpoint>) : CheckpointRepository {
        val flow = MutableStateFlow(cps)
        override fun observeCheckpoints(): Flow<List<Checkpoint>> = flow
        override suspend fun ensureSeeded() {}
    }
    private class FakeCheckInRepo : CheckInRepository {
        override fun observeCheckIns(): Flow<List<CheckIn>> = MutableStateFlow(emptyList())
        override fun observePoints(): Flow<Int> = MutableStateFlow(0)
        override suspend fun recordCheckIn(checkpointId: String, userLat: Double, userLng: Double, nowMillis: Long) =
            CheckInResult.Success
    }
    private class FakeLocation(loc: UserLocation?) : LocationProvider {
        val flow = MutableStateFlow(loc)
        override fun observeLocation(): Flow<UserLocation?> = flow
    }

    @Test fun withinRangeTrueWhenUserAtCheckpoint() = runTest {
        val vm = MapViewModel(FakeCheckpointRepo(listOf(park)), FakeCheckInRepo(), FakeLocation(UserLocation(51.5073, -0.1657)))
        vm.state.test {
            var s = awaitItem()
            while (s.userLocation == null || s.checkpoints.isEmpty()) s = awaitItem()
            assertTrue(s.checkpoints.single().withinRange)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun withinRangeFalseWhenUserFarAway() = runTest {
        val vm = MapViewModel(FakeCheckpointRepo(listOf(park)), FakeCheckInRepo(), FakeLocation(UserLocation(52.5, -0.16)))
        vm.state.test {
            var s = awaitItem()
            while (s.userLocation == null || s.checkpoints.isEmpty()) s = awaitItem()
            assertEquals(false, s.checkpoints.single().withinRange)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
