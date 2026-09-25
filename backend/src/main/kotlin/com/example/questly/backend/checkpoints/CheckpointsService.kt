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
    private data class Single(val fetchedAt: Long, val checkpoint: CheckpointDto)

    private val cache = ConcurrentHashMap<String, Entry>()
    // Per-checkpoint cache so check-in validation reuses what a recent /checkpoints call already
    // fetched, instead of a fresh (slow, rate-limited) Overpass round-trip per check-in.
    private val byId = ConcurrentHashMap<String, Single>()

    /** Cache key rounds the point so nearby requests share a result. */
    private fun keyFor(lat: Double, lng: Double, radiusMeters: Double): String {
        fun round4(v: Double) = (v * 10_000).roundToInt()
        return "${round4(lat)}:${round4(lng)}:${radiusMeters.toInt()}"
    }

    /**
     * Resolves a single checkpoint for check-in validation: from the per-id cache when a recent
     * /checkpoints call saw it (the normal path), else one targeted Overpass lookup. Returns null if
     * the id doesn't exist; propagates IOException if Overpass is unreachable on a cache miss.
     */
    suspend fun resolve(id: String): CheckpointDto? {
        byId[id]?.takeIf { System.currentTimeMillis() - it.fetchedAt < CACHE_TTL_MILLIS }?.let { return it.checkpoint }
        val fetched = overpass.findById(id) ?: return null
        byId[id] = Single(System.currentTimeMillis(), fetched)
        return fetched
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
            val stampedAt = System.currentTimeMillis()
            fetched.forEach { byId[it.id] = Single(stampedAt, it) } // warm the per-id cache for check-ins
            fetched
        }

        return if (types.isEmpty()) withinRadius else withinRadius.filter { it.category in types }
    }
}
