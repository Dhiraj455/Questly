package com.example.questly.feature.map

import android.widget.Toast
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.questly.core.data.CheckInResult

@Composable
fun MapScreen(viewModel: MapViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var selectedId by remember { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize()) {
        MapLibreMap(
            checkpoints = state.checkpoints,
            userLocation = state.userLocation,
            onMarkerClick = { selectedId = it },
            modifier = Modifier.fillMaxSize(),
        )

        BrandPill(
            count = state.checkpoints.size,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp),
        )

        val selected = state.checkpoints.firstOrNull { it.checkpoint.id == selectedId }
        if (selected != null) {
            CheckpointCard(
                item = selected,
                onCheckIn = {
                    viewModel.checkIn(selected.checkpoint.id) { result ->
                        Toast.makeText(context, result.message(), Toast.LENGTH_SHORT).show()
                    }
                },
                onDismiss = { selectedId = null },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun BrandPill(count: Int, modifier: Modifier = Modifier) {
    Surface(
        modifier,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 3.dp,
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Filled.Explore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Text("Questly", style = MaterialTheme.typography.titleMedium)
            Text(
                "· $count nearby",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CheckpointCard(
    item: CheckpointUi,
    onCheckIn: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier,
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.checkpoint.title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close")
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PointsChip(item.checkpoint.points)
                RangeChip(item.withinRange)
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onCheckIn,
                enabled = item.withinRange,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(),
            ) {
                Text(
                    if (item.withinRange) "Check in" else "Move closer to check in",
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun PointsChip(points: Int) {
    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                Icons.Filled.EmojiEvents,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(16.dp),
            )
            Text(
                "$points pts",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun RangeChip(withinRange: Boolean) {
    val bg = if (withinRange) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (withinRange) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(shape = RoundedCornerShape(50), color = bg) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                if (withinRange) Icons.Filled.MyLocation else Icons.Filled.NearMe,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(16.dp),
            )
            Text(
                if (withinRange) "You're here" else "Out of range",
                style = MaterialTheme.typography.labelLarge,
                color = fg,
            )
        }
    }
}

private fun CheckInResult.message() = when (this) {
    CheckInResult.Success -> "Checked in! Points added."
    CheckInResult.TooFar -> "You're too far away."
    CheckInResult.OnCooldown -> "Already checked in recently."
    CheckInResult.UnknownCheckpoint -> "Unknown checkpoint."
}
