package com.example.questly.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "checkpoints")
data class CheckpointEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val lat: Double,
    val lng: Double,
    val radiusMeters: Double,
    val points: Int,
    val kind: String,
)
