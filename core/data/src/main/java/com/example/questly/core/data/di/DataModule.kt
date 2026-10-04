package com.example.questly.core.data.di

import android.content.Context
import androidx.room.Room
import com.example.questly.core.data.CheckInRepository
import com.example.questly.core.data.CheckpointRepository
import com.example.questly.core.data.FriendsRepository
import com.example.questly.core.data.LeaderboardRepository
import com.example.questly.core.data.ProfileRepository
import com.example.questly.core.data.RemoteCheckInRepository
import com.example.questly.core.data.RemoteCheckpointRepository
import com.example.questly.core.data.RemoteFriendsRepository
import com.example.questly.core.data.RemoteLeaderboardRepository
import com.example.questly.core.data.RemoteProfileRepository
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
    @Binds abstract fun checkpointRepo(impl: RemoteCheckpointRepository): CheckpointRepository
    @Binds abstract fun checkInRepo(impl: RemoteCheckInRepository): CheckInRepository
    @Binds abstract fun profileRepo(impl: RemoteProfileRepository): ProfileRepository
    @Binds abstract fun friendsRepo(impl: RemoteFriendsRepository): FriendsRepository
    @Binds abstract fun leaderboardRepo(impl: RemoteLeaderboardRepository): LeaderboardRepository
}

@Module
@InstallIn(SingletonComponent::class)
object DataProvidesModule {
    @Provides @Singleton
    fun database(@ApplicationContext ctx: Context): QuestlyDatabase =
        // Checkpoints are just a cache of Overpass results, so a schema change can safely wipe
        // and re-fetch rather than carry migrations.
        Room.databaseBuilder(ctx, QuestlyDatabase::class.java, "questly.db")
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides fun checkpointDao(db: QuestlyDatabase): CheckpointDao = db.checkpointDao()
    @Provides fun checkInDao(db: QuestlyDatabase): CheckInDao = db.checkInDao()
}
