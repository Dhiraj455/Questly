package com.example.questly.backend.events

import kotlinx.serialization.Serializable

/** Allowed enum values, enforced in the service (DTOs stay plain strings like the rest of the API). */
object EventEnums {
    val VISIBILITY = setOf("PUBLIC", "FRIENDS", "PRIVATE")
    val REGISTRATION = setOf("NONE", "FREE", "PAID")
    // A new/updated event may be DRAFT or PUBLISHED; CANCELLED only happens via POST /events/{id}/cancel.
    val WRITABLE_STATUS = setOf("DRAFT", "PUBLISHED")
}

/** Registration lifecycle. A free RSVP is REGISTERED, or WAITLISTED when the event is full. */
object RegistrationStatus {
    const val REGISTERED = "REGISTERED"
    const val WAITLISTED = "WAITLISTED"
    const val CANCELLED = "CANCELLED"
    const val ATTENDED = "ATTENDED"
}

/**
 * Create or replace an event. Used for both POST (create) and PUT (full update) — the host always
 * sends the complete, intended state. Timestamps are ISO-8601 strings; the service validates them.
 */
@Serializable
data class EventWriteRequest(
    val title: String,
    val description: String = "",
    val category: String,
    val venueName: String = "",
    val lat: Double,
    val lng: Double,
    val startsAt: String,
    val endsAt: String? = null,
    val capacity: Int? = null,
    val visibility: String = "PUBLIC",
    val registrationType: String = "NONE",
    val priceCents: Int? = null,
    val currency: String? = null,
    val status: String = "PUBLISHED",
)

/**
 * An event as returned to clients. [hostDisplayName] saves the app a lookup; [distanceMeters] is
 * only populated in area/discovery listings (null for a single event or the host's own list).
 */
@Serializable
data class EventDto(
    val id: String,
    val hostId: String,
    val hostDisplayName: String,
    val isHost: Boolean,
    val title: String,
    val description: String,
    val category: String,
    val venueName: String,
    val lat: Double,
    val lng: Double,
    val startsAt: String,
    val endsAt: String? = null,
    val capacity: Int? = null,
    val visibility: String,
    val registrationType: String,
    val priceCents: Int? = null,
    val currency: String? = null,
    val status: String,
    val createdAt: String,
    val distanceMeters: Double? = null,
    // Registration (Milestone F): how many are REGISTERED, spots left (null = unlimited), and the
    // caller's own registration status (null = not registered).
    val registeredCount: Int = 0,
    val spotsLeft: Int? = null,
    val viewerStatus: String? = null,
)

@Serializable
data class EventsPage(val events: List<EventDto>)

/** The caller's registration result for an event (REGISTERED or WAITLISTED). */
@Serializable
data class RegistrationDto(val eventId: String, val status: String)

/** One attendee in a host's roster. */
@Serializable
data class RosterEntryDto(
    val userId: String,
    val displayName: String,
    val status: String,
    val registeredAt: String,
)

@Serializable
data class RosterDto(
    val registered: List<RosterEntryDto>,
    val waitlisted: List<RosterEntryDto>,
    val attended: List<RosterEntryDto>,
)

/** Expected event-flow rejections (e.g. editing someone else's event) -> mapped to a 409. */
class EventError(val reason: String, val detail: String) : RuntimeException(detail)
