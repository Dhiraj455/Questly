package com.example.questly.backend.checkpoints

import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

private const val MAX_QUESTS = 150
private const val CACHE_TTL_MILLIS = 10 * 60 * 1000L // 10 minutes

/**
 * Serves nearby checkpoints from Overpass with a small in-memory cache, so repeat queries near the
 * same spot don't re-hit Overpass (respecting its usage policy and rate limits).
 *
 * ponytail: cache is per-instance and in-memory. A shared/persistent cache (the checkpoint_cache
 * table in the v3 plan) is the upgrade when there's more than one server instance.
 */
class CheckpointsService(private val overpass: OverpassClient) {
    private data class Entry(val fetchedAt: Long, val checkpoints: List<CheckpointDto>)

    private val cache = ConcurrentHashMap<String, Entry>()

    /** Cache key rounds the point so nearby requests share a result. */
    private fun keyFor(lat: Double, lng: Double, radiusMeters: Double): String {
        fun round4(v: Double) = (v * 10_000).roundToInt()
        return "${round4(lat)}:${round4(lng)}:${radiusMeters.toInt()}"
    }

    /**
     * Returns checkpoints whose centre is truly within [radiusMeters], nearest first, optionally
     * filtered to [types] (category names). Throws if Overpass fails and there's no cached result.
     */
    suspend fun nearby(
        lat: Double,
        lng: Double,
        radiusMeters: Double,
        types: Set<String>,
    ): List<CheckpointDto> {
        val key = keyFor(lat, lng, radiusMeters)
        val cached = cache[key]?.takeIf { System.currentTimeMillis() - it.fetchedAt < CACHE_TTL_MILLIS }

        val withinRadius = cached?.checkpoints ?: run {
            val fetched = overpass.query(lat, lng, radiusMeters)
                // Overpass 'around' matches large features by geometry; keep only those whose centre
                // is genuinely within the radius, nearest first, capped.
                .map { it to distanceMeters(lat, lng, it.lat, it.lng) }
                .filter { (_, d) -> d <= radiusMeters }
                .sortedBy { (_, d) -> d }
                .take(MAX_QUESTS)
                .map { (cp, _) -> cp }
            cache[key] = Entry(System.currentTimeMillis(), fetched)
            fetched
        }

        return if (types.isEmpty()) withinRadius else withinRadius.filter { it.category in types }
    }
}
