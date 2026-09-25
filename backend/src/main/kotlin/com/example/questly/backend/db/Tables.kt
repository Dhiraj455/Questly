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
    val createdAt = timestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
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
