package app.altitude.location

import android.os.SystemClock
import app.altitude.BuildConfig
import app.altitude.domain.elevationCacheKey
import app.altitude.domain.millisUntilNextPlaceRequest
import app.altitude.domain.openMeteoElevationUrl
import app.altitude.domain.parseOpenMeteoElevations
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val USER_AGENT = "Altitude/${BuildConfig.VERSION_NAME} (https://github.com/sinonchum/altitude)"
private const val MIN_REQUEST_INTERVAL_MS = 1_000L

private val requestMutex = Mutex()
private val elevationCache = ConcurrentHashMap<String, Double>()
private var lastRequestStartedMs = 0L

/**
 * Meters above sea level from a public DEM, or null for each point that cannot be resolved.
 * Results are cached. Calls are spaced at least one second apart.
 */
suspend fun fetchElevations(points: List<Pair<Double, Double>>): List<Double?> {
    if (points.isEmpty()) return emptyList()
    return withContext(Dispatchers.IO) {
        val missing = points.withIndex().filter { elevationCache[elevationCacheKey(it.value.first, it.value.second)] == null }
        if (missing.isNotEmpty()) {
            val fetched = requestElevations(missing.map { it.value })
            if (fetched != null && fetched.size == missing.size) {
                missing.forEachIndexed { index, item ->
                    val meters = fetched[index] ?: return@forEachIndexed
                    elevationCache[elevationCacheKey(item.value.first, item.value.second)] = meters
                }
            }
        }
        points.map { elevationCache[elevationCacheKey(it.first, it.second)] }
    }
}

private suspend fun requestElevations(points: List<Pair<Double, Double>>): List<Double?>? {
    awaitRequestSlot()
    val connection = (URL(openMeteoElevationUrl(points)).openConnection() as HttpURLConnection).apply {
        connectTimeout = 8_000
        readTimeout = 8_000
        instanceFollowRedirects = true
        setRequestProperty("User-Agent", USER_AGENT)
        setRequestProperty("Accept", "application/json")
    }
    return try {
        val code = connection.responseCode
        if (code !in 200..299) return null
        val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        parseOpenMeteoElevations(body)
    } catch (cancel: CancellationException) {
        throw cancel
    } catch (_: Exception) {
        null
    } finally {
        connection.disconnect()
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
