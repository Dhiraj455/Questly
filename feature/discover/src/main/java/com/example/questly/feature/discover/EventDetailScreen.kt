package com.example.questly.feature.discover

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.questly.core.data.RegistrationOutcome
import com.example.questly.core.model.Event
import com.example.questly.core.model.EventRegistration
import com.example.questly.core.model.EventStatus
import com.example.questly.core.model.EventVisibility
import com.example.questly.core.model.RegistrationStatus

@Composable
fun EventDetailScreen(
    initialEvent: Event,
    onBack: () -> Unit,
    onEdit: (Event) -> Unit,
    onCancel: (Event) -> Unit,
    onViewRoster: (Event) -> Unit,
    onRegister: (String, (RegistrationOutcome) -> Unit) -> Unit,
    onUnregister: (String, (RegistrationOutcome) -> Unit) -> Unit,
    onShowMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    var event by remember(initialEvent.id) { mutableStateOf(initialEvent) }
    var busy by remember(initialEvent.id) { mutableStateOf(false) }
    val cancelled = event.status == EventStatus.CANCELLED

    fun applyOutcome(outcome: RegistrationOutcome) {
        busy = false
        when (outcome) {
            is RegistrationOutcome.Success -> event = outcome.event
            is RegistrationOutcome.Error -> onShowMessage(outcome.message)
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(160.dp)
                .background(Brush.linearGradient(categoryGradient(event.category))),
        ) {
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Icon(
                categoryIcon(event.category),
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).size(44.dp),
            )
            Surface(
                color = Color.Black.copy(alpha = 0.25f),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
            ) {
                Text(
                    categoryLabel(event.category),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                )
            }
        }

        Column(Modifier.padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(16.dp))
            Text(event.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            if (cancelled) {
                Spacer(Modifier.height(6.dp))
                Text("Cancelled", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(16.dp))

            InfoRow(Icons.Filled.Person, "Hosted by ${event.hostDisplayName}${if (event.isHost) " (you)" else ""}")
            InfoRow(Icons.Filled.CalendarMonth, dateTimeLabel(event.startsAtMillis))
            if (event.venueName.isNotBlank()) InfoRow(Icons.Filled.Place, event.venueName)
            InfoRow(Icons.Filled.Groups, attendanceLine(event))

            if (event.description.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                Text("About", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(event.description, style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = {}, label = { Text(visibilityLabel(event)) })
                AssistChip(
                    onClick = {
                        val uri = Uri.parse("geo:${event.lat},${event.lng}?q=${event.lat},${event.lng}(${Uri.encode(event.title)})")
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                            .onFailure { onShowMessage("No maps app to open the location") }
                    },
                    label = { Text("Open in Maps") },
                    leadingIcon = { Icon(Icons.Filled.Place, contentDescription = null, Modifier.size(18.dp)) },
                )
            }

            Spacer(Modifier.height(24.dp))
            if (event.isHost) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(onClick = { onEdit(event) }, modifier = Modifier.weight(1f), enabled = !cancelled) {
                        Text("Edit")
                    }
                    Button(onClick = { onCancel(event) }, modifier = Modifier.weight(1f), enabled = !cancelled) {
                        Text("Cancel event")
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { onViewRoster(event) },
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
                ) { Text("View attendees (${event.registeredCount})") }
            } else {
                AttendeeAction(event, busy, cancelled, onShowMessage,
                    onRegister = { busy = true; onRegister(event.id, ::applyOutcome) },
                    onLeave = { busy = true; onUnregister(event.id, ::applyOutcome) },
                )
            }
        }
    }
}

@Composable
private fun AttendeeAction(
    event: Event,
    busy: Boolean,
    cancelled: Boolean,
    onShowMessage: (String) -> Unit,
    onRegister: () -> Unit,
    onLeave: () -> Unit,
) {
    val modifier = Modifier.fillMaxWidth().navigationBarsPadding()
    when {
        event.registration == EventRegistration.NONE ->
            Text(
                "Just show up — no registration needed.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = modifier,
            )

        event.viewerStatus == RegistrationStatus.ATTENDED ->
            Text("You attended this event 🎉", style = MaterialTheme.typography.titleMedium, modifier = modifier)

        event.isGoing -> Column(modifier) {
            Text(
                if (event.viewerStatus == RegistrationStatus.WAITLISTED) "You're on the waitlist" else "You're going 🎉",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onLeave, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                if (busy) CircularProgressIndicator(Modifier.height(20.dp), strokeWidth = 2.dp) else Text("Leave")
            }
        }

        event.registration == EventRegistration.PAID -> Button(
            onClick = { onShowMessage("Paid tickets are coming in a future update") },
            modifier = modifier,
            enabled = !cancelled,
        ) { Text(registrationLabel(event)) }

        else -> Button(onClick = onRegister, modifier = modifier, enabled = !busy && !cancelled) {
            if (busy) CircularProgressIndicator(Modifier.height(20.dp), strokeWidth = 2.dp)
            else Text(if (event.spotsLeft == 0) "Join the waitlist" else "Register — free")
        }
    }
}

/** "12 going · 3 spots left" / "12 going" (unlimited). */
private fun attendanceLine(event: Event): String {
    val going = "${event.registeredCount} going"
    return event.spotsLeft?.let { "$going · $it spots left" } ?: going
}

@Composable
private fun InfoRow(icon: ImageVector, text: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun visibilityLabel(event: Event): String = when (event.visibility) {
    EventVisibility.PUBLIC -> "Public"
    EventVisibility.FRIENDS -> "Friends only"
    EventVisibility.PRIVATE -> "Private"
}
