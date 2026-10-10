package com.example.questly.backend.chat

import kotlinx.serialization.Serializable

object ConversationType {
    const val DIRECT = "DIRECT"
    const val EVENT = "EVENT"
}

@Serializable
data class MessageDto(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val senderDisplayName: String,
    val body: String,
    val createdAt: String,
)

@Serializable
data class MessagesPage(val items: List<MessageDto>, val nextCursor: String? = null)

/**
 * A conversation in the user's inbox. [title] is the other person's name (DIRECT) or the event title
 * (EVENT). [otherUserId] is set for DIRECT (so the UI can open their profile / block them).
 */
@Serializable
data class ConversationDto(
    val id: String,
    val type: String,
    val title: String,
    val eventId: String? = null,
    val otherUserId: String? = null,
    val lastMessage: MessageDto? = null,
    val unreadCount: Int = 0,
    val muted: Boolean = false,
)

@Serializable
data class ConversationsPage(val conversations: List<ConversationDto>)

@Serializable
data class StartDirectRequest(val userId: String)

@Serializable
data class SendMessageRequest(val body: String)

@Serializable
data class MuteRequest(val muted: Boolean)

@Serializable
data class ReportRequest(val reason: String = "")

/** Expected chat-flow rejections (not a member, blocked, not friends…) -> mapped to a 409. */
class ChatError(val reason: String, val detail: String) : RuntimeException(detail)
