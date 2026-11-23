package com.annem.launcher.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material.icons.rounded.LocationOn
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
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.repeatOnLifecycle
import com.annem.launcher.Actions
import com.annem.launcher.DeviceLocation
import com.annem.launcher.SettingsStore
import com.annem.launcher.WeatherApi
import com.annem.launcher.weatherDescription
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

private const val REFRESH_MS = 30L * 60L * 1000L // 30 dakikada bir
private const val RETRY_MS = 5L * 60L * 1000L    // hata olursa 5 dakika sonra tekrar

/**
 * Ekranın üstündeki büyük hava durumu kutusu. Telefonun yaklaşık konumunu kullanır
 * (izin yoksa ya da konum kapalıysa Ayarlar'daki şehri). Yalnızca ekran görünürken ve
 * en fazla 30 dakikada bir günceller; internet yoksa son bilinen değeri gösterir.
 * Dokununca Google'ın hava durumu sayfası açılır.
 */
@Composable
fun WeatherTile(store: SettingsStore, modifier: Modifier) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    // İzin dialogundan ya da ayarlardan dönünce izin durumu yeniden okunur.
    val lifecycleState by lifecycle.currentStateAsState()
    val granted = remember(lifecycleState) { DeviceLocation.hasPermission(context) }
    val useDevice = store.useDeviceLocation && granted
    val source = if (useDevice) "device" else "city:${store.weatherLatitude},${store.weatherLongitude}"

    // Kurulumda bir kez konum izni iste (yakın kurarken görür; sonra bir daha sorulmaz).
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (store.useDeviceLocation && !granted && !store.locationPermissionAsked) {
            store.locationPermissionAsked = true
            permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    var weather by remember(source) { mutableStateOf(store.cachedWeather(source)) }
    var placeName by remember(source) {
        mutableStateOf(if (useDevice) store.devicePlaceName ?: "Konumum" else store.weatherCity)
    }
    var offline by remember(source) { mutableStateOf(false) }

    LaunchedEffect(source, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                // Ana ekrana her dönüşte değil, veri 30 dakikadan eskiyse güncelle.
                val age = System.currentTimeMillis() - (weather?.fetchedAt ?: 0L)
                if (age in 0 until REFRESH_MS) delay(REFRESH_MS - age)

                val ok = try {
                    var latitude = store.weatherLatitude
                    var longitude = store.weatherLongitude
                    if (useDevice) {
                        val location = DeviceLocation.current(context)
                        if (location != null) {
                            latitude = location.latitude
                            longitude = location.longitude
                            DeviceLocation.placeName(context, latitude, longitude)?.let {
                                placeName = it
                                store.devicePlaceName = it
                            }
                        } else if (store.devicePlaceName == null) {
                            // Konum kapalı ve hiç bulunamadı: sabit şehri göster.
                            placeName = store.weatherCity
                        }
                    }
                    val fresh = WeatherApi.fetch(latitude, longitude)
                    weather = fresh
                    store.cacheWeather(fresh, source)
                    offline = false
                    true
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    offline = true
                    false
                }
                if (!ok) delay(RETRY_MS)
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
        "$placeName hava durumu: ${current.temperature} derece, $description, " +
            "en yüksek ${current.max}, en düşük ${current.min}. Google'da açmak için dokun."
    } else {
        "$placeName hava durumu: $description"
    }

    Surface(
        onClick = { Actions.openGoogleWeather(context, placeName) },
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = spoken },
        shape = RoundedCornerShape(24.dp),
        color = Color.White,
        border = BorderStroke(2.dp, Palette.WeatherLine),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = look?.icon ?: Icons.Rounded.CloudOff,
                contentDescription = null,
                tint = look?.tint ?: Palette.InkSoft,
                modifier = Modifier.size(72.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                // 1. satır: büyük sıcaklık + en yüksek / en düşük
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (current != null) "${current.temperature}°" else "--°",
                        fontSize = 54.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Palette.Ink,
                        lineHeight = 56.sp,
                    )
                    if (current != null) {
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text("↑ ${current.max}°", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Palette.InkSoft, lineHeight = 24.sp)
                            Text("↓ ${current.min}°", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Palette.InkSoft, lineHeight = 24.sp)
                        }
                    }
                }
                // 2. satır: durum ("Parçalı bulutlu"), gerekirse küçülerek tam sığar
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val fitted = (maxWidth.value / (description.length * 0.6f)).coerceIn(16f, 24f)
                    Text(
                        description,
                        fontSize = fitted.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Palette.Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // 3. satır: konum
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.LocationOn,
                        contentDescription = null,
                        tint = Palette.InkSoft,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        placeName,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = Palette.InkSoft,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
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
