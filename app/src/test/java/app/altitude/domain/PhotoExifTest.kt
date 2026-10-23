package app.altitude.domain

import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhotoExifTest {
    @Test
    fun altitudeAboveSeaLevelKeepsTheMagnitude() {
        assertEquals(3650.0, signedGpsAltitudeMeters(3650.0, ALTITUDE_ABOVE_SEA_LEVEL)!!, 0.0)
        assertEquals(3650.0, signedGpsAltitudeMeters(3650.0, null)!!, 0.0)
        assertEquals(0.0, signedGpsAltitudeMeters(0.0, null)!!, 0.0)
    }

    @Test
    fun altitudeBelowSeaLevelIsNegative() {
        assertEquals(-12.5, signedGpsAltitudeMeters(12.5, ALTITUDE_BELOW_SEA_LEVEL)!!, 0.0)
        assertEquals(0.0, signedGpsAltitudeMeters(0.0, ALTITUDE_BELOW_SEA_LEVEL)!!, 0.0)
    }

    @Test
    fun missingAltitudeStaysMissing() {
        assertNull(signedGpsAltitudeMeters(null, ALTITUDE_BELOW_SEA_LEVEL))
        assertNull(signedGpsAltitudeMeters(null, ALTITUDE_ABOVE_SEA_LEVEL))
        assertNull(signedGpsAltitudeMeters(Double.NaN, ALTITUDE_ABOVE_SEA_LEVEL))
        assertNull(signedGpsAltitudeMeters(-1.0, ALTITUDE_ABOVE_SEA_LEVEL))
    }

    @Test
    fun rationalAltitudeParsesFractions() {
        assertEquals(12.5, rationalToDouble("25/2")!!, 0.0001)
        assertEquals(3650.0, rationalToDouble("3650/1")!!, 0.0)
        assertNull(rationalToDouble("1/0"))
        assertNull(rationalToDouble(""))
    }

    @Test
    fun dateTimeOriginalKeepsTheWallClockAndOffset() {
        val clock = resolveCapturedClock(
            dateTimeOriginal = "2024:06:15 14:30:00",
            dateTimeDigitized = "2024:06:15 06:30:00",
            dateTime = "2020:01:01 00:00:00",
            offsetTimeOriginal = "+08:00",
            offsetTimeDigitized = "-05:00",
            offsetTime = "+01:00",
            gpsDateStamp = "2024:06:15",
            gpsTimeStamp = "06:30:00",
        )
        assertEquals(LocalDateTime.of(2024, 6, 15, 14, 30), clock!!.local)
        assertEquals(ZoneOffset.ofHours(8), clock.offset)
    }

    @Test
    fun compactAndNegativeOffsetsParse() {
        assertEquals(ZoneOffset.ofHours(8), parseExifOffset("+0800"))
        assertEquals(ZoneOffset.ofHoursMinutes(-4, -30), parseExifOffset("-04:30"))
        assertEquals(ZoneOffset.ofHours(8), parseExifOffset(" +08 "))
        assertEquals(ZoneOffset.UTC, parseExifOffset("Z"))
        assertNull(parseExifOffset("08:00"))
        assertNull(parseExifOffset("UTC"))
        assertNull(parseExifOffset(null))
    }

    @Test
    fun missingOffsetLeavesTheRecordedClockAlone() {
        val clock = resolveCapturedClock(
            dateTimeOriginal = "2024:06:15 14:30:00",
            dateTimeDigitized = null,
            dateTime = null,
            offsetTimeOriginal = null,
            offsetTimeDigitized = null,
            offsetTime = null,
            gpsDateStamp = "2024:06:15",
            gpsTimeStamp = "06:30:00",
        )
        assertEquals(LocalDateTime.of(2024, 6, 15, 14, 30), clock!!.local)
        assertNull(clock.offset)
    }

    @Test
    fun blankCameraDateIsIgnored() {
        assertNull(parseExifDateTime("    :  :     :  :  "))
        assertNull(parseExifDateTime(null))
        assertNull(parseExifDateTime(""))
    }

    @Test
    fun digitizedTimeUsesItsOwnOffset() {
        val clock = resolveCapturedClock(
            dateTimeOriginal = null,
            dateTimeDigitized = "2024:01:02 03:04:05",
            dateTime = "1999:01:01 00:00:00",
            offsetTimeOriginal = "+08:00",
            offsetTimeDigitized = "-04:00",
            offsetTime = "+01:00",
            gpsDateStamp = null,
            gpsTimeStamp = null,
        )
        assertEquals(LocalDateTime.of(2024, 1, 2, 3, 4, 5), clock!!.local)
        assertEquals(ZoneOffset.ofHours(-4), clock.offset)
    }

    @Test
    fun gpsTimeAloneIsUtc() {
        val clock = resolveCapturedClock(
            dateTimeOriginal = null,
            dateTimeDigitized = null,
            dateTime = null,
            offsetTimeOriginal = null,
            offsetTimeDigitized = null,
            offsetTime = null,
            gpsDateStamp = "2024:06:15",
            gpsTimeStamp = "6:30:00",
        )
        assertEquals(LocalDateTime.of(2024, 6, 15, 6, 30), clock!!.local)
        assertEquals(ZoneOffset.UTC, clock.offset)
    }

    @Test
    fun gpsTimeShiftsIntoTheRecordedOffset() {
        val clock = resolveCapturedClock(
            dateTimeOriginal = null,
            dateTimeDigitized = null,
            dateTime = null,
            offsetTimeOriginal = "+08:00",
            offsetTimeDigitized = null,
            offsetTime = null,
            gpsDateStamp = "2024:06:15",
            gpsTimeStamp = "6/1,30/1,0/1",
        )
        assertEquals(LocalDateTime.of(2024, 6, 15, 14, 30), clock!!.local)
        assertEquals(ZoneOffset.ofHours(8), clock.offset)
    }

    @Test
    fun missingEveryTimeFieldStaysEmpty() {
        assertNull(
            resolveCapturedClock(
                dateTimeOriginal = null,
                dateTimeDigitized = null,
                dateTime = null,
                offsetTimeOriginal = "+08:00",
                offsetTimeDigitized = null,
                offsetTime = null,
                gpsDateStamp = "2024:06:15",
                gpsTimeStamp = null,
            ),
        )
    }

    @Test
    fun offsetAndFileNameUseTheWallClock() {
        val clock = CapturedClock(LocalDateTime.of(2024, 6, 15, 14, 30), ZoneOffset.ofHours(8))
        assertEquals("+08:00", formatExifOffset(clock.offset!!))
        assertEquals("+00:00", formatExifOffset(ZoneOffset.UTC))
        assertEquals("-04:30", formatExifOffset(ZoneOffset.ofHoursMinutes(-4, -30)))
        assertEquals("2024:06:15 14:30:00", formatExifWallClock(clock.local))
        assertEquals("Altitude_20240615_143000.jpg", importDisplayName(clock, fallbackEpochMs = 0, index = 0))
        assertEquals("Altitude_20240615_143000_2.jpg", importDisplayName(clock, fallbackEpochMs = 0, index = 2))
    }

    @Test
    fun orientationTagsMapToTheAndroidTransform() {
        assertEquals(ExifOrientation.NORMAL, ExifOrientation.fromTag(0))
        assertEquals(ExifOrientation.NORMAL, ExifOrientation.fromTag(1))
        assertEquals(ExifOrientation.NORMAL, ExifOrientation.fromTag(99))
        assertEquals(OrientationTransform(0, false), ExifOrientation.NORMAL.transform())
        assertEquals(OrientationTransform(0, true), ExifOrientation.FLIP_HORIZONTAL.transform())
        assertEquals(OrientationTransform(180, false), ExifOrientation.ROTATE_180.transform())
        assertEquals(OrientationTransform(180, true), ExifOrientation.FLIP_VERTICAL.transform())
        assertEquals(OrientationTransform(90, true), ExifOrientation.TRANSPOSE.transform())
        assertEquals(OrientationTransform(90, false), ExifOrientation.ROTATE_90.transform())
        assertEquals(OrientationTransform(270, true), ExifOrientation.TRANSVERSE.transform())
        assertEquals(OrientationTransform(270, false), ExifOrientation.ROTATE_270.transform())
        assertEquals(6, ExifOrientation.ROTATE_90.tag)
        assertEquals(8, ExifOrientation.ROTATE_270.tag)
    }
}
