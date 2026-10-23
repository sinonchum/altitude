package app.altitude.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/** The eight EXIF orientation values. Unknown tags are treated as [NORMAL]. */
enum class ExifOrientation(val tag: Int) {
    NORMAL(1),
    FLIP_HORIZONTAL(2),
    ROTATE_180(3),
    FLIP_VERTICAL(4),
    TRANSPOSE(5),
    ROTATE_90(6),
    TRANSVERSE(7),
    ROTATE_270(8),
    ;

    /**
     * Clockwise rotation applied first, then an optional horizontal mirror.
     * Same order Android uses to bake the EXIF orientation into pixels.
     */
    fun transform(): OrientationTransform = when (this) {
        NORMAL -> OrientationTransform(0, false)
        FLIP_HORIZONTAL -> OrientationTransform(0, true)
        ROTATE_180 -> OrientationTransform(180, false)
        FLIP_VERTICAL -> OrientationTransform(180, true)
        TRANSPOSE -> OrientationTransform(90, true)
        ROTATE_90 -> OrientationTransform(90, false)
        TRANSVERSE -> OrientationTransform(270, true)
        ROTATE_270 -> OrientationTransform(270, false)
    }

    companion object {
        fun fromTag(tag: Int): ExifOrientation = entries.firstOrNull { it.tag == tag } ?: NORMAL
    }
}

data class OrientationTransform(val clockwiseDegrees: Int, val mirrorHorizontal: Boolean)

/**
 * Wall clock stored on the photo, plus the offset when the file recorded one.
 * [local] is the time as taken. It is not shifted into the phone's current zone.
 */
data class CapturedClock(
    val local: LocalDateTime,
    val offset: ZoneOffset?,
) {
    fun instant(): OffsetDateTime? = offset?.let { local.atOffset(it) }
}

data class PhotoCapture(
    val latitude: Double?,
    val longitude: Double?,
    val exifAltitudeMeters: Double?,
    val clock: CapturedClock?,
    val orientation: ExifOrientation,
) {
    val hasCoordinates: Boolean
        get() = latitude != null && longitude != null
}

const val ALTITUDE_ABOVE_SEA_LEVEL = 0
const val ALTITUDE_BELOW_SEA_LEVEL = 1

/**
 * GPSAltitude is an unsigned rational. GPSAltitudeRef 1 is below sea level.
 * A missing ref means above sea level, which is the EXIF default.
 * A missing altitude stays missing even when the ref tag is present.
 */
fun signedGpsAltitudeMeters(altitudeMeters: Double?, altitudeRef: Int?): Double? {
    val magnitude = altitudeMeters ?: return null
    if (!magnitude.isFinite() || magnitude < 0.0) return null
    return if (altitudeRef == ALTITUDE_BELOW_SEA_LEVEL) -magnitude else magnitude
}

fun parseExifDateTime(raw: String?): LocalDateTime? {
    val text = cleanExifText(raw) ?: return null
    if (text.contains(":  :")) return null
    return runCatching { LocalDateTime.parse(text, EXIF_DATE_TIME) }.getOrNull()
}

/** Accepts `±HH:MM`, `±HHMM`, `±HH`, and `Z`. Anything else is unknown, not guessed. */
fun parseExifOffset(raw: String?): ZoneOffset? {
    val text = cleanExifText(raw) ?: return null
    if (text.equals("Z", ignoreCase = true)) return ZoneOffset.UTC
    val match = OFFSET.matchEntire(text) ?: return null
    val minutes = match.groupValues[3].ifEmpty { "00" }
    val normalized = "${match.groupValues[1]}${match.groupValues[2]}:$minutes"
    return runCatching { ZoneOffset.of(normalized) }.getOrNull()
}

/** GPS date and time are UTC. Either half missing means no GPS instant. */
fun parseGpsUtc(dateStamp: String?, timeStamp: String?): OffsetDateTime? {
    val date = cleanExifText(dateStamp) ?: return null
    val time = cleanExifText(timeStamp) ?: return null
    val day = runCatching { LocalDate.parse(date, GPS_DATE) }.getOrNull() ?: return null
    val clock = parseGpsClock(time) ?: return null
    return OffsetDateTime.of(day, clock, ZoneOffset.UTC)
}

/**
 * Prefer the camera's local DateTimeOriginal, then digitized, then the generic stamp.
 * The matching offset tag is attached and the wall clock is left as written.
 * GPS time is used only when no local stamp exists. It is UTC, and shifted into a
 * recorded offset when the file has one.
 */
