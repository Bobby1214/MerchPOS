package com.bobbykong.merchpos.ui.sell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bobbykong.merchpos.data.PosException
import com.bobbykong.merchpos.data.PosRepository
import com.bobbykong.merchpos.data.model.Event
import com.bobbykong.merchpos.data.model.OrderDiscount
import com.bobbykong.merchpos.data.model.PaymentMethod
import com.bobbykong.merchpos.data.model.Product
import com.bobbykong.merchpos.domain.BundleRule
import com.bobbykong.merchpos.domain.Catalog
import com.bobbykong.merchpos.domain.CartKey
import com.bobbykong.merchpos.domain.PriceBreakdown
import com.bobbykong.merchpos.domain.PriceCalculator
import com.bobbykong.merchpos.domain.PriceLine
import com.bobbykong.merchpos.domain.minusOne
import com.bobbykong.merchpos.domain.plusOne
import com.bobbykong.merchpos.domain.toCartItems
import com.bobbykong.merchpos.domain.toggleGift
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CartLine(val product: Product, val qty: Int, val isGift: Boolean) {
    val key: CartKey get() = CartKey(product.id, isGift)
}

data class SellUiState(
    // True until the database answers, so the screen doesn't flash "No event running" at start-up
    val loading: Boolean = true,
    val event: Event? = null,
    val products: List<Product> = emptyList(),
    val categories: List<String> = emptyList(),
    val selectedCategory: String? = null,
    val cart: List<CartLine> = emptyList(),
    val bundles: List<BundleRule> = emptyList(),
    val catalog: Catalog = Catalog(),
) {
    val visibleProducts: List<Product>
        get() = if (selectedCategory == null) products else products.filter { it.category == selectedCategory }
    val itemCount: Int get() = cart.sumOf { it.qty }
    val price: PriceBreakdown = priceWith(null)

    fun priceWith(discount: OrderDiscount?): PriceBreakdown = PriceCalculator.calculate(
        cart.map { PriceLine(it.product.id, it.product.category, it.product.priceSen, it.qty, it.isGift) },
        bundles,
        discount,
    )

    fun qtyInCart(productId: Int): Int = cart.filter { it.product.id == productId }.sumOf { it.qty }
}

class SellViewModel(private val repository: PosRepository) : ViewModel() {
    private val cart = MutableStateFlow<Map<CartKey, Int>>(emptyMap())
    private val category = MutableStateFlow<String?>(null)

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val uiState: StateFlow<SellUiState> =
        combine(
            repository.openEvent,
            repository.catalog,
            repository.activeBundles,
            cart,
            category,
        ) { event, catalog, bundles, cart, category ->
            val products = catalog.sellable
            val byId = products.associateBy { it.id }
            val categories = products.map { it.category }.distinct().sorted()
            // Drop deactivated products, and never show more than is in stock (paid + gift together).
            // Stock is shared: 2 cheki types use the same film, a set uses the same posters as single sales.
            val taken = mutableMapOf<Int, Int>()
            val lines = cart.mapNotNull { (key, qty) ->
                val product = byId[key.productId] ?: return@mapNotNull null
                val shown = qty.coerceAtMost(catalog.canMake(product.id, catalog.leftAfter(taken)))
                taken[product.id] = (taken[product.id] ?: 0) + shown
                CartLine(product, shown, key.isGift).takeIf { shown > 0 }
            }
            SellUiState(
                loading = false,
                event = event,
                products = products,
                categories = categories,
                selectedCategory = category?.takeIf { it in categories },
                cart = lines,
                bundles = bundles,
                catalog = catalog,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SellUiState())

    fun add(product: Product) = increase(CartKey(product.id, isGift = false), product)

    fun increase(key: CartKey, product: Product) {
        val state = uiState.value
        val inCart = state.cart.groupingBy { it.product.id }.fold(0) { sum, line -> sum + line.qty }
        if (state.catalog.canMake(product.id, state.catalog.leftAfter(inCart)) < 1) {
            val parts = state.catalog.partsOf(product.id)
            _message.value =
                if (parts.isEmpty()) "Only ${product.stockQty} ${product.name} in stock"
                else "Not enough " + parts.joinToString(" / ") { state.catalog.nameOf(it.partId) } + " for another ${product.name}"
            return
        }
        cart.update { it.plusOne(key) }
    }

    fun decrease(key: CartKey) = cart.update { it.minusOne(key) }

    fun toggleGift(key: CartKey) = cart.update { it.toggleGift(key) }

    fun clearCart() {
        cart.value = emptyMap()
    }

    fun selectCategory(category: String?) {
        this.category.value = category
    }

    fun checkout(payment: PaymentMethod, discount: OrderDiscount?) {
        val items = uiState.value.cart.associate { it.key to it.qty }.toCartItems()
        viewModelScope.launch {
            try {
                val orderId = repository.checkout(items, payment, discount)
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
