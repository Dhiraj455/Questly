package com.example.questly.core.data

import android.util.Log
import com.example.questly.core.model.Event
import com.example.questly.core.model.EventInput
import com.example.questly.core.model.EventRoster
import com.example.questly.core.network.ApiFailure
import com.example.questly.core.network.EventActionException
import com.example.questly.core.network.QuestlyApi
import com.example.questly.core.network.TokenStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Outcome of a create/update; carries the saved event on success or a short reason to show. */
sealed interface EventResult {
    data class Success(val event: Event) : EventResult
    data class Error(val message: String) : EventResult
}

/** Outcome of a register/unregister; carries the refreshed event on success or a short reason. */
sealed interface RegistrationOutcome {
    data class Success(val event: Event) : RegistrationOutcome
    data class Error(val message: String) : RegistrationOutcome
}

interface EventsRepository {
    /** Events near the last-queried point (nearest-first). */
    fun observeNearby(): StateFlow<List<Event>>

    /** Events the signed-in user hosts. */
    fun observeMine(): StateFlow<List<Event>>

    /** Refreshes [observeNearby] for a point. Returns null on success or a short reason on failure. */
    suspend fun refreshNearby(lat: Double, lng: Double, radiusKm: Double): String?

    /** Refreshes [observeMine]. Returns null on success or a short reason on failure. */
    suspend fun refreshMine(): String?

    /** Fetches a single event (e.g. for a detail screen); null if unavailable. */
    suspend fun event(id: String): Event?

    suspend fun create(input: EventInput): EventResult
    suspend fun update(id: String, input: EventInput): EventResult

    /** Cancels an event. Returns null on success or a short reason on failure. */
    suspend fun cancel(id: String): String?

    /** RSVPs for a free event, returning the refreshed event (with the new viewer status). */
    suspend fun register(eventId: String): RegistrationOutcome

    /** Cancels the caller's registration, returning the refreshed event. */
    suspend fun unregister(eventId: String): RegistrationOutcome

    /** The host's attendee roster, or null on failure. */
    suspend fun roster(eventId: String): EventRoster?
}

@Singleton
class RemoteEventsRepository @Inject constructor(
    private val api: QuestlyApi,
    tokens: TokenStore,
) : EventsRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val nearby = MutableStateFlow<List<Event>>(emptyList())
    private val mine = MutableStateFlow<List<Event>>(emptyList())

    init {
        // Drop cached events on sign-out so a new account doesn't inherit them.
        scope.launch {
            tokens.signedIn.collect { signedIn ->
                if (!signedIn) {
                    nearby.value = emptyList()
                    mine.value = emptyList()
                }
            }
        }
    }

    override fun observeNearby(): StateFlow<List<Event>> = nearby.asStateFlow()
    override fun observeMine(): StateFlow<List<Event>> = mine.asStateFlow()

    override suspend fun refreshNearby(lat: Double, lng: Double, radiusKm: Double): String? = runCatching {
        nearby.value = api.nearbyEvents(lat, lng, radiusKm, category = null, limit = 200).events.map { it.toModel() }
        lastNearbyQuery = Triple(lat, lng, radiusKm)
        null
    }.getOrElse { reasonFor(it).also { _ -> Log.w(TAG, "nearby refresh failed", it) } }

    override suspend fun refreshMine(): String? = runCatching {
        mine.value = api.myEvents().events.map { it.toModel() }
        null
    }.getOrElse { reasonFor(it).also { _ -> Log.w(TAG, "mine refresh failed", it) } }

    override suspend fun event(id: String): Event? =
        runCatching { api.event(id).toModel() }.onFailure { Log.w(TAG, "event fetch failed", it) }.getOrNull()

    override suspend fun create(input: EventInput): EventResult = write { api.createEvent(input.toRequest()).toModel() }

    override suspend fun update(id: String, input: EventInput): EventResult =
        write { api.updateEvent(id, input.toRequest()).toModel() }

    override suspend fun cancel(id: String): String? {
        val error = runCatching { api.cancelEvent(id) }.exceptionOrNull()
        refreshMine()
        refreshNearbyLast()
        if (error != null) Log.w(TAG, "cancel failed", error)
        return error?.let(::reasonFor)
    }

    override suspend fun register(eventId: String): RegistrationOutcome =
        registrationAction(eventId) { api.registerEvent(eventId) }

    override suspend fun unregister(eventId: String): RegistrationOutcome =
        registrationAction(eventId) { api.unregisterEvent(eventId) }

    override suspend fun roster(eventId: String): EventRoster? =
        runCatching { api.eventRoster(eventId).toModel() }.onFailure { Log.w(TAG, "roster failed", it) }.getOrNull()

    /** Runs a register/unregister, then re-fetches the event and refreshes the lists so badges update. */
    private suspend fun registrationAction(eventId: String, action: suspend () -> Unit): RegistrationOutcome = try {
        action()
        val updated = api.event(eventId).toModel()
        refreshMine()
        refreshNearbyLast()
        RegistrationOutcome.Success(updated)
    } catch (e: Throwable) {
        Log.w(TAG, "registration action failed", e)
        RegistrationOutcome.Error(reasonFor(e))
    }

    private inline fun write(block: () -> Event): EventResult = try {
        val event = block()
        // The new/updated event belongs to the host, so refresh their list in the background.
        scope.launch { refreshMine() }
        EventResult.Success(event)
    } catch (e: Throwable) {
        Log.w(TAG, "event write failed", e)
        EventResult.Error(reasonFor(e))
    }

    private suspend fun refreshNearbyLast() {
        val last = lastNearbyQuery ?: return
        refreshNearby(last.first, last.second, last.third)
    }

    // Remember the last discovery point so a cancel can refresh the same view.
    private var lastNearbyQuery: Triple<Double, Double, Double>? = null

    private fun reasonFor(e: Throwable): String = when {
        e is EventActionException -> when (e.reason) {
            "NOT_HOST" -> "only the host can do that"
            "CANCELLED" -> "this event is cancelled"
            "HOST" -> "you're hosting this event"
            "NOT_OPEN" -> "this event isn't open for registration"
            "NO_REGISTRATION" -> "this event doesn't need registration"
            "PAYMENT_REQUIRED" -> "paid registration is coming soon"
            else -> e.reason
        }
        e is ApiFailure -> "server error ${e.status}"
        e is java.net.UnknownHostException || e is java.net.ConnectException -> "can't reach the server"
        e is java.net.SocketTimeoutException || e.javaClass.simpleName.contains("Timeout") ->
            "the server timed out — it may be waking up, try again"
        else -> e.message?.take(80) ?: e.javaClass.simpleName
    }

    private companion object { const val TAG = "QuestlyEvents" }
}
