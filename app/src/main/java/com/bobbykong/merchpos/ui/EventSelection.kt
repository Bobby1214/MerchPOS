package com.bobbykong.merchpos.ui

import com.bobbykong.merchpos.data.PosRepository
import com.bobbykong.merchpos.data.model.Event
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Which event a screen is showing. Defaults to the newest event until the user picks another. */
class EventSelection(repository: PosRepository, scope: CoroutineScope) {
    private val pickedId = MutableStateFlow<Int?>(null)

    val events: StateFlow<List<Event>> =
        repository.events.stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val selected: StateFlow<Event?> =
        combine(events, pickedId) { list, id -> list.firstOrNull { it.id == id } ?: list.firstOrNull() }
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    fun pick(eventId: Int) {
        pickedId.value = eventId
    }
}
