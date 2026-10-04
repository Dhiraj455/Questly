package com.example.questly.core.data

import com.example.questly.core.model.FeedItem
import com.example.questly.core.model.Friend
import com.example.questly.core.model.FriendRequest
import com.example.questly.core.network.FriendActionException
import com.example.questly.core.network.QuestlyApi
import com.example.questly.core.network.TokenStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

data class FriendsData(
    val myCode: String? = null,
    val friends: List<Friend> = emptyList(),
    val requests: List<FriendRequest> = emptyList(),
)

enum class AddFriendResult { SUCCESS, NOT_FOUND, SELF, ALREADY_FRIENDS, ALREADY_REQUESTED, ERROR }

interface FriendsRepository {
    fun observeFriends(): StateFlow<FriendsData>
    fun observeFeed(): StateFlow<List<FeedItem>>
    suspend fun refresh()
    suspend fun addFriend(code: String): AddFriendResult
    /** Returns true if the server accepted the action. The caller can surface a failure. */
    suspend fun accept(requestId: String): Boolean
    suspend fun decline(requestId: String): Boolean
    suspend fun unfriend(userId: String): Boolean
}

@Singleton
class RemoteFriendsRepository @Inject constructor(
    private val api: QuestlyApi,
    tokens: TokenStore,
) : FriendsRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val friends = MutableStateFlow(FriendsData())
    private val feed = MutableStateFlow<List<FeedItem>>(emptyList())

    init {
        // Clear the friend list/feed on sign-out so a new account doesn't inherit them.
        scope.launch {
            tokens.signedIn.collect { signedIn ->
                if (signedIn) refresh() else { friends.value = FriendsData(); feed.value = emptyList() }
            }
        }
    }

    override fun observeFriends(): StateFlow<FriendsData> = friends.asStateFlow()
    override fun observeFeed(): StateFlow<List<FeedItem>> = feed.asStateFlow()

    override suspend fun refresh() {
        runCatching {
            val code = api.friendCode().code
            val page = api.friends()
            friends.value = FriendsData(
                myCode = code,
                friends = page.friends.map { Friend(it.userId, it.displayName, it.totalPoints) },
                requests = page.incomingRequests.map { FriendRequest(it.id, it.requesterId, it.requesterDisplayName) },
            )
        }
        runCatching {
            feed.value = api.feed().items.map {
                FeedItem(it.id, it.userId, it.displayName, it.title, it.points, Instant.parse(it.timestamp).toEpochMilli())
            }
        }
    }

    override suspend fun addFriend(code: String): AddFriendResult = try {
        api.addFriend(code.trim())
        refresh()
        AddFriendResult.SUCCESS
    } catch (e: FriendActionException) {
        when (e.reason) {
            "NOT_FOUND" -> AddFriendResult.NOT_FOUND
            "SELF" -> AddFriendResult.SELF
            "ALREADY_FRIENDS" -> AddFriendResult.ALREADY_FRIENDS
            "ALREADY_REQUESTED" -> AddFriendResult.ALREADY_REQUESTED
            else -> AddFriendResult.ERROR
        }
    } catch (_: Exception) {
        AddFriendResult.ERROR
    }

    override suspend fun accept(requestId: String): Boolean {
        val ok = runCatching { api.acceptFriend(requestId) }.isSuccess
        refresh()
        return ok
    }

    override suspend fun decline(requestId: String): Boolean {
        val ok = runCatching { api.declineFriend(requestId) }.isSuccess
        refresh()
        return ok
    }

    override suspend fun unfriend(userId: String): Boolean {
        val ok = runCatching { api.unfriend(userId) }.isSuccess
        refresh()
        return ok
    }
}
