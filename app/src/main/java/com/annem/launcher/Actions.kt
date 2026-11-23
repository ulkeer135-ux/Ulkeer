package com.annem.launcher

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.MediaStore
import android.widget.Toast

/** Ana ekrandaki her düğmenin başlattığı işlem. Hepsi hata durumunda sade bir mesaj gösterir. */
object Actions {

    private const val WHATSAPP = "com.whatsapp"
    private const val WHATSAPP_BUSINESS = "com.whatsapp.w4b"
    private const val GOOGLE_PHOTOS = "com.google.android.apps.photos"
    private const val WA_VIDEO_MIME = "vnd.android.cursor.item/vnd.com.whatsapp.video.call"

    fun openDialer(context: Context) {
        if (!start(context, Intent(Intent.ACTION_DIAL))) {
            toast(context, "Telefon uygulaması açılamadı")
        }
    }

    fun openWhatsApp(context: Context) {
        val intent = context.packageManager.getLaunchIntentForPackage(WHATSAPP)
            ?: context.packageManager.getLaunchIntentForPackage(WHATSAPP_BUSINESS)
        if (intent == null || !start(context, intent)) {
            toast(context, "WhatsApp bu telefonda yüklü değil")
        }
    }

    fun openMessages(context: Context) {
        val main = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING)
        val fallback = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"))
        if (!start(context, main) && !start(context, fallback)) {
            toast(context, "Mesajlar açılamadı")
        }
    }

    fun openCamera(context: Context) {
        if (!start(context, Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))) {
            toast(context, "Kamera açılamadı")
        }
    }

    fun openGallery(context: Context) {
        val gallery = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_GALLERY)
        val photos = context.packageManager.getLaunchIntentForPackage(GOOGLE_PHOTOS)
        val ok = start(context, gallery) || (photos != null && start(context, photos))
        if (!ok) toast(context, "Fotoğraflar açılamadı")
    }

    /**
     * Arama izni verildiyse numarayı doğrudan arar, verilmediyse numara yazılı halde
     * telefon uygulamasını açar. Android, 112 gibi acil numaraları güvenlik gereği her
     * zaman telefon uygulamasında açar; orada yeşil düğmeye basmak gerekir.
     */
    fun call(context: Context, number: String) {
        val uri = Uri.fromParts("tel", number, null)
        val canCall = context.checkSelfPermission(Manifest.permission.CALL_PHONE) ==
            PackageManager.PERMISSION_GRANTED
        val started = (canCall && start(context, Intent(Intent.ACTION_CALL, uri))) ||
            start(context, Intent(Intent.ACTION_DIAL, uri))
        if (!started) toast(context, "Arama başlatılamadı")
    }

    /**
     * WhatsApp görüntülü aramayı doğrudan başlatır. Bunun için kişinin telefon rehberinde
     * kayıtlı olması ve rehber izninin verilmiş olması gerekir. Olmazsa WhatsApp'ta
     * kişinin sohbetini açar (oradan kamera simgesine dokunulur).
     */
    fun videoCall(context: Context, number: String) {
        if (startWhatsAppVideo(context, number)) return

        val digits = normalizeForWhatsApp(number)
        val chat = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$digits")).setPackage(WHATSAPP)
        if (start(context, chat)) {
            toast(context, "Görüntülü arama için üstteki kamera simgesine dokun")
        } else {
            toast(context, "WhatsApp bu telefonda yüklü değil")
        }
    }

    private fun startWhatsAppVideo(context: Context, number: String): Boolean {
        if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS) !=
            PackageManager.PERMISSION_GRANTED
        ) return false

        val tail = normalizeForWhatsApp(number).takeLast(10)
        if (tail.length < 7) return false

        val dataId = findWhatsAppVideoDataId(context, tail) ?: return false
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(
                Uri.withAppendedPath(ContactsContract.Data.CONTENT_URI, dataId.toString()),
                WA_VIDEO_MIME,
            )
            .setPackage(WHATSAPP)
        return start(context, intent)
    }

    /** Rehberde, numarası [tail] ile biten WhatsApp görüntülü arama kaydının kimliği. */
    private fun findWhatsAppVideoDataId(context: Context, tail: String): Long? {
        val projection = arrayOf(ContactsContract.Data._ID, ContactsContract.Data.DATA1)
        val selection = "${ContactsContract.Data.MIMETYPE} = ?"
        val cursor = try {
            context.contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                projection,
                selection,
                arrayOf(WA_VIDEO_MIME),
                null,
            )
        } catch (e: Exception) {
            null
        } ?: return null

        var found: Long? = null
        try {
            while (found == null && cursor.moveToNext()) {
                val data = cursor.getString(1) ?: ""
                val waDigits = data.substringBefore("@").filter { it.isDigit() }
                if (waDigits.isNotEmpty() && waDigits.endsWith(tail)) {
                    found = cursor.getLong(0)
                }
            }
        } finally {
            cursor.close()
        }
        return found
    }

    /** "0532 123 45 67", "+90 532…", "532…" → "905321234567" */
    fun normalizeForWhatsApp(number: String): String {
        var d = number.filter { it.isDigit() }
        if (d.startsWith("00")) d = d.drop(2)
        if (d.length == 11 && d.startsWith("0")) d = "90" + d.drop(1)
        if (d.length == 10 && d.startsWith("5")) d = "90$d"
        return d
    }

    private fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }

    private fun toast(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
}
