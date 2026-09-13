package com.example.questly

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.LocalActivity
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.LocalActivity
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.questly.feature.checkin.PointsScreen
import com.example.questly.feature.discover.DiscoverScreen
import com.example.questly.feature.map.MapScreen

private enum class Tab(val label: String, val selectedIcon: ImageVector, val icon: ImageVector) {
    Explore("Explore", Icons.Filled.Explore, Icons.Outlined.Explore),
    Discover("Discover", Icons.Filled.LocalActivity, Icons.Outlined.LocalActivity),
    Rewards("Rewards", Icons.Filled.EmojiEvents, Icons.Outlined.EmojiEvents),
}

@Composable
fun QuestlyNavHost() {
    // ponytail: two-tab Int state, not a NavHost/route graph. Upgrade to
    // navigation-compose when a 3rd destination or deep links appear.
    var current by remember { mutableIntStateOf(0) }
    Scaffold(
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
                Tab.Rewards -> PointsScreen()
            }
        }
    }
}
