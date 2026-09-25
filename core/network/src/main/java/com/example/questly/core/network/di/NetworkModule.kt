package com.example.questly.core.network.di

import com.example.questly.core.network.OverpassClient
import com.example.questly.core.network.BackendCheckpointClient
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkModule {
    @Binds abstract fun overpassClient(impl: BackendCheckpointClient): OverpassClient
}
