package com.example.questly.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "checkins")
data class CheckInEntity(
    @PrimaryKey val id: String,
    val checkpointId: String,
    val timestampMillis: Long,
    val title: String = "", // snapshotted from the checkpoint at check-in time
    val points: Int = 0, // snapshotted so a later cache refresh can't change past earnings
)
