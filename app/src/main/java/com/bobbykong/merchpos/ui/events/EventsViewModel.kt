package com.bobbykong.merchpos.ui.events

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bobbykong.merchpos.data.PosException
import com.bobbykong.merchpos.data.PosRepository
import com.bobbykong.merchpos.data.model.Event
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class EventsViewModel(private val repository: PosRepository) : ViewModel() {
    val events: StateFlow<List<Event>> =
        repository.events.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val openEvent: StateFlow<Event?> =
        repository.openEvent.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun start(name: String) {
        viewModelScope.launch {
            try {
                repository.startEvent(name)
            } catch (e: PosException) {
                _message.value = e.message
            }
        }
    }

    fun end(eventId: Int) {
        viewModelScope.launch { repository.endEvent(eventId) }
    }

    fun messageShown() {
        _message.value = null
    }
}
