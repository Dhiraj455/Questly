package com.example.questly.backend.events

import com.example.questly.backend.auth.ApiException
import com.example.questly.backend.checkpoints.distanceMeters
import com.example.questly.backend.checkpoints.pointsForCategory
import com.example.questly.backend.db.EventRegistrations
import com.example.questly.backend.db.Events
import com.example.questly.backend.db.Users
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.count
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.math.cos

/**
 * User-created events. Any user can host; others discover events in their area and (from Milestone F)
 * register. Visibility is enforced server-side: PUBLIC to everyone, FRIENDS to the host's friends,
 * PRIVATE to the host only. [friendIdsProvider] returns a user's friend ids (injected to avoid a hard
 * dependency on FriendsService, mirroring CheckInService.onCheckIn).
 */
class EventsService(
    private val friendIdsProvider: suspend (UUID) -> List<UUID> = { emptyList() },
    // Invoked after an event is cancelled (id, title) so registrants can be notified. Default no-op.
    private val onCancelled: suspend (UUID, String) -> Unit = { _, _ -> },
) {
    private fun now() = OffsetDateTime.now(ZoneOffset.UTC)

    suspend fun create(userId: UUID, req: EventWriteRequest): EventDto {
        val v = validate(req)
        val id = UUID.randomUUID()
        val current = now()
        return newSuspendedTransaction(Dispatchers.IO) {
            Events.insert {
                it[Events.id] = id
                it[hostId] = userId
                it[title] = v.title
                it[description] = v.description
                it[category] = v.category
                it[venueName] = v.venueName
                it[lat] = v.lat
                it[lng] = v.lng
                it[startsAt] = v.startsAt
                it[endsAt] = v.endsAt
                it[capacity] = v.capacity
                it[visibility] = v.visibility
                it[registrationType] = v.registrationType
                it[priceCents] = v.priceCents
                it[currency] = v.currency
                it[status] = v.status
                it[createdAt] = current
                it[updatedAt] = current
            }
            loadOne(id, userId) ?: error("Event vanished after insert")
        }
    }

    suspend fun update(userId: UUID, eventId: UUID, req: EventWriteRequest): EventDto {
        val v = validate(req)
        return newSuspendedTransaction(Dispatchers.IO) {
            val row = Events.selectAll().where { Events.id eq eventId }.firstOrNull()
                ?: throw ApiException(HttpStatusCode.NotFound, "not_found", "No such event")
            if (row[Events.hostId] != userId) throw EventError("NOT_HOST", "Only the host can edit this event")
            if (row[Events.status] == "CANCELLED") throw EventError("CANCELLED", "A cancelled event can't be edited")
            Events.update({ Events.id eq eventId }) {
                it[title] = v.title
                it[description] = v.description
                it[category] = v.category
                it[venueName] = v.venueName
                it[lat] = v.lat
                it[lng] = v.lng
                it[startsAt] = v.startsAt
                it[endsAt] = v.endsAt
                it[capacity] = v.capacity
                it[visibility] = v.visibility
                it[registrationType] = v.registrationType
                it[priceCents] = v.priceCents
                it[currency] = v.currency
                it[status] = v.status
                it[updatedAt] = now()
            }
            loadOne(eventId, userId) ?: error("Event vanished after update")
        }
    }

    suspend fun cancel(userId: UUID, eventId: UUID) {
        val title = newSuspendedTransaction(Dispatchers.IO) {
            val row = Events.selectAll().where { Events.id eq eventId }.firstOrNull()
                ?: throw ApiException(HttpStatusCode.NotFound, "not_found", "No such event")
            if (row[Events.hostId] != userId) throw EventError("NOT_HOST", "Only the host can cancel this event")
            Events.update({ Events.id eq eventId }) {
                it[status] = "CANCELLED"
                it[updatedAt] = now()
            }
            row[Events.title]
        }
        onCancelled(eventId, title) // notify registrants (best-effort, outside the transaction)
    }

    /** A single event, with visibility enforced for the viewer. */
    suspend fun get(userId: UUID, eventId: UUID): EventDto {
        val friends = friendIdsProvider(userId).toSet()
        return newSuspendedTransaction(Dispatchers.IO) {
            val dto = loadOne(eventId, userId)
                ?: throw ApiException(HttpStatusCode.NotFound, "not_found", "No such event")
            if (!canView(dto, userId, friends)) {
                // Don't leak existence of events the viewer may not see.
                throw ApiException(HttpStatusCode.NotFound, "not_found", "No such event")
            }
            dto
        }
    }

    /**
     * Discovery: published, upcoming events within [radiusKm] of (lat,lng), newest-starting first,
     * filtered to what the viewer may see. A SQL bounding box pre-filters; exact great-circle distance
     * is applied in memory (fine at v5 scale; revisit with PostGIS if the dataset grows).
     */
    suspend fun listArea(
        userId: UUID,
        lat: Double,
        lng: Double,
        radiusKm: Double,
        category: String?,
        limit: Int,
    ): EventsPage {
        if (!lat.isFinite() || !lng.isFinite() || lat !in -90.0..90.0 || lng !in -180.0..180.0) {
            throw ApiException(HttpStatusCode.BadRequest, "invalid_location", "Latitude or longitude is invalid")
        }
        val radius = radiusKm.coerceIn(0.1, 200.0)
        val latDelta = radius / 111.0
        val lngDelta = radius / (111.0 * cos(Math.toRadians(lat)).coerceAtLeast(0.01))
        val friends = friendIdsProvider(userId).toSet()
        val current = now()

        return newSuspendedTransaction(Dispatchers.IO) {
            val rows = eventsWithHost()
                .where {
                    (Events.status eq "PUBLISHED") and
                        (Events.startsAt greaterEq current) and
                        (Events.lat greaterEq (lat - latDelta)) and
                        (Events.lat lessEq (lat + latDelta)) and
                        (Events.lng greaterEq (lng - lngDelta)) and
                        (Events.lng lessEq (lng + lngDelta)) and
                        (
                            (Events.visibility eq "PUBLIC") or
                                (Events.hostId eq userId) or
                                ((Events.visibility eq "FRIENDS") and (Events.hostId inList friends))
                            )
                }
                .orderBy(Events.startsAt to SortOrder.ASC)
                .toList()

            val filtered = rows
                .mapNotNull { row ->
                    val dto = toDto(row, userId)
                    if (category != null && !dto.category.equals(category, ignoreCase = true)) return@mapNotNull null
                    val d = distanceMeters(lat, lng, dto.lat, dto.lng)
                    if (d > radius * 1000.0) null else dto.copy(distanceMeters = d)
                }
                .sortedBy { it.distanceMeters }
                .take(limit)
            EventsPage(enrich(filtered, userId))
        }
    }

    /** Events the user hosts (any status), newest-starting first. */
    suspend fun mine(userId: UUID): EventsPage = newSuspendedTransaction(Dispatchers.IO) {
        val events = eventsWithHost()
            .where { Events.hostId eq userId }
            .orderBy(Events.startsAt to SortOrder.DESC)
            .map { toDto(it, userId) }
        EventsPage(enrich(events, userId))
    }

    // --- helpers -------------------------------------------------------------------------------

    /** Adds registration counts, remaining spots, and the viewer's own status to each event. */
    private fun enrich(dtos: List<EventDto>, viewerId: UUID): List<EventDto> {
        if (dtos.isEmpty()) return dtos
        val ids = dtos.map { UUID.fromString(it.id) }
        val countCol = EventRegistrations.userId.count()
        val counts = EventRegistrations
            .select(EventRegistrations.eventId, countCol)
            .where { (EventRegistrations.eventId inList ids) and (EventRegistrations.status eq RegistrationStatus.REGISTERED) }
            .groupBy(EventRegistrations.eventId)
            .associate { it[EventRegistrations.eventId] to it[countCol].toInt() }
        val viewer = EventRegistrations
            .select(EventRegistrations.eventId, EventRegistrations.status)
            .where { (EventRegistrations.eventId inList ids) and (EventRegistrations.userId eq viewerId) }
            .associate { it[EventRegistrations.eventId] to it[EventRegistrations.status] }
        return dtos.map { dto ->
            val id = UUID.fromString(dto.id)
            val c = counts[id] ?: 0
            dto.copy(
                registeredCount = c,
                spotsLeft = dto.capacity?.let { (it - c).coerceAtLeast(0) },
                viewerStatus = viewer[id],
            )
        }
    }

    private fun canView(dto: EventDto, userId: UUID, friends: Set<UUID>): Boolean = when (dto.visibility) {
        "PUBLIC" -> true
        "FRIENDS" -> dto.isHost || UUID.fromString(dto.hostId) in friends
        else -> dto.isHost // PRIVATE
    }

    private fun loadOne(eventId: UUID, viewerId: UUID): EventDto? =
        eventsWithHost().where { Events.id eq eventId }.firstOrNull()
            ?.let { enrich(listOf(toDto(it, viewerId)), viewerId).first() }

    private fun eventsWithHost() = Events
        .join(Users, JoinType.INNER, Events.hostId, Users.id)
        .select(EVENT_WITH_HOST_COLUMNS)

    private fun toDto(row: ResultRow, viewerId: UUID): EventDto {
        val hostId = row[Events.hostId]
        return EventDto(
            id = row[Events.id].toString(),
            hostId = hostId.toString(),
            hostDisplayName = row[Users.displayName],
            isHost = hostId == viewerId,
            title = row[Events.title],
            description = row[Events.description],
            category = row[Events.category],
            venueName = row[Events.venueName],
            lat = row[Events.lat],
            lng = row[Events.lng],
            startsAt = row[Events.startsAt].toString(),
            endsAt = row[Events.endsAt]?.toString(),
            capacity = row[Events.capacity],
            visibility = row[Events.visibility],
            registrationType = row[Events.registrationType],
            priceCents = row[Events.priceCents],
            currency = row[Events.currency],
            status = row[Events.status],
            createdAt = row[Events.createdAt].toString(),
        )
    }

    /** A validated, storable event derived from a client request. */
    private data class Validated(
        val title: String,
        val description: String,
        val category: String,
        val venueName: String,
        val lat: Double,
        val lng: Double,
        val startsAt: OffsetDateTime,
        val endsAt: OffsetDateTime?,
        val capacity: Int?,
        val visibility: String,
        val registrationType: String,
        val priceCents: Int?,
        val currency: String?,
        val status: String,
    )

    private fun validate(req: EventWriteRequest): Validated {
        fun bad(code: String, msg: String): Nothing = throw ApiException(HttpStatusCode.BadRequest, code, msg)

        val title = req.title.trim()
        if (title.isEmpty() || title.length > 120) bad("invalid_title", "Title must be 1–120 characters")
        val description = req.description.trim().also { if (it.length > 2000) bad("invalid_description", "Description is too long") }
        val venueName = req.venueName.trim().also { if (it.length > 120) bad("invalid_venue", "Venue name is too long") }

        val category = req.category.trim().uppercase()
        if (pointsForCategory(category) == null) bad("invalid_category", "Unknown event category")

        if (!req.lat.isFinite() || !req.lng.isFinite() || req.lat !in -90.0..90.0 || req.lng !in -180.0..180.0) {
            bad("invalid_location", "Latitude or longitude is invalid")
        }

        val startsAt = try { OffsetDateTime.parse(req.startsAt) } catch (_: Exception) { bad("invalid_timestamp", "startsAt must be ISO-8601") }
        val endsAt = req.endsAt?.let { raw ->
            try { OffsetDateTime.parse(raw) } catch (_: Exception) { bad("invalid_timestamp", "endsAt must be ISO-8601") }
        }
        if (endsAt != null && !endsAt.isAfter(startsAt)) bad("invalid_time_range", "endsAt must be after startsAt")

        req.capacity?.let { if (it <= 0) bad("invalid_capacity", "Capacity must be positive") }

        val visibility = req.visibility.trim().uppercase()
        if (visibility !in EventEnums.VISIBILITY) bad("invalid_visibility", "Unknown visibility")
        val registrationType = req.registrationType.trim().uppercase()
        if (registrationType !in EventEnums.REGISTRATION) bad("invalid_registration", "Unknown registration type")
        val status = req.status.trim().uppercase()
        if (status !in EventEnums.WRITABLE_STATUS) bad("invalid_status", "Status must be DRAFT or PUBLISHED")

        var priceCents = req.priceCents
        var currency = req.currency?.trim()?.uppercase()
        if (registrationType == "PAID") {
            if (priceCents == null || priceCents <= 0) bad("invalid_price", "A paid event needs a positive priceCents")
            if (currency.isNullOrBlank() || currency.length != 3) bad("invalid_currency", "A paid event needs a 3-letter ISO currency")
        } else {
            // Price/currency are meaningless for free/none events; drop them to satisfy the DB invariant.
            priceCents = null
            currency = null
        }

        return Validated(
            title, description, category, venueName, req.lat, req.lng,
            startsAt, endsAt, req.capacity, visibility, registrationType, priceCents, currency, status,
        )
    }

    private companion object {
        val EVENT_WITH_HOST_COLUMNS = listOf(
            Events.id, Events.hostId, Events.title, Events.description, Events.category, Events.venueName,
            Events.lat, Events.lng, Events.startsAt, Events.endsAt, Events.capacity, Events.visibility,
            Events.registrationType, Events.priceCents, Events.currency, Events.status, Events.createdAt,
            Users.displayName,
        )
    }
}
