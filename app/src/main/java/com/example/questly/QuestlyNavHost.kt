package com.example.questly

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.LocalActivity
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Leaderboard
import androidx.compose.material.icons.outlined.LocalActivity
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.questly.feature.chat.ChatScreen
import com.example.questly.feature.checkin.FriendsScreen
import com.example.questly.feature.checkin.LeaderboardScreen
import com.example.questly.feature.checkin.PointsScreen
import com.example.questly.feature.discover.DiscoverScreen
import com.example.questly.feature.map.MapScreen

private enum class Tab(val label: String, val selectedIcon: ImageVector, val icon: ImageVector) {
    Explore("Explore", Icons.Filled.Explore, Icons.Outlined.Explore),
    Discover("Discover", Icons.Filled.LocalActivity, Icons.Outlined.LocalActivity),
    Friends("Friends", Icons.Filled.Group, Icons.Outlined.Group),
    Chat("Chat", Icons.AutoMirrored.Filled.Chat, Icons.AutoMirrored.Outlined.Chat),
    Ranks("Ranks", Icons.Filled.Leaderboard, Icons.Outlined.Leaderboard),
    Rewards("Rewards", Icons.Filled.EmojiEvents, Icons.Outlined.EmojiEvents),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestlyNavHost() {
    var current by remember { mutableIntStateOf(0) }
    var showProfile by remember { mutableStateOf(false) }

    // The profile opens over the tabs (no nav backstack in this shell); back returns to them
    // instead of exiting the app.
    if (showProfile) {
        BackHandler { showProfile = false }
        ProfileScreen(onBack = { showProfile = false })
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Tab.entries[current].label) },
                actions = {
                    IconButton(onClick = { showProfile = true }) {
                        Icon(Icons.Filled.AccountCircle, contentDescription = "Profile")
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEachIndexed { index, tab ->
                    val selected = current == index
                    NavigationBarItem(
                        selected = selected,
                        onClick = { current = index },
                        icon = {
                            Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = tab.label)
                        },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (Tab.entries[current]) {
                Tab.Explore -> MapScreen()
                Tab.Discover -> DiscoverScreen()
                Tab.Friends -> FriendsScreen()
                Tab.Chat -> ChatScreen()
                Tab.Ranks -> LeaderboardScreen()
                Tab.Rewards -> PointsScreen()
            }
        }
    }
}
