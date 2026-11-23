package com.annem.launcher

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File

/** Uygulamanın tek ayarı: kırmızı Acil Yardım düğmesinin aradığı numara. */
class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("annem", Context.MODE_PRIVATE)

    var emergencyNumber by mutableStateOf(prefs.getString(KEY_EMERGENCY, "112") ?: "112")
        private set

    init {
        // 1.0 sürümündeki "Ailem" listesinden kalan kayıtları temizle.
        if (prefs.contains(KEY_OLD_CONTACTS)) prefs.edit().remove(KEY_OLD_CONTACTS).apply()
        File(context.filesDir, "photos").deleteRecursively()
    }

    fun updateEmergencyNumber(number: String) {
        emergencyNumber = number.trim().ifEmpty { "112" }
        prefs.edit().putString(KEY_EMERGENCY, emergencyNumber).apply()
    }

    private companion object {
        const val KEY_EMERGENCY = "emergency"
        const val KEY_OLD_CONTACTS = "contacts"
    }
}
