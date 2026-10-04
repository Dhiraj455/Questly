package com.example.questly.core.data

import com.example.questly.core.model.FeedItem
import com.example.questly.core.model.Friend
import com.example.questly.core.model.FriendRequest
import com.example.questly.core.network.FriendActionException
import com.example.questly.core.network.QuestlyApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    suspend fun accept(requestId: String)
    suspend fun decline(requestId: String)
    suspend fun unfriend(userId: String)
}

@Singleton
class RemoteFriendsRepository @Inject constructor(
    private val api: QuestlyApi,
) : FriendsRepository {
    private val friends = MutableStateFlow(FriendsData())
    private val feed = MutableStateFlow<List<FeedItem>>(emptyList())

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

    override suspend fun accept(requestId: String) { runCatching { api.acceptFriend(requestId) }; refresh() }
    override suspend fun decline(requestId: String) { runCatching { api.declineFriend(requestId) }; refresh() }
    override suspend fun unfriend(userId: String) { runCatching { api.unfriend(userId) }; refresh() }
}
