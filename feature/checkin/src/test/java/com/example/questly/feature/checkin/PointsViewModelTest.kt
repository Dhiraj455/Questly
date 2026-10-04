package com.example.questly.feature.checkin

import app.cash.turbine.test
import com.example.questly.core.data.CheckInRepository
import com.example.questly.core.data.CheckInResult
import com.example.questly.core.data.ProfileRepository
import com.example.questly.core.model.CheckIn
import com.example.questly.core.model.Profile
import kotlinx.coroutines.flow.StateFlow
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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PointsViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(StandardTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private class FakeCheckInRepo : CheckInRepository {
        val points = MutableStateFlow(0)
        val checkIns = MutableStateFlow<List<CheckIn>>(emptyList())
        override fun observeCheckIns(): Flow<List<CheckIn>> = checkIns
        override fun observePoints(): Flow<Int> = points
        override suspend fun recordCheckIn(checkpointId: String, userLat: Double, userLng: Double, nowMillis: Long) =
            CheckInResult.Success
    }

    private class FakeProfileRepo : ProfileRepository {
        val profile = MutableStateFlow<Profile?>(null)
        override fun observeProfile(): StateFlow<Profile?> = profile
        override suspend fun refresh() {}
    }

    @Test fun stateReflectsRepositoryPointsAndHistory() = runTest {
        val repo = FakeCheckInRepo().apply {
            points.value = 50
            checkIns.value = listOf(CheckIn("c1", "hyde-park", 1000L))
        }
        val vm = PointsViewModel(repo, FakeProfileRepo())
        vm.state.test {
            assertEquals(PointsUiState(), awaitItem()) // stateIn initial value
            assertEquals(
                PointsUiState(50, listOf(CheckIn("c1", "hyde-park", 1000L))),
                awaitItem(), // settled combined value (repo preset before subscribe)
            )
            cancelAndIgnoreRemainingEvents()
        }
    }
}
