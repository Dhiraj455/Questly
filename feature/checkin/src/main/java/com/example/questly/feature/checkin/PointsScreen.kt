package com.example.questly.feature.checkin

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun PointsScreen(viewModel: PointsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(Modifier.padding(16.dp)) {
        Text("Points: ${state.totalPoints}", style = MaterialTheme.typography.headlineMedium)
        LazyColumn {
            items(state.history, key = { it.id }) { checkIn ->
                Text("Checked in at ${checkIn.checkpointId}", Modifier.padding(vertical = 4.dp))
            }
        }
    }
}
