package com.example.questly.feature.map

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.Box
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
private fun CheckpointCard(
    item: CheckpointUi,
    onCheckIn: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(item.checkpoint.title, style = MaterialTheme.typography.titleMedium)
            Text("${item.checkpoint.points} pts", style = MaterialTheme.typography.bodyMedium)
            Row {
                Button(enabled = item.withinRange, onClick = onCheckIn) {
                    Text(if (item.withinRange) "Check in" else "Too far to check in")
                }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    }
}

private fun CheckInResult.message() = when (this) {
    CheckInResult.Success -> "Checked in! Points added."
    CheckInResult.TooFar -> "You're too far away."
    CheckInResult.OnCooldown -> "Already checked in recently."
    CheckInResult.UnknownCheckpoint -> "Unknown checkpoint."
}
