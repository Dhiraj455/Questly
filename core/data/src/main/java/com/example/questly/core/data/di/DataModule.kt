package com.example.questly.core.data.di

import android.content.Context
import androidx.room.Room
import com.example.questly.core.data.CheckInRepository
import com.example.questly.core.data.CheckpointRepository
import com.example.questly.core.data.LocalCheckInRepository
import com.example.questly.core.data.LocalCheckpointRepository
import com.example.questly.core.database.CheckInDao
import com.example.questly.core.database.CheckpointDao
import com.example.questly.core.database.QuestlyDatabase
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataBindingsModule {
    @Binds abstract fun checkpointRepo(impl: LocalCheckpointRepository): CheckpointRepository
    @Binds abstract fun checkInRepo(impl: LocalCheckInRepository): CheckInRepository
}

@Module
@InstallIn(SingletonComponent::class)
object DataProvidesModule {
    @Provides @Singleton
    fun database(@ApplicationContext ctx: Context): QuestlyDatabase =
        Room.databaseBuilder(ctx, QuestlyDatabase::class.java, "questly.db").build()

    @Provides fun checkpointDao(db: QuestlyDatabase): CheckpointDao = db.checkpointDao()
    @Provides fun checkInDao(db: QuestlyDatabase): CheckInDao = db.checkInDao()
}
