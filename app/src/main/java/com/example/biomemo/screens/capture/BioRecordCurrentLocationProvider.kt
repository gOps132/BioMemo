package com.example.biomemo.screens.capture

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class BioRecordCurrentLocationProvider(
    private val activity: AppCompatActivity
) {
    suspend fun currentLocation(timeoutMillis: Long = 2_500L): BioRecordLocationSnapshot? {
        if (!hasLocationPermission(activity)) return null
        val locationManager = activity.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val provider = locationManager.enabledProvider() ?: return null
        return withContext(Dispatchers.Main) {
            withTimeoutOrNull(timeoutMillis) {
                locationManager.awaitCurrentLocation(provider)
            } ?: locationManager.lastKnownSnapshot(provider)
        }
    }

    private suspend fun LocationManager.awaitCurrentLocation(provider: String): BioRecordLocationSnapshot? {
        return suspendCancellableCoroutine { continuation ->
            val signal = CancellationSignal()
            continuation.invokeOnCancellation { signal.cancel() }
            runCatching {
                getCurrentLocation(provider, signal, activity.mainExecutor) { location ->
                    continuation.resume(location?.toSnapshot(provider))
                }
            }.onFailure {
                continuation.resume(lastKnownSnapshot(provider))
            }
        }
    }

    private fun LocationManager.lastKnownSnapshot(provider: String): BioRecordLocationSnapshot? {
        return runCatching { getLastKnownLocation(provider)?.toSnapshot(provider) }.getOrNull()
    }

    private fun LocationManager.enabledProvider(): String? {
        return listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .firstOrNull { provider -> runCatching { isProviderEnabled(provider) }.getOrDefault(false) }
    }

    private fun Location.toSnapshot(provider: String): BioRecordLocationSnapshot {
        return BioRecordLocationSnapshot(
            latitude = latitude,
            longitude = longitude,
            source = "device $provider"
        )
    }

    companion object {
        val LOCATION_PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        fun hasLocationPermission(context: Context): Boolean {
            return LOCATION_PERMISSIONS.any { permission ->
                ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
            }
        }
    }
}
