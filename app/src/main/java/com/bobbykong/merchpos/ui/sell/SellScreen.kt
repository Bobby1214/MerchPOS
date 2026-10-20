package com.bobbykong.merchpos.ui.sell

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import com.bobbykong.merchpos.ui.components.Pill
import com.bobbykong.merchpos.ui.components.ProductPicture
import com.bobbykong.merchpos.ui.components.pressBounce
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bobbykong.merchpos.data.model.DiscountType
import com.bobbykong.merchpos.data.model.OrderDiscount
import com.bobbykong.merchpos.data.model.PaymentMethod
import com.bobbykong.merchpos.data.model.Product
import com.bobbykong.merchpos.data.model.isSoldOut
import com.bobbykong.merchpos.domain.PriceBreakdown
import com.bobbykong.merchpos.ui.AppViewModelProvider
import com.bobbykong.merchpos.ui.components.CartLineRow
import com.bobbykong.merchpos.ui.components.CategorySidebar
import com.bobbykong.merchpos.ui.components.DiscountInput
import com.bobbykong.merchpos.ui.components.DiscountPicker
import com.bobbykong.merchpos.ui.components.PaymentPicker
import com.bobbykong.merchpos.ui.components.PriceSummary
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
        Pill(
            "Now selling at ${event.name}",
            container = MaterialTheme.colorScheme.tertiaryContainer,
            content = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 8.dp),
        )

        if (state.products.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                Text("No products yet. Add some in the Products tab.", textAlign = TextAlign.Center)
            }
        } else {
            val gridState = rememberLazyGridState()
            // Start at the top when the category or the order changes.
            // Otherwise the grid keeps the old first item in view and items moved above it are hidden.
            LaunchedEffect(state.selectedCategory, state.visibleProducts.map { it.id }) { gridState.scrollToItem(0) }
            Row(Modifier.weight(1f)) {
                CategorySidebar(
                    products = state.products,
                    categories = state.categories,
                    selected = state.selectedCategory,
                    onSelect = viewModel::selectCategory,
                )
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(116.dp),
                    modifier = Modifier.weight(1f),
                    state = gridState,
                    contentPadding = PaddingValues(start = 4.dp, top = 4.dp, end = 12.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.visibleProducts, key = { it.id }) { product ->
                        ProductTile(
                            product,
                            inCart = state.qtyInCart(product.id),
                            isSet = state.catalog.hasParts(product.id),
                            onClick = { viewModel.add(product) },
                            // Tiles glide to their new place when the category or order changes
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }

        // The cart slides up from the bottom when the first item is added
        AnimatedVisibility(
            visible = state.cart.isNotEmpty(),
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
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
private fun ProductTile(product: Product, inCart: Int, isSet: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val soldOut = product.isSoldOut
    Card(
        onClick = onClick,
        enabled = !soldOut,
        interactionSource = interaction,
        modifier = modifier
            .pressBounce(interaction)
            .alpha(if (soldOut) 0.55f else 1f),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp, pressedElevation = 0.dp),
    ) {
        Column(Modifier.padding(8.dp)) {
            Box {
                ProductPicture(product, Modifier.fillMaxWidth().aspectRatio(1.2f).clip(MaterialTheme.shapes.medium))
                when {
                    soldOut -> Pill(
                        "Sold out",
                        MaterialTheme.colorScheme.errorContainer,
                        MaterialTheme.colorScheme.error,
                        Modifier.align(Alignment.TopStart).padding(6.dp),
                    )
                    isSet -> Pill(
                        "Set",
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.onPrimaryContainer,
                        Modifier.align(Alignment.TopStart).padding(6.dp),
                    )
                }
                CartBadge(inCart, Modifier.align(Alignment.TopEnd).padding(6.dp))
            }
            Text(
                product.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp, start = 2.dp),
            )
            StockLabel(product, Modifier.padding(start = 2.dp), setLabel = isSet)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                Text(
                    formatRm(product.priceSen),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f).padding(start = 2.dp),
                )
                // The round "+" of shopping apps. The whole card is the button; this just shows it.
                Box(
                    Modifier.size(30.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

/** Pink bubble with how many are in the cart. Pops in, and the number flips when it changes. */
@Composable
private fun CartBadge(count: Int, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = count > 0,
        enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)),
        exit = scaleOut() + fadeOut(),
        modifier = modifier,
    ) {
        Box(
            Modifier
                .size(28.dp)
                .shadow(4.dp, CircleShape)
                .background(MaterialTheme.colorScheme.secondary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = count,
                transitionSpec = {
                    (slideInVertically { -it } + fadeIn()).togetherWith(slideOutVertically { it } + fadeOut())
                },
                label = "cartCount",
            ) { value ->
                Text(
                    "$value",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondary,
                )
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
    Surface(
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shadowElevation = 16.dp,
    ) {
        // animateContentSize: the panel grows and shrinks smoothly as lines are added or removed
        Column(Modifier.animateContentSize().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp)) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(width = 36.dp, height = 4.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant, CircleShape),
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                Text(
                    "Cart · ${state.itemCount} items",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onClear) { Text("Clear") }
            }
            LazyColumn(Modifier.heightIn(max = 200.dp)) {
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
                        modifier = Modifier.animateItem(),
                    )
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant)
            PriceSummary(state.price)
            val interaction = remember { MutableInteractionSource() }
            Button(
                onClick = onCheckout,
                interactionSource = interaction,
                modifier = Modifier
                    .padding(top = 10.dp)
                    .fillMaxWidth()
                    .height(54.dp)
                    .pressBounce(interaction, 0.96f),
                shape = CircleShape,
            ) {
                Text("Checkout", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                AnimatedContent(
                    targetState = formatRm(state.price.totalSen),
                    transitionSpec = {
                        (slideInVertically { -it } + fadeIn()).togetherWith(slideOutVertically { it } + fadeOut())
                    },
                    label = "total",
                ) { total ->
                    Text(total, style = MaterialTheme.typography.titleMedium)
                }
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
