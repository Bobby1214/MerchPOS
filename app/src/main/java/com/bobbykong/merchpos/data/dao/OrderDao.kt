package com.bobbykong.merchpos.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.bobbykong.merchpos.data.model.Order
import com.bobbykong.merchpos.data.model.OrderItem
import com.bobbykong.merchpos.data.model.OrderLine
import kotlinx.coroutines.flow.Flow

@Dao
interface OrderDao {
    @Insert
    suspend fun insertOrder(order: Order): Long

    @Insert
    suspend fun insertItems(items: List<OrderItem>)

    @Query("SELECT * FROM orders WHERE id = :id")
    suspend fun getById(id: Int): Order?

    @Query("SELECT * FROM order_items WHERE order_id = :orderId")
    suspend fun getItems(orderId: Int): List<OrderItem>

    /** Returns 1 if the order was cancelled, or 0 if it was already cancelled. */
    @Query("UPDATE orders SET status = 'CANCELLED', updated_at = :time WHERE id = :id AND status = 'COMPLETED'")
    suspend fun markCancelled(id: Int, time: Long): Int

    @Query("SELECT * FROM orders WHERE event_id = :eventId ORDER BY created_at DESC")
    fun observeForEvent(eventId: Int): Flow<List<Order>>

    @Query(
        """
        SELECT oi.order_id, oi.product_id, p.name AS product_name,
               oi.qty, oi.unit_price_sen, oi.is_gift
        FROM order_items oi
        JOIN orders o ON o.id = oi.order_id
        JOIN products p ON p.id = oi.product_id
        WHERE o.event_id = :eventId
        ORDER BY oi.id
        """
    )
    fun observeLinesForEvent(eventId: Int): Flow<List<OrderLine>>
}
