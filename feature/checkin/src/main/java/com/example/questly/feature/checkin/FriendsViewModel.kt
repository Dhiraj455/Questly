package com.example.questly.feature.checkin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.questly.core.data.AddFriendResult
import com.example.questly.core.data.FriendsData
import com.example.questly.core.data.FriendsRepository
import com.example.questly.core.model.FeedItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FriendsUiState(
    val data: FriendsData = FriendsData(),
    val feed: List<FeedItem> = emptyList(),
)

@HiltViewModel
class FriendsViewModel @Inject constructor(
    private val repo: FriendsRepository,
) : ViewModel() {

    init { refresh() }

    val state: StateFlow<FriendsUiState> =
        combine(repo.observeFriends(), repo.observeFeed()) { data, feed ->
            FriendsUiState(data, feed)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FriendsUiState())

    fun refresh() = viewModelScope.launch { repo.refresh() }
    fun addFriend(code: String, onResult: (AddFriendResult) -> Unit) =
        viewModelScope.launch { onResult(repo.addFriend(code)) }
    fun accept(requestId: String) = viewModelScope.launch { repo.accept(requestId) }
    fun decline(requestId: String) = viewModelScope.launch { repo.decline(requestId) }
    fun unfriend(userId: String) = viewModelScope.launch { repo.unfriend(userId) }
}
