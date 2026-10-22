package app.altitude.domain

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max

data class FusionConfig(
    val baroNoiseTauSeconds: Double = 0.15,
    val gpsTauExcellentSeconds: Double = 0.8,
    val gpsTauGoodSeconds: Double = 1.5,
    val gpsTauFairSeconds: Double = 3.0,
    val gpsTauPoorSeconds: Double = 6.0,
    val calibrationTauExcellentSeconds: Double = 12.0,
    val calibrationTauGoodSeconds: Double = 25.0,
    val calibrationTauFairSeconds: Double = 45.0,
    val calibrationTauPoorSeconds: Double = 90.0,
    val excellentAccuracyMeters: Double = 6.0,
    val goodAccuracyMeters: Double = 12.0,
    val fairAccuracyMeters: Double = 25.0,
    val maxCalibrationAccuracyMeters: Double = 40.0,
    val missingAccuracyMeters: Double = 30.0,
    val outlierGateMeters: Double = 25.0,
    val outlierSigma: Double = 3.0,
    val outlierResetMs: Long = 8_000,
    val freshGpsMs: Long = 5_000,
    val holdoverDriftMetersPerSecond: Double = 0.15,
    val minAltitudeMeters: Double = -500.0,
    val maxAltitudeMeters: Double = 12_000.0,
)

/**
 * Complementary filter. The barometer, once its offset is fixed against GPS mean-sea-level
 * altitude, owns short-term motion. GPS owns the slow correction of that offset.
 * Pressure altitude by itself is not mean sea level and is labeled as such.
 */
