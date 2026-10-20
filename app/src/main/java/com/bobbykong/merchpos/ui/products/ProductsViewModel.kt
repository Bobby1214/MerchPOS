package com.bobbykong.merchpos.ui.products

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bobbykong.merchpos.data.PosException
import com.bobbykong.merchpos.data.PosRepository
import com.bobbykong.merchpos.data.files.ImageStore
import com.bobbykong.merchpos.data.model.BundleDeal
import com.bobbykong.merchpos.data.model.BundleWithProducts
import com.bobbykong.merchpos.data.model.Product
import com.bobbykong.merchpos.domain.Catalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The product the user wants to delete, and how many orders it's in (0 = can be deleted). */
data class PendingProductDelete(val product: Product, val orderCount: Int)

class ProductsViewModel(
    private val repository: PosRepository,
    // Application context (not an Activity), so holding it in a ViewModel doesn't leak a screen
    private val app: Application,
) : ViewModel() {
    val catalog: StateFlow<Catalog> =
        repository.catalog.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Catalog())

    val products: StateFlow<List<Product>> =
        repository.allProducts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val bundles: StateFlow<List<BundleWithProducts>> =
        repository.allBundles.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _pendingDelete = MutableStateFlow<PendingProductDelete?>(null)
    val pendingDelete: StateFlow<PendingProductDelete?> = _pendingDelete.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** Step 1: check whether it was ever sold, so the dialog can offer Delete or Deactivate. */
    fun requestDelete(product: Product) {
        viewModelScope.launch {
            _pendingDelete.value = PendingProductDelete(product, repository.orderCountForProduct(product.id))
        }
    }

    fun dismissDelete() {
        _pendingDelete.value = null
    }

    fun confirmDelete() {
        val product = _pendingDelete.value?.product ?: return
        _pendingDelete.value = null
        viewModelScope.launch {
            try {
                repository.deleteProduct(product.id)
                ImageStore.delete(app, product.imagePath)
                _message.value = "Deleted ${product.name}"
            } catch (e: PosException) {
                _message.value = e.message
            }
        }
    }

    fun deactivateInstead() {
        val product = _pendingDelete.value?.product ?: return
        _pendingDelete.value = null
        viewModelScope.launch {
            repository.updateProduct(product.copy(isActive = false), stockBefore = product.stockQty)
            _message.value = "${product.name} deactivated"
        }
    }

    fun messageShown() {
        _message.value = null
    }

    /**
     * Saves a new product ([original] = null) or an edit. Returns an error message, or null on success.
     * Parts rules are checked here first, so the error shows inside the form.
     */
    fun save(original: Product?, form: ProductForm): String? {
        form.validate()?.let { return it }
        val isMaterial = form.kind == ProductKind.MATERIAL
        catalog.value.partsError(original?.id ?: 0, isMaterial, form.partsToSave)?.let { return it }
        val name = form.name.trim()
        val category = form.category.trim()
        val priceSen = form.priceSen ?: 0
        val stockQty = form.stockQty ?: 0

        viewModelScope.launch {
            try {
                if (original == null) {
                    repository.addProduct(name, category, priceSen, stockQty, form.imagePath, isMaterial, form.partsToSave)
                } else {
                    repository.updateProduct(
                        original.copy(
                            name = name,
                            category = category,
                            priceSen = priceSen,
                            stockQty = stockQty,
                            isActive = form.isActive,
                            imagePath = form.imagePath,
                            isMaterial = isMaterial,
                        ),
                        stockBefore = original.stockQty,
                        parts = form.partsToSave,
                    )
                    // The old photo was replaced or removed
                    if (original.imagePath != form.imagePath) ImageStore.delete(app, original.imagePath)
                }
            } catch (e: PosException) {
                _message.value = e.message
            }
        }
        return null
    }

    /** Copies the image into app storage. Returns its file name, or null if it can't be read. */
    suspend fun importImage(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching { ImageStore.import(app, uri) }.getOrNull()
    }

    /** Deletes a photo that was picked in the form but never saved. */
    fun discardImage(name: String?) = ImageStore.delete(app, name)

    fun saveBundle(original: BundleDeal?, form: BundleForm): String? {
        form.validate()?.let { return it }
        val names = products.value.associate { it.id to it.name }
        viewModelScope.launch { repository.saveBundle(form.toBundle(original?.id ?: 0, names), form.productIds) }
        return null
    }

    fun reorderProducts(ids: List<Int>) {
        viewModelScope.launch { repository.reorderProducts(ids) }
    }

    fun reorderCategories(names: List<String>) {
        viewModelScope.launch { repository.reorderCategories(names) }
    }

    fun deleteBundle(bundle: BundleDeal) {
        viewModelScope.launch { repository.deleteBundle(bundle) }
    }
}
