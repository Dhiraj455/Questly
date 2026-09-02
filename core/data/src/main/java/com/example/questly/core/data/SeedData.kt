package com.example.questly.core.data

import com.example.questly.core.database.CheckpointEntity

// Real London park coordinates + one dummy event. Kept short; a real feed replaces this later.
val SEED_CHECKPOINTS = listOf(
    CheckpointEntity("hyde-park", "Hyde Park", "Check in at Hyde Park", 51.5073, -0.1657, 150.0, 50, "CHALLENGE"),
    CheckpointEntity("regents-park", "Regent's Park", "Check in at Regent's Park", 51.5313, -0.1570, 150.0, 50, "CHALLENGE"),
    CheckpointEntity("greenwich-park", "Greenwich Park", "Check in at Greenwich Park", 51.4769, 0.0005, 150.0, 60, "CHALLENGE"),
    CheckpointEntity("summer-fest", "Summer Fest (demo)", "Live music event", 51.5033, -0.1195, 120.0, 100, "EVENT"),
)
