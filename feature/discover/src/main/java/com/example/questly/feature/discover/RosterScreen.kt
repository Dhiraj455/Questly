package com.example.questly.feature.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.questly.core.data.EventsRepository
import com.example.questly.core.model.EventRoster
import com.example.questly.core.model.RosterMember
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface RosterUi {
    data object Loading : RosterUi
    data object Error : RosterUi
    data class Loaded(val roster: EventRoster) : RosterUi
}

@HiltViewModel
class EventRosterViewModel @Inject constructor(private val repo: EventsRepository) : ViewModel() {
    private val _state = MutableStateFlow<RosterUi>(RosterUi.Loading)
    val state: StateFlow<RosterUi> = _state.asStateFlow()

    fun load(eventId: String) = viewModelScope.launch {
        _state.value = RosterUi.Loading
        _state.value = repo.roster(eventId)?.let { RosterUi.Loaded(it) } ?: RosterUi.Error
    }
}

@Composable
fun RosterScreen(
    eventId: String,
    eventTitle: String,
    onBack: () -> Unit,
    viewModel: EventRosterViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(eventId) { viewModel.load(eventId) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Column {
                Text("Attendees", style = MaterialTheme.typography.titleLarge)
                Text(eventTitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        when (val s = state) {
            RosterUi.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            RosterUi.Error -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text("Couldn't load the roster", color = MaterialTheme.colorScheme.error)
            }
            is RosterUi.Loaded -> {
                val r = s.roster
                if (r.registered.isEmpty() && r.waitlisted.isEmpty() && r.attended.isEmpty()) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Text("No one has registered yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        section("Going (${r.registered.size})", r.registered)
                        section("Waitlist (${r.waitlisted.size})", r.waitlisted)
                        section("Attended (${r.attended.size})", r.attended)
                    }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.section(title: String, members: List<RosterMember>) {
    if (members.isEmpty()) return
    item(key = "h-$title") {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
        )
    }
    items(members.size, key = { "$title-${members[it].userId}" }) { i ->
        Text(members[i].displayName, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 4.dp))
    }
}
