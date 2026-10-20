package com.bobbykong.merchpos.ui.orders

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bobbykong.merchpos.data.model.Product
import com.bobbykong.merchpos.ui.components.CartLineRow
import com.bobbykong.merchpos.ui.components.DiscountPicker
import com.bobbykong.merchpos.ui.components.PaymentPicker
import com.bobbykong.merchpos.ui.components.PriceSummary
import com.bobbykong.merchpos.util.formatRm

/** Full-screen editor for one order. Nothing is saved until "Save". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditOrderDialog(state: EditUiState, viewModel: OrdersViewModel) {
    val draft = state.draft
    Dialog(
        onDismissRequest = viewModel::closeEdit,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Edit order #${draft.order.id}") },
                    navigationIcon = {
                        IconButton(onClick = viewModel::closeEdit) { Icon(Icons.Filled.Close, contentDescription = "Close") }
                    },
                    actions = { TextButton(onClick = viewModel::saveEdit) { Text("Save") } },
                )
            },
        ) { padding ->
            LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp)) {
                items(state.lines, key = { "${it.product.id}-${it.isGift}" }) { line ->
                    CartLineRow(
                        name = line.product.name + if (line.product.isActive) "" else " (inactive)",
                        unitPriceSen = line.unitPriceSen,
                        qty = line.qty,
                        isGift = line.isGift,
                        canIncrease = line.canIncrease,
                        onIncrease = { viewModel.increase(line.key) },
                        onDecrease = { viewModel.decrease(line.key) },
                        onToggleGift = { viewModel.toggleGift(line.key) },
                    )
                }
                item { AddItemButton(state.addable, onAdd = viewModel::add) }
                item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
                item { PaymentPicker(selected = draft.payment, onSelect = viewModel::setPayment) }
                item { DiscountPicker(draft.discount, onChange = viewModel::setDiscount) }
                item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
                item { PriceSummary(state.price) }
                item {
                    Text(
                        "Was ${formatRm(draft.order.totalSen)}. Old lines keep their old price.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                draft.error?.let { error ->
                    item { Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
                }
                item {
                    Button(onClick = viewModel::saveEdit, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                        Text("Save changes")
                    }
                }
            }
        }
    }
}

@Composable
private fun AddItemButton(addable: List<Product>, onAdd: (Product) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, enabled = addable.isNotEmpty()) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text("Add item")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            addable.forEach { product ->
                DropdownMenuItem(
                    text = { Text("${product.name} · ${formatRm(product.priceSen)} (${product.stockQty} left)") },
                    onClick = {
                        onAdd(product)
                        expanded = false
                    },
                )
            }
        }
    }
}
