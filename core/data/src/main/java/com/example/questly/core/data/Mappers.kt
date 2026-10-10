package com.example.questly.core.data

import com.example.questly.core.database.CheckInEntity
import com.example.questly.core.database.CheckpointEntity
import com.example.questly.core.model.CheckIn
import com.example.questly.core.model.Checkpoint
import com.example.questly.core.model.CheckpointKind
import com.example.questly.core.model.Conversation
import com.example.questly.core.model.ConversationType
import com.example.questly.core.model.Message
import com.example.questly.core.model.Event
import com.example.questly.core.model.EventCategory
import com.example.questly.core.model.EventInput
import com.example.questly.core.model.EventRegistration
import com.example.questly.core.model.EventRoster
import com.example.questly.core.model.EventStatus
import com.example.questly.core.model.EventVisibility
import com.example.questly.core.model.RegistrationStatus
import com.example.questly.core.model.RosterMember
import com.example.questly.core.network.ChatConversationDto
import com.example.questly.core.network.ChatMessageDto
import com.example.questly.core.network.EventDto
import com.example.questly.core.network.EventWriteRequestDto
import com.example.questly.core.network.RosterDto
import com.example.questly.core.network.RosterEntryDto
import com.example.questly.core.network.OverpassPoi
import com.example.questly.core.network.PoiKind
import java.time.Instant
import java.time.OffsetDateTime

fun CheckpointEntity.toModel() =
    Checkpoint(id, title, description, lat, lng, radiusMeters, points, CheckpointKind.valueOf(kind), category)

fun CheckInEntity.toModel() = CheckIn(id, checkpointId, timestampMillis, title, points)

// Fixed check-in radius for every quest sourced from a POI point.
private const val CHECK_IN_RADIUS_M = 150.0

fun pointsFor(kind: PoiKind): Int = when (kind) {
    PoiKind.PARK -> 50
    PoiKind.BEACH -> 75
    PoiKind.VIEWPOINT -> 60
    PoiKind.LANDMARK -> 60
}

private fun descriptionFor(kind: PoiKind): String = when (kind) {
    PoiKind.PARK -> "Explore this park to earn points."
    PoiKind.BEACH -> "Visit this beach to earn points."
    PoiKind.VIEWPOINT -> "Take in this viewpoint to earn points."
    PoiKind.LANDMARK -> "Visit this landmark to earn points."
}

fun EventDto.toModel(): Event = Event(
    id = id,
    hostId = hostId,
    hostDisplayName = hostDisplayName,
    isHost = isHost,
    title = title,
    description = description,
    category = enumOrDefault(category, EventCategory.LANDMARK),
    venueName = venueName,
    lat = lat,
    lng = lng,
    startsAtMillis = parseMillis(startsAt),
    endsAtMillis = endsAt?.let(::parseMillis),
    capacity = capacity,
    visibility = enumOrDefault(visibility, EventVisibility.PUBLIC),
    registration = enumOrDefault(registrationType, EventRegistration.NONE),
    priceCents = priceCents,
    currency = currency,
    status = enumOrDefault(status, EventStatus.PUBLISHED),
    distanceMeters = distanceMeters,
    registeredCount = registeredCount,
    spotsLeft = spotsLeft,
    viewerStatus = viewerStatus?.let { enumOrNull<RegistrationStatus>(it) },
)

fun RosterDto.toModel(): EventRoster = EventRoster(
    registered = registered.map { it.toMember() },
    waitlisted = waitlisted.map { it.toMember() },
    attended = attended.map { it.toMember() },
)

private fun RosterEntryDto.toMember() = RosterMember(
    userId = userId,
    displayName = displayName,
    status = enumOrNull<RegistrationStatus>(status) ?: RegistrationStatus.REGISTERED,
)

fun EventInput.toRequest(): EventWriteRequestDto = EventWriteRequestDto(
    title = title.trim(),
    description = description.trim(),
    category = category.name,
    venueName = venueName.trim(),
    lat = lat,
    lng = lng,
    startsAt = Instant.ofEpochMilli(startsAtMillis).toString(),
    endsAt = endsAtMillis?.let { Instant.ofEpochMilli(it).toString() },
    capacity = capacity,
    visibility = visibility.name,
    registrationType = registration.name,
    priceCents = priceCents,
    currency = currency,
    status = status.name,
)

fun ChatMessageDto.toModel() = Message(
    id = id,
    conversationId = conversationId,
    senderId = senderId,
    senderDisplayName = senderDisplayName,
    body = body,
    createdAtMillis = parseMillis(createdAt),
)

fun ChatConversationDto.toModel() = Conversation(
    id = id,
    type = enumOrDefault(type, ConversationType.DIRECT),
    title = title,
    eventId = eventId,
    otherUserId = otherUserId,
    lastMessage = lastMessage?.toModel(),
    unreadCount = unreadCount,
    muted = muted,
)

private inline fun <reified T : Enum<T>> enumOrDefault(name: String, default: T): T =
    runCatching { enumValueOf<T>(name.trim().uppercase()) }.getOrDefault(default)

private inline fun <reified T : Enum<T>> enumOrNull(name: String): T? =
    runCatching { enumValueOf<T>(name.trim().uppercase()) }.getOrNull()

/** Parses an ISO-8601 timestamp (offset or 'Z') to epoch millis, tolerating a missing seconds field. */
private fun parseMillis(iso: String): Long = runCatching { OffsetDateTime.parse(iso).toInstant().toEpochMilli() }
    .recoverCatching { Instant.parse(iso).toEpochMilli() }
    .getOrDefault(0L)

fun OverpassPoi.toCheckpointEntity() = CheckpointEntity(
    id = id,
    title = name,
    description = descriptionFor(kind),
    lat = lat,
    lng = lng,
    radiusMeters = CHECK_IN_RADIUS_M,
    points = pointsFor(kind),
    kind = CheckpointKind.CHALLENGE.name,
    category = kind.name, // PARK / BEACH / VIEWPOINT / LANDMARK
)
