package com.example.questly.feature.discover

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.BeachAccess
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Park
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.questly.core.model.Event
import com.example.questly.core.model.EventCategory
import com.example.questly.core.model.EventRegistration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** Two-stop gradient used for an event card's banner, keyed by category. */
fun categoryGradient(category: EventCategory): List<Color> = when (category) {
    EventCategory.PARK -> listOf(Color(0xFF11998E), Color(0xFF38EF7D))
    EventCategory.BEACH -> listOf(Color(0xFF2193B0), Color(0xFF6DD5ED))
    EventCategory.VIEWPOINT -> listOf(Color(0xFF654EA3), Color(0xFFEAAFC8))
    EventCategory.LANDMARK -> listOf(Color(0xFFF7971E), Color(0xFFFFD200))
}

fun categoryIcon(category: EventCategory): ImageVector = when (category) {
    EventCategory.PARK -> Icons.Filled.Park
    EventCategory.BEACH -> Icons.Filled.BeachAccess
    EventCategory.VIEWPOINT -> Icons.Filled.Landscape
    EventCategory.LANDMARK -> Icons.Filled.AccountBalance
}

fun categoryLabel(category: EventCategory): String = when (category) {
    EventCategory.PARK -> "Park"
    EventCategory.BEACH -> "Beach"
    EventCategory.VIEWPOINT -> "Viewpoint"
    EventCategory.LANDMARK -> "Landmark"
}

fun distanceLabel(meters: Double?): String = when {
    meters == null -> ""
    meters < 1000 -> "${meters.roundToInt()} m away"
    else -> "%.1f km away".format(meters / 1000)
}

private val DATE_TIME = DateTimeFormatter.ofPattern("EEE, MMM d · h:mm a", Locale.getDefault())

/** A friendly local date-time for an event start, e.g. "Sat, Oct 11 · 6:00 PM". */
fun dateTimeLabel(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(DATE_TIME)

/** Price in major units with currency, e.g. "$15.00" for 1500/USD, or a plain amount for others. */
fun priceLabel(priceCents: Int?, currency: String?): String {
    if (priceCents == null) return ""
    val amount = priceCents / 100.0
    val symbol = when (currency?.uppercase()) {
        "USD" -> "$"
        "EUR" -> "€"
        "GBP" -> "£"
        "INR" -> "₹"
        else -> ""
    }
    return if (symbol.isNotEmpty()) "%s%.2f".format(symbol, amount)
    else "%.2f %s".format(amount, currency ?: "")
}

/** Short banner badge for a Discover card: the viewer's relationship to the event, else its category. */
fun cardBadge(event: Event): String = when {
    event.isHost -> "Hosting"
    event.viewerStatus == com.example.questly.core.model.RegistrationStatus.WAITLISTED -> "Waitlisted"
    event.isGoing -> "Going"
    else -> categoryLabel(event.category)
}

/** The call-to-action label for an event's registration type. */
fun registrationLabel(event: Event): String = when (event.registration) {
    EventRegistration.NONE -> "No registration needed"
    EventRegistration.FREE -> "Register — free"
    EventRegistration.PAID -> "Get ticket · ${priceLabel(event.priceCents, event.currency)}"
}
