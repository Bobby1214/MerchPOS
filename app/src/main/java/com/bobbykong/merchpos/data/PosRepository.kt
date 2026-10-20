package com.bobbykong.merchpos.data

import androidx.room.withTransaction
import com.bobbykong.merchpos.data.model.BundleDeal
import com.bobbykong.merchpos.data.model.Event
import com.bobbykong.merchpos.data.model.MovementReason
import com.bobbykong.merchpos.data.model.Order
import com.bobbykong.merchpos.data.model.OrderDiscount
import com.bobbykong.merchpos.data.model.OrderItem
import com.bobbykong.merchpos.data.model.OrderStatus
import com.bobbykong.merchpos.data.model.PaymentMethod
import com.bobbykong.merchpos.data.model.Product
import com.bobbykong.merchpos.data.model.StockMovement
import com.bobbykong.merchpos.domain.PriceCalculator
import com.bobbykong.merchpos.domain.PriceLine

/** A business rule was broken. The message is safe to show to the user. */
class PosException(message: String) : Exception(message)

data class CartItem(val productId: Int, val qty: Int, val isGift: Boolean = false)

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
    private val bundleDao = db.bundleDao()

    val allProducts = productDao.observeAll()
    val activeProducts = productDao.observeActive()
    val events = eventDao.observeAll()
    val openEvent = eventDao.observeOpen()
    val allBundles = bundleDao.observeAll()
    val activeBundles = bundleDao.observeActive()

    fun ordersForEvent(eventId: Int) = orderDao.observeForEvent(eventId)
    fun linesForEvent(eventId: Int) = orderDao.observeLinesForEvent(eventId)
    fun salesSummary(eventId: Int) = reportDao.observeSummary(eventId)
    fun discountsAndGifts(eventId: Int) = reportDao.observeDiscountsAndGifts(eventId)
    fun paymentTotals(eventId: Int) = reportDao.observePaymentTotals(eventId)
    fun dailySales(eventId: Int) = reportDao.observeDailySales(eventId)
    fun bestSellers(eventId: Int) = reportDao.observeBestSellers(eventId)

    suspend fun exportRows() = reportDao.getExportRows()

    // ---------- Events ----------

    suspend fun startEvent(name: String): Int = db.withTransaction {
        if (eventDao.getOpen() != null) throw PosException("End the current event first")
        eventDao.insert(Event(name = name.trim(), startDate = clock())).toInt()
    }

    suspend fun endEvent(eventId: Int) {
        eventDao.end(eventId, clock())
    }

    suspend fun orderCountForEvent(eventId: Int) = orderDao.countForEvent(eventId)

    /**
     * Deletes the event and all its orders. Stock from COMPLETED orders goes back
     * (cancelled orders already returned theirs). Can't be undone.
     */
    suspend fun deleteEvent(eventId: Int) = db.withTransaction {
        val now = clock()
        val returned = mutableMapOf<Int, Int>()
        orderDao.getForEvent(eventId).filter { it.status == OrderStatus.COMPLETED }.forEach { order ->
            orderDao.getItems(order.id).forEach { item ->
                returned[item.productId] = (returned[item.productId] ?: 0) + item.qty
            }
        }
        returned.forEach { (productId, qty) -> productDao.addStock(productId, qty) }
        movementDao.insertAll(
            returned.map { (productId, qty) ->
                StockMovement(productId = productId, changeQty = qty, reason = MovementReason.EVENT_DELETED, createdAt = now)
            }
        )
        // Order lines go with their orders (CASCADE). Old movements keep their rows, with order_id set to NULL.
        orderDao.deleteForEvent(eventId)
        eventDao.delete(eventId)
    }

    // ---------- Products & bundles ----------

    suspend fun addProduct(name: String, category: String, priceSen: Int, stockQty: Int, imagePath: String? = null): Int =
        db.withTransaction {
            val id = productDao.insert(
                Product(name = name, category = category, priceSen = priceSen, stockQty = stockQty, imagePath = imagePath)
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

    suspend fun orderCountForProduct(productId: Int) = orderDao.countOrdersWithProduct(productId)

    /**
     * Hard-deletes a product that was NEVER sold, with its stock history.
     * Your rule: a product in any order is never deleted (old orders need it), so this throws instead.
     */
    suspend fun deleteProduct(productId: Int) = db.withTransaction {
        val product = productDao.getById(productId) ?: return@withTransaction
        val orders = orderDao.countOrdersWithProduct(productId)
        if (orders > 0) {
            throw PosException("${product.name} is in $orders order(s), so it can't be deleted. Deactivate it instead.")
        }
        movementDao.deleteForProduct(productId)
        productDao.delete(productId)
    }

    suspend fun saveBundle(bundle: BundleDeal) {
        if (bundle.id == 0) bundleDao.insert(bundle) else bundleDao.update(bundle)
    }

    suspend fun deleteBundle(bundle: BundleDeal) = bundleDao.delete(bundle)

    // ---------- Orders ----------

    /**
     * Saves the order and deducts stock in ONE transaction.
     * If anything fails, the exception rolls everything back: no order, no stock change.
     * Returns the new order id.
     */
    suspend fun checkout(items: List<CartItem>, payment: PaymentMethod, discount: OrderDiscount? = null): Int {
        if (items.isEmpty()) throw PosException("Cart is empty")
        return db.withTransaction {
            val event = eventDao.getOpen() ?: throw PosException("Start an event before selling")
            val now = clock()
            val products = loadProducts(items.map { it.productId })

            items.forEach { item ->
                val product = products.getValue(item.productId)
                if (!product.isActive) throw PosException("${product.name} is deactivated")
                if (item.qty <= 0) throw PosException("Invalid quantity for ${product.name}")
            }
            // Paid and gift lines of the same product both take stock
            val qtyByProduct = items.groupingBy { it.productId }.fold(0) { sum, item -> sum + item.qty }
            qtyByProduct.forEach { (productId, qty) ->
                val product = products.getValue(productId)
                // Conditional UPDATE: stock can never go below 0
                if (productDao.deductStock(productId, qty) == 0) {
                    throw PosException("Only ${product.stockQty} ${product.name} left")
                }
            }

            val price = price(items, products, discount) { products.getValue(it).priceSen }
            val orderId = orderDao.insertOrder(
                Order(
                    eventId = event.id,
                    createdAt = now,
                    subtotalSen = price.subtotalSen,
                    bundleDiscountSen = price.bundleDiscountSen,
                    discountType = discount?.type,
                    discountValue = discount?.value ?: 0,
                    discountSen = price.discountSen,
                    totalSen = price.totalSen,
                    paymentMethod = payment,
                )
            ).toInt()

            orderDao.insertItems(items.map { it.toOrderItem(orderId, products.getValue(it.productId).priceSen) })
            movementDao.insertAll(
                qtyByProduct.map { (productId, qty) ->
                    StockMovement(
                        productId = productId,
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

    /**
     * Replaces an order's lines, payment and discount in ONE transaction.
     * Same result as "return the old stock, take the new stock", but only the difference per product is applied.
     * Lines keep their original price; new products use today's price.
     * A deactivated product can be kept or reduced, never increased.
     */
    suspend fun editOrder(orderId: Int, items: List<CartItem>, payment: PaymentMethod, discount: OrderDiscount?) {
        if (items.isEmpty()) throw PosException("An order needs at least 1 item. Use Cancel order instead.")
        if (items.any { it.qty <= 0 }) throw PosException("Invalid quantity")
        db.withTransaction {
            val order = orderDao.getById(orderId) ?: throw PosException("Order not found")
            if (order.status == OrderStatus.CANCELLED) throw PosException("Cancelled orders can't be edited")
            val now = clock()

            val oldItems = orderDao.getItems(orderId)
            val oldQty = oldItems.groupingBy { it.productId }.fold(0) { sum, item -> sum + item.qty }
            val oldPrice = oldItems.associate { it.productId to it.unitPriceSen }
            val newQty = items.groupingBy { it.productId }.fold(0) { sum, item -> sum + item.qty }
            val products = loadProducts(oldQty.keys + newQty.keys)

            val movements = mutableListOf<StockMovement>()
            for ((productId, product) in products) {
                // Positive = more items leave the stock
                val delta = (newQty[productId] ?: 0) - (oldQty[productId] ?: 0)
                if (delta > 0 && productDao.deductStock(productId, delta) == 0) {
                    throw PosException(
                        if (!product.isActive) "${product.name} is deactivated: you can keep or reduce it, not add more"
                        else "Only ${product.stockQty} more ${product.name} in stock"
                    )
                }
                if (delta < 0) productDao.addStock(productId, -delta)
                if (delta != 0) {
                    movements += StockMovement(
                        productId = productId,
                        changeQty = -delta,
                        reason = MovementReason.EDIT,
                        orderId = orderId,
                        createdAt = now,
                    )
                }
            }

            val unitPrice = { productId: Int -> oldPrice[productId] ?: products.getValue(productId).priceSen }
            val price = price(items, products, discount, unitPrice)
            orderDao.deleteItems(orderId)
            orderDao.insertItems(items.map { it.toOrderItem(orderId, unitPrice(it.productId)) })
            orderDao.updateOrder(
                order.copy(
                    subtotalSen = price.subtotalSen,
                    bundleDiscountSen = price.bundleDiscountSen,
                    discountType = discount?.type,
                    discountValue = discount?.value ?: 0,
                    discountSen = price.discountSen,
                    totalSen = price.totalSen,
                    paymentMethod = payment,
                    isEdited = true,
                    updatedAt = now,
                )
            )
            movementDao.insertAll(movements)
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

    private suspend fun loadProducts(ids: Collection<Int>): Map<Int, Product> =
        ids.distinct().associateWith { productDao.getById(it) ?: throw PosException("Product not found") }

    private suspend fun price(
        items: List<CartItem>,
        products: Map<Int, Product>,
        discount: OrderDiscount?,
        unitPrice: (Int) -> Int,
    ) = PriceCalculator.calculate(
        lines = items.map {
            PriceLine(products.getValue(it.productId).category, unitPrice(it.productId), it.qty, it.isGift)
        },
        bundles = bundleDao.getActive(),
        discount = discount,
    )

    private fun CartItem.toOrderItem(orderId: Int, unitPriceSen: Int) =
        OrderItem(orderId = orderId, productId = productId, qty = qty, unitPriceSen = unitPriceSen, isGift = isGift)
}
