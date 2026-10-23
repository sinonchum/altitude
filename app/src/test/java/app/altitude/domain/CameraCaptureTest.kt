package app.altitude.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraCaptureTest {
    @Test
    fun orientationSnapsToQuartersWithHysteresis() {
        assertEquals(0, CaptureOrientation.snapDegrees(CaptureOrientation.UNKNOWN, 10))
        assertEquals(90, CaptureOrientation.snapDegrees(CaptureOrientation.UNKNOWN, 80))
        assertEquals(0, CaptureOrientation.snapDegrees(0, 59))
        assertEquals(90, CaptureOrientation.snapDegrees(0, 60))
        assertEquals(90, CaptureOrientation.snapDegrees(90, 31))
        assertEquals(0, CaptureOrientation.snapDegrees(90, 30))
        assertEquals(0, CaptureOrientation.snapDegrees(0, 350))
        assertEquals(270, CaptureOrientation.snapDegrees(0, 300))
        assertEquals(180, CaptureOrientation.snapDegrees(90, 170))
        assertEquals(90, CaptureOrientation.snapDegrees(90, CaptureOrientation.UNKNOWN))
    }

    @Test
    fun degreesMapToSurfaceRotation() {
        assertEquals(0, CaptureOrientation.surfaceRotation(0))
        assertEquals(3, CaptureOrientation.surfaceRotation(90))
        assertEquals(2, CaptureOrientation.surfaceRotation(180))
        assertEquals(1, CaptureOrientation.surfaceRotation(270))
        assertEquals(DevicePose.REVERSE_LANDSCAPE, CaptureOrientation.pose(90))
        assertEquals(DevicePose.LANDSCAPE, CaptureOrientation.pose(270))
        assertEquals(DevicePose.PORTRAIT, CaptureOrientation.pose(0))
        assertEquals(0, CaptureOrientation.iconOffsetDegrees(90, 3))
        assertEquals(-90, CaptureOrientation.iconOffsetDegrees(90, 0))
        assertEquals(90, CaptureOrientation.iconOffsetDegrees(270, 0))
    }

    @Test
    fun uprightBufferSwapsSidesForQuarterTurns() {
        assertEquals(PixelSize(4000, 3000), CaptureFraming.uprightSize(3000, 4000, 90))
        assertEquals(PixelSize(4000, 3000), CaptureFraming.uprightSize(3000, 4000, -270))
        assertEquals(PixelSize(3000, 4000), CaptureFraming.uprightSize(3000, 4000, 180))
        assertEquals(PixelSize(4000, 3000), CaptureFraming.uprightSize(4000, 3000, 0))
        val upright = CaptureFraming.uprightSize(3000, 4000, 90)
        val stamped = WatermarkMetrics.outputSize(upright.width, upright.height, WatermarkStyle.PLATE_LIGHT, false)
        assertEquals(4000, stamped.width)
        assertTrue(stamped.height > upright.height)
        assertEquals(
            WatermarkMetrics.outputSize(3000, 4000, WatermarkStyle.PLATE_LIGHT, false).height - 4000,
            stamped.height - 3000,
        )
    }

    @Test
    fun lensButtonNeedsBothCamerasAndPresetsFollowTheRange() {
        assertFalse(CameraLenses.buttonVisible(back = true, front = false))
        assertFalse(CameraLenses.buttonVisible(back = false, front = true))
        assertTrue(CameraLenses.buttonVisible(back = true, front = true))
        assertEquals(CameraLens.BACK, CameraLenses.resolve(CameraLens.FRONT, back = true, front = false))
        assertEquals(CameraLens.FRONT, CameraLenses.resolve(CameraLens.FRONT, back = true, front = true))
        assertEquals(CameraLens.BACK, CameraLenses.resolve(CameraLens.BACK, back = false, front = false))
        assertEquals(listOf(1f, 2f), CameraZoom.presetsWithin(1f, 2.4f))
        assertEquals(listOf(0.5f, 1f, 2f, 5f), CameraZoom.presetsWithin(0.5f, 8f))
        assertEquals(listOf(1f), CameraZoom.presetsWithin(0.8f, 1.5f))
        assertTrue(CameraZoom.presetsWithin(1.2f, 1.8f).isEmpty())
    }

    @Test
    fun focusFallsBackWhenAutofocusIsMissing() {
        assertEquals(
            FocusMeteringChoice.AF or FocusMeteringChoice.AE,
            FocusMeteringChoice.select { true },
        )
        assertEquals(FocusMeteringChoice.AE, FocusMeteringChoice.select { it == FocusMeteringChoice.AE })
        assertEquals(FocusMeteringChoice.AF, FocusMeteringChoice.select { it == FocusMeteringChoice.AF })
        assertNull(FocusMeteringChoice.select { false })
    }

    @Test
    fun autofocusRequiresPictureModeAndFocusTravel() {
        val picture = intArrayOf(Autofocus.MODE_AUTO, Autofocus.MODE_CONTINUOUS_PICTURE)
        assertTrue(Autofocus.supported(picture, 0.1f, meteringSupportsAf = false))
        assertTrue(Autofocus.supported(intArrayOf(Autofocus.MODE_CONTINUOUS_PICTURE), 10f, meteringSupportsAf = false))
        assertFalse(Autofocus.supported(picture, 0f, meteringSupportsAf = true))
        assertFalse(Autofocus.supported(intArrayOf(0), 5f, meteringSupportsAf = true))
        assertFalse(Autofocus.supported(intArrayOf(3), 5f, meteringSupportsAf = true))
        assertFalse(Autofocus.supported(picture, Float.NaN, meteringSupportsAf = true))
        assertFalse(Autofocus.supported(intArrayOf(0), null, meteringSupportsAf = true))
        assertFalse(Autofocus.supported(null, 0f, meteringSupportsAf = true))
        assertTrue(Autofocus.supported(intArrayOf(Autofocus.MODE_AUTO), null, meteringSupportsAf = true))
        assertFalse(Autofocus.supported(intArrayOf(Autofocus.MODE_AUTO), null, meteringSupportsAf = false))
        assertTrue(Autofocus.supported(null, null, meteringSupportsAf = true))
        assertFalse(Autofocus.supported(null, null, meteringSupportsAf = false))
    }

    @Test
    fun tapUsesFocusAndMeteringOnlyWhenTheCameraCanFocus() {
        val everything: (Int) -> Boolean = { true }
        val exposureOnly: (Int) -> Boolean = { it == FocusMeteringChoice.AE }
        assertEquals(
            FocusMeteringChoice.AF or FocusMeteringChoice.AE,
            Autofocus.tapFlags(autofocus = true, everything),
        )
        assertEquals(FocusMeteringChoice.AE, Autofocus.tapFlags(autofocus = true, exposureOnly))
        assertEquals(FocusMeteringChoice.AE, Autofocus.tapFlags(autofocus = false, everything))
        assertEquals(FocusMeteringChoice.AE, Autofocus.tapFlags(autofocus = false, exposureOnly))
        assertNull(Autofocus.tapFlags(autofocus = false) { false })
        assertNull(Autofocus.tapFlags(autofocus = true) { false })
    }
}

class CameraStageMetricsTest {
    @Test
    fun landscapePreviewFillsMostOfTheScreen() {
        val style = WatermarkStyle.PLATE_LIGHT
        for ((width, height) in listOf(891f to 411f, 800f to 360f, 640f to 360f)) {
            val fraction = CameraStageMetrics.landscapePreviewFraction(
                screenWidth = width,
                screenHeight = height,
                zoomVisible = true,
                photoAspect = 4f / 3f,
                style = style,
                hasPlace = false,
            )
            assertTrue(
                "preview fraction $fraction on ${width}x$height",
                fraction >= CameraStageMetrics.MIN_LANDSCAPE_PREVIEW_FRACTION,
            )
        }
    }

    @Test
    fun shutterStaysOnThePhysicalBottomEdge() {
        assertTrue(CameraStageMetrics.shutterOnEnd(1))
        assertTrue(CameraStageMetrics.shutterOnEnd(0))
        assertFalse(CameraStageMetrics.shutterOnEnd(CameraStageMetrics.ROTATION_270))
        assertFalse(CameraStageMetrics.isLandscape(411f, 891f))
        assertTrue(CameraStageMetrics.isLandscape(891f, 411f))
    }
}
