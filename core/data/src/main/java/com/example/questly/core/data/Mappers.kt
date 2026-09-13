package com.example.questly.core.data

import com.example.questly.core.database.CheckInEntity
import com.example.questly.core.database.CheckpointEntity
import com.example.questly.core.model.CheckIn
import com.example.questly.core.model.Checkpoint
import com.example.questly.core.model.CheckpointKind
import com.example.questly.core.network.OverpassPoi
import com.example.questly.core.network.PoiKind

fun CheckpointEntity.toModel() =
    Checkpoint(id, title, description, lat, lng, radiusMeters, points, CheckpointKind.valueOf(kind), category)

fun CheckInEntity.toModel() = CheckIn(id, checkpointId, timestampMillis, title, points)

// Fixed check-in radius for every quest sourced from a POI point.
private const val CHECK_IN_RADIUS_M = 150.0

fun pointsFor(kind: PoiKind): Int = when (kind) {
    PoiKind.PARK -> 50
    PoiKind.BEACH -> 75
    PoiKind.VIEWPOINT -> 60
    PoiKind.LANDMARK -> 60
}

private fun descriptionFor(kind: PoiKind): String = when (kind) {
    PoiKind.PARK -> "Explore this park to earn points."
    PoiKind.BEACH -> "Visit this beach to earn points."
    PoiKind.VIEWPOINT -> "Take in this viewpoint to earn points."
    PoiKind.LANDMARK -> "Visit this landmark to earn points."
}

fun OverpassPoi.toCheckpointEntity() = CheckpointEntity(
    id = id,
    title = name,
    description = descriptionFor(kind),
    lat = lat,
    lng = lng,
    radiusMeters = CHECK_IN_RADIUS_M,
    points = pointsFor(kind),
    kind = CheckpointKind.CHALLENGE.name,
    category = kind.name, // PARK / BEACH / VIEWPOINT / LANDMARK
)
