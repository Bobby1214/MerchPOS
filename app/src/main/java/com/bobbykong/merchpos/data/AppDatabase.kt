package com.bobbykong.merchpos.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.bobbykong.merchpos.data.dao.EventDao
import com.bobbykong.merchpos.data.dao.OrderDao
import com.bobbykong.merchpos.data.dao.ProductDao
import com.bobbykong.merchpos.data.dao.ReportDao
import com.bobbykong.merchpos.data.dao.StockMovementDao
import com.bobbykong.merchpos.data.model.Event
import com.bobbykong.merchpos.data.model.Order
import com.bobbykong.merchpos.data.model.OrderItem
import com.bobbykong.merchpos.data.model.Product
import com.bobbykong.merchpos.data.model.StockMovement

@Database(
    entities = [Event::class, Product::class, Order::class, OrderItem::class, StockMovement::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun eventDao(): EventDao
    abstract fun productDao(): ProductDao
    abstract fun orderDao(): OrderDao
    abstract fun stockMovementDao(): StockMovementDao
    abstract fun reportDao(): ReportDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        // Singleton: the whole app shares one database connection
        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "merchpos.db",
                ).build().also { instance = it }
            }
    }
}
