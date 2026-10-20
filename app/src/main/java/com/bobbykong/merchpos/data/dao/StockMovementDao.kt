package com.bobbykong.merchpos.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.bobbykong.merchpos.data.model.StockChange
import com.bobbykong.merchpos.data.model.StockMovement

@Dao
interface StockMovementDao {
    @Insert
    suspend fun insert(movement: StockMovement)

    @Insert
    suspend fun insertAll(movements: List<StockMovement>)

    @Query("DELETE FROM stock_movements WHERE product_id = :productId")
    suspend fun deleteForProduct(productId: Int)

    /**
     * What an order really took from stock, per product (the sum of its SALE / EDIT / CANCEL rows).
     * Used to give stock back, so it's right even if a set's parts changed after the sale.
     */
    @Query(
        "SELECT product_id, SUM(change_qty) AS change_qty FROM stock_movements " +
            "WHERE order_id = :orderId GROUP BY product_id"
    )
    suspend fun netForOrder(orderId: Int): List<StockChange>

    @Query(
        "SELECT product_id, SUM(change_qty) AS change_qty FROM stock_movements " +
            "WHERE order_id IN (SELECT id FROM orders WHERE event_id = :eventId) GROUP BY product_id"
    )
    suspend fun netForEvent(eventId: Int): List<StockChange>

    @Query("SELECT * FROM stock_movements WHERE product_id = :productId ORDER BY created_at, id")
    suspend fun getForProduct(productId: Int): List<StockMovement>
}
