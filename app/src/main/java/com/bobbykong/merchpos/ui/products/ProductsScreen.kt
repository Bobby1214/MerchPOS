package com.bobbykong.merchpos.ui.products

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bobbykong.merchpos.data.files.ImageStore
import com.bobbykong.merchpos.data.model.BundleDeal
import com.bobbykong.merchpos.data.model.Product
import com.bobbykong.merchpos.ui.AppViewModelProvider
import com.bobbykong.merchpos.ui.components.ProductImage
import com.bobbykong.merchpos.ui.components.StockLabel
import com.bobbykong.merchpos.util.formatRm
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductsScreen(
    modifier: Modifier = Modifier,
    viewModel: ProductsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val products by viewModel.products.collectAsStateWithLifecycle()
    val bundles by viewModel.bundles.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showProductForm by remember { mutableStateOf(false) }
    var editingProduct by remember { mutableStateOf<Product?>(null) }
    var showBundleForm by remember { mutableStateOf(false) }
    var editingBundle by remember { mutableStateOf<BundleDeal?>(null) }
    val categories = products.map { it.category }.distinct().sorted()

    Column(modifier) {
        PrimaryTabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Products") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Bundles") })
        }
        Box(Modifier.fillMaxSize()) {
            if (tab == 0) {
                ProductList(products, onClick = {
                    editingProduct = it
                    showProductForm = true
                })
            } else {
                BundleList(bundles, onClick = {
                    editingBundle = it
                    showBundleForm = true
                })
            }
            FloatingActionButton(
                onClick = {
                    if (tab == 0) {
                        editingProduct = null
                        showProductForm = true
                    } else {
                        editingBundle = null
                        showBundleForm = true
                    }
                },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = if (tab == 0) "Add product" else "Add bundle")
            }
        }
    }

    if (showProductForm) {
        ProductFormDialog(
            original = editingProduct,
            categories = categories,
            onDismiss = { showProductForm = false },
            onSave = { form -> viewModel.save(editingProduct, form) },
            onImportImage = viewModel::importImage,
            onDiscardImage = viewModel::discardImage,
        )
    }
    if (showBundleForm) {
        BundleFormDialog(
            original = editingBundle,
            categories = categories,
            onDismiss = { showBundleForm = false },
            onSave = { form -> viewModel.saveBundle(editingBundle, form) },
            onDelete = { viewModel.deleteBundle(it) },
        )
    }
}

