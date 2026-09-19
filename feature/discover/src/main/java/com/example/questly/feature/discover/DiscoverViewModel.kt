package com.example.questly.feature.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

private val INITIAL_VISIBLE = DiscoverSection.entries.associateWith { PAGE_SIZE }

@HiltViewModel
class DiscoverViewModel @Inject constructor(
    private val allEvents: List<Event>,
) : ViewModel() {

    private val radiusMeters = MutableStateFlow(DEFAULT_RADIUS_M)
    private val visibleCounts = MutableStateFlow(INITIAL_VISIBLE)
    private val query = MutableStateFlow("")

    val state: StateFlow<DiscoverUiState> =
        combine(radiusMeters, visibleCounts, query) { radius, visible, q ->
            buildDiscoverState(allEvents, radius, visible, q)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            buildDiscoverState(allEvents, DEFAULT_RADIUS_M, INITIAL_VISIBLE),
        )

    /** Called when the user releases the radius slider. Re-buckets and resets radius-based paging. */
    fun setRadius(meters: Double) {
        radiusMeters.value = meters
        visibleCounts.update {
            it + (DiscoverSection.WITHIN_RADIUS to PAGE_SIZE) + (DiscoverSection.NEARBY to PAGE_SIZE)
        }
    }

    /** Called as the user types in the search box. Re-filters and resets paging to the first page. */
    fun setQuery(text: String) {
        query.value = text
        visibleCounts.value = INITIAL_VISIBLE
    }

    fun loadMore(section: DiscoverSection) {
        visibleCounts.update { it + (section to ((it[section] ?: PAGE_SIZE) + PAGE_SIZE)) }
    }
}
