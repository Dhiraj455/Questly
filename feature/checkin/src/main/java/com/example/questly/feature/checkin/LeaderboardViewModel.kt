package com.example.questly.feature.checkin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.questly.core.data.LeaderboardRepository
import com.example.questly.core.model.LeaderboardEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LeaderboardViewModel @Inject constructor(
    repo: LeaderboardRepository,
) : ViewModel() {

    // The WebSocket connects while the screen is observed (WhileSubscribed) and closes shortly after.
    val entries: StateFlow<List<LeaderboardEntry>> =
        repo.leaderboard().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val repository = repo
    private val _myUserId = MutableStateFlow<String?>(null)
    val myUserId: StateFlow<String?> = _myUserId.asStateFlow()

    init { refreshMyId() }

    /** Re-fetch the current user's id (call when the screen is shown, so it's right after switching accounts). */
    fun refreshMyId() = viewModelScope.launch { _myUserId.value = repository.myUserId() }
}
