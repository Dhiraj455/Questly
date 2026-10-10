package com.example.questly.feature.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.questly.core.data.EventResult
import com.example.questly.core.model.Event
import com.example.questly.core.model.EventCategory
import com.example.questly.core.model.EventInput
import com.example.questly.core.model.EventRegistration
import com.example.questly.core.model.EventStatus
import com.example.questly.core.model.EventVisibility
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.roundToInt

/**
 * Create (when [existing] is null) or edit an event. Default coordinates come from the caller's
 * current location. On a successful save [onSaved] fires; validation and server errors go to
 * [onShowMessage].
 */
@Composable
fun EventEditScreen(
    existing: Event?,
    defaultLat: Double?,
    defaultLng: Double?,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onShowMessage: (String) -> Unit,
    viewModel: EventEditViewModel = hiltViewModel(),
) {
    val submitting by viewModel.submitting.collectAsStateWithLifecycle()

    var title by remember { mutableStateOf(existing?.title ?: "") }
    var description by remember { mutableStateOf(existing?.description ?: "") }
    var category by remember { mutableStateOf(existing?.category ?: EventCategory.PARK) }
    var venue by remember { mutableStateOf(existing?.venueName ?: "") }
    var lat by remember { mutableStateOf((existing?.lat ?: defaultLat)?.toString() ?: "") }
    var lng by remember { mutableStateOf((existing?.lng ?: defaultLng)?.toString() ?: "") }
    var startMillis by remember {
        mutableLongStateOf(existing?.startsAtMillis ?: (System.currentTimeMillis() + 86_400_000L))
    }
    var capacity by remember { mutableStateOf(existing?.capacity?.toString() ?: "") }
    var visibility by remember { mutableStateOf(existing?.visibility ?: EventVisibility.PUBLIC) }
    var registration by remember { mutableStateOf(existing?.registration ?: EventRegistration.NONE) }
    var price by remember { mutableStateOf(existing?.priceCents?.let { "%.2f".format(it / 100.0) } ?: "") }
    var currency by remember { mutableStateOf(existing?.currency ?: "USD") }
    var publish by remember { mutableStateOf((existing?.status ?: EventStatus.PUBLISHED) != EventStatus.DRAFT) }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                if (existing == null) "Create event" else "Edit event",
                style = MaterialTheme.typography.titleLarge,
            )
        }

        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )

            FieldLabel("Category")
            ChipRow(EventCategory.entries, category, ::categoryLabel) { category = it }

            OutlinedTextField(
                value = venue,
                onValueChange = { venue = it },
                label = { Text("Venue name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            FieldLabel("When")
            StartTimeField(startMillis) { startMillis = it }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = lat,
                    onValueChange = { lat = it },
                    label = { Text("Latitude") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = lng,
                    onValueChange = { lng = it },
                    label = { Text("Longitude") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
            }

            OutlinedTextField(
                value = capacity,
                onValueChange = { capacity = it.filter(Char::isDigit) },
                label = { Text("Capacity (optional)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            FieldLabel("Who can see it")
            ChipRow(EventVisibility.entries, visibility, ::visibilityText) { visibility = it }

            FieldLabel("Registration")
            ChipRow(EventRegistration.entries, registration, ::registrationText) { registration = it }

            if (registration == EventRegistration.PAID) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = price,
                        onValueChange = { price = it },
                        label = { Text("Price") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(2f),
                    )
                    OutlinedTextField(
                        value = currency,
                        onValueChange = { currency = it.uppercase().take(3) },
                        label = { Text("Currency") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Publish now", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Switch(checked = publish, onCheckedChange = { publish = it })
            }
            if (!publish) {
                Text(
                    "Saved as a draft — only you can see it until you publish.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(4.dp))
            Button(
                onClick = {
                    val input = validate(
                        title, description, category, venue, lat, lng, startMillis, capacity,
                        visibility, registration, price, currency, publish, onShowMessage,
                    ) ?: return@Button
                    viewModel.save(existing?.id, input) { result ->
                        when (result) {
                            is EventResult.Success -> onSaved()
                            is EventResult.Error -> onShowMessage(result.message)
                        }
                    }
                },
                enabled = !submitting,
                modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
            ) {
                if (submitting) {
                    CircularProgressIndicator(Modifier.height(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(if (existing == null) "Create event" else "Save changes")
                }
            }
        }
    }
}

/** Validates the form, returning an [EventInput] or null after reporting the first problem. */
private fun validate(
    title: String,
    description: String,
    category: EventCategory,
    venue: String,
    lat: String,
    lng: String,
    startMillis: Long,
    capacity: String,
    visibility: EventVisibility,
    registration: EventRegistration,
    price: String,
    currency: String,
    publish: Boolean,
    onError: (String) -> Unit,
): EventInput? {
    if (title.isBlank()) { onError("Give your event a title"); return null }
    val latValue = lat.toDoubleOrNull()
    val lngValue = lng.toDoubleOrNull()
    if (latValue == null || lngValue == null || latValue !in -90.0..90.0 || lngValue !in -180.0..180.0) {
        onError("Enter a valid location (lat/lng)"); return null
    }
    var priceCents: Int? = null
    var currencyCode: String? = null
    if (registration == EventRegistration.PAID) {
        val amount = price.toDoubleOrNull()
        if (amount == null || amount <= 0.0) { onError("Enter a ticket price"); return null }
        if (currency.length != 3) { onError("Enter a 3-letter currency, e.g. USD"); return null }
        priceCents = (amount * 100).roundToInt()
        currencyCode = currency.uppercase()
    }
    return EventInput(
        title = title,
        description = description,
        category = category,
        venueName = venue,
        lat = latValue,
        lng = lngValue,
        startsAtMillis = startMillis,
        endsAtMillis = null,
        capacity = capacity.toIntOrNull()?.takeIf { it > 0 },
        visibility = visibility,
        registration = registration,
        priceCents = priceCents,
        currency = currencyCode,
        status = if (publish) EventStatus.PUBLISHED else EventStatus.DRAFT,
    )
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChipRow(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(label(option)) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StartTimeField(millis: Long, onChange: (Long) -> Unit) {
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var pendingDate by remember { mutableLongStateOf(millis) }

    OutlinedButton(onClick = { showDate = true }, modifier = Modifier.fillMaxWidth()) {
        Text(dateTimeLabel(millis))
    }

    if (showDate) {
        val dateState = rememberDatePickerState(initialSelectedDateMillis = millis)
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    pendingDate = dateState.selectedDateMillis ?: millis
                    showDate = false
                    showTime = true
                }) { Text("Next") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Cancel") } },
        ) { DatePicker(state = dateState) }
    }

    if (showTime) {
        val current = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
        val timeState = rememberTimePickerState(initialHour = current.hour, initialMinute = current.minute)
        AlertDialog(
            onDismissRequest = { showTime = false },
            confirmButton = {
                TextButton(onClick = {
                    val date = Instant.ofEpochMilli(pendingDate).atZone(ZoneOffset.UTC).toLocalDate()
                    val combined = date.atTime(LocalTime.of(timeState.hour, timeState.minute))
                        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    onChange(combined)
                    showTime = false
                }) { Text("Done") }
            },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text("Cancel") } },
            text = { TimePicker(state = timeState) },
        )
    }
}

private fun visibilityText(v: EventVisibility): String = when (v) {
    EventVisibility.PUBLIC -> "Public"
    EventVisibility.FRIENDS -> "Friends"
    EventVisibility.PRIVATE -> "Private"
}

private fun registrationText(r: EventRegistration): String = when (r) {
    EventRegistration.NONE -> "None"
    EventRegistration.FREE -> "Free RSVP"
    EventRegistration.PAID -> "Paid"
}
