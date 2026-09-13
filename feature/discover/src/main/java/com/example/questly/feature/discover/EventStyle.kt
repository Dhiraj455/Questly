package com.example.questly.feature.discover

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/** Two-stop gradient used for an event card's banner, keyed by category. */
fun categoryGradient(category: EventCategory): List<Color> = when (category) {
    EventCategory.MUSIC -> listOf(Color(0xFF7B2FF7), Color(0xFFF107A3))
    EventCategory.FOOD -> listOf(Color(0xFFFF6A00), Color(0xFFEE0979))
    EventCategory.SPORTS -> listOf(Color(0xFF11998E), Color(0xFF38EF7D))
    EventCategory.OUTDOORS -> listOf(Color(0xFF2196F3), Color(0xFF43E97B))
    EventCategory.ARTS -> listOf(Color(0xFFF857A6), Color(0xFFFF5858))
    EventCategory.COMMUNITY -> listOf(Color(0xFF4568DC), Color(0xFFB06AB3))
}

fun categoryIcon(category: EventCategory): ImageVector = when (category) {
    EventCategory.MUSIC -> Icons.Filled.MusicNote
    EventCategory.FOOD -> Icons.Filled.Restaurant
    EventCategory.SPORTS -> Icons.Filled.SportsSoccer
    EventCategory.OUTDOORS -> Icons.Filled.Terrain
    EventCategory.ARTS -> Icons.Filled.Palette
    EventCategory.COMMUNITY -> Icons.Filled.Groups
}

fun categoryLabel(category: EventCategory): String = when (category) {
    EventCategory.MUSIC -> "Music"
    EventCategory.FOOD -> "Food & Drink"
    EventCategory.SPORTS -> "Sports"
    EventCategory.OUTDOORS -> "Outdoors"
    EventCategory.ARTS -> "Arts"
    EventCategory.COMMUNITY -> "Community"
}
