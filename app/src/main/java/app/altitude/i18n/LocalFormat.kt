package app.altitude.i18n

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources
import app.altitude.R
import app.altitude.domain.AltitudeUnit
import app.altitude.domain.WatermarkContent
import app.altitude.domain.formatDecimal
import app.altitude.domain.formatInteger
import app.altitude.domain.metersToFeet
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

class LocalFormat(private val resources: Resources) {
    private val locale: Locale
        get() = resources.configuration.locales[0]

    fun integer(value: Long): String = formatInteger(value, locale)

    fun altitudeNumber(meters: Double, unit: AltitudeUnit): String {
        val value = if (unit == AltitudeUnit.FEET) metersToFeet(meters) else meters
        return integer(value.roundToLong())
    }

    fun altitudeReading(meters: Double, unit: AltitudeUnit): String {
        return resources.getString(
            R.string.format_altitude,
            altitudeNumber(meters, unit),
            resources.getString(unit.symbolRes()),
        )
    }

    fun accuracy(meters: Double, unit: AltitudeUnit): String {
        val value = if (unit == AltitudeUnit.FEET) metersToFeet(meters) else meters
        return resources.getString(
            R.string.format_accuracy,
            integer(value.roundToLong()),
            resources.getString(unit.symbolRes()),
        )
    }

    fun coordinate(value: Double, latitude: Boolean): String {
        val number = formatDecimal(abs(value), 5, locale)
        val hemisphere = resources.getString(
            when {
                latitude && value >= 0.0 -> R.string.hemisphere_n
                latitude -> R.string.hemisphere_s
                value >= 0.0 -> R.string.hemisphere_e
                else -> R.string.hemisphere_w
            },
        )
        return resources.getString(R.string.format_coordinate, number, hemisphere)
    }

    fun coordinatePair(latitude: Double, longitude: Double): String {
        return resources.getString(
            R.string.format_coordinate_pair,
            coordinate(latitude, true),
            coordinate(longitude, false),
        )
    }

    fun displayClock(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        return Instant.ofEpochMilli(epochMs).atZone(zone).format(pattern(R.string.pattern_display_clock))
    }

    fun watermarkStamp(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val zoned = Instant.ofEpochMilli(epochMs).atZone(zone)
        val date = zoned.format(pattern(R.string.pattern_watermark_date))
        val time = zoned.format(pattern(R.string.pattern_watermark_time))
        return resources.getString(R.string.format_watermark_stamp, date, time)
    }

    fun watermark(
        altitudeMeters: Double?,
        absoluteMsl: Boolean,
        unit: AltitudeUnit,
        latitude: Double?,
        longitude: Double?,
        place: String?,
        epochMs: Long,
    ): WatermarkContent {
        val altitude = if (altitudeMeters != null && absoluteMsl) {
            altitudeReading(altitudeMeters, unit)
        } else {
            resources.getString(R.string.altitude_unavailable)
        }
        val coordinates = if (latitude != null && longitude != null) {
            coordinatePair(latitude, longitude)
        } else {
            resources.getString(R.string.watermark_awaiting_fix)
        }
        return WatermarkContent(
            altitude = altitude,
            coordinates = coordinates,
            place = place?.takeIf { it.isNotBlank() },
            time = watermarkStamp(epochMs),
            wordmark = resources.getString(R.string.wordmark),
        )
    }

    private fun pattern(patternRes: Int): DateTimeFormatter {
        return DateTimeFormatter.ofPattern(resources.getString(patternRes), locale)
    }
}

@Composable
fun rememberLocalFormat(): LocalFormat {
    return LocalFormat(LocalResources.current)
}
