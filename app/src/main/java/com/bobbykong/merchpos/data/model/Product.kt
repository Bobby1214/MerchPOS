package com.bobbykong.merchpos.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "products")
data class Product(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val category: String,
    @ColumnInfo(name = "price_sen") val priceSen: Int,
    @ColumnInfo(name = "stock_qty") val stockQty: Int,
    // File name inside the app's product_images folder (not a full path, so backups work on another phone)
    @ColumnInfo(name = "image_path") val imagePath: String? = null,
    // Soft delete: inactive products can't be sold, but old orders still point to them
    @ColumnInfo(name = "is_active") val isActive: Boolean = true,
    // Material (e.g. Instax film): has stock, is used by other products, but isn't sold on its own
    @ColumnInfo(name = "is_material", defaultValue = "0") val isMaterial: Boolean = false,
)

/**
 * "[productId] is made of [qty] × [partId]", e.g. Poster Set = 1 × Poster A + 1 × Poster B,
 * or Service cheki = 1 × Instax film. A product with parts has no stock of its own:
 * selling it takes stock from its parts.
 * Many-to-many from products to products (a self-referencing relationship).
 */
@Entity(
    tableName = "product_parts",
    primaryKeys = ["product_id", "part_id"],
    foreignKeys = [
        // Deleting the set deletes its recipe
        ForeignKey(entity = Product::class, parentColumns = ["id"], childColumns = ["product_id"], onDelete = ForeignKey.CASCADE),
        // A product used as a part can't be deleted
        ForeignKey(entity = Product::class, parentColumns = ["id"], childColumns = ["part_id"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("part_id")],
)
data class ProductPart(
    @ColumnInfo(name = "product_id") val productId: Int,
    @ColumnInfo(name = "part_id") val partId: Int,
    val qty: Int,
)

/** Stock at or below this number shows a low-stock warning. */
const val LOW_STOCK_THRESHOLD = 3

val Product.isSoldOut: Boolean get() = stockQty <= 0
val Product.isLowStock: Boolean get() = stockQty in 1..LOW_STOCK_THRESHOLD
