package com.example.questly.feature.chat

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.questly.core.model.Conversation
import com.example.questly.core.model.ConversationType
import com.example.questly.core.model.Friend
import kotlinx.coroutines.launch

private sealed interface ChatNav {
    data object Inbox : ChatNav
    data object NewDm : ChatNav
    data class Thread(val conversation: Conversation) : ChatNav
}

@Composable
fun ChatScreen(viewModel: ChatViewModel = hiltViewModel()) {
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val friends by viewModel.friendList.collectAsStateWithLifecycle()

    var stack by remember { mutableStateOf<List<ChatNav>>(listOf(ChatNav.Inbox)) }
    val current = stack.last()
    fun push(nav: ChatNav) { stack = stack + nav }
    fun pop() { if (stack.size > 1) stack = stack.dropLast(1) }

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    fun message(text: String) { scope.launch { snackbar.showSnackbar(text) } }

    if (current != ChatNav.Inbox) BackHandler { pop() }

    Box(Modifier.fillMaxSize()) {
        when (val nav = current) {
            ChatNav.Inbox -> ChatInbox(
                conversations = conversations,
                onOpen = { push(ChatNav.Thread(it)) },
                onNewMessage = { push(ChatNav.NewDm) },
            )

            ChatNav.NewDm -> NewDmPicker(
                friends = friends,
                onBack = { pop() },
                onPick = { friend ->
                    viewModel.startDirect(friend.userId) { result ->
                        when (result) {
                            is com.example.questly.core.data.ConversationResult.Success ->
                                stack = listOf(ChatNav.Inbox, ChatNav.Thread(result.conversation))
                            is com.example.questly.core.data.ConversationResult.Error -> message(result.message)
                        }
                    }
                },
            )

            is ChatNav.Thread -> ConversationScreen(conversation = nav.conversation, onBack = { pop() })
        }

        SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }
}

@Composable
private fun ChatInbox(
    conversations: List<Conversation>,
    onOpen: (Conversation) -> Unit,
    onNewMessage: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        if (conversations.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(
                    "No conversations yet.\nMessage a friend, or register for an event to join its chat.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                items(conversations, key = { it.id }) { ConversationRow(it, onOpen) }
            }
        }
        FloatingActionButton(
            onClick = onNewMessage,
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp).navigationBarsPadding(),
        ) {
            Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "New message")
        }
    }
}

@Composable
private fun ConversationRow(conversation: Conversation, onOpen: (Conversation) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onOpen(conversation) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (conversation.type == ConversationType.EVENT) Icons.Filled.Event else Icons.Filled.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(24.dp),
            )
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                conversation.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                conversation.lastMessage?.let { "${it.senderDisplayName}: ${it.body}" } ?: "No messages yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (conversation.unreadCount > 0) {
            Badge { Text(conversation.unreadCount.toString()) }
        }
    }
}

@Composable
private fun NewDmPicker(
    friends: List<Friend>,
    onBack: () -> Unit,
    onPick: (Friend) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text("New message", style = MaterialTheme.typography.titleLarge)
        }
        if (friends.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text("Add friends first to start a DM.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
                items(friends, key = { it.userId }) { friend ->
                    Card(
                        onClick = { onPick(friend) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Filled.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(friend.displayName, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }
    }
}
