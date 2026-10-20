package com.bobbykong.merchpos.ui.report

import com.bobbykong.merchpos.data.model.isLowStock
import com.bobbykong.merchpos.data.model.isSoldOut
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bobbykong.merchpos.data.PosRepository
import com.bobbykong.merchpos.data.model.BestSeller
import com.bobbykong.merchpos.data.model.DailySales
import com.bobbykong.merchpos.data.model.DiscountGiftSummary
import com.bobbykong.merchpos.data.model.PaymentMethod
import com.bobbykong.merchpos.data.model.PaymentTotal
import com.bobbykong.merchpos.data.model.Product
import com.bobbykong.merchpos.data.model.SalesSummary
import com.bobbykong.merchpos.ui.EventSelection
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class ReportUiState(
    val summary: SalesSummary = SalesSummary(orderCount = 0, totalSen = 0),
    val extras: DiscountGiftSummary = DiscountGiftSummary(discountSen = 0, giftQty = 0, giftValueSen = 0),
    val payments: List<PaymentTotal> = emptyList(),
    val daily: List<DailySales> = emptyList(),
    val bestSellers: List<BestSeller> = emptyList(),
    val stock: List<Product> = emptyList(),
) {
    val itemsSold: Int get() = bestSellers.sumOf { it.qtySold }
    val eWalletSen: Long get() = payments.filter { it.paymentMethod.isEWallet }.sumOf { it.totalSen }

    fun totalFor(method: PaymentMethod): Long = payments.firstOrNull { it.paymentMethod == method }?.totalSen ?: 0
}

@OptIn(ExperimentalCoroutinesApi::class)
class ReportViewModel(private val repository: PosRepository) : ViewModel() {
    val eventSelection = EventSelection(repository, viewModelScope)

    /** Only products to worry about: sold out first, then low stock (fewest left first). */
    private val needsRestock = repository.activeProducts.map { products ->
        products.filter { it.isSoldOut || it.isLowStock }.sortedBy { it.stockQty }
    }

    val uiState: StateFlow<ReportUiState> =
        eventSelection.selected.map { it?.id }.distinctUntilChanged().flatMapLatest { eventId ->
            if (eventId == null) {
                needsRestock.map { ReportUiState(stock = it) }
            } else {
                // combine() takes at most 5 flows, so two are joined first
                val totals = combine(repository.salesSummary(eventId), repository.discountsAndGifts(eventId)) { a, b -> a to b }
                combine(
                    totals,
                    repository.paymentTotals(eventId),
                    repository.dailySales(eventId),
                    repository.bestSellers(eventId),
                    needsRestock,
                ) { (summary, extras), payments, daily, bestSellers, stock ->
                    ReportUiState(summary, extras, payments, daily, bestSellers, stock)
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportUiState())
}
