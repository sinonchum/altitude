package app.altitude.domain

import kotlin.math.abs
import kotlin.math.min

enum class CameraLens { BACK, FRONT }

enum class DevicePose {
    PORTRAIT,
    LANDSCAPE,
    REVERSE_PORTRAIT,
    REVERSE_LANDSCAPE,
}

/**
 * Physical device orientation for capture.
 * Degrees follow [android.view.OrientationEventListener]: 0 is natural portrait,
 * 90 means the left side is up. Surface rotation constants are the counter-clockwise
 * display rotations (0, 1, 2, 3).
 */
object CaptureOrientation {
    const val UNKNOWN = -1
    const val HYSTERESIS_DEGREES = 15

    fun snapDegrees(previous: Int, rawDegrees: Int, hysteresis: Int = HYSTERESIS_DEGREES): Int {
        if (rawDegrees < 0) return if (previous < 0) 0 else floorMod(previous, 360)
        val raw = floorMod(rawDegrees, 360)
        if (previous < 0) return nearestQuarter(raw)
        val held = floorMod(previous, 360)
        if (angularDistance(raw, held) >= 45 + hysteresis) return nearestQuarter(raw)
        return held
    }

    fun surfaceRotation(deviceDegrees: Int): Int = when (floorMod(deviceDegrees, 360)) {
        0 -> 0
        90 -> 3
        180 -> 2
        270 -> 1
        else -> 0
    }

    fun pose(deviceDegrees: Int): DevicePose = when (floorMod(deviceDegrees, 360)) {
        0 -> DevicePose.PORTRAIT
        90 -> DevicePose.REVERSE_LANDSCAPE
        180 -> DevicePose.REVERSE_PORTRAIT
        270 -> DevicePose.LANDSCAPE
        else -> DevicePose.PORTRAIT
    }

    /**
     * Clockwise-negative rotation for controls so they read upright when the window
     * rotation has not yet caught the sensor. Zero once the two agree.
     */
    fun iconOffsetDegrees(deviceDegrees: Int, displaySurfaceRotation: Int): Int {
        val delta = floorMod(clockwiseDegrees(surfaceRotation(deviceDegrees)) - clockwiseDegrees(displaySurfaceRotation), 360)
        return if (delta > 180) -(delta - 360) else -delta
    }

    fun clockwiseDegrees(surfaceRotation: Int): Int = when (surfaceRotation) {
        0 -> 0
        1 -> 270
        2 -> 180
        3 -> 90
        else -> 0
    }

    private fun nearestQuarter(raw: Int): Int = floorMod((raw + 45) / 90 * 90, 360)

    private fun angularDistance(a: Int, b: Int): Int {
        val span = abs(a - b) % 360
        return min(span, 360 - span)
    }

    private fun floorMod(value: Int, modulus: Int): Int {
        val remainder = value % modulus
        return if (remainder < 0) remainder + modulus else remainder
    }
}

object CameraLenses {
    fun selectable(back: Boolean, front: Boolean): List<CameraLens> = buildList {
        if (back) add(CameraLens.BACK)
        if (front) add(CameraLens.FRONT)
    }

    fun buttonVisible(back: Boolean, front: Boolean): Boolean = selectable(back, front).size > 1

    fun resolve(preferred: CameraLens, back: Boolean, front: Boolean): CameraLens {
        val choices = selectable(back, front)
        if (preferred in choices) return preferred
        return choices.firstOrNull() ?: CameraLens.BACK
    }
}

object FocusMeteringChoice {
    const val AF = 1
    const val AE = 2

    fun select(supports: (Int) -> Boolean): Int? {
        return intArrayOf(AF or AE, AE, AF).firstOrNull(supports)
    }
}

object CaptureFraming {
    fun uprightSize(width: Int, height: Int, rotationDegrees: Int): PixelSize {
        val quarter = ((rotationDegrees % 360) + 360) % 360
        return if (quarter == 90 || quarter == 270) PixelSize(height, width) else PixelSize(width, height)
    }
}
