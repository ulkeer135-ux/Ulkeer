package com.annem.launcher.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

/**
 * Tek satırlık yazı: [maxSp] boyutunda sığıyorsa öyle kalır, sığmıyorsa gerçek genişliği
 * ölçülerek tam sığacak kadar küçülür (en az [minSp]). Böylece "WhatsApp" gibi yazılar
 * hiçbir telefonda kesilmez.
 */
@Composable
fun FitText(
    text: String,
    maxSp: Float,
    minSp: Float,
    fontWeight: FontWeight,
    color: Color,
    modifier: Modifier = Modifier,
    textAlign: TextAlign? = null,
    contentAlignment: Alignment = Alignment.CenterStart,
) {
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier = modifier, contentAlignment = contentAlignment) {
        val available = constraints.maxWidth
        val size = remember(text, available, maxSp, minSp, fontWeight) {
            val style = TextStyle(fontSize = maxSp.sp, fontWeight = fontWeight, fontFamily = Nunito)
            val needed = measurer.measure(text, style = style, softWrap = false, maxLines = 1).size.width
            if (needed <= available || needed == 0) maxSp else (maxSp * available / needed * 0.96f).coerceAtLeast(minSp)
        }
        Text(
            text,
            fontSize = size.sp,
            fontWeight = fontWeight,
            color = color,
            maxLines = 1,
            softWrap = false,
            lineHeight = (size * 1.2f).sp,
            textAlign = textAlign,
        )
    }
}
