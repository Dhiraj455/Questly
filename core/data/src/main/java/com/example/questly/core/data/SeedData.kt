package com.example.questly.core.data

import com.example.questly.core.database.CheckpointEntity

// Single seeded visit point for now. Real park/place data replaces this later.
// North Avenue Beach, Chicago.
val SEED_CHECKPOINTS = listOf(
    CheckpointEntity(
        id = "bollywood-beach",
        title = "Bollywood Beach Party",
        description = "A Bollywood party at North Avenue Beach, Chicago",
        lat = 41.9109,
        lng = -87.6267,
        radiusMeters = 150.0,
        points = 100,
        kind = "EVENT",
    ),
)
