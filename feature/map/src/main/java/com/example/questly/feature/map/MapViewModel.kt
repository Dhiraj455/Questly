package com.example.questly.feature.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.questly.core.data.CheckInRepository
import com.example.questly.core.data.CheckInResult
import com.example.questly.core.data.CheckpointRepository
import com.example.questly.core.location.LocationProvider
import com.example.questly.core.model.distanceMeters
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MapViewModel @Inject constructor(
    private val checkpointRepository: CheckpointRepository,
    private val checkInRepository: CheckInRepository,
    locationProvider: LocationProvider,
) : ViewModel() {

    init { viewModelScope.launch { checkpointRepository.ensureSeeded() } }

    val state: StateFlow<MapUiState> =
        combine(locationProvider.observeLocation(), checkpointRepository.observeCheckpoints()) { loc, checkpoints ->
            val ui = checkpoints
                .map { cp ->
                    val d = loc?.let { distanceMeters(it.lat, it.lng, cp.lat, cp.lng) }
                    CheckpointUi(cp, withinRange = d != null && d <= cp.radiusMeters, distanceMeters = d)
                }
                .sortedBy { it.distanceMeters ?: Double.MAX_VALUE }
            MapUiState(loc, ui)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MapUiState())

    fun checkIn(checkpointId: String, onResult: (CheckInResult) -> Unit) {
        val loc = state.value.userLocation ?: return onResult(CheckInResult.TooFar)
        viewModelScope.launch {
            onResult(checkInRepository.recordCheckIn(checkpointId, loc.lat, loc.lng, System.currentTimeMillis()))
        }
    }
}
