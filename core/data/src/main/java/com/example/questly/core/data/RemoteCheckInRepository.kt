package com.example.questly.core.data

import com.example.questly.core.database.CheckpointDao
import com.example.questly.core.model.CheckIn
import com.example.questly.core.network.CheckInRejectedException
import com.example.questly.core.network.QuestlyApi
import com.example.questly.core.network.TokenStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RemoteCheckInRepository @Inject constructor(
    private val api: QuestlyApi,
    private val checkpointDao: CheckpointDao,
    private val tokens: TokenStore,
) : CheckInRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val history = MutableStateFlow<List<CheckIn>>(emptyList())
    private val total = MutableStateFlow(0)
    init {
        // Reload for whoever is signed in; wipe the cache on sign-out so the next account never
        // sees the previous user's history/points.
        scope.launch {
            tokens.signedIn.collect { signedIn ->
                if (signedIn) runCatching { reload() }
                else { history.value = emptyList(); total.value = 0 }
            }
        }
    }
    override fun observeCheckIns() = history.asStateFlow()
    override fun observePoints() = total.asStateFlow()
    override suspend fun recordCheckIn(checkpointId: String, userLat: Double, userLng: Double, nowMillis: Long): CheckInResult = try {
        // The backend can't reach Overpass, so it validates against the checkpoint we send. Pull it
        // from the local cache (populated by discovery); the server still enforces distance/cooldown
        // and recomputes points from the category, so this can't be used to forge a reward.
        val cp = checkpointDao.getById(checkpointId) ?: return CheckInResult.UnknownCheckpoint
        api.checkIn(
            id = checkpointId,
            userLat = userLat,
            userLng = userLng,
            time = Instant.ofEpochMilli(nowMillis).toString(),
            key = UUID.randomUUID().toString(),
            checkpointLat = cp.lat,
            checkpointLng = cp.lng,
            category = cp.category,
            title = cp.title,
        )
        reload(); CheckInResult.Success
    } catch (e: CheckInRejectedException) {
        when (e.reason) {
            "ON_COOLDOWN" -> CheckInResult.OnCooldown
            "TOO_FAR" -> CheckInResult.TooFar
            "UNKNOWN_CHECKPOINT" -> CheckInResult.UnknownCheckpoint
            else -> CheckInResult.NetworkError // IMPLAUSIBLE or anything unexpected
        }
    } catch (_: Exception) {
        // Any HTTP/IO/decoding failure — never let it disappear in a ViewModel coroutine.
        CheckInResult.NetworkError
    }
    suspend fun reload() { history.value = api.history().items.map { CheckIn(it.id, it.checkpointId, Instant.parse(it.timestamp).toEpochMilli(), it.title, it.points) }; total.value = api.points().total }
}
