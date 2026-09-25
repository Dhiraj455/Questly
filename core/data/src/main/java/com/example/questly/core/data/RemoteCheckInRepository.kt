package com.example.questly.core.data

import com.example.questly.core.model.CheckIn
import com.example.questly.core.network.CheckInRejectedException
import com.example.questly.core.network.QuestlyApi
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
class RemoteCheckInRepository @Inject constructor(private val api: QuestlyApi) : CheckInRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val history = MutableStateFlow<List<CheckIn>>(emptyList())
    private val total = MutableStateFlow(0)
    init { scope.launch { runCatching { reload() } } }
    override fun observeCheckIns() = history.asStateFlow()
    override fun observePoints() = total.asStateFlow()
    override suspend fun recordCheckIn(checkpointId: String, userLat: Double, userLng: Double, nowMillis: Long): CheckInResult = try {
        api.checkIn(checkpointId, userLat, userLng, Instant.ofEpochMilli(nowMillis).toString(), UUID.randomUUID().toString())
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
