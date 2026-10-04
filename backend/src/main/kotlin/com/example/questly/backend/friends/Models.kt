package com.example.questly.backend.friends

import kotlinx.serialization.Serializable

@Serializable
data class FriendCodeDto(val code: String)

@Serializable
data class AddFriendRequest(val code: String)

/** A confirmed friend, with their point total so the list reads as a mini-leaderboard. */
@Serializable
data class FriendDto(
    val userId: String,
    val displayName: String,
    val totalPoints: Int,
)

@Serializable
data class FriendRequestDto(
    val id: String,
    val requesterId: String,
    val requesterDisplayName: String,
    val createdAt: String,
)

@Serializable
data class FriendsPage(
    val friends: List<FriendDto>,
    val incomingRequests: List<FriendRequestDto>,
)

/** One entry in the activity feed: a check-in by the user or one of their friends. */
@Serializable
data class FeedItemDto(
    val id: String,
    val userId: String,
    val displayName: String,
    val title: String,
    val points: Int,
    val timestamp: String,
)

@Serializable
data class FeedPage(val items: List<FeedItemDto>, val nextCursor: String? = null)

/** Thrown for expected friend-flow rejections -> mapped to a 409 with a stable reason. */
class FriendError(val reason: String, val detail: String) : RuntimeException(detail)
