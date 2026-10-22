package app.altitude.location

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import androidx.core.content.ContextCompat
import app.altitude.domain.GnssSample
import app.altitude.domain.LocationFeed
import app.altitude.domain.activeLocationFeeds
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.Granularity
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
    /**
     * Satellite updates are registered whenever precise location is granted.
     * Fused location is added beside them, never instead of them: constructing the
     * Play Services client used to count as success and skip [LocationManager.GPS_PROVIDER],
     * so phones where fused location never delivers (common without working Google
     * services) never asked the GPS chip for a fix.
     */
    @SuppressLint("MissingPermission")
    fun fixes(precise: Boolean): Flow<GnssSample> = callbackFlow {
        val feeds = activeLocationFeeds(playServicesAvailable(context), precise)
        val manager = context.getSystemService(LocationManager::class.java)
        val listener = LocationListener { location ->
            trySend(location.toGnssSample())
        }
        if (manager != null) {
            if (LocationFeed.SATELLITE in feeds) {
                requestProvider(manager, LocationManager.GPS_PROVIDER, listener)
            }
            if (LocationFeed.NETWORK in feeds) {
                requestProvider(manager, LocationManager.NETWORK_PROVIDER, listener)
            }
        }
        val stopFused = if (LocationFeed.FUSED in feeds) startFused(precise) else null
        awaitClose {
            if (manager != null) runCatching { manager.removeUpdates(listener) }
            stopFused?.invoke()
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestProvider(
        manager: LocationManager,
        provider: String,
        listener: LocationListener,
    ) {
        if (!manager.allProviders.contains(provider)) return
        runCatching { manager.getLastKnownLocation(provider) }
            .getOrNull()
            ?.let(listener::onLocationChanged)
        val enabled = runCatching { manager.isProviderEnabled(provider) }.getOrDefault(false)
        if (!enabled) return
        runCatching {
            manager.requestLocationUpdates(provider, 1_000L, 0f, listener, Looper.getMainLooper())
        }
    }

    @SuppressLint("MissingPermission")
    private fun kotlinx.coroutines.channels.ProducerScope<GnssSample>.startFused(precise: Boolean): (() -> Unit)? {
        return try {
            val client = LocationServices.getFusedLocationProviderClient(context)
            val granularity = if (precise) Granularity.GRANULARITY_FINE else Granularity.GRANULARITY_COARSE
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1_000L)
                .setMinUpdateIntervalMillis(500L)
                .setMinUpdateDistanceMeters(0f)
                .setGranularity(granularity)
                .setWaitForAccurateLocation(false)
                .build()
            val callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.locations.forEach { location -> trySend(location.toGnssSample()) }
                }
            }
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
                .addOnFailureListener { }
            client.lastLocation.addOnSuccessListener { location ->
                if (location != null) trySend(location.toGnssSample())
            }
            return { runCatching { client.removeLocationUpdates(callback) } }
        } catch (_: Throwable) {
            null
        }
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
        elapsedRealtimeMs = elapsedRealtimeNanos / 1_000_000L,
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

fun isGpsProviderEnabled(context: Context): Boolean {
    val manager = context.getSystemService(LocationManager::class.java) ?: return false
    if (!manager.allProviders.contains(LocationManager.GPS_PROVIDER)) return false
    return runCatching { manager.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false)
}

fun locationModeChanges(context: Context): Flow<Unit> = callbackFlow {
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            trySend(Unit)
        }
    }
    val filter = IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION)
    val registered = runCatching {
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }.isSuccess
    awaitClose {
        if (registered) runCatching { context.unregisterReceiver(receiver) }
    }
}
