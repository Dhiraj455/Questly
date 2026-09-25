package com.example.questly.feature.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.questly.core.data.CHECK_IN_COOLDOWN_MILLIS
import com.example.questly.core.data.CheckInRepository
import com.example.questly.core.data.CheckInResult
import com.example.questly.core.data.CheckpointRepository
import com.example.questly.core.data.RefreshResult
import com.example.questly.core.location.LocationProvider
import com.example.questly.core.location.UserLocation
import com.example.questly.core.model.distanceMeters
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
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

// Wait for the slider to settle before hitting the network, so dragging through several values
// (or a burst of releases) collapses into a single query for the final radius.
private const val RADIUS_DEBOUNCE_MILLIS = 400L

@HiltViewModel
class MapViewModel @Inject constructor(
    private val checkpointRepository: CheckpointRepository,
    private val checkInRepository: CheckInRepository,
    locationProvider: LocationProvider,
) : ViewModel() {

    private val location = MutableStateFlow<UserLocation?>(null)
    private val radiusMeters = MutableStateFlow(DEFAULT_RADIUS_M)
    private val isLoading = MutableStateFlow(false)
    private val checkingInId = MutableStateFlow<String?>(null)
    private val error = MutableStateFlow<String?>(null)

    private val refreshLock = Mutex()
    private var lastQuery: Query? = null
    private var radiusRefreshJob: Job? = null

    init {
        viewModelScope.launch {
            locationProvider.observeLocation().collect { loc ->
                location.value = loc
                if (loc != null) maybeRefresh(loc, radiusMeters.value, force = false)
            }
        }
    }

    // Builds the display list, marking a checkpoint checkedIn while its latest check-in is still
    // within the cooldown window (matching the recordCheckIn rule, so the button stays disabled).
    private val checkpointsUi: Flow<List<CheckpointUi>> =
        combine(location, checkpointRepository.observeCheckpoints(), checkInRepository.observeCheckIns()) {
                loc, checkpoints, checkIns ->
            val now = System.currentTimeMillis()
            val lastByCheckpoint = checkIns
                .groupBy { it.checkpointId }
                .mapValues { (_, rows) -> rows.maxOf { it.timestampMillis } }
            checkpoints
                .map { cp ->
                    val d = loc?.let { distanceMeters(it.lat, it.lng, cp.lat, cp.lng) }
                    val last = lastByCheckpoint[cp.id]
                    CheckpointUi(
                        cp,
                        withinRange = d != null && d <= cp.radiusMeters,
                        distanceMeters = d,
                        checkedIn = last != null && now - last < CHECK_IN_COOLDOWN_MILLIS,
                    )
                }
                .sortedBy { it.distanceMeters ?: Double.MAX_VALUE }
        }

    val state: StateFlow<MapUiState> =
        combine(checkpointsUi, location, radiusMeters, isLoading, checkingInId) { ui, loc, radius, loading, checkingId ->
            MapUiState(loc, ui, radiusMeters = radius, isLoading = loading, checkingInId = checkingId)
        }.combine(error) { current, err ->
            current.copy(error = err)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MapUiState())

    /**
     * Called when the user releases the radius slider. Updates the shown radius immediately but
     * debounces the network query, cancelling any pending one so only the last value is fetched.
     */
    fun setRadius(meters: Double) {
        radiusMeters.value = meters
        radiusRefreshJob?.cancel()
        radiusRefreshJob = viewModelScope.launch {
            delay(RADIUS_DEBOUNCE_MILLIS)
            location.value?.let { maybeRefresh(it, meters, force = true) }
        }
    }

    /** Manual pull-to-refresh / refresh button. */
    fun refresh() {
        viewModelScope.launch { location.value?.let { maybeRefresh(it, radiusMeters.value, force = true) } }
    }

    fun checkIn(checkpointId: String, onResult: (CheckInResult) -> Unit) {
        val loc = state.value.userLocation ?: return onResult(CheckInResult.TooFar)
        if (checkingInId.value != null) return
        viewModelScope.launch {
            checkingInId.value = checkpointId
            try {
                onResult(checkInRepository.recordCheckIn(checkpointId, loc.lat, loc.lng, System.currentTimeMillis()))
            } finally {
                checkingInId.value = null
            }
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
