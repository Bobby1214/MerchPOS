package com.bobbykong.merchpos.data.dao

import androidx.room.Dao
import androidx.room.Query
import com.bobbykong.merchpos.data.model.BestSeller
import com.bobbykong.merchpos.data.model.DailySales
import com.bobbykong.merchpos.data.model.DiscountGiftSummary
import com.bobbykong.merchpos.data.model.ExportRow
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
               SUM(CASE WHEN oi.is_gift = 1 THEN 0 ELSE oi.qty END) AS qty_sold,
               SUM(CASE WHEN oi.is_gift = 1 THEN 0 ELSE oi.qty * oi.unit_price_sen END) AS revenue_sen
        FROM order_items oi
        JOIN orders o ON o.id = oi.order_id
        JOIN products p ON p.id = oi.product_id
        WHERE o.event_id = :eventId AND o.status = 'COMPLETED'
        GROUP BY p.id
        HAVING qty_sold > 0
        ORDER BY qty_sold DESC, revenue_sen DESC
        """
    )
    fun observeBestSellers(eventId: Int): Flow<List<BestSeller>>

    @Query(
        """
        SELECT
            (SELECT COALESCE(SUM(bundle_discount_sen + discount_sen), 0) FROM orders
             WHERE event_id = :eventId AND status = 'COMPLETED') AS discount_sen,
            COALESCE(SUM(oi.qty), 0) AS gift_qty,
            COALESCE(SUM(oi.qty * oi.unit_price_sen), 0) AS gift_value_sen
        FROM order_items oi
        JOIN orders o ON o.id = oi.order_id
        WHERE o.event_id = :eventId AND o.status = 'COMPLETED' AND oi.is_gift = 1
        """
    )
    fun observeDiscountsAndGifts(eventId: Int): Flow<DiscountGiftSummary>

    /** Every order line of every event, for the CSV export. Times are in the phone's local time. */
    @Query(
        """
        SELECT o.id AS order_id,
               strftime('%Y-%m-%d %H:%M:%S', o.created_at / 1000, 'unixepoch', 'localtime') AS created_at,
               date(o.created_at / 1000, 'unixepoch', 'localtime') AS sale_date,
               CAST(strftime('%H', o.created_at / 1000, 'unixepoch', 'localtime') AS INTEGER) AS sale_hour,
               e.id AS event_id, e.name AS event_name,
               o.status AS order_status, o.payment_method,
               p.id AS product_id, p.name AS product_name, p.category,
               oi.qty, oi.unit_price_sen, oi.is_gift,
               CASE WHEN oi.is_gift = 1 THEN 0 ELSE oi.qty * oi.unit_price_sen END AS line_total_sen,
               o.subtotal_sen AS order_subtotal_sen,
               o.bundle_discount_sen AS order_bundle_discount_sen,
               o.discount_sen AS order_discount_sen,
               o.total_sen AS order_total_sen,
               o.is_edited AS order_is_edited
        FROM order_items oi
        JOIN orders o ON o.id = oi.order_id
        JOIN events e ON e.id = o.event_id
        JOIN products p ON p.id = oi.product_id
        ORDER BY o.created_at, oi.id
        """
    )
    suspend fun getExportRows(): List<ExportRow>
}
