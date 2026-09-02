package com.example.questly

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
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
import com.example.questly.feature.checkin.PointsScreen
import com.example.questly.feature.map.MapScreen

@Composable
fun QuestlyNavHost() {
    // ponytail: two-tab Int state, not a NavHost/route graph — two screens don't
    // justify a nav graph. Upgrade to navigation-compose when a 3rd destination appears.
    var tab by remember { mutableIntStateOf(0) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Text("🗺") },
                    label = { Text("Map") },
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Text("⭐") },
                    label = { Text("Points") },
                )
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                0 -> MapScreen()
                else -> PointsScreen()
            }
        }
    }
}
