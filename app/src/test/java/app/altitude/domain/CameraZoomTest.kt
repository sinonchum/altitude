package app.altitude.domain

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraZoomTest {
    @Test
    fun pinchStaysInsideTheDeviceRangeIncludingUltrawide() {
        assertEquals(0.5f, CameraZoom.afterPinch(1f, 0.4f, 0.5f, 8f), 0.001f)
        assertEquals(2.4f, CameraZoom.afterPinch(1.2f, 2f, 0.5f, 8f), 0.001f)
        assertEquals(8f, CameraZoom.afterPinch(5f, 3f, 0.5f, 8f), 0.001f)
        assertEquals(1.2f, CameraZoom.afterPinch(1.2f, Float.NaN, 0.5f, 8f), 0.001f)
    }

    @Test
    fun presetsDropRatiosTheLensCannotReach() {
        assertEquals(listOf(0.5f, 1f, 2f, 5f), CameraZoom.presetsWithin(0.5f, 10f))
        assertEquals(listOf(1f, 2f), CameraZoom.presetsWithin(1f, 4f))
        assertEquals(listOf(1f), CameraZoom.presetsWithin(0.6f, 1.2f))
        assertTrue(CameraZoom.presetsWithin(6f, 8f).isEmpty())
        assertTrue(CameraZoom.presetsWithin(2f, 1f).isEmpty())
    }

    @Test
    fun presetHighlightAndLabelsFollowTheRatio() {
        assertTrue(CameraZoom.matchesPreset(1.04f, 1f))
        assertFalse(CameraZoom.matchesPreset(2.3f, 2f))
        assertEquals("1", CameraZoom.formatZoomNumber(1f, Locale.US))
        assertEquals("2.3", CameraZoom.formatZoomNumber(2.26f, Locale.US))
        assertEquals("0,5", CameraZoom.formatZoomNumber(0.5f, Locale.FRANCE))
        assertEquals("2,3", CameraZoom.formatZoomNumber(2.26f, Locale.FRANCE))
    }
}
