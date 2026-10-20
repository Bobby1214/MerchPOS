package com.bobbykong.merchpos.domain

import com.bobbykong.merchpos.data.model.BundleDeal
import com.bobbykong.merchpos.data.model.BundleType
import com.bobbykong.merchpos.data.model.BundleWithProducts
import com.bobbykong.merchpos.data.model.DiscountType
import com.bobbykong.merchpos.data.model.OrderDiscount

data class PriceLine(val productId: Int, val category: String, val unitPriceSen: Int, val qty: Int, val isGift: Boolean)

/** A bundle plus the products it accepts (only used by PRODUCTS bundles). */
data class BundleRule(val bundle: BundleDeal, val productIds: Set<Int> = emptySet()) {
    fun accepts(line: PriceLine): Boolean = when (bundle.type) {
        BundleType.CATEGORY -> bundle.category.equals(line.category, ignoreCase = true)
        BundleType.PRODUCTS -> line.productId in productIds
    }
}

fun BundleWithProducts.toRule() = BundleRule(bundle, products.map { it.id }.toSet())

data class AppliedBundle(val bundle: BundleDeal, val times: Int, val savingSen: Int)

data class PriceBreakdown(
    val subtotalSen: Int = 0,
    val giftValueSen: Int = 0,
    val appliedBundles: List<AppliedBundle> = emptyList(),
    val bundleDiscountSen: Int = 0,
    val discountSen: Int = 0,
    val totalSen: Int = 0,
)

/**
 * The single source of truth for order totals. The cart screen and the repository both call it,
 * so what the customer sees is exactly what gets saved.
 *
 * Order of steps:
 * 1. Gifts are left out (charged RM0).
 * 2. Bundles are applied to the paid items.
 * 3. The order discount is applied to what's left. The total never goes below RM0.
 */
object PriceCalculator {
    fun calculate(lines: List<PriceLine>, bundles: List<BundleRule>, discount: OrderDiscount?): PriceBreakdown {
        val paid = lines.filter { !it.isGift }
        val subtotal = paid.sumOf { it.unitPriceSen * it.qty }
        val giftValue = lines.filter { it.isGift }.sumOf { it.unitPriceSen * it.qty }

        val applied = bestBundles(paid, bundles)
        val bundleDiscount = applied.sumOf { it.savingSen }
        val afterBundles = subtotal - bundleDiscount
        val discountSen = discountAmount(afterBundles, discount)

        return PriceBreakdown(
            subtotalSen = subtotal,
            giftValueSen = giftValue,
            appliedBundles = applied,
            bundleDiscountSen = bundleDiscount,
            discountSen = discountSen,
            totalSen = afterBundles - discountSen,
        )
    }

    /** How much [discount] takes off [amountSen]. Never more than [amountSen]. */
    fun discountAmount(amountSen: Int, discount: OrderDiscount?): Int {
        if (discount == null || amountSen <= 0) return 0
        val total = when (discount.type) {
            DiscountType.FIXED -> amountSen - discount.value
            DiscountType.PERCENT -> {
                // Your rule: the discounted total is rounded to whole RM, and .50 rounds up.
                // Integer maths only: amount × (100 − %) is the total × 100.
                val totalTimes100 = amountSen.toLong() * (100 - discount.value.coerceIn(0, 100))
                val rounded = (totalTimes100 + 5_000) / 10_000 * 100
                // Rounding up must never make the "discount" negative
                rounded.toInt().coerceAtMost(amountSen)
            }
        }
        return amountSen - total.coerceAtLeast(0)
    }

    /**
     * Picks which bundles to apply so the customer saves the most. Each item can be in only one bundle.
     *
     * Search with memoization (dynamic programming): the "state" is how many of each product are still free.
     * best(state) = the most money we can still save. For each bundle that fits, we try it
     * (using its most expensive eligible items) and add best(what's left). The memo map remembers
     * every state we already solved, so the same state is never worked out twice.
     */
    private fun bestBundles(paid: List<PriceLine>, rules: List<BundleRule>): List<AppliedBundle> {
        // One slot per product, most expensive first
        val slots = paid.groupBy { it.productId }
            .map { (_, lines) -> lines.first().copy(qty = lines.sumOf { it.qty }) }
            .sortedByDescending { it.unitPriceSen }
        val usable = rules.filter { it.bundle.isActive && it.bundle.qty >= 1 }
        if (slots.isEmpty() || usable.isEmpty()) return emptyList()
        // For each bundle: which slots it may take items from (still most expensive first)
        val eligible = usable.map { rule -> slots.indices.filter { rule.accepts(slots[it]) } }

        /** Uses bundle [r] once on [counts]. Returns (saving, counts left), or null if there aren't enough items. */
        fun apply(counts: List<Int>, r: Int): Pair<Long, List<Int>>? {
            val left = counts.toMutableList()
            var need = usable[r].bundle.qty
            var normalPrice = 0L
            for (i in eligible[r]) {
                val take = minOf(left[i], need)
                left[i] -= take
                need -= take
                normalPrice += take.toLong() * slots[i].unitPriceSen
                if (need == 0) break
            }
            return if (need > 0) null else (normalPrice - usable[r].bundle.priceSen) to left
        }

        // state -> (best saving from here, which bundle to use first; -1 = none)
        val memo = HashMap<List<Int>, Pair<Long, Int>>()
        fun best(counts: List<Int>): Long {
            memo[counts]?.let { return it.first }
            var bestSaving = 0L
            var bestRule = -1
            for (r in usable.indices) {
                val (saving, left) = apply(counts, r) ?: continue
                if (saving <= 0) continue // a "deal" that costs more than normal is never used
                val total = saving + best(left)
                if (total > bestSaving) {
                    bestSaving = total
                    bestRule = r
                }
            }
            memo[counts] = bestSaving to bestRule
            return bestSaving
        }

        var counts = slots.map { it.qty }
        best(counts)

        // Follow the remembered choices to list which bundles were used
        val used = LinkedHashMap<Int, AppliedBundle>()
        while (true) {
            val r = memo.getValue(counts).second
            if (r < 0) break
            val (saving, left) = checkNotNull(apply(counts, r))
            val bundle = usable[r].bundle
            val previous = used[bundle.id]
            used[bundle.id] = AppliedBundle(bundle, (previous?.times ?: 0) + 1, (previous?.savingSen ?: 0) + saving.toInt())
            counts = left
        }
        return used.values.toList()
    }
}
