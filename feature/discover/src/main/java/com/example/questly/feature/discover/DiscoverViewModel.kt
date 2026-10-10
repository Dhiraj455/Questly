package com.example.questly.feature.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.questly.core.data.EventsRepository
import com.example.questly.core.data.RegistrationOutcome
import com.example.questly.core.location.LocationProvider
import com.example.questly.core.location.UserLocation
import com.example.questly.core.model.Event
import com.example.questly.core.model.distanceMeters
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

private val INITIAL_VISIBLE = DiscoverSection.entries.associateWith { PAGE_SIZE }

// Re-query discovery once the user has moved at least this far from the last fetch point.
private const val REQUERY_MOVE_M = 2_000.0

@HiltViewModel
class DiscoverViewModel @Inject constructor(
    private val repo: EventsRepository,
    locationProvider: LocationProvider,
) : ViewModel() {

    private val radiusMeters = MutableStateFlow(DEFAULT_RADIUS_M)
    private val visibleCounts = MutableStateFlow(INITIAL_VISIBLE)
    private val query = MutableStateFlow("")
    private val location = MutableStateFlow<UserLocation?>(null)
    private val isLoading = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)

    private val refreshLock = Mutex()
    private var lastQuery: UserLocation? = null

    init {
        viewModelScope.launch {
            locationProvider.observeLocation().collect { loc ->
                location.value = loc
                if (loc != null) maybeRefresh(loc, force = false)
            }
        }
    }

    val state: StateFlow<DiscoverUiState> =
        combine(repo.observeNearby(), radiusMeters, visibleCounts, query) { events, radius, visible, q ->
            buildDiscoverState(events, radius, visible, q)
        }.combine(location) { s, loc -> s.copy(userLat = loc?.lat, userLng = loc?.lng) }
            .combine(isLoading) { s, loading -> s.copy(isLoading = loading) }
            .combine(error) { s, err -> s.copy(error = err) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DiscoverUiState())

    val myEvents: StateFlow<List<Event>> =
        repo.observeMine().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Called when the user releases the radius slider — re-buckets locally (no network). */
    fun setRadius(meters: Double) {
        radiusMeters.value = meters
        visibleCounts.update {
            it + (DiscoverSection.WITHIN_RADIUS to PAGE_SIZE) + (DiscoverSection.NEARBY to PAGE_SIZE)
        }
    }

    fun setQuery(text: String) {
        query.value = text
        visibleCounts.value = INITIAL_VISIBLE
    }

    fun loadMore(section: DiscoverSection) {
        visibleCounts.update { it + (section to ((it[section] ?: PAGE_SIZE) + PAGE_SIZE)) }
    }

    /** Manual refresh of the discovery feed at the current location. */
    fun refresh() = viewModelScope.launch {
        location.value?.let { maybeRefresh(it, force = true) }
    }

    fun refreshMine() = viewModelScope.launch { repo.refreshMine() }

    fun cancel(id: String, onError: (String) -> Unit = {}) = viewModelScope.launch {
        repo.cancel(id)?.let(onError)
    }

    fun register(id: String, onResult: (RegistrationOutcome) -> Unit) = viewModelScope.launch {
        onResult(repo.register(id))
    }

    fun unregister(id: String, onResult: (RegistrationOutcome) -> Unit) = viewModelScope.launch {
        onResult(repo.unregister(id))
    }

    private suspend fun maybeRefresh(loc: UserLocation, force: Boolean) {
        if (!force && !shouldRequery(loc)) return
        refreshLock.withLock {
            if (!force && !shouldRequery(loc)) return // another refresh may have run while we waited
            isLoading.value = true
            error.value = null
            try {
                repo.refreshNearby(loc.lat, loc.lng, FETCH_RADIUS_KM)?.let { error.value = it }
                lastQuery = loc
            } finally {
                isLoading.value = false
            }
        }
    }

    private fun shouldRequery(loc: UserLocation): Boolean {
        val last = lastQuery ?: return true
        return distanceMeters(last.lat, last.lng, loc.lat, loc.lng) >= REQUERY_MOVE_M
    }
}