fun resolveCapturedClock(
    dateTimeOriginal: String?,
    dateTimeDigitized: String?,
    dateTime: String?,
    offsetTimeOriginal: String?,
    offsetTimeDigitized: String?,
    offsetTime: String?,
    gpsDateStamp: String?,
    gpsTimeStamp: String?,
): CapturedClock? {
    parseExifDateTime(dateTimeOriginal)?.let { local ->
        val offset = parseExifOffset(offsetTimeOriginal) ?: parseExifOffset(offsetTime)
        return CapturedClock(local, offset)
    }
    parseExifDateTime(dateTimeDigitized)?.let { local ->
        val offset = parseExifOffset(offsetTimeDigitized) ?: parseExifOffset(offsetTime)
        return CapturedClock(local, offset)
    }
    parseExifDateTime(dateTime)?.let { local ->
        return CapturedClock(local, parseExifOffset(offsetTime))
    }
    val gps = parseGpsUtc(gpsDateStamp, gpsTimeStamp) ?: return null
    val offset = parseExifOffset(offsetTimeOriginal)
        ?: parseExifOffset(offsetTimeDigitized)
        ?: parseExifOffset(offsetTime)
        ?: ZoneOffset.UTC
    return CapturedClock(gps.withOffsetSameInstant(offset).toLocalDateTime(), offset)
}

fun formatExifWallClock(local: LocalDateTime): String = local.format(EXIF_DATE_TIME)

fun formatExifOffset(offset: ZoneOffset): String {
    val total = offset.totalSeconds
    val sign = if (total < 0) "-" else "+"
    val absolute = abs(total)
    val hours = absolute / 3600
    val minutes = (absolute % 3600) / 60
    return String.format(Locale.US, "%s%02d:%02d", sign, hours, minutes)
}

fun importDisplayName(clock: CapturedClock?, fallbackEpochMs: Long, index: Int): String {
    val stamp = if (clock != null) {
        clock.local.format(FILE_STAMP)
    } else {
        formatExifDateTime(fallbackEpochMs).replace(":", "").replace(" ", "_")
    }
    val suffix = if (index > 0) "_$index" else ""
    return "Altitude_$stamp$suffix.jpg"
}

fun rationalToDouble(raw: String): Double? {
    val text = raw.trim()
    if (text.isEmpty()) return null
    val slash = text.indexOf('/')
    if (slash < 0) return text.toDoubleOrNull()?.takeIf(Double::isFinite)
    val numerator = text.substring(0, slash).trim().toDoubleOrNull() ?: return null
    val denominator = text.substring(slash + 1).trim().toDoubleOrNull() ?: return null
    if (!numerator.isFinite() || !denominator.isFinite() || denominator == 0.0) return null
    return (numerator / denominator).takeIf(Double::isFinite)
}

fun cleanExifText(raw: String?): String? {
    return raw?.replace("\u0000", "")?.trim()?.takeIf { it.isNotEmpty() }
}

private fun parseGpsClock(time: String): LocalTime? {
    val colon = GPS_CLOCK.matchEntire(time)
    if (colon != null) {
        val hour = colon.groupValues[1].toDoubleOrNull() ?: return null
        val minute = colon.groupValues[2].toDoubleOrNull() ?: return null
        val second = colon.groupValues[3].toDoubleOrNull() ?: return null
        return clockFromParts(hour, minute, second)
    }
    val parts = time.split(',').map { it.trim() }
    if (parts.size != 3) return null
    val numbers = parts.map { rationalToDouble(it) ?: return null }
    return clockFromParts(numbers[0], numbers[1], numbers[2])
}

private fun clockFromParts(hour: Double, minute: Double, second: Double): LocalTime? {
    if (!hour.isFinite() || !minute.isFinite() || !second.isFinite()) return null
    val wholeHour = hour.toInt()
    val wholeMinute = minute.toInt()
    val wholeSecond = second.toInt()
    if (wholeHour !in 0..23 || wholeMinute !in 0..59 || wholeSecond !in 0..60) return null
    return LocalTime.of(wholeHour, wholeMinute, wholeSecond.coerceAtMost(59))
}

private val EXIF_DATE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss", Locale.US)
private val GPS_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy:MM:dd", Locale.US)
private val FILE_STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss", Locale.US)
private val OFFSET = Regex("""^([+-])(\d{2})(?::?(\d{2}))?$""")
private val GPS_CLOCK = Regex("""^(\d{1,2}):(\d{1,2}):(\d{1,2}(?:\.\d+)?)$""")
