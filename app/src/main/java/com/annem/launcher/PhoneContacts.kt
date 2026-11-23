package com.annem.launcher

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.ContactsContract
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Locale

/** Telefon rehberindeki, telefon numarası olan bir kişi. */
data class PhoneContact(
    val id: Long,
    val name: String,
    val number: String,
    val photoUri: String?,
    val starred: Boolean,
    /** Büyük (tam boy) fotoğraf; favori kartlarında kullanılır. */
    val fullPhotoUri: String? = null,
)

/** Kişileri doğrudan telefonun rehberinden okur; uygulamada ayrıca kişi tutulmaz. */
object PhoneContacts {

    fun hasPermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    /** Favoriler (rehberde yıldızlı) önce, sonra alfabetik (Türkçe sıralama). */
    suspend fun load(context: Context): List<PhoneContact> = withContext(Dispatchers.IO) {
        if (!hasPermission(context)) return@withContext emptyList()
        val phone = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI,
            ContactsContract.CommonDataKinds.Phone.STARRED,
            ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
        )
        val byId = LinkedHashMap<Long, PhoneContact>()
        try {
            // Varsayılan (birincil) numara önce gelsin; her kişiden yalnızca biri alınır.
            context.contentResolver.query(
                phone,
                projection,
                null,
                null,
                "${ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY} DESC, " +
                    "${ContactsContract.CommonDataKinds.Phone.IS_PRIMARY} DESC",
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    if (byId.containsKey(id)) continue
                    val name = c.getString(1)?.trim().orEmpty()
                    val number = c.getString(2)?.trim().orEmpty()
                    if (name.isEmpty() || number.isEmpty()) continue
                    byId[id] = PhoneContact(
                        id = id,
                        name = name,
                        number = number,
                        photoUri = c.getString(3),
                        starred = c.getInt(4) == 1,
                        fullPhotoUri = c.getString(5),
                    )
                }
            }
        } catch (e: Exception) {
            return@withContext emptyList()
        }
        val collator = Collator.getInstance(Locale.forLanguageTag("tr-TR"))
        byId.values.sortedWith(
            compareByDescending<PhoneContact> { it.starred }.thenComparator { a, b -> collator.compare(a.name, b.name) }
        )
    }

    suspend fun loadPhoto(context: Context, uri: String): ImageBitmap? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(Uri.parse(uri))?.use { stream ->
                BitmapFactory.decodeStream(stream)?.asImageBitmap()
            }
        } catch (e: Exception) {
            null
        }
    }
}
