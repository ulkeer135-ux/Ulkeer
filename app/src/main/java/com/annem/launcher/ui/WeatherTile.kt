package com.annem.launcher.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.FilterDrama
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Grain
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Umbrella
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.annem.launcher.Actions
import com.annem.launcher.SettingsStore
import com.annem.launcher.WeatherApi
import com.annem.launcher.WeatherNow
import com.annem.launcher.weatherDescription
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

private const val REFRESH_MS = 30L * 60L * 1000L // 30 dakikada bir
private const val RETRY_MS = 5L * 60L * 1000L    // hata olursa 5 dakika sonra tekrar

/**
 * Ekranın üstündeki geniş hava durumu kutusu. Yalnızca ekran görünürken ve en fazla
 * 30 dakikada bir internetten günceller; internet yoksa son bilinen değeri gösterir.
 * Dokununca Google'ın hava durumu sayfası açılır.
 */
@Composable
fun WeatherTile(store: SettingsStore, modifier: Modifier) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val city = store.weatherCity
    val latitude = store.weatherLatitude
    val longitude = store.weatherLongitude

    var weather by remember(latitude, longitude) { mutableStateOf(store.cachedWeather()) }
    var offline by remember(latitude, longitude) { mutableStateOf(false) }

    LaunchedEffect(latitude, longitude, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                // Ana ekrana her dönüşte değil, veri 30 dakikadan eskiyse güncelle.
                val age = System.currentTimeMillis() - (weather?.fetchedAt ?: 0L)
                if (age in 0 until REFRESH_MS) delay(REFRESH_MS - age)
                val next = try {
                    val fresh = WeatherApi.fetch(latitude, longitude)
                    weather = fresh
                    store.cacheWeather(fresh)
                    offline = false
                    REFRESH_MS
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    offline = true
                    RETRY_MS
                }
                if (next == RETRY_MS) delay(RETRY_MS)
            }
        }
    }

    val current = weather
    val look = current?.let { weatherLook(it.code, it.isDay) }
    val description = when {
        current != null -> weatherDescription(current.code)
        offline -> "İnternet yok"
        else -> "Yükleniyor…"
    }
    val spoken = if (current != null) {
        "$city hava durumu: ${current.temperature} derece, $description. Google'da açmak için dokun."
    } else {
        "$city hava durumu: $description"
    }

    Surface(
        onClick = { Actions.openGoogleWeather(context, city) },
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = spoken },
        shape = RoundedCornerShape(24.dp),
        color = Color.White,
        border = BorderStroke(2.dp, Palette.WeatherLine),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = look?.icon ?: Icons.Rounded.CloudOff,
                contentDescription = null,
                tint = look?.tint ?: Palette.InkSoft,
                modifier = Modifier.size(56.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text(
                    if (current != null) "${current.temperature}°" else "--°",
                    fontSize = 42.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Palette.Ink,
                    lineHeight = 44.sp,
                )
                Text(
                    description,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    color = Palette.InkSoft,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    city,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Palette.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (current != null) {
                    Text("↑${current.max}°  ↓${current.min}°", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Palette.InkSoft)
                }
            }
        }
    }
}

private class WeatherLook(val icon: ImageVector, val tint: Color)

private fun weatherLook(code: Int, isDay: Boolean): WeatherLook = when (code) {
    0 -> if (isDay) WeatherLook(Icons.Rounded.WbSunny, Palette.Sun) else WeatherLook(Icons.Rounded.NightsStay, Palette.Night)
    1, 2 -> WeatherLook(Icons.Rounded.FilterDrama, Palette.Cloudy)
    3, 45, 48 -> WeatherLook(Icons.Rounded.Cloud, Palette.Cloudy)
    51, 53, 55, 56, 57 -> WeatherLook(Icons.Rounded.Grain, Palette.Rain)
    61, 63, 65, 66, 67, 80, 81, 82 -> WeatherLook(Icons.Rounded.Umbrella, Palette.Rain)
    71, 73, 75, 77, 85, 86 -> WeatherLook(Icons.Rounded.AcUnit, Palette.Snow)
    95, 96, 99 -> WeatherLook(Icons.Rounded.FlashOn, Palette.Storm)
    else -> WeatherLook(Icons.Rounded.Cloud, Palette.Cloudy)
}
