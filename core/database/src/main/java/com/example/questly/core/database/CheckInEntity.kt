package com.example.questly.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "checkins")
data class CheckInEntity(
    @PrimaryKey val id: String,
    val checkpointId: String,
    val timestampMillis: Long,
)
