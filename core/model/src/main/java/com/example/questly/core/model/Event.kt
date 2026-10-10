package com.example.questly.core.model

/** Event categories mirror the checkpoint categories, so an event rides the same points table. */
enum class EventCategory { PARK, BEACH, VIEWPOINT, LANDMARK }

enum class EventVisibility { PUBLIC, FRIENDS, PRIVATE }

enum class EventRegistration { NONE, FREE, PAID }

enum class EventStatus { DRAFT, PUBLISHED, CANCELLED }

/** The viewer's own registration state for an event (Milestone F). */
enum class RegistrationStatus { REGISTERED, WAITLISTED, CANCELLED, ATTENDED }

/**
 * A user-created event, fed from the backend (`/events`). [distanceMeters] is only populated in
 * area/discovery listings — it's null for a single event or the host's own list. [isHost] marks
 * events the signed-in user owns (so the UI can show manage controls).
 */
data class Event(
    val id: String,
    val hostId: String,
    val hostDisplayName: String,
    val isHost: Boolean,
    val title: String,
    val description: String,
    val category: EventCategory,
    val venueName: String,
    val lat: Double,
    val lng: Double,
    val startsAtMillis: Long,
    val endsAtMillis: Long?,
    val capacity: Int?,
    val visibility: EventVisibility,
    val registration: EventRegistration,
    val priceCents: Int?,
    val currency: String?,
    val status: EventStatus,
    val distanceMeters: Double? = null,
    // Registration (Milestone F).
    val registeredCount: Int = 0,
    val spotsLeft: Int? = null,
    val viewerStatus: RegistrationStatus? = null,
) {
    /** The viewer holds an active (non-cancelled) registration. */
    val isGoing: Boolean
        get() = viewerStatus == RegistrationStatus.REGISTERED ||
            viewerStatus == RegistrationStatus.WAITLISTED ||
            viewerStatus == RegistrationStatus.ATTENDED
}

/** One person on an event's roster. */
data class RosterMember(
    val userId: String,
    val displayName: String,
    val status: RegistrationStatus,
)

/** A host's attendee roster, split by status. */
data class EventRoster(
    val registered: List<RosterMember> = emptyList(),
    val waitlisted: List<RosterMember> = emptyList(),
    val attended: List<RosterMember> = emptyList(),
)

/**
 * The host-supplied fields for creating or updating an event — the domain-side counterpart of the
 * backend's EventWriteRequest. Timestamps are epoch millis; the repository formats them as ISO-8601.
 */
data class EventInput(
    val title: String,
    val description: String,
    val category: EventCategory,
    val venueName: String,
    val lat: Double,
    val lng: Double,
    val startsAtMillis: Long,
    val endsAtMillis: Long?,
    val capacity: Int?,
    val visibility: EventVisibility,
    val registration: EventRegistration,
    val priceCents: Int?,
    val currency: String?,
    val status: EventStatus,
)
