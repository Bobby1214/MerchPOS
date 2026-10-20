package com.bobbykong.merchpos.data.files

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.IOException
import java.util.UUID

/** Product photos live in the app's private files/product_images folder. The DB stores only the file name. */
object ImageStore {
    const val DIR_NAME = "product_images"
    private const val MAX_SIZE_PX = 1024

    fun dir(context: Context): File = File(context.filesDir, DIR_NAME).apply { mkdirs() }

    fun file(context: Context, name: String): File = File(dir(context), name)

    /** A temporary file + content:// Uri the camera app can write a photo into. */
    fun newCameraUri(context: Context): Uri {
        val folder = File(context.cacheDir, "camera").apply { mkdirs() }
        val file = File(folder, "photo.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    /**
     * Copies a picked or captured image into app storage, shrunk to max 1024px and turned upright.
     * Phone photos are often 12MP+; shrinking keeps the app and backups small. Returns the new file name.
     */
    fun import(context: Context, source: Uri): String {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0) throw IOException("Can't read this image")

        // Load a smaller version straight away instead of the full photo, to save memory
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIZE_PX) sample *= 2
        val decoded = resolver.openInputStream(source)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: throw IOException("Can't read this image")

        val rotation = runCatching {
            resolver.openInputStream(source)?.use { ExifInterface(it).rotationDegrees } ?: 0
        }.getOrDefault(0)
        val scale = minOf(1f, MAX_SIZE_PX.toFloat() / maxOf(decoded.width, decoded.height))
        val matrix = Matrix().apply {
            postScale(scale, scale)
            postRotate(rotation.toFloat())
        }
        val upright = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)

        val name = "${UUID.randomUUID()}.jpg"
        file(context, name).outputStream().use { upright.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        return name
    }

    fun delete(context: Context, name: String?) {
        if (name != null) file(context, name).delete()
    }
}
