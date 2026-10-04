package com.example.questly.backend.friends

import com.example.questly.backend.db.CheckIns
import com.example.questly.backend.db.FriendRequests
import com.example.questly.backend.db.Friendships
import com.example.questly.backend.db.Users
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.sum
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update
import java.security.SecureRandom
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * The social graph: friend codes, requests, friendships, and the activity feed. A friendship is one
 * row with the smaller UUID first, so (a,b) and (b,a) are the same undirected edge.
 */
class FriendsService {

    suspend fun myCode(userId: UUID): FriendCodeDto = newSuspendedTransaction(Dispatchers.IO) {
        FriendCodeDto(ensureCode(userId))
    }

    /** The user's friend ids (for notifying friends of their activity). */
    suspend fun friendIdsOf(userId: UUID): List<UUID> = newSuspendedTransaction(Dispatchers.IO) {
        friendIds(userId)
    }

    /** A user's display name, or null if unknown. */
    suspend fun displayNameOf(userId: UUID): String? = newSuspendedTransaction(Dispatchers.IO) {
        Users.select(Users.displayName).where { Users.id eq userId }.firstOrNull()?.get(Users.displayName)
    }

    suspend fun list(userId: UUID): FriendsPage = newSuspendedTransaction(Dispatchers.IO) {
        val ids = friendIds(userId)
        val names = displayNames(ids)
        val points = pointsByUser(ids)
        val friends = ids
            .map { FriendDto(it.toString(), names[it] ?: "Explorer", points[it] ?: 0) }
            .sortedByDescending { it.totalPoints }

        val incoming = FriendRequests
            .join(Users, JoinType.INNER, FriendRequests.requesterId, Users.id)
            .select(FriendRequests.id, FriendRequests.requesterId, Users.displayName, FriendRequests.createdAt)
            .where { FriendRequests.addresseeId eq userId }
            .orderBy(FriendRequests.createdAt to SortOrder.DESC)
            .map {
                FriendRequestDto(
                    id = it[FriendRequests.id].toString(),
                    requesterId = it[FriendRequests.requesterId].toString(),
                    requesterDisplayName = it[Users.displayName],
                    createdAt = it[FriendRequests.createdAt].toString(),
                )
            }
        FriendsPage(friends, incoming)
    }

    suspend fun sendRequest(userId: UUID, rawCode: String): Unit = newSuspendedTransaction(Dispatchers.IO) {
        val code = rawCode.trim().uppercase()
        val target = Users.select(Users.id).where { Users.friendCode eq code }
            .firstOrNull()?.get(Users.id)
            ?: throw FriendError("NOT_FOUND", "No one has that friend code")
        if (target == userId) throw FriendError("SELF", "That's your own code")
        if (areFriends(userId, target)) throw FriendError("ALREADY_FRIENDS", "You're already friends")

        // If they already requested us, accept instead of creating a mirror-image request.
        val reverse = FriendRequests.selectAll()
            .where { (FriendRequests.requesterId eq target) and (FriendRequests.addresseeId eq userId) }
            .firstOrNull()
        if (reverse != null) {
            createFriendship(userId, target)
            FriendRequests.deleteWhere { FriendRequests.id eq reverse[FriendRequests.id] }
            return@newSuspendedTransaction
        }
        val alreadySent = FriendRequests.selectAll()
            .where { (FriendRequests.requesterId eq userId) and (FriendRequests.addresseeId eq target) }
            .any()
        if (alreadySent) throw FriendError("ALREADY_REQUESTED", "You've already sent them a request")

        FriendRequests.insert {
            it[id] = UUID.randomUUID()
            it[requesterId] = userId
            it[addresseeId] = target
            it[createdAt] = OffsetDateTime.now(ZoneOffset.UTC)
        }
    }

    suspend fun accept(userId: UUID, requestId: UUID): Unit = newSuspendedTransaction(Dispatchers.IO) {
        val req = FriendRequests.selectAll()
            .where { (FriendRequests.id eq requestId) and (FriendRequests.addresseeId eq userId) }
            .firstOrNull() ?: throw FriendError("NOT_FOUND", "That request no longer exists")
        createFriendship(userId, req[FriendRequests.requesterId])
        FriendRequests.deleteWhere { FriendRequests.id eq requestId }
    }

    suspend fun decline(userId: UUID, requestId: UUID): Unit = newSuspendedTransaction(Dispatchers.IO) {
        FriendRequests.deleteWhere {
            (FriendRequests.id eq requestId) and (FriendRequests.addresseeId eq userId)
        }
    }

    suspend fun unfriend(userId: UUID, otherId: UUID): Unit = newSuspendedTransaction(Dispatchers.IO) {
        val (low, high) = order(userId, otherId)
        Friendships.deleteWhere { (Friendships.userLow eq low) and (Friendships.userHigh eq high) }
    }

