package com.annem.launcher.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.roundToInt

data class BatteryState(val percent: Int, val charging: Boolean) {
    val low: Boolean get() = percent <= 20 && !charging
}

/**
 * Pil durumunu sistemin ACTION_BATTERY_CHANGED yayınından canlı izler; izin gerekmez.
 * Enerji: yalnızca ekran görünürken (ON_START…ON_STOP) dinler.
 */
@Composable
fun rememberBatteryState(): BatteryState? {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var state by remember { mutableStateOf<BatteryState?>(null) }

    DisposableEffect(context, lifecycleOwner) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                parseBattery(intent)?.let { state = it }
            }
        }
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        var registered = false

        fun register() {
            if (registered) return
            val sticky = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter)
            }
            registered = true
            // Yayın "yapışkan" olduğu için kayıt anında son durum hemen gelir.
            sticky?.let { intent -> parseBattery(intent)?.let { state = it } }
        }

        fun unregister() {
            if (!registered) return
            context.unregisterReceiver(receiver)
            registered = false
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> register()
                Lifecycle.Event.ON_STOP -> unregister()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            unregister()
        }
    }
    return state
}

private fun parseBattery(intent: Intent): BatteryState? {
    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
    if (level < 0 || scale <= 0) return null
    val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
    val percent = (level * 100f / scale).roundToInt().coerceIn(0, 100)
    return BatteryState(percent, plugged)
}

/** Saatin yanındaki büyük şarj göstergesi: pil çizimi + "%85". */
@Composable
fun BatteryIndicator(state: BatteryState, signal: SignalState? = null) {
    val color = when {
        state.low -> Palette.Red
        state.charging -> Palette.Green
        else -> Palette.Ink
    }
    val description = "Şarj yüzde ${state.percent}" + (if (state.charging) ", şarj oluyor" else "") +
        (signal?.let { ", şebeke ${signalLabel(it)}" } ?: "")

    // Beyaz "hap" içinde: pil çizimi (şarjdayken şimşek) ve yüzde.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .semantics(mergeDescendants = true) { contentDescription = description }
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        // Şebeke çekişi, şarjın hemen solunda.
        signal?.let {
            SignalMark(it)
            Spacer(Modifier.width(10.dp))
        }
        // Pil çizimi yok: yalnızca yüzde (şarjdayken önünde şimşek).
        if (state.charging) {
            Icon(Icons.Rounded.Bolt, contentDescription = null, tint = color, modifier = Modifier.size(26.dp))
        }
        Text("%${state.percent}", fontSize = 24.sp, fontWeight = FontWeight.Black, color = color)
    }
}
