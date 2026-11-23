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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.annem.launcher.ui.AnnemTheme
import com.annem.launcher.ui.ContactsScreen
import com.annem.launcher.ui.HomeScreen
import com.annem.launcher.ui.Palette
import com.annem.launcher.ui.SettingsScreen

private sealed interface Screen {
    data object Home : Screen
    data object Contacts : Screen
    data object Settings : Screen
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

        val store = SettingsStore(applicationContext)

        setContent {
            AnnemTheme {
                AnnemApp(store = store, homeSignal = homeSignal.intValue)
            }
        }
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
    BackHandler { screen = Screen.Home }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Palette.Cream)
            .safeDrawingPadding(),
    ) {
        when (screen) {
            Screen.Home -> HomeScreen(
                store = store,
                onOpenContacts = { screen = Screen.Contacts },
                onOpenSettings = { screen = Screen.Settings },
            )
            Screen.Contacts -> ContactsScreen(onBack = { screen = Screen.Home })
            Screen.Settings -> SettingsScreen(store = store, onBack = { screen = Screen.Home })
        }
    }
}
