package com.annem.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Ana ekrandaki "Ailem" listesinde görünen kişi. photoVersion = 0 ise fotoğraf yok. */
data class FamilyContact(
    val id: String,
    val name: String,
    val phone: String,
    val photoVersion: Long = 0L,
)

/**
 * Kişileri ve acil durum numarasını SharedPreferences'ta, fotoğrafları uygulamanın
 * kendi klasöründe tutar. İnternet ya da hesap gerekmez.
 */
class ContactStore(private val context: Context) {

    private val prefs = context.getSharedPreferences("annem", Context.MODE_PRIVATE)

    val contacts = mutableStateListOf<FamilyContact>()

    var emergencyNumber by mutableStateOf(prefs.getString(KEY_EMERGENCY, "112") ?: "112")
        private set

    init {
        load()
    }

    fun upsert(contact: FamilyContact) {
        val index = contacts.indexOfFirst { it.id == contact.id }
        if (index >= 0) contacts[index] = contact else contacts.add(contact)
        save()
    }

    fun delete(id: String) {
        contacts.removeAll { it.id == id }
        photoFile(id).delete()
        save()
    }

    fun move(id: String, delta: Int) {
        val from = contacts.indexOfFirst { it.id == id }
        val to = from + delta
        if (from < 0 || to < 0 || to >= contacts.size) return
        val item = contacts.removeAt(from)
        contacts.add(to, item)
        save()
    }

    fun updateEmergencyNumber(number: String) {
        emergencyNumber = number.trim().ifEmpty { "112" }
        prefs.edit().putString(KEY_EMERGENCY, emergencyNumber).apply()
    }

    fun photoFile(id: String): File {
        val dir = File(context.filesDir, "photos")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "$id.jpg")
    }

    fun loadPhoto(id: String): ImageBitmap? {
        val file = photoFile(id)
        if (!file.exists()) return null
        return BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
    }

    /** Seçilen fotoğrafı küçültüp (en fazla ~512 px) döndürmeyi düzelterek kaydeder. */
    fun savePhoto(id: String, uri: Uri): Boolean {
        try {
            val resolver = context.contentResolver

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false

            var sample = 1
            while (bounds.outWidth / (sample * 2) >= 512 && bounds.outHeight / (sample * 2) >= 512) {
                sample *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sample }
            var bitmap: Bitmap = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOptions)
            } ?: return false

            val rotation = resolver.openInputStream(uri)?.use { stream ->
                when (ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )) {
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

            photoFile(id).outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            return true
        } catch (e: Exception) {
            return false
        }
    }

    private fun load() {
        contacts.clear()
        val raw = prefs.getString(KEY_CONTACTS, null) ?: return
        try {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                contacts.add(
                    FamilyContact(
                        id = o.getString("id"),
                        name = o.getString("name"),
                        phone = o.getString("phone"),
                        photoVersion = o.optLong("photoVersion", 0L),
                    )
                )
            }
        } catch (e: Exception) {
            // Bozuk kayıt: boş listeyle devam et.
        }
    }

    private fun save() {
        val array = JSONArray()
        contacts.forEach { c ->
            array.put(
                JSONObject()
                    .put("id", c.id)
                    .put("name", c.name)
                    .put("phone", c.phone)
                    .put("photoVersion", c.photoVersion)
            )
        }
        prefs.edit().putString(KEY_CONTACTS, array.toString()).apply()
    }

    private companion object {
        const val KEY_CONTACTS = "contacts"
        const val KEY_EMERGENCY = "emergency"
    }
}
