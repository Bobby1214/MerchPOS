package com.bobbykong.merchpos

import com.bobbykong.merchpos.data.model.BundleDeal
import com.bobbykong.merchpos.data.model.DiscountType
import com.bobbykong.merchpos.data.model.OrderDiscount
import com.bobbykong.merchpos.data.model.BundleType
import com.bobbykong.merchpos.domain.BundleRule
import com.bobbykong.merchpos.domain.PriceCalculator
import com.bobbykong.merchpos.domain.PriceLine
import org.junit.Assert.assertEquals
import org.junit.Test

class PriceCalculatorTest {
    private val threeBadges = BundleDeal(id = 1, name = "3 Badges", category = "Badge", qty = 3, priceSen = 2000)
    private val fiveBadges = BundleDeal(id = 2, name = "5 Badges", category = "Badge", qty = 5, priceSen = 3000)

    private fun rules(vararg b: BundleDeal) = b.map { BundleRule(it) }

    private fun badge(qty: Int, price: Int = 800, gift: Boolean = false) = PriceLine(productId = price, category = "Badge", unitPriceSen = price, qty = qty, isGift = gift)

    @Test
    fun noBundlesNoDiscount() {
        val p = PriceCalculator.calculate(listOf(badge(2), PriceLine(99, "Print", 1500, 1, false)), emptyList(), null)
        assertEquals(3100, p.subtotalSen)
        assertEquals(3100, p.totalSen)
    }

    @Test
    fun giftsAreFreeButCounted() {
        val p = PriceCalculator.calculate(listOf(badge(2), badge(1, gift = true)), emptyList(), null)
        assertEquals(1600, p.subtotalSen)
        assertEquals(800, p.giftValueSen)
        assertEquals(1600, p.totalSen)
    }

    @Test
    fun bundleAppliesPerFullSet() {
        // 7 badges at RM8: two sets of 3 (save RM4 each), 1 left at full price
        val p = PriceCalculator.calculate(listOf(badge(7)), rules(threeBadges), null)
        assertEquals(5600, p.subtotalSen)
        assertEquals(800, p.bundleDiscountSen)
        assertEquals(4800, p.totalSen)
        assertEquals(2, p.appliedBundles.single().times)
    }

    @Test
    fun bundlePicksTheMostExpensiveItems() {
        // RM10, RM10, RM10, RM5: bundle the three RM10 ones (save RM10), not include the RM5
        val p = PriceCalculator.calculate(listOf(badge(3, price = 1000), badge(1, price = 500)), rules(threeBadges), null)
        assertEquals(1000, p.bundleDiscountSen)
    }

    @Test
    fun picksBestMixOfBundles() {
        // 8 badges at RM8 = RM64. Best: 5 for RM30 + 3 for RM20 = RM50 (save RM14)
        val p = PriceCalculator.calculate(listOf(badge(8)), rules(threeBadges, fiveBadges), null)
        assertEquals(1400, p.bundleDiscountSen)
        assertEquals(5000, p.totalSen)
    }

    @Test
    fun bundleIgnoresGiftsOtherCategoriesAndBadDeals() {
        val gifts = listOf(badge(2), badge(1, gift = true))
        assertEquals(0, PriceCalculator.calculate(gifts, rules(threeBadges), null).bundleDiscountSen)
        val print = listOf(PriceLine(99, "Print", 800, 3, false))
        assertEquals(0, PriceCalculator.calculate(print, rules(threeBadges), null).bundleDiscountSen)
        // "3 for RM30" costs more than 3 × RM8, so it's never applied
        val bad = threeBadges.copy(priceSen = 3000)
        assertEquals(0, PriceCalculator.calculate(listOf(badge(3)), rules(bad), null).bundleDiscountSen)
        val off = threeBadges.copy(isActive = false)
        assertEquals(0, PriceCalculator.calculate(listOf(badge(3)), rules(off), null).bundleDiscountSen)
    }

    @Test
    fun categoryMatchIgnoresCase() {
        val p = PriceCalculator.calculate(listOf(PriceLine(1, "badge", 800, 3, false)), rules(threeBadges), null)
        assertEquals(400, p.bundleDiscountSen)
    }

