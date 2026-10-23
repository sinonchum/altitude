package app.altitude.domain

import java.util.Locale

private const val OPEN_METEO_ELEVATION = "https://api.open-meteo.com/v1/elevation"

/**
 * Open-Meteo elevation endpoint. The service returns Copernicus DEM heights in
 * meters above sea level for the requested coordinates. No API key, no local table.
 */
fun openMeteoElevationUrl(points: List<Pair<Double, Double>>): String {
    require(points.isNotEmpty())
    val latitudes = points.joinToString(",") { formatCoordinateQuery(it.first) }
    val longitudes = points.joinToString(",") { formatCoordinateQuery(it.second) }
    return "$OPEN_METEO_ELEVATION?latitude=$latitudes&longitude=$longitudes"
}

/** About 11 m. Coarser than the DEM cell, fine enough that nearby photos do not share a height. */
fun elevationCacheKey(latitude: Double, longitude: Double): String {
    return String.format(Locale.US, "%.4f,%.4f", latitude, longitude)
}

/**
 * Parses an Open-Meteo elevation body.
 * Returns null when the body is not a usable elevation payload.
 * A null entry in the array is a missing height for that coordinate.
 */
fun parseOpenMeteoElevations(body: String): List<Double?>? {
    val root = try {
        parseJsonValue(body) as? Map<*, *>
    } catch (_: RuntimeException) {
        return null
    } ?: return null
    val values = root["elevation"] as? List<*> ?: return null
    return values.map { value ->
        when (value) {
            is String -> value.toDoubleOrNull()?.takeIf { it.isFinite() }
            else -> null
        }
    }
}
