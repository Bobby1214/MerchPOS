package com.bobbykong.merchpos.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "order_items",
    foreignKeys = [
        ForeignKey(
            entity = Order::class,
            parentColumns = ["id"],
            childColumns = ["order_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        // RESTRICT: the DB refuses to hard-delete a product that was ever sold
        ForeignKey(
            entity = Product::class,
            parentColumns = ["id"],
            childColumns = ["product_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("order_id"), Index("product_id")],
)
data class OrderItem(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    @ColumnInfo(name = "order_id") val orderId: Int,
    @ColumnInfo(name = "product_id") val productId: Int,
    val qty: Int,
    // Price snapshot: changing the product price later doesn't change old orders
    @ColumnInfo(name = "unit_price_sen") val unitPriceSen: Int,
    // Phase 2 (gift items)
    @ColumnInfo(name = "is_gift") val isGift: Boolean = false,
)

/** One order line joined with its product name, for showing orders. */
data class OrderLine(
    @ColumnInfo(name = "order_id") val orderId: Int,
    @ColumnInfo(name = "product_id") val productId: Int,
    @ColumnInfo(name = "product_name") val productName: String,
    val qty: Int,
    @ColumnInfo(name = "unit_price_sen") val unitPriceSen: Int,
    @ColumnInfo(name = "is_gift") val isGift: Boolean,
) {
    val lineTotalSen: Int get() = if (isGift) 0 else qty * unitPriceSen
}
