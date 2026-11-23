package com.annem.launcher.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import com.annem.launcher.R

/** "Sıcak ve yumuşak" görünüm: krem zemin, beyaz kutular, renkli simge balonları. */
object Palette {
    val Cream = Color(0xFFFFF6EA)
    val Ink = Color(0xFF2B2118)
    val InkSoft = Color(0xFF6B5A4C)
    val CardBorder = Color(0xFFEADBC9)

    val Blue = Color(0xFF2F5BD3)
    val Green = Color(0xFF1E7A44)
    val Red = Color(0xFFB4361E)

    // Arka plandaki yumuşak yuvarlaklar ve selam.
    val BlobPeach = Color(0xFFFFE3C9)
    val BlobPink = Color(0xFFFDE6EE)
    val Greeting = Color(0xFF8A4B2A)
    val Heart = Color(0xFFE4572E)
    val Moon = Color(0xFFFFE9A8)

    // Kutular: beyaz kart; Bg = simge balonu, Line = alttaki renkli "gölge", Icon = simge.
    val ContactsBg = Color(0xFFDCE8FF); val ContactsLine = Color(0xFFC9D8FF); val ContactsIcon = Color(0xFF2F5BD3)
    val PhoneBg = Color(0xFFDDF5E4); val PhoneLine = Color(0xFFBFE8CC); val PhoneIcon = Color(0xFF1E7A44)
    val WaBg = Color(0xFFD5F2E6); val WaLine = Color(0xFFB5E3CF); val WaIcon = Color(0xFF14735A)
    val SmsBg = Color(0xFFFFF0C7); val SmsLine = Color(0xFFF3DC9A); val SmsIcon = Color(0xFF8A5A00)
    val CamBg = Color(0xFFEFE2FB); val CamLine = Color(0xFFE2CCF5); val CamIcon = Color(0xFF6E3AA6)
    val PicBg = Color(0xFFFFE3D6); val PicLine = Color(0xFFF7CDB8); val PicIcon = Color(0xFFB4481C)
    val Slot4Bg = Color(0xFFFDE2E0); val Slot4Line = Color(0xFFF5C6C2); val Slot4Icon = Color(0xFFB3261E)
    val Slot5Bg = Color(0xFFFCE1EE); val Slot5Line = Color(0xFFF3C3DA); val Slot5Icon = Color(0xFFA3266A)
    val Slot6Bg = Color(0xFFDDF0F5); val Slot6Line = Color(0xFFB9DDE8); val Slot6Icon = Color(0xFF1F6E86)
    val EmptyLine = Color(0xFFD9C3AC); val EmptyBg = Color(0xFFFBEBDA); val EmptyIcon = Color(0xFF8A6E55)

    // Hava durumu kartı: zemin havaya göre (yazı beyaz), alt gölge bir ton koyusu.
    val SkyNight = Color(0xFF33407A); val SkyNightShadow = Color(0xFF1F2A57)
    val SkyDay = Color(0xFF2F6FC0); val SkyDayShadow = Color(0xFF1F4F8F)
    val SkyCloudy = Color(0xFF5B6B80); val SkyCloudyShadow = Color(0xFF3E4B5C)
    val SkyRain = Color(0xFF3F5A85); val SkyRainShadow = Color(0xFF2A3F61)
    val SkySnow = Color(0xFF4A7AA2); val SkySnowShadow = Color(0xFF325778)
    val SkyStorm = Color(0xFF4B3A78); val SkyStormShadow = Color(0xFF302452)
    val OnSkySoft = Color(0xFFDDE3FF)
    val SunYellow = Color(0xFFFFD45C)
}

/** Yuvarlak hatlı, okunaklı yazı tipi (SIL OFL lisanslı, uygulamaya gömülü). */
val Nunito = FontFamily(
    Font(R.font.nunito_regular, FontWeight.Normal),
    Font(R.font.nunito_bold, FontWeight.Bold),
    Font(R.font.nunito_extrabold, FontWeight.ExtraBold),
    Font(R.font.nunito_black, FontWeight.Black),
)

private fun nunitoTypography(): Typography {
    val base = Typography()
    return Typography(
        displayLarge = base.displayLarge.copy(fontFamily = Nunito),
        displayMedium = base.displayMedium.copy(fontFamily = Nunito),
        displaySmall = base.displaySmall.copy(fontFamily = Nunito),
        headlineLarge = base.headlineLarge.copy(fontFamily = Nunito),
        headlineMedium = base.headlineMedium.copy(fontFamily = Nunito),
        headlineSmall = base.headlineSmall.copy(fontFamily = Nunito),
        titleLarge = base.titleLarge.copy(fontFamily = Nunito),
        titleMedium = base.titleMedium.copy(fontFamily = Nunito),
        titleSmall = base.titleSmall.copy(fontFamily = Nunito),
        bodyLarge = base.bodyLarge.copy(fontFamily = Nunito),
        bodyMedium = base.bodyMedium.copy(fontFamily = Nunito),
        bodySmall = base.bodySmall.copy(fontFamily = Nunito),
        labelLarge = base.labelLarge.copy(fontFamily = Nunito),
        labelMedium = base.labelMedium.copy(fontFamily = Nunito),
        labelSmall = base.labelSmall.copy(fontFamily = Nunito),
    )
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
            typography = nunitoTypography(),
            content = content,
        )
    }
}
