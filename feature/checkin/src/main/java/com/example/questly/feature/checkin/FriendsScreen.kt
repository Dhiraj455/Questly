package com.example.questly.feature.checkin

import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.PinDrop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.questly.core.data.AddFriendResult
import com.example.questly.core.model.FeedItem
import com.example.questly.core.model.Friend
import com.example.questly.core.model.FriendRequest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(viewModel: FriendsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Fetch the latest friends/requests/feed whenever the screen opens (requests from others
    // arrive while you're away), and support swipe-down to refresh.
    LaunchedEffect(Unit) { viewModel.refresh() }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { viewModel.refresh() },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
        item {
            Spacer(Modifier.height(8.dp))
            FriendCodeCard(code = state.data.myCode)
        }
        item {
            AddFriendRow { code, done ->
                viewModel.addFriend(code) { result ->
                    Toast.makeText(context, result.message(), Toast.LENGTH_SHORT).show()
                    if (result == AddFriendResult.SUCCESS) done()
                }
            }
        }

        if (state.data.requests.isNotEmpty()) {
            item { SectionHeader("Requests") }
            items(state.data.requests, key = { it.id }) { req ->
                RequestRow(
                    req,
                    onAccept = {
                        viewModel.accept(req.id) {
                            Toast.makeText(context, "Couldn't accept — check your connection and try again", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onDecline = {
                        viewModel.decline(req.id) {
                            Toast.makeText(context, "Couldn't decline — try again", Toast.LENGTH_SHORT).show()
                        }
                    },
                )
            }
        }

        item { SectionHeader(if (state.data.friends.isEmpty()) "Friends" else "Friends · ${state.data.friends.size}") }
        if (state.data.friends.isEmpty()) {
            item {
                Text(
                    "No friends yet — share your code to add some.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(state.data.friends, key = { it.userId }) { friend ->
                FriendRow(friend, onRemove = { viewModel.unfriend(friend.userId) })
            }
        }

        item { SectionHeader("Activity") }
        if (state.feed.isEmpty()) {
            item {
                Text(
                    "Check-ins from you and your friends will show up here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(state.feed, key = { it.id }) { FeedRow(it) }
            item { Spacer(Modifier.height(16.dp)) }
        }
        }
    }
}

private fun AddFriendResult.message(): String = when (this) {
    AddFriendResult.SUCCESS -> "Friend request sent"
    AddFriendResult.NOT_FOUND -> "No one has that code"
    AddFriendResult.SELF -> "That's your own code"
    AddFriendResult.ALREADY_FRIENDS -> "You're already friends"
    AddFriendResult.ALREADY_REQUESTED -> "Request already sent"
    AddFriendResult.ERROR -> "Something went wrong — try again"
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun FriendCodeCard(code: String?) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Row(
            Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Your friend code", style = MaterialTheme.typography.labelLarge)
                Text(
                    code ?: "…",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text("Share it so friends can add you", style = MaterialTheme.typography.bodySmall)
            }
            if (code != null) {
                IconButton(onClick = {
                    clipboard.setText(AnnotatedString(code))
                    Toast.makeText(context, "Code copied", Toast.LENGTH_SHORT).show()
                }) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = "Copy code")
                }
            }
        }
    }
}

@Composable
private fun AddFriendRow(onAdd: (String, done: () -> Unit) -> Unit) {
    var code by remember { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = code,
            onValueChange = { code = it.uppercase() },
            label = { Text("Enter a friend code") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.weight(1f),
        )
        Button(
            onClick = { onAdd(code) { code = "" } },
            enabled = code.isNotBlank(),
            modifier = Modifier.height(56.dp),
            shape = RoundedCornerShape(14.dp),
        ) {
            Icon(Icons.Filled.PersonAdd, contentDescription = null)
            Spacer(Modifier.size(6.dp))
            Text("Add")
        }
    }
}

@Composable
private fun RequestRow(request: FriendRequest, onAccept: () -> Unit, onDecline: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${request.requesterDisplayName} wants to be friends",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onAccept) {
                Icon(Icons.Filled.Check, contentDescription = "Accept", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onDecline) {
                Icon(Icons.Filled.Close, contentDescription = "Decline")
            }
        }
    }
}

@Composable
private fun FriendRow(friend: Friend, onRemove: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(friend.displayName, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${friend.totalPoints} points",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.PersonRemove, contentDescription = "Remove friend")
            }
        }
    }
}

@Composable
private fun FeedRow(item: FeedItem) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.PinDrop,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                "${item.displayName} · ${item.title.ifBlank { "Checkpoint" }}",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                DateUtils.getRelativeTimeSpanString(item.timestampMillis).toString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            "+${item.points}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
