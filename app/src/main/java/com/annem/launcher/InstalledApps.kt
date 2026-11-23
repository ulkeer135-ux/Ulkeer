package com.annem.launcher

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Locale

/** Ana ekrandaki ayarlanabilir kutular. */
object Slots {
    const val CAMERA = "camera"
    const val GALLERY = "gallery"
    val ALL = listOf(CAMERA, GALLERY)
}

/** Bir kutuya atanmış uygulama ve kutuda görünecek yazı. */
data class TileSlot(val packageName: String, val label: String)

data class InstalledApp(val packageName: String, val label: String)

/** Telefonda yüklü, açılabilir uygulamalar (uygulama çekmecesindekiler). */
object InstalledApps {

    @Suppress("DEPRECATION")
    suspend fun load(context: Context): List<InstalledApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val collator = Collator.getInstance(Locale.forLanguageTag("tr-TR"))
        pm.queryIntentActivities(intent, 0)
            .map { InstalledApp(it.activityInfo.packageName, it.loadLabel(pm).toString().trim()) }
            .filter { it.packageName != context.packageName && it.label.isNotEmpty() }
            .distinctBy { it.packageName }
            .sortedWith(compareBy<InstalledApp, String>(collator) { it.label })
    }

    suspend fun loadIcon(context: Context, packageName: String): ImageBitmap? = withContext(Dispatchers.IO) {
        try {
            drawableToBitmap(context.packageManager.getApplicationIcon(packageName)).asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) return drawable.bitmap
        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 144
        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 144
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, width, height)
        drawable.draw(canvas)
        return bitmap
    }
}
