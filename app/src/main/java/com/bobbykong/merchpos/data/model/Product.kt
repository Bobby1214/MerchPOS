package com.bobbykong.merchpos.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "products")
data class Product(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val category: String,
    @ColumnInfo(name = "price_sen") val priceSen: Int,
    @ColumnInfo(name = "stock_qty") val stockQty: Int,
    @ColumnInfo(name = "image_path") val imagePath: String? = null,
    // Soft delete: inactive products can't be sold, but old orders still point to them
    @ColumnInfo(name = "is_active") val isActive: Boolean = true,
)

/** Stock at or below this number shows a low-stock warning. */
const val LOW_STOCK_THRESHOLD = 3

val Product.isSoldOut: Boolean get() = stockQty <= 0
val Product.isLowStock: Boolean get() = stockQty in 1..LOW_STOCK_THRESHOLD
