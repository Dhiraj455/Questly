package com.example.questly.feature.discover

import com.example.questly.core.model.Event
import com.example.questly.core.model.EventCategory
import com.example.questly.core.model.EventRegistration
import com.example.questly.core.model.EventStatus
import com.example.questly.core.model.EventVisibility
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoverStateTest {

    private fun event(id: String, distanceM: Double, startsAt: Long = 0L) = Event(
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
        startsAtMillis = startsAt,
        endsAtMillis = null,
        capacity = null,
        visibility = EventVisibility.PUBLIC,
        registration = EventRegistration.NONE,
        priceCents = null,
        currency = null,
        status = EventStatus.PUBLISHED,
        distanceMeters = distanceM,
    )

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
        assertTrue(s.soon.events.none { it.id == "tooFar" })
    }

    @Test fun eventsAreSortedByAscendingDistance() {
        val all = listOf(event("c", 3_000.0), event("a", 500.0), event("b", 1_500.0))
        val s = buildDiscoverState(all, radiusMeters = 5_000.0, visibleCounts = emptyMap())
        assertEquals(listOf("a", "b", "c"), s.withinRadius.events.map { it.id })
    }

    @Test fun soonIsSortedByStartTimeAndRadiusIndependent() {
        val all = listOf(
            event("late", 800.0, startsAt = 3_000),
            event("early", 30_000.0, startsAt = 1_000),
            event("mid", 1_000.0, startsAt = 2_000),
        )
        val tight = buildDiscoverState(all, radiusMeters = 1_000.0, visibleCounts = emptyMap())
        val wide = buildDiscoverState(all, radiusMeters = 20_000.0, visibleCounts = emptyMap())

        assertEquals(listOf("early", "mid", "late"), tight.soon.events.map { it.id })
        assertEquals(tight.soon.events, wide.soon.events)
    }

    @Test fun filtersByQueryOnTitle() {
        val all = listOf(
            event("a", 500.0).copy(title = "Beach Cleanup"),
            event("b", 600.0).copy(title = "Park Yoga"),
        )
        val s = buildDiscoverState(all, radiusMeters = 5_000.0, visibleCounts = emptyMap(), query = "beach")
        assertEquals(listOf("a"), s.withinRadius.events.map { it.id })
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
