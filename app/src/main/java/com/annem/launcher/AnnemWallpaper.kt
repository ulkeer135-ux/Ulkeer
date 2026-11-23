package com.annem.launcher

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import android.view.WindowManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Telefonun ana ekran duvar kağıdını Annem'in zeminiyle aynı yapar (krem + iki yumuşak yuvarlak).
 * Başka bir uygulamadan ana ekrana dönerken telefonun kendi geçiş animasyonu bir an duvar kağıdını
 * gösteriyor; duvar kağıdı Annem'le aynı olunca bu an fark edilmez. Kilit ekranına dokunulmaz.
 */
object AnnemWallpaper {

    suspend fun apply(context: Context): Boolean = withContext(Dispatchers.Default) {
        try {
            val (w, h) = screenSize(context)
            val d = context.resources.displayMetrics.density
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            c.drawColor(0xFFFFF6EA.toInt())
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            val top = statusBarHeight(context).toFloat()
            // HomeScreen'deki yuvarlaklarla aynı ölçü ve yer.
            p.color = 0xFFFFE3C9.toInt()
            c.drawCircle(w + 90 * d - 150 * d, top - 120 * d + 150 * d, 150 * d, p)
            p.color = 0xFFFDE6EE.toInt()
            c.drawCircle(-130 * d + 110 * d, top + 130 * d + 110 * d, 110 * d, p)
            val wm = WallpaperManager.getInstance(context)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                wm.setBitmap(bmp, null, true, WallpaperManager.FLAG_SYSTEM)
            } else {
                wm.setBitmap(bmp)
            }
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun screenSize(context: Context): Pair<Int, Int> {
        val wm = context.getSystemService(WindowManager::class.java)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val b = wm.maximumWindowMetrics.bounds
            minOf(b.width(), b.height()) to maxOf(b.width(), b.height())
        } else {
            val m = android.util.DisplayMetrics()
            @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(m)
            minOf(m.widthPixels, m.heightPixels) to maxOf(m.widthPixels, m.heightPixels)
        }
    }

    private fun statusBarHeight(context: Context): Int {
        val id = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) context.resources.getDimensionPixelSize(id) else 0
    }
}
