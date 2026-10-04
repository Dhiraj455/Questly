package com.example.questly.core.model

data class Friend(
    val userId: String,
    val displayName: String,
    val totalPoints: Int,
)

data class FriendRequest(
    val id: String,
    val requesterId: String,
    val requesterDisplayName: String,
)

data class LeaderboardEntry(
    val userId: String,
    val displayName: String,
    val totalPoints: Int,
    val rank: Int,
)

/** One entry in the activity feed — a check-in by the user or a friend. */
data class FeedItem(
    val id: String,
    val userId: String,
    val displayName: String,
    val title: String,
    val points: Int,
    val timestampMillis: Long,
)
