package com.example.questly.feature.discover.di

import com.example.questly.feature.discover.Event
import com.example.questly.feature.discover.dummyEvents
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object DiscoverModule {
    // Dummy event feed for now; swap for a real repository when events become live.
    @Provides fun events(): List<Event> = dummyEvents()
}
