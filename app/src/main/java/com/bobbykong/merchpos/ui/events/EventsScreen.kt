package com.bobbykong.merchpos.ui.events

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bobbykong.merchpos.ui.AppViewModelProvider
import com.bobbykong.merchpos.ui.components.StartEventCard
import com.bobbykong.merchpos.util.formatDate
import com.bobbykong.merchpos.util.formatDateTime
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun EventsScreen(
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    viewModel: EventsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val events by viewModel.events.collectAsStateWithLifecycle()
    val openEvent by viewModel.openEvent.collectAsStateWithLifecycle()
    val loaded by viewModel.loaded.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val pendingDelete by viewModel.pendingDelete.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    var confirmEnd by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf(false) }

    // The system "save as" / "open file" screens. The user picks where the file goes (Downloads, Drive...).
    val exportCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let(viewModel::exportCsv)
    }
    val backup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let(viewModel::backup)
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::restore)
    }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.messageShown()
        }
    }

    LazyColumn(
        modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            val open = openEvent
            when {
                !loaded -> Unit
                open == null -> StartEventCard(onStart = viewModel::start)
                else -> Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Now running", style = MaterialTheme.typography.labelLarge)
                        Text(open.name, style = MaterialTheme.typography.titleLarge)
                        Text("Started ${formatDateTime(open.startDate)}")
                        Button(onClick = { confirmEnd = true }) { Text("End event") }
                    }
                }
            }
        }
        item { Text("All events", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
        items(events, key = { it.id }) { event ->
            ListItem(
                headlineContent = { Text(event.name) },
                supportingContent = {
                    Text("${formatDate(event.startDate)} – ${event.endDate?.let(::formatDate) ?: "ongoing"}")
                },
                trailingContent = {
                    IconButton(onClick = { viewModel.requestDelete(event) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete ${event.name}")
                    }
                },
            )
        }
        item {
            Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Data", style = MaterialTheme.typography.titleMedium)
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    OutlinedButton(onClick = { exportCsv.launch("merchpos-sales-${today()}.csv") }, enabled = !busy) {
                        Text("Export sales (CSV)")
                    }
                    OutlinedButton(onClick = { backup.launch("merchpos-backup-${today()}.zip") }, enabled = !busy) {
                        Text("Back up everything (ZIP)")
                    }
                    OutlinedButton(onClick = { confirmRestore = true }, enabled = !busy) {
                        Text("Restore from backup")
                    }
                    Text(
                        "CSV: one row per item sold, for Excel or openGauss. " +
                            "Backup: all events, orders, products and photos. Keep a copy off the phone (Drive/PC) after each event.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    val open = openEvent
    if (confirmEnd && open != null) {
        AlertDialog(
            onDismissRequest = { confirmEnd = false },
            title = { Text("End ${open.name}?") },
            text = { Text("You can't sell under this event after it ends. Its report stays available.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.end(open.id)
                    confirmEnd = false
                }) { Text("End event") }
            },
            dismissButton = {
                TextButton(onClick = { confirmEnd = false }) { Text("Keep running") }
            },
        )
    }

    pendingDelete?.let { pending ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDelete,
            title = { Text("Delete ${pending.event.name}?") },
            text = {
                Text(
                    if (pending.orderCount == 0) {
                        "This event has no orders."
                    } else {
                        "This also deletes its ${pending.orderCount} order(s) and removes them from reports. " +
                            "Stock from completed orders goes back to Products. This can't be undone."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDelete) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDelete) { Text("Keep") }
            },
        )
    }

    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text("Restore from backup?") },
            text = { Text("ALL data on this phone is replaced by the backup. Then the app restarts. Make a backup first if unsure.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRestore = false
                    restore.launch(arrayOf("application/zip", "application/octet-stream"))
                }) { Text("Choose file", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRestore = false }) { Text("Cancel") }
            },
        )
    }
}

private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
