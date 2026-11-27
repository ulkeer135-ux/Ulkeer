package com.annem.launcher.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import com.annem.launcher.DayForecast
import com.annem.launcher.Forecast
import com.annem.launcher.HourForecast
import com.annem.launcher.WeatherNow

private const val REFRESH_MS = 30L * 60L * 1000L // 30 dakikada bir
private const val RETRY_MS = 5L * 60L * 1000L    // hata olursa 5 dakika sonra tekrar

/**
 * Ekranın üstündeki büyük hava durumu kutusu. Telefonun yaklaşık konumunu kullanır
 * (izin yoksa ya da konum kapalıysa Ayarlar'daki şehri). Yalnızca ekran görünürken ve
 * en fazla 30 dakikada bir günceller; internet yoksa son bilinen değeri gösterir.
 * Dokununca saatlik ve günlük tahmin alttan açılan cam çekmecede görünür.
 */
@Composable
fun WeatherTile(store: SettingsStore, modifier: Modifier, onDetailsVisible: (Boolean) -> Unit = {}) {
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
    // Son kullanılan konum (çekmecedeki tahmin aynı yerden alınsın).
    var usedLat by remember(source) { mutableStateOf(store.weatherLatitude) }
    var usedLon by remember(source) { mutableStateOf(store.weatherLongitude) }
    var detailsOpen by remember { mutableStateOf(false) }
    LaunchedEffect(detailsOpen) { onDetailsVisible(detailsOpen) }

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
                    usedLat = latitude
                    usedLon = longitude
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
    val look = weatherLook(current?.code ?: -1, current?.isDay ?: true)
    val description = when {
        current != null -> weatherDescription(current.code)
        offline -> "İnternet yok"
        else -> "Yükleniyor…"
    }
    val spoken = if (current != null) {
        "$placeName hava durumu: ${current.temperature} derece, $description, " +
            "en yüksek ${current.max}, en düşük ${current.min}. Saatlik tahmin için dokun."
    } else {
        "$placeName hava durumu: $description"
    }
    val shape = RoundedCornerShape(30.dp)

    // Kart, havaya göre renk alır (gece lacivert, gündüz mavi, yağmurda gri-mavi…).
    Surface(
        onClick = { detailsOpen = true },
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = spoken },
        shape = shape,
        color = look.sky,
        contentColor = Color.White,
    ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                // Kart kısa kalırsa (küçük ekran) konum satırı gizlenir; hiçbir yazı kesilmez.
                val showPlace = maxHeight >= 116.dp
                val bigCard = maxHeight >= 140.dp
                // Çok kısa kart: durum yazısı sıcaklığın sağına geçer.
                val sideText = maxHeight < 112.dp
                val tempSp = if (bigCard) 54f else 44f
                if (look.stars) {
                    // Gece: birkaç küçük yıldız (süs).
                    Star(Modifier.align(Alignment.TopStart).offset(x = 130.dp, y = 18.dp), 5.dp)
                    Star(Modifier.align(Alignment.BottomStart).offset(x = 210.dp, y = (-20).dp), 4.dp)
                    Star(Modifier.align(Alignment.TopEnd).offset(x = (-36).dp, y = 26.dp), 6.dp)
                    Star(Modifier.align(Alignment.BottomEnd).offset(x = (-80).dp, y = (-34).dp), 4.dp)
                }
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = look.icon,
                        contentDescription = null,
                        tint = look.iconTint,
                        modifier = Modifier.size(if (bigCard) 70.dp else 56.dp),
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                        // 1. satır: büyük sıcaklık + en yüksek / en düşük
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (current != null) "${current.temperature}°" else "--°",
                                fontSize = tempSp.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                lineHeight = (tempSp + 2f).sp,
                            )
                            if (current != null) {
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text("↑ ${current.max}°", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = Palette.OnSkySoft, lineHeight = 22.sp)
                                    Text("↓ ${current.min}°", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = Palette.OnSkySoft, lineHeight = 22.sp)
                                }
                            }
                            if (sideText) {
                                Spacer(Modifier.width(14.dp))
                                FitText(
                                    text = description,
                                    maxSp = 22f,
                                    minSp = 13f,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        // 2. satır: durum, gerekirse küçülerek tam sığar
                        if (!sideText) FitText(
                            text = description,
                            maxSp = 23f,
                            minSp = 15f,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        // 3. satır: konum (yer varsa)
                        if (showPlace) Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.LocationOn,
                                contentDescription = null,
                                tint = Palette.OnSkySoft,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                placeName,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = Palette.OnSkySoft,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
    }

    if (detailsOpen) {
        WeatherSheet(
            place = placeName,
            latitude = usedLat,
            longitude = usedLon,
            useDevice = useDevice,
            onFresh = { w ->
                weather = w
                store.cacheWeather(w, source)
                offline = false
            },
            onGoogle = { Actions.openGoogleWeather(context, placeName) },
            onDismiss = { detailsOpen = false },
        )
    }
}

@Composable
private fun Star(modifier: Modifier, size: androidx.compose.ui.unit.Dp) {
    Box(modifier = modifier.size(size).clip(CircleShape).background(Palette.Moon))
}

private class WeatherLook(
    val icon: ImageVector,
    val iconTint: Color,
    val sky: Color,
    val shadow: Color,
    val stars: Boolean = false,
)

private fun weatherLook(code: Int, isDay: Boolean): WeatherLook {
    val night = !isDay
    return when (code) {
        0 -> if (isDay) {
            WeatherLook(Icons.Rounded.WbSunny, Palette.SunYellow, Palette.SkyDay, Palette.SkyDayShadow)
        } else {
            WeatherLook(Icons.Rounded.NightsStay, Palette.Moon, Palette.SkyNight, Palette.SkyNightShadow, stars = true)
        }
        1, 2 -> if (isDay) {
            WeatherLook(Icons.Rounded.FilterDrama, Color.White, Palette.SkyDay, Palette.SkyDayShadow)
        } else {
            WeatherLook(Icons.Rounded.NightsStay, Palette.Moon, Palette.SkyNight, Palette.SkyNightShadow, stars = true)
        }
        3, 45, 48 -> WeatherLook(Icons.Rounded.Cloud, Color.White, Palette.SkyCloudy, Palette.SkyCloudyShadow)
        51, 53, 55, 56, 57 -> WeatherLook(Icons.Rounded.Grain, Color.White, Palette.SkyRain, Palette.SkyRainShadow)
        61, 63, 65, 66, 67, 80, 81, 82 -> WeatherLook(Icons.Rounded.Umbrella, Color.White, Palette.SkyRain, Palette.SkyRainShadow)
        71, 73, 75, 77, 85, 86 -> WeatherLook(Icons.Rounded.AcUnit, Color.White, Palette.SkySnow, Palette.SkySnowShadow)
        95, 96, 99 -> WeatherLook(Icons.Rounded.FlashOn, Palette.SunYellow, Palette.SkyStorm, Palette.SkyStormShadow)
        // Veri yokken: günün saatine uygun sakin bir gökyüzü.
        else -> if (night) {
            WeatherLook(Icons.Rounded.CloudOff, Palette.OnSkySoft, Palette.SkyNight, Palette.SkyNightShadow)
        } else {
            WeatherLook(Icons.Rounded.CloudOff, Palette.OnSkySoft, Palette.SkyDay, Palette.SkyDayShadow)
        }
    }
}

/** Çekmece her açılışta yeniden indirmesin: son tahmin 30 dakika bellekte tutulur. */
private object ForecastCache {
    var key: String = ""
    var data: Forecast? = null
}

/** Açık zeminde (cam çekmece) okunur simge rengi. */
private fun lightTint(code: Int, isDay: Boolean): Color {
    val look = weatherLook(code, isDay)
    return when {
        code == 0 && isDay -> Color(0xFFE8A200)
        code in listOf(95, 96, 99) -> Color(0xFFE8A200)
        else -> look.sky
    }
}

private val RainBlue = Color(0xFF2F6FC0)

/** Hava durumu kutusuna dokununca: saatlik (24 saat) ve günlük (7 gün) tahmin. */
@Composable
private fun WeatherSheet(
    place: String,
    latitude: Double,
    longitude: Double,
    useDevice: Boolean,
    onFresh: (WeatherNow) -> Unit,
    onGoogle: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val key = String.format(java.util.Locale.US, "%.2f,%.2f,%b", latitude, longitude, useDevice)
    var forecast by remember { mutableStateOf(ForecastCache.data?.takeIf { ForecastCache.key == key }) }
    var failed by remember { mutableStateOf(false) }
    var attempt by remember { mutableStateOf(0) }
    LaunchedEffect(attempt) {
        val cached = forecast
        if (attempt == 0 && cached != null && System.currentTimeMillis() - cached.fetchedAt < REFRESH_MS) return@LaunchedEffect
        failed = false
        try {
            var lat = latitude
            var lon = longitude
            if (useDevice) DeviceLocation.current(context)?.let { lat = it.latitude; lon = it.longitude }
            val fresh = WeatherApi.forecast(lat, lon)
            forecast = fresh
            ForecastCache.key = key
            ForecastCache.data = fresh
            // Ana ekrandaki kutu da aynı anda aynı değeri göstersin.
            onFresh(fresh.now)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failed = true
        }
    }

    BalloonSheet(onDismiss = onDismiss) { close ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Hava durumu", fontSize = 26.sp, fontWeight = FontWeight.Black, color = Palette.Ink)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.LocationOn, contentDescription = null, tint = Palette.InkSoft, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(4.dp))
                Text(place, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Palette.InkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            val f = forecast
            if (f == null) {
                Text(
                    if (failed) "İnternet yok, tahmin alınamadı." else "Yükleniyor…",
                    fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Palette.Ink,
                )
                if (failed) {
                    OutlinedButton(onClick = { attempt++ }) { Text("Tekrar dene", fontSize = 19.sp, color = Palette.Ink) }
                }
            } else {
                // Şu an
                val now = f.now
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        weatherLook(now.code, now.isDay).icon,
                        contentDescription = null,
                        tint = lightTint(now.code, now.isDay),
                        modifier = Modifier.size(60.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text("${now.temperature}°", fontSize = 50.sp, fontWeight = FontWeight.Black, color = Palette.Ink, lineHeight = 52.sp)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(weatherDescription(now.code), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Palette.Ink)
                        Text("↑ ${now.max}°   ↓ ${now.min}°", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Palette.InkSoft)
                    }
                }

                Text("Saatlik", fontSize = 21.sp, fontWeight = FontWeight.Black, color = Palette.Ink)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(f.hours) { h -> HourCard(h) }
                }

                Text("Günlük", fontSize = 21.sp, fontWeight = FontWeight.Black, color = Palette.Ink)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    f.days.forEach { d -> DayRow(d) }
                }
            }

            OutlinedButton(
                onClick = onGoogle,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White.copy(alpha = 0.35f)),
            ) { Text("Google'da aç", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Palette.InkSoft) }
            OutlinedButton(
                onClick = close,
                modifier = Modifier.fillMaxWidth().height(60.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White.copy(alpha = 0.5f)),
            ) { Text("Kapat", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Palette.Ink) }
        }
    }
}

