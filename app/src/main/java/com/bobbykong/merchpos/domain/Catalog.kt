package com.bobbykong.merchpos.domain

import com.bobbykong.merchpos.data.model.Category
import com.bobbykong.merchpos.data.model.Product
import com.bobbykong.merchpos.data.model.ProductPart

/**
 * All products plus their parts, with the stock maths in one place.
 *
 * Two kinds of stock:
 * - A product WITHOUT parts holds its own stock (stock_qty).
 * - A product WITH parts (a set, a cheki) has none. "How many can I make" is worked out from its parts.
 *   It's never stored: it would go stale every time a part is sold on its own (a derived attribute).
 */
class Catalog(
    storedProducts: List<Product> = emptyList(),
    partRows: List<ProductPart> = emptyList(),
    categoryOrder: List<Category> = emptyList(),
) {
    val parts: Map<Int, List<ProductPart>> = partRows.groupBy { it.productId }

    private val storedById = storedProducts.associateBy { it.id }

    /** Stock of every product that holds stock. Inactive products count as 0: they can't be used. */
    private val ownStock: Map<Int, Int> = storedProducts
        .filter { !hasParts(it.id) }
        .associate { it.id to if (it.isActive) it.stockQty else 0 }

    private val categoryRank = categoryOrder.associate { it.name to it.sortOrder }

    /**
     * Every product, in the order you dragged them: by category order, then by position inside the category.
     * For one with parts, stockQty = how many can be made right now.
     */
    val products: List<Product> = storedProducts
        .map { p -> if (hasParts(p.id)) p.copy(stockQty = canMake(p.id)) else p }
        .sortedWith(
            // Categories without a saved place go last, A→Z
            compareBy<Product> { categoryRank[it.category] ?: Int.MAX_VALUE }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.category }
                .thenBy { it.sortOrder }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
        )

    /** Category names of [list] in sidebar order. */
    fun categoriesOf(list: List<Product> = products): List<String> = list.map { it.category }.distinct()

    /** What the Sell screen shows: active, and not a material. */
    val sellable: List<Product> get() = products.filter { it.isActive && !it.isMaterial }

    fun partsOf(productId: Int): List<ProductPart> = parts[productId].orEmpty()

    fun hasParts(productId: Int): Boolean = partsOf(productId).isNotEmpty()

    fun nameOf(productId: Int): String = storedById[productId]?.name ?: "?"

    /** Sold qty per product → qty each stock-holding product loses. A set turns into its parts. */
    fun needs(soldQty: Map<Int, Int>): Map<Int, Int> {
        val result = mutableMapOf<Int, Int>()
        soldQty.forEach { (productId, qty) ->
            val recipe = partsOf(productId)
            if (recipe.isEmpty()) {
                result[productId] = (result[productId] ?: 0) + qty
            } else {
                recipe.forEach { result[it.partId] = (result[it.partId] ?: 0) + it.qty * qty }
            }
        }
        return result
    }

    /** Stock left once everything in [soldQty] is taken (e.g. the cart). */
    fun leftAfter(soldQty: Map<Int, Int>): Map<Int, Int> {
        val used = needs(soldQty)
        return ownStock.mapValues { (id, stock) -> stock - (used[id] ?: 0) }
    }

    /** How many of [productId] can be made from [left]. E.g. film 7 and a cheki uses 1 → 7. */
    fun canMake(productId: Int, left: Map<Int, Int> = ownStock): Int {
        val recipe = partsOf(productId)
        if (recipe.isEmpty()) return (left[productId] ?: 0).coerceAtLeast(0)
        return recipe.minOf { ((left[it.partId] ?: 0) / it.qty).coerceAtLeast(0) }
    }

    /** Products that use [productId] as a part. */
    fun usedIn(productId: Int): List<Product> =
        parts.filterValues { list -> list.any { it.partId == productId } }.keys.mapNotNull { storedById[it] }

    /** Products that may be picked as a part of [productId]: not itself, and not made of parts. */
    fun partChoices(productId: Int): List<Product> =
        products.filter { it.id != productId && !hasParts(it.id) }

    /**
     * Checks a new parts list for [productId] (0 = new product). Returns an error message, or null if OK.
     * Rule: only one level. A part can't have parts, and a product used as a part can't get parts.
     */
    fun partsError(productId: Int, isMaterial: Boolean, newParts: Map<Int, Int>): String? {
        if (newParts.isEmpty()) return null
        val name = storedById[productId]?.name ?: "This product"
        val stored = storedById[productId]
        return when {
            isMaterial -> "A material can't be made of parts"
            newParts.values.any { it < 1 } -> "Each part needs a quantity of 1 or more"
            productId in newParts -> "A product can't be a part of itself"
            newParts.keys.any { it !in storedById } -> "A part was deleted. Check the parts list."
            newParts.keys.any { hasParts(it) } ->
                "${nameOf(newParts.keys.first { hasParts(it) })} is made of parts itself, so it can't be a part"
            usedIn(productId).isNotEmpty() ->
                "$name is a part of ${usedIn(productId).first().name}, so it can't have parts"
            stored != null && !hasParts(productId) && stored.stockQty > 0 ->
                "$name still has ${stored.stockQty} in stock. Set its stock to 0 first, or add the set as a new product."
            else -> null
        }
    }
}
