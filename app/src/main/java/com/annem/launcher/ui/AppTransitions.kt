package com.annem.launcher.ui

import android.os.SystemClock
import android.view.View
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.withContext

/**
 * Kutuya dokunulduğu an kutunun ekrandaki yeri. Açılan uygulama bu kutudan balon gibi
 * büyüyerek açılsın diye Actions.start bunu kullanır (1,5 sn içinde; sonra geçersiz).
 */
object LaunchOrigin {
    private var view: View? = null
    private var rect: Rect? = null
    private var at = 0L

    fun set(view: View, rect: Rect) {
        this.view = view
        this.rect = rect
        at = SystemClock.uptimeMillis()
    }

    /** Taze ise (view, rect) döner ve temizler. */
    fun take(): Pair<View, Rect>? {
        val v = view
        val r = rect
        view = null
        rect = null
        return if (v != null && r != null && SystemClock.uptimeMillis() - at < 1_500) v to r else null
    }
}

/**
 * Başka bir uygulamadan ana ekrana dönünce: Annem ekranı ortadan büyüyen bir balonla açılır.
 * (Diğer uygulamanın kendi kapanışını telefon yönetir; bu, Annem'in geliş efektidir.)
 */
object HomeReveal {
    /** 0 = tamamen gizli, 1 = tamamen açık. */
    val progress = mutableFloatStateOf(1f)
    val token = mutableIntStateOf(0)
    @Volatile var launchedApp = false
    private var startedAt = 0L

    /** Annem'den bir uygulama açıldı (dönüşte efekt oynasın). */
    fun markAppLaunched() { launchedApp = true }

    /** Ekran çizilmeden önce çağrılır (onStart): ilk kare gizli başlar, sonra balon açılır. */
    fun startIfReturning() {
        if (!launchedApp) return
        launchedApp = false
        startedAt = SystemClock.uptimeMillis()
        progress.floatValue = 0f
        token.intValue++
    }

    internal fun fresh(): Boolean = SystemClock.uptimeMillis() - startedAt < 1_500
}

private val NoSystemScale = object : androidx.compose.ui.MotionDurationScale {
    override val scaleFactor = 1f
}

/** Ana ekran içeriğine uygulanır: dönüşte ortadan büyüyen balon (0,7 sn). */
fun Modifier.homeReveal(): Modifier = composed {
    val token = HomeReveal.token.intValue
    LaunchedEffect(token) {
        val p = HomeReveal.progress
        if (p.floatValue >= 1f) return@LaunchedEffect
        try {
            if (HomeReveal.fresh()) {
                // Telefonun animasyon hızı ayarı efekti kısaltmasın.
                withContext(NoSystemScale) {
                    animate(
                        initialValue = p.floatValue,
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = 700, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)),
                    ) { value, _ -> p.floatValue = value }
                }
            }
        } finally {
            // Ne olursa olsun ekran görünür kalsın (yarıda kesilirse de).
            p.floatValue = 1f
        }
    }
    graphicsLayer {
        val p = HomeReveal.progress.floatValue
        if (p < 1f) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val r = kotlin.math.hypot(cx, cy) * p
            transformOrigin = TransformOrigin(0.5f, 0.5f)
            scaleX = 0.9f + 0.1f * p
            scaleY = 0.9f + 0.1f * p
            alpha = (p * 3f).coerceAtMost(1f)
            clip = true
            shape = GenericShape { _, _ -> addOval(Rect(Offset(cx, cy), r)) }
        }
    }
}
