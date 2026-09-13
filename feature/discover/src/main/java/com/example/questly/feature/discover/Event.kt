package com.example.questly.feature.discover

enum class EventCategory { MUSIC, FOOD, SPORTS, OUTDOORS, ARTS, COMMUNITY }

/** A discoverable event. Dummy data for now; a real feed replaces the source later. */
data class Event(
    val id: String,
    val title: String,
    val category: EventCategory,
    val distanceMeters: Double,
    val recommended: Boolean = false,
)
