package com.example.questly.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverpassQueryTest {

    @Test fun queryRequestsJsonAndAllTypesWithinRadius() {
        val q = buildOverpassQuery(lat = 41.9, lng = -87.6, radiusMeters = 5000.0)
        assertTrue("asks for json", q.contains("[out:json]"))
        assertTrue("park", q.contains("leisure\"=\"park") || q.contains("leisure=park") || q.contains("\"leisure\"=\"park\""))
        assertTrue("beach", q.contains("natural") && q.contains("beach"))
        assertTrue("viewpoint", q.contains("viewpoint"))
        assertTrue("landmark tourism/historic", q.contains("attraction") && q.contains("historic"))
        assertTrue("radius applied around user", q.contains("around:5000") && q.contains("41.9") && q.contains("-87.6"))
    }

    @Test fun parsesNodesAndClassifiesKinds() {
        val json = """
            {"elements":[
              {"type":"node","id":1,"lat":41.90,"lon":-87.60,"tags":{"leisure":"park","name":"Green Park"}},
              {"type":"node","id":2,"lat":41.91,"lon":-87.61,"tags":{"natural":"beach","name":"North Beach"}},
              {"type":"node","id":3,"lat":41.92,"lon":-87.62,"tags":{"tourism":"viewpoint","name":"High View"}},
              {"type":"node","id":4,"lat":41.93,"lon":-87.63,"tags":{"tourism":"attraction","name":"Big Bean"}},
              {"type":"node","id":5,"lat":41.94,"lon":-87.64,"tags":{"historic":"monument","name":"Old Monument"}}
            ]}
        """.trimIndent()
        val pois = parseOverpassJson(json)
        assertEquals(5, pois.size)
        assertEquals(PoiKind.PARK, pois.first { it.name == "Green Park" }.kind)
        assertEquals(PoiKind.BEACH, pois.first { it.name == "North Beach" }.kind)
        assertEquals(PoiKind.VIEWPOINT, pois.first { it.name == "High View" }.kind)
        assertEquals(PoiKind.LANDMARK, pois.first { it.name == "Big Bean" }.kind)
        assertEquals(PoiKind.LANDMARK, pois.first { it.name == "Old Monument" }.kind)
    }

    @Test fun dropsUnnamedElements() {
        val json = """{"elements":[{"type":"node","id":1,"lat":1.0,"lon":2.0,"tags":{"leisure":"park"}}]}"""
        assertTrue(parseOverpassJson(json).isEmpty())
    }

    @Test fun usesWayCenterCoordinates() {
        val json = """
            {"elements":[
              {"type":"way","id":9,"center":{"lat":41.5,"lon":-87.5},"tags":{"leisure":"park","name":"Big Park"}}
            ]}
        """.trimIndent()
        val poi = parseOverpassJson(json).single()
        assertEquals(41.5, poi.lat, 0.0001)
        assertEquals(-87.5, poi.lng, 0.0001)
        assertEquals("way/9", poi.id)
    }

    @Test fun ignoresUnknownTagsGracefully() {
        val json = """{"elements":[{"type":"node","id":1,"lat":1.0,"lon":2.0,"tags":{"amenity":"cafe","name":"Cafe"}}]}"""
        assertTrue(parseOverpassJson(json).isEmpty())
    }
}