@Composable
private fun ProductList(products: List<Product>, onClick: (Product) -> Unit) {
    if (products.isEmpty()) {
        EmptyText("No products yet. Tap + to add your first item.")
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(products, key = { it.id }) { product ->
            Card(onClick = { onClick(product) }, modifier = Modifier.alpha(if (product.isActive) 1f else 0.5f)) {
                ListItem(
                    leadingContent = product.imagePath?.let { path ->
                        { ProductImage(path, Modifier.size(56.dp).clip(RoundedCornerShape(8.dp))) }
                    },
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
    }
}

@Composable
private fun BundleList(bundles: List<BundleDeal>, onClick: (BundleDeal) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(
                "A bundle applies by itself in the cart when the customer picks enough items from its category. " +
                    "If several bundles fit, the app picks the best deal for the customer.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (bundles.isEmpty()) {
            item { Text("No bundles yet. Tap + to add one, e.g. any 3 Badges for RM20.", modifier = Modifier.padding(top = 16.dp)) }
        }
        items(bundles, key = { it.id }) { bundle ->
            Card(onClick = { onClick(bundle) }, modifier = Modifier.alpha(if (bundle.isActive) 1f else 0.5f)) {
                ListItem(
                    headlineContent = { Text(bundle.name) },
                    supportingContent = { Text("Any ${bundle.qty} × ${bundle.category} for ${formatRm(bundle.priceSen)}") },
                    trailingContent = if (bundle.isActive) null else ({ Text("Off") }),
                )
            }
        }
    }
}

@Composable
private fun EmptyText(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ProductFormDialog(
    original: Product?,
    categories: List<String>,
    onDismiss: () -> Unit,
    onSave: (ProductForm) -> String?,
    onImportImage: suspend (Uri) -> String?,
    onDiscardImage: (String?) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var form by remember { mutableStateOf(ProductForm.from(original)) }
    var error by remember { mutableStateOf<String?>(null) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }

    // A photo picked in this form but not saved yet is thrown away when replaced
    fun setImage(name: String?) {
        if (form.imagePath != original?.imagePath) onDiscardImage(form.imagePath)
        form = form.copy(imagePath = name)
    }

    fun importFrom(uri: Uri) = scope.launch {
        val name = onImportImage(uri)
        if (name == null) error = "Couldn't load that image" else setImage(name)
    }

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(::importFrom)
    }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = cameraUri
        if (saved && uri != null) importFrom(uri)
    }

    fun dismiss() {
        if (form.imagePath != original?.imagePath) onDiscardImage(form.imagePath)
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = ::dismiss,
        title = { Text(if (original == null) "Add product" else "Edit product") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val imageModifier = Modifier.size(72.dp).clip(RoundedCornerShape(8.dp))
                    val path = form.imagePath
                    if (path != null) {
                        ProductImage(path, imageModifier)
                    } else {
                        Box(imageModifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                            Text("No photo", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Column {
                        TextButton(onClick = {
                            pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }) { Text("Gallery") }
                        TextButton(onClick = {
                            val uri = ImageStore.newCameraUri(context)
                            cameraUri = uri
                            try {
                                takePhoto.launch(uri)
                            } catch (_: ActivityNotFoundException) {
                                error = "No camera app found"
                            }
                        }) { Text("Camera") }
                    }
                    if (path != null) TextButton(onClick = { setImage(null) }) { Text("Remove") }
                }
                OutlinedTextField(
                    value = form.name,
                    onValueChange = { form = form.copy(name = it) },
                    label = { Text("Name") },
                    singleLine = true,
                )
                CategoryField(form.category, categories) { form = form.copy(category = it) }
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
                    ActiveSwitch(
                        checked = form.isActive,
                        hint = "Inactive products can't be sold. Old orders keep them.",
                        onChange = { form = form.copy(isActive = it) },
                    )
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
            TextButton(onClick = ::dismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun BundleFormDialog(
    original: BundleDeal?,
    categories: List<String>,
    onDismiss: () -> Unit,
    onSave: (BundleForm) -> String?,
    onDelete: (BundleDeal) -> Unit,
) {
    var form by remember { mutableStateOf(BundleForm.from(original)) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (original == null) "Add bundle" else "Edit bundle") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CategoryField(form.category, categories) { form = form.copy(category = it) }
                OutlinedTextField(
                    value = form.qty,
                    onValueChange = { form = form.copy(qty = it) },
                    label = { Text("How many items (e.g. 3)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    value = form.price,
                    onValueChange = { form = form.copy(price = it) },
                    label = { Text("Bundle price (RM)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(
                    value = form.name,
                    onValueChange = { form = form.copy(name = it) },
                    label = { Text("Name (optional)") },
                    placeholder = { Text("e.g. 3 Badges for RM20") },
                    singleLine = true,
                )
                if (original != null) {
                    ActiveSwitch(checked = form.isActive, hint = "Turn off to pause this deal.") {
                        form = form.copy(isActive = it)
                    }
                    TextButton(onClick = {
                        onDelete(original)
                        onDismiss()
                    }) { Text("Delete bundle", color = MaterialTheme.colorScheme.error) }
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

@Composable
private fun CategoryField(value: String, categories: List<String>, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text("Category (e.g. Badge)") },
        singleLine = true,
    )
    if (categories.isNotEmpty()) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            categories.forEach { category ->
                SuggestionChip(onClick = { onChange(category) }, label = { Text(category) })
            }
        }
    }
}

@Composable
private fun ActiveSwitch(checked: Boolean, hint: String, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text("Active")
            Text(hint, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
