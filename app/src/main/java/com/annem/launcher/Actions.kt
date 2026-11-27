package com.annem.launcher

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast
import java.net.URLEncoder

/** Ana ekrandaki her düğmenin başlattığı işlem. Hepsi hata durumunda sade bir mesaj gösterir. */
object Actions {

    private const val WHATSAPP = "com.whatsapp"
    private const val WHATSAPP_BUSINESS = "com.whatsapp.w4b"

    /** Telefonun kendi rehber uygulamasını açar. */
    fun openContacts(context: Context) {
        val main = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CONTACTS)
        val fallback = Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI)
        if (!start(context, main) && !start(context, fallback)) {
            toast(context, "Rehber açılamadı")
        }
    }

    /** Paket adıyla bir uygulamayı açar; yüklü değilse false. */
    fun openApp(context: Context, packageName: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        return start(context, intent)
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

    /** Kişiyle WhatsApp görüntülü araması başlatır (rehberdeki WhatsApp satırı üzerinden). */
    fun whatsappVideoCall(context: Context, video: WhatsAppVideo) {
        val pkg = if (video.mimeType == PhoneContacts.WHATSAPP_BUSINESS_VIDEO_MIME) WHATSAPP_BUSINESS else WHATSAPP
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(
                android.content.ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI, video.dataId),
                video.mimeType,
            )
            .setPackage(pkg)
        if (!start(context, intent)) toast(context, "WhatsApp görüntülü arama başlatılamadı")
    }

    private fun start(context: Context, intent: Intent): Boolean = try {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // Hava durumu çekmecesi gibi: uygulama alttan yukarı kayarak açılır (Annem arkada durur).
        com.annem.launcher.ui.LaunchOrigin.take()
        val options = android.app.ActivityOptions.makeCustomAnimation(
            context, R.anim.annem_sheet_up, R.anim.annem_stay,
        ).toBundle()
        try {
            context.startActivity(intent, options)
        } catch (e: IllegalArgumentException) {
            context.startActivity(intent)
        }
        // Dönüşte hava durumu kapanışı gibi balonla toplanan çekmece oynasın.
        com.annem.launcher.ui.HomeReveal.markAppLaunched()
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
