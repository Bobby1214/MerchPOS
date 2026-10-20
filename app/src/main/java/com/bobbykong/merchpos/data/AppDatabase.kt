package com.bobbykong.merchpos.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.bobbykong.merchpos.data.dao.BundleDao
import com.bobbykong.merchpos.data.dao.EventDao
import com.bobbykong.merchpos.data.dao.OrderDao
import com.bobbykong.merchpos.data.dao.ProductDao
import com.bobbykong.merchpos.data.dao.ProductPartDao
import com.bobbykong.merchpos.data.dao.ReportDao
import com.bobbykong.merchpos.data.dao.StockMovementDao
import com.bobbykong.merchpos.data.model.BundleDeal
import com.bobbykong.merchpos.data.model.BundleProduct
import com.bobbykong.merchpos.data.model.Event
import com.bobbykong.merchpos.data.model.Order
import com.bobbykong.merchpos.data.model.OrderItem
import com.bobbykong.merchpos.data.model.Product
import com.bobbykong.merchpos.data.model.ProductPart
import com.bobbykong.merchpos.data.model.StockMovement

@Database(
    entities = [
        Event::class, Product::class, Order::class, OrderItem::class, StockMovement::class, BundleDeal::class,
        BundleProduct::class, ProductPart::class,
    ],
    version = 4,
    exportSchema = true,
    // Room writes these migrations from the saved schemas.
    // v2: bundles table + discount columns. v3: bundle type + bundle_products link table.
    // v4: product_parts (sets, cheki film) + products.is_material.
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4)],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun eventDao(): EventDao
    abstract fun productDao(): ProductDao
    abstract fun orderDao(): OrderDao
    abstract fun stockMovementDao(): StockMovementDao
    abstract fun reportDao(): ReportDao
    abstract fun bundleDao(): BundleDao
    abstract fun productPartDao(): ProductPartDao

    companion object {
        const val DB_NAME = "merchpos.db"

        @Volatile
        private var instance: AppDatabase? = null

        // Singleton: the whole app shares one database connection
        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DB_NAME,
                ).build().also { instance = it }
            }
    }
}
