package com.bobbykong.merchpos.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.bobbykong.merchpos.data.model.BundleDeal
import com.bobbykong.merchpos.data.model.BundleProduct
import com.bobbykong.merchpos.data.model.BundleWithProducts
import kotlinx.coroutines.flow.Flow

@Dao
interface BundleDao {
    // @Transaction: Room runs 2 queries here (bundles, then their products), so they must see the same data
    @Transaction
    @Query("SELECT * FROM bundles ORDER BY is_active DESC, name COLLATE NOCASE")
    fun observeAll(): Flow<List<BundleWithProducts>>

    @Transaction
    @Query("SELECT * FROM bundles WHERE is_active = 1")
    fun observeActive(): Flow<List<BundleWithProducts>>

    @Transaction
    @Query("SELECT * FROM bundles WHERE is_active = 1")
    suspend fun getActive(): List<BundleWithProducts>

    @Insert
    suspend fun insert(bundle: BundleDeal): Long

    @Update
    suspend fun update(bundle: BundleDeal)

    // Hard delete is fine: orders store the saving amount, not a link to the bundle
    @Delete
    suspend fun delete(bundle: BundleDeal)

    @Insert
    suspend fun insertProducts(links: List<BundleProduct>)

    @Query("DELETE FROM bundle_products WHERE bundle_id = :bundleId")
    suspend fun clearProducts(bundleId: Int)
}
