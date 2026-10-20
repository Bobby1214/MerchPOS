package com.bobbykong.merchpos

import com.bobbykong.merchpos.util.formatRm
import com.bobbykong.merchpos.util.formatSenPlain
import com.bobbykong.merchpos.util.parseRmToSen
import com.bobbykong.merchpos.util.quickCashAmounts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {
    @Test
    fun formatsSenAsRinggit() {
        assertEquals("RM12.50", formatRm(1250))
        assertEquals("RM0.05", formatRm(5))
        assertEquals("RM0.00", formatRm(0))
        assertEquals("-RM3.20", formatRm(-320))
        assertEquals("8.00", formatSenPlain(800))
    }

    @Test
    fun parsesValidPrices() {
        assertEquals(1250, parseRmToSen("12.50"))
        assertEquals(1250, parseRmToSen("12.5"))
        assertEquals(1200, parseRmToSen("12"))
        assertEquals(1200, parseRmToSen("12."))
        assertEquals(1250, parseRmToSen(" RM12.50 "))
        assertEquals(5, parseRmToSen("0.05"))
    }

    @Test
    fun rejectsInvalidPrices() {
        assertNull(parseRmToSen(""))
        assertNull(parseRmToSen("abc"))
        assertNull(parseRmToSen("12.555"))
        assertNull(parseRmToSen("-5"))
        assertNull(parseRmToSen("1,000"))
    }

    @Test
    fun quickCashStartsWithExactAmount() {
        assertEquals(listOf(2350, 3000, 5000, 10000), quickCashAmounts(2350))
        assertEquals(listOf(5000, 10000), quickCashAmounts(5000))
    }
}
