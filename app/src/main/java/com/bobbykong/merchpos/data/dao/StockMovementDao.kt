package com.bobbykong.merchpos.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.bobbykong.merchpos.data.model.StockMovement

@Dao
interface StockMovementDao {
    @Insert
    suspend fun insert(movement: StockMovement)

    @Insert
    suspend fun insertAll(movements: List<StockMovement>)

    @Query("DELETE FROM stock_movements WHERE product_id = :productId")
    suspend fun deleteForProduct(productId: Int)

    @Query("SELECT * FROM stock_movements WHERE product_id = :productId ORDER BY created_at, id")
    suspend fun getForProduct(productId: Int): List<StockMovement>
}
