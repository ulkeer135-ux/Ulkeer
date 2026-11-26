package com.annem.launcher

import android.app.Activity
import android.content.Context
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager

/** Ekranın kendiliğinden kapanmasıyla ilgili denetim ve teşhis. */
object ScreenAwake {

    /** Annem'in penceresindeki tüm "ekranı açık tut" isteklerini kaldırır (Rüya ekranı dışında). */
    fun release(activity: Activity) {
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        clear(activity.window.decorView)
    }

    private fun clear(v: View) {
        if (v.keepScreenOn) v.keepScreenOn = false
        if (v is ViewGroup) for (i in 0 until v.childCount) clear(v.getChildAt(i))
    }

    /** Annem şu an ekranı açık tutuyor mu? */
    fun annemKeepsOn(activity: Activity): Boolean {
        val flag = activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0
        return flag || any(activity.window.decorView)
    }

    private fun any(v: View): Boolean =
        v.keepScreenOn || (v is ViewGroup && (0 until v.childCount).any { any(v.getChildAt(it)) })

    /** Telefonun ekran kapanma süresi (ms); okunamazsa -1. */
    fun timeoutMs(context: Context): Int =
        runCatching { Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT) }.getOrDefault(-1)

    /** Geliştirici seçenekleri › "Uyanık kal" (şarjdayken ekran kapanmaz) açık mı? */
    fun stayOnWhilePlugged(context: Context): Boolean =
        runCatching { Settings.Global.getInt(context.contentResolver, Settings.Global.STAY_ON_WHILE_PLUGGED_IN) != 0 }
            .getOrDefault(false)

    fun describeTimeout(ms: Int): String = when {
        ms < 0 -> "okunamadı"
        ms >= 24 * 3600_000 || ms == Int.MAX_VALUE -> "Asla (ekran kapanmaz)"
        ms >= 60_000 -> "${ms / 60_000} dakika"
        else -> "${ms / 1000} saniye"
    }
}
