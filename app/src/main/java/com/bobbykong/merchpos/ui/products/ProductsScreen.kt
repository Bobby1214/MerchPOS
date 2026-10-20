package com.bobbykong.merchpos.ui.products

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bobbykong.merchpos.data.model.Product
import com.bobbykong.merchpos.ui.AppViewModelProvider
import com.bobbykong.merchpos.ui.components.StockLabel
import com.bobbykong.merchpos.util.formatRm

@Composable
fun ProductsScreen(
    modifier: Modifier = Modifier,
    viewModel: ProductsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val products by viewModel.products.collectAsStateWithLifecycle()
    var showForm by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Product?>(null) }

    Box(modifier) {
        if (products.isEmpty()) {
            Text(
                "No products yet. Tap + to add your first item.",
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
            )
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(products, key = { it.id }) { product ->
                ProductRow(product, onClick = {
                    editing = product
                    showForm = true
                })
            }
        }
        FloatingActionButton(
            onClick = {
                editing = null
                showForm = true
            },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Add product")
        }
    }

    if (showForm) {
        ProductFormDialog(
            original = editing,
            categories = products.map { it.category }.distinct().sorted(),
            onDismiss = { showForm = false },
            onSave = { form -> viewModel.save(editing, form) },
        )
    }
}

@Composable
private fun ProductRow(product: Product, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.alpha(if (product.isActive) 1f else 0.5f)) {
        ListItem(
            headlineContent = { Text(product.name) },
            supportingContent = {
                Column {
                    Text("${product.category} · ${formatRm(product.priceSen)}")
                    StockLabel(product)
                }
            },
            trailingContent = if (product.isActive) null else ({ Text("Inactive") }),
        )
    }
}

@Composable
private fun ProductFormDialog(
    original: Product?,
    categories: List<String>,
    onDismiss: () -> Unit,
    onSave: (ProductForm) -> String?,
) {
    var form by remember { mutableStateOf(ProductForm.from(original)) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (original == null) "Add product" else "Edit product") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = form.name,
                    onValueChange = { form = form.copy(name = it) },
                    label = { Text("Name") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = form.category,
                    onValueChange = { form = form.copy(category = it) },
                    label = { Text("Category (e.g. Badge)") },
                    singleLine = true,
                )
                if (categories.isNotEmpty()) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        categories.forEach { category ->
                            SuggestionChip(onClick = { form = form.copy(category = category) }, label = { Text(category) })
                        }
                    }
                }
                OutlinedTextField(
                    value = form.price,
                    onValueChange = { form = form.copy(price = it) },
                    label = { Text("Price (RM)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(
                    value = form.stock,
                    onValueChange = { form = form.copy(stock = it) },
                    label = { Text("Stock") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                if (original != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text("Active")
                            Text(
                                "Inactive products can't be sold. Old orders keep them.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Switch(checked = form.isActive, onCheckedChange = { form = form.copy(isActive = it) })
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                error = onSave(form)
                if (error == null) onDismiss()
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
