package com.example.questly.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class GeoTest {
    @Test fun samePointIsZero() {
        assertEquals(0.0, distanceMeters(51.5, -0.12, 51.5, -0.12), 0.5)
    }

    @Test fun knownDistanceIsApproximatelyCorrect() {
        // ~111.2 km per degree of latitude
        val d = distanceMeters(0.0, 0.0, 1.0, 0.0)
        assertEquals(111_195.0, d, 500.0)
    }
}
