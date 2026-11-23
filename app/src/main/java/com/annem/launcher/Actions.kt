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
import java.net.URLEncoder

/** Ana ekrandaki her düğmenin başlattığı işlem. Hepsi hata durumunda sade bir mesaj gösterir. */
object Actions {

    private const val WHATSAPP = "com.whatsapp"
    private const val WHATSAPP_BUSINESS = "com.whatsapp.w4b"
    private const val GOOGLE_PHOTOS = "com.google.android.apps.photos"

    /** Telefonun kendi rehber uygulamasını açar. */
    fun openContacts(context: Context) {
        val main = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CONTACTS)
        val fallback = Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI)
        if (!start(context, main) && !start(context, fallback)) {
            toast(context, "Rehber açılamadı")
        }
    }

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
     * telefon uygulamasını açar (orada yeşil düğmeye basmak gerekir).
     */
    fun call(context: Context, number: String) {
        val uri = Uri.fromParts("tel", number, null)
        val canCall = context.checkSelfPermission(Manifest.permission.CALL_PHONE) ==
            PackageManager.PERMISSION_GRANTED
        val started = (canCall && start(context, Intent(Intent.ACTION_CALL, uri))) ||
            start(context, Intent(Intent.ACTION_DIAL, uri))
        if (!started) toast(context, "Arama başlatılamadı")
    }

    /** Google'ın hava durumu sonucunu açar (Google uygulaması ya da tarayıcı). */
    fun openGoogleWeather(context: Context, city: String) {
        val query = URLEncoder.encode("$city hava durumu", "UTF-8")
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$query"))
        if (!start(context, intent)) toast(context, "Hava durumu sayfası açılamadı")
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
