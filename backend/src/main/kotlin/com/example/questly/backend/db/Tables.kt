package com.example.questly.backend.db

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone

// Exposed mappings for the V1 baseline (see db/migration/V1__baseline.sql). The DB owns the FK
// constraints; these objects are just the query surface.

object Users : Table("users") {
    val id = uuid("id")
    val email = text("email")
    val displayName = text("display_name")
    val passwordHash = text("password_hash").nullable()
    val emailVerified = bool("email_verified")
    val friendCode = text("friend_code").nullable()
    val createdAt = timestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}

object FriendRequests : Table("friend_requests") {
    val id = uuid("id")
    val requesterId = uuid("requester_id")
    val addresseeId = uuid("addressee_id")
    val createdAt = timestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}

object Friendships : Table("friendships") {
    val userLow = uuid("user_low")
    val userHigh = uuid("user_high")
    val createdAt = timestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(userLow, userHigh)
}

object DeviceTokens : Table("device_tokens") {
    val token = text("token")
    val userId = uuid("user_id")
    val createdAt = timestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(token)
}

object EmailTokens : Table("email_tokens") {
    val token = text("token")
    val userId = uuid("user_id")
    val purpose = text("purpose") // VERIFY | RESET
    val expiresAt = timestampWithTimeZone("expires_at")
    val usedAt = timestampWithTimeZone("used_at").nullable()
    override val primaryKey = PrimaryKey(token)
}

object RefreshTokens : Table("refresh_tokens") {
    val token = text("token")
    val userId = uuid("user_id")
    val expiresAt = timestampWithTimeZone("expires_at")
    val revokedAt = timestampWithTimeZone("revoked_at").nullable()
    override val primaryKey = PrimaryKey(token)
}

object CheckIns : Table("checkins") {
    val id = uuid("id")
    val userId = uuid("user_id")
    val checkpointId = text("checkpoint_id")
    val title = text("title")
    val points = integer("points")
    val clientLat = double("client_lat")
    val clientLng = double("client_lng")
    val clientTimestamp = timestampWithTimeZone("client_timestamp")
    val createdAt = timestampWithTimeZone("created_at")
    val idempotencyKey = uuid("idempotency_key")
    override val primaryKey = PrimaryKey(id)
}

object Events : Table("events") {
    val id = uuid("id")
    val hostId = uuid("host_id")
    val title = text("title")
    val description = text("description")
    val category = text("category")
    val venueName = text("venue_name")
    val lat = double("lat")
    val lng = double("lng")
    val startsAt = timestampWithTimeZone("starts_at")
    val endsAt = timestampWithTimeZone("ends_at").nullable()
    val capacity = integer("capacity").nullable()
    val visibility = text("visibility")
    val registrationType = text("registration_type")
    val priceCents = integer("price_cents").nullable()
    val currency = text("currency").nullable()
    val status = text("status")
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")
    override val primaryKey = PrimaryKey(id)
}

object EventRegistrations : Table("event_registrations") {
    val id = uuid("id")
    val eventId = uuid("event_id")
    val userId = uuid("user_id")
    val status = text("status")
    val registeredAt = timestampWithTimeZone("registered_at")
    val updatedAt = timestampWithTimeZone("updated_at")
    override val primaryKey = PrimaryKey(id)
}

object Conversations : Table("conversations") {
    val id = uuid("id")
    val type = text("type")
    val eventId = uuid("event_id").nullable()
    val dmKey = text("dm_key").nullable()
    val createdAt = timestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}

object ConversationMembers : Table("conversation_members") {
    val conversationId = uuid("conversation_id")
    val userId = uuid("user_id")
    val joinedAt = timestampWithTimeZone("joined_at")
    val lastReadAt = timestampWithTimeZone("last_read_at").nullable()
    val muted = bool("muted")
    override val primaryKey = PrimaryKey(conversationId, userId)
}

object Messages : Table("messages") {
    val id = uuid("id")
    val conversationId = uuid("conversation_id")
    val senderId = uuid("sender_id")
    val body = text("body")
    val createdAt = timestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}

object UserBlocks : Table("user_blocks") {
    val blockerId = uuid("blocker_id")
    val blockedId = uuid("blocked_id")
    val createdAt = timestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(blockerId, blockedId)
}

object MessageReports : Table("message_reports") {
    val id = uuid("id")
    val reporterId = uuid("reporter_id")
    val messageId = uuid("message_id")
    val reason = text("reason")
    val createdAt = timestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}