    @Test
    fun fixedDiscountNeverGoesBelowZero() {
        assertEquals(500, PriceCalculator.discountAmount(2000, OrderDiscount(DiscountType.FIXED, 500)))
        assertEquals(2000, PriceCalculator.discountAmount(2000, OrderDiscount(DiscountType.FIXED, 9999)))
        assertEquals(0, PriceCalculator.discountAmount(2000, null))
    }

    @Test
    fun percentDiscountRoundsTotalToWholeRinggitHalfUp() {
        // 15% off RM13.30 = RM11.305 -> RM11
        assertEquals(230, PriceCalculator.discountAmount(1330, OrderDiscount(DiscountType.PERCENT, 15)))
        // 50% off RM39 = RM19.50 -> RM20 (exactly .50 rounds up)
        assertEquals(1900, PriceCalculator.discountAmount(3900, OrderDiscount(DiscountType.PERCENT, 50)))
        // 100% off = free
        assertEquals(3900, PriceCalculator.discountAmount(3900, OrderDiscount(DiscountType.PERCENT, 100)))
        // 1% off RM12.90 = RM12.771 -> RM13 would be MORE than before, so no change
        assertEquals(0, PriceCalculator.discountAmount(1290, OrderDiscount(DiscountType.PERCENT, 1)))
    }

    @Test
    fun discountAppliesAfterGiftsAndBundles() {
        // 3 badges bundle = RM20, + 1 gift. 10% off RM20 = RM18
        val p = PriceCalculator.calculate(
            listOf(badge(3), badge(1, gift = true)),
            rules(threeBadges),
            OrderDiscount(DiscountType.PERCENT, 10),
        )
        assertEquals(2400, p.subtotalSen)
        assertEquals(400, p.bundleDiscountSen)
        assertEquals(200, p.discountSen)
        assertEquals(1800, p.totalSen)
    }

    // Products: Raiden RM10 (id 1), Furina RM10 (id 2), Zhongli RM8 (id 3), all Badges
    private fun item(id: Int, price: Int, qty: Int) = PriceLine(id, "Badge", price, qty, false)
    private val raidenOrFurina = BundleRule(
        BundleDeal(id = 10, name = "Any 3 of Raiden/Furina", type = BundleType.PRODUCTS, qty = 3, priceSen = 2500),
        productIds = setOf(1, 2),
    )

    @Test
    fun productBundleOnlyCountsTickedProducts() {
        // 2 Raiden + 1 Furina = RM30 -> RM25
        val mixed = PriceCalculator.calculate(listOf(item(1, 1000, 2), item(2, 1000, 1)), listOf(raidenOrFurina), null)
        assertEquals(500, mixed.bundleDiscountSen)
        // 2 Raiden + 1 Zhongli: Zhongli isn't ticked, so no bundle
        val notTicked = PriceCalculator.calculate(listOf(item(1, 1000, 2), item(3, 800, 1)), listOf(raidenOrFurina), null)
        assertEquals(0, notTicked.bundleDiscountSen)
    }

    @Test
    fun productAndCategoryBundlesPickBestMix() {
        // 3 Raiden + 3 Zhongli = RM54. "3 Badges RM20" on both sets saves 10 + 4 = 14,
        // better than Raiden/Furina RM25 (save 5) + 3 Badges on Zhongli (save 4) = 9
        val p = PriceCalculator.calculate(
            listOf(item(1, 1000, 3), item(3, 800, 3)),
            listOf(raidenOrFurina) + rules(threeBadges),
            null,
        )
        assertEquals(1400, p.bundleDiscountSen)
        // With a weak category deal (3 for RM28: saves 2 on Raidens, nothing on Zhongli), the product deal wins
        val p2 = PriceCalculator.calculate(
            listOf(item(1, 1000, 3), item(3, 800, 3)),
            listOf(raidenOrFurina) + rules(threeBadges.copy(priceSen = 2800)),
            null,
        )
        assertEquals(500, p2.bundleDiscountSen)
    }
}
