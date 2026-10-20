package com.bobbykong.merchpos.data

import androidx.room.withTransaction
import com.bobbykong.merchpos.data.model.Event
import com.bobbykong.merchpos.data.model.MovementReason
import com.bobbykong.merchpos.data.model.Order
import com.bobbykong.merchpos.data.model.OrderItem
import com.bobbykong.merchpos.data.model.PaymentMethod
import com.bobbykong.merchpos.data.model.Product
import com.bobbykong.merchpos.data.model.StockMovement

/** A business rule was broken. The message is safe to show to the user. */
class PosException(message: String) : Exception(message)

data class CartItem(val productId: Int, val qty: Int)

/**
 * The single place the app reads and writes data.
 * Every change that touches stock runs in one transaction and writes a stock_movements row.
 */
class PosRepository(
    private val db: AppDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val eventDao = db.eventDao()
    private val productDao = db.productDao()
    private val orderDao = db.orderDao()
    private val movementDao = db.stockMovementDao()
    private val reportDao = db.reportDao()

    val allProducts = productDao.observeAll()
    val activeProducts = productDao.observeActive()
    val events = eventDao.observeAll()
    val openEvent = eventDao.observeOpen()

    fun ordersForEvent(eventId: Int) = orderDao.observeForEvent(eventId)
    fun linesForEvent(eventId: Int) = orderDao.observeLinesForEvent(eventId)
    fun salesSummary(eventId: Int) = reportDao.observeSummary(eventId)
    fun paymentTotals(eventId: Int) = reportDao.observePaymentTotals(eventId)
    fun dailySales(eventId: Int) = reportDao.observeDailySales(eventId)
    fun bestSellers(eventId: Int) = reportDao.observeBestSellers(eventId)

    suspend fun startEvent(name: String): Int = db.withTransaction {
        if (eventDao.getOpen() != null) throw PosException("End the current event first")
        eventDao.insert(Event(name = name.trim(), startDate = clock())).toInt()
    }

    suspend fun endEvent(eventId: Int) {
        eventDao.end(eventId, clock())
    }

    suspend fun addProduct(name: String, category: String, priceSen: Int, stockQty: Int): Int =
        db.withTransaction {
            val id = productDao.insert(
                Product(name = name, category = category, priceSen = priceSen, stockQty = stockQty)
            ).toInt()
            if (stockQty > 0) {
                movementDao.insert(
                    StockMovement(productId = id, changeQty = stockQty, reason = MovementReason.INITIAL, createdAt = clock())
                )
            }
            id
        }

    /**
     * [stockBefore] is the stock shown when the edit form opened. Only the difference the user typed
     * is applied, so a sale made while the form was open isn't overwritten.
     */
    suspend fun updateProduct(edited: Product, stockBefore: Int) = db.withTransaction {
        val current = productDao.getById(edited.id) ?: throw PosException("Product not found")
        val newStock = (current.stockQty + edited.stockQty - stockBefore).coerceAtLeast(0)
        productDao.update(edited.copy(stockQty = newStock))
        val change = newStock - current.stockQty
        if (change != 0) {
            movementDao.insert(
                StockMovement(productId = edited.id, changeQty = change, reason = MovementReason.ADJUST, createdAt = clock())
            )
        }
    }

    /**
     * Saves the order and deducts stock in ONE transaction.
     * If any line fails, the exception rolls everything back: no order, no stock change.
     * Returns the new order id.
     */
    suspend fun checkout(items: List<CartItem>, payment: PaymentMethod): Int {
        if (items.isEmpty()) throw PosException("Cart is empty")
        return db.withTransaction {
            val event = eventDao.getOpen() ?: throw PosException("Start an event before selling")
            val now = clock()

            val lines = items.map { item ->
                val product = productDao.getById(item.productId) ?: throw PosException("Product not found")
                if (!product.isActive) throw PosException("${product.name} is deactivated")
                if (item.qty <= 0) throw PosException("Invalid quantity for ${product.name}")
                // Conditional UPDATE: stock can never go below 0
                if (productDao.deductStock(product.id, item.qty) == 0) {
                    throw PosException("Only ${product.stockQty} ${product.name} left")
                }
                product to item.qty
            }

            val subtotal = lines.sumOf { (product, qty) -> product.priceSen * qty }
            val orderId = orderDao.insertOrder(
                Order(
                    eventId = event.id,
                    createdAt = now,
                    subtotalSen = subtotal,
                    totalSen = subtotal,
                    paymentMethod = payment,
                )
            ).toInt()

            orderDao.insertItems(
                lines.map { (product, qty) ->
                    OrderItem(orderId = orderId, productId = product.id, qty = qty, unitPriceSen = product.priceSen)
                }
            )
            movementDao.insertAll(
                lines.map { (product, qty) ->
                    StockMovement(
                        productId = product.id,
                        changeQty = -qty,
                        reason = MovementReason.SALE,
                        orderId = orderId,
                        createdAt = now,
                    )
                }
            )
            orderId
        }
    }

    /** Marks the order CANCELLED (it's kept, not deleted) and returns its stock. Safe to call twice. */
    suspend fun cancelOrder(orderId: Int) = db.withTransaction {
        val now = clock()
        if (orderDao.markCancelled(orderId, now) == 0) return@withTransaction
        val items = orderDao.getItems(orderId)
        items.forEach { productDao.addStock(it.productId, it.qty) }
        movementDao.insertAll(
            items.map {
                StockMovement(
                    productId = it.productId,
                    changeQty = it.qty,
                    reason = MovementReason.CANCEL,
                    orderId = orderId,
                    createdAt = now,
                )
            }
        )
    }
}
