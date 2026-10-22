package app.altitude.location

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import app.altitude.domain.GnssSample
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

fun pressureHpa(context: Context): Flow<Double> = callbackFlow {
    val manager = context.getSystemService(SensorManager::class.java)
    val sensor = manager?.getDefaultSensor(Sensor.TYPE_PRESSURE)
    if (manager == null || sensor == null) {
        awaitClose { }
        return@callbackFlow
    }
    val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val value = event.values.firstOrNull()?.toDouble() ?: return
            trySend(value)
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }
    manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
    awaitClose { manager.unregisterListener(listener) }
}

class GnssRepository(private val context: Context) {
    @SuppressLint("MissingPermission")
    fun fixes(): Flow<GnssSample> = callbackFlow {
        val fused = if (playServicesAvailable(context)) startFused() else false
        if (!fused) startPlatform()
        awaitClose { /* listeners are removed by invokeOnClose */ }
    }

    @SuppressLint("MissingPermission")
    private fun kotlinx.coroutines.channels.ProducerScope<GnssSample>.startFused(): Boolean {
        return try {
            val client = LocationServices.getFusedLocationProviderClient(context)
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 500L)
                .setMinUpdateIntervalMillis(200L)
                .setMinUpdateDistanceMeters(0f)
                .setWaitForAccurateLocation(false)
                .build()
            val callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.lastLocation?.toGnssSample()?.let { trySend(it) }
                }
            }
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
            client.lastLocation.addOnSuccessListener { location ->
                location?.toGnssSample()?.let { trySend(it) }
            }
            invokeOnClose { client.removeLocationUpdates(callback) }
            true
        } catch (_: Throwable) {
            false
        }
    }

    @SuppressLint("MissingPermission")
    private fun kotlinx.coroutines.channels.ProducerScope<GnssSample>.startPlatform() {
        val manager = context.getSystemService(LocationManager::class.java) ?: return
        val listener = LocationListener { location ->
            location.toGnssSample()?.let { trySend(it) }
        }
        listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).forEach { provider ->
            if (!manager.allProviders.contains(provider)) return@forEach
            runCatching { manager.getLastKnownLocation(provider) }
                .getOrNull()
                ?.toGnssSample()
                ?.let { trySend(it) }
            if (manager.isProviderEnabled(provider)) {
                manager.requestLocationUpdates(provider, 500L, 0f, listener, Looper.getMainLooper())
            }
        }
        invokeOnClose { manager.removeUpdates(listener) }
    }
}

fun playServicesAvailable(context: Context): Boolean {
    return GoogleApiAvailability.getInstance()
        .isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
}

fun Location.toGnssSample(): GnssSample {
    val platformMsl = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && hasMslAltitude()) {
        mslAltitudeMeters
    } else {
        null
    }
    val vertical = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && hasMslAltitudeAccuracy() ->
            mslAltitudeAccuracyMeters.toDouble()
        hasVerticalAccuracy() -> verticalAccuracyMeters.toDouble()
        else -> null
    }
    return GnssSample(
        latitude = latitude,
        longitude = longitude,
        ellipsoidalAltitudeMeters = if (hasAltitude()) altitude else null,
        platformMslAltitudeMeters = platformMsl,
        horizontalAccuracyMeters = if (hasAccuracy()) accuracy.toDouble() else null,
        verticalAccuracyMeters = vertical,
        timeMs = time,
    )
}

fun isLocationEnabled(context: Context): Boolean {
    val manager = context.getSystemService(LocationManager::class.java) ?: return false
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        manager.isLocationEnabled
    } else {
        @Suppress("DEPRECATION")
        manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }
}
