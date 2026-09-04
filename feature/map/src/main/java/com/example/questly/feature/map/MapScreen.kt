package com.example.questly.feature.map

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.PinDrop
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.questly.core.data.CheckInResult
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// Peek shows only the header + drag handle; the list stays below the fold until dragged up.
private val SHEET_PEEK = 92.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(viewModel: MapViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var selectedId by remember { mutableStateOf<String?>(null) }
    var focus by remember { mutableStateOf<FocusTarget?>(null) }
    var focusSerial by remember { mutableIntStateOf(0) }

    val scaffoldState = rememberBottomSheetScaffoldState()
    val scope = rememberCoroutineScope()

    fun flyTo(cp: CheckpointUi) {
        focusSerial += 1
        focus = FocusTarget(cp.checkpoint.lat, cp.checkpoint.lng, focusSerial)
    }

    fun checkIn(cp: CheckpointUi) {
        viewModel.checkIn(cp.checkpoint.id) { result ->
            Toast.makeText(context, result.message(), Toast.LENGTH_SHORT).show()
        }
    }

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = SHEET_PEEK,
        sheetContent = {
            NearbySheet(
                checkpoints = state.checkpoints,
                onSelect = ::flyTo,
                onCheckIn = ::checkIn,
            )
        },
    ) {
        Box(Modifier.fillMaxSize()) {
            MapLibreMap(
                checkpoints = state.checkpoints,
                userLocation = state.userLocation,
                onMarkerClick = { id ->
                    // Red-dot tap: select it, fly there, and drop the sheet so the card shows.
                    state.checkpoints.firstOrNull { it.checkpoint.id == id }?.let {
                        selectedId = id
                        flyTo(it)
                        scope.launch { scaffoldState.bottomSheetState.partialExpand() }
                    }
                },
                focus = focus,
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
                    onCheckIn = { checkIn(selected) },
                    onDismiss = { selectedId = null },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = 16.dp, end = 16.dp, bottom = SHEET_PEEK + 16.dp)
                        .fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun NearbySheet(
    checkpoints: List<CheckpointUi>,
    onSelect: (CheckpointUi) -> Unit,
    onCheckIn: (CheckpointUi) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text("Nearby challenges", style = MaterialTheme.typography.titleLarge)
        Text(
            if (checkpoints.isEmpty()) "Drag up to browse" else "${checkpoints.size} found — sorted by distance",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            // Top padding keeps the first row below the collapsed-peek fold; bottom
            // padding clears the app nav bar when the sheet is expanded.
            contentPadding = PaddingValues(top = 20.dp, bottom = 32.dp),
        ) {
            items(checkpoints, key = { it.checkpoint.id }) { item ->
                ChallengeRow(item = item, onClick = { onSelect(item) }, onCheckIn = { onCheckIn(item) })
            }
        }
    }
}

@Composable
private fun ChallengeRow(item: CheckpointUi, onClick: () -> Unit, onCheckIn: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.PinDrop,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(24.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(item.checkpoint.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${distanceLabel(item)} · ${item.checkpoint.points} pts",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(onClick = onCheckIn, enabled = item.withinRange) {
                Text("Check in", fontWeight = FontWeight.SemiBold)
            }
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
            Text(
                item.checkpoint.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(Icons.Filled.EmojiEvents, "${item.checkpoint.points} pts", accent = true)
                Chip(
                    if (item.withinRange) Icons.Filled.MyLocation else Icons.Filled.NearMe,
                    distanceLabel(item),
                    accent = item.withinRange,
                )
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = onCheckIn, enabled = item.withinRange, modifier = Modifier.fillMaxWidth()) {
                Text(
                    if (item.withinRange) "Check in" else "Move closer to check in",
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun Chip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, accent: Boolean) {
    val bg = if (accent) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (accent) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(shape = RoundedCornerShape(50), color = bg) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = fg)
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

private fun distanceLabel(item: CheckpointUi): String {
    if (item.withinRange) return "You're here"
    val m = item.distanceMeters ?: return "Distance unknown"
    return when {
        m < 1000 -> "${m.roundToInt()} m away"
        m < 100_000 -> "%.1f km away".format(m / 1000)
        else -> "${(m / 1000).roundToInt()} km away"
    }
}

private fun CheckInResult.message() = when (this) {
    CheckInResult.Success -> "Checked in! Points added."
    CheckInResult.TooFar -> "You're too far away."
    CheckInResult.OnCooldown -> "Already checked in recently."
    CheckInResult.UnknownCheckpoint -> "Unknown checkpoint."
}
