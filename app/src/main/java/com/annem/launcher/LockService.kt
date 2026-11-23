package com.annem.launcher

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.KeyguardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * Annem'in erişilebilirlik servisi. Üç iş yapar, hiçbiri ekran içeriğini okumaz:
 *  1. "Ekranı Kapat": güç tuşuna kısa basmak gibi (GLOBAL_ACTION_LOCK_SCREEN).
 *  2. Ses tuşu kilidi: açıksa ses tuşlarını yutar. Aramada ve telefon çalarken tuşlar
 *     her zaman çalışır (karşı tarafı duyamazsa sesi açabilsin, çalan telefonu susturabilsin).
 *  3. Kontrol paneli kilidi (isteğe bağlı, Android 12+): panel aşağı çekilince geri kapatır.
 *
 * Pil: ses tuşu kilidi yalnızca tuşa basıldığı an çalışır. Kontrol paneli kilidi kapalıyken
 * servis hiçbir pencere olayı dinlemez; açıkken yalnızca sistem arayüzünün olaylarını dinler.
 *
 * Cihaz yöneticisi (DevicePolicyManager.lockNow) yerine bu yol tercih edilir: lockNow sonrası
 * Android bir sonraki açılışta parmak izi yerine PIN ister; bu yöntem istemez.
 */
class LockService : AccessibilityService() {

    override fun onServiceConnected() {
        instance = this
        applyConfig()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    /** Ses tuşları: kilit açıksa ve arama/çalma yoksa tuşu yut (true = sistem işlemez). */
    override fun onKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (code != KeyEvent.KEYCODE_VOLUME_UP &&
            code != KeyEvent.KEYCODE_VOLUME_DOWN &&
            code != KeyEvent.KEYCODE_VOLUME_MUTE
        ) {
            return false
        }
        if (!volumeLockEnabled(this)) return false
        if (inCallOrRinging()) return false
        return true
    }

    /** Kontrol paneli kilidi: sistem arayüzü bir pencere açınca (kilit ekranı dışında) paneli kapat. */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (!panelLockActive(this)) return
        if (event.packageName?.toString() != SYSTEM_UI) return
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (keyguard?.isKeyguardLocked == true) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
        }
    }

    override fun onInterrupt() = Unit

    private fun inCallOrRinging(): Boolean {
        val audio = getSystemService(AudioManager::class.java) ?: return false
        return when (audio.mode) {
            AudioManager.MODE_IN_CALL, AudioManager.MODE_IN_COMMUNICATION, AudioManager.MODE_RINGTONE -> true
            else -> false
        }
    }

    /** Kontrol paneli kilidi kapalıyken hiçbir pencere olayı istenmez (pil). */
    private fun applyConfig() {
        val info = serviceInfo ?: return
        val panel = panelLockActive(this)
        info.eventTypes = if (panel) AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED else AccessibilityEvent.TYPE_ANNOUNCEMENT
        info.packageNames = if (panel) arrayOf(SYSTEM_UI) else null
        info.flags = info.flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        serviceInfo = info
    }

    companion object {
        private const val SYSTEM_UI = "com.android.systemui"
        private const val PREFS = "annem"
        const val KEY_VOLUME_LOCK = "volume_lock"
        const val KEY_PANEL_LOCK = "panel_lock"

        @Volatile
        private var instance: LockService? = null

        val isSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

        /** Kontrol paneli kilidi Android 12 ve üstünde desteklenir. */
        val isPanelLockSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

        /** Ekranı kapatır; servis açık değilse ya da desteklenmiyorsa false döner. */
        fun lock(): Boolean {
            if (!isSupported) return false
            val service = instance ?: return false
            return service.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
        }

        /** Ayar değişince servisin dinlediği olayları güncelle. */
        fun refreshConfig() {
            instance?.applyConfig()
        }

        val isConnected: Boolean get() = instance != null

        fun volumeLockEnabled(context: Context): Boolean =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_VOLUME_LOCK, true)

        fun panelLockActive(context: Context): Boolean =
            isPanelLockSupported &&
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_PANEL_LOCK, false)

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
