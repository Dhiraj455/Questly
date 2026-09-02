package com.example.questly.feature.map

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.questly.core.data.CheckInResult

@Composable
fun MapScreen(viewModel: MapViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // ponytail: MapLibre MapView (AndroidView + OSM raster style) is deferred.
    // This list is the functional, tested check-in UI; the visual map is additive.
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Nearby (${state.checkpoints.size})")
        LazyColumn {
            items(state.checkpoints, key = { it.checkpoint.id }) { item ->
                Card(Modifier.padding(vertical = 4.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(item.checkpoint.title)
                        Text("${item.checkpoint.points} pts")
                        Button(
                            enabled = item.withinRange,
                            onClick = {
                                viewModel.checkIn(item.checkpoint.id) { result ->
                                    Toast.makeText(context, result.message(), Toast.LENGTH_SHORT).show()
                                }
                            },
                        ) { Text(if (item.withinRange) "Check in" else "Too far") }
                    }
                }
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
