package com.bobbykong.merchpos.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// SimpleDateFormat instead of java.time because minSdk 24 doesn't have java.time

fun formatDate(millis: Long): String =
    SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(millis))

fun formatDateTime(millis: Long): String =
    SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(millis))

/** "2026-10-20" -> "Tue, 20 Oct" */
fun formatDay(isoDay: String): String {
    val date = runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(isoDay) }.getOrNull()
        ?: return isoDay
    return SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(date)
}
