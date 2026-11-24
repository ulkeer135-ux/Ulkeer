package com.annem.launcher

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.app.ActivityOptions
import android.app.KeyguardManager
import android.provider.Settings
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * İki iş yapar:
 *  1. "Ekranı Kapat" düğmesi: güç tuşuna kısa basmakla aynı şeyi yapar (GLOBAL_ACTION_LOCK_SCREEN).
 *  2. Hızlı ana ekran tuşu: OPPO/ColorOS, ana ekran tuşunda üçüncü taraf ana ekranlara geçmeden
 *     önce ~1 sn duvar kağıdı gösteren kendi animasyonunu oynatıyor. Servis yalnızca ANA EKRAN
 *     tuşunu karşılar ve Annem'i doğrudan, animasyonsuz öne getirir (geri tuşundaki kadar hızlı).
 *     Diğer tüm tuşlar dokunulmadan geçer. Ekran içeriğini okumaz.
 *
 * Cihaz yöneticisi (DevicePolicyManager.lockNow) yerine bu yol seçildi: lockNow sonrası
 * Android bir sonraki açılışta parmak izi yerine PIN ister; bu yöntem istemez.
 */
class LockService : AccessibilityService() {

    override fun onServiceConnected() {
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_HOME) return false
        if (!getSharedPreferences("annem", MODE_PRIVATE).getBoolean("fast_home", true)) return false
        // Kilit ekranındayken telefonun kendi davranışı kalsın.
        if (getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true) return false
        if (event.action == KeyEvent.ACTION_UP) {
            val prefs = getSharedPreferences("annem", MODE_PRIVATE)
            prefs.edit().putInt("home_key_count", prefs.getInt("home_key_count", 0) + 1).apply()
        }
        if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) {
            // Annem zaten ekrandaysa yeniden başlatma: yalnızca ana sayfaya dön.
            if (MainActivity.showHomeIfVisible()) return true
            try {
                // HOME kategorisi şart: yoksa Android bunu sıradan bir açılış sayıp
                // Annem'in ikinci bir kopyasını oluşturuyordu (bir an kaybolup açılma).
                val intent = Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .setClass(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                startActivity(intent, ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle())
            } catch (_: Throwable) {
                // Olmazsa telefonun kendi ana ekran eylemi.
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
        }
        return true
    }

    override fun onInterrupt() = Unit

    companion object {
        @Volatile
        private var instance: LockService? = null

        val isSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

        /** Ekranı kapatır; servis açık değilse ya da desteklenmiyorsa false döner. */
        fun lock(): Boolean {
            if (!isSupported) return false
            val service = instance ?: return false
            return service.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
        }

        /** Servis şu an bağlı mı (telefon çalıştırıyor mu)? */
        val isConnected: Boolean get() = instance != null

        /** Telefon bu servise tuş olaylarını iletiyor mu (tuş yakalama yetkisi verilmiş mi)? */
        val canFilterKeys: Boolean
            get() {
                val info = instance?.serviceInfo ?: return false
                val flagOn = info.flags and android.accessibilityservice.AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS != 0
                val capOn = info.capabilities and android.accessibilityservice.AccessibilityServiceInfo.CAPABILITY_CAN_REQUEST_FILTER_KEY_EVENTS != 0
                return flagOn && capOn
            }

        fun homeKeyCount(context: Context): Int =
            context.getSharedPreferences("annem", Context.MODE_PRIVATE).getInt("home_key_count", 0)

        /** Servis Erişilebilirlik ayarlarında açılmış mı? */
        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            val me = ComponentName(context, LockService::class.java)
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
        }
    }
}
