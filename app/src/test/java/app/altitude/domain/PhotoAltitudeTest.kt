package app.altitude.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoAltitudeTest {
    @Test
    fun exifAltitudeWinsOverALookup() {
        val selected = selectPhotoAltitude(
            exifAltitudeMeters = 5200.0,
            elevationLookupMeters = 100.0,
            hasCoordinates = true,
        )
        assertEquals(AltitudeProvenance.FROM_PHOTO, selected.provenance)
        assertEquals(5200.0, selected.meters!!, 0.0)
    }

    @Test
    fun zeroAndNegativeExifValuesAreReal() {
        val sea = selectPhotoAltitude(0.0, 40.0, hasCoordinates = true)
        val below = selectPhotoAltitude(-8.0, 40.0, hasCoordinates = true)
        assertEquals(AltitudeProvenance.FROM_PHOTO, sea.provenance)
        assertEquals(0.0, sea.meters!!, 0.0)
        assertEquals(AltitudeProvenance.FROM_PHOTO, below.provenance)
        assertEquals(-8.0, below.meters!!, 0.0)
    }

    @Test
    fun missingExifUsesTheLookupWhenThePhotoHasCoordinates() {
        val selected = selectPhotoAltitude(
            exifAltitudeMeters = null,
            elevationLookupMeters = 3648.2,
            hasCoordinates = true,
        )
        assertEquals(AltitudeProvenance.COMPUTED, selected.provenance)
        assertEquals(3648.2, selected.meters!!, 0.0)
    }

    @Test
    fun failedLookupDegradesWithoutInventingANumber() {
        val selected = selectPhotoAltitude(
            exifAltitudeMeters = null,
            elevationLookupMeters = null,
            hasCoordinates = true,
        )
        assertEquals(AltitudeProvenance.MISSING, selected.provenance)
        assertNull(selected.meters)
        val nonsense = selectPhotoAltitude(Double.NaN, Double.NaN, hasCoordinates = true)
        assertEquals(AltitudeProvenance.MISSING, nonsense.provenance)
    }

    @Test
    fun lookupIsIgnoredWhenThePhotoHasNoCoordinates() {
        val selected = selectPhotoAltitude(
            exifAltitudeMeters = null,
            elevationLookupMeters = 8848.0,
            hasCoordinates = false,
        )
        assertEquals(AltitudeProvenance.MISSING, selected.provenance)
        assertNull(selected.meters)
    }

    @Test
    fun noticesDistinguishHiddenLocationFromAPhotoThatHasNone() {
        assertEquals(
            ImportNoticeKind.NO_LOCATION,
            importNoticeKind(hasCoordinates = false, altitude = null, locationRedacted = false),
        )
        assertEquals(
            ImportNoticeKind.LOCATION_HIDDEN,
            importNoticeKind(hasCoordinates = false, altitude = null, locationRedacted = true),
        )
        assertEquals(
            ImportNoticeKind.NONE,
            importNoticeKind(hasCoordinates = true, altitude = null, locationRedacted = false),
        )
        assertEquals(
            ImportNoticeKind.COMPUTED_ALTITUDE,
            importNoticeKind(
                hasCoordinates = true,
                altitude = SelectedAltitude(10.0, AltitudeProvenance.COMPUTED),
                locationRedacted = false,
            ),
        )
        assertEquals(
            ImportNoticeKind.ALTITUDE_MISSING,
            importNoticeKind(
                hasCoordinates = true,
                altitude = SelectedAltitude(null, AltitudeProvenance.MISSING),
                locationRedacted = false,
            ),
        )
        assertEquals(
            ImportNoticeKind.NONE,
            importNoticeKind(
                hasCoordinates = true,
                altitude = SelectedAltitude(10.0, AltitudeProvenance.FROM_PHOTO),
                locationRedacted = true,
            ),
        )
    }

    @Test
    fun openMeteoBodyIsParsedAndMalformedBodiesAreEmpty() {
        assertEquals(listOf(3648.0), parseOpenMeteoElevations("""{"elevation":[3648.0]}"""))
        assertEquals(listOf(10.5, -430.0), parseOpenMeteoElevations("""{"elevation":[10.5,-430.0]}"""))
        assertEquals(listOf(null), parseOpenMeteoElevations("""{"elevation":[null]}"""))
        assertNull(parseOpenMeteoElevations("""{"error":true,"reason":"no"}"""))
        assertNull(parseOpenMeteoElevations("not json"))
        val url = openMeteoElevationUrl(listOf(29.65218 to 91.117))
        assertTrue(url.startsWith("https://api.open-meteo.com/v1/elevation?"))
        assertTrue(url.contains("latitude=29.65218"))
        assertTrue(url.contains("longitude=91.11700"))
        assertEquals("29.6522,91.1170", elevationCacheKey(29.65218, 91.117))
    }
}
