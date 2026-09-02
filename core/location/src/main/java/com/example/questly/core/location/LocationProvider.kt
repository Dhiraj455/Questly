package com.example.questly.core.location

import kotlinx.coroutines.flow.Flow

data class UserLocation(val lat: Double, val lng: Double)

interface LocationProvider {
    /** Emits the latest fix, or null before the first fix. Caller must hold location permission. */
    fun observeLocation(): Flow<UserLocation?>
}
