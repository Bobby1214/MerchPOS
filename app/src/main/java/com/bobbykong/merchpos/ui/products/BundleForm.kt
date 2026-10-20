package com.bobbykong.merchpos.ui.products

import com.bobbykong.merchpos.data.model.BundleDeal
import com.bobbykong.merchpos.util.formatSenPlain
import com.bobbykong.merchpos.util.parseRmToSen

data class BundleForm(
    val name: String = "",
    val category: String = "",
    val qty: String = "",
    val price: String = "",
    val isActive: Boolean = true,
) {
    val qtyValue: Int? get() = qty.trim().toIntOrNull()?.takeIf { it >= 2 }
    val priceSen: Int? get() = parseRmToSen(price)

    fun validate(): String? = when {
        category.isBlank() -> "Category is required"
        qtyValue == null -> "Bundle size must be 2 or more"
        priceSen == null -> "Enter a valid price, e.g. 20"
        else -> null
    }

    /** Call only after [validate] returns null. A blank name becomes e.g. "Any 3 Badge". */
    fun toBundle(id: Int): BundleDeal {
        val qty = checkNotNull(qtyValue)
        val category = category.trim()
        return BundleDeal(
            id = id,
            name = name.trim().ifEmpty { "Any $qty $category" },
            category = category,
            qty = qty,
            priceSen = checkNotNull(priceSen),
            isActive = isActive,
        )
    }

    companion object {
        fun from(bundle: BundleDeal?): BundleForm =
            if (bundle == null) {
                BundleForm()
            } else {
                BundleForm(bundle.name, bundle.category, bundle.qty.toString(), formatSenPlain(bundle.priceSen), bundle.isActive)
            }
    }
}
