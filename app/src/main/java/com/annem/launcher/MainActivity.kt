package com.annem.launcher

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.annem.launcher.ui.AnnemTheme
import com.annem.launcher.ui.AppPickerScreen
import com.annem.launcher.ui.ContactsScreen
import com.annem.launcher.ui.DreamScreen
import com.annem.launcher.ui.HomeScreen
import com.annem.launcher.ui.Palette
import com.annem.launcher.ui.SettingsScreen

private sealed interface Screen {
    data object Home : Screen
    data object Contacts : Screen
    data object Settings : Screen
    data object Dreams : Screen
    data class PickApp(val slotId: String, val fromHome: Boolean) : Screen
}

class MainActivity : ComponentActivity() {

    /** Ana Sayfa tuşuna her basıldığında artar; uygulama ana ekrana döner. */
    private val homeSignal = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Açık zemin: durum çubuğu simgeleri her zaman koyu olsun.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        StartupStats.onActivityCreated(this)

        hideStatusBar()
        val store = SettingsStore(applicationContext)
        val dreams = DreamStore(applicationContext)

        setContent {
            AnnemTheme {
                AnnemApp(store = store, dreams = dreams, homeSignal = homeSignal.intValue)
            }
        }
    }

    /**
     * Telefonun üstteki küçük çubuğu (saat, pil, sinyal) gizlenir: Annem zaten büyük saati ve
     * pili gösteriyor. Yukarıdan aşağı kaydırınca çubuk bir süre görünür, bildirimler yine açılır.
     */
    private fun hideStatusBar() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.statusBars())
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        focused = hasFocus
        if (hasFocus) hideStatusBar()
    }

    override fun onRestart() {
        super.onRestart()
        StartupStats.onActivityRestarted(this)
    }

    // --- Yedek: ekran kendiliğinden kararmazsa Annem kapatır ---
    // Telefonun süresi (+3 sn) boyunca dokunulmazsa ve ekran hâlâ açıksa, Ekranı Kapat'ın
    // yolu (erişilebilirlik / cihaz yöneticisi) ile ekran kapatılır. Rüya Tabirleri hariç.
    private var lastInteraction = android.os.SystemClock.uptimeMillis()
    private val idleHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val idleCheck = object : Runnable {
        override fun run() {
            val timeout = ScreenAwake.timeoutMs(this@MainActivity)
            val usable = timeout in 5_000 until 24 * 3600_000 && !ScreenAwake.stayOnWhilePlugged(this@MainActivity)
            val pm = getSystemService(android.os.PowerManager::class.java)
            val idle = android.os.SystemClock.uptimeMillis() - lastInteraction
            if (usable && !dreamsVisible && pm?.isInteractive == true && idle > timeout + 3_000) {
                lastInteraction = android.os.SystemClock.uptimeMillis()
                ScreenLock.lock(this@MainActivity)
            }
            idleHandler.postDelayed(this, 2_000)
        }
    }

    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        lastInteraction = android.os.SystemClock.uptimeMillis()
        return super.dispatchTouchEvent(ev)
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        lastInteraction = android.os.SystemClock.uptimeMillis()
        return super.dispatchKeyEvent(event)
    }

    override fun onResume() {
        super.onResume()
        lastInteraction = android.os.SystemClock.uptimeMillis()
        idleHandler.removeCallbacks(idleCheck)
        idleHandler.postDelayed(idleCheck, 2_000)
        if (!dreamsVisible) ScreenAwake.release(this)
        visible = this
        BadgeService.ensureConnected(this)
    }

    override fun onPause() {
        idleHandler.removeCallbacks(idleCheck)
        if (visible === this) visible = null
        focused = false
        super.onPause()
    }

    override fun onDestroy() {
        if (visible === this) visible = null
        super.onDestroy()
    }

    companion object {
        /** Rüya Tabirleri ekranı açık mı (orada ekran bilerek açık tutulur). */
        @Volatile
        var dreamsVisible = false

        @Volatile
        private var visible: MainActivity? = null

        @Volatile
        private var focused = false

        /**
         * Annem gerçekten önde mi: hem açık (resumed) hem de dokunmalar ona gidiyor (odak).
         * ColorOS'un küçük pencere / bölünmüş ekran modunda Annem açık kalıp başka uygulama
         * odakta olabilir; o durumda "önde" sayılmaz.
         */
        val isInFront: Boolean get() = visible != null && focused

        /** Annem öndeyse ana sayfaya döndürür ve true verir (hızlı ana ekran tuşu için). */
        fun showHomeIfVisible(): Boolean {
            if (!isInFront) return false
            val activity = visible ?: return false
            activity.runOnUiThread { activity.homeSignal.intValue++ }
            return true
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        homeSignal.intValue++
    }
}

@Composable
private fun AnnemApp(store: SettingsStore, dreams: DreamStore, homeSignal: Int) {
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }

    LaunchedEffect(homeSignal) { screen = Screen.Home }
    // Rüya Tabirleri dışındaki her ekranda: ekranı açık tutma isteği kalmasın, telefon kendi
    // süresinde kararsın.
    val activityForAwake = androidx.compose.ui.platform.LocalContext.current as? android.app.Activity
    LaunchedEffect(screen) {
        if (screen != Screen.Dreams) activityForAwake?.let { ScreenAwake.release(it) }
    }

    // Ana ekranda geri tuşu hiçbir şey yapmaz; diğer ekranlarda bir adım geri gider.
    BackHandler {
        val current = screen
        screen = if (current is Screen.PickApp && !current.fromHome) Screen.Settings else Screen.Home
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Palette.Cream)
            // Yalnızca sistem çubukları ve çentik: klavye (IME) burada hesaba katılmaz.
            // Klavye açık bir uygulamadan (Trendyol, Claude…) dönerken klavyenin kapanma
            // animasyonu ana ekranı her karede yeniden yerleştiriyordu; takılma buydu.
            // Yazı yazılan ekranlar (Ayarlar, uygulama seçici) kendi imePadding'ini uygular.
            .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout)),
    ) {
        when (val current = screen) {
            Screen.Home -> HomeScreen(
                store = store,
                onOpenContacts = { screen = Screen.Contacts },
                onOpenSettings = { screen = Screen.Settings },
                onPickApp = { slotId -> screen = Screen.PickApp(slotId, fromHome = true) },
                onOpenDreams = { screen = Screen.Dreams },
            )
            Screen.Dreams -> {
                androidx.compose.runtime.DisposableEffect(Unit) {
                    MainActivity.dreamsVisible = true
                    onDispose { MainActivity.dreamsVisible = false }
                }
                DreamScreen(store = dreams, onBack = { screen = Screen.Home })
            }
            Screen.Contacts -> ContactsScreen(store = store, onBack = { screen = Screen.Home })
            Screen.Settings -> SettingsScreen(
                store = store,
                dreams = dreams,
                onBack = { screen = Screen.Home },
                onPickApp = { slotId -> screen = Screen.PickApp(slotId, fromHome = false) },
            )
            is Screen.PickApp -> AppPickerScreen(
                title = "${Slots.title(current.slotId)} için uygulama seç",
                backLabel = if (current.fromHome) "Ana Ekran" else "Ayarlar",
                onPick = { app ->
                    store.setSlot(current.slotId, app.packageName, app.label)
                    screen = if (current.fromHome) Screen.Home else Screen.Settings
                },
                onBack = { screen = if (current.fromHome) Screen.Home else Screen.Settings },
            )
        }
    }
}
