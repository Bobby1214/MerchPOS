package com.bobbykong.merchpos.ui.sell

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bobbykong.merchpos.data.model.DiscountType
import com.bobbykong.merchpos.data.model.OrderDiscount
import com.bobbykong.merchpos.data.model.PaymentMethod
import com.bobbykong.merchpos.data.model.Product
import com.bobbykong.merchpos.data.model.isSoldOut
import com.bobbykong.merchpos.ui.AppViewModelProvider
import com.bobbykong.merchpos.domain.PriceBreakdown
import com.bobbykong.merchpos.ui.components.CartLineRow
import com.bobbykong.merchpos.ui.components.DiscountInput
import com.bobbykong.merchpos.ui.components.DiscountPicker
import com.bobbykong.merchpos.ui.components.PaymentPicker
import com.bobbykong.merchpos.ui.components.PriceSummary
import com.bobbykong.merchpos.ui.components.ProductImage
import com.bobbykong.merchpos.ui.components.StartEventCard
import com.bobbykong.merchpos.ui.components.StockLabel
import com.bobbykong.merchpos.util.formatRm
import com.bobbykong.merchpos.util.formatSenPlain
import com.bobbykong.merchpos.util.parseRmToSen
import com.bobbykong.merchpos.util.quickCashAmounts

@Composable
fun SellScreen(
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    viewModel: SellViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    var showCheckout by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.messageShown()
        }
    }

    if (state.loading) return
    val event = state.event
    if (event == null) {
        Column(modifier.padding(16.dp)) {
            StartEventCard(onStart = viewModel::startEvent)
        }
        return
    }

    Column(modifier) {
        Text(
            "Event: ${event.name}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )

        if (state.categories.size > 1) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(
                        selected = state.selectedCategory == null,
                        onClick = { viewModel.selectCategory(null) },
                        label = { Text("All") },
                    )
                }
                items(state.categories) { category ->
                    FilterChip(
                        selected = state.selectedCategory == category,
                        onClick = { viewModel.selectCategory(category) },
                        label = { Text(category) },
                    )
                }
            }
        }

        if (state.products.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                Text("No products yet. Add some in the Products tab.", textAlign = TextAlign.Center)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.visibleProducts, key = { it.id }) { product ->
                    ProductTile(product, inCart = state.qtyInCart(product.id), onClick = { viewModel.add(product) })
                }
            }
        }

        if (state.cart.isNotEmpty()) {
            CartPanel(
                state = state,
                onIncrease = { viewModel.increase(it.key, it.product) },
                onDecrease = { viewModel.decrease(it.key) },
                onToggleGift = { viewModel.toggleGift(it.key) },
                onClear = viewModel::clearCart,
                onCheckout = { showCheckout = true },
            )
        }
    }

    if (showCheckout && state.cart.isNotEmpty()) {
        CheckoutDialog(
            priceWith = state::priceWith,
            onDismiss = { showCheckout = false },
            onConfirm = { method, discount ->
                viewModel.checkout(method, discount)
                showCheckout = false
            },
        )
    }
}

@Composable
private fun ProductTile(product: Product, inCart: Int, onClick: () -> Unit) {
    Card(onClick = onClick, enabled = !product.isSoldOut) {
        Column {
            product.imagePath?.let {
                ProductImage(it, Modifier.fillMaxWidth().aspectRatio(1.5f))
            }
            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                Text(
                    product.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    product.category,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(formatRm(product.priceSen), style = MaterialTheme.typography.titleSmall)
                StockLabel(product)
                if (inCart > 0) {
                    Text(
                        "In cart: $inCart",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun CartPanel(
    state: SellUiState,
    onIncrease: (CartLine) -> Unit,
    onDecrease: (CartLine) -> Unit,
    onToggleGift: (CartLine) -> Unit,
    onClear: () -> Unit,
    onCheckout: () -> Unit,
) {
    Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            LazyColumn(Modifier.heightIn(max = 220.dp)) {
                items(state.cart, key = { "${it.product.id}-${it.isGift}" }) { line ->
                    CartLineRow(
                        name = line.product.name,
                        unitPriceSen = line.product.priceSen,
                        qty = line.qty,
                        isGift = line.isGift,
                        canIncrease = state.qtyInCart(line.product.id) < line.product.stockQty,
                        onIncrease = { onIncrease(line) },
                        onDecrease = { onDecrease(line) },
                        onToggleGift = { onToggleGift(line) },
                    )
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Text("${state.itemCount} items", style = MaterialTheme.typography.bodySmall)
            PriceSummary(state.price)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                OutlinedButton(onClick = onClear) { Text("Clear") }
                Button(onClick = onCheckout, modifier = Modifier.weight(1f)) { Text("Checkout") }
            }
        }
    }
}

@Composable
private fun CheckoutDialog(
    priceWith: (OrderDiscount?) -> PriceBreakdown,
    onDismiss: () -> Unit,
    onConfirm: (PaymentMethod, OrderDiscount?) -> Unit,
) {
    var method by rememberSaveable { mutableStateOf(PaymentMethod.CASH) }
    var discountType by rememberSaveable { mutableStateOf<DiscountType?>(null) }
    var discountText by rememberSaveable { mutableStateOf("") }
    var tenderedText by rememberSaveable { mutableStateOf("") }

    val discountInput = DiscountInput(discountType, discountText)
    val price = priceWith(discountInput.discount)
    val totalSen = price.totalSen
    val tenderedSen = parseRmToSen(tenderedText)
    val changeSen = tenderedSen?.let { it - totalSen }
    val discountOk = discountInput.error == null
    // Cash: you must enter enough money first. E-wallet: you confirm after checking the payment arrived.
    val paymentOk = method != PaymentMethod.CASH || totalSen == 0 || (changeSen != null && changeSen >= 0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Total ${formatRm(totalSen)}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PaymentPicker(selected = method, onSelect = { method = it })
                HorizontalDivider()
                DiscountPicker(discountInput) {
                    discountType = it.type
                    discountText = it.text
                }
                PriceSummary(price)
                HorizontalDivider()
                if (method == PaymentMethod.CASH) {
                    OutlinedTextField(
                        value = tenderedText,
                        onValueChange = { tenderedText = it },
                        label = { Text("Cash received (RM)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        quickCashAmounts(totalSen).forEach { amount ->
                            SuggestionChip(
                                onClick = { tenderedText = formatSenPlain(amount) },
                                label = { Text(formatRm(amount)) },
                            )
                        }
                    }
                    when {
                        changeSen == null -> Unit
                        changeSen < 0 -> Text("Short by ${formatRm(-changeSen)}", color = MaterialTheme.colorScheme.error)
                        else -> Text(
                            "Change: ${formatRm(changeSen)}",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                } else {
                    Text("Let the customer pay with ${method.label}. Check the payment has arrived before you confirm.")
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(method, discountInput.discount) }, enabled = discountOk && paymentOk) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Back") }
        },
    )
}
