package com.bobbykong.merchpos

import com.bobbykong.merchpos.data.files.csvField
import com.bobbykong.merchpos.data.model.BundleType
import com.bobbykong.merchpos.data.model.BundleWithProducts
import com.bobbykong.merchpos.data.model.DiscountType
import com.bobbykong.merchpos.data.model.OrderDiscount
import com.bobbykong.merchpos.data.model.Product
import com.bobbykong.merchpos.domain.CartKey
import com.bobbykong.merchpos.domain.minusOne
import com.bobbykong.merchpos.domain.plusOne
import com.bobbykong.merchpos.domain.qtyOf
import com.bobbykong.merchpos.domain.toggleGift
import com.bobbykong.merchpos.ui.components.DiscountInput
import com.bobbykong.merchpos.ui.products.BundleForm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FormsAndCartTest {
    @Test
    fun cartGiftToggleMergesLines() {
        val paid = CartKey(1, isGift = false)
        val gift = CartKey(1, isGift = true)
        // Customer buys 3 and gets 1 free
        var cart = emptyMap<CartKey, Int>().plusOne(paid).toggleGift(paid)
        cart = cart.plusOne(paid).plusOne(paid).plusOne(paid)
        assertEquals(mapOf(gift to 1, paid to 3), cart)
        assertEquals(4, cart.qtyOf(1))
        assertEquals(mapOf(paid to 4), cart.toggleGift(gift))
        assertEquals(mapOf(paid to 3), cart.minusOne(gift))
    }

    @Test
    fun discountInputParsing() {
        assertNull(DiscountInput().discount)
        assertNull(DiscountInput().error)
        assertEquals(OrderDiscount(DiscountType.PERCENT, 10), DiscountInput(DiscountType.PERCENT, "10").discount)
        assertNotNull(DiscountInput(DiscountType.PERCENT, "150").error)
        assertNotNull(DiscountInput(DiscountType.PERCENT, "").error)
        assertEquals(OrderDiscount(DiscountType.FIXED, 250), DiscountInput(DiscountType.FIXED, "2.50").discount)
        assertEquals(DiscountInput(DiscountType.FIXED, "2.50"), DiscountInput.from(OrderDiscount(DiscountType.FIXED, 250)))
    }

    @Test
    fun bundleFormDefaultsNameAndValidates() {
        val form = BundleForm(category = "Badge", qty = "3", price = "20")
        assertNull(form.validate())
        assertEquals("Any 3 Badge", form.toBundle(0).name)
        assertNotNull(form.copy(qty = "1").validate())
        assertNotNull(form.copy(category = " ").validate())
        assertNotNull(form.copy(price = "x").validate())

        val products = BundleForm(type = BundleType.PRODUCTS, qty = "3", price = "25")
        assertNotNull(products.validate()) // nothing ticked
        val one = products.toggleProduct(1)
        assertNull(one.validate())
        assertEquals("3 × Raiden Badge", one.toBundle(0, mapOf(1 to "Raiden Badge")).name)
        val two = one.toggleProduct(2)
        assertEquals("Any 3 of Furina Badge, Raiden Badge", two.toBundle(0, mapOf(1 to "Raiden Badge", 2 to "Furina Badge")).name)
        assertEquals(null, two.toBundle(0).category)
        assertEquals(setOf(2), two.toggleProduct(1).productIds)

        // Editing: an auto name is blanked so it can update, a custom name is kept
        val raiden = Product(id = 1, name = "Raiden Badge", category = "Badge", priceSen = 1000, stockQty = 5)
        val saved = one.toBundle(5, mapOf(1 to "Raiden Badge"))
        assertEquals("", BundleForm.from(BundleWithProducts(saved, listOf(raiden))).name)
        val custom = saved.copy(name = "Raiden deal")
        assertEquals("Raiden deal", BundleForm.from(BundleWithProducts(custom, listOf(raiden))).name)
    }

    @Test
    fun csvQuotesOnlyWhenNeeded() {
        assertEquals("Badge", csvField("Badge"))
        assertEquals("\"Raiden, Badge\"", csvField("Raiden, Badge"))
        assertEquals("\"5\"\" print\"", csvField("5\" print"))
    }
}
