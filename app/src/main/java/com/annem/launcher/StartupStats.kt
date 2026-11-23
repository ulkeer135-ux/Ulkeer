package com.annem.launcher

import android.content.Context
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.view.Choreographer
import androidx.compose.runtime.mutableStateListOf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Ana ekranın ne kadar sürede göründüğünü ölçer (Ayarlar › Açılış ölçümleri):
 *  - "Sıfırdan açılış": Android Annem'i arka planda kapatmış, süreç yeniden başladı.
 *  - "Geri dönüş": Annem zaten bellekteydi, yalnızca ekrana geri geldi.
 * Ölçüm, işlem (ya da ekrana dönüş) başlangıcından ilk çizilen kareye kadardır.
 * Son 8 kayıt saklanır; başka hiçbir veri tutulmaz.
 */
object StartupStats {

    private const val PREFS = "annem_perf"
    private const val KEY = "events"
    private const val MAX = 8

    val events = mutableStateListOf<String>()
    private var loaded = false
    private var firstActivityInProcess = true

    private fun load(context: Context) {
        if (loaded) return
        loaded = true
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return
        events.addAll(raw.split('\n').filter { it.isNotBlank() })
    }

    /** onCreate'te çağrılır: süreç yeni başladıysa sıfırdan açılış olarak ölçülür. */
    fun onActivityCreated(context: Context) {
        load(context)
        if (!firstActivityInProcess) return
        firstActivityInProcess = false
        val start = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) Process.getStartUptimeMillis() else SystemClock.uptimeMillis()
        measureUntilDrawn(context, "Sıfırdan açılış", start)
    }

    /** onRestart'ta çağrılır (başka bir uygulamadan ana ekrana dönüş). */
    fun onActivityRestarted(context: Context) {
        load(context)
        measureUntilDrawn(context, "Geri dönüş", SystemClock.uptimeMillis())
    }

    /** İki kare sonra ölç: ilk karede içerik çizilir, ikincisi ekrana çıktığını garantiler. */
    private fun measureUntilDrawn(context: Context, kind: String, startUptime: Long) {
        val app = context.applicationContext
        Choreographer.getInstance().postFrameCallback {
            Choreographer.getInstance().postFrameCallback {
                val ms = SystemClock.uptimeMillis() - startUptime
                record(app, kind, ms)
            }
        }
    }

    private fun record(context: Context, kind: String, ms: Long) {
        val time = SimpleDateFormat("HH:mm", Locale.forLanguageTag("tr-TR")).format(Date())
        events.add(0, "$time  $kind: $ms ms")
        while (events.size > MAX) events.removeAt(events.lastIndex)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, events.joinToString("\n"))
            .apply()
    }
}
