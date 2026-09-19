package com.example.questly.feature.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(viewModel: DiscoverViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showFilter by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(16.dp))
            Text("Discover", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Events happening around you",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            SearchAndFilterBar(
                query = state.query,
                onQueryChange = viewModel::setQuery,
                onFilterClick = { showFilter = true },
            )
        }

        EventSectionRow(
            title = "Recommended",
            section = state.recommended,
            emptyText = "No recommendations yet",
            onLoadMore = { viewModel.loadMore(DiscoverSection.RECOMMENDED) },
        )
        EventSectionRow(
            title = "Within your radius",
            section = state.withinRadius,
            emptyText = "Nothing here yet — widen the radius",
            onLoadMore = { viewModel.loadMore(DiscoverSection.WITHIN_RADIUS) },
        )
        EventSectionRow(
            title = "Nearby, around you",
            section = state.nearby,
            emptyText = "No events further out right now",
            onLoadMore = { viewModel.loadMore(DiscoverSection.NEARBY) },
        )
    }

    if (showFilter) {
        ModalBottomSheet(
            onDismissRequest = { showFilter = false },
            sheetState = rememberModalBottomSheetState(),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp),
            ) {
                Text("Filter", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Distance",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                RadiusSlider(radiusMeters = state.radiusMeters, onRadiusChange = viewModel::setRadius)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { showFilter = false }) { Text("Done") }
                }
            }
        }
    }
}

@Composable
private fun SearchAndFilterBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onFilterClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            placeholder = { Text("Search events") },
        )
        FilledTonalIconButton(onClick = onFilterClick, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Filled.Tune, contentDescription = "Filter")
        }
    }
}

// ponytail: this styled radius slider is duplicated from :feature:map. When a third caller appears,
// lift it (and the radius constants) into a shared :core:designsystem module.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RadiusSlider(radiusMeters: Double, onRadiusChange: (Double) -> Unit) {
    var meters by remember(radiusMeters) { mutableFloatStateOf(radiusMeters.toFloat()) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "%.0f km".format(meters / 1000),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.width(52.dp),
        )
        Slider(
            value = meters,
            onValueChange = { meters = it },
            onValueChangeFinished = { onRadiusChange(meters.toDouble()) },
            valueRange = MIN_RADIUS_M.toFloat()..MAX_RADIUS_M.toFloat(),
            modifier = Modifier.weight(1f),
            thumb = {
                Box(
                    Modifier
                        .size(16.dp)
                        .shadow(1.dp, CircleShape)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                )
            },
            track = { sliderState ->
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
private fun EventSectionRow(
    title: String,
    section: EventSection,
    emptyText: String,
    onLoadMore: () -> Unit,
) {
    Spacer(Modifier.height(20.dp))
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (section.hasMore) {
            TextButton(onClick = onLoadMore) { Text("Load more") }
        }
    }
    Spacer(Modifier.height(8.dp))
    if (section.events.isEmpty()) {
        Text(
            emptyText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    } else {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(section.events, key = { it.id }) { EventCard(it) }
            if (section.hasMore) {
                item(key = "load-more-$title") { LoadMoreCard(onClick = onLoadMore) }
            }
        }
    }
}

private val CARD_WIDTH = 240.dp
private val CARD_HEIGHT = 220.dp
private val BANNER_HEIGHT = 120.dp

@Composable
private fun EventCard(event: Event) {
    Card(
        Modifier
            .width(CARD_WIDTH)
            .height(CARD_HEIGHT),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(BANNER_HEIGHT)
                .background(Brush.linearGradient(categoryGradient(event.category))),
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.25f),
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp),
            ) {
                Text(
                    categoryLabel(event.category),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
            Icon(
                categoryIcon(event.category),
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
                    .size(36.dp),
            )
        }
        Column(Modifier.padding(14.dp)) {
            Text(
                event.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(
                    Icons.Filled.NearMe,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    distanceLabel(event.distanceMeters),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LoadMoreCard(onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .width(130.dp)
            .height(CARD_HEIGHT),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            Modifier.fillMaxWidth().height(CARD_HEIGHT),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text("Load more", style = MaterialTheme.typography.labelLarge)
        }
    }
}

private fun distanceLabel(meters: Double): String =
    if (meters < 1000) "${meters.roundToInt()} m away" else "%.1f km away".format(meters / 1000)
