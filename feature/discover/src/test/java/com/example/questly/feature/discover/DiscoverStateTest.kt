package com.example.questly.feature.discover

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoverStateTest {

    private fun event(id: String, distanceM: Double, recommended: Boolean = false) =
        Event(id, "Event $id", EventCategory.MUSIC, distanceM, recommended)

    @Test fun bucketsByRadiusAndExcludesBeyondNearbyMax() {
        val all = listOf(
            event("near", 1_000.0),
            event("mid", 10_000.0),
            event("far", 40_000.0),
            event("tooFar", 60_000.0), // beyond 50 km — excluded everywhere
        )
        val s = buildDiscoverState(all, radiusMeters = 5_000.0, visibleCounts = emptyMap())

        assertEquals(listOf("near"), s.withinRadius.events.map { it.id })
        assertEquals(listOf("mid", "far"), s.nearby.events.map { it.id })
        assertTrue(s.nearby.events.none { it.id == "tooFar" })
    }

    @Test fun eventsAreSortedByAscendingDistance() {
        val all = listOf(event("c", 3_000.0), event("a", 500.0), event("b", 1_500.0))
        val s = buildDiscoverState(all, radiusMeters = 5_000.0, visibleCounts = emptyMap())
        assertEquals(listOf("a", "b", "c"), s.withinRadius.events.map { it.id })
    }

    @Test fun recommendedIsRadiusIndependent() {
        val all = listOf(
            event("r1", 800.0, recommended = true),
            event("r2", 30_000.0, recommended = true),
            event("plain", 1_000.0),
        )
        val tight = buildDiscoverState(all, radiusMeters = 1_000.0, visibleCounts = emptyMap())
        val wide = buildDiscoverState(all, radiusMeters = 20_000.0, visibleCounts = emptyMap())

        assertEquals(listOf("r1", "r2"), tight.recommended.events.map { it.id })
        assertEquals(tight.recommended.events, wide.recommended.events)
    }

    @Test fun slicesToVisibleCountAndReportsHasMore() {
        val all = (1..8).map { event("w$it", it * 100.0) } // all within 5 km
        val s = buildDiscoverState(
            all,
            radiusMeters = 5_000.0,
            visibleCounts = mapOf(DiscoverSection.WITHIN_RADIUS to PAGE_SIZE),
        )
        assertEquals(PAGE_SIZE, s.withinRadius.events.size)
        assertTrue(s.withinRadius.hasMore)
    }

    @Test fun noHasMoreWhenAllVisible() {
        val all = (1..3).map { event("w$it", it * 100.0) }
        val s = buildDiscoverState(all, radiusMeters = 5_000.0, visibleCounts = emptyMap())
        assertEquals(3, s.withinRadius.events.size)
        assertFalse(s.withinRadius.hasMore)
    }
}
