package com.example.questly.core.model

enum class ConversationType { DIRECT, EVENT }

data class Message(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val senderDisplayName: String,
    val body: String,
    val createdAtMillis: Long,
)

/**
 * A conversation in the user's inbox. [title] is the other person's name (DIRECT) or the event title
 * (EVENT); [otherUserId] is set for DIRECT so the UI can block/report that person.
 */
data class Conversation(
    val id: String,
    val type: ConversationType,
    val title: String,
    val eventId: String? = null,
    val otherUserId: String? = null,
    val lastMessage: Message? = null,
    val unreadCount: Int = 0,
    val muted: Boolean = false,
)
