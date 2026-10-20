package com.bobbykong.merchpos

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bobbykong.merchpos.data.AppDatabase
import com.bobbykong.merchpos.data.CartItem
import com.bobbykong.merchpos.data.PosException
import com.bobbykong.merchpos.data.PosRepository
import com.bobbykong.merchpos.data.model.BundleDeal
import com.bobbykong.merchpos.data.model.BundleType
import com.bobbykong.merchpos.data.model.DiscountType
import com.bobbykong.merchpos.data.model.MovementReason
import com.bobbykong.merchpos.data.model.OrderDiscount
import com.bobbykong.merchpos.data.model.OrderStatus
import com.bobbykong.merchpos.data.model.PaymentMethod
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PosRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: PosRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        repo = PosRepository(db)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun stockOf(id: Int) = db.productDao().getById(id)!!.stockQty

    private suspend fun assertFails(block: suspend () -> Unit) {
        try {
            block()
            fail("Expected PosException")
        } catch (_: PosException) {
        }
    }

    @Test
    fun checkoutDeductsStockAndSavesPriceSnapshot() = runBlocking {
        val eventId = repo.startEvent("Comic Fiesta")
        val badge = repo.addProduct("Badge", "Badge", 800, 10)
        val stand = repo.addProduct("Stand", "Acrylic", 2500, 5)

        val orderId = repo.checkout(listOf(CartItem(badge, 3), CartItem(stand, 1)), PaymentMethod.CASH)

        assertEquals(7, stockOf(badge))
        assertEquals(4, stockOf(stand))
        val order = db.orderDao().getById(orderId)!!
        assertEquals(eventId, order.eventId)
        assertEquals(4900, order.totalSen)
        assertEquals(800, db.orderDao().getItems(orderId).first { it.productId == badge }.unitPriceSen)
        val reasons = db.stockMovementDao().getForProduct(badge).map { it.reason to it.changeQty }
        assertEquals(listOf(MovementReason.INITIAL to 10, MovementReason.SALE to -3), reasons)
    }

    @Test
    fun checkoutFailureRollsBackEverything() = runBlocking {
        repo.startEvent("Comic Fiesta")
        val badge = repo.addProduct("Badge", "Badge", 800, 10)
        val stand = repo.addProduct("Stand", "Acrylic", 2500, 1)

        // Badge line succeeds first, then stand fails: the badge deduction must be undone too
        assertFails { repo.checkout(listOf(CartItem(badge, 2), CartItem(stand, 2)), PaymentMethod.CASH) }

        assertEquals(10, stockOf(badge))
        assertEquals(1, stockOf(stand))
        assertTrue(repo.ordersForEvent(1).first().isEmpty())
    }

    @Test
    fun cannotSellWithoutEventOrInactiveProduct() = runBlocking {
        val badge = repo.addProduct("Badge", "Badge", 800, 10)
        assertFails { repo.checkout(listOf(CartItem(badge, 1)), PaymentMethod.CASH) }

        repo.startEvent("Comic Fiesta")
        val product = db.productDao().getById(badge)!!
        repo.updateProduct(product.copy(isActive = false), stockBefore = product.stockQty)
        assertFails { repo.checkout(listOf(CartItem(badge, 1)), PaymentMethod.CASH) }
        assertEquals(10, stockOf(badge))
    }

    @Test
    fun cancelRestoresStockOnceAndIsExcludedFromReport() = runBlocking {
        val eventId = repo.startEvent("Comic Fiesta")
        val badge = repo.addProduct("Badge", "Badge", 800, 10)
        val keep = repo.checkout(listOf(CartItem(badge, 1)), PaymentMethod.TOUCH_N_GO)
        val cancel = repo.checkout(listOf(CartItem(badge, 4)), PaymentMethod.CASH)

        repo.cancelOrder(cancel)
        repo.cancelOrder(cancel) // second call must do nothing

        assertEquals(9, stockOf(badge))
        assertEquals(OrderStatus.CANCELLED, db.orderDao().getById(cancel)!!.status)
        val summary = repo.salesSummary(eventId).first()
        assertEquals(1, summary.orderCount)
        assertEquals(800L, summary.totalSen)
        assertEquals(PaymentMethod.TOUCH_N_GO, repo.paymentTotals(eventId).first().single().paymentMethod)
        assertEquals(1, repo.bestSellers(eventId).first().single().qtySold)
        assertEquals(OrderStatus.COMPLETED, db.orderDao().getById(keep)!!.status)
    }

    @Test
    fun editingStockKeepsSalesMadeWhileFormWasOpen() = runBlocking {
        repo.startEvent("Comic Fiesta")
        val badge = repo.addProduct("Badge", "Badge", 800, 10)
        val formSnapshot = db.productDao().getById(badge)!!

        repo.checkout(listOf(CartItem(badge, 2)), PaymentMethod.CASH) // stock now 8
        // User restocks +5 in a form that still showed 10
        repo.updateProduct(formSnapshot.copy(stockQty = 15), stockBefore = 10)

        assertEquals(13, stockOf(badge))
        assertEquals(MovementReason.ADJUST to 5, db.stockMovementDao().getForProduct(badge).last().let { it.reason to it.changeQty })
    }

    @Test
    fun onlyOneEventCanRunAtATime() = runBlocking {
        val first = repo.startEvent("Day 1")
        assertFails { repo.startEvent("Day 2") }
        repo.endEvent(first)
        repo.startEvent("Day 2")
        assertEquals(2, repo.events.first().size)
    }

    @Test
    fun giftsTakeStockButCostNothing() = runBlocking {
        val eventId = repo.startEvent("Comic Fiesta")
        val badge = repo.addProduct("Badge", "Badge", 800, 10)

        val orderId = repo.checkout(listOf(CartItem(badge, 2), CartItem(badge, 1, isGift = true)), PaymentMethod.CASH)

        assertEquals(7, stockOf(badge))
        assertEquals(1600, db.orderDao().getById(orderId)!!.totalSen)
        val extras = repo.discountsAndGifts(eventId).first()
        assertEquals(1, extras.giftQty)
        assertEquals(800L, extras.giftValueSen)
        assertEquals(2, repo.bestSellers(eventId).first().single().qtySold)
    }

    @Test
    fun checkoutSavesBundleAndDiscount() = runBlocking {
        repo.startEvent("Comic Fiesta")
        val badge = repo.addProduct("Badge", "Badge", 800, 10)
        repo.saveBundle(BundleDeal(name = "3 Badges", category = "Badge", qty = 3, priceSen = 2000))

        val orderId = repo.checkout(listOf(CartItem(badge, 3)), PaymentMethod.CASH, OrderDiscount(DiscountType.FIXED, 500))

        val order = db.orderDao().getById(orderId)!!
        assertEquals(2400, order.subtotalSen)
        assertEquals(400, order.bundleDiscountSen)
        assertEquals(500, order.discountSen)
        assertEquals(1500, order.totalSen)
    }

    @Test
    fun editOrderAppliesOnlyTheDifference() = runBlocking {
        repo.startEvent("Comic Fiesta")
        val badge = repo.addProduct("Badge", "Badge", 800, 10)
        val stand = repo.addProduct("Stand", "Acrylic", 2500, 5)
        val orderId = repo.checkout(listOf(CartItem(badge, 2)), PaymentMethod.CASH) // badge stock 8

        // Price goes up later: the edited order must keep RM8 for badges
        val product = db.productDao().getById(badge)!!
        repo.updateProduct(product.copy(priceSen = 1000), stockBefore = product.stockQty)

        repo.editOrder(
            orderId,
            listOf(CartItem(badge, 1), CartItem(stand, 1), CartItem(badge, 1, isGift = true)),
            PaymentMethod.TOUCH_N_GO,
            null,
        )

        assertEquals(8, stockOf(badge)) // still 2 badges in the order (1 paid + 1 gift)
        assertEquals(4, stockOf(stand))
        val order = db.orderDao().getById(orderId)!!
        assertTrue(order.isEdited)
        assertEquals(PaymentMethod.TOUCH_N_GO, order.paymentMethod)
        assertEquals(800 + 2500, order.totalSen)
        assertEquals(MovementReason.EDIT to -1, db.stockMovementDao().getForProduct(stand).last().let { it.reason to it.changeQty })
    }

    @Test
    fun editOrderLimitsAndRollsBack() = runBlocking {
        repo.startEvent("Comic Fiesta")
        val badge = repo.addProduct("Badge", "Badge", 800, 3)
        val orderId = repo.checkout(listOf(CartItem(badge, 2)), PaymentMethod.CASH) // stock 1

        // Max is stock left + what the order already has = 1 + 2 = 3
        assertFails { repo.editOrder(orderId, listOf(CartItem(badge, 4)), PaymentMethod.CASH, null) }
        assertEquals(1, stockOf(badge))
        repo.editOrder(orderId, listOf(CartItem(badge, 3)), PaymentMethod.CASH, null)
        assertEquals(0, stockOf(badge))

        // Deactivated: can reduce, can't increase
        val product = db.productDao().getById(badge)!!
        repo.updateProduct(product.copy(isActive = false), stockBefore = product.stockQty)
        repo.editOrder(orderId, listOf(CartItem(badge, 1)), PaymentMethod.CASH, null)
        assertEquals(2, stockOf(badge))
        assertFails { repo.editOrder(orderId, listOf(CartItem(badge, 2)), PaymentMethod.CASH, null) }

        assertFails { repo.editOrder(orderId, emptyList(), PaymentMethod.CASH, null) }
        repo.cancelOrder(orderId)
        assertFails { repo.editOrder(orderId, listOf(CartItem(badge, 1)), PaymentMethod.CASH, null) }
    }

    @Test
    fun deleteEventRemovesOrdersAndReturnsStockOnce() = runBlocking {
        val eventId = repo.startEvent("Practice run")
        val badge = repo.addProduct("Badge", "Badge", 800, 10)
        repo.checkout(listOf(CartItem(badge, 3)), PaymentMethod.CASH)
        val cancelled = repo.checkout(listOf(CartItem(badge, 2)), PaymentMethod.CASH)
        repo.cancelOrder(cancelled) // its 2 already went back
        assertEquals(7, stockOf(badge))

        repo.deleteEvent(eventId)

        assertEquals(10, stockOf(badge))
        assertTrue(repo.events.first().isEmpty())
        assertEquals(0, repo.orderCountForEvent(eventId))
        assertTrue(db.orderDao().getItems(cancelled).isEmpty())
        assertEquals(MovementReason.EVENT_DELETED, db.stockMovementDao().getForProduct(badge).last().reason)
        // A new event can start and sell normally afterwards
        repo.startEvent("Comic Fiesta")
        repo.checkout(listOf(CartItem(badge, 1)), PaymentMethod.CASH)
        assertEquals(9, stockOf(badge))
    }

    @Test
    fun exportRowsHaveOneRowPerLine() = runBlocking {
        repo.startEvent("Comic Fiesta")
        val badge = repo.addProduct("Badge", "Badge", 800, 10)
        repo.checkout(listOf(CartItem(badge, 2), CartItem(badge, 1, isGift = true)), PaymentMethod.DUITNOW_QR)

        val rows = repo.exportRows()
        assertEquals(2, rows.size)
        assertEquals("Comic Fiesta", rows[0].eventName)
        assertEquals("DUITNOW_QR", rows[0].paymentMethod)
        assertEquals(1600, rows[0].lineTotalSen)
        assertEquals(0, rows[1].lineTotalSen)
        assertTrue(rows[1].isGift)
    }

    @Test
    fun deleteProductOnlyWhenNeverSold() = runBlocking {
        val eventId = repo.startEvent("Practice run")
        val unsold = repo.addProduct("Typo product", "Badge", 800, 5)
        val sold = repo.addProduct("Badge", "Badge", 800, 10)
        repo.checkout(listOf(CartItem(sold, 1)), PaymentMethod.CASH)

        repo.deleteProduct(unsold)
        assertEquals(null, db.productDao().getById(unsold))
        assertTrue(db.stockMovementDao().getForProduct(unsold).isEmpty())

        // Sold, even if the order is cancelled: old orders still point to it
        assertEquals(1, repo.orderCountForProduct(sold))
        assertFails { repo.deleteProduct(sold) }
        assertEquals(9, stockOf(sold))

        // Once the event and its orders are deleted, it was never sold any more
        repo.deleteEvent(eventId)
        repo.deleteProduct(sold)
        assertEquals(null, db.productDao().getById(sold))
    }

    @Test
    fun productBundleSavesLinksAndPricesCheckout() = runBlocking {
        repo.startEvent("Comic Fiesta")
        val raiden = repo.addProduct("Raiden Badge", "Badge", 1000, 10)
        val furina = repo.addProduct("Furina Badge", "Badge", 1000, 10)
        val zhongli = repo.addProduct("Zhongli Badge", "Badge", 800, 10)
        repo.saveBundle(
            BundleDeal(name = "Raiden/Furina", type = BundleType.PRODUCTS, qty = 3, priceSen = 2500),
            productIds = setOf(raiden, furina),
        )
        val saved = db.bundleDao().getActive().single()
        assertEquals(setOf(raiden, furina), saved.products.map { it.id }.toSet())

        // Zhongli isn't in the bundle
        val noDeal = repo.checkout(listOf(CartItem(raiden, 2), CartItem(zhongli, 1)), PaymentMethod.CASH)
        assertEquals(2800, db.orderDao().getById(noDeal)!!.totalSen)
        val deal = repo.checkout(listOf(CartItem(raiden, 2), CartItem(furina, 1)), PaymentMethod.CASH)
        assertEquals(2500, db.orderDao().getById(deal)!!.totalSen)

        // Switching to a category bundle clears the product links
        repo.saveBundle(saved.bundle.copy(type = BundleType.CATEGORY, category = "Badge"), productIds = setOf(raiden))
        assertTrue(db.bundleDao().getActive().single().products.isEmpty())
    }

    @Test
    fun setTakesStockFromItsParts() = runBlocking {
        repo.startEvent("Comic Fiesta")
        val a = repo.addProduct("Poster A", "Poster", 1500, 3)
        val b = repo.addProduct("Poster B", "Poster", 1500, 2)
        val set = repo.addProduct("Poster Set", "Set", 2500, stockQty = 99, parts = mapOf(a to 1, b to 1))
        assertEquals(0, stockOf(set)) // stock typed for a set is ignored
        assertEquals(2, repo.allProducts.first().first { it.id == set }.stockQty)

        val order = repo.checkout(listOf(CartItem(set, 2)), PaymentMethod.CASH)
        assertEquals(1, stockOf(a))
        assertEquals(0, stockOf(b))
        // Poster B is out, so no more sets
        assertFails { repo.checkout(listOf(CartItem(set, 1)), PaymentMethod.CASH) }
        assertEquals(1, stockOf(a))

        // Edit 2 -> 1 set returns 1 of each part
        repo.editOrder(order, listOf(CartItem(set, 1)), PaymentMethod.CASH, null)
        assertEquals(2, stockOf(a))
        assertEquals(1, stockOf(b))

        // Change the recipe after the sale: cancel still returns what was really taken
        repo.updateProduct(repo.allProducts.first().first { it.id == set }, stockBefore = 0, parts = mapOf(a to 2))
        repo.cancelOrder(order)
        assertEquals(3, stockOf(a))
        assertEquals(2, stockOf(b))

        // A part can't be deleted while a set uses it
        assertFails { repo.deleteProduct(a) }
    }

    @Test
    fun chekiTypesShareFilmAndMaterialIsNotSold() = runBlocking {
        val eventId = repo.startEvent("Comic Fiesta")
        val film = repo.addProduct("Instax film", "Material", 0, 5, isMaterial = true)
        val normal = repo.addProduct("Normal cheki", "Cheki", 1000, 0, parts = mapOf(film to 1))
        val service = repo.addProduct("Service cheki", "Cheki", 2500, 0, parts = mapOf(film to 1))

        repo.checkout(listOf(CartItem(normal, 2), CartItem(service, 2)), PaymentMethod.CASH)
        assertEquals(1, stockOf(film))
        assertFails { repo.checkout(listOf(CartItem(normal, 1), CartItem(service, 1)), PaymentMethod.CASH) }
        assertFails { repo.checkout(listOf(CartItem(film, 1)), PaymentMethod.CASH) }
        assertEquals(1, stockOf(film))
        // Movements are on the film, so the film's history shows every cheki
        assertEquals(listOf(5, -4), db.stockMovementDao().getForProduct(film).map { it.changeQty })

        // A material can't get parts, and nesting isn't allowed
        assertFails { repo.addProduct("Bad", "X", 100, 0, isMaterial = true, parts = mapOf(film to 1)) }
        assertFails { repo.addProduct("Nested", "X", 100, 0, parts = mapOf(normal to 1)) }

        repo.deleteEvent(eventId)
        assertEquals(5, stockOf(film))
    }

    @Test
    fun dragOrderIsSavedAndNewItemsGoLast() = runBlocking {
        val a = repo.addProduct("A", "Badge", 100, 1)
        val b = repo.addProduct("B", "Badge", 100, 1)
        val p = repo.addProduct("P", "Print", 100, 1)
        // Added in this order: Badge then Print
        assertEquals(listOf(a, b, p), repo.allProducts.first().map { it.id })

        repo.reorderProducts(listOf(b, a))
        repo.reorderCategories(listOf("Print", "Badge"))
        assertEquals(listOf(p, b, a), repo.allProducts.first().map { it.id })

        val c = repo.addProduct("C", "Badge", 100, 1)
        val k = repo.addProduct("K", "Keychain", 100, 1)
        assertEquals(listOf(p, b, a, c, k), repo.allProducts.first().map { it.id })
    }
}
