package com.annem.launcher

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File

/**
 * Uygulama ayarları: hava durumunun konumu (telefonun konumu ya da sabit şehir) ve
 * son alınan hava durumu (internet yokken gösterilir).
 */
class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("annem", Context.MODE_PRIVATE)

    /** true: telefonun konumu kullanılır (izin varsa); false: sabit şehir. */
    var useDeviceLocation by mutableStateOf(prefs.getBoolean(KEY_USE_LOC, true))
        private set

    /** Sabit şehir; telefonun konumu alınamazsa da bu kullanılır. */
    var weatherCity by mutableStateOf(prefs.getString(KEY_CITY, DEFAULT_CITY) ?: DEFAULT_CITY)
        private set
    var weatherLatitude by mutableStateOf(readDouble(KEY_LAT, DEFAULT_LAT))
        private set
    var weatherLongitude by mutableStateOf(readDouble(KEY_LON, DEFAULT_LON))
        private set

    /** Ana ekrandaki selamda kullanılan hitap ("İyi akşamlar, Anneciğim"). Boş olabilir. */
    var greetingName by mutableStateOf(prefs.getString(KEY_GREETING, DEFAULT_GREETING) ?: DEFAULT_GREETING)
        private set

    fun updateGreetingName(name: String) {
        greetingName = name.trim()
        prefs.edit().putString(KEY_GREETING, greetingName).apply()
    }

    /** Annem içinde favorilere eklenen kişiler (rehberdeki yıldızlılara ek olarak). */
    var contactFavorites by mutableStateOf(readFavorites())
        private set

    /** Kişilere Annem içinde verilen görünen adlar ("Oğlum", "Gelinim"…); rehber değişmez. */
    var contactLabels by mutableStateOf(readLabels())
        private set

    /** Rehberde yıldızlı olduğu halde Annem'de favorilerden çıkarılan kişiler. */
    var contactHidden by mutableStateOf(readIds(KEY_HIDDEN))
        private set

    /** Annem içinde fotoğraf seçilmiş kişiler: kişi → sürüm (fotoğraf değişince yenilenir). */
    var contactPhotos by mutableStateOf(readPhotoVersions())
        private set

    fun isFavorite(id: Long, starredInPhone: Boolean): Boolean =
        (starredInPhone || id in contactFavorites) && id !in contactHidden

    /** Yıldıza dokununca: favoriyse çıkarır, değilse ekler. */
    fun toggleFavorite(id: Long, starredInPhone: Boolean) {
        if (isFavorite(id, starredInPhone)) {
            contactFavorites = contactFavorites - id
            if (starredInPhone) contactHidden = contactHidden + id
        } else {
            contactHidden = contactHidden - id
            if (!starredInPhone) contactFavorites = contactFavorites + id
        }
        saveContacts()
    }

    fun updateLabel(id: Long, label: String) {
        contactLabels = if (label.isBlank()) contactLabels - id else contactLabels + (id to label.trim())
        saveContacts()
    }

    fun setPhotoVersion(id: Long, version: Long?) {
        contactPhotos = if (version == null) contactPhotos - id else contactPhotos + (id to version)
        saveContacts()
    }

    private fun saveContacts() {
        val labelsJson = org.json.JSONObject()
        contactLabels.forEach { (k, v) -> labelsJson.put(k.toString(), v) }
        val photosJson = org.json.JSONObject()
        contactPhotos.forEach { (k, v) -> photosJson.put(k.toString(), v) }
        prefs.edit()
            .putString(KEY_FAVORITES, contactFavorites.joinToString(","))
            .putString(KEY_HIDDEN, contactHidden.joinToString(","))
            .putString(KEY_LABELS, labelsJson.toString())
            .putString(KEY_PHOTOS, photosJson.toString())
            .apply()
    }

    private fun readFavorites(): Set<Long> = readIds(KEY_FAVORITES)

    private fun readIds(key: String): Set<Long> =
        prefs.getString(key, "").orEmpty().split(',').mapNotNull { it.trim().toLongOrNull() }.toSet()

    private fun readPhotoVersions(): Map<Long, Long> = try {
        val json = org.json.JSONObject(prefs.getString(KEY_PHOTOS, "{}") ?: "{}")
        json.keys().asSequence().mapNotNull { k -> k.toLongOrNull()?.let { it to json.getLong(k) } }.toMap()
    } catch (e: Exception) {
        emptyMap()
    }

    private fun readLabels(): Map<Long, String> = try {
        val json = org.json.JSONObject(prefs.getString(KEY_LABELS, "{}") ?: "{}")
        json.keys().asSequence().mapNotNull { k -> k.toLongOrNull()?.let { it to json.getString(k) } }.toMap()
    } catch (e: Exception) {
        emptyMap()
    }

    /** Ana ekrandaki kutu sayısı: 9 (3×3) ya da 6 (2×3, daha büyük kutular). */
    var tileCount by mutableStateOf(if (prefs.getInt(KEY_TILES, 9) == 6) 6 else 9)
        private set

    fun updateTileCount(count: Int) {
        tileCount = if (count == 6) 6 else 9
        prefs.edit().putInt(KEY_TILES, tileCount).apply()
    }

    /** WhatsApp ve Telefon kutularında kırmızı sayılar gösterilsin mi? */
    var badgesEnabled by mutableStateOf(prefs.getBoolean(KEY_BADGES, true))
        private set

    fun updateBadgesEnabled(enabled: Boolean) {
        badgesEnabled = enabled
        prefs.edit().putBoolean(KEY_BADGES, enabled).apply()
    }

    /** Ana ekran tuşunu Annem kendisi karşılasın (telefonun 1 sn'lik geçişini atlar). */
    var fastHome by mutableStateOf(prefs.getBoolean(KEY_FAST_HOME, true))
        private set

    fun updateFastHome(enabled: Boolean) {
        fastHome = enabled
        prefs.edit().putBoolean(KEY_FAST_HOME, enabled).apply()
    }

    /** Telefonun konumundan bulunan son semt adı (ör. "Kadıköy"). */
    var devicePlaceName: String?
        get() = prefs.getString(KEY_DEVICE_PLACE, null)
        set(value) = prefs.edit().putString(KEY_DEVICE_PLACE, value).apply()

    /** İlk açılışta konum izni bir kez soruldu mu? */
    var locationPermissionAsked: Boolean
        get() = prefs.getBoolean(KEY_ASKED, false)
        set(value) = prefs.edit().putBoolean(KEY_ASKED, value).apply()

    /** Kamera / Fotoğraflar kutularına atanmış uygulamalar (yoksa varsayılan davranış). */
    private val slots = mutableStateMapOf<String, TileSlot>()

    init {
        Slots.ALL.forEach { id ->
            val pkg = prefs.getString("slot_${id}_pkg", null)
            val label = prefs.getString("slot_${id}_label", null)
            if (pkg != null && label != null) slots[id] = TileSlot(pkg, label)
        }
        // Eski sürümlerden kalan kayıtları bir kez temizle (Ailem listesi, fotoğraflar, acil numara).
        if (!prefs.getBoolean("legacy_cleaned", false)) {
            prefs.edit().remove("contacts").remove("emergency").putBoolean("legacy_cleaned", true).apply()
            File(context.filesDir, "photos").deleteRecursively()
        }
    }

    fun slot(id: String): TileSlot? = slots[id]

    fun setSlot(id: String, packageName: String, label: String) {
        slots[id] = TileSlot(packageName, label)
        prefs.edit().putString("slot_${id}_pkg", packageName).putString("slot_${id}_label", label).apply()
    }

    fun setSlotLabel(id: String, label: String) {
        val current = slots[id] ?: return
        setSlot(id, current.packageName, label)
    }

    fun clearSlot(id: String) {
        slots.remove(id)
        prefs.edit().remove("slot_${id}_pkg").remove("slot_${id}_label").apply()
    }

    fun updateUseDeviceLocation(enabled: Boolean) {
        useDeviceLocation = enabled
        prefs.edit().putBoolean(KEY_USE_LOC, enabled).apply()
    }

    /** Sabit şehri kaydeder ve hava durumunu sabit şehre geçirir. */
    fun setPlace(place: Place) {
        weatherCity = place.name
        weatherLatitude = place.latitude
        weatherLongitude = place.longitude
        prefs.edit()
            .putString(KEY_CITY, place.name)
            .putString(KEY_LAT, place.latitude.toString())
            .putString(KEY_LON, place.longitude.toString())
            .apply()
        updateUseDeviceLocation(false)
    }

    /** [source] o anki konum kaynağı; başka kaynağın eski verisi gösterilmez. */
    fun cachedWeather(source: String): WeatherNow? {
        if (!prefs.contains(KEY_W_AT) || prefs.getString(KEY_W_SRC, null) != source) return null
        return WeatherNow(
            temperature = prefs.getInt(KEY_W_TEMP, 0),
            code = prefs.getInt(KEY_W_CODE, -1),
            isDay = prefs.getBoolean(KEY_W_DAY, true),
            max = prefs.getInt(KEY_W_MAX, 0),
            min = prefs.getInt(KEY_W_MIN, 0),
            fetchedAt = prefs.getLong(KEY_W_AT, 0L),
        )
    }

    /** Son başarılı hava durumu güncellemesinin zamanı (yoksa 0). */
    fun lastWeatherAt(): Long = prefs.getLong(KEY_W_AT, 0L)

    /** "Şimdi yenile": saklanan veriyi eskimiş say; ana ekran açılınca hemen yenilenir. */
    fun invalidateWeather() {
        if (prefs.contains(KEY_W_AT)) prefs.edit().putLong(KEY_W_AT, 1L).apply()
    }

    fun cacheWeather(w: WeatherNow, source: String) {
        prefs.edit()
            .putString(KEY_W_SRC, source)
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
        const val DEFAULT_GREETING = "Anneciğim"
        const val KEY_GREETING = "greeting_name"
        const val KEY_BADGES = "badges_enabled"
        const val KEY_FAST_HOME = "fast_home"
        const val KEY_TILES = "tile_count"
        const val KEY_FAVORITES = "contact_favorites"
        const val KEY_LABELS = "contact_labels"
        const val KEY_HIDDEN = "contact_hidden"
        const val KEY_PHOTOS = "contact_photos"
        const val DEFAULT_LAT = 41.0082
        const val DEFAULT_LON = 28.9784

        const val KEY_USE_LOC = "use_device_location"
        const val KEY_ASKED = "location_permission_asked"
        const val KEY_DEVICE_PLACE = "device_place"
        const val KEY_CITY = "weather_city"
        const val KEY_LAT = "weather_lat"
        const val KEY_LON = "weather_lon"
        const val KEY_W_SRC = "w_src"
        const val KEY_W_TEMP = "w_temp"
        const val KEY_W_CODE = "w_code"
        const val KEY_W_DAY = "w_day"
        const val KEY_W_MAX = "w_max"
        const val KEY_W_MIN = "w_min"
        const val KEY_W_AT = "w_at"
    }
}
