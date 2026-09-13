package com.example.questly.feature.discover

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

    // 10 events all within 1 km, so they land in the WITHIN_RADIUS section at the default radius.
    private val events = (1..10).map {
        Event("e$it", "Event $it", EventCategory.MUSIC, distanceMeters = it * 50.0)
    }

    @Test fun loadMoreRevealsTheNextPageForASection() = runTest {
        val vm = DiscoverViewModel(events)
        vm.state.test {
            assertEquals(PAGE_SIZE, awaitItem().withinRadius.events.size) // first page
            vm.loadMore(DiscoverSection.WITHIN_RADIUS)
            val next = awaitItem()
            assertEquals(10, next.withinRadius.events.size) // all revealed
            assertFalse(next.withinRadius.hasMore)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun changingRadiusResetsPagingAndRebuckets() = runTest {
        // 3 near (< 1 km) + 10 far (~10 km) so NEARBY is pageable at both radii.
        val mixed = (1..3).map { Event("n$it", "Near $it", EventCategory.FOOD, it * 100.0) } +
            (1..10).map { Event("f$it", "Far $it", EventCategory.SPORTS, 10_000.0 + it) }
        val vm = DiscoverViewModel(mixed)
        vm.state.test {
            assertTrue(awaitItem().nearby.hasMore) // first page of 10 far events
            vm.loadMore(DiscoverSection.NEARBY)
            assertFalse(awaitItem().nearby.hasMore) // all 10 revealed

            vm.setRadius(2_000.0)
            val s = awaitItem()
            assertEquals(2_000.0, s.radiusMeters, 0.0)
            assertEquals(listOf("n1", "n2", "n3"), s.withinRadius.events.map { it.id }) // rebucketed
            assertEquals(PAGE_SIZE, s.nearby.events.size) // paging reset to first page
            assertTrue(s.nearby.hasMore)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
