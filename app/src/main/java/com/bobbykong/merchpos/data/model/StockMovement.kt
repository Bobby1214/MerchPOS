package com.bobbykong.merchpos.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class MovementReason { INITIAL, SALE, CANCEL, ADJUST, EDIT, EVENT_DELETED }

/** The "bank statement" for stock: every change to products.stock_qty gets one row here. */
@Entity(
    tableName = "stock_movements",
    foreignKeys = [
        ForeignKey(
            entity = Product::class,
            parentColumns = ["id"],
            childColumns = ["product_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = Order::class,
            parentColumns = ["id"],
            childColumns = ["order_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("product_id"), Index("order_id")],
)
data class StockMovement(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    @ColumnInfo(name = "product_id") val productId: Int,
    // +5 = stock added, -2 = stock removed
    @ColumnInfo(name = "change_qty") val changeQty: Int,
    val reason: MovementReason,
    @ColumnInfo(name = "order_id") val orderId: Int? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
