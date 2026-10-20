package com.bobbykong.merchpos.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.bobbykong.merchpos.data.model.ProductPart
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductPartDao {
    @Query("SELECT * FROM product_parts")
    fun observeAll(): Flow<List<ProductPart>>

    @Query("SELECT * FROM product_parts")
    suspend fun getAll(): List<ProductPart>

    @Insert
    suspend fun insertAll(parts: List<ProductPart>)

    @Query("DELETE FROM product_parts WHERE product_id = :productId")
    suspend fun clearFor(productId: Int)
}
