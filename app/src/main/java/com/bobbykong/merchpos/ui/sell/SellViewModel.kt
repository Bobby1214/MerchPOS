package com.bobbykong.merchpos.ui.sell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bobbykong.merchpos.data.CartItem
import com.bobbykong.merchpos.data.PosException
import com.bobbykong.merchpos.data.PosRepository
import com.bobbykong.merchpos.data.model.Event
import com.bobbykong.merchpos.data.model.PaymentMethod
import com.bobbykong.merchpos.data.model.Product
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CartLine(val product: Product, val qty: Int) {
    val lineTotalSen: Int get() = product.priceSen * qty
}

data class SellUiState(
    val event: Event? = null,
    val products: List<Product> = emptyList(),
    val categories: List<String> = emptyList(),
    val selectedCategory: String? = null,
    val cart: List<CartLine> = emptyList(),
) {
    val visibleProducts: List<Product>
        get() = if (selectedCategory == null) products else products.filter { it.category == selectedCategory }
    val totalSen: Int get() = cart.sumOf { it.lineTotalSen }
    val itemCount: Int get() = cart.sumOf { it.qty }

    fun qtyInCart(productId: Int): Int = cart.firstOrNull { it.product.id == productId }?.qty ?: 0
}

class SellViewModel(private val repository: PosRepository) : ViewModel() {
    // productId -> qty. A Map keeps one entry per product, in the order they were added.
    private val cart = MutableStateFlow<Map<Int, Int>>(emptyMap())
    private val category = MutableStateFlow<String?>(null)

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val uiState: StateFlow<SellUiState> =
        combine(repository.openEvent, repository.activeProducts, cart, category) { event, products, cart, category ->
            val byId = products.associateBy { it.id }
            val categories = products.map { it.category }.distinct()
            SellUiState(
                event = event,
                products = products,
                categories = categories,
                selectedCategory = category?.takeIf { it in categories },
                // Drop products that were deactivated, and never show more than is in stock
                cart = cart.mapNotNull { (id, qty) ->
                    byId[id]?.let { CartLine(it, qty.coerceAtMost(it.stockQty)) }
                }.filter { it.qty > 0 },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SellUiState())

    fun add(product: Product) {
        val inCart = cart.value[product.id] ?: 0
        if (inCart >= product.stockQty) {
            _message.value = "Only ${product.stockQty} ${product.name} in stock"
            return
        }
        cart.update { it + (product.id to inCart + 1) }
    }

    fun decrease(productId: Int) {
        cart.update {
            val qty = (it[productId] ?: return@update it) - 1
            if (qty <= 0) it - productId else it + (productId to qty)
        }
    }

    fun clearCart() {
        cart.value = emptyMap()
    }

    fun selectCategory(category: String?) {
        this.category.value = category
    }

    fun checkout(payment: PaymentMethod) {
        val items = uiState.value.cart.map { CartItem(it.product.id, it.qty) }
        viewModelScope.launch {
            try {
                val orderId = repository.checkout(items, payment)
                cart.value = emptyMap()
                _message.value = "Order #$orderId saved"
            } catch (e: PosException) {
                _message.value = e.message
            }
        }
    }

    fun startEvent(name: String) {
        viewModelScope.launch {
            try {
                repository.startEvent(name)
            } catch (e: PosException) {
                _message.value = e.message
            }
        }
    }

    fun messageShown() {
        _message.value = null
    }
}
