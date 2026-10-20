package com.bobbykong.merchpos.ui.products

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bobbykong.merchpos.data.PosRepository
import com.bobbykong.merchpos.data.files.ImageStore
import com.bobbykong.merchpos.data.model.BundleDeal
import com.bobbykong.merchpos.data.model.Product
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProductsViewModel(
    private val repository: PosRepository,
    // Application context (not an Activity), so holding it in a ViewModel doesn't leak a screen
    private val app: Application,
) : ViewModel() {
    val products: StateFlow<List<Product>> =
        repository.allProducts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val bundles: StateFlow<List<BundleDeal>> =
        repository.allBundles.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Saves a new product ([original] = null) or an edit. Returns an error message, or null on success. */
    fun save(original: Product?, form: ProductForm): String? {
        form.validate()?.let { return it }
        val name = form.name.trim()
        val category = form.category.trim()
        val priceSen = checkNotNull(form.priceSen)
        val stockQty = checkNotNull(form.stockQty)

        viewModelScope.launch {
            if (original == null) {
                repository.addProduct(name, category, priceSen, stockQty, form.imagePath)
            } else {
                repository.updateProduct(
                    original.copy(
                        name = name,
                        category = category,
                        priceSen = priceSen,
                        stockQty = stockQty,
                        isActive = form.isActive,
                        imagePath = form.imagePath,
                    ),
                    stockBefore = original.stockQty,
                )
                // The old photo was replaced or removed
                if (original.imagePath != form.imagePath) ImageStore.delete(app, original.imagePath)
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
        viewModelScope.launch { repository.saveBundle(form.toBundle(original?.id ?: 0)) }
        return null
    }

    fun deleteBundle(bundle: BundleDeal) {
        viewModelScope.launch { repository.deleteBundle(bundle) }
    }
}
