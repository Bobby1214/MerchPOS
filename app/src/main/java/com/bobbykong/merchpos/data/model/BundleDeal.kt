package com.bobbykong.merchpos.data.model

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Junction
import androidx.room.PrimaryKey
import androidx.room.Relation

enum class BundleType {
    // "Any 3 Badges": every product in [BundleDeal.category]
    CATEGORY,

    // "3 Raiden Badge" or "any 3 of Raiden/Furina": only the products linked in bundle_products
    PRODUCTS,
}

/**
 * "Any [qty] items for [priceSen]", e.g. any 3 Badges for RM20.
 * Named BundleDeal, not Bundle, so it isn't mixed up with android.os.Bundle.
 */
@Entity(tableName = "bundles")
data class BundleDeal(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    @ColumnInfo(defaultValue = "CATEGORY") val type: BundleType = BundleType.CATEGORY,
    // Only for CATEGORY bundles
    val category: String? = null,
    val qty: Int,
    @ColumnInfo(name = "price_sen") val priceSen: Int,
    @ColumnInfo(name = "is_active") val isActive: Boolean = true,
)

/**
 * Link table for PRODUCTS bundles: one row per (bundle, product).
 * Many-to-many: a bundle has many products, and a product can be in many bundles.
 */
@Entity(
    tableName = "bundle_products",
    primaryKeys = ["bundle_id", "product_id"],
    foreignKeys = [
        ForeignKey(
            entity = BundleDeal::class,
            parentColumns = ["id"],
            childColumns = ["bundle_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        // Deleting an (unsold) product just removes it from its bundles
        ForeignKey(
            entity = Product::class,
            parentColumns = ["id"],
            childColumns = ["product_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("product_id")],
)
data class BundleProduct(
    @ColumnInfo(name = "bundle_id") val bundleId: Int,
    @ColumnInfo(name = "product_id") val productId: Int,
)

/** A bundle with its products. Room fills [products] through the bundle_products link table. */
data class BundleWithProducts(
    @Embedded val bundle: BundleDeal,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(BundleProduct::class, parentColumn = "bundle_id", entityColumn = "product_id"),
    )
    val products: List<Product>,
)
