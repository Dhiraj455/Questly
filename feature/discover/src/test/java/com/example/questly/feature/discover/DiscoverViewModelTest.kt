package com.example.questly.feature.discover

import app.cash.turbine.test
import com.example.questly.core.data.EventResult
import com.example.questly.core.data.EventsRepository
import com.example.questly.core.data.RegistrationOutcome
import com.example.questly.core.model.EventRoster
import com.example.questly.core.location.LocationProvider
import com.example.questly.core.location.UserLocation
import com.example.questly.core.model.Event
import com.example.questly.core.model.EventCategory
import com.example.questly.core.model.EventInput
import com.example.questly.core.model.EventRegistration
import com.example.questly.core.model.EventStatus
import com.example.questly.core.model.EventVisibility
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DiscoverViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(StandardTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun event(id: String, distanceM: Double) = Event(
        id = id,
        hostId = "h",
        hostDisplayName = "Host",
        isHost = false,
        title = "Event $id",
        description = "",
        category = EventCategory.PARK,
        venueName = "",
        lat = 0.0,
        lng = 0.0,
        startsAtMillis = 0L,
        endsAtMillis = null,
        capacity = null,
        visibility = EventVisibility.PUBLIC,
        registration = EventRegistration.NONE,
        priceCents = null,
        currency = null,
        status = EventStatus.PUBLISHED,
        distanceMeters = distanceM,
    )

    /** Serves [preset] as the "nearby" events once a location fix triggers a refresh. */
    private class FakeEventsRepository(private val preset: List<Event>) : EventsRepository {
        private val nearby = MutableStateFlow<List<Event>>(emptyList())
        private val mine = MutableStateFlow<List<Event>>(emptyList())
        override fun observeNearby(): StateFlow<List<Event>> = nearby.asStateFlow()
        override fun observeMine(): StateFlow<List<Event>> = mine.asStateFlow()
        override suspend fun refreshNearby(lat: Double, lng: Double, radiusKm: Double): String? {
            nearby.value = preset; return null
        }
        override suspend fun refreshMine(): String? = null
        override suspend fun event(id: String): Event? = preset.find { it.id == id }
        override suspend fun create(input: EventInput): EventResult = EventResult.Error("unused")
        override suspend fun update(id: String, input: EventInput): EventResult = EventResult.Error("unused")
        override suspend fun cancel(id: String): String? = null
        override suspend fun register(eventId: String): RegistrationOutcome = RegistrationOutcome.Error("unused")
        override suspend fun unregister(eventId: String): RegistrationOutcome = RegistrationOutcome.Error("unused")
        override suspend fun roster(eventId: String): EventRoster? = null
    }

    private class FakeLocationProvider : LocationProvider {
        override fun observeLocation(): Flow<UserLocation?> = flowOf(UserLocation(0.0, 0.0))
    }

    private fun viewModel(events: List<Event>) =
        DiscoverViewModel(FakeEventsRepository(events), FakeLocationProvider())

    @Test fun loadMoreRevealsTheNextPageForASection() = runTest {
        val vm = viewModel((1..10).map { event("e$it", it * 50.0) })
        vm.state.test {
            var s = awaitItem()
            while (s.withinRadius.events.size < PAGE_SIZE) s = awaitItem()
            assertEquals(PAGE_SIZE, s.withinRadius.events.size)
            assertTrue(s.withinRadius.hasMore)

            vm.loadMore(DiscoverSection.WITHIN_RADIUS)
            while (s.withinRadius.events.size < 10) s = awaitItem()
            assertFalse(s.withinRadius.hasMore)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun changingRadiusRebucketsAndResetsPaging() = runTest {
        // 3 near (< 1 km) + 10 far (~10 km): NEARBY is pageable at the default radius.
        val mixed = (1..3).map { event("n$it", it * 100.0) } +
            (1..10).map { event("f$it", 10_000.0 + it) }
        val vm = viewModel(mixed)
        vm.state.test {
            var s = awaitItem()
            while (s.nearby.events.isEmpty()) s = awaitItem()
            assertTrue(s.nearby.hasMore) // first page of 10 far events

            vm.setRadius(2_000.0)
            while (s.radiusMeters != 2_000.0) s = awaitItem()
            assertEquals(listOf("n1", "n2", "n3"), s.withinRadius.events.map { it.id }) // rebucketed
            assertEquals(PAGE_SIZE, s.nearby.events.size) // paging reset to first page
            assertTrue(s.nearby.hasMore)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
