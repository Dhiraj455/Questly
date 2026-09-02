package com.example.questly.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [CheckpointEntity::class, CheckInEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class QuestlyDatabase : RoomDatabase() {
    abstract fun checkpointDao(): CheckpointDao
    abstract fun checkInDao(): CheckInDao
}
