package com.annem.launcher

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Hava durumu için yaklaşık konum. Yalnızca "yaklaşık konum" izni kullanılır (GPS değil,
 * baz istasyonu/Wi-Fi), Google Play Hizmetleri gerekmez. Enerji: hava durumu
 * güncellenirken (en fazla 30 dakikada bir, ekran açıkken) tek seferlik sorulur.
 */
object DeviceLocation {

    private const val FRESH_ENOUGH_MS = 60L * 60L * 1000L // 1 saatten yeni son konum yeterli
    private const val REQUEST_TIMEOUT_MS = 20_000L

    fun hasPermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Telefonun yaklaşık konumu; izin yoksa ya da konum kapalıysa null. */
    @SuppressLint("MissingPermission")
    suspend fun current(context: Context): Location? {
        if (!hasPermission(context)) return null
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        val providers = try {
            manager.getProviders(true)
        } catch (e: Exception) {
            emptyList()
        }

        val lastKnown = providers
            .mapNotNull { provider ->
                try {
                    manager.getLastKnownLocation(provider)
                } catch (e: Exception) {
                    null
                }
            }
            .maxByOrNull { it.time }
        if (lastKnown != null && System.currentTimeMillis() - lastKnown.time < FRESH_ENOUGH_MS) {
            return lastKnown
        }

        val provider = when {
            LocationManager.NETWORK_PROVIDER in providers -> LocationManager.NETWORK_PROVIDER
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && LocationManager.FUSED_PROVIDER in providers ->
                LocationManager.FUSED_PROVIDER
            LocationManager.GPS_PROVIDER in providers -> LocationManager.GPS_PROVIDER
            else -> return lastKnown
        }
        val fresh = withTimeoutOrNull(REQUEST_TIMEOUT_MS) { requestOnce(context, manager, provider) }
        return fresh ?: lastKnown
    }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    private suspend fun requestOnce(
        context: Context,
        manager: LocationManager,
        provider: String,
    ): Location? = suspendCancellableCoroutine { cont ->
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val signal = CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                manager.getCurrentLocation(provider, signal, context.mainExecutor) { location ->
                    if (cont.isActive) cont.resume(location)
                }
            } else {
                // Eski Android'lerde arayüzün varsayılan metotları yok: hepsi açıkça yazıldı.
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        if (cont.isActive) cont.resume(location)
                    }

                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
                    override fun onProviderEnabled(provider: String) = Unit
                    override fun onProviderDisabled(provider: String) {
                        if (cont.isActive) cont.resume(null)
                    }
                }
                manager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
                cont.invokeOnCancellation { manager.removeUpdates(listener) }
            }
        } catch (e: Exception) {
            if (cont.isActive) cont.resume(null)
        }
    }

    /** Konumun semt/ilçe adı (ör. "Kadıköy"); bulunamazsa null. */
    @Suppress("DEPRECATION")
    suspend fun placeName(context: Context, latitude: Double, longitude: Double): String? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(context, Locale.forLanguageTag("tr-TR"))
        val address: Address? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            withTimeoutOrNull(10_000L) {
                suspendCancellableCoroutine<Address?> { cont ->
                    try {
                        geocoder.getFromLocation(latitude, longitude, 1, object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<Address>) {
                                if (cont.isActive) cont.resume(addresses.firstOrNull())
                            }

                            override fun onError(errorMessage: String?) {
                                if (cont.isActive) cont.resume(null)
                            }
                        })
                    } catch (e: Exception) {
                        if (cont.isActive) cont.resume(null)
                    }
                }
            }
        } else {
            withContext(Dispatchers.IO) {
                try {
                    geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull()
                } catch (e: Exception) {
                    null
                }
            }
        }
        return address?.let { it.subAdminArea ?: it.locality ?: it.adminArea }
    }
}
