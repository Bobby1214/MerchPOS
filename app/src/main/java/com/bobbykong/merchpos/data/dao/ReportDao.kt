package com.bobbykong.merchpos.data.dao

import androidx.room.Dao
import androidx.room.Query
import com.bobbykong.merchpos.data.model.BestSeller
import com.bobbykong.merchpos.data.model.DailySales
import com.bobbykong.merchpos.data.model.PaymentTotal
import com.bobbykong.merchpos.data.model.SalesSummary
import kotlinx.coroutines.flow.Flow

/** Report queries. Cancelled orders are never counted. */
@Dao
interface ReportDao {
    @Query(
        """
        SELECT COUNT(*) AS order_count, COALESCE(SUM(total_sen), 0) AS total_sen
        FROM orders
        WHERE event_id = :eventId AND status = 'COMPLETED'
        """
    )
    fun observeSummary(eventId: Int): Flow<SalesSummary>

    @Query(
        """
        SELECT payment_method, COUNT(*) AS order_count, SUM(total_sen) AS total_sen
        FROM orders
        WHERE event_id = :eventId AND status = 'COMPLETED'
        GROUP BY payment_method
        """
    )
    fun observePaymentTotals(eventId: Int): Flow<List<PaymentTotal>>

    @Query(
        """
        SELECT date(created_at / 1000, 'unixepoch', 'localtime') AS day,
               COUNT(*) AS order_count, SUM(total_sen) AS total_sen
        FROM orders
        WHERE event_id = :eventId AND status = 'COMPLETED'
        GROUP BY day
        ORDER BY day
        """
    )
    fun observeDailySales(eventId: Int): Flow<List<DailySales>>

    @Query(
        """
        SELECT p.id AS product_id, p.name AS name,
               SUM(oi.qty) AS qty_sold,
               SUM(CASE WHEN oi.is_gift = 1 THEN 0 ELSE oi.qty * oi.unit_price_sen END) AS revenue_sen
        FROM order_items oi
        JOIN orders o ON o.id = oi.order_id
        JOIN products p ON p.id = oi.product_id
        WHERE o.event_id = :eventId AND o.status = 'COMPLETED'
        GROUP BY p.id
        ORDER BY qty_sold DESC, revenue_sen DESC
        """
    )
    fun observeBestSellers(eventId: Int): Flow<List<BestSeller>>
}
