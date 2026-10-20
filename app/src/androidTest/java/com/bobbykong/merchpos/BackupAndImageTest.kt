package com.bobbykong.merchpos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bobbykong.merchpos.data.AppDatabase
import com.bobbykong.merchpos.data.CartItem
import com.bobbykong.merchpos.data.PosException
import com.bobbykong.merchpos.data.PosRepository
import com.bobbykong.merchpos.data.files.BackupManager
import com.bobbykong.merchpos.data.files.ImageStore
import com.bobbykong.merchpos.data.model.PaymentMethod
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Uses the real on-disk database file, like the app does. */
@RunWith(AndroidJUnit4::class)
class BackupAndImageTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun cleanStart() {
        context.deleteDatabase(AppDatabase.DB_NAME)
        ImageStore.dir(context).deleteRecursively()
    }

    private fun openDb() = Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DB_NAME).build()

    private fun cacheFile(name: String) = File(context.cacheDir, name).apply { delete() }

    @Test
    fun backupThenRestoreBringsBackDataAndPhotos() = runBlocking {
        val db = openDb()
        val repo = PosRepository(db)
        repo.startEvent("Comic Fiesta")
        val badge = repo.addProduct("Badge", "Badge", 800, 10, imagePath = "photo.jpg")
        ImageStore.file(context, "photo.jpg").writeText("fake image")
        repo.checkout(listOf(CartItem(badge, 2)), PaymentMethod.CASH)

        val zip = cacheFile("backup.zip")
        BackupManager(context, db).backup(Uri.fromFile(zip))

        // Changes after the backup should disappear on restore
        repo.checkout(listOf(CartItem(badge, 5)), PaymentMethod.CASH)
        ImageStore.file(context, "photo.jpg").delete()

        BackupManager(context, db).restore(Uri.fromFile(zip))

        val restored = openDb()
        assertEquals(8, restored.productDao().getById(badge)!!.stockQty)
        assertEquals(1, restored.reportDao().observeSummary(1).first().orderCount)
        assertEquals("fake image", ImageStore.file(context, "photo.jpg").readText())
        restored.close()
    }

    @Test
    fun restoreRejectsFilesThatAreNotBackups() = runBlocking {
        val db = openDb()
        PosRepository(db).startEvent("Keep me")
        val bad = cacheFile("bad.zip")
        ZipOutputStream(bad.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry(AppDatabase.DB_NAME))
            zip.write("not a database".toByteArray())
            zip.closeEntry()
        }
        try {
            BackupManager(context, db).restore(Uri.fromFile(bad))
            fail("Expected PosException")
        } catch (_: PosException) {
        }
        // The current data is untouched
        assertEquals("Keep me", PosRepository(db).events.first().single().name)
        db.close()
    }

    @Test
    fun csvExportWritesHeaderAndRows() = runBlocking {
        val db = openDb()
        val repo = PosRepository(db)
        repo.startEvent("Comic Fiesta, KL")
        val badge = repo.addProduct("Badge", "Badge", 800, 10)
        repo.checkout(listOf(CartItem(badge, 2)), PaymentMethod.CASH)

        val csv = cacheFile("sales.csv")
        BackupManager(context, db).exportCsv(Uri.fromFile(csv), repo.exportRows())

        val lines = csv.readLines()
        assertEquals(BackupManager.CSV_HEADER.joinToString(","), lines[0])
        assertEquals(2, lines.size)
        assertTrue(lines[1].contains("\"Comic Fiesta, KL\""))
        assertTrue(lines[1].contains(",CASH,"))
        db.close()
    }

    @Test
    fun importShrinksBigImages() {
        val source = cacheFile("big.png")
        Bitmap.createBitmap(3000, 2000, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, source.outputStream())

        val name = ImageStore.import(context, Uri.fromFile(source))

        val saved = BitmapFactory.decodeFile(ImageStore.file(context, name).path)
        assertEquals(1024, maxOf(saved.width, saved.height))
        assertEquals(3f / 2f, saved.width.toFloat() / saved.height, 0.01f)
    }
}