class AltitudeFusion(
    private val config: FusionConfig = FusionConfig(),
) {
    private var smoothedPressure: Double? = null
    private var smoothedGps: Double? = null
    private var calibrationOffset: Double? = null
    private var lastTimestampMs: Long? = null
    private var lastAcceptedGpsTimestampMs: Long? = null
    private var lastAcceptedAccuracy: Double? = null
    private var outlierSinceMs: Long? = null
    private var seenSensor: Boolean = false

    fun update(input: FusionInput): FusionOutput {
        if (input.gpsMslMeters != null || input.pressureHpa != null) seenSensor = true
        val gps = sanitizeAltitude(input.gpsMslMeters)
        val accuracy = if (gps != null) sanitizeAccuracy(input.gpsVerticalAccuracyMeters) else null
        val dt = lastTimestampMs?.let { previous ->
            (input.timestampMs - previous).coerceAtLeast(0L) / 1000.0
        } ?: 0.0
        lastTimestampMs = input.timestampMs

        val rawPressure = input.pressureHpa?.let(PressureAltitude::metersFromHpaOrNull)
        if (rawPressure != null) {
            smoothedPressure = approach(smoothedPressure, rawPressure, dt, config.baroNoiseTauSeconds)
        }
        val baro = smoothedPressure

        if (gps != null && accuracy != null && baro != null && accuracy <= config.maxCalibrationAccuracyMeters) {
            calibrate(input.timestampMs, dt, gps, baro, accuracy)
        } else if (gps != null && accuracy != null && baro == null) {
            val tau = gpsTau(accuracy)
            smoothedGps = approach(smoothedGps, gps, dt, tau)
            markGps(input.timestampMs, accuracy)
            outlierSinceMs = null
        }

        val offset = calibrationOffset
        val ageMs = lastAcceptedGpsTimestampMs?.let { input.timestampMs - it }

        if (baro != null && offset != null) {
            val age = ageMs ?: Long.MAX_VALUE
            val base = lastAcceptedAccuracy ?: config.missingAccuracyMeters
            val extra = if (age <= config.freshGpsMs) {
                0.0
            } else {
                config.holdoverDriftMetersPerSecond * ((age - config.freshGpsMs).coerceAtLeast(0L) / 1000.0)
            }
            val acc = base + extra
            val source = if (age <= config.freshGpsMs) AltitudeSource.FUSED else AltitudeSource.PRESSURE_HOLDOVER
            return publish(baro + offset, acc, source, quality(acc), absolute = true, calibrated = true, offset)
        }

        if (baro != null) {
            return publish(
                altitude = baro,
                accuracy = null,
                source = AltitudeSource.PRESSURE_UNCALIBRATED,
                quality = FixQuality.POOR,
                absolute = false,
                calibrated = false,
                offset = null,
            )
        }

        val gpsOnly = smoothedGps
        if (gpsOnly != null) {
            val acc = lastAcceptedAccuracy
            return publish(gpsOnly, acc, AltitudeSource.GPS, quality(acc), absolute = true, calibrated = false, offset = null)
        }

        return publish(
            altitude = null,
            accuracy = null,
            source = AltitudeSource.NONE,
            quality = if (seenSensor) FixQuality.SEARCHING else FixQuality.NONE,
            absolute = false,
            calibrated = false,
            offset = null,
        )
    }

    private fun calibrate(timestampMs: Long, dt: Double, gps: Double, baro: Double, accuracy: Double) {
        val desired = gps - baro
        val current = calibrationOffset
        if (current == null) {
            calibrationOffset = desired
            outlierSinceMs = null
            markGps(timestampMs, accuracy)
            return
        }
        val innovation = desired - current
        val gate = max(config.outlierGateMeters, accuracy * config.outlierSigma)
        if (abs(innovation) <= gate) {
            val alpha = alpha(dt, calibrationTau(accuracy))
            calibrationOffset = current + alpha * innovation
            outlierSinceMs = null
            markGps(timestampMs, accuracy)
            return
        }
        val since = outlierSinceMs ?: timestampMs.also { outlierSinceMs = it }
        if (timestampMs - since >= config.outlierResetMs) {
            calibrationOffset = desired
            outlierSinceMs = null
            markGps(timestampMs, accuracy)
        }
    }

    private fun markGps(timestampMs: Long, accuracy: Double) {
        lastAcceptedGpsTimestampMs = timestampMs
        lastAcceptedAccuracy = accuracy
    }

    private fun sanitizeAltitude(value: Double?): Double? {
        if (value == null || !value.isFinite()) return null
        if (value < config.minAltitudeMeters || value > config.maxAltitudeMeters) return null
        return value
    }

    private fun sanitizeAccuracy(accuracy: Double?): Double {
        if (accuracy == null || !accuracy.isFinite() || accuracy <= 0.0) return config.missingAccuracyMeters
        return accuracy
    }

    private fun approach(previous: Double?, sample: Double, dtSeconds: Double, tauSeconds: Double): Double {
        if (previous == null) return sample
        val alpha = alpha(dtSeconds, tauSeconds)
        return previous + alpha * (sample - previous)
    }

    private fun alpha(dtSeconds: Double, tauSeconds: Double): Double {
        if (tauSeconds <= 0.0) return 1.0
        if (dtSeconds <= 0.0) return 0.0
        return 1.0 - exp(-dtSeconds / tauSeconds)
    }

    private fun calibrationTau(accuracy: Double): Double = when {
        accuracy <= config.excellentAccuracyMeters -> config.calibrationTauExcellentSeconds
        accuracy <= config.goodAccuracyMeters -> config.calibrationTauGoodSeconds
        accuracy <= config.fairAccuracyMeters -> config.calibrationTauFairSeconds
        else -> config.calibrationTauPoorSeconds
    }

    private fun gpsTau(accuracy: Double): Double = when {
        accuracy <= config.excellentAccuracyMeters -> config.gpsTauExcellentSeconds
        accuracy <= config.goodAccuracyMeters -> config.gpsTauGoodSeconds
        accuracy <= config.fairAccuracyMeters -> config.gpsTauFairSeconds
        else -> config.gpsTauPoorSeconds
    }

    private fun quality(accuracy: Double?): FixQuality {
        val value = accuracy ?: return FixQuality.POOR
        return when {
            value <= config.excellentAccuracyMeters -> FixQuality.EXCELLENT
            value <= config.goodAccuracyMeters -> FixQuality.GOOD
            value <= config.fairAccuracyMeters -> FixQuality.FAIR
            else -> FixQuality.POOR
        }
    }

    private fun publish(
        altitude: Double?,
        accuracy: Double?,
        source: AltitudeSource,
        quality: FixQuality,
        absolute: Boolean,
        calibrated: Boolean,
        offset: Double?,
    ): FusionOutput = FusionOutput(
        altitudeMslMeters = altitude,
        accuracyMeters = accuracy,
        source = source,
        quality = quality,
        absoluteMsl = absolute,
        barometerCalibrated = calibrated,
        calibrationOffsetMeters = offset,
    )
}
