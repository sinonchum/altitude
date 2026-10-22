package app.altitude.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.os.SystemClock
import app.altitude.BuildConfig
import app.altitude.domain.OsmLookup
import app.altitude.domain.choosePlaceLabel
import app.altitude.domain.formatCoordinateQuery
import app.altitude.domain.millisUntilNextPlaceRequest
import app.altitude.domain.nominatimAcceptLanguage
import app.altitude.domain.osmLocalityName
import app.altitude.domain.placeCacheKey
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import kotlin.coroutines.resume

private const val NOMINATIM_URL = "https://nominatim.openstreetmap.org/reverse"
private const val USER_AGENT = "Altitude/${BuildConfig.VERSION_NAME} (https://github.com/sinonchum/altitude)"
private const val MIN_REQUEST_INTERVAL_MS = 1_000L

private val requestMutex = Mutex()
private val placeCache = ConcurrentHashMap<String, String>()
private var lastRequestStartedMs = 0L

/**
 * Locality for [latitude] and [longitude] in [locale].
 * Nominatim is the source when it can be reached. The platform geocoder runs
 * only after a network failure. Work stays off the main thread.
 */
suspend fun resolvePlaceName(
    context: Context,
    latitude: Double,
    longitude: Double,
    locale: Locale,
): String? {
    return withContext(Dispatchers.IO) {
        val languageTag = locale.toLanguageTag()
        val key = placeCacheKey(latitude, longitude, languageTag)
        placeCache[key]?.let { return@withContext it }

        val acceptLanguage = nominatimAcceptLanguage(languageTag)
        awaitRequestSlot()
        val osm = fetchOsm(latitude, longitude, acceptLanguage)
        val geocoderName = if (osm is OsmLookup.Unreachable) {
            withTimeoutOrNull(4_000) { lookupGeocoder(context, latitude, longitude, locale) }
        } else {
            null
        }
        val label = choosePlaceLabel(languageTag, osm, geocoderName, ::transliterateHan)
        if (osm is OsmLookup.Reached && !label.isNullOrBlank()) placeCache[key] = label
        label
    }
}

private suspend fun awaitRequestSlot() {
    requestMutex.withLock {
        val wait = millisUntilNextPlaceRequest(
            SystemClock.elapsedRealtime(),
            lastRequestStartedMs,
            MIN_REQUEST_INTERVAL_MS,
        )
        if (wait > 0) delay(wait)
        lastRequestStartedMs = SystemClock.elapsedRealtime()
    }
}

private fun fetchOsm(latitude: Double, longitude: Double, acceptLanguage: String): OsmLookup {
    val query = buildString {
        append("format=jsonv2")
        append("&lat=").append(formatCoordinateQuery(latitude))
        append("&lon=").append(formatCoordinateQuery(longitude))
        append("&zoom=10&addressdetails=1&accept-language=")
        append(URLEncoder.encode(acceptLanguage, Charsets.UTF_8.name()))
    }
    val connection = (URL("$NOMINATIM_URL?$query").openConnection() as HttpURLConnection).apply {
        connectTimeout = 4_000
        readTimeout = 4_000
        instanceFollowRedirects = true
        setRequestProperty("User-Agent", USER_AGENT)
        setRequestProperty("Accept", "application/json")
        setRequestProperty("Accept-Language", acceptLanguage)
    }
    return try {
        val code = connection.responseCode
        if (code !in 200..299) return OsmLookup.Unreachable
        val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        OsmLookup.Reached(parseOsmLocality(body))
    } catch (cancel: CancellationException) {
        throw cancel
    } catch (_: Exception) {
        OsmLookup.Unreachable
    } finally {
        connection.disconnect()
    }
}

private fun parseOsmLocality(body: String): String? {
    val json = JSONObject(body)
    if (json.has("error")) return null
    val addressJson = json.optJSONObject("address")
    val address = buildMap {
        if (addressJson != null) {
            val keys = addressJson.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = addressJson.optString(key)
                if (value.isNotBlank()) put(key, value)
            }
        }
    }
    return osmLocalityName(
        name = json.optString("name"),
        address = address,
        addresstype = json.optString("addresstype").takeIf { it.isNotBlank() },
        displayName = json.optString("display_name").takeIf { it.isNotBlank() },
    )
}

private suspend fun lookupGeocoder(
    context: Context,
    latitude: Double,
    longitude: Double,
    locale: Locale,
): String? {
    if (!Geocoder.isPresent()) return null
    val geocoder = Geocoder(context, locale)
    return try {
        suspendCancellableCoroutine { continuation ->
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
    } catch (cancel: CancellationException) {
        throw cancel
    } catch (_: Exception) {
        null
    }
}

private fun transliterateHan(text: String): String? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
    return runCatching {
        android.icu.text.Transliterator.getInstance("Han-Latin; Latin-ASCII").transliterate(text)
    }.getOrNull()?.takeIf { it.isNotBlank() }
}

private fun Address.localityLabel(): String? {
    return sequenceOf(locality, subAdminArea, adminArea, featureName)
        .firstOrNull { !it.isNullOrBlank() }
        ?.trim()
}
