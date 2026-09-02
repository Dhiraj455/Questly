package com.example.questly.feature.checkin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.questly.core.data.CheckInRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class PointsViewModel @Inject constructor(
    checkInRepository: CheckInRepository,
) : ViewModel() {
    val state: StateFlow<PointsUiState> =
        combine(checkInRepository.observePoints(), checkInRepository.observeCheckIns()) { points, history ->
            PointsUiState(points, history)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PointsUiState())
}
