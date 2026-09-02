package com.example.questly.core.location.di

import com.example.questly.core.location.FusedLocationProviderImpl
import com.example.questly.core.location.LocationProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class LocationModule {
    @Binds abstract fun locationProvider(impl: FusedLocationProviderImpl): LocationProvider
}
