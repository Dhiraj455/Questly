package com.example.questly.core.network.di

import com.example.questly.core.network.OverpassClient
import com.example.questly.core.network.OverpassClientImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkModule {
    // Discovery hits Overpass directly from the device. Public Overpass servers block the backend's
    // datacenter IP, but a phone's network reaches them fine — so the app fetches quests itself and
    // uses the backend only for auth, check-in validation and points.
    @Binds abstract fun overpassClient(impl: OverpassClientImpl): OverpassClient
}
