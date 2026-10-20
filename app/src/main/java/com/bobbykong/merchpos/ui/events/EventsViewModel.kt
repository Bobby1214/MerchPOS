package com.bobbykong.merchpos.ui.events

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bobbykong.merchpos.data.PosException
import com.bobbykong.merchpos.data.PosRepository
import com.bobbykong.merchpos.data.files.BackupManager
import com.bobbykong.merchpos.data.model.Event
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PendingDelete(val event: Event, val orderCount: Int)

class EventsViewModel(
    private val repository: PosRepository,
    private val backupManager: BackupManager,
) : ViewModel() {
    val events: StateFlow<List<Event>> =
        repository.events.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val openEvent: StateFlow<Event?> =
        repository.openEvent.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // False until the database answers, so the screen doesn't flash "No event running" at start-up
    val loaded: StateFlow<Boolean> =
        repository.openEvent.map { true }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _pendingDelete = MutableStateFlow<PendingDelete?>(null)
    val pendingDelete: StateFlow<PendingDelete?> = _pendingDelete.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

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

    /** Step 1: count the orders so the confirm dialog can say what will be deleted. */
    fun requestDelete(event: Event) {
        viewModelScope.launch {
            _pendingDelete.value = PendingDelete(event, repository.orderCountForEvent(event.id))
        }
    }

    fun dismissDelete() {
        _pendingDelete.value = null
    }

    fun confirmDelete() {
        val pending = _pendingDelete.value ?: return
        _pendingDelete.value = null
        viewModelScope.launch {
            repository.deleteEvent(pending.event.id)
            _message.value = "Deleted ${pending.event.name}"
        }
    }

    fun exportCsv(uri: Uri) = runFileJob("Sales exported") {
        backupManager.exportCsv(uri, repository.exportRows())
    }

    fun backup(uri: Uri) = runFileJob("Backup saved") { backupManager.backup(uri) }

    fun restore(uri: Uri) = runFileJob(null) {
        backupManager.restore(uri)
        backupManager.restartApp()
    }

    private fun runFileJob(doneMessage: String?, job: suspend () -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            try {
                job()
                doneMessage?.let { _message.value = it }
            } catch (e: PosException) {
                _message.value = e.message
            } catch (e: Exception) {
                _message.value = "Failed: ${e.message}"
            } finally {
                _busy.value = false
            }
        }
    }

    fun messageShown() {
        _message.value = null
    }
}
