package com.annem.launcher.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

object Palette {
    val Cream = Color(0xFFFBF7F0)
    val Ink = Color(0xFF1C1B19)
    val InkSoft = Color(0xFF3A3833)
    val CardBorder = Color(0xFFE2DACB)

    val Blue = Color(0xFF1F4E9C)
    val Green = Color(0xFF1E6B3A)
    val Red = Color(0xFFB4361E)

    // Kutular: açık zemin, koyu simge. Her kutu renk tonunun yanında açıklık olarak da ayrışır.
    val ContactsBg = Color(0xFFDCE8FA); val ContactsLine = Color(0xFF9DB6DB); val ContactsIcon = Color(0xFF1F4E9C)
    val PhoneBg = Color(0xFFDDF1E3); val PhoneLine = Color(0xFF9CCBAA); val PhoneIcon = Color(0xFF1E6B3A)
    val WaBg = Color(0xFFD6EFE6); val WaLine = Color(0xFF94C9B5); val WaIcon = Color(0xFF17624A)
    val SmsBg = Color(0xFFFFF0C7); val SmsLine = Color(0xFFE3C66E); val SmsIcon = Color(0xFF7A5300)
    val CamBg = Color(0xFFF2E2F5); val CamLine = Color(0xFFCDA6D4); val CamIcon = Color(0xFF6B2F78)
    val PicBg = Color(0xFFFCE4D8); val PicLine = Color(0xFFE3A989); val PicIcon = Color(0xFF8A3A12)
}

/**
 * Sistem yazı boyutu ayarı ne olursa olsun yerleşim bozulmasın diye fontScale 1'e
 * sabitlenir: ekranın kendisi zaten çok büyük yazı kullanıyor.
 */
@Composable
fun AnnemTheme(content: @Composable () -> Unit) {
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1f)) {
        MaterialTheme(
            colorScheme = lightColorScheme(
                primary = Palette.Blue,
                onPrimary = Color.White,
                secondary = Palette.Green,
                error = Palette.Red,
                background = Palette.Cream,
                surface = Palette.Cream,
                onBackground = Palette.Ink,
                onSurface = Palette.Ink,
            ),
            content = content,
        )
    }
}
