package com.bobbykong.merchpos.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.bobbykong.merchpos.data.model.BundleDeal
import kotlinx.coroutines.flow.Flow

@Dao
interface BundleDao {
    @Query("SELECT * FROM bundles ORDER BY is_active DESC, category COLLATE NOCASE, qty")
    fun observeAll(): Flow<List<BundleDeal>>

    @Query("SELECT * FROM bundles WHERE is_active = 1")
    fun observeActive(): Flow<List<BundleDeal>>

    @Query("SELECT * FROM bundles WHERE is_active = 1")
    suspend fun getActive(): List<BundleDeal>

    @Insert
    suspend fun insert(bundle: BundleDeal): Long

    @Update
    suspend fun update(bundle: BundleDeal)

    // Hard delete is fine: orders store the saving amount, not a link to the bundle
    @Delete
    suspend fun delete(bundle: BundleDeal)
}
