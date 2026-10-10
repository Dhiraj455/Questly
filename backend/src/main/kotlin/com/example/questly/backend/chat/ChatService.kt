package com.example.questly.backend.chat

import com.example.questly.backend.auth.ApiException
import com.example.questly.backend.db.ConversationMembers
import com.example.questly.backend.db.Conversations
import com.example.questly.backend.db.EventRegistrations
import com.example.questly.backend.db.Events
import com.example.questly.backend.db.MessageReports
import com.example.questly.backend.db.Messages
import com.example.questly.backend.db.UserBlocks
import com.example.questly.backend.db.Users
import com.example.questly.backend.push.PushSender
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

private const val MAX_BODY = 2000

/**
 * Conversations and messages for 1:1 DMs (between friends) and per-event group chats. Live delivery
 * goes through [ChatHub] (WebSocket); offline/muted-aware delivery through [PushSender] (FCM). Blocks,
 * mutes and reports provide the moderation surface Play's UGC policy requires.
 */
class ChatService(
    private val hub: ChatHub,
    private val push: PushSender,
    private val areFriends: suspend (UUID, UUID) -> Boolean,
) {
    private val json = Json
    private fun now() = OffsetDateTime.now(ZoneOffset.UTC)

    // ----- conversations -----------------------------------------------------------------------

    /** Get-or-create the DM between the caller and a friend. */
    suspend fun startDirect(userId: UUID, otherId: UUID): ConversationDto {
        if (otherId == userId) throw ChatError("SELF", "You can't message yourself")
        if (!areFriends(userId, otherId)) throw ChatError("NOT_FRIENDS", "You can only DM friends")
        if (blockedBetween(userId, otherId)) throw ChatError("BLOCKED", "Messaging is unavailable with this user")
        val key = dmKey(userId, otherId)
        val id = newSuspendedTransaction(Dispatchers.IO) {
            val existing = Conversations.select(Conversations.id).where { Conversations.dmKey eq key }
                .firstOrNull()?.get(Conversations.id)
            if (existing != null) return@newSuspendedTransaction existing
            val convId = UUID.randomUUID()
            Conversations.insert {
                it[Conversations.id] = convId
                it[type] = ConversationType.DIRECT
                it[dmKey] = key
                it[createdAt] = now()
            }
            addMember(convId, userId)
            addMember(convId, otherId)
            convId
        }
        return conversation(userId, id)
    }

    /** Get-or-create the group chat for an event. Members are the host + anyone registered. */
    suspend fun eventConversation(userId: UUID, eventId: UUID): ConversationDto {
        val id = newSuspendedTransaction(Dispatchers.IO) {
            val event = Events.select(Events.id, Events.hostId).where { Events.id eq eventId }.firstOrNull()
                ?: throw ApiException(HttpStatusCode.NotFound, "not_found", "No such event")
            val hostId = event[Events.hostId]
            if (userId != hostId && !isRegistered(eventId, userId)) {
                throw ChatError("NOT_A_MEMBER", "Register for the event to join its chat")
            }
            val existing = Conversations.select(Conversations.id).where { Conversations.eventId eq eventId }
                .firstOrNull()?.get(Conversations.id)
            val convId = existing ?: UUID.randomUUID().also { convId ->
                Conversations.insert {
                    it[Conversations.id] = convId
                    it[type] = ConversationType.EVENT
                    it[Conversations.eventId] = eventId
                    it[createdAt] = now()
                }
                // Seed membership with the host and all active registrants.
                addMember(convId, hostId)
                activeRegistrantIds(eventId).forEach { addMember(convId, it) }
            }
            addMember(convId, userId) // idempotent — covers a late joiner
            convId
        }
        return conversation(userId, id)
    }

    suspend fun list(userId: UUID): ConversationsPage = newSuspendedTransaction(Dispatchers.IO) {
        val ids = ConversationMembers.select(ConversationMembers.conversationId)
            .where { ConversationMembers.userId eq userId }
            .map { it[ConversationMembers.conversationId] }
        val dtos = ids.map { toDto(userId, it) }
            .sortedByDescending { it.lastMessage?.createdAt ?: "" }
        ConversationsPage(dtos)
    }

    suspend fun history(userId: UUID, conversationId: UUID, cursor: String?, limit: Int): MessagesPage =
        newSuspendedTransaction(Dispatchers.IO) {
            requireMember(conversationId, userId)
            val hidden = blockedIds(userId)
            val offset = cursor?.toLongOrNull()?.takeIf { it >= 0 } ?: 0L
            val rows = messagesWithSender()
                .where { Messages.conversationId eq conversationId }
                .orderBy(Messages.createdAt to SortOrder.DESC, Messages.id to SortOrder.DESC)
                .limit(limit + 1, offset)
                .toList()
            val items = rows.take(limit)
                .filter { it[Messages.senderId] !in hidden } // hide messages from users you've blocked
                .map(::toMessageDto)
            MessagesPage(items, if (rows.size > limit) (offset + limit).toString() else null)
        }

    suspend fun send(userId: UUID, conversationId: UUID, rawBody: String): MessageDto {
        val body = rawBody.trim()
        if (body.isEmpty()) throw ApiException(HttpStatusCode.BadRequest, "empty_message", "Message is empty")
        if (body.length > MAX_BODY) throw ApiException(HttpStatusCode.BadRequest, "message_too_long", "Message is too long")

        data class Sent(val dto: MessageDto, val recipients: List<UUID>, val mutedRecipients: Set<UUID>, val convTitle: String)
        val sent = newSuspendedTransaction(Dispatchers.IO) {
            requireMember(conversationId, userId)
            val senderName = Users.select(Users.displayName).where { Users.id eq userId }
                .firstOrNull()?.get(Users.displayName) ?: "Someone"

            val memberRows = ConversationMembers.select(ConversationMembers.userId, ConversationMembers.muted)
                .where { ConversationMembers.conversationId eq conversationId }
                .toList()
            val otherMembers = memberRows.map { it[ConversationMembers.userId] }.filter { it != userId }
            // Don't deliver to anyone who has blocked the sender (and, for DMs, this also stops a blocked
            // sender reaching the blocker).
            val blockedBySomeone = UserBlocks.select(UserBlocks.blockerId)
                .where { (UserBlocks.blockedId eq userId) and (UserBlocks.blockerId inList otherMembers) }
                .map { it[UserBlocks.blockerId] }.toSet()
            val recipients = otherMembers.filter { it !in blockedBySomeone }
            val muted = memberRows.filter { it[ConversationMembers.muted] }.map { it[ConversationMembers.userId] }.toSet()

            val id = UUID.randomUUID()
            val ts = now()
            Messages.insert {
                it[Messages.id] = id
                it[Messages.conversationId] = conversationId
                it[senderId] = userId
                it[Messages.body] = body
                it[createdAt] = ts
            }
            // The sender has implicitly read their own message.
            ConversationMembers.update({ (ConversationMembers.conversationId eq conversationId) and (ConversationMembers.userId eq userId) }) {
                it[lastReadAt] = ts
            }
            val dto = MessageDto(id.toString(), conversationId.toString(), userId.toString(), senderName, body, ts.toString())
            Sent(dto, recipients, muted, conversationTitle(userId, conversationId))
        }

        // Live fan-out + offline push (best-effort, outside the transaction).
        hub.deliver(sent.recipients, json.encodeToString(MessageDto.serializer(), sent.dto))
        if (push.enabled) {
            val pushTargets = sent.recipients.filter { it !in sent.mutedRecipients && !hub.isOnline(it) }
            if (pushTargets.isNotEmpty()) {
                push.sendToUsers(pushTargets, sent.convTitle, "${sent.dto.senderDisplayName}: ${sent.dto.body.take(120)}")
            }
        }
        return sent.dto
    }

    suspend fun markRead(userId: UUID, conversationId: UUID): Unit = newSuspendedTransaction(Dispatchers.IO) {
        ConversationMembers.update({ (ConversationMembers.conversationId eq conversationId) and (ConversationMembers.userId eq userId) }) {
            it[lastReadAt] = now()
        }
    }

    // ----- moderation --------------------------------------------------------------------------

    suspend fun block(userId: UUID, targetId: UUID): Unit = newSuspendedTransaction(Dispatchers.IO) {
        if (targetId == userId) throw ChatError("SELF", "You can't block yourself")
        UserBlocks.insertIgnore {
            it[blockerId] = userId
            it[blockedId] = targetId
            it[createdAt] = now()
        }
    }

    suspend fun unblock(userId: UUID, targetId: UUID): Unit = newSuspendedTransaction(Dispatchers.IO) {
        UserBlocks.deleteWhere { (UserBlocks.blockerId eq userId) and (UserBlocks.blockedId eq targetId) }
    }

    suspend fun mute(userId: UUID, conversationId: UUID, muted: Boolean): Unit = newSuspendedTransaction(Dispatchers.IO) {
        requireMember(conversationId, userId)
        ConversationMembers.update({ (ConversationMembers.conversationId eq conversationId) and (ConversationMembers.userId eq userId) }) {
            it[ConversationMembers.muted] = muted
        }
    }

    suspend fun report(userId: UUID, messageId: UUID, reason: String): Unit = newSuspendedTransaction(Dispatchers.IO) {
        val exists = Messages.select(Messages.id).where { Messages.id eq messageId }.any()
        if (!exists) throw ApiException(HttpStatusCode.NotFound, "not_found", "No such message")
        MessageReports.insertIgnore {
            it[id] = UUID.randomUUID()
            it[reporterId] = userId
            it[MessageReports.messageId] = messageId
            it[MessageReports.reason] = reason.take(500)
            it[createdAt] = now()
        }
    }

    // ----- helpers -----------------------------------------------------------------------------

    private suspend fun conversation(userId: UUID, conversationId: UUID): ConversationDto =
        newSuspendedTransaction(Dispatchers.IO) { toDto(userId, conversationId) }

    private fun toDto(userId: UUID, conversationId: UUID): ConversationDto {
        val conv = Conversations.selectAll().where { Conversations.id eq conversationId }.first()
        val type = conv[Conversations.type]
        val eventId = conv[Conversations.eventId]
        val otherUserId = if (type == ConversationType.DIRECT) otherMemberId(conversationId, userId) else null
        val title = conversationTitle(userId, conversationId)
        val last = messagesWithSender()
            .where { Messages.conversationId eq conversationId }
            .orderBy(Messages.createdAt to SortOrder.DESC, Messages.id to SortOrder.DESC)
            .limit(1).firstOrNull()?.let(::toMessageDto)
        val memberRow = ConversationMembers.selectAll()
            .where { (ConversationMembers.conversationId eq conversationId) and (ConversationMembers.userId eq userId) }
            .first()
        val lastRead = memberRow[ConversationMembers.lastReadAt]
        val unread = Messages.select(Messages.id).where {
            var cond = (Messages.conversationId eq conversationId) and (Messages.senderId neq userId)
            if (lastRead != null) cond = cond and (Messages.createdAt greater lastRead)
            cond
        }.count().toInt()
        return ConversationDto(
            id = conversationId.toString(),
            type = type,
            title = title,
            eventId = eventId?.toString(),
            otherUserId = otherUserId?.toString(),
            lastMessage = last,
            unreadCount = unread,
            muted = memberRow[ConversationMembers.muted],
        )
    }

    private fun conversationTitle(userId: UUID, conversationId: UUID): String {
        val conv = Conversations.selectAll().where { Conversations.id eq conversationId }.first()
        return if (conv[Conversations.type] == ConversationType.EVENT) {
            val eventId = conv[Conversations.eventId] ?: return "Event chat"
            Events.select(Events.title).where { Events.id eq eventId }.firstOrNull()?.get(Events.title) ?: "Event chat"
        } else {
            val otherId = otherMemberId(conversationId, userId)
            otherId?.let { displayName(it) } ?: "Direct message"
        }
    }

    private fun otherMemberId(conversationId: UUID, userId: UUID): UUID? =
        ConversationMembers.select(ConversationMembers.userId)
            .where { (ConversationMembers.conversationId eq conversationId) and (ConversationMembers.userId neq userId) }
            .firstOrNull()?.get(ConversationMembers.userId)

    private fun displayName(userId: UUID): String =
        Users.select(Users.displayName).where { Users.id eq userId }.firstOrNull()?.get(Users.displayName) ?: "Explorer"

    private fun addMember(conversationId: UUID, userId: UUID) {
        ConversationMembers.insertIgnore {
            it[ConversationMembers.conversationId] = conversationId
            it[ConversationMembers.userId] = userId
            it[joinedAt] = now()
        }
    }

    private fun requireMember(conversationId: UUID, userId: UUID) {
        val member = ConversationMembers.selectAll()
            .where { (ConversationMembers.conversationId eq conversationId) and (ConversationMembers.userId eq userId) }
            .any()
        if (!member) throw ChatError("NOT_A_MEMBER", "You're not in this conversation")
    }

    private fun isRegistered(eventId: UUID, userId: UUID): Boolean = EventRegistrations.selectAll()
        .where {
            (EventRegistrations.eventId eq eventId) and (EventRegistrations.userId eq userId) and
                (EventRegistrations.status inList listOf("REGISTERED", "WAITLISTED", "ATTENDED"))
        }.any()

    private fun activeRegistrantIds(eventId: UUID): List<UUID> = EventRegistrations.select(EventRegistrations.userId)
        .where {
            (EventRegistrations.eventId eq eventId) and
                (EventRegistrations.status inList listOf("REGISTERED", "WAITLISTED", "ATTENDED"))
        }.map { it[EventRegistrations.userId] }

    private fun blockedIds(userId: UUID): Set<UUID> = UserBlocks.select(UserBlocks.blockedId)
        .where { UserBlocks.blockerId eq userId }.map { it[UserBlocks.blockedId] }.toSet()

    private suspend fun blockedBetween(a: UUID, b: UUID): Boolean = newSuspendedTransaction(Dispatchers.IO) {
        UserBlocks.selectAll().where {
            ((UserBlocks.blockerId eq a) and (UserBlocks.blockedId eq b)) or
                ((UserBlocks.blockerId eq b) and (UserBlocks.blockedId eq a))
        }.any()
    }

    private fun messagesWithSender() = Messages
        .join(Users, JoinType.INNER, Messages.senderId, Users.id)
        .select(Messages.id, Messages.conversationId, Messages.senderId, Users.displayName, Messages.body, Messages.createdAt)

    private fun toMessageDto(row: ResultRow) = MessageDto(
        id = row[Messages.id].toString(),
        conversationId = row[Messages.conversationId].toString(),
        senderId = row[Messages.senderId].toString(),
        senderDisplayName = row[Users.displayName],
        body = row[Messages.body],
        createdAt = row[Messages.createdAt].toString(),
    )

    private fun dmKey(a: UUID, b: UUID): String = listOf(a.toString(), b.toString()).sorted().joinToString(":")
}
