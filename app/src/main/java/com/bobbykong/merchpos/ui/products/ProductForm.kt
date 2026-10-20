package com.bobbykong.merchpos.ui.products

import com.bobbykong.merchpos.data.model.Product
import com.bobbykong.merchpos.util.formatSenPlain
import com.bobbykong.merchpos.util.parseRmToSen

/** What the user typed in the product form. Kept as text until it's valid. */
data class ProductForm(
    val name: String = "",
    val category: String = "",
    val price: String = "",
    val stock: String = "",
    val isActive: Boolean = true,
    val imagePath: String? = null,
) {
    val priceSen: Int? get() = parseRmToSen(price)
    val stockQty: Int? get() = stock.trim().toIntOrNull()?.takeIf { it >= 0 }

    /** Returns an error message, or null if the form is valid. */
    fun validate(): String? = when {
        name.isBlank() -> "Name is required"
        category.isBlank() -> "Category is required"
        priceSen == null -> "Enter a valid price, e.g. 12.50"
        stockQty == null -> "Stock must be a whole number, 0 or more"
        else -> null
    }

    companion object {
        fun from(product: Product?): ProductForm =
            if (product == null) {
                ProductForm()
            } else {
                ProductForm(
                    name = product.name,
                    category = product.category,
                    price = formatSenPlain(product.priceSen),
                    stock = product.stockQty.toString(),
                    isActive = product.isActive,
                    imagePath = product.imagePath,
                )
            }
    }
}
