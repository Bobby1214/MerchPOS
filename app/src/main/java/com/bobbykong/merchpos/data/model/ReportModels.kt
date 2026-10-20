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

data class DiscountGiftSummary(
    // Bundle savings + order discounts
    @ColumnInfo(name = "discount_sen") val discountSen: Long,
    @ColumnInfo(name = "gift_qty") val giftQty: Int,
    @ColumnInfo(name = "gift_value_sen") val giftValueSen: Long,
)

data class BestSeller(
    @ColumnInfo(name = "product_id") val productId: Int,
    val name: String,
    @ColumnInfo(name = "qty_sold") val qtySold: Int,
    @ColumnInfo(name = "revenue_sen") val revenueSen: Long,
)

/** One CSV row: one order line plus its order, event and product. Built for loading into openGauss. */
data class ExportRow(
    @ColumnInfo(name = "order_id") val orderId: Int,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "sale_date") val saleDate: String,
    @ColumnInfo(name = "sale_hour") val saleHour: Int,
    @ColumnInfo(name = "event_id") val eventId: Int,
    @ColumnInfo(name = "event_name") val eventName: String,
    @ColumnInfo(name = "order_status") val orderStatus: String,
    @ColumnInfo(name = "payment_method") val paymentMethod: String,
    @ColumnInfo(name = "product_id") val productId: Int,
    @ColumnInfo(name = "product_name") val productName: String,
    val category: String,
    val qty: Int,
    @ColumnInfo(name = "unit_price_sen") val unitPriceSen: Int,
    @ColumnInfo(name = "is_gift") val isGift: Boolean,
    @ColumnInfo(name = "line_total_sen") val lineTotalSen: Int,
    @ColumnInfo(name = "order_subtotal_sen") val orderSubtotalSen: Int,
    @ColumnInfo(name = "order_bundle_discount_sen") val orderBundleDiscountSen: Int,
    @ColumnInfo(name = "order_discount_sen") val orderDiscountSen: Int,
    @ColumnInfo(name = "order_total_sen") val orderTotalSen: Int,
    @ColumnInfo(name = "order_is_edited") val orderIsEdited: Boolean,
)
