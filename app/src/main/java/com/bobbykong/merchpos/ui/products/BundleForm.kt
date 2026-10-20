package com.bobbykong.merchpos.ui.products

import com.bobbykong.merchpos.data.model.BundleDeal
import com.bobbykong.merchpos.data.model.BundleType
import com.bobbykong.merchpos.data.model.BundleWithProducts
import com.bobbykong.merchpos.util.formatSenPlain
import com.bobbykong.merchpos.util.parseRmToSen

data class BundleForm(
    val name: String = "",
    val type: BundleType = BundleType.CATEGORY,
    val category: String = "",
    val productIds: Set<Int> = emptySet(),
    val qty: String = "",
    val price: String = "",
    val isActive: Boolean = true,
) {
    val qtyValue: Int? get() = qty.trim().toIntOrNull()?.takeIf { it >= 2 }
    val priceSen: Int? get() = parseRmToSen(price)

    fun validate(): String? = when {
        type == BundleType.CATEGORY && category.isBlank() -> "Category is required"
        type == BundleType.PRODUCTS && productIds.isEmpty() -> "Tick at least one product"
        qtyValue == null -> "Bundle size must be 2 or more"
        priceSen == null -> "Enter a valid price, e.g. 20"
        else -> null
    }

    fun toggleProduct(id: Int) = copy(productIds = if (id in productIds) productIds - id else productIds + id)

    /**
     * Call only after [validate] returns null. [productNames] (id -> name) is used for the default name:
     * "Any 3 Badge", "3 × Raiden Badge" or "Any 3 of Raiden Badge, Furina Badge".
     */
    fun toBundle(id: Int, productNames: Map<Int, String> = emptyMap()): BundleDeal {
        val qty = checkNotNull(qtyValue)
        val defaultName = defaultName(qty, productNames)
        return BundleDeal(
            id = id,
            name = name.trim().ifEmpty { defaultName },
            type = type,
            category = if (type == BundleType.CATEGORY) category.trim() else null,
            qty = qty,
            priceSen = checkNotNull(priceSen),
            isActive = isActive,
        )
    }

    private fun defaultName(qty: Int, productNames: Map<Int, String>): String = when (type) {
        BundleType.CATEGORY -> "Any $qty ${category.trim()}"
        BundleType.PRODUCTS -> {
            val names = productIds.mapNotNull { productNames[it] }.sorted()
            if (names.size == 1) "$qty × ${names.single()}" else "Any $qty of ${names.joinToString()}"
        }
    }

    companion object {
        fun from(item: BundleWithProducts?): BundleForm {
            val bundle = item?.bundle ?: return BundleForm()
            val form = BundleForm(
                name = bundle.name,
                type = bundle.type,
                category = bundle.category.orEmpty(),
                productIds = item.products.map { it.id }.toSet(),
                qty = bundle.qty.toString(),
                price = formatSenPlain(bundle.priceSen),
                isActive = bundle.isActive,
            )
            // An auto-made name is left blank, so it updates if you change the qty or products
            val auto = form.defaultName(bundle.qty, item.products.associate { it.id to it.name })
            return if (bundle.name == auto) form.copy(name = "") else form
        }
    }
}
