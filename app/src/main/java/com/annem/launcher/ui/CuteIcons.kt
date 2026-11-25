package com.annem.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

/*
 * Ana ekrandaki "Telefon" ve "WhatsApp" kutuları için sevimli, gülen yüzlü simgeler.
 * Hepsi Annem'in kendi çizimi (marka logosu değil); kutunun renk paletini kullanır.
 */

private val Blush = Color(0xFFF7A6B8)
private val Face = Color(0xFF2B2118)
private val HeartPink = Color(0xFFF2667F)

/** Gülümseyen eski tip masa telefonu: ahize üstte, gövdede yüz ve pembe yanaklar. */
@Composable
fun CutePhoneIcon(color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val s = size.minDimension
        val ox = (size.width - s) / 2f
        val oy = (size.height - s) / 2f
        fun p(x: Float, y: Float) = Offset(ox + x * s, oy + y * s)
        fun sz(w: Float, h: Float) = Size(w * s, h * s)
        val dark = lerp(color, Color.Black, 0.18f)
        val light = lerp(color, Color.White, 0.74f)

        // Ahize: ortası ince, iki ucu kalın (kulaklık ve ağızlık)
        drawRoundRect(dark, p(0.16f, 0.12f), sz(0.68f, 0.12f), CornerRadius(0.06f * s))
        drawRoundRect(dark, p(0.10f, 0.12f), sz(0.20f, 0.22f), CornerRadius(0.08f * s))
        drawRoundRect(dark, p(0.70f, 0.12f), sz(0.20f, 0.22f), CornerRadius(0.08f * s))
        // Ahize ile gövde arası bağlantı
        drawRoundRect(dark, p(0.42f, 0.26f), sz(0.16f, 0.14f), CornerRadius(0.04f * s))

        // Gövde: yumuşak köşeli, alta doğru genişleyen
        val body = Path().apply {
            moveTo(ox + 0.26f * s, oy + 0.38f * s)
            lineTo(ox + 0.74f * s, oy + 0.38f * s)
            quadraticTo(ox + 0.82f * s, oy + 0.38f * s, ox + 0.85f * s, oy + 0.48f * s)
            lineTo(ox + 0.92f * s, oy + 0.80f * s)
            quadraticTo(ox + 0.94f * s, oy + 0.90f * s, ox + 0.84f * s, oy + 0.90f * s)
            lineTo(ox + 0.16f * s, oy + 0.90f * s)
            quadraticTo(ox + 0.06f * s, oy + 0.90f * s, ox + 0.08f * s, oy + 0.80f * s)
            lineTo(ox + 0.15f * s, oy + 0.48f * s)
            quadraticTo(ox + 0.18f * s, oy + 0.38f * s, ox + 0.26f * s, oy + 0.38f * s)
            close()
        }
        drawPath(body, color)
        // Yüz için açık renk kadran
        drawCircle(light, radius = 0.20f * s, center = p(0.5f, 0.65f))

        cuteFace(p(0.5f, 0.65f), s * 0.20f, eye = Face)
    }
}

/** Gülümseyen konuşma balonu, köşesinde küçük bir kalp. */
@Composable
fun CuteChatIcon(color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val s = size.minDimension
        val ox = (size.width - s) / 2f
        val oy = (size.height - s) / 2f
        fun p(x: Float, y: Float) = Offset(ox + x * s, oy + y * s)
        fun sz(w: Float, h: Float) = Size(w * s, h * s)

        // Balon ve kuyruğu
        drawRoundRect(color, p(0.06f, 0.14f), sz(0.80f, 0.62f), CornerRadius(0.30f * s))
        val tail = Path().apply {
            moveTo(ox + 0.22f * s, oy + 0.66f * s)
            lineTo(ox + 0.14f * s, oy + 0.92f * s)
            lineTo(ox + 0.44f * s, oy + 0.72f * s)
            close()
        }
        drawPath(tail, color)

        // Yüz (beyaz gözler ve gülüş)
        cuteFace(p(0.46f, 0.45f), s * 0.24f, eye = Color.White)

        // Kalp: sağ üst köşede, beyaz çerçeveli
        heart(p(0.84f, 0.16f), 0.15f * s, Color.White)
        heart(p(0.84f, 0.165f), 0.11f * s, HeartPink)
    }
}

/** İki göz, gülen ağız ve pembe yanaklar. r: yüzün yarıçapı. */
private fun DrawScope.cuteFace(c: Offset, r: Float, eye: Color) {
    val eyeR = r * 0.16f
    drawCircle(eye, eyeR, Offset(c.x - r * 0.42f, c.y - r * 0.12f))
    drawCircle(eye, eyeR, Offset(c.x + r * 0.42f, c.y - r * 0.12f))
    drawCircle(Blush.copy(alpha = 0.85f), r * 0.17f, Offset(c.x - r * 0.72f, c.y + r * 0.28f))
    drawCircle(Blush.copy(alpha = 0.85f), r * 0.17f, Offset(c.x + r * 0.72f, c.y + r * 0.28f))
    drawArc(
        color = eye,
        startAngle = 20f,
        sweepAngle = 140f,
        useCenter = false,
        topLeft = Offset(c.x - r * 0.34f, c.y - r * 0.18f),
        size = Size(r * 0.68f, r * 0.62f),
        style = Stroke(width = r * 0.13f, cap = StrokeCap.Round),
    )
}

/** Kalp: iki daire ve bir üçgen. */
private fun DrawScope.heart(c: Offset, w: Float, color: Color) {
    val r = w * 0.28f
    drawCircle(color, r, Offset(c.x - r * 0.95f, c.y))
    drawCircle(color, r, Offset(c.x + r * 0.95f, c.y))
    val tri = Path().apply {
        moveTo(c.x - r * 1.9f, c.y + r * 0.25f)
        lineTo(c.x + r * 1.9f, c.y + r * 0.25f)
        lineTo(c.x, c.y + r * 2.3f)
        close()
    }
    drawPath(tri, color)
}

private fun lerp(a: Color, b: Color, t: Float) = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = 1f,
)
