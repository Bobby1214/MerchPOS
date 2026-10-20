package com.bobbykong.merchpos.domain

import com.bobbykong.merchpos.data.model.BundleDeal
import com.bobbykong.merchpos.data.model.DiscountType
import com.bobbykong.merchpos.data.model.OrderDiscount

data class PriceLine(val category: String, val unitPriceSen: Int, val qty: Int, val isGift: Boolean)

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
    fun calculate(lines: List<PriceLine>, bundles: List<BundleDeal>, discount: OrderDiscount?): PriceBreakdown {
        val paid = lines.filter { !it.isGift }
        val subtotal = paid.sumOf { it.unitPriceSen * it.qty }
        val giftValue = lines.filter { it.isGift }.sumOf { it.unitPriceSen * it.qty }

        val applied = paid.groupBy { it.category.lowercase() }.flatMap { (category, categoryLines) ->
            bestBundles(categoryLines, bundles.filter { it.isActive && it.category.lowercase() == category })
        }
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
     * Picks which bundles to apply to one category so the customer saves the most.
     *
     * Dynamic programming, like the coin-change problem:
     * the items are sorted most expensive first, and best[i] = most money saved using only the first i items.
     * For item i we either leave it out of any bundle (best[i-1]),
     * or end a bundle of size q here (best[i-q] + saving of those q items).
     */
    private fun bestBundles(lines: List<PriceLine>, bundles: List<BundleDeal>): List<AppliedBundle> {
        if (bundles.isEmpty()) return emptyList()
        val prices = lines.flatMap { line -> List(line.qty) { line.unitPriceSen } }.sortedDescending()
        val n = prices.size
        val prefix = LongArray(n + 1)
        for (i in 0 until n) prefix[i + 1] = prefix[i] + prices[i]

        val best = LongArray(n + 1)
        val choice = arrayOfNulls<BundleDeal>(n + 1)
        for (i in 1..n) {
            best[i] = best[i - 1]
            for (bundle in bundles) {
                if (bundle.qty < 1 || bundle.qty > i) continue
                val saving = prefix[i] - prefix[i - bundle.qty] - bundle.priceSen
                if (saving > 0 && best[i - bundle.qty] + saving > best[i]) {
                    best[i] = best[i - bundle.qty] + saving
                    choice[i] = bundle
                }
            }
        }

        // Walk backwards to find out which bundles made up the best answer
        val used = LinkedHashMap<BundleDeal, AppliedBundle>()
        var i = n
        while (i > 0) {
            val bundle = choice[i]
            if (bundle == null) {
                i--
                continue
            }
            val saving = (prefix[i] - prefix[i - bundle.qty] - bundle.priceSen).toInt()
            val previous = used[bundle]
            used[bundle] = AppliedBundle(bundle, (previous?.times ?: 0) + 1, (previous?.savingSen ?: 0) + saving)
            i -= bundle.qty
        }
        return used.values.toList()
    }
}
