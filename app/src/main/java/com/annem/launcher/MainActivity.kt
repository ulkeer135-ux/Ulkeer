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
import com.annem.launcher.ui.AnnemTheme
import com.annem.launcher.ui.AppPickerScreen
import com.annem.launcher.ui.ContactsScreen
import com.annem.launcher.ui.HomeScreen
import com.annem.launcher.ui.Palette
import com.annem.launcher.ui.SettingsScreen

private sealed interface Screen {
    data object Home : Screen
    data object Contacts : Screen
    data object Settings : Screen
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

        val store = SettingsStore(applicationContext)

        setContent {
            AnnemTheme {
                AnnemApp(store = store, homeSignal = homeSignal.intValue)
            }
        }
    }

    override fun onRestart() {
        super.onRestart()
        StartupStats.onActivityRestarted(this)
    }

    override fun onResume() {
        super.onResume()
        BadgeService.ensureConnected(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        homeSignal.intValue++
    }
}

@Composable
private fun AnnemApp(store: SettingsStore, homeSignal: Int) {
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }

    LaunchedEffect(homeSignal) { screen = Screen.Home }

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
            )
            Screen.Contacts -> ContactsScreen(store = store, onBack = { screen = Screen.Home })
            Screen.Settings -> SettingsScreen(
                store = store,
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
