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

/**
 * Ana ekrandaki, uygulaması kullanıcı tarafından seçilen üç kutu. Kimlikler eski
 * sürümlerle uyumlu kalsın diye değişmedi (camera/gallery eski atamaları korur).
 */
object Slots {
    const val MESSAGES = "messages" // 1. kutu
    const val CAMERA = "camera"     // 2. kutu
    const val GALLERY = "gallery"   // 3. kutu
    const val SLOT4 = "slot4"       // 4–6: yalnızca 9 kutulu düzende
    const val SLOT5 = "slot5"
    const val SLOT6 = "slot6"
    val ALL = listOf(MESSAGES, CAMERA, GALLERY, SLOT4, SLOT5, SLOT6)

    /** 6 kutulu düzende 3, 9 kutulu düzende 6 seçilebilir kutu var. */
    fun visible(nineTiles: Boolean): List<String> = if (nineTiles) ALL else ALL.take(3)

    /** "Daha fazla" çekmecesindeki 3 kutu (ana ekran düzeninden bağımsız). */
    const val MORE1 = "more1"
    const val MORE2 = "more2"
    const val MORE3 = "more3"
    val MORE = listOf(MORE1, MORE2, MORE3)

    fun title(id: String): String =
        if (id in MORE) "Daha fazla ${MORE.indexOf(id) + 1}. kutu" else "${ALL.indexOf(id) + 1}. kutu"
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
            .let { listOf(InstalledApp(InternalApps.DREAMS, InternalApps.DREAMS_LABEL)) + it }
    }

    // Ana ekran her açıldığında simgeleri yeniden çizmemek için küçük bir bellek önbelleği.
    private val iconCache = android.util.LruCache<String, ImageBitmap>(48)

    /** Önbellekte varsa beklemeden döner (ilk karede simge hazır olsun diye). */
    fun cachedIcon(packageName: String): ImageBitmap? = iconCache.get(packageName)

    suspend fun loadIcon(context: Context, packageName: String): ImageBitmap? {
        iconCache.get(packageName)?.let { return it }
        return withContext(Dispatchers.IO) {
            try {
                drawableToBitmap(context.packageManager.getApplicationIcon(packageName)).asImageBitmap()
                    .also { iconCache.put(packageName, it) }
            } catch (e: Exception) {
                null
            }
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
