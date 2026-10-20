package com.bobbykong.merchpos.ui.products

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bobbykong.merchpos.data.PosRepository
import com.bobbykong.merchpos.data.model.Product
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProductsViewModel(private val repository: PosRepository) : ViewModel() {
    val products: StateFlow<List<Product>> =
        repository.allProducts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Saves a new product ([original] = null) or an edit. Returns an error message, or null on success. */
    fun save(original: Product?, form: ProductForm): String? {
        form.validate()?.let { return it }
        val name = form.name.trim()
        val category = form.category.trim()
        val priceSen = checkNotNull(form.priceSen)
        val stockQty = checkNotNull(form.stockQty)

        viewModelScope.launch {
            if (original == null) {
                repository.addProduct(name, category, priceSen, stockQty)
            } else {
                repository.updateProduct(
                    original.copy(
                        name = name,
                        category = category,
                        priceSen = priceSen,
                        stockQty = stockQty,
                        isActive = form.isActive,
                    ),
                    stockBefore = original.stockQty,
                )
            }
        }
        return null
    }
}
