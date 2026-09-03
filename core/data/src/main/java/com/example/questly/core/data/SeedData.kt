package com.example.questly.core.data

import com.example.questly.core.database.CheckpointEntity
import kotlin.math.cos

// Real London park coordinates + one dummy event. A real feed replaces this later.
val SEED_CHECKPOINTS = listOf(
    CheckpointEntity("hyde-park", "Hyde Park", "Check in at Hyde Park", 51.5073, -0.1657, 150.0, 50, "CHALLENGE"),
    CheckpointEntity("regents-park", "Regent's Park", "Check in at Regent's Park", 51.5313, -0.1570, 150.0, 50, "CHALLENGE"),
    CheckpointEntity("greenwich-park", "Greenwich Park", "Check in at Greenwich Park", 51.4769, 0.0005, 150.0, 60, "CHALLENGE"),
    CheckpointEntity("summer-fest", "Summer Fest (demo)", "Live music event", 51.5033, -0.1195, 120.0, 100, "EVENT"),
)

/**
 * Demo challenges generated around [lat],[lng] so the map is never empty wherever
 * the device/emulator sits. Fixed ids so repeated seeding upserts (no duplicates).
 * The first one is intentionally within check-in range for easy testing.
 */
fun demoCheckpointsNear(lat: Double, lng: Double): List<CheckpointEntity> {
    fun at(northMeters: Double, eastMeters: Double): Pair<Double, Double> {
        val dLat = northMeters / 111_111.0
        val dLng = eastMeters / (111_111.0 * cos(Math.toRadians(lat)))
        return lat + dLat to lng + dLng
    }
    val (aLat, aLng) = at(80.0, 0.0)       // ~80 m away -> inside a 150 m radius
    val (bLat, bLng) = at(400.0, 250.0)    // ~470 m
    val (cLat, cLng) = at(-350.0, 300.0)   // ~460 m
    val (dLat, dLng) = at(900.0, -200.0)   // ~920 m
    return listOf(
        CheckpointEntity("near-1", "Corner Café (demo)", "A cozy café right by you", aLat, aLng, 150.0, 40, "CHALLENGE"),
        CheckpointEntity("near-2", "City Park (demo)", "Green space a short walk away", bLat, bLng, 150.0, 60, "CHALLENGE"),
        CheckpointEntity("near-3", "Live Gig (demo)", "Music event nearby", cLat, cLng, 120.0, 100, "EVENT"),
        CheckpointEntity("near-4", "Riverside Walk (demo)", "Scenic route nearby", dLat, dLng, 150.0, 50, "CHALLENGE"),
    )
}
