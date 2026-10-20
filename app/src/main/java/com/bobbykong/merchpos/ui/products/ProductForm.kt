package com.bobbykong.merchpos.ui.products

import com.bobbykong.merchpos.data.model.Product
import com.bobbykong.merchpos.domain.Catalog
import com.bobbykong.merchpos.util.formatSenPlain
import com.bobbykong.merchpos.util.parseRmToSen

enum class ProductKind(val label: String) {
    // Has its own stock
    NORMAL("Normal"),

    // A set or a cheki: stock comes from its parts
    USES_PARTS("Uses parts"),

    // E.g. Instax film: has stock, hidden from Sell
    MATERIAL("Material"),
}

/** What the user typed in the product form. Kept as text until it's valid. */
data class ProductForm(
    val name: String = "",
    val category: String = "",
    val price: String = "",
    val stock: String = "",
    val isActive: Boolean = true,
    val imagePath: String? = null,
    val kind: ProductKind = ProductKind.NORMAL,
    // part id -> qty, in the order they were added
    val parts: Map<Int, Int> = emptyMap(),
) {
    val priceSen: Int? get() = parseRmToSen(price)
    val stockQty: Int? get() = stock.trim().toIntOrNull()?.takeIf { it >= 0 }

    /** The parts to save: only a "Uses parts" product keeps them. */
    val partsToSave: Map<Int, Int> get() = if (kind == ProductKind.USES_PARTS) parts else emptyMap()

    /** Returns an error message, or null if the form is valid. */
    fun validate(): String? = when {
        name.isBlank() -> "Name is required"
        category.isBlank() -> "Category is required"
        // A material isn't sold, so its price only matters if you type one
        kind != ProductKind.MATERIAL && priceSen == null -> "Enter a valid price, e.g. 12.50"
        kind == ProductKind.MATERIAL && price.isNotBlank() && priceSen == null -> "Enter a valid price, or leave it empty"
        kind == ProductKind.USES_PARTS && parts.isEmpty() -> "Add at least one part"
        kind != ProductKind.USES_PARTS && stockQty == null -> "Stock must be a whole number, 0 or more"
        else -> null
    }

    fun addPart(id: Int) = copy(parts = parts + (id to (parts[id] ?: 0) + 1))

    /** −1 on a part. At 0 it's removed. */
    fun removeOnePart(id: Int): ProductForm {
        val qty = (parts[id] ?: return this) - 1
        return copy(parts = if (qty <= 0) parts - id else parts + (id to qty))
    }

    companion object {
        fun from(product: Product?, catalog: Catalog = Catalog()): ProductForm {
            if (product == null) return ProductForm()
            val parts = catalog.partsOf(product.id).associate { it.partId to it.qty }
            return ProductForm(
                name = product.name,
                category = product.category,
                price = formatSenPlain(product.priceSen),
                stock = product.stockQty.toString(),
                isActive = product.isActive,
                imagePath = product.imagePath,
                kind = when {
                    parts.isNotEmpty() -> ProductKind.USES_PARTS
                    product.isMaterial -> ProductKind.MATERIAL
                    else -> ProductKind.NORMAL
                },
                parts = parts,
            )
        }
    }
}
