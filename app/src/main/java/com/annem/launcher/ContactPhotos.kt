package com.annem.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Kişilere Annem içinde seçilen fotoğraflar (ör. WhatsApp'tan kaydedilen profil fotoğrafı).
 * Uygulamanın kendi klasöründe tutulur; telefonun rehberi değişmez.
 */
object ContactPhotos {

    private const val MAX_SIDE = 720

    private fun file(context: Context, id: Long): File {
        val dir = File(context.filesDir, "contact_photos")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "$id.jpg")
    }

    suspend fun load(context: Context, id: Long): ImageBitmap? = withContext(Dispatchers.IO) {
        val f = file(context, id)
        if (!f.exists()) null else BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap()
    }

    fun delete(context: Context, id: Long) {
        file(context, id).delete()
    }

    /** Seçilen fotoğrafı küçültür, döndürmesini düzeltir ve kaydeder. */
    suspend fun save(context: Context, id: Long, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext false

            var sample = 1
            while (bounds.outWidth / (sample * 2) >= MAX_SIDE && bounds.outHeight / (sample * 2) >= MAX_SIDE) {
                sample *= 2
            }
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            var bitmap: Bitmap = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            } ?: return@withContext false

            val rotation = resolver.openInputStream(uri)?.use { stream ->
                when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
            if (rotation != 0f) {
                val matrix = Matrix().apply { postRotate(rotation) }
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            }
            file(context, id).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            true
        } catch (e: Exception) {
            false
        }
    }
}
