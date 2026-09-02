package com.example.questly.core.data

import com.example.questly.core.database.CheckInEntity
import com.example.questly.core.database.CheckpointEntity
import com.example.questly.core.model.CheckIn
import com.example.questly.core.model.Checkpoint
import com.example.questly.core.model.CheckpointKind

fun CheckpointEntity.toModel() =
    Checkpoint(id, title, description, lat, lng, radiusMeters, points, CheckpointKind.valueOf(kind))

fun CheckInEntity.toModel() = CheckIn(id, checkpointId, timestampMillis)
