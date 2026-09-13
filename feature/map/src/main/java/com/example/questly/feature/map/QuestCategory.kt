package com.example.questly.feature.map

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.BeachAccess
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.PinDrop
import androidx.compose.ui.graphics.vector.ImageVector

/** A distinct, recognizable icon per POI category. */
fun categoryIcon(category: String): ImageVector = when (category) {
    "PARK" -> Icons.Filled.Park
    "BEACH" -> Icons.Filled.BeachAccess
    "VIEWPOINT" -> Icons.Filled.Landscape
    "LANDMARK" -> Icons.Filled.AccountBalance
    else -> Icons.Filled.PinDrop
}

/** Human-readable, pluralized label for a category (used in the type filter). */
fun categoryLabel(category: String): String = when (category) {
    "PARK" -> "Parks"
    "BEACH" -> "Beaches"
    "VIEWPOINT" -> "Viewpoints"
    "LANDMARK" -> "Landmarks"
    else -> "Other"
}
