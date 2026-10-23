package app.altitude.domain

enum class AltitudeUnit {
    METERS,
    FEET,
}

enum class DisplayTheme(val lightBars: Boolean) {
    NIGHT(false),
    VOID(false),
    GLACIER(false),
    SAFFRON(false),
    SNOW(true),
}

enum class AppMode {
    DISPLAY,
    CAMERA,
}

enum class WatermarkStyle {
    PLATE_LIGHT,
    PLATE_DARK,
    MOUNT,
    CAPTION,
}

enum class CaptionAnchor {
    BOTTOM,
    TOP,
}

enum class AltitudeSource {
    NONE,
    GPS,
    PRESSURE_UNCALIBRATED,
    FUSED,
    PRESSURE_HOLDOVER,
}

enum class FixQuality {
    NONE,
    SEARCHING,
    POOR,
    FAIR,
    GOOD,
    EXCELLENT,
}

data class GnssSample(
    val latitude: Double,
    val longitude: Double,
    val ellipsoidalAltitudeMeters: Double?,
    val platformMslAltitudeMeters: Double?,
    val horizontalAccuracyMeters: Double?,
    val verticalAccuracyMeters: Double?,
    val timeMs: Long,
    val elapsedRealtimeMs: Long = 0L,
)

data class FusionInput(
    val timestampMs: Long,
    val gpsMslMeters: Double?,
    val gpsVerticalAccuracyMeters: Double?,
    val pressureHpa: Double?,
)

data class FusionOutput(
    val altitudeMslMeters: Double?,
    val accuracyMeters: Double?,
    val source: AltitudeSource,
    val quality: FixQuality,
    val absoluteMsl: Boolean,
    val barometerCalibrated: Boolean,
    val calibrationOffsetMeters: Double?,
) {
    companion object {
        fun empty(): FusionOutput = FusionOutput(
            altitudeMslMeters = null,
            accuracyMeters = null,
            source = AltitudeSource.NONE,
            quality = FixQuality.NONE,
            absoluteMsl = false,
            barometerCalibrated = false,
            calibrationOffsetMeters = null,
        )
    }
}

data class PrefsSnapshot(
    val unit: AltitudeUnit,
    val theme: DisplayTheme,
    val maxBrightness: Boolean,
    val showCoordinates: Boolean,
    val showAccuracy: Boolean,
    val showClock: Boolean,
    val showPlace: Boolean,
    val dynamicColor: Boolean,
    val watermarkStyle: WatermarkStyle,
    val captionAnchor: CaptionAnchor,
    val onboardingComplete: Boolean,
    val frontCamera: Boolean,
    val screenFlash: Boolean,
)

data class LastKnown(
    val altitudeMeters: Double,
    val timeMs: Long,
    val latitude: Double?,
    val longitude: Double?,
)

data class WatermarkContent(
    val altitude: String,
    val coordinates: String?,
    val place: String?,
    val time: String?,
    val wordmark: String = "ALTITUDE",
)
