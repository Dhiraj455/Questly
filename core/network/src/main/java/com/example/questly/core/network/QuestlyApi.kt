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
import io.ktor.client.request.bearerAuth
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
    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
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
    suspend fun checkpoints(lat: Double, lng: Double, radius: Double): List<CheckpointDto> = authorizedGet("/checkpoints?lat=$lat&lng=$lng&radiusMeters=$radius").body()
    suspend fun history(): CheckInPage = authorizedGet("/checkins").body()
    suspend fun points(): PointsDto = authorizedGet("/points").body()
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
@Singleton
class BackendCheckpointClient @Inject constructor(private val api: QuestlyApi) : OverpassClient {
    override suspend fun query(lat: Double, lng: Double, radiusMeters: Double): List<OverpassPoi> =
        api.checkpoints(lat, lng, radiusMeters).map {
            OverpassPoi(it.id, it.lat, it.lng, it.title, PoiKind.valueOf(it.category))
        }
}
@Serializable data class RegisterRequest(val email: String, val password: String, val displayName: String)
@Serializable data class LoginRequest(val email: String, val password: String)
@Serializable data class GoogleSignInRequest(val idToken: String)
@Serializable data class RefreshRequest(val refreshToken: String)
@Serializable data class TokenPair(val accessToken: String, val refreshToken: String, val expiresInSeconds: Long)
@Serializable data class CheckpointDto(val id: String, val title: String, val description: String = "", val lat: Double, val lng: Double, val radiusMeters: Double, val points: Int, val category: String)
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
@Serializable data class CheckInRejectionResponse(val reason: String, val message: String = "", val retryAfterSeconds: Int? = null)
