package com.annem.launcher

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent

/**
 * Yalnızca "Ekranı Kapat" düğmesi için var: güç tuşuna kısa basmakla aynı şeyi yapar
 * (GLOBAL_ACTION_LOCK_SCREEN). Ekran içeriğini okumaz, hiçbir olayı işlemez.
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
