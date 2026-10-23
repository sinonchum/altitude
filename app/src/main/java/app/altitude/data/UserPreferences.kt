package app.altitude.data

import android.content.Context
import android.content.SharedPreferences
import app.altitude.domain.AltitudeUnit
import app.altitude.domain.CameraLens
import app.altitude.domain.CaptionAnchor
import app.altitude.domain.DisplayTheme
import app.altitude.domain.LastKnown
import app.altitude.domain.PrefsSnapshot
import app.altitude.domain.WatermarkStyle
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class UserPreferences(context: Context) {
    private val prefs = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun current(): PrefsSnapshot = prefs.toSnapshot()

    fun updates(): Flow<PrefsSnapshot> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            trySend(prefs.toSnapshot())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(prefs.toSnapshot())
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    fun setUnit(unit: AltitudeUnit) = edit { putString(KEY_UNIT, unit.name) }

    fun setTheme(theme: DisplayTheme) = edit { putString(KEY_THEME, theme.name) }

    fun setMaxBrightness(enabled: Boolean) = edit { putBoolean(KEY_BRIGHTNESS, enabled) }

    fun setShowCoordinates(enabled: Boolean) = edit { putBoolean(KEY_COORDINATES, enabled) }

    fun setShowAccuracy(enabled: Boolean) = edit { putBoolean(KEY_ACCURACY, enabled) }

    fun setShowClock(enabled: Boolean) = edit { putBoolean(KEY_CLOCK, enabled) }

    fun setShowPlace(enabled: Boolean) = edit { putBoolean(KEY_PLACE, enabled) }

    fun setDynamicColor(enabled: Boolean) = edit { putBoolean(KEY_DYNAMIC, enabled) }

    fun setWatermarkStyle(style: WatermarkStyle) = edit { putString(KEY_WATERMARK, style.name) }

    fun setCaptionAnchor(anchor: CaptionAnchor) = edit { putString(KEY_ANCHOR, anchor.name) }

    fun setFrontCamera(front: Boolean) = edit { putString(KEY_LENS, if (front) CameraLens.FRONT.name else CameraLens.BACK.name) }

    fun setScreenFlash(enabled: Boolean) = edit { putBoolean(KEY_SCREEN_FLASH, enabled) }

    fun setOnboardingComplete() = edit { putBoolean(KEY_ONBOARDING, true) }

    fun readLastKnown(): LastKnown? {
        if (!prefs.contains(KEY_LAST_ALT)) return null
        return LastKnown(
            altitudeMeters = Double.fromBits(prefs.getLong(KEY_LAST_ALT, 0L)),
            timeMs = prefs.getLong(KEY_LAST_TIME, 0L),
            latitude = prefs.takeDouble(KEY_LAST_LAT),
            longitude = prefs.takeDouble(KEY_LAST_LON),
        )
    }

    fun writeLastKnown(known: LastKnown) = edit {
        putLong(KEY_LAST_ALT, known.altitudeMeters.toRawBits())
        putLong(KEY_LAST_TIME, known.timeMs)
        known.latitude?.let { putLong(KEY_LAST_LAT, it.toRawBits()) }
        known.longitude?.let { putLong(KEY_LAST_LON, it.toRawBits()) }
    }

    private fun edit(block: SharedPreferences.Editor.() -> Unit) {
        prefs.edit().apply(block).apply()
    }

    private fun SharedPreferences.toSnapshot(): PrefsSnapshot = PrefsSnapshot(
        unit = enumOr(KEY_UNIT, AltitudeUnit.METERS),
        theme = enumOr(KEY_THEME, DisplayTheme.NIGHT),
        maxBrightness = getBoolean(KEY_BRIGHTNESS, false),
        showCoordinates = getBoolean(KEY_COORDINATES, true),
        showAccuracy = getBoolean(KEY_ACCURACY, true),
        showClock = getBoolean(KEY_CLOCK, true),
        showPlace = getBoolean(KEY_PLACE, true),
        dynamicColor = getBoolean(KEY_DYNAMIC, false),
        watermarkStyle = enumOr(KEY_WATERMARK, WatermarkStyle.PLATE_LIGHT),
        captionAnchor = enumOr(KEY_ANCHOR, CaptionAnchor.BOTTOM),
        onboardingComplete = getBoolean(KEY_ONBOARDING, false),
        frontCamera = getString(KEY_LENS, null) == CameraLens.FRONT.name,
        screenFlash = getBoolean(KEY_SCREEN_FLASH, false),
    )

    private fun SharedPreferences.takeDouble(key: String): Double? {
        if (!contains(key)) return null
        return Double.fromBits(getLong(key, 0L))
    }

    private inline fun <reified T : Enum<T>> SharedPreferences.enumOr(key: String, default: T): T {
        val raw = getString(key, null) ?: return default
        return enumValues<T>().firstOrNull { it.name == raw } ?: default
    }

    private companion object {
        const val NAME = "altitude_prefs"
        const val KEY_UNIT = "unit"
        const val KEY_THEME = "theme"
        const val KEY_BRIGHTNESS = "max_brightness"
        const val KEY_COORDINATES = "show_coordinates"
        const val KEY_ACCURACY = "show_accuracy"
        const val KEY_CLOCK = "show_clock"
        const val KEY_PLACE = "show_place"
        const val KEY_DYNAMIC = "dynamic_color"
        const val KEY_WATERMARK = "watermark"
        const val KEY_ANCHOR = "caption_anchor"
        const val KEY_ONBOARDING = "onboarding_complete"
        const val KEY_LENS = "camera_lens"
        const val KEY_SCREEN_FLASH = "screen_flash"
        const val KEY_LAST_ALT = "last_alt_bits"
        const val KEY_LAST_TIME = "last_alt_time"
        const val KEY_LAST_LAT = "last_lat_bits"
        const val KEY_LAST_LON = "last_lon_bits"
    }
}
