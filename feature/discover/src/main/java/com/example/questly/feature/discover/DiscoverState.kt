package com.example.questly.feature.discover

import com.example.questly.core.model.Event

const val MIN_RADIUS_M = 1_000.0
const val MAX_RADIUS_M = 20_000.0
const val DEFAULT_RADIUS_M = 5_000.0

// Events beyond the radius but still "around you" are shown up to this distance. The repository
// fetches this wide so the radius slider can re-bucket locally without another network call.
const val NEARBY_MAX_M = 50_000.0
const val FETCH_RADIUS_KM = NEARBY_MAX_M / 1000.0
const val PAGE_SIZE = 6

enum class DiscoverSection { SOON, WITHIN_RADIUS, NEARBY }

/** A horizontal row of events, already sliced to the section's visible count. */
data class EventSection(
    val events: List<Event> = emptyList(),
    val hasMore: Boolean = false,
)

data class DiscoverUiState(
    val radiusMeters: Double = DEFAULT_RADIUS_M,
    val query: String = "",
    val soon: EventSection = EventSection(),
    val withinRadius: EventSection = EventSection(),
    val nearby: EventSection = EventSection(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val userLat: Double? = null,
    val userLng: Double? = null,
)

private fun Event.distanceOrMax(): Double = distanceMeters ?: Double.MAX_VALUE

/**
 * Buckets [all] events into the three Discover sections for [radiusMeters], each sliced to the
 * visible count in [visibleCounts] (defaulting to [PAGE_SIZE]). "Soon" is a time-sorted cross-cut
 * of everything nearby; the other two bucket by distance. A non-blank [query] keeps only events
 * whose title or venue contains it (case-insensitive).
 */
fun buildDiscoverState(
    all: List<Event>,
    radiusMeters: Double,
    visibleCounts: Map<DiscoverSection, Int>,
    query: String = "",
): DiscoverUiState {
    val matched = query.trim().takeIf { it.isNotEmpty() }?.let { q ->
        all.filter { it.title.contains(q, ignoreCase = true) || it.venueName.contains(q, ignoreCase = true) }
    } ?: all

    fun section(events: List<Event>, id: DiscoverSection): EventSection {
        val visible = visibleCounts[id] ?: PAGE_SIZE
        return EventSection(events = events.take(visible), hasMore = events.size > visible)
    }

    val withinRadius = matched.filter { it.distanceOrMax() <= radiusMeters }.sortedBy { it.distanceOrMax() }
    val nearby = matched
        .filter { it.distanceOrMax() > radiusMeters && it.distanceOrMax() <= NEARBY_MAX_M }
        .sortedBy { it.distanceOrMax() }
    val soon = matched.filter { it.distanceOrMax() <= NEARBY_MAX_M }.sortedBy { it.startsAtMillis }

    return DiscoverUiState(
        radiusMeters = radiusMeters,
        query = query,
        soon = section(soon, DiscoverSection.SOON),
        withinRadius = section(withinRadius, DiscoverSection.WITHIN_RADIUS),
        nearby = section(nearby, DiscoverSection.NEARBY),
    )
}
