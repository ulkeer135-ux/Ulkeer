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
import android.view.accessibility.AccessibilityNodeInfo
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/**
 * İki iş yapar:
 *  1. "Ekranı Kapat" düğmesi: güç tuşuna kısa basmakla aynı şeyi yapar (GLOBAL_ACTION_LOCK_SCREEN).
 *  2. Hızlı ana ekran tuşu: OPPO/ColorOS, ana ekran tuşunda üçüncü taraf ana ekranlara geçmeden
 *     önce ~1 sn duvar kağıdı gösteren kendi animasyonunu oynatıyor. Servis yalnızca ANA EKRAN
 *     tuşunu karşılar ve Annem'i doğrudan, animasyonsuz öne getirir (geri tuşundaki kadar hızlı).
 *     Diğer tüm tuşlar dokunulmadan geçer.
 *  3. (Ayar açıksa) "Ekranı Kapat"tan önce son uygulamalar ekranını açıp "Tümünü temizle"ye basar.
 *     Ekran içeriğini yalnızca bu düğmeyi bulmak için, yalnızca son uygulamalar ekranında okur.
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
                // Bazı uygulamalarda telefon bu açılışı sessizce engelleyebiliyor. Kısa süre
                // sonra Annem önde değilse telefonun kendi ana ekran eylemi yapılır:
                // tek basış her zaman ana ekrana döndürür.
                android.os.Handler(mainLooper).postDelayed({
                    if (!MainActivity.isInFront) fallbackHome()
                }, 700)
            } catch (_: Throwable) {
                fallbackHome()
            }
        }
        return true
    }

    private fun fallbackHome() {
        val prefs = getSharedPreferences("annem", MODE_PRIVATE)
        prefs.edit().putInt("home_fallback_count", prefs.getInt("home_fallback_count", 0) + 1).apply()
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    override fun onInterrupt() = Unit

    private val handler = Handler(Looper.getMainLooper())
    @Volatile private var clearing = false

    /**
     * Son uygulamalar → "Tümünü temizle" → ana ekran → (istenirse) kilit.
     * Düğme bulunamazsa ya da liste boşsa yine ana ekrana döner (ve kilitler).
     * Telefonun son uygulamalar ekranında "Kilitle" ile kilitlenen uygulamalar temizlenmez.
     */
    private fun runClear(thenLock: Boolean) {
        if (clearing) return
        clearing = true
        if (!performGlobalAction(GLOBAL_ACTION_RECENTS)) {
            finish(thenLock)
            return
        }
        val started = SystemClock.uptimeMillis()
        val poll = object : Runnable {
            override fun run() {
                val found = try { findClearAll() } catch (e: Exception) { null }
                when {
                    found === EMPTY -> {
                        // Açık uygulama yok: beklemeden bitir.
                        record("Liste boştu (açık uygulama yok)")
                        markCleared()
                        goHome(thenLock)
                    }
                    found != null -> {
                        val ok = found.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        record((if (ok) "Temizle düğmesine basıldı: " else "Düğme bulundu ama basılamadı: ") + describe(found))
                        bump(if (ok) KEY_CLEAR_OK else KEY_CLEAR_MISS)
                        if (ok) markCleared()
                        handler.postDelayed({ goHome(thenLock) }, 600)
                    }
                    SystemClock.uptimeMillis() - started > 1_800 -> {
                        // Düğme bulunamadı: yine de ana ekrana dön (ve ekranı kapat).
                        record("Temizle düğmesi bulunamadı. Ekrandakiler: " + try { dumpClickables() } catch (e: Exception) { "okunamadı" })
                        bump(KEY_CLEAR_MISS)
                        goHome(thenLock)
                    }
                    else -> handler.postDelayed(this, 150)
                }
            }
        }
        handler.postDelayed(poll, 350)
    }

    private fun goHome(thenLock: Boolean) {
        performGlobalAction(GLOBAL_ACTION_HOME)
        handler.postDelayed({ finish(thenLock) }, 300)
    }

    private fun finish(thenLock: Boolean) {
        clearing = false
        if (thenLock) performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
    }

    private fun record(text: String) {
        val time = java.text.SimpleDateFormat("HH:mm", java.util.Locale("tr")).format(java.util.Date())
        getSharedPreferences("annem", MODE_PRIVATE).edit().putString(KEY_LAST_RESULT, "$time · $text").apply()
    }

    private fun describe(n: AccessibilityNodeInfo): String {
        val id = n.viewIdResourceName?.substringAfter(":id/") ?: "-"
        val d = n.contentDescription?.toString()?.take(30) ?: ""
        val t = n.text?.toString()?.take(30) ?: ""
        return "[$id|$d|$t]"
    }

    /** Teşhis: son uygulamalar ekranındaki tıklanabilir öğeler (en fazla 15). */
    private fun dumpClickables(): String {
        val out = mutableListOf<String>()
        val roots = mutableListOf<AccessibilityNodeInfo>()
        rootInActiveWindow?.let { roots += it }
        try { windows.forEach { w -> w.root?.let { roots += it } } } catch (e: Exception) { }
        val pkgs = roots.mapNotNull { it.packageName?.toString() }.distinct()
        for (root in roots) {
            if (root.packageName?.toString() == packageName) continue
            val queue = ArrayDeque<AccessibilityNodeInfo>()
            queue.add(root)
            var seen = 0
            while (queue.isNotEmpty() && seen < 600 && out.size < 15) {
                val node = queue.removeFirst()
                seen++
                if (node.isClickable) out += describe(node)
                for (i in 0 until node.childCount) node.getChild(i)?.let { queue.add(it) }
            }
        }
        return "pencereler=" + pkgs.joinToString(",") + " öğeler=" + out.joinToString(" ")
    }

    private fun markCleared() {
        getSharedPreferences("annem", MODE_PRIVATE).edit()
            .putLong(KEY_LAST_CLEAR, System.currentTimeMillis())
            .putBoolean(KEY_DIRTY, false)
            .apply()
    }

    private fun bump(key: String) {
        val p = getSharedPreferences("annem", MODE_PRIVATE)
        p.edit().putInt(key, p.getInt(key, 0) + 1).apply()
    }

    /** Son uygulamalar ekranındaki "Tümünü temizle" düğmesi; liste boşsa EMPTY. */
    private fun findClearAll(): AccessibilityNodeInfo? {
        val roots = mutableListOf<AccessibilityNodeInfo>()
        rootInActiveWindow?.let { roots += it }
        try { windows.forEach { w -> w.root?.let { roots += it } } } catch (e: Exception) { }
        for (root in roots) {
            if (root.packageName?.toString() == packageName) continue
            search(root)?.let { return it }
        }
        return null
    }

    private fun search(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var seen = 0
        while (queue.isNotEmpty() && seen < 600) {
            val node = queue.removeFirst()
            seen++
            if (isEmptyText(node)) return EMPTY
            if (isClearAll(node)) {
                var n: AccessibilityNodeInfo? = node
                while (n != null && !n.isClickable) n = n.parent
                if (n != null && n.isVisibleToUser) return n
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let { queue.add(it) }
        }
        return null
    }

    private fun isEmptyText(node: AccessibilityNodeInfo): Boolean {
        val t = node.text?.toString()?.lowercase(java.util.Locale("tr")) ?: return false
        return EMPTY_WORDS.any { t.contains(it) }
    }

    private fun isClearAll(node: AccessibilityNodeInfo): Boolean {
        val id = node.viewIdResourceName?.lowercase() ?: ""
        if (id.contains("clear_all") || id.contains("clearall") || id.contains("clear_button") ||
            id.contains("clean") || id.contains("clear")
        ) return true
        val tr = java.util.Locale("tr")
        val label = ((node.text?.toString() ?: "") + " " + (node.contentDescription?.toString() ?: "")).lowercase(tr)
        return CLEAR_WORDS.any { label.contains(it) }
    }

    companion object {
        @Volatile
        private var instance: LockService? = null

        val isSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

        private const val KEY_CLEAR_OK = "clear_all_ok"
        private const val KEY_CLEAR_MISS = "clear_all_miss"
        private const val KEY_LAST_CLEAR = "clear_last_at"
        private const val KEY_LAST_RESULT = "clear_last_result"
        /** Son temizlikten sonra Annem'den uygulama açıldı mı? */
        const val KEY_DIRTY = "apps_used_since_clear"
        /** "Liste boş" işareti (gerçek bir düğüm değil). */
        private val EMPTY: AccessibilityNodeInfo = AccessibilityNodeInfo.obtain()
        private val EMPTY_WORDS = listOf(
            "son uygulama yok", "yakın zamanda kullanılan", "uygulama yok", "no recent", "nothing here",
        )
        private val CLEAR_WORDS = listOf(
            "tümünü temizle", "hepsini temizle", "tümünü kapat", "hepsini kapat", "temizle",
            "clear all", "close all", "clear",
        )

        /** Önce arka plan uygulamalarını kapatıp sonra kilitler (arka planda); servis yoksa false. */
        fun clearRecentsThenLock(context: Context): Boolean = clearRecents(context, thenLock = true)

        /** Arka plan uygulamalarını kapatır; thenLock ise ardından ekranı kapatır. */
        fun clearRecents(context: Context, thenLock: Boolean): Boolean {
            val p = context.getSharedPreferences("annem", Context.MODE_PRIVATE)
            fun note(t: String) {
                val time = java.text.SimpleDateFormat("HH:mm", java.util.Locale("tr")).format(java.util.Date())
                p.edit().putString(KEY_LAST_RESULT, "$time · $t").apply()
            }
            if (!isSupported) { note("Bu Android sürümünde desteklenmiyor"); return false }
            val service = instance ?: run { note("Erişilebilirlik servisi çalışmıyor"); return false }
            if (!canReadScreen) {
                note("Ekranı okuma yetkisi yok: Erişilebilirlik'te Annem'i kapatıp yeniden aç")
                return false
            }
            service.handler.post { service.runClear(thenLock) }
            return true
        }

        /** Annem'den uygulama açılınca çağrılır: bir sonraki otomatik temizlik gerekli olsun. */
        fun markAppsUsed(context: Context) {
            context.getSharedPreferences("annem", Context.MODE_PRIVATE).edit().putBoolean(KEY_DIRTY, true).apply()
        }

        fun appsUsedSinceClear(context: Context): Boolean =
            context.getSharedPreferences("annem", Context.MODE_PRIVATE).getBoolean(KEY_DIRTY, true)

        fun lastResult(context: Context): String =
            context.getSharedPreferences("annem", Context.MODE_PRIVATE).getString(KEY_LAST_RESULT, null) ?: "Henüz denenmedi"

        fun lastClearAt(context: Context): Long =
            context.getSharedPreferences("annem", Context.MODE_PRIVATE).getLong(KEY_LAST_CLEAR, 0L)

        /** Servis ekran içeriğini okuyabiliyor mu (Tümünü temizle'yi bulmak için)? */
        val canReadScreen: Boolean
            get() {
                val info = instance?.serviceInfo ?: return false
                return info.capabilities and android.accessibilityservice.AccessibilityServiceInfo.CAPABILITY_CAN_RETRIEVE_WINDOW_CONTENT != 0
            }

        fun clearCounts(context: Context): Pair<Int, Int> {
            val p = context.getSharedPreferences("annem", Context.MODE_PRIVATE)
            return p.getInt(KEY_CLEAR_OK, 0) to p.getInt(KEY_CLEAR_MISS, 0)
        }

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

        fun homeFallbackCount(context: Context): Int =
            context.getSharedPreferences("annem", Context.MODE_PRIVATE).getInt("home_fallback_count", 0)

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
