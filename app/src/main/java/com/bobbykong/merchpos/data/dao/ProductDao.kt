package com.bobbykong.merchpos.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.bobbykong.merchpos.data.model.Product
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    @Query("SELECT * FROM products ORDER BY is_active DESC, name COLLATE NOCASE")
    fun observeAll(): Flow<List<Product>>

    @Query("SELECT * FROM products WHERE is_active = 1 ORDER BY category COLLATE NOCASE, name COLLATE NOCASE")
    fun observeActive(): Flow<List<Product>>

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun getById(id: Int): Product?

    @Insert
    suspend fun insert(product: Product): Long

    @Update
    suspend fun update(product: Product)

    /** Returns 1 if stock was deducted, or 0 if there isn't enough stock or the product is inactive. */
    @Query(
        "UPDATE products SET stock_qty = stock_qty - :qty " +
            "WHERE id = :id AND is_active = 1 AND stock_qty >= :qty"
    )
    suspend fun deductStock(id: Int, qty: Int): Int

    @Query("UPDATE products SET stock_qty = stock_qty + :qty WHERE id = :id")
    suspend fun addStock(id: Int, qty: Int): Int
}
