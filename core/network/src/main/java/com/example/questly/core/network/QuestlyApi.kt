package com.example.questly.core.network

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

// Backend base URL, from BuildConfig (QUESTLY_API_BASE_URL in local.properties). Defaults to the
// Android emulator's host alias (http://10.0.2.2:8081/v1) for local dev.
private val API = BuildConfig.QUESTLY_API_BASE_URL

@Singleton
class TokenStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context, "questly_session",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )
    private val _signedIn = MutableStateFlow(prefs.getString("access", null) != null)
    val signedIn = _signedIn.asStateFlow()
    var accessToken: String? get() = prefs.getString("access", null); set(value) { prefs.edit().putString("access", value).apply(); _signedIn.value = value != null }
    var refreshToken: String? get() = prefs.getString("refresh", null); set(value) { prefs.edit().putString("refresh", value).apply() }
    fun save(pair: TokenPair) {
        prefs.edit().putString("access", pair.accessToken).putString("refresh", pair.refreshToken).apply()
        _signedIn.value = true
    }
    fun clear() { prefs.edit().clear().apply(); _signedIn.value = false }
}

@Singleton
class QuestlyApi @Inject constructor(private val tokens: TokenStore) {
    private val json = Json { ignoreUnknownKeys = true }
    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) { json(json) }
        install(WebSockets)
        // Tolerate free-tier cold starts and the backend's own Overpass round-trip.
        install(HttpTimeout) {
            connectTimeoutMillis = 30_000
            requestTimeoutMillis = 60_000
            socketTimeoutMillis = 60_000
        }
    }
    private val refreshMutex = Mutex()
    private fun url(path: String) = "$API$path"
    private fun io(response: HttpResponse): HttpResponse { if (response.status.value !in 200..299) throw ApiFailure(response.status.value); return response }
    /** Registration only needs to report success/failure; never leak Ktor response types to callers. */
    suspend fun register(email: String, password: String, displayName: String) {
        io(client.post(url("/auth/register")) {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(email, password, displayName))
        })
    }
    suspend fun login(email: String, password: String): TokenPair = io(client.post(url("/auth/login")) { contentType(ContentType.Application.Json); setBody(LoginRequest(email, password)) }).body<TokenPair>().also(tokens::save)
    suspend fun googleSignIn(idToken: String): TokenPair = io(client.post(url("/auth/google")) { contentType(ContentType.Application.Json); setBody(GoogleSignInRequest(idToken)) }).body<TokenPair>().also(tokens::save)
    suspend fun history(): CheckInPage = authorizedGet("/checkins").body()
    suspend fun points(): PointsDto = authorizedGet("/points").body()
    suspend fun me(): UserDto = authorizedGet("/auth/me").body()

    /**
     * Streams live leaderboard snapshots over a WebSocket. Each collection opens one connection and
     * emits a [LeaderboardDto] on connect and on every update; it completes when the socket closes,
     * so callers add their own reconnect (see RemoteLeaderboardRepository).
     */
    fun leaderboardStream(): Flow<LeaderboardDto> = flow {
        val token = tokens.accessToken ?: return@flow
        // http(s)://host/v1 -> ws(s)://host/v1/leaderboard
        val wsUrl = API.replaceFirst("http", "ws") + "/leaderboard"
        client.webSocket(urlString = wsUrl, request = { header("Authorization", "Bearer $token") }) {
            for (frame in incoming) {
                if (frame is Frame.Text) emit(json.decodeFromString<LeaderboardDto>(frame.readText()))
            }
        }
    }
    suspend fun profile(): ProfileDto = authorizedGet("/profile").body()
    suspend fun friendCode(): FriendCodeDto = authorizedGet("/friends/code").body()
    suspend fun friends(): FriendsPageDto = authorizedGet("/friends").body()
    suspend fun feed(): FeedPageDto = authorizedGet("/feed").body()

    /** Sends a friend request by code. Maps the server's 409 reason to [FriendActionException]. */
    suspend fun addFriend(code: String): Unit {
        val response = authorizedPost("/friends/requests") {
            contentType(ContentType.Application.Json)
            setBody(AddFriendRequest(code))
        }
        when {
            response.status.value in 200..299 -> Unit
            response.status.value == 409 -> throw FriendActionException(response.body<ApiErrorBody>().code)
            else -> throw ApiFailure(response.status.value)
        }
    }

    /** Registers this device's FCM token so the backend can push to it. */
    suspend fun registerDevice(fcmToken: String) {
        io(authorizedPost("/devices") {
            contentType(ContentType.Application.Json)
            setBody(DeviceTokenRequest(fcmToken))
        })
    }

    /** Drops an FCM token (on sign-out) so a shared device stops receiving the old user's pushes. */
    suspend fun unregisterDevice(fcmToken: String) {
        val first = client.delete(url("/devices/$fcmToken")) { auth() }
        io(if (first.status.value == 401) { refreshSession(); client.delete(url("/devices/$fcmToken")) { auth() } } else first)
    }

    suspend fun acceptFriend(requestId: String) { io(authorizedPost("/friends/requests/$requestId/accept") {}) }
    suspend fun declineFriend(requestId: String) { io(authorizedPost("/friends/requests/$requestId/decline") {}) }
    suspend fun unfriend(userId: String) {
        val first = client.delete(url("/friends/$userId")) { auth() }
        io(if (first.status.value == 401) { refreshSession(); client.delete(url("/friends/$userId")) { auth() } } else first)
    }
    suspend fun checkIn(
        id: String,
        userLat: Double,
        userLng: Double,
        time: String,
        key: String,
        checkpointLat: Double,
        checkpointLng: Double,
        category: String,
        title: String,
    ): CheckInDto {
        val response = authorizedPost("/checkins") {
            header("Idempotency-Key", key)
            contentType(ContentType.Application.Json)
            setBody(CheckInRequest(id, userLat, userLng, time, checkpointLat, checkpointLng, category, title))
        }
        return when {
            response.status.value in 200..299 -> response.body()
            // 409 carries the rule that rejected the check-in, so the UI can be specific.
            response.status.value == 409 -> throw CheckInRejectedException(response.body<CheckInRejectionResponse>().reason)
            else -> throw ApiFailure(response.status.value)
        }
    }

    /**
     * Best-effort server sign-out: revokes the refresh token server-side, then clears the local
     * session regardless (a network blip must never trap the user in a signed-in state).
     */
    suspend fun signOut() {
        tokens.refreshToken?.let { refresh ->
            runCatching {
                client.post(url("/auth/logout")) {
                    contentType(ContentType.Application.Json)
                    setBody(RefreshRequest(refresh))
                }
            }
        }
        tokens.clear()
    }

    /** Permanently deletes the account on the server, then clears the local session. Throws on failure. */
    suspend fun deleteAccount() {
        val first = client.delete(url("/auth/me")) { auth() }
        val response = if (first.status.value == 401) {
            refreshSession()
            client.delete(url("/auth/me")) { auth() }
        } else {
            first
        }
        io(response)
        tokens.clear()
    }

    /** Replays one protected request after a 401, rotating the persisted token pair if possible. */
    private suspend fun authorizedGet(path: String): HttpResponse {
        val first = client.get(url(path)) { auth() }
        if (first.status.value != 401) return io(first)
        refreshSession()
        return io(client.get(url(path)) { auth() })
    }

    // Returns the raw response (no 2xx check) so callers can read non-2xx bodies (e.g. a 409 reason).
    private suspend fun authorizedPost(
        path: String,
        body: io.ktor.client.request.HttpRequestBuilder.() -> Unit,
    ): HttpResponse {
        val first = client.post(url(path)) { auth(); body() }
        if (first.status.value != 401) return first
        refreshSession()
        return client.post(url(path)) { auth(); body() }
    }

    private suspend fun refreshSession() = refreshMutex.withLock {
        val refresh = tokens.refreshToken ?: throw ApiFailure(401)
        try {
            io(client.post(url("/auth/refresh")) {
                contentType(ContentType.Application.Json)
                setBody(RefreshRequest(refresh))
            }).body<TokenPair>().also(tokens::save)
        } catch (error: ApiFailure) {
            tokens.clear()
            throw error
        }
    }
    private fun io.ktor.client.request.HttpRequestBuilder.auth() { tokens.accessToken?.let(::bearerAuth) ?: throw ApiFailure(401) }
}
class ApiFailure(val status: Int) : RuntimeException("Questly service request failed ($status)")
/** A check-in the server rejected by rule; [reason] is TOO_FAR / ON_COOLDOWN / UNKNOWN_CHECKPOINT / IMPLAUSIBLE. */
class CheckInRejectedException(val reason: String) : RuntimeException("Check-in rejected: $reason")
/** A friend action the server rejected; [reason] is NOT_FOUND / SELF / ALREADY_FRIENDS / ALREADY_REQUESTED. */
class FriendActionException(val reason: String) : RuntimeException("Friend action rejected: $reason")
@Serializable data class RegisterRequest(val email: String, val password: String, val displayName: String)
@Serializable data class LoginRequest(val email: String, val password: String)
@Serializable data class GoogleSignInRequest(val idToken: String)
@Serializable data class RefreshRequest(val refreshToken: String)
@Serializable data class TokenPair(val accessToken: String, val refreshToken: String, val expiresInSeconds: Long)
@Serializable data class UserDto(val id: String, val email: String, val displayName: String, val emailVerified: Boolean = false, val createdAt: String = "")
@Serializable data class CheckInRequest(
    val checkpointId: String,
    val lat: Double,
    val lng: Double,
    val clientTimestamp: String,
    val checkpointLat: Double,
    val checkpointLng: Double,
    val category: String,
    val title: String,
)
@Serializable data class CheckInDto(val id: String, val checkpointId: String, val title: String, val points: Int, val timestamp: String)
@Serializable data class CheckInPage(val items: List<CheckInDto>)
@Serializable data class PointsDto(val total: Int)
@Serializable data class StatsDto(val currentStreakDays: Int, val longestStreakDays: Int, val totalCheckIns: Int, val totalPoints: Int)
@Serializable data class AchievementDto(val id: String, val title: String, val description: String, val target: Int, val value: Int, val earned: Boolean)
@Serializable data class ProfileDto(val stats: StatsDto, val achievements: List<AchievementDto>)
@Serializable data class FriendCodeDto(val code: String)
@Serializable data class AddFriendRequest(val code: String)
@Serializable data class FriendDto(val userId: String, val displayName: String, val totalPoints: Int)
@Serializable data class FriendRequestDto(val id: String, val requesterId: String, val requesterDisplayName: String, val createdAt: String = "")
@Serializable data class FriendsPageDto(val friends: List<FriendDto>, val incomingRequests: List<FriendRequestDto>)
@Serializable data class FeedItemDto(val id: String, val userId: String, val displayName: String, val title: String, val points: Int, val timestamp: String)
@Serializable data class FeedPageDto(val items: List<FeedItemDto>, val nextCursor: String? = null)
@Serializable data class ApiErrorBody(val code: String, val message: String = "")
@Serializable data class LeaderboardEntryDto(val userId: String, val displayName: String, val totalPoints: Int, val rank: Int)
@Serializable data class LeaderboardDto(val entries: List<LeaderboardEntryDto>)
@Serializable data class DeviceTokenRequest(val token: String)
@Serializable data class CheckInRejectionResponse(val reason: String, val message: String = "", val retryAfterSeconds: Int? = null)
