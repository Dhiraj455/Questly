package com.example.questly.core.data

import android.util.Log
import com.example.questly.core.model.Conversation
import com.example.questly.core.model.Message
import com.example.questly.core.network.ApiFailure
import com.example.questly.core.network.ChatActionException
import com.example.questly.core.network.QuestlyApi
import com.example.questly.core.network.TokenStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** A page of message history (newest-first) plus the cursor for older messages. */
data class MessagePage(val messages: List<Message>, val nextCursor: String?)

sealed interface ConversationResult {
    data class Success(val conversation: Conversation) : ConversationResult
    data class Error(val message: String) : ConversationResult
}

sealed interface SendResult {
    data class Success(val message: Message) : SendResult
    data class Error(val message: String) : SendResult
}

interface ChatRepository {
    fun observeConversations(): StateFlow<List<Conversation>>

    /** The signed-in user's id (cached), for aligning "my" messages. Null if unavailable. */
    suspend fun currentUserId(): String?

    /** Live inbound messages (across all the user's conversations), reconnecting as needed. */
    fun incomingMessages(): SharedFlow<Message>

    suspend fun refreshConversations()
    suspend fun startDirect(userId: String): ConversationResult
    suspend fun eventConversation(eventId: String): ConversationResult
    suspend fun messages(conversationId: String, cursor: String?): MessagePage?
    suspend fun send(conversationId: String, body: String): SendResult
    suspend fun markRead(conversationId: String)
    suspend fun mute(conversationId: String, muted: Boolean): String?
    suspend fun block(userId: String): String?
    suspend fun unblock(userId: String): String?
    suspend fun report(messageId: String, reason: String): String?
}

@Singleton
class RemoteChatRepository @Inject constructor(
    private val api: QuestlyApi,
    tokens: TokenStore,
) : ChatRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val conversations = MutableStateFlow<List<Conversation>>(emptyList())
    private val incoming = MutableSharedFlow<Message>(extraBufferCapacity = 64)

    @Volatile private var cachedUserId: String? = null

    init {
        // While signed in, keep a WebSocket open (reconnecting) and refresh the inbox on each message.
        scope.launch {
            tokens.signedIn.collectLatest { signedIn ->
                if (!signedIn) {
                    conversations.value = emptyList()
                    return@collectLatest
                }
                refreshConversations()
                while (isActive) {
                    runCatching {
                        api.chatStream().collect { dto ->
                            incoming.emit(dto.toModel())
                            refreshConversations()
                        }
                    }.onFailure { Log.w(TAG, "chat stream dropped, retrying", it) }
                    delay(RECONNECT_DELAY_MS)
                }
            }
        }
    }

    override fun observeConversations(): StateFlow<List<Conversation>> = conversations.asStateFlow()
    override fun incomingMessages(): SharedFlow<Message> = incoming.asSharedFlow()

    override suspend fun currentUserId(): String? =
        cachedUserId ?: runCatching { api.me().id }.getOrNull()?.also { cachedUserId = it }

    override suspend fun refreshConversations() {
        runCatching { conversations.value = api.conversations().conversations.map { it.toModel() } }
            .onFailure { Log.w(TAG, "conversations refresh failed", it) }
    }

    override suspend fun startDirect(userId: String): ConversationResult = openConversation { api.startDirect(userId) }

    override suspend fun eventConversation(eventId: String): ConversationResult =
        openConversation { api.eventConversation(eventId) }

    override suspend fun messages(conversationId: String, cursor: String?): MessagePage? = runCatching {
        val page = api.messages(conversationId, cursor)
        MessagePage(page.items.map { it.toModel() }, page.nextCursor)
    }.onFailure { Log.w(TAG, "messages load failed", it) }.getOrNull()

    override suspend fun send(conversationId: String, body: String): SendResult = try {
        SendResult.Success(api.sendMessage(conversationId, body).toModel())
    } catch (e: Throwable) {
        Log.w(TAG, "send failed", e)
        SendResult.Error(reasonFor(e))
    }

    override suspend fun markRead(conversationId: String) {
        runCatching { api.markConversationRead(conversationId); refreshConversations() }
            .onFailure { Log.w(TAG, "markRead failed", it) }
    }

    override suspend fun mute(conversationId: String, muted: Boolean): String? =
        act("mute") { api.muteConversation(conversationId, muted) }

    override suspend fun block(userId: String): String? = act("block") { api.blockUser(userId) }
    override suspend fun unblock(userId: String): String? = act("unblock") { api.unblockUser(userId) }
    override suspend fun report(messageId: String, reason: String): String? =
        act("report") { api.reportMessage(messageId, reason) }

    private suspend inline fun openConversation(block: () -> com.example.questly.core.network.ChatConversationDto): ConversationResult = try {
        val conv = block().toModel()
        refreshConversations()
        ConversationResult.Success(conv)
    } catch (e: Throwable) {
        Log.w(TAG, "open conversation failed", e)
        ConversationResult.Error(reasonFor(e))
    }

    private suspend inline fun act(name: String, block: () -> Unit): String? {
        val error = runCatching { block() }.exceptionOrNull()
        if (error != null) Log.w(TAG, "$name failed", error)
        return error?.let(::reasonFor)
    }

    private fun reasonFor(e: Throwable): String = when {
        e is ChatActionException -> when (e.reason) {
            "NOT_FRIENDS" -> "you can only message friends"
            "SELF" -> "you can't message yourself"
            "BLOCKED" -> "messaging is unavailable with this user"
            "NOT_A_MEMBER" -> "register for the event to join its chat"
            else -> e.reason
        }
        e is ApiFailure -> "server error ${e.status}"
        e is java.net.UnknownHostException || e is java.net.ConnectException -> "can't reach the server"
        e is java.net.SocketTimeoutException || e.javaClass.simpleName.contains("Timeout") ->
            "the server timed out — it may be waking up, try again"
        else -> e.message?.take(80) ?: e.javaClass.simpleName
    }

    private companion object {
        const val TAG = "QuestlyChat"
        const val RECONNECT_DELAY_MS = 3_000L
    }
}
