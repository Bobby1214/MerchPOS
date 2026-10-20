package com.bobbykong.merchpos.data.files

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.bobbykong.merchpos.data.AppDatabase
import com.bobbykong.merchpos.data.PosException
import com.bobbykong.merchpos.data.model.ExportRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.system.exitProcess

/** CSV export, plus backup/restore of the database and product photos as one ZIP file. */
class BackupManager(private val context: Context, private val db: AppDatabase) {

    suspend fun exportCsv(uri: Uri, rows: List<ExportRow>) = withContext(Dispatchers.IO) {
        val out = context.contentResolver.openOutputStream(uri) ?: throw IOException("Can't create file")
        out.bufferedWriter().use { writer ->
            writer.write(CSV_HEADER.joinToString(","))
            writer.write("\n")
            rows.forEach { writer.write(it.toCsvLine()); writer.write("\n") }
        }
    }

    suspend fun backup(uri: Uri) = withContext(Dispatchers.IO) {
        // Room uses WAL mode: recent changes sit in a -wal file. This moves them into the main .db file first.
        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
        val out = context.contentResolver.openOutputStream(uri) ?: throw IOException("Can't create file")
        ZipOutputStream(out).use { zip ->
            zip.addFile(AppDatabase.DB_NAME, context.getDatabasePath(AppDatabase.DB_NAME))
            ImageStore.dir(context).listFiles()?.forEach { zip.addFile("${ImageStore.DIR_NAME}/${it.name}", it) }
        }
    }

    /** Replaces ALL data with the backup. Call [restartApp] afterwards. */
    suspend fun restore(uri: Uri) = withContext(Dispatchers.IO) {
        val temp = File(context.cacheDir, "restore").apply { deleteRecursively(); mkdirs() }
        try {
            val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Can't open file")
            ZipInputStream(input).use { zip ->
                generateSequence { zip.nextEntry }.forEach { entry ->
                    val target = File(temp, entry.name).canonicalFile
                    // "Zip slip" check: a bad ZIP could try to write outside our folder with "../"
                    if (!target.path.startsWith(temp.canonicalPath + File.separator)) {
                        throw PosException("This isn't a MerchPOS backup")
                    }
                    if (entry.isDirectory) {
                        target.mkdirs()
                    } else {
                        target.parentFile?.mkdirs()
                        target.outputStream().use { zip.copyTo(it) }
                    }
                }
            }
            val backupDb = File(temp, AppDatabase.DB_NAME)
            if (!backupDb.isSqliteFile()) throw PosException("This isn't a MerchPOS backup")

            db.close()
            val dbFile = context.getDatabasePath(AppDatabase.DB_NAME)
            listOf("", "-wal", "-shm").forEach { File(dbFile.path + it).delete() }
            backupDb.copyTo(dbFile, overwrite = true)

            val images = ImageStore.dir(context)
            images.deleteRecursively()
            File(temp, ImageStore.DIR_NAME).takeIf { it.exists() }?.copyRecursively(images)
            images.mkdirs()
        } finally {
            temp.deleteRecursively()
        }
    }

    /** Starts the app fresh so it opens the restored database. Room upgrades an older backup automatically. */
    fun restartApp() {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)
        exitProcess(0)
    }

    private fun ZipOutputStream.addFile(name: String, file: File) {
        putNextEntry(ZipEntry(name))
        file.inputStream().use { it.copyTo(this) }
        closeEntry()
    }

    private fun File.isSqliteFile(): Boolean {
        if (!isFile || length() < 16) return false
        val header = ByteArray(16)
        inputStream().use { it.read(header) }
        return String(header, Charsets.US_ASCII) == "SQLite format 3\u0000"
    }

    companion object {
        val CSV_HEADER = listOf(
            "order_id", "created_at", "sale_date", "sale_hour", "event_id", "event_name",
            "order_status", "payment_method", "product_id", "product_name", "category",
            "qty", "unit_price_sen", "is_gift", "line_total_sen",
            "order_subtotal_sen", "order_bundle_discount_sen", "order_discount_sen", "order_total_sen",
            "order_is_edited",
        )
    }
}

fun ExportRow.toCsvLine(): String = listOf(
    orderId, createdAt, saleDate, saleHour, eventId, eventName,
    orderStatus, paymentMethod, productId, productName, category,
    qty, unitPriceSen, isGift, lineTotalSen,
    orderSubtotalSen, orderBundleDiscountSen, orderDiscountSen, orderTotalSen,
    orderIsEdited,
).joinToString(",") { csvField(it.toString()) }

/** Quotes a field if it has a comma, quote or new line. A quote inside becomes two quotes. */
fun csvField(value: String): String =
    if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + value.replace("\"", "\"\"") + "\"" else value
