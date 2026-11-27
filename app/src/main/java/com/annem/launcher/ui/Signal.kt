package com.annem.launcher.ui

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.telephony.PhoneStateListener
import android.telephony.SignalStrength
import android.telephony.TelephonyManager
import androidx.compose.foundation.background
import androidx.compose.material.icons.rounded.AirplanemodeActive
import androidx.compose.material.icons.rounded.SimCardAlert
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay

/** Şebeke durumu: çubuk sayısı 0–4; ya da hiç şebeke yok / SIM yok / uçak modu. */
sealed interface SignalState {
    data class Bars(val level: Int) : SignalState
    data object NoSim : SignalState
    data object Airplane : SignalState
}

/** Şebekede sorun var mı (arama yapılamayabilir)? */
val SignalState.noService: Boolean get() = this is SignalState.Bars && level == 0

private fun readSignal(context: Context, tm: TelephonyManager, fallbackLevel: Int?): SignalState {
    val airplane = Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0
    if (airplane) return SignalState.Airplane
    if (tm.simState == TelephonyManager.SIM_STATE_ABSENT) return SignalState.NoSim
    val level = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        try { tm.signalStrength?.level } catch (e: Exception) { null } ?: 0
    } else {
        fallbackLevel ?: 0
    }
    return SignalState.Bars(level.coerceIn(0, 4))
}

/**
 * Telefonun şebeke çekişini izler; izin gerekmez.
 * Enerji: yalnızca ekran görünürken, 5 saniyede bir sistemin hazır tuttuğu değeri okur.
 * Telefon özelliği olmayan cihazlarda (tablet) null döner.
 */
@Composable
fun rememberSignalState(): SignalState? {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val tm = remember {
        if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            context.getSystemService(TelephonyManager::class.java)
        } else null
    } ?: return null
    var state by remember { mutableStateOf<SignalState?>(null) }
    var legacyLevel by remember { mutableStateOf<Int?>(null) }

    // Android 8–8.1: değer yalnızca dinleyiciyle gelir.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
        DisposableEffect(tm) {
            @Suppress("DEPRECATION")
            val listener = object : PhoneStateListener() {
                @Deprecated("Deprecated in Java")
                override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
                    legacyLevel = signalStrength.level
                }
            }
            @Suppress("DEPRECATION")
            try { tm.listen(listener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS) } catch (e: Exception) { }
            onDispose {
                @Suppress("DEPRECATION")
                try { tm.listen(listener, PhoneStateListener.LISTEN_NONE) } catch (e: Exception) { }
            }
        }
    }

    LaunchedEffect(tm, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                state = readSignal(context, tm, legacyLevel)
                delay(5_000)
            }
        }
    }
    return state
}

/** Şarj kutucuğunun içindeki şebeke işareti: 4 çubuk; uçak modu / SIM yok için simge. */
@Composable
fun SignalMark(state: SignalState) {
    when (state) {
        SignalState.Airplane -> androidx.compose.material3.Icon(
            androidx.compose.material.icons.Icons.Rounded.AirplanemodeActive, contentDescription = null,
            tint = Palette.Red, modifier = Modifier.height(24.dp).width(24.dp),
        )
        SignalState.NoSim -> androidx.compose.material3.Icon(
            androidx.compose.material.icons.Icons.Rounded.SimCardAlert, contentDescription = null,
            tint = Palette.Red, modifier = Modifier.height(24.dp).width(24.dp),
        )
        is SignalState.Bars -> SignalBars(state.level, signalColor(state.level))
    }
}

fun signalColor(level: Int): Color = when (level) {
    0, 1 -> Palette.Red
    2 -> Color(0xFFC2560C)
    else -> Palette.Green
}

fun signalLabel(state: SignalState): String = when (state) {
    SignalState.Airplane -> "uçak modu"
    SignalState.NoSim -> "SIM yok"
    is SignalState.Bars -> when (state.level) { 0 -> "şebeke yok"; 1 -> "zayıf"; 2 -> "orta"; else -> "iyi" }
}

@Composable
private fun SignalBars(level: Int, color: Color) {
    val empty = Palette.Ink.copy(alpha = 0.15f)
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier.height(22.dp),
    ) {
        listOf(7, 12, 17, 22).forEachIndexed { i, h ->
            Box(
                Modifier
                    .width(6.dp)
                    .height(h.dp)
                    .background(if (i < level) color else empty, RoundedCornerShape(2.dp)),
            )
        }
    }
}
