package com.example.questly.feature.discover

const val MIN_RADIUS_M = 1_000.0
const val MAX_RADIUS_M = 20_000.0
const val DEFAULT_RADIUS_M = 5_000.0

// Events beyond the radius but still "around you" are shown up to this distance.
const val NEARBY_MAX_M = 50_000.0
const val PAGE_SIZE = 6

enum class DiscoverSection { RECOMMENDED, WITHIN_RADIUS, NEARBY }

/** A horizontal row of events, already sliced to the section's visible count. */
data class EventSection(
    val events: List<Event> = emptyList(),
    val hasMore: Boolean = false,
)

data class DiscoverUiState(
    val radiusMeters: Double = DEFAULT_RADIUS_M,
    val recommended: EventSection = EventSection(),
    val withinRadius: EventSection = EventSection(),
    val nearby: EventSection = EventSection(),
)

/**
 * Buckets [all] events into the three Discover sections for [radiusMeters], each sliced to the
 * visible count in [visibleCounts] (defaulting to [PAGE_SIZE]).
 */
fun buildDiscoverState(
    all: List<Event>,
    radiusMeters: Double,
    visibleCounts: Map<DiscoverSection, Int>,
): DiscoverUiState {
    fun section(events: List<Event>, id: DiscoverSection): EventSection {
        val sorted = events.sortedBy { it.distanceMeters }
        val visible = visibleCounts[id] ?: PAGE_SIZE
        return EventSection(events = sorted.take(visible), hasMore = sorted.size > visible)
    }
    return DiscoverUiState(
        radiusMeters = radiusMeters,
        recommended = section(all.filter { it.recommended }, DiscoverSection.RECOMMENDED),
        withinRadius = section(all.filter { it.distanceMeters <= radiusMeters }, DiscoverSection.WITHIN_RADIUS),
        nearby = section(
            all.filter { it.distanceMeters > radiusMeters && it.distanceMeters <= NEARBY_MAX_M },
            DiscoverSection.NEARBY,
        ),
    )
}
