package com.annem.launcher

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.telecom.TelecomManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/** Ana ekrandaki kırmızı sayılar. Yalnızca sayılar tutulur; bildirim içeriği saklanmaz. */
object Badges {
    var whatsapp by mutableIntStateOf(0)
        internal set
    var missedCalls by mutableIntStateOf(0)
        internal set
}

/**
 * Bildirim dinleyicisi: yalnızca WhatsApp bildirimlerini ve cevapsız arama bildirimlerini
 * SAYAR. Mesaj metinleri okunmaz, kaydedilmez, hiçbir yere gönderilmez.
 */
class BadgeService : NotificationListenerService() {

    override fun onListenerConnected() {
        recount()
    }

    override fun onListenerDisconnected() {
        Badges.whatsapp = 0
        Badges.missedCalls = 0
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        recount()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        recount()
    }

    private fun recount() {
        val active: Array<StatusBarNotification> = try {
            activeNotifications ?: emptyArray()
        } catch (e: Exception) {
            return
        }
        val dialer = defaultDialerPackage()
        var whatsapp = 0
        var missed = 0
        for (sbn in active) {
            val n = sbn.notification ?: continue
            val pkg = sbn.packageName
            if (pkg == WHATSAPP || pkg == WHATSAPP_BUSINESS) {
                whatsapp += whatsappCount(n)
            } else if (isMissedCall(pkg, n, dialer)) {
                missed += if (n.number > 0) n.number else 1
            }
        }
        Badges.whatsapp = whatsapp
        Badges.missedCalls = missed
    }

    /** Sohbet başına bildirimdeki mesaj sayısı; özet ve "arka planda çalışıyor" bildirimleri sayılmaz. */
    @Suppress("DEPRECATION")
    private fun whatsappCount(n: Notification): Int {
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return 0
        if (n.flags and Notification.FLAG_ONGOING_EVENT != 0) return 0
        if (n.flags and Notification.FLAG_FOREGROUND_SERVICE != 0) return 0
        when (n.category) {
            Notification.CATEGORY_CALL, Notification.CATEGORY_SERVICE,
            Notification.CATEGORY_PROGRESS, Notification.CATEGORY_STATUS -> return 0
        }
        val messages = n.extras?.getParcelableArray(Notification.EXTRA_MESSAGES)
        return if (messages != null && messages.isNotEmpty()) messages.size else 1
    }

    private fun isMissedCall(pkg: String, n: Notification, dialer: String?): Boolean {
        if (n.category == CATEGORY_MISSED_CALL) return true
        if (pkg != dialer) return false
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return false
        val channel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) n.channelId.orEmpty() else ""
        val title = n.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        return channel.contains("miss", ignoreCase = true) ||
            title.contains("cevapsız", ignoreCase = true) ||
            title.contains("missed", ignoreCase = true)
    }

    private fun defaultDialerPackage(): String? = try {
        (getSystemService(Context.TELECOM_SERVICE) as? TelecomManager)?.defaultDialerPackage
    } catch (e: Exception) {
        null
    }

    companion object {
        private const val WHATSAPP = "com.whatsapp"
        private const val WHATSAPP_BUSINESS = "com.whatsapp.w4b"
        private const val CATEGORY_MISSED_CALL = "missed_call" // Notification.CATEGORY_MISSED_CALL (API 30)

        /** Bildirim erişimi açık mı? */
        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
                ?: return false
            val me = ComponentName(context, BadgeService::class.java)
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
        }

        /** Mümkünse doğrudan bu uygulamanın bildirim erişimi sayfası, değilse genel liste. */
        fun settingsIntent(context: Context): Intent =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                    .putExtra(
                        Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                        ComponentName(context, BadgeService::class.java).flattenToString(),
                    )
            } else {
                Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
            }
    }
}
