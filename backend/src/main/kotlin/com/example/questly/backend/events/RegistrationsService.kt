package com.example.questly.backend.events

import com.example.questly.backend.auth.ApiException
import com.example.questly.backend.db.EventRegistrations
import com.example.questly.backend.db.Events
import com.example.questly.backend.db.Users
import com.example.questly.backend.push.PushSender
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * Registration & participation for FREE events (Milestone F). Enforces capacity (overflow goes to the
 * WAITLIST), auto-promotes the oldest waitlisted entry when a registered spot frees, exposes the host's
 * roster, and marks ATTENDED when the user checks in at the event. Paid registration is Milestone G.
 * Push notifications are best-effort (no-op when FCM is disabled) and reuse the existing [PushSender].
 */
class RegistrationsService(
    private val push: PushSender,
    private val friendIdsOf: suspend (UUID) -> List<UUID>,
    private val displayNameOf: suspend (UUID) -> String?,
    // Invoked after a brand-new registration (eventId, userId) so the event's group chat can add them.
    private val onRegistered: suspend (UUID, UUID) -> Unit = { _, _ -> },
) {
    private fun now() = OffsetDateTime.now(ZoneOffset.UTC)

    private data class Registered(val status: String, val hostId: UUID, val title: String, val isNew: Boolean)

    suspend fun register(userId: UUID, eventId: UUID): RegistrationDto {
        val result = newSuspendedTransaction(Dispatchers.IO) {
            val ev = Events.selectAll().where { Events.id eq eventId }.firstOrNull()
                ?: throw ApiException(HttpStatusCode.NotFound, "not_found", "No such event")
            if (ev[Events.status] != "PUBLISHED") throw EventError("NOT_OPEN", "This event isn't open for registration")
            if (ev[Events.hostId] == userId) throw EventError("HOST", "You're hosting this event")
            when (ev[Events.registrationType]) {
                "NONE" -> throw EventError("NO_REGISTRATION", "This event doesn't need registration")
                "PAID" -> throw EventError("PAYMENT_REQUIRED", "Paid registration is coming soon")
            }

            val existing = registrationRow(eventId, userId)
            val active = existing?.get(EventRegistrations.status)
            if (active == RegistrationStatus.REGISTERED || active == RegistrationStatus.WAITLISTED || active == RegistrationStatus.ATTENDED) {
                // Idempotent: already in. Don't re-notify.
                return@newSuspendedTransaction Registered(active, ev[Events.hostId], ev[Events.title], isNew = false)
            }

            val capacity = ev[Events.capacity]
            val registeredCount = EventRegistrations.selectAll()
                .where { (EventRegistrations.eventId eq eventId) and (EventRegistrations.status eq RegistrationStatus.REGISTERED) }
                .count()
            val newStatus =
                if (capacity != null && registeredCount >= capacity) RegistrationStatus.WAITLISTED
                else RegistrationStatus.REGISTERED

            if (existing == null) {
                EventRegistrations.insert {
                    it[id] = UUID.randomUUID()
                    it[EventRegistrations.eventId] = eventId
                    it[EventRegistrations.userId] = userId
                    it[status] = newStatus
                    it[registeredAt] = now()
                    it[updatedAt] = now()
                }
            } else {
                // Re-activating a previously cancelled registration.
                EventRegistrations.update({ (EventRegistrations.eventId eq eventId) and (EventRegistrations.userId eq userId) }) {
                    it[status] = newStatus
                    it[registeredAt] = now()
                    it[updatedAt] = now()
                }
            }
            Registered(newStatus, ev[Events.hostId], ev[Events.title], isNew = true)
        }

        if (result.isNew) {
            onRegistered(eventId, userId) // join the event's group chat
            if (push.enabled) {
                val name = displayNameOf(userId) ?: "Someone"
                push.sendToUsers(listOf(result.hostId), "New registration", "$name registered for ${result.title}")
                val friends = friendIdsOf(userId)
                if (friends.isNotEmpty()) {
                    push.sendToUsers(friends, "A friend is going", "$name is going to ${result.title}")
                }
            }
        }
        return RegistrationDto(eventId.toString(), result.status)
    }

    private data class Promotion(val userId: UUID, val title: String)

    suspend fun unregister(userId: UUID, eventId: UUID) {
        val promotion = newSuspendedTransaction(Dispatchers.IO) {
            val existing = registrationRow(eventId, userId)
                ?: return@newSuspendedTransaction null
            if (existing[EventRegistrations.status] == RegistrationStatus.CANCELLED) return@newSuspendedTransaction null
            val wasRegistered = existing[EventRegistrations.status] == RegistrationStatus.REGISTERED
            EventRegistrations.update({ (EventRegistrations.eventId eq eventId) and (EventRegistrations.userId eq userId) }) {
                it[status] = RegistrationStatus.CANCELLED
                it[updatedAt] = now()
            }
            if (!wasRegistered) return@newSuspendedTransaction null

            // A registered spot freed — promote the oldest waitlisted entry into it.
            val next = EventRegistrations.selectAll()
                .where { (EventRegistrations.eventId eq eventId) and (EventRegistrations.status eq RegistrationStatus.WAITLISTED) }
                .orderBy(EventRegistrations.registeredAt to SortOrder.ASC)
                .firstOrNull() ?: return@newSuspendedTransaction null
            EventRegistrations.update({ EventRegistrations.id eq next[EventRegistrations.id] }) {
                it[status] = RegistrationStatus.REGISTERED
                it[updatedAt] = now()
            }
            val title = Events.select(Events.title).where { Events.id eq eventId }
                .firstOrNull()?.get(Events.title) ?: "an event"
            Promotion(next[EventRegistrations.userId], title)
        }
        if (promotion != null && push.enabled) {
            push.sendToUsers(listOf(promotion.userId), "You're in!", "A spot opened up — you're registered for ${promotion.title}")
        }
    }

    /** The host's roster, split by status (cancelled entries omitted). */
    suspend fun roster(hostId: UUID, eventId: UUID): RosterDto = newSuspendedTransaction(Dispatchers.IO) {
        val ev = Events.select(Events.hostId).where { Events.id eq eventId }.firstOrNull()
            ?: throw ApiException(HttpStatusCode.NotFound, "not_found", "No such event")
        if (ev[Events.hostId] != hostId) throw EventError("NOT_HOST", "Only the host can see the roster")
        val rows = EventRegistrations
            .join(Users, JoinType.INNER, EventRegistrations.userId, Users.id)
            .select(EventRegistrations.userId, Users.displayName, EventRegistrations.status, EventRegistrations.registeredAt)
            .where { EventRegistrations.eventId eq eventId }
            .orderBy(EventRegistrations.registeredAt to SortOrder.ASC)
            .map {
                RosterEntryDto(
                    userId = it[EventRegistrations.userId].toString(),
                    displayName = it[Users.displayName],
                    status = it[EventRegistrations.status],
                    registeredAt = it[EventRegistrations.registeredAt].toString(),
                )
            }
        RosterDto(
            registered = rows.filter { it.status == RegistrationStatus.REGISTERED },
            waitlisted = rows.filter { it.status == RegistrationStatus.WAITLISTED },
            attended = rows.filter { it.status == RegistrationStatus.ATTENDED },
        )
    }

    /**
     * Marks the user ATTENDED if they're registered for the event they just checked in at. [checkpointId]
     * is the check-in's checkpoint id; when it's an event id, this flips REGISTERED/WAITLISTED → ATTENDED.
     * A no-op for ordinary (non-event) checkpoints.
     */
    suspend fun markAttended(userId: UUID, checkpointId: String) {
        val eventId = runCatching { UUID.fromString(checkpointId) }.getOrNull() ?: return
        newSuspendedTransaction(Dispatchers.IO) {
            EventRegistrations.update({
                (EventRegistrations.eventId eq eventId) and (EventRegistrations.userId eq userId) and
                    (EventRegistrations.status inList listOf(RegistrationStatus.REGISTERED, RegistrationStatus.WAITLISTED))
            }) {
                it[status] = RegistrationStatus.ATTENDED
                it[updatedAt] = now()
            }
        }
    }

    /** Notifies everyone registered/waitlisted that an event was cancelled (called from EventsService.cancel). */
    suspend fun notifyCancelled(eventId: UUID, title: String) {
        if (!push.enabled) return
        val ids = newSuspendedTransaction(Dispatchers.IO) {
            EventRegistrations.select(EventRegistrations.userId)
                .where {
                    (EventRegistrations.eventId eq eventId) and
                        (EventRegistrations.status inList listOf(RegistrationStatus.REGISTERED, RegistrationStatus.WAITLISTED))
                }
                .map { it[EventRegistrations.userId] }
        }
        if (ids.isNotEmpty()) push.sendToUsers(ids, "Event cancelled", "\"$title\" was cancelled by the host")
    }

    private fun registrationRow(eventId: UUID, userId: UUID) = EventRegistrations.selectAll()
        .where { (EventRegistrations.eventId eq eventId) and (EventRegistrations.userId eq userId) }
        .firstOrNull()
}
