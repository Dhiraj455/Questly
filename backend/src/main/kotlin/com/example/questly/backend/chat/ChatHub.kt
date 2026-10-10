package com.example.questly.backend.chat

import kotlinx.coroutines.channels.Channel
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks live WebSocket subscribers per user and fans message payloads out to them. Each connection
 * registers a [Channel]; the WS route drains it to the socket. Delivery is best-effort and in-memory —
 * offline users (no live channel) are reached via FCM instead, and a single-process deploy is assumed
 * (a multi-node deploy would need a shared pub/sub, noted for later scale work).
 */
class ChatHub {
    private val subscribers = ConcurrentHashMap<UUID, MutableSet<Channel<String>>>()

    fun register(userId: UUID): Channel<String> {
        val channel = Channel<String>(capacity = 64)
        subscribers.compute(userId) { _, set ->
            (set ?: ConcurrentHashMap.newKeySet()).apply { add(channel) }
        }
        return channel
    }

    fun unregister(userId: UUID, channel: Channel<String>) {
        subscribers.computeIfPresent(userId) { _, set ->
            set.remove(channel)
            if (set.isEmpty()) null else set
        }
        channel.close()
    }

    /** True if the user has at least one live connection. */
    fun isOnline(userId: UUID): Boolean = subscribers[userId]?.isNotEmpty() == true

    /** Fan a JSON payload out to every live connection of the given users. */
    fun deliver(userIds: Collection<UUID>, payload: String) {
        for (userId in userIds) {
            subscribers[userId]?.forEach { it.trySend(payload) }
        }
    }
}
