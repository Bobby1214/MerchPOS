package com.bobbykong.merchpos

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bobbykong.merchpos.data.AppDatabase
import com.bobbykong.merchpos.data.CartItem
import com.bobbykong.merchpos.data.PosException
import com.bobbykong.merchpos.data.PosRepository
import com.bobbykong.merchpos.data.model.MovementReason
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
}
