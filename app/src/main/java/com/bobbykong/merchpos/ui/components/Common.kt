package com.bobbykong.merchpos.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bobbykong.merchpos.data.model.Event
import com.bobbykong.merchpos.data.model.Product
import com.bobbykong.merchpos.data.model.isLowStock
import com.bobbykong.merchpos.data.model.isSoldOut

/** "12 left", or a red warning when stock is low or sold out. */
@Composable
fun StockLabel(product: Product, modifier: Modifier = Modifier) {
    val (text, color) = when {
        product.isSoldOut -> "Sold out" to MaterialTheme.colorScheme.error
        product.isLowStock -> "Low stock: ${product.stockQty} left" to MaterialTheme.colorScheme.error
        else -> "${product.stockQty} left" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(text, style = MaterialTheme.typography.bodySmall, color = color, modifier = modifier)
}

@Composable
fun StartEventCard(onStart: (String) -> Unit, modifier: Modifier = Modifier) {
    var name by rememberSaveable { mutableStateOf("") }
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("No event running", style = MaterialTheme.typography.titleMedium)
            Text(
                "Start an event (e.g. Comic Fiesta 2026) before selling, so the report can group sales by event.",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Event name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    onStart(name.trim())
                    name = ""
                },
                enabled = name.isNotBlank(),
            ) { Text("Start event") }
        }
    }
}

@Composable
fun EventPicker(
    events: List<Event>,
    selected: Event?,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { expanded = true }, enabled = events.isNotEmpty()) {
            Text(selected?.name ?: "No events yet")
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Filled.ArrowDropDown, contentDescription = "Choose event")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            events.forEach { event ->
                DropdownMenuItem(
                    text = { Text(event.name) },
                    onClick = {
                        onPick(event.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** A label on the left and a value on the right. */
@Composable
fun LabelValueRow(label: String, value: String, modifier: Modifier = Modifier, bold: Boolean = false) {
    val style = if (bold) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium
    Row(modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, style = style, modifier = Modifier.weight(1f))
        Text(value, style = style)
    }
}
