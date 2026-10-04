package com.example.questly.feature.checkin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.questly.core.data.CheckInRepository
import com.example.questly.core.data.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PointsViewModel @Inject constructor(
    checkInRepository: CheckInRepository,
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    init {
        // Re-fetch streak/achievements whenever the check-in history changes (initial load + each
        // new check-in), so the Rewards screen reflects the latest state.
        viewModelScope.launch {
            checkInRepository.observeCheckIns().collect { profileRepository.refresh() }
        }
    }

    val state: StateFlow<PointsUiState> =
        combine(
            checkInRepository.observePoints(),
            checkInRepository.observeCheckIns(),
            profileRepository.observeProfile(),
        ) { points, history, profile ->
            PointsUiState(points, history, profile)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PointsUiState())
}
