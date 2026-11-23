package com.annem.launcher

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.roundToInt

/** Anlık hava durumu + günün en yüksek/en düşük sıcaklığı. */
data class WeatherNow(
    val temperature: Int,
    val code: Int,
    val isDay: Boolean,
    val max: Int,
    val min: Int,
    val fetchedAt: Long,
)

data class Place(val name: String, val region: String, val latitude: Double, val longitude: Double) {
    val label: String get() = if (region.isBlank() || region == name) name else "$name, $region"
}

/**
 * Veri kaynağı Open-Meteo: ücretsiz, anahtar istemiyor. Google'ın kendi Weather API'si
 * faturalandırma açık bir API anahtarı gerektirdiği için kullanılmadı; kutuya dokununca
 * Google'ın hava durumu sayfası açılır.
 */
object WeatherApi {

    suspend fun fetch(latitude: Double, longitude: Double): WeatherNow = withContext(Dispatchers.IO) {
        val url = URL(
            "https://api.open-meteo.com/v1/forecast" +
                "?latitude=$latitude&longitude=$longitude" +
                "&current=temperature_2m,weather_code,is_day" +
                "&daily=temperature_2m_max,temperature_2m_min" +
                "&timezone=auto&forecast_days=1"
        )
        val json = JSONObject(httpGet(url))
        val current = json.getJSONObject("current")
        val daily = json.getJSONObject("daily")
        WeatherNow(
            temperature = current.getDouble("temperature_2m").roundToInt(),
            code = current.getInt("weather_code"),
            isDay = current.optInt("is_day", 1) == 1,
            max = daily.getJSONArray("temperature_2m_max").getDouble(0).roundToInt(),
            min = daily.getJSONArray("temperature_2m_min").getDouble(0).roundToInt(),
            fetchedAt = System.currentTimeMillis(),
        )
    }

    /** Şehir adından konum bulur (ör. "Kadıköy", "Ankara"). Bulamazsa null. */
    suspend fun geocode(name: String): Place? = withContext(Dispatchers.IO) {
        val query = URLEncoder.encode(name, "UTF-8")
        val url = URL("https://geocoding-api.open-meteo.com/v1/search?name=$query&count=1&language=tr&format=json")
        val results = JSONObject(httpGet(url)).optJSONArray("results")
        if (results == null || results.length() == 0) {
            null
        } else {
            val r = results.getJSONObject(0)
            Place(
                name = r.getString("name"),
                region = r.optString("admin1", ""),
                latitude = r.getDouble("latitude"),
                longitude = r.getDouble("longitude"),
            )
        }
    }

    private fun httpGet(url: URL): String {
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        try {
            val status = connection.responseCode
            if (status !in 200..299) throw IOException("HTTP $status")
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}

/** WMO hava kodunun Türkçe karşılığı. */
fun weatherDescription(code: Int): String = when (code) {
    0 -> "Açık"
    1 -> "Az bulutlu"
    2 -> "Parçalı bulutlu"
    3 -> "Kapalı"
    45, 48 -> "Sisli"
    51, 53, 55 -> "Çiseleme"
    56, 57 -> "Dondurucu çiseleme"
    61 -> "Hafif yağmurlu"
    63 -> "Yağmurlu"
    65 -> "Şiddetli yağmur"
    66, 67 -> "Dondurucu yağmur"
    71 -> "Hafif kar"
    73 -> "Karlı"
    75 -> "Yoğun kar"
    77 -> "Kar taneleri"
    80, 81 -> "Sağanak yağış"
    82 -> "Şiddetli sağanak"
    85, 86 -> "Kar sağanağı"
    95 -> "Gök gürültülü"
    96, 99 -> "Dolu ve fırtına"
    else -> "Hava durumu"
}
