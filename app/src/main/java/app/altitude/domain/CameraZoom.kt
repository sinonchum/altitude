package app.altitude.domain

import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

object CameraZoom {
    val presetRatios: List<Float> = listOf(0.5f, 1f, 2f, 5f)

    fun clamp(ratio: Float, min: Float, max: Float): Float {
        if (!ratio.isFinite()) return 1f
        if (!min.isFinite() || !max.isFinite() || max < min) return ratio
        return ratio.coerceIn(min, max)
    }

    fun afterPinch(current: Float, scale: Float, min: Float, max: Float): Float {
        if (!scale.isFinite() || scale <= 0f) return clamp(current, min, max)
        return clamp(current * scale, min, max)
    }

    fun presetsWithin(min: Float, max: Float, candidates: List<Float> = presetRatios): List<Float> {
        if (!min.isFinite() || !max.isFinite() || max < min) return emptyList()
        return candidates.filter { ratio ->
            ratio.isFinite() && ratio >= min - 0.001f && ratio <= max + 0.001f
        }
    }

    fun matchesPreset(current: Float, preset: Float): Boolean {
        return current.isFinite() && preset.isFinite() && abs(current - preset) < 0.06f
    }

    fun formatZoomNumber(ratio: Float, locale: Locale): String {
        if (!ratio.isFinite()) return ""
        val tenths = (ratio * 10f).roundToInt()
        val sign = if (tenths < 0) "-" else ""
        val absolute = abs(tenths)
        val whole = absolute / 10
        val fraction = absolute % 10
        if (fraction == 0) return sign + whole.toString()
        val separator = DecimalFormatSymbols.getInstance(locale).decimalSeparator
        return "$sign$whole$separator$fraction"
    }
}