@Composable
private fun HourCard(h: HourForecast) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = Color.White.copy(alpha = 0.6f),
        border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.9f)),
    ) {
        Column(
            modifier = Modifier.width(80.dp).padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(h.label, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Palette.InkSoft, maxLines = 1)
            Icon(weatherLook(h.code, h.isDay).icon, contentDescription = null, tint = lightTint(h.code, h.isDay), modifier = Modifier.size(36.dp))
            Text("${h.temperature}°", fontSize = 23.sp, fontWeight = FontWeight.Black, color = Palette.Ink)
            if (h.rainChance >= 20) {
                Text("%${h.rainChance}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = RainBlue)
            } else {
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun DayRow(d: DayForecast) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.White.copy(alpha = 0.6f),
        border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.9f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(d.label, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Palette.Ink, modifier = Modifier.weight(1f), maxLines = 1)
            if (d.rainChance >= 20) {
                Text("%${d.rainChance}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = RainBlue)
                Spacer(Modifier.width(10.dp))
            }
            Icon(weatherLook(d.code, true).icon, contentDescription = null, tint = lightTint(d.code, true), modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(14.dp))
            Text("${d.max}°", fontSize = 21.sp, fontWeight = FontWeight.Black, color = Palette.Ink)
            Text("  ${d.min}°", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Palette.InkSoft)
        }
    }
}
