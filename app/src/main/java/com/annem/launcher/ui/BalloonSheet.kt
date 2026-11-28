package com.annem.launcher.ui

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Alttan açılan cam çekmece; "Kapat", aşağı itme, boşluğa dokunma ve geri tuşunda
 * WhatsApp'taki gibi balon toplanması efektiyle (1 sn) kapanır.
 * İçerik, kapatma işlevini (balon efektiyle) parametre olarak alır.
 * "Daha fazla" ve hava durumu çekmecesi bunu ortak kullanır.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun BalloonSheet(
    onDismiss: () -> Unit,
    containerColor: Color = Palette.Cream.copy(alpha = 0.72f),
    content: @Composable ColumnScope.(close: () -> Unit) -> Unit,
) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // Açıkken boşta kapanma sayacı beklesin (dokunuşlar bu ayrı pencerede kalır).
    androidx.compose.runtime.DisposableEffect(Unit) {
        com.annem.launcher.MainActivity.sheetsOpen++
        onDispose { com.annem.launcher.MainActivity.sheetsOpen-- }
    }
    // Elle aşağı itince de "Kapat"taki balon efekti: çekmecenin kendi kayarak kapanması engellenir,
    // yerine balon toplanması başlar.
    val closeRef = remember { arrayOf<() -> Unit>({}) }
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { value ->
            if (value == androidx.compose.material3.SheetValue.Hidden) { closeRef[0](); false } else true
        },
    )
    // "Kapat": WhatsApp'taki ek menüsü gibi balon toplanması — çekmece, alttaki "Daha fazla"
    // sekmesine doğru küçülen bir daireyle kapanır, hafifçe küçülüp solar; sonra kaldırılır.
    val slideOut = remember { androidx.compose.animation.core.Animatable(0f) }
    var closing by remember { mutableStateOf(false) }
    val closeAnimated: () -> Unit = {
        if (!closing) {
            closing = true
            // Telefonun "animasyon hızı" ayarı (pil tasarrufu vb.) efekti kısaltmasın: süre hep gerçek 1 sn.
            scope.launch(object : androidx.compose.ui.MotionDurationScale { override val scaleFactor = 1f }) {
                slideOut.animateTo(
                    1f,
                    androidx.compose.animation.core.tween(
                        durationMillis = 1000,
                        easing = androidx.compose.animation.core.CubicBezierEasing(0.45f, 0f, 0.55f, 1f),
                    ),
                )
                onDismiss()
            }
        }
    }
    closeRef[0] = closeAnimated
    androidx.compose.material3.ModalBottomSheet(
        // Boşluğa dokunma / geri tuşu: çekmece hâlâ görünüyorsa yine balon efektiyle kapanır.
        onDismissRequest = { if (!closing) { if (sheetState.isVisible) closeAnimated() else onDismiss() } },
        sheetState = sheetState,
        modifier = Modifier.graphicsLayer {
            val p = slideOut.value
            if (p > 0f) {
                // Balonun merkezi: çekmecenin alt ortası ("Daha fazla" sekmesinin olduğu yer).
                val cx = size.width / 2f
                val cy = size.height
                val maxR = kotlin.math.hypot(cx, cy)
                val r = maxR * (1f - p)
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                scaleX = 1f - 0.4f * p
                scaleY = 1f - 0.4f * p
                // Son kısma kadar net görünsün, sonda solsun.
                alpha = if (p < 0.7f) 1f else 1f - (p - 0.7f) / 0.3f
                clip = true
                shape = androidx.compose.foundation.shape.GenericShape { _, _ ->
                    addOval(androidx.compose.ui.geometry.Rect(androidx.compose.ui.geometry.Offset(cx, cy), r))
                }
            }
        },
        containerColor = containerColor,
        scrimColor = Color.Black.copy(alpha = 0.12f),
        tonalElevation = 0.dp,
    ) {
        content(closeAnimated)
    }
}
