package app.altitude.domain

import kotlin.math.pow

/**
 * International Standard Atmosphere troposphere (valid to about 11 km).
 * Pressure altitude is relative to a standard 1013.25 hPa surface, not to mean sea level.
 */
object PressureAltitude {
    const val STANDARD_PRESSURE_HPA = 1013.25

    private const val T0_KELVIN = 288.15
    private const val LAPSE_KELVIN_PER_METER = 0.0065

    // (R * L) / (g0 * M) with R = 8.3144598, g0 = 9.80665, M = 0.0289644
    private const val EXPONENT = 0.1902632367921655
    private const val SCALE_METERS = T0_KELVIN / LAPSE_KELVIN_PER_METER

    fun metersFromHpaOrNull(pressureHpa: Double): Double? {
        if (!pressureHpa.isFinite() || pressureHpa < 250.0 || pressureHpa > 1100.0) return null
        val ratio = pressureHpa / STANDARD_PRESSURE_HPA
        return SCALE_METERS * (1.0 - ratio.pow(EXPONENT))
    }

    fun hpaFromMeters(meters: Double): Double {
        val ratio = 1.0 - (meters / SCALE_METERS)
        return STANDARD_PRESSURE_HPA * ratio.pow(1.0 / EXPONENT)
    }
}