    suspend fun feed(userId: UUID, limit: Int, cursor: String?): FeedPage = newSuspendedTransaction(Dispatchers.IO) {
        val ids = friendIds(userId) + userId
        val offset = cursor?.toLongOrNull()?.takeIf { it >= 0 } ?: 0L
        val rows = CheckIns
            .join(Users, JoinType.INNER, CheckIns.userId, Users.id)
            .select(CheckIns.id, CheckIns.userId, Users.displayName, CheckIns.title, CheckIns.points, CheckIns.createdAt)
            .where { CheckIns.userId inList ids }
            .orderBy(CheckIns.createdAt to SortOrder.DESC, CheckIns.id to SortOrder.DESC)
            .limit(limit + 1, offset)
            .toList()
        val items = rows.take(limit).map {
            FeedItemDto(
                id = it[CheckIns.id].toString(),
                userId = it[CheckIns.userId].toString(),
                displayName = it[Users.displayName],
                title = it[CheckIns.title],
                points = it[CheckIns.points],
                timestamp = it[CheckIns.createdAt].toString(),
            )
        }
        FeedPage(items, if (rows.size > limit) (offset + limit).toString() else null)
    }

    private fun friendIds(userId: UUID): List<UUID> {
        val asLow = Friendships.select(Friendships.userHigh).where { Friendships.userLow eq userId }
            .map { it[Friendships.userHigh] }
        val asHigh = Friendships.select(Friendships.userLow).where { Friendships.userHigh eq userId }
            .map { it[Friendships.userLow] }
        return asLow + asHigh
    }

    private fun areFriends(a: UUID, b: UUID): Boolean {
        val (low, high) = order(a, b)
        return Friendships.selectAll()
            .where { (Friendships.userLow eq low) and (Friendships.userHigh eq high) }
            .any()
    }

    private fun createFriendship(a: UUID, b: UUID) {
        val (low, high) = order(a, b)
        val exists = Friendships.selectAll()
            .where { (Friendships.userLow eq low) and (Friendships.userHigh eq high) }
            .any()
        if (!exists) {
            Friendships.insert {
                it[userLow] = low
                it[userHigh] = high
                it[createdAt] = OffsetDateTime.now(ZoneOffset.UTC)
            }
        }
    }

    // Order a pair the same way Postgres orders the `uuid` type: UNSIGNED, big-endian. Java's
    // UUID.compareTo is SIGNED on each 64-bit half, so for UUIDs with the high bit set it disagrees
    // with Postgres — which would hand the `check (user_low < user_high)` constraint a row it rejects,
    // 500ing the accept. Matching Postgres keeps every friendship insertable and lookups consistent.
    private fun order(a: UUID, b: UUID): Pair<UUID, UUID> = if (unsignedCompare(a, b) < 0) a to b else b to a

    private fun unsignedCompare(a: UUID, b: UUID): Int {
        val high = java.lang.Long.compareUnsigned(a.mostSignificantBits, b.mostSignificantBits)
        return if (high != 0) high else java.lang.Long.compareUnsigned(a.leastSignificantBits, b.leastSignificantBits)
    }

    private fun displayNames(ids: List<UUID>): Map<UUID, String> {
        if (ids.isEmpty()) return emptyMap()
        return Users.select(Users.id, Users.displayName).where { Users.id inList ids }
            .associate { it[Users.id] to it[Users.displayName] }
    }

    private fun pointsByUser(ids: List<UUID>): Map<UUID, Int> {
        if (ids.isEmpty()) return emptyMap()
        val total = CheckIns.points.sum()
        return CheckIns.select(CheckIns.userId, total).where { CheckIns.userId inList ids }
            .groupBy(CheckIns.userId)
            .associate { it[CheckIns.userId] to (it[total] ?: 0) }
    }

    /** Returns the user's friend code, allocating a unique one on first use. */
    private fun ensureCode(userId: UUID): String {
        Users.select(Users.friendCode).where { Users.id eq userId }
            .firstOrNull()?.get(Users.friendCode)?.let { return it }
        repeat(10) {
            val code = generateCode()
            val taken = Users.select(Users.id).where { Users.friendCode eq code }.any()
            if (!taken) {
                Users.update({ Users.id eq userId }) { it[friendCode] = code }
                return code
            }
        }
        error("Could not allocate a unique friend code")
    }

    private fun generateCode(): String {
        val sb = StringBuilder("QSTLY-")
        repeat(6) { sb.append(ALPHABET[random.nextInt(ALPHABET.length)]) }
        return sb.toString()
    }

    private companion object {
        // Crockford-ish: no I/O/0/1 so codes are easy to read and share aloud.
        const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val random = SecureRandom()
    }
}
