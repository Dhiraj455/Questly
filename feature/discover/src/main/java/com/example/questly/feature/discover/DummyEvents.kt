package com.example.questly.feature.discover

import com.example.questly.feature.discover.EventCategory.ARTS
import com.example.questly.feature.discover.EventCategory.COMMUNITY
import com.example.questly.feature.discover.EventCategory.FOOD
import com.example.questly.feature.discover.EventCategory.MUSIC
import com.example.questly.feature.discover.EventCategory.OUTDOORS
import com.example.questly.feature.discover.EventCategory.SPORTS

/**
 * Deterministic placeholder events spread across distances so all three Discover sections have
 * content at a range of radii. A real feed replaces this later.
 */
fun dummyEvents(): List<Event> = listOf(
    // Close by (< 5 km)
    Event("e1", "Sunset Rooftop Beats", MUSIC, 600.0, recommended = true),
    Event("e2", "Riverside Food Truck Fest", FOOD, 900.0, recommended = true),
    Event("e3", "Morning Park Yoga", OUTDOORS, 1_200.0),
    Event("e4", "Indie Art Walk", ARTS, 1_800.0),
    Event("e5", "Neighborhood 5K Fun Run", SPORTS, 2_400.0),
    Event("e6", "Farmers Market Live", COMMUNITY, 3_100.0),
    Event("e7", "Craft Beer & Vinyl Night", MUSIC, 3_900.0, recommended = true),
    Event("e8", "Street Taco Crawl", FOOD, 4_600.0),

    // Mid range (5–20 km)
    Event("e9", "Lakeside Jazz Evening", MUSIC, 6_200.0, recommended = true),
    Event("e10", "Trailhead Group Hike", OUTDOORS, 7_500.0),
    Event("e11", "Ceramics Pop-up Studio", ARTS, 8_800.0),
    Event("e12", "Sunday League Football", SPORTS, 10_400.0),
    Event("e13", "Community Beach Cleanup", COMMUNITY, 12_100.0, recommended = true),
    Event("e14", "Night Market Bites", FOOD, 14_500.0),
    Event("e15", "Open-Air Cinema", ARTS, 16_900.0),
    Event("e16", "Sunrise Cycling Meetup", SPORTS, 18_700.0),

    // Around you (20–50 km)
    Event("e17", "Vineyard Harvest Festival", FOOD, 22_000.0, recommended = true),
    Event("e18", "Mountain Summit Trek", OUTDOORS, 26_500.0),
    Event("e19", "Regional Music Fair", MUSIC, 31_000.0),
    Event("e20", "Sculpture Garden Tour", ARTS, 35_800.0),
    Event("e21", "Lakeshore Marathon", SPORTS, 41_200.0),
    Event("e22", "Countryside Craft Fair", COMMUNITY, 47_900.0),

    // Beyond 50 km — should never surface (proves the cap)
    Event("e23", "Coastal Surf Classic", SPORTS, 58_000.0),
    Event("e24", "Highland Folk Festival", MUSIC, 72_000.0),
)
