package com.bobbykong.merchpos

import com.bobbykong.merchpos.ui.products.ProductForm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ProductFormTest {
    private val valid = ProductForm(name = "Raiden Badge", category = "Badge", price = "8", stock = "20")

    @Test
    fun validFormHasNoError() {
        assertNull(valid.validate())
        assertEquals(800, valid.priceSen)
        assertEquals(20, valid.stockQty)
    }

    @Test
    fun rejectsBadInput() {
        assertNotNull(valid.copy(name = " ").validate())
        assertNotNull(valid.copy(category = "").validate())
        assertNotNull(valid.copy(price = "8.999").validate())
        assertNotNull(valid.copy(stock = "-1").validate())
        assertNotNull(valid.copy(stock = "2.5").validate())
    }
}
