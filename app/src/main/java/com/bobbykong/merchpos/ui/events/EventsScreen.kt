package com.bobbykong.merchpos.ui.events

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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

@Composable
fun EventsScreen(
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    viewModel: EventsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val events by viewModel.events.collectAsStateWithLifecycle()
    val openEvent by viewModel.openEvent.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    var confirmEnd by remember { mutableStateOf(false) }

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
            if (open == null) {
                StartEventCard(onStart = viewModel::start)
            } else {
                Card(Modifier.fillMaxWidth()) {
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
            )
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
}
