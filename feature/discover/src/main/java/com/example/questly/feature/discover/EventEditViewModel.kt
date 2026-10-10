package com.example.questly.feature.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.questly.core.data.EventResult
import com.example.questly.core.data.EventsRepository
import com.example.questly.core.model.EventInput
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Submits a create (when [existingId] is null) or a full update, exposing an in-flight flag. */
@HiltViewModel
class EventEditViewModel @Inject constructor(
    private val repo: EventsRepository,
) : ViewModel() {

    private val _submitting = MutableStateFlow(false)
    val submitting: StateFlow<Boolean> = _submitting.asStateFlow()

    fun save(existingId: String?, input: EventInput, onDone: (EventResult) -> Unit) = viewModelScope.launch {
        _submitting.value = true
        try {
            onDone(if (existingId == null) repo.create(input) else repo.update(existingId, input))
        } finally {
            _submitting.value = false
        }
    }
}
