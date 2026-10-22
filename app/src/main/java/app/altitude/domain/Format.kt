package app.altitude.domain

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

private const val METERS_PER_FOOT = 0.3048

fun metersToFeet(meters: Double): Double = meters / METERS_PER_FOOT

fun groupThousands(value: Long): String {
    val sign = if (value < 0) "-" else ""
    val digits = abs(value).toString()
    val grouped = buildString(digits.length + digits.length / 3) {
        digits.forEachIndexed { index, char ->
            if (index > 0 && (digits.length - index) % 3 == 0) append(',')
            append(char)
        }
    }
    return sign + grouped
}

fun formatAltitudeNumber(meters: Double, unit: AltitudeUnit): String {
    val value = if (unit == AltitudeUnit.FEET) metersToFeet(meters) else meters
    return groupThousands(value.roundToLong())
}

fun formatAltitudeReading(meters: Double, unit: AltitudeUnit): String {
    val suffix = if (unit == AltitudeUnit.FEET) "ft" else "m"
    return "${formatAltitudeNumber(meters, unit)} $suffix"
}

fun formatAccuracy(meters: Double, unit: AltitudeUnit): String {
    val value = if (unit == AltitudeUnit.FEET) metersToFeet(meters) else meters
    val suffix = if (unit == AltitudeUnit.FEET) "ft" else "m"
    return "± ${groupThousands(value.roundToLong())} $suffix"
}

fun formatCoordinate(value: Double, latitude: Boolean): String {
    val hemisphere = when {
        latitude && value >= 0.0 -> "N"
        latitude -> "S"
        value >= 0.0 -> "E"
        else -> "W"
    }
    return String.format(Locale.US, "%.5f° %s", abs(value), hemisphere)
}

fun formatCoordinatePair(latitude: Double, longitude: Double): String {
    return "${formatCoordinate(latitude, true)}   ${formatCoordinate(longitude, false)}"
}

fun formatDisplayClock(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    return Instant.ofEpochMilli(epochMs).atZone(zone).format(CLOCK)
}

fun formatWatermarkStamp(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val zoned = Instant.ofEpochMilli(epochMs).atZone(zone)
    return zoned.format(DATE) + "  " + zoned.format(MINUTE)
}

fun formatExifDateTime(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    return Instant.ofEpochMilli(epochMs).atZone(zone).format(EXIF)
}

fun buildWatermarkContent(
    altitudeMeters: Double?,
    absoluteMsl: Boolean,
    unit: AltitudeUnit,
    latitude: Double?,
    longitude: Double?,
    place: String?,
    epochMs: Long,
    zone: ZoneId = ZoneId.systemDefault(),
): WatermarkContent {
    val altitude = if (altitudeMeters != null && absoluteMsl) {
        formatAltitudeReading(altitudeMeters, unit)
    } else {
        "— —"
    }
    val coordinates = if (latitude != null && longitude != null) {
        formatCoordinatePair(latitude, longitude)
    } else {
        null
    }
    return WatermarkContent(
        altitude = altitude,
        coordinates = coordinates,
        place = place?.takeIf { it.isNotBlank() },
        time = formatWatermarkStamp(epochMs, zone),
    )
}

private val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.US)
private val MINUTE: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
private val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd", Locale.US)
private val EXIF: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss", Locale.US)
