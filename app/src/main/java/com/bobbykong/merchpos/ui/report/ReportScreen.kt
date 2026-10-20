package com.bobbykong.merchpos.ui.report

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bobbykong.merchpos.data.model.PaymentMethod
import com.bobbykong.merchpos.ui.AppViewModelProvider
import com.bobbykong.merchpos.ui.components.EventPicker
import com.bobbykong.merchpos.ui.components.LabelValueRow
import com.bobbykong.merchpos.ui.components.StockLabel
import com.bobbykong.merchpos.util.formatDay
import com.bobbykong.merchpos.util.formatRm

@Composable
fun ReportScreen(
    modifier: Modifier = Modifier,
    viewModel: ReportViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val events by viewModel.eventSelection.events.collectAsStateWithLifecycle()
    val selected by viewModel.eventSelection.selected.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { EventPicker(events, selected, onPick = viewModel.eventSelection::pick) }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Total sales", style = MaterialTheme.typography.labelLarge)
                    Text(formatRm(state.summary.totalSen), style = MaterialTheme.typography.headlineMedium)
                    Text("${plural(state.summary.orderCount, "order")} · ${plural(state.itemsSold, "item")} sold")
                    if (state.extras.discountSen > 0) {
                        Text("Discounts & bundles: −${formatRm(state.extras.discountSen)}")
                    }
                    if (state.extras.giftQty > 0) {
                        Text("Gifts: ${plural(state.extras.giftQty, "item")}, worth ${formatRm(state.extras.giftValueSen)}")
                    }
                }
            }
        }

        item {
            Section("Payment methods") {
                PaymentMethod.entries.forEach { method ->
                    LabelValueRow(method.label, formatRm(state.totalFor(method)))
                }
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                LabelValueRow("Cash total", formatRm(state.totalFor(PaymentMethod.CASH)), bold = true)
                LabelValueRow("E-wallet total", formatRm(state.eWalletSen), bold = true)
            }
        }

        item {
            Section("Sales per day") {
                if (state.daily.isEmpty()) Text("No sales yet")
                state.daily.forEach { day ->
                    LabelValueRow("${formatDay(day.day)} (${plural(day.orderCount, "order")})", formatRm(day.totalSen))
                }
            }
        }

        item {
            Section("Best sellers (before discounts)") {
                if (state.bestSellers.isEmpty()) Text("No sales yet")
                state.bestSellers.forEachIndexed { index, seller ->
                    LabelValueRow("${index + 1}. ${seller.name}", "${seller.qtySold} sold · ${formatRm(seller.revenueSen)}")
                }
            }
        }

        item {
            Section("Stock left (now)") {
                if (state.stock.isEmpty()) Text("No active products")
                state.stock.forEach { product ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text(product.name, modifier = Modifier.weight(1f))
                        StockLabel(product)
                    }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 4.dp))
            content()
        }
    }
}

/** plural(1, "order") -> "1 order", plural(3, "order") -> "3 orders" */
private fun plural(count: Int, word: String) = "$count $word" + if (count == 1) "" else "s"
