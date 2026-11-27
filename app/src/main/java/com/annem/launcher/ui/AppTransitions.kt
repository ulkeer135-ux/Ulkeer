package com.annem.launcher.ui

import android.os.SystemClock
import android.view.View
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.hypot
import kotlinx.coroutines.withContext

/**
 * Kutuya dokunulduğu an kutunun ekrandaki yeri (1,5 sn içinde; sonra geçersiz).
 * Uygulamalar artık hava durumu çekmecesi gibi alttan açıldığı için Actions.start bunu
 * yalnızca temizler; ileride kutuya bağlı bir efekt istenirse hazır dursun.
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

/** Başka bir uygulamadan ana ekrana dönüşü izler; efekt HomeReturnCurtain'dedir. */
object HomeReveal {
    /** 0 = tamamen gizli, 1 = tamamen açık. */
    val progress = mutableFloatStateOf(1f)
    val token = mutableIntStateOf(0)
    @Volatile var launchedApp = false
    private var startedAt = 0L

    /** Annem'den bir uygulama açıldı (dönüşte efekt oynasın). */
    fun markAppLaunched() { launchedApp = true }

    /** Ekran çizilmeden önce çağrılır (onStart): ilk kare çekmeceyle örtülü başlar, sonra balon toplanır. */
    fun startIfReturning() {
        if (!launchedApp) return
        launchedApp = false
        startedAt = SystemClock.uptimeMillis()
        progress.floatValue = 0f
        token.intValue++
    }

    internal fun fresh(): Boolean = SystemClock.uptimeMillis() - startedAt < 1_500
}

/**
 * Hava durumu çekmecesinin hareketi, bütün uygulamalar için ortak:
 * açılış alttan yukarı kayarak (0,35 sn), kapanış alt ortaya doğru toplanan balon (1 sn).
 */
internal object SheetMotion {
    const val OPEN_MS = 350
    const val CLOSE_MS = 1000
    val OpenEasing: Easing = FastOutSlowInEasing
    val CloseEasing: Easing = CubicBezierEasing(0.45f, 0f, 0.55f, 1f)

    /** Telefonun "animasyon hızı" ayarı (pil tasarrufu vb.) efekti kısaltmasın. */
    val NoSystemScale: MotionDurationScale = object : MotionDurationScale {
        override val scaleFactor = 1f
    }
}

/**
 * Hava durumu çekmecesinin kapanışındaki balon toplanması: katman, alt ortaya doğru
 * küçülen bir daireyle kırpılır, hafifçe küçülür ve sonda solar. p: 0 = tam açık, 1 = kayboldu.
 */
internal fun GraphicsLayerScope.balloonCollapse(p: Float) {
    if (p <= 0f) return
    val cx = size.width / 2f
    val cy = size.height
    val r = hypot(cx, cy) * (1f - p)
    transformOrigin = TransformOrigin(0.5f, 1f)
    scaleX = 1f - 0.4f * p
    scaleY = 1f - 0.4f * p
    alpha = if (p < 0.7f) 1f else 1f - (p - 0.7f) / 0.3f
    clip = true
    shape = GenericShape { _, _ -> addOval(Rect(Offset(cx, cy), r)) }
}

/**
 * Annem'den açılan bir uygulamadan dönünce: uygulamanın yerinde krem bir çekmece görünür ve
 * hava durumu çekmecesi gibi alt ortaya doğru balonla toplanarak ana ekranı açar.
 * (Diğer uygulamanın kendi penceresini telefon kapatır; bu, onun ardından gelen kapanıştır.)
 * Dokunmaları engellemez: efekt sürerken de ana ekrandaki kutulara basılabilir.
 */
@Composable
fun HomeReturnCurtain() {
    val token = HomeReveal.token.intValue
    LaunchedEffect(token) {
        val p = HomeReveal.progress
        if (p.floatValue >= 1f) return@LaunchedEffect
        try {
            if (HomeReveal.fresh()) {
                withContext(SheetMotion.NoSystemScale) {
                    animate(
                        initialValue = p.floatValue,
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = SheetMotion.CLOSE_MS, easing = SheetMotion.CloseEasing),
                    ) { value, _ -> p.floatValue = value }
                }
            }
        } finally {
            // Ne olursa olsun ana ekran açık kalsın (yarıda kesilirse de).
            p.floatValue = 1f
        }
    }
    val visible by remember { derivedStateOf { HomeReveal.progress.floatValue < 1f } }
    if (visible) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { balloonCollapse(HomeReveal.progress.floatValue) }
                .background(Palette.Cream),
        )
    }
}
