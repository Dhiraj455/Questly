package com.example.questly.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.questly.core.data.ChatRepository
import com.example.questly.core.data.ConversationResult
import com.example.questly.core.data.FriendsRepository
import com.example.questly.core.model.Conversation
import com.example.questly.core.model.Friend
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The chat inbox + "new DM" friend picker. */
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chat: ChatRepository,
    private val friends: FriendsRepository,
) : ViewModel() {

    val conversations: StateFlow<List<Conversation>> =
        chat.observeConversations().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val friendList: StateFlow<List<Friend>> =
        friends.observeFriends().map { it.friends }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        chat.refreshConversations()
        friends.refresh()
    }

    fun startDirect(userId: String, onResult: (ConversationResult) -> Unit) =
        viewModelScope.launch { onResult(chat.startDirect(userId)) }
}
