package app.altitude.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

suspend fun resolvePlaceName(context: Context, latitude: Double, longitude: Double): String? {
    return withContext(Dispatchers.IO) {
        withTimeoutOrNull(4_000) {
            runCatching { lookup(context, latitude, longitude) }.getOrNull()
        }
    }
}

private suspend fun lookup(context: Context, latitude: Double, longitude: Double): String? {
    if (!Geocoder.isPresent()) return null
    val geocoder = Geocoder(context, Locale.getDefault())
    return suspendCancellableCoroutine { continuation ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            geocoder.getFromLocation(latitude, longitude, 1) { addresses ->
                if (continuation.isActive) continuation.resume(addresses.firstOrNull()?.localityLabel())
            }
        } else {
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(latitude, longitude, 1)
            continuation.resume(addresses?.firstOrNull()?.localityLabel())
        }
    }
}

private fun Address.localityLabel(): String? {
    return sequenceOf(locality, subAdminArea, adminArea, featureName)
        .firstOrNull { !it.isNullOrBlank() }
        ?.trim()
}
