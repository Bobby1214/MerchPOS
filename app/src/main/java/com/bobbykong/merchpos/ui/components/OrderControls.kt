package com.bobbykong.merchpos.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ripple
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.bobbykong.merchpos.data.files.ImageStore
import com.bobbykong.merchpos.data.model.DiscountType
import com.bobbykong.merchpos.data.model.OrderDiscount
import com.bobbykong.merchpos.data.model.PaymentMethod
import com.bobbykong.merchpos.domain.PriceBreakdown
import com.bobbykong.merchpos.util.formatRm
import com.bobbykong.merchpos.util.formatSenPlain
import com.bobbykong.merchpos.util.parseRmToSen

/** What the user typed in the discount field. [discount] is null when "None" is picked or the text is invalid. */
data class DiscountInput(val type: DiscountType? = null, val text: String = "") {
    val discount: OrderDiscount?
        get() = when (type) {
            null -> null
            DiscountType.PERCENT -> text.trim().toIntOrNull()?.takeIf { it in 1..100 }?.let { OrderDiscount(type, it) }
            DiscountType.FIXED -> parseRmToSen(text)?.takeIf { it > 0 }?.let { OrderDiscount(type, it) }
        }

    val error: String?
        get() = when {
            type == null || discount != null -> null
            type == DiscountType.PERCENT -> "Enter a whole number from 1 to 100"
            else -> "Enter an amount, e.g. 5 or 2.50"
        }

    companion object {
        fun from(discount: OrderDiscount?): DiscountInput = when (discount?.type) {
            null -> DiscountInput()
            DiscountType.PERCENT -> DiscountInput(DiscountType.PERCENT, discount.value.toString())
            DiscountType.FIXED -> DiscountInput(DiscountType.FIXED, formatSenPlain(discount.value))
        }
    }
}

@Composable
fun PaymentPicker(selected: PaymentMethod, onSelect: (PaymentMethod) -> Unit) {
    Column {
        Text("Payment", style = MaterialTheme.typography.labelLarge)
        PaymentMethod.entries.forEach { option ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .selectable(selected = selected == option, onClick = { onSelect(option) }, role = Role.RadioButton)
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = selected == option, onClick = null)
                Spacer(Modifier.width(8.dp))
                Text(option.label)
            }
        }
    }
}

@Composable
fun DiscountPicker(input: DiscountInput, onChange: (DiscountInput) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Discount", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = input.type == null, onClick = { onChange(DiscountInput()) }, label = { Text("None") })
            FilterChip(
                selected = input.type == DiscountType.PERCENT,
                onClick = { onChange(DiscountInput(DiscountType.PERCENT)) },
                label = { Text("%") },
            )
            FilterChip(
                selected = input.type == DiscountType.FIXED,
                onClick = { onChange(DiscountInput(DiscountType.FIXED)) },
                label = { Text("RM") },
            )
        }
        if (input.type != null) {
            OutlinedTextField(
                value = input.text,
                onValueChange = { onChange(input.copy(text = it)) },
                label = { Text(if (input.type == DiscountType.PERCENT) "Percent off" else "Amount off (RM)") },
                singleLine = true,
                isError = input.error != null && input.text.isNotEmpty(),
                supportingText = {
                    val hint = if (input.type == DiscountType.PERCENT) "Total is rounded to the nearest RM" else null
                    (input.error?.takeIf { input.text.isNotEmpty() } ?: hint)?.let { Text(it) }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (input.type == DiscountType.PERCENT) KeyboardType.Number else KeyboardType.Decimal,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Subtotal, bundle savings, discount, gifts and total. */
@Composable
fun PriceSummary(price: PriceBreakdown, modifier: Modifier = Modifier) {
    Column(modifier) {
        val hasExtras = price.appliedBundles.isNotEmpty() || price.discountSen > 0
        if (hasExtras) LabelValueRow("Subtotal", formatRm(price.subtotalSen))
        price.appliedBundles.forEach {
            LabelValueRow("${it.bundle.name} ×${it.times}", "−" + formatRm(it.savingSen))
        }
        if (price.discountSen > 0) LabelValueRow("Discount", "−" + formatRm(price.discountSen))
        if (price.giftValueSen > 0) LabelValueRow("Gifts (worth ${formatRm(price.giftValueSen)})", "FREE")
        LabelValueRow("Total", formatRm(price.totalSen), bold = true)
    }
}

/** One cart line: name, price, a Gift chip and a − qty + stepper. Used by Sell and Edit order. */
@Composable
fun CartLineRow(
    name: String,
    unitPriceSen: Int,
    qty: Int,
    isGift: Boolean,
    canIncrease: Boolean,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
    onToggleGift: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (isGift) "FREE · worth ${formatRm(unitPriceSen)} each" else formatRm(unitPriceSen * qty),
                style = MaterialTheme.typography.bodySmall,
                color = if (isGift) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FilterChip(
            selected = isGift,
            onClick = onToggleGift,
            label = { Text("Gift") },
            shape = CircleShape,
            modifier = Modifier.padding(end = 6.dp),
        )
        QtyStepper(qty, canIncrease, onIncrease, onDecrease)
    }
}

/** − 2 + inside one rounded pill. The number rolls up or down when it changes. */
@Composable
fun QtyStepper(qty: Int, canIncrease: Boolean, onIncrease: () -> Unit, onDecrease: () -> Unit) {
    Row(
        Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepButton("−", enabled = true, onClick = onDecrease)
        AnimatedContent(
            targetState = qty,
            transitionSpec = {
                val up = targetState > initialState
                (slideInVertically { if (up) it else -it } + fadeIn())
                    .togetherWith(slideOutVertically { if (up) -it else it } + fadeOut())
            },
            label = "qty",
        ) { value ->
            Text(
                "$value",
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(min = 24.dp),
            )
        }
        StepButton("+", enabled = canIncrease, onClick = onIncrease, filled = true)
    }
}

@Composable
private fun StepButton(symbol: String, enabled: Boolean, onClick: () -> Unit, filled: Boolean = false) {
    val interaction = remember { MutableInteractionSource() }
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(36.dp)
            .pressBounce(interaction, 0.85f)
            .padding(3.dp)
            .clip(CircleShape)
            .background(if (filled && enabled) colors.secondary else Color.Transparent)
            .clickable(interactionSource = interaction, indication = ripple(), enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            symbol,
            style = MaterialTheme.typography.titleMedium,
            color = when {
                !enabled -> colors.onSurface.copy(alpha = 0.3f)
                filled -> colors.onSecondary
                else -> colors.onSurface
            },
        )
    }
}

@Composable
fun ProductImage(imagePath: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // Built once per photo; crossfade = the photo fades in instead of popping
    val request = remember(imagePath) {
        ImageRequest.Builder(context).data(ImageStore.file(context, imagePath)).crossfade(true).build()
    }
    AsyncImage(
        model = request,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier,
    )
}
