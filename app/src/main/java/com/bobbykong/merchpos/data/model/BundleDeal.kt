package com.bobbykong.merchpos.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * "Any [qty] items from [category] for [priceSen]", e.g. any 3 Badges for RM20.
 * Named BundleDeal, not Bundle, so it isn't mixed up with android.os.Bundle.
 */
@Entity(tableName = "bundles")
data class BundleDeal(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val category: String,
    val qty: Int,
    @ColumnInfo(name = "price_sen") val priceSen: Int,
    @ColumnInfo(name = "is_active") val isActive: Boolean = true,
)
