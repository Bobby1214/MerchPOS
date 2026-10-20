package com.bobbykong.merchpos.util

import kotlin.math.abs

// All money is whole sen (Int/Long). Never Double: 0.1 + 0.2 != 0.3 in binary floating point.

/** 1250 -> "RM12.50" */
fun formatRm(sen: Long): String = (if (sen < 0) "-" else "") + "RM" + formatSenPlain(abs(sen))

fun formatRm(sen: Int): String = formatRm(sen.toLong())

/** 1250 -> "12.50", without "RM". Used to pre-fill text fields. */
fun formatSenPlain(sen: Long): String {
    val ringgit = sen / 100
    val cents = abs(sen % 100)
    return "$ringgit." + cents.toString().padStart(2, '0')
}

fun formatSenPlain(sen: Int): String = formatSenPlain(sen.toLong())

private val RM_PATTERN = Regex("""^(?:RM)?\s*(\d{1,7})(?:\.(\d{0,2}))?$""", RegexOption.IGNORE_CASE)

/** Parses "12", "12.5", "12.50" or "RM12.50" into sen using only integer maths. Returns null if invalid. */
fun parseRmToSen(input: String): Int? {
    val match = RM_PATTERN.matchEntire(input.trim()) ?: return null
    val ringgit = match.groupValues[1].toInt()
    val cents = match.groupValues[2].padEnd(2, '0').toInt()
    return ringgit * 100 + cents
}

/** Quick "cash received" buttons: the exact amount, then rounded up to RM10, RM50 and RM100. */
fun quickCashAmounts(totalSen: Int): List<Int> =
    listOf(totalSen, roundUp(totalSen, 1000), roundUp(totalSen, 5000), roundUp(totalSen, 10000))
        .distinct()
        .sorted()

private fun roundUp(value: Int, step: Int): Int = (value + step - 1) / step * step
