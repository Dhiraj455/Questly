package com.example.questly.feature.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.questly.core.data.CheckInRepository
import com.example.questly.core.data.CheckInResult
import com.example.questly.core.data.CheckpointRepository
import com.example.questly.core.data.RefreshResult
import com.example.questly.core.location.LocationProvider
import com.example.questly.core.location.UserLocation
import com.example.questly.core.model.distanceMeters
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

// Re-query when the user has moved at least this fraction of the current radius.
private const val REQUERY_MOVE_FRACTION = 0.3

@HiltViewModel
class MapViewModel @Inject constructor(
    private val checkpointRepository: CheckpointRepository,
    private val checkInRepository: CheckInRepository,
    locationProvider: LocationProvider,
) : ViewModel() {

    private val location = MutableStateFlow<UserLocation?>(null)
    private val radiusMeters = MutableStateFlow(DEFAULT_RADIUS_M)
    private val isLoading = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)

    private val refreshLock = Mutex()
    private var lastQuery: Query? = null

    init {
        viewModelScope.launch {
            locationProvider.observeLocation().collect { loc ->
                location.value = loc
                if (loc != null) maybeRefresh(loc, radiusMeters.value, force = false)
            }
        }
    }

    val state: StateFlow<MapUiState> =
        combine(location, checkpointRepository.observeCheckpoints(), radiusMeters, isLoading, error) {
                loc, checkpoints, radius, loading, err ->
            val ui = checkpoints
                .map { cp ->
                    val d = loc?.let { distanceMeters(it.lat, it.lng, cp.lat, cp.lng) }
                    CheckpointUi(cp, withinRange = d != null && d <= cp.radiusMeters, distanceMeters = d)
                }
                .sortedBy { it.distanceMeters ?: Double.MAX_VALUE }
            MapUiState(loc, ui, radiusMeters = radius, isLoading = loading, error = err)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MapUiState())

    /** Called when the user releases the radius slider. */
    fun setRadius(meters: Double) {
        radiusMeters.value = meters
        viewModelScope.launch { location.value?.let { maybeRefresh(it, meters, force = true) } }
    }

    /** Manual pull-to-refresh / refresh button. */
    fun refresh() {
        viewModelScope.launch { location.value?.let { maybeRefresh(it, radiusMeters.value, force = true) } }
    }

    fun checkIn(checkpointId: String, onResult: (CheckInResult) -> Unit) {
        val loc = state.value.userLocation ?: return onResult(CheckInResult.TooFar)
        viewModelScope.launch {
            onResult(checkInRepository.recordCheckIn(checkpointId, loc.lat, loc.lng, System.currentTimeMillis()))
        }
    }

    private suspend fun maybeRefresh(loc: UserLocation, radius: Double, force: Boolean) {
        if (!force && !shouldRequery(loc, radius)) return
        refreshLock.withLock {
            if (!force && !shouldRequery(loc, radius)) return // recheck: another refresh may have run
            isLoading.value = true
            error.value = null
            when (val result = checkpointRepository.refresh(loc.lat, loc.lng, radius)) {
                is RefreshResult.Error -> error.value = result.message
                is RefreshResult.Success -> {}
            }
            lastQuery = Query(loc.lat, loc.lng, radius)
            isLoading.value = false
        }
    }

    private fun shouldRequery(loc: UserLocation, radius: Double): Boolean {
        val last = lastQuery ?: return true
        if (last.radiusMeters != radius) return true
        return distanceMeters(last.lat, last.lng, loc.lat, loc.lng) >= REQUERY_MOVE_FRACTION * radius
    }

    private data class Query(val lat: Double, val lng: Double, val radiusMeters: Double)
}
