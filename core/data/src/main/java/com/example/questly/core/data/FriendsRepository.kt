package com.example.questly.core.data

import android.util.Log
import com.example.questly.core.model.FeedItem
import com.example.questly.core.model.Friend
import com.example.questly.core.model.FriendRequest
import com.example.questly.core.network.ApiFailure
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
    /** Null on success; otherwise a short reason to show the user. The caller surfaces failures. */
    suspend fun accept(requestId: String): String?
    suspend fun decline(requestId: String): String?
    suspend fun unfriend(userId: String): String?
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
        }.onFailure { Log.w(TAG, "friends refresh failed", it) }
        runCatching {
            feed.value = api.feed().items.map {
                FeedItem(it.id, it.userId, it.displayName, it.title, it.points, Instant.parse(it.timestamp).toEpochMilli())
            }
        }.onFailure { Log.w(TAG, "feed refresh failed", it) }
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

    override suspend fun accept(requestId: String): String? = run("accept") { api.acceptFriend(requestId) }

    override suspend fun decline(requestId: String): String? = run("decline") { api.declineFriend(requestId) }

    override suspend fun unfriend(userId: String): String? = run("unfriend") { api.unfriend(userId) }

    /** Runs a friend action, always refreshes after, and returns null on success or a short reason. */
    private suspend inline fun run(action: String, block: () -> Unit): String? {
        val error = runCatching { block() }.exceptionOrNull()
        refresh()
        if (error != null) Log.w(TAG, "$action failed", error)
        return error?.let(::reasonFor)
    }

    private fun reasonFor(e: Throwable): String = when {
        e is ApiFailure -> "server error ${e.status}"
        e is java.net.UnknownHostException || e is java.net.ConnectException -> "can't reach the server"
        // Ktor's HttpRequestTimeoutException (matched by name to avoid a ktor dependency here).
        e is java.net.SocketTimeoutException || e.javaClass.simpleName.contains("Timeout") ->
            "the server timed out — it may be waking up, try again"
        else -> e.message?.take(80) ?: e.javaClass.simpleName
    }

    private companion object { const val TAG = "QuestlyFriends" }
}
