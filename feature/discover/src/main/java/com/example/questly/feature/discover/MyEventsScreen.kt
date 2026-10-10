package com.example.questly.feature.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.questly.core.model.Event
import com.example.questly.core.model.EventStatus

@Composable
fun MyEventsScreen(
    events: List<Event>,
    onBack: () -> Unit,
    onEventClick: (Event) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("My events", style = MaterialTheme.typography.titleLarge)
        }

        if (events.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "You're not hosting any events yet.\nTap + on Discover to create one.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(events, key = { it.id }) { event -> MyEventRow(event, onEventClick) }
            }
        }
    }
}

@Composable
private fun MyEventRow(event: Event, onClick: (Event) -> Unit) {
    Card(
        onClick = { onClick(event) },
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                categoryIcon(event.category),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                Text(event.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    dateTimeLabel(event.startsAtMillis),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                statusLabel(event.status),
                style = MaterialTheme.typography.labelMedium,
                color = when (event.status) {
                    EventStatus.CANCELLED -> MaterialTheme.colorScheme.error
                    EventStatus.DRAFT -> MaterialTheme.colorScheme.onSurfaceVariant
                    EventStatus.PUBLISHED -> MaterialTheme.colorScheme.primary
                },
            )
        }
    }
}

private fun statusLabel(status: EventStatus): String = when (status) {
    EventStatus.DRAFT -> "Draft"
    EventStatus.PUBLISHED -> "Live"
    EventStatus.CANCELLED -> "Cancelled"
}
