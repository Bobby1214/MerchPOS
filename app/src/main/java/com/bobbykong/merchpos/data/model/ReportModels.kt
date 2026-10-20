package com.bobbykong.merchpos.data.model

import androidx.room.ColumnInfo

// Result rows for report queries. Sums use Long because SQLite SUM() returns a 64-bit integer.

data class SalesSummary(
    @ColumnInfo(name = "order_count") val orderCount: Int,
    @ColumnInfo(name = "total_sen") val totalSen: Long,
)

data class PaymentTotal(
    @ColumnInfo(name = "payment_method") val paymentMethod: PaymentMethod,
    @ColumnInfo(name = "order_count") val orderCount: Int,
    @ColumnInfo(name = "total_sen") val totalSen: Long,
)

data class DailySales(
    // "yyyy-MM-dd" in the phone's local time
    val day: String,
    @ColumnInfo(name = "order_count") val orderCount: Int,
    @ColumnInfo(name = "total_sen") val totalSen: Long,
)

data class BestSeller(
    @ColumnInfo(name = "product_id") val productId: Int,
    val name: String,
    @ColumnInfo(name = "qty_sold") val qtySold: Int,
    @ColumnInfo(name = "revenue_sen") val revenueSen: Long,
)
