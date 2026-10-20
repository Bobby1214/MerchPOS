package com.bobbykong.merchpos.ui.orders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bobbykong.merchpos.data.model.DiscountType
import com.bobbykong.merchpos.data.model.Order
import com.bobbykong.merchpos.data.model.OrderStatus
import com.bobbykong.merchpos.ui.AppViewModelProvider
import com.bobbykong.merchpos.ui.components.EventPicker
import com.bobbykong.merchpos.ui.components.LabelValueRow
import com.bobbykong.merchpos.util.formatDateTime
import com.bobbykong.merchpos.util.formatRm

@Composable
fun OrdersScreen(
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    viewModel: OrdersViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val events by viewModel.eventSelection.events.collectAsStateWithLifecycle()
    val selected by viewModel.eventSelection.selected.collectAsStateWithLifecycle()
    val orders by viewModel.orders.collectAsStateWithLifecycle()
    val editState by viewModel.editState.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    var toCancel by remember { mutableStateOf<Order?>(null) }

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
        item { EventPicker(events, selected, onPick = viewModel.eventSelection::pick) }
        if (orders.isEmpty()) {
            item { Text("No orders for this event yet.", modifier = Modifier.padding(top = 16.dp)) }
        }
        items(orders, key = { it.order.id }) { item ->
            OrderCard(item, onEdit = { viewModel.startEdit(item) }, onCancel = { toCancel = item.order })
        }
    }

    editState?.let { EditOrderDialog(it, viewModel) }

    toCancel?.let { order ->
        AlertDialog(
            onDismissRequest = { toCancel = null },
            title = { Text("Cancel order #${order.id}?") },
            text = { Text("The stock goes back, and this sale won't count in reports. The order is kept as CANCELLED.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.cancel(order.id)
                    toCancel = null
                }) { Text("Cancel order") }
            },
            dismissButton = {
                TextButton(onClick = { toCancel = null }) { Text("Keep") }
            },
        )
    }
}

@Composable
private fun OrderCard(item: OrderWithLines, onEdit: () -> Unit, onCancel: () -> Unit) {
    val order = item.order
    val cancelled = order.status == OrderStatus.CANCELLED
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("#${order.id} · ${order.paymentMethod.label}", style = MaterialTheme.typography.titleMedium)
                    Text(
                        formatDateTime(order.createdAt) + if (order.isEdited) " · edited" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    formatRm(order.totalSen),
                    style = MaterialTheme.typography.titleMedium,
                    textDecoration = if (cancelled) TextDecoration.LineThrough else null,
                )
            }
            item.lines.forEach { line ->
                if (line.isGift) {
                    LabelValueRow("${line.qty} × ${line.productName} (gift)", "FREE")
                } else {
                    LabelValueRow("${line.qty} × ${line.productName}", formatRm(line.lineTotalSen))
                }
            }
            if (order.bundleDiscountSen > 0) LabelValueRow("Bundle savings", "−" + formatRm(order.bundleDiscountSen))
            if (order.discountSen > 0) {
                val label = order.discount?.let { if (it.type == DiscountType.PERCENT) "Discount ${it.value}%" else "Discount" }
                LabelValueRow(label ?: "Discount", "−" + formatRm(order.discountSen))
            }
            if (cancelled) {
                Text("CANCELLED", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
            } else {
                Row(Modifier.align(Alignment.End)) {
                    TextButton(onClick = onEdit) { Text("Edit") }
                    TextButton(onClick = onCancel) { Text("Cancel order") }
                }
            }
        }
    }
}
