package com.bobbykong.merchpos.ui.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bobbykong.merchpos.data.PosException
import com.bobbykong.merchpos.data.PosRepository
import com.bobbykong.merchpos.data.model.Order
import com.bobbykong.merchpos.data.model.OrderLine
import com.bobbykong.merchpos.data.model.PaymentMethod
import com.bobbykong.merchpos.data.model.Product
import com.bobbykong.merchpos.domain.CartKey
import com.bobbykong.merchpos.domain.PriceBreakdown
import com.bobbykong.merchpos.domain.PriceCalculator
import com.bobbykong.merchpos.domain.PriceLine
import com.bobbykong.merchpos.domain.minusOne
import com.bobbykong.merchpos.domain.plusOne
import com.bobbykong.merchpos.domain.qtyOf
import com.bobbykong.merchpos.domain.toCartItems
import com.bobbykong.merchpos.domain.toggleGift
import com.bobbykong.merchpos.ui.EventSelection
import com.bobbykong.merchpos.ui.components.DiscountInput
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OrderWithLines(val order: Order, val lines: List<OrderLine>)

/** The order being edited, before it's saved. */
data class EditDraft(
    val order: Order,
    val cart: Map<CartKey, Int>,
    // What the order had before editing: qty and price per product
    val oldQty: Map<Int, Int>,
    val oldPrice: Map<Int, Int>,
    val payment: PaymentMethod,
    val discount: DiscountInput,
    val error: String? = null,
) {
    fun unitPrice(product: Product): Int = oldPrice[product.id] ?: product.priceSen

    /** Your rule: a deactivated product can be kept or reduced, never increased. */
    fun maxQty(product: Product): Int =
        (oldQty[product.id] ?: 0) + if (product.isActive) product.stockQty else 0
}

data class EditLine(val product: Product, val qty: Int, val isGift: Boolean, val unitPriceSen: Int, val canIncrease: Boolean) {
    val key: CartKey get() = CartKey(product.id, isGift)
}

data class EditUiState(
    val draft: EditDraft,
    val lines: List<EditLine>,
    // Products that can still be added
    val addable: List<Product>,
    val price: PriceBreakdown,
)

@OptIn(ExperimentalCoroutinesApi::class)
class OrdersViewModel(private val repository: PosRepository) : ViewModel() {
    val eventSelection = EventSelection(repository, viewModelScope)

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

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

    private val draft = MutableStateFlow<EditDraft?>(null)

    val editState: StateFlow<EditUiState?> =
        combine(draft, repository.allProducts, repository.activeBundles) { draft, products, bundles ->
            if (draft == null) return@combine null
            val byId = products.associateBy { it.id }
            val lines = draft.cart.mapNotNull { (key, qty) ->
                val product = byId[key.productId] ?: return@mapNotNull null
                EditLine(
                    product = product,
                    qty = qty,
                    isGift = key.isGift,
                    unitPriceSen = draft.unitPrice(product),
                    canIncrease = draft.cart.qtyOf(product.id) < draft.maxQty(product),
                )
            }
            EditUiState(
                draft = draft,
                lines = lines,
                addable = products.filter { it.isActive && !it.isMaterial && draft.cart.qtyOf(it.id) < draft.maxQty(it) },
                price = PriceCalculator.calculate(
                    lines.map { PriceLine(it.product.id, it.product.category, it.unitPriceSen, it.qty, it.isGift) },
                    bundles,
                    draft.discount.discount,
                ),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun cancel(orderId: Int) {
        viewModelScope.launch { repository.cancelOrder(orderId) }
    }

    fun startEdit(item: OrderWithLines) {
        draft.value = EditDraft(
            order = item.order,
            cart = item.lines.associate { CartKey(it.productId, it.isGift) to it.qty },
            oldQty = item.lines.groupingBy { it.productId }.fold(0) { sum, line -> sum + line.qty },
            oldPrice = item.lines.associate { it.productId to it.unitPriceSen },
            payment = item.order.paymentMethod,
            discount = DiscountInput.from(item.order.discount),
        )
    }

    private fun updateCart(change: (Map<CartKey, Int>) -> Map<CartKey, Int>) =
        draft.update { it?.copy(cart = change(it.cart), error = null) }

    fun add(product: Product) = increase(CartKey(product.id, isGift = false))

    fun increase(key: CartKey) = updateCart { it.plusOne(key) }

    fun decrease(key: CartKey) = updateCart { it.minusOne(key) }

    fun toggleGift(key: CartKey) = updateCart { it.toggleGift(key) }

    fun setPayment(payment: PaymentMethod) = draft.update { it?.copy(payment = payment) }

    fun setDiscount(discount: DiscountInput) = draft.update { it?.copy(discount = discount, error = null) }

    fun closeEdit() {
        draft.value = null
    }

    fun saveEdit() {
        val current = draft.value ?: return
        current.discount.error?.let { error ->
            draft.update { it?.copy(error = error) }
            return
        }
        viewModelScope.launch {
            try {
                repository.editOrder(current.order.id, current.cart.toCartItems(), current.payment, current.discount.discount)
                draft.value = null
                _message.value = "Order #${current.order.id} updated"
            } catch (e: PosException) {
                draft.update { it?.copy(error = e.message) }
            }
        }
    }

    fun messageShown() {
        _message.value = null
    }
}
