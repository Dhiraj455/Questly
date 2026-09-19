package com.example.questly.feature.map

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.questly.core.data.CheckInResult
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// Collapsed peek shows ONLY the drag handle; header + list appear on drag-up.
private val SHEET_PEEK = 40.dp

// Fully-expanded sheet tops out here, leaving a map strip + status bar visible above it.
private const val SHEET_MAX_FRACTION = 0.8f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(viewModel: MapViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var selectedId by remember { mutableStateOf<String?>(null) }
    var focus by remember { mutableStateOf<FocusTarget?>(null) }
    var focusSerial by remember { mutableIntStateOf(0) }

    // Type filter: empty set == "All". Only categories actually present get a checkbox.
    var selectedCategories by remember { mutableStateOf<Set<String>>(emptySet()) }
    val availableCategories = state.checkpoints
        .map { it.checkpoint.category }.filter { it.isNotBlank() }.distinct().sorted()
    val visibleCheckpoints = if (selectedCategories.isEmpty()) state.checkpoints
    else state.checkpoints.filter { it.checkpoint.category in selectedCategories }

    val scaffoldState = rememberBottomSheetScaffoldState()
    val scope = rememberCoroutineScope()

    fun flyTo(cp: CheckpointUi) {
        focusSerial += 1
        focus = FocusTarget(cp.checkpoint.lat, cp.checkpoint.lng, focusSerial)
    }

    fun recenter() {
        val loc = state.userLocation ?: return
        focusSerial += 1
        focus = FocusTarget(loc.lat, loc.lng, focusSerial)
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
                checkpoints = visibleCheckpoints,
                radiusMeters = state.radiusMeters,
                isLoading = state.isLoading,
                error = state.error,
                availableCategories = availableCategories,
                selectedCategories = selectedCategories,
                onToggleCategory = { cat ->
                    selectedCategories =
                        if (cat in selectedCategories) selectedCategories - cat else selectedCategories + cat
                },
                onSelectAllCategories = { selectedCategories = emptySet() },
                onRadiusChange = viewModel::setRadius,
                onRefresh = viewModel::refresh,
                onSelect = ::flyTo,
                onCheckIn = ::checkIn,
            )
        },
    ) {
        Box(Modifier.fillMaxSize()) {
            MapLibreMap(
                checkpoints = visibleCheckpoints,
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
                count = visibleCheckpoints.size,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp),
            )

            if (state.userLocation != null) {
                FloatingActionButton(
                    onClick = ::recenter,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = SHEET_PEEK + 16.dp),
                ) {
                    Icon(Icons.Filled.MyLocation, contentDescription = "Recenter on my location")
                }
            }

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
    radiusMeters: Double,
    isLoading: Boolean,
    error: String?,
    availableCategories: List<String>,
    selectedCategories: Set<String>,
    onToggleCategory: (String) -> Unit,
    onSelectAllCategories: () -> Unit,
    onRadiusChange: (Double) -> Unit,
    onRefresh: () -> Unit,
    onSelect: (CheckpointUi) -> Unit,
    onCheckIn: (CheckpointUi) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            // Cap expansion so a strip of map + the status bar stay visible above the sheet.
            .fillMaxHeight(SHEET_MAX_FRACTION)
            .navigationBarsPadding() // keep content clear of the system nav bar
            .padding(horizontal = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Nearby challenges", style = MaterialTheme.typography.titleLarge)
                Text(
                    when {
                        isLoading -> "Finding quests…"
                        error != null -> "Couldn't refresh — showing last results"
                        checkpoints.isEmpty() -> "No quests here — widen the radius or refresh"
                        else -> "${checkpoints.size} found — sorted by distance"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (error != null && !isLoading) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (availableCategories.isNotEmpty()) {
                TypeFilterButton(
                    availableCategories = availableCategories,
                    selectedCategories = selectedCategories,
                    onToggleCategory = onToggleCategory,
                    onSelectAllCategories = onSelectAllCategories,
                )
            }
            if (isLoading) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh quests")
                }
            }
        }
        RadiusSlider(radiusMeters = radiusMeters, onRadiusChange = onRadiusChange)
        LazyColumn(
            modifier = Modifier.weight(1f), // scroll within the capped sheet height
            verticalArrangement = Arrangement.spacedBy(8.dp),
            // Top padding keeps the first row below the collapsed-peek fold; bottom
            // padding clears the app nav bar when the sheet is expanded.
            contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp),
        ) {
            items(checkpoints, key = { it.checkpoint.id }) { item ->
                ChallengeRow(item = item, onClick = { onSelect(item) }, onCheckIn = { onCheckIn(item) })
            }
        }
    }
}

@Composable
private fun TypeFilterButton(
    availableCategories: List<String>,
    selectedCategories: Set<String>,
    onToggleCategory: (String) -> Unit,
    onSelectAllCategories: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.FilterList, contentDescription = "Filter by type")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("All types") },
                onClick = onSelectAllCategories,
                leadingIcon = { Checkbox(checked = selectedCategories.isEmpty(), onCheckedChange = null) },
            )
            availableCategories.forEach { cat ->
                DropdownMenuItem(
                    text = { Text(categoryLabel(cat)) },
                    onClick = { onToggleCategory(cat) },
                    leadingIcon = { Checkbox(checked = cat in selectedCategories, onCheckedChange = null) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RadiusSlider(radiusMeters: Double, onRadiusChange: (Double) -> Unit) {
    // Drag updates only the local position; we query on release so a single drag
    // doesn't fire a dozen Overpass calls.
    var meters by remember(radiusMeters) { mutableFloatStateOf(radiusMeters.toFloat()) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "%.0f km".format(meters / 1000),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.width(56.dp),
        )
        Slider(
            value = meters,
            onValueChange = { meters = it },
            onValueChangeFinished = { onRadiusChange(meters.toDouble()) },
            valueRange = MIN_RADIUS_M.toFloat()..MAX_RADIUS_M.toFloat(),
            modifier = Modifier.weight(1f),
            thumb = {
                // Small round thumb — Material3's default wide "pill" thumb looks oversized on
                // this compact control (see the reported UI). A 16dp dot reads as a normal slider.
                Box(
                    Modifier
                        .size(16.dp)
                        .shadow(1.dp, CircleShape)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                )
            },
            track = { sliderState ->
                // Continuous track with no thumb gap or end stop-indicator dot, for a clean line.
                SliderDefaults.Track(
                    sliderState = sliderState,
                    modifier = Modifier.height(4.dp),
                    thumbTrackGapSize = 0.dp,
                    drawStopIndicator = null,
                )
            },
        )
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
                    categoryIcon(item.checkpoint.category),
                    contentDescription = categoryLabel(item.checkpoint.category),
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
            if (item.checkedIn) {
                CheckedInBadge()
            } else {
                Button(onClick = onCheckIn, enabled = item.withinRange) {
                    Text("Check in", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/** Read-only "Checked in" status shown in place of the check-in button once a checkpoint is done. */
@Composable
private fun CheckedInBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(18.dp),
            )
            Text(
                "Checked in",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                fontWeight = FontWeight.SemiBold,
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
            if (item.checkedIn) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CheckedInBadge() }
            } else {
                Button(onClick = onCheckIn, enabled = item.withinRange, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        if (item.withinRange) "Check in" else "Move closer to check in",
                        fontWeight = FontWeight.SemiBold,
                    )
                }
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
