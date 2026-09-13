package com.example.questly.core.data

import com.example.questly.core.database.CheckpointDao
import com.example.questly.core.model.Checkpoint
import com.example.questly.core.model.distanceMeters
import com.example.questly.core.network.OverpassClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

// Safety bound on cached/shown quests. Kept well above a typical radius's POI count so the
// RADIUS (not this cap) determines the result set — otherwise widening the radius just returns
// the same nearest-N and the list appears not to change. Only binds in very dense areas.
internal const val MAX_QUESTS = 150

class RemoteCheckpointRepository @Inject constructor(
    private val checkpointDao: CheckpointDao,
    private val overpassClient: OverpassClient,
) : CheckpointRepository {

    override fun observeCheckpoints(): Flow<List<Checkpoint>> =
        checkpointDao.observeAll().map { rows -> rows.map { it.toModel() } }

    override suspend fun refresh(lat: Double, lng: Double, radiusMeters: Double): RefreshResult {
        val pois = try {
            overpassClient.query(lat, lng, radiusMeters)
        } catch (e: Exception) {
            // Keep the cached quests on a network/HTTP blip rather than wiping them.
            return RefreshResult.Error(e.message ?: "Couldn't reach the quest service")
        }
        val entities = pois
            .map { it to distanceMeters(lat, lng, it.lat, it.lng) }
            // Overpass 'around' matches large features (routes, multipolygons) by geometry, but
            // their center can be far away. Keep only quests whose center is truly in radius.
            .filter { (_, d) -> d <= radiusMeters }
            .sortedBy { (_, d) -> d }
            .take(MAX_QUESTS)
            .map { (poi, _) -> poi.toCheckpointEntity() }
        checkpointDao.deleteAll()
        checkpointDao.upsertAll(entities)
        return RefreshResult.Success(entities.size)
    }
}
