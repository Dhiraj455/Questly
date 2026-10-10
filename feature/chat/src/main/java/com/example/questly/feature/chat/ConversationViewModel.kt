package com.example.questly.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.questly.core.data.ChatRepository
import com.example.questly.core.data.SendResult
import com.example.questly.core.model.Message
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * One conversation's messages. History loads newest-first (rendered bottom-up); live messages for this
 * conversation stream in and are prepended. Sending appends the server's echo (the sender doesn't get
 * their own message over the socket).
 */
@HiltViewModel
class ConversationViewModel @Inject constructor(
    private val repo: ChatRepository,
) : ViewModel() {

    // Newest-first (pairs with a reverseLayout list, so index 0 sits at the bottom).
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending.asStateFlow()

    private val _myId = MutableStateFlow<String?>(null)
    val myId: StateFlow<String?> = _myId.asStateFlow()

    private var conversationId: String? = null
    private var started = false

    fun start(conversationId: String) {
        if (started) return
        started = true
        this.conversationId = conversationId

        viewModelScope.launch { _myId.value = repo.currentUserId() }
        viewModelScope.launch {
            repo.markRead(conversationId)
            repo.messages(conversationId, cursor = null)?.let { page ->
                _messages.value = page.messages // already newest-first
            }
        }
        viewModelScope.launch {
            repo.incomingMessages().collect { msg ->
                if (msg.conversationId == conversationId && _messages.value.none { it.id == msg.id }) {
                    _messages.update { listOf(msg) + it }
                    repo.markRead(conversationId)
                }
            }
        }
    }

    fun send(body: String, onError: (String) -> Unit) {
        val id = conversationId ?: return
        val text = body.trim()
        if (text.isEmpty()) return
        viewModelScope.launch {
            _sending.value = true
            try {
                when (val result = repo.send(id, text)) {
                    is SendResult.Success ->
                        if (_messages.value.none { it.id == result.message.id }) {
                            _messages.update { listOf(result.message) + it }
                        }
                    is SendResult.Error -> onError(result.message)
                }
            } finally {
                _sending.value = false
            }
        }
    }

    fun mute(muted: Boolean, onError: (String) -> Unit) = viewModelScope.launch {
        conversationId?.let { repo.mute(it, muted)?.let(onError) }
    }

    fun block(userId: String, onDone: (String?) -> Unit) = viewModelScope.launch { onDone(repo.block(userId)) }
    fun unblock(userId: String, onDone: (String?) -> Unit) = viewModelScope.launch { onDone(repo.unblock(userId)) }
    fun report(messageId: String, onDone: (String?) -> Unit) = viewModelScope.launch { onDone(repo.report(messageId, "")) }
}
