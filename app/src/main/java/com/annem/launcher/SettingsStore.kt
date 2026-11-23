package com.annem.launcher

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File

/** Uygulama ayarları: hava durumu şehri ve son alınan hava durumu (internet yokken gösterilir). */
class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("annem", Context.MODE_PRIVATE)

    var weatherCity by mutableStateOf(prefs.getString(KEY_CITY, DEFAULT_CITY) ?: DEFAULT_CITY)
        private set
    var weatherLatitude by mutableStateOf(readDouble(KEY_LAT, DEFAULT_LAT))
        private set
    var weatherLongitude by mutableStateOf(readDouble(KEY_LON, DEFAULT_LON))
        private set

    init {
        // Eski sürümlerden kalan kayıtları temizle (Ailem listesi, fotoğraflar, acil numara).
        prefs.edit().remove("contacts").remove("emergency").apply()
        File(context.filesDir, "photos").deleteRecursively()
    }

    fun setPlace(place: Place) {
        weatherCity = place.name
        weatherLatitude = place.latitude
        weatherLongitude = place.longitude
        prefs.edit()
            .putString(KEY_CITY, place.name)
            .putString(KEY_LAT, place.latitude.toString())
            .putString(KEY_LON, place.longitude.toString())
            .remove(KEY_W_AT) // başka şehrin eski hava durumu gösterilmesin
            .apply()
    }

    fun cachedWeather(): WeatherNow? {
        if (!prefs.contains(KEY_W_AT)) return null
        return WeatherNow(
            temperature = prefs.getInt(KEY_W_TEMP, 0),
            code = prefs.getInt(KEY_W_CODE, -1),
            isDay = prefs.getBoolean(KEY_W_DAY, true),
            max = prefs.getInt(KEY_W_MAX, 0),
            min = prefs.getInt(KEY_W_MIN, 0),
            fetchedAt = prefs.getLong(KEY_W_AT, 0L),
        )
    }

    fun cacheWeather(w: WeatherNow) {
        prefs.edit()
            .putInt(KEY_W_TEMP, w.temperature)
            .putInt(KEY_W_CODE, w.code)
            .putBoolean(KEY_W_DAY, w.isDay)
            .putInt(KEY_W_MAX, w.max)
            .putInt(KEY_W_MIN, w.min)
            .putLong(KEY_W_AT, w.fetchedAt)
            .apply()
    }

    private fun readDouble(key: String, default: Double): Double =
        prefs.getString(key, null)?.toDoubleOrNull() ?: default

    private companion object {
        const val DEFAULT_CITY = "İstanbul"
        const val DEFAULT_LAT = 41.0082
        const val DEFAULT_LON = 28.9784

        const val KEY_CITY = "weather_city"
        const val KEY_LAT = "weather_lat"
        const val KEY_LON = "weather_lon"
        const val KEY_W_TEMP = "w_temp"
        const val KEY_W_CODE = "w_code"
        const val KEY_W_DAY = "w_day"
        const val KEY_W_MAX = "w_max"
        const val KEY_W_MIN = "w_min"
        const val KEY_W_AT = "w_at"
    }
}
