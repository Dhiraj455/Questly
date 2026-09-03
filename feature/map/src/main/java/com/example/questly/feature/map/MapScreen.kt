package com.example.questly.feature.map

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PinDrop
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(viewModel: MapViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var selectedId by remember { mutableStateOf<String?>(null) }
    var focus by remember { mutableStateOf<FocusTarget?>(null) }
    var focusSerial by remember { mutableIntStateOf(0) }

    val scaffoldState = rememberBottomSheetScaffoldState()
    val listState = rememberLazyListState()

    fun focusOn(cp: CheckpointUi) {
        selectedId = cp.checkpoint.id
        focusSerial += 1
        focus = FocusTarget(cp.checkpoint.lat, cp.checkpoint.lng, focusSerial)
    }

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = 200.dp,
        sheetContent = {
            NearbySheet(
                checkpoints = state.checkpoints,
                selectedId = selectedId,
                listState = listState,
                onSelect = ::focusOn,
                onCheckIn = { cp ->
                    viewModel.checkIn(cp.checkpoint.id) { result ->
                        Toast.makeText(context, result.message(), Toast.LENGTH_SHORT).show()
                    }
                },
            )
        },
    ) {
        Box(Modifier.fillMaxSize()) {
            MapLibreMap(
                checkpoints = state.checkpoints,
                userLocation = state.userLocation,
                onMarkerClick = { id ->
                    state.checkpoints.firstOrNull { it.checkpoint.id == id }?.let { focusOn(it) }
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
        }
    }
}

@Composable
private fun NearbySheet(
    checkpoints: List<CheckpointUi>,
    selectedId: String?,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onSelect: (CheckpointUi) -> Unit,
    onCheckIn: (CheckpointUi) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text("Nearby challenges", style = MaterialTheme.typography.titleLarge)
        Text(
            "${checkpoints.size} within reach — sorted by distance",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
        ) {
            items(checkpoints, key = { it.checkpoint.id }) { item ->
                ChallengeRow(
                    item = item,
                    selected = item.checkpoint.id == selectedId,
                    onClick = { onSelect(item) },
                    onCheckIn = { onCheckIn(item) },
                )
            }
        }
    }
}

@Composable
private fun ChallengeRow(
    item: CheckpointUi,
    selected: Boolean,
    onClick: () -> Unit,
    onCheckIn: () -> Unit,
) {
    val container =
        if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = container,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
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
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (item.withinRange) {
                        Icon(
                            Icons.Filled.MyLocation,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    Text(
                        distanceLabel(item),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text("·", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Icon(
                        Icons.Filled.EmojiEvents,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        "${item.checkpoint.points}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Button(onClick = onCheckIn, enabled = item.withinRange) {
                Text("Check in", fontWeight = FontWeight.SemiBold)
            }
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
