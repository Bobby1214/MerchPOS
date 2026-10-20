package com.bobbykong.merchpos.ui.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bobbykong.merchpos.data.PosRepository
import com.bobbykong.merchpos.data.model.Order
import com.bobbykong.merchpos.data.model.OrderLine
import com.bobbykong.merchpos.ui.EventSelection
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class OrderWithLines(val order: Order, val lines: List<OrderLine>)

@OptIn(ExperimentalCoroutinesApi::class)
class OrdersViewModel(private val repository: PosRepository) : ViewModel() {
    val eventSelection = EventSelection(repository, viewModelScope)

    val orders: StateFlow<List<OrderWithLines>> =
        eventSelection.selected.map { it?.id }.distinctUntilChanged().flatMapLatest { eventId ->
            if (eventId == null) {
                flowOf(emptyList())
            } else {
                combine(repository.ordersForEvent(eventId), repository.linesForEvent(eventId)) { orders, lines ->
                    val linesByOrder = lines.groupBy { it.orderId }
                    orders.map { OrderWithLines(it, linesByOrder[it.id].orEmpty()) }
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun cancel(orderId: Int) {
        viewModelScope.launch { repository.cancelOrder(orderId) }
    }
}
