package app.altitude.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

class PressureAltitudeTest {
    @Test
    fun seaLevelIsZero() {
        val meters = PressureAltitude.metersFromHpaOrNull(PressureAltitude.STANDARD_PRESSURE_HPA)
        assertEquals(0.0, meters!!, 0.001)
    }

    @Test
    fun roundTripAcrossTibetElevations() {
        listOf(0.0, 1000.0, 3650.0, 5200.0, 8849.0).forEach { meters ->
            val pressure = PressureAltitude.hpaFromMeters(meters)
            val back = PressureAltitude.metersFromHpaOrNull(pressure)
            assertEquals(meters, back!!, 0.05)
        }
    }

    @Test
    fun rejectsPressureOutsideTheTroposphereModel() {
        assertNull(PressureAltitude.metersFromHpaOrNull(10.0))
        assertNull(PressureAltitude.metersFromHpaOrNull(1200.0))
        assertNull(PressureAltitude.metersFromHpaOrNull(Double.NaN))
    }
}

class Egm96GeoidTest {
    private val geoid: Egm96Geoid by lazy {
        Egm96Geoid.decode(File("src/main/assets/egm96-15.bin").readBytes())
    }

    @Test
    fun knownGridNodes() {
        assertEquals(17.160, geoid.undulationMeters(0.0, 0.0), 0.001)
        assertEquals(13.610, geoid.undulationMeters(90.0, 0.0), 0.001)
        assertEquals(-17.210, geoid.undulationMeters(40.0, -105.0), 0.001)
    }

    @Test
    fun lhasaAndLondonAreInThePublishedNeighborhood() {
        assertEquals(-34.645, geoid.undulationMeters(29.65, 91.12), 0.03)
        assertEquals(45.800, geoid.undulationMeters(51.48, 0.0), 0.03)
    }

    @Test
    fun longitudeWrapsAcrossTheAntimeridian() {
        val east = geoid.undulationMeters(10.0, 179.9)
        val west = geoid.undulationMeters(10.0, -179.9)
        assertTrue(kotlin.math.abs(east - west) < 8.0)
    }

    @Test
    fun syntheticBilinearAndWrap() {
        val centimeters = shortArrayOf(
            0, 1000, 2000, 3000,
            0, 1000, 2000, 3000,
        )
        val grid = Egm96Geoid(
            rows = 2,
            cols = 4,
            lat0 = 0.0,
            lon0 = 0.0,
            latStep = 10.0,
            lonStep = 90.0,
            centimeters = centimeters,
        )
        assertEquals(0.0, grid.undulationMeters(0.0, 0.0), 0.0001)
        assertEquals(5.0, grid.undulationMeters(0.0, 45.0), 0.0001)
        assertEquals(15.0, grid.undulationMeters(0.0, 315.0), 0.0001)
        assertEquals(15.0, grid.undulationMeters(0.0, -45.0), 0.0001)
    }
}

class MslResolveTest {
    @Test
    fun platformMslWinsOverTheGeoid() {
        val geoid = GeoidModel { _, _ -> -40.0 }
        val sample = GnssSample(
            latitude = 29.65,
            longitude = 91.12,
            ellipsoidalAltitudeMeters = 3600.0,
            platformMslAltitudeMeters = 3656.0,
            horizontalAccuracyMeters = 4.0,
            verticalAccuracyMeters = 6.0,
            timeMs = 1L,
        )
        assertEquals(3656.0, resolveMsl(sample, geoid)!!, 0.0)
        assertEquals(3640.0, resolveMsl(sample.copy(platformMslAltitudeMeters = null), geoid)!!, 0.0)
        assertNull(resolveMsl(sample.copy(platformMslAltitudeMeters = null), null))
    }
}

class FormatTest {
    @Test
    fun groupsAndConvertsFeet() {
        assertEquals("5,210", groupThousands(5210))
        assertEquals("-12", groupThousands(-12))
        assertEquals("1,000,000", groupThousands(1_000_000))
        assertEquals("5,210 m", formatAltitudeReading(5210.0, AltitudeUnit.METERS))
        assertEquals("17,093 ft", formatAltitudeReading(5210.0, AltitudeUnit.FEET))
    }

    @Test
    fun formatsCoordinatesAndShanghaiTime() {
        assertEquals("29.65200° N", formatCoordinate(29.652, true))
        assertEquals("91.10000° W", formatCoordinate(-91.1, false))
        val zone = ZoneId.of("Asia/Shanghai")
        val millis = ZonedDateTime.of(2026, 10, 22, 16, 42, 8, 0, zone).toInstant().toEpochMilli()
        assertEquals("16:42:08", formatDisplayClock(millis, zone))
        assertEquals("2026.10.22  16:42", formatWatermarkStamp(millis, zone))
    }

    @Test
    fun watermarkOmitsAltitudeUntilItIsMeanSeaLevel() {
        val zone = ZoneId.of("Asia/Shanghai")
        val millis = ZonedDateTime.of(2026, 10, 22, 16, 42, 0, 0, zone).toInstant().toEpochMilli()
        val ready = buildWatermarkContent(
            altitudeMeters = 5210.4,
            absoluteMsl = true,
            unit = AltitudeUnit.METERS,
            latitude = 29.65,
            longitude = 91.12,
            place = "拉萨",
            epochMs = millis,
            zone = zone,
        )
        assertEquals("5,210 m", ready.altitude)
        assertEquals("拉萨", ready.place)
        assertTrue(ready.coordinates!!.contains("29.65000° N"))
        assertTrue(ready.coordinates.contains("91.12000° E"))

        val waiting = buildWatermarkContent(null, false, AltitudeUnit.METERS, null, null, null, millis, zone)
        assertEquals("— —", waiting.altitude)
        assertNull(waiting.coordinates)
    }

    @Test
    fun numbersFollowTheLocale() {
        assertEquals("5,210", formatInteger(5210, Locale.US))
        assertEquals("-12", formatInteger(-12, Locale.US))
        assertEquals("1,000,000", formatInteger(1_000_000, Locale.US))
        assertEquals("29.65200", formatDecimal(29.652, 5, Locale.US))

        val frenchGrouped = formatInteger(5210, Locale.FRANCE)
        assertTrue(frenchGrouped.startsWith("5"))
        assertTrue(frenchGrouped.endsWith("210"))
        val separator = frenchGrouped.removePrefix("5").removeSuffix("210")
        assertEquals(1, separator.length)
        assertTrue(Character.isSpaceChar(separator[0]))
        assertFalse(frenchGrouped.contains(','))
        assertFalse(frenchGrouped.contains('.'))

        val frenchDecimal = formatDecimal(29.652, 5, Locale.FRANCE)
        assertEquals("29,65200", frenchDecimal)

        assertEquals("5,210", formatInteger(5210, Locale.SIMPLIFIED_CHINESE))
        assertEquals("29.65200", formatDecimal(29.652, 5, Locale.SIMPLIFIED_CHINESE))

        val frenchReading = formatAltitudeReading(5210.0, AltitudeUnit.METERS, Locale.FRANCE)
        assertTrue(frenchReading.endsWith(" m"))
        assertTrue(Character.isSpaceChar(frenchReading[1]))
    }
}

class WatermarkMetricsTest {
    @Test
    fun plateExtendsTheStillWithoutChangingItsWidth() {
        assertEquals(162, WatermarkMetrics.plateHeightPx(1000, false))
        assertEquals(210, WatermarkMetrics.plateHeightPx(1000, true))
        val size = WatermarkMetrics.outputSize(3000, 4000, WatermarkStyle.PLATE_LIGHT, false)
        assertEquals(3000, size.width)
        assertEquals(4486, size.height)
        val caption = WatermarkMetrics.outputSize(3000, 4000, WatermarkStyle.CAPTION, false)
        assertEquals(3000, caption.width)
        assertEquals(4000, caption.height)
    }

    @Test
    fun mountAddsAPrintMargin() {
        val size = WatermarkMetrics.outputSize(3000, 4000, WatermarkStyle.MOUNT, false)
        assertEquals(3276, size.width)
        assertEquals(4738, size.height)
    }

    @Test
    fun frameFitsInsideTheBodyAndKeepsThePhotoAspect() {
        val fit = WatermarkMetrics.fitFrame(
            availWidth = 1000f,
            availHeight = 1600f,
            photoAspect = 0.75f,
            style = WatermarkStyle.PLATE_LIGHT,
            hasPlace = false,
        )
        assertTrue(fit.outerWidth <= 1000.5f)
        assertTrue(fit.outerHeight <= 1600.5f)
        assertEquals(0.75, (fit.photoWidth / fit.photoHeight).toDouble(), 0.001)
        assertEquals(162.0, (fit.bottom / fit.photoWidth * 1000.0), 1.0)
    }

    @Test
    fun landscapePlateUsesTheShorterEdge() {
        val size = WatermarkMetrics.outputSize(4000, 3000, WatermarkStyle.PLATE_LIGHT, false)
        assertEquals(4000, size.width)
        assertEquals(3486, size.height)
        val portrait = WatermarkMetrics.outputSize(3000, 4000, WatermarkStyle.PLATE_LIGHT, false)
        assertEquals(size.height - 3000, portrait.height - 4000)
        val mount = WatermarkMetrics.outputSize(4000, 3000, WatermarkStyle.MOUNT, false)
        assertEquals(4276, mount.width)
        assertEquals(3738, mount.height)
        val fit = WatermarkMetrics.fitFrame(
            availWidth = 1600f,
            availHeight = 900f,
            photoAspect = 4f / 3f,
            style = WatermarkStyle.PLATE_LIGHT,
            hasPlace = false,
        )
        assertTrue(fit.outerWidth <= 1600.5f)
        assertTrue(fit.outerHeight <= 900.5f)
        assertEquals(4.0 / 3.0, (fit.photoWidth / fit.photoHeight).toDouble(), 0.001)
        val shortEdge = minOf(fit.photoWidth, fit.photoHeight)
        assertEquals(162.0, (fit.bottom / shortEdge * 1000.0), 1.0)
    }
}

class AltitudeFusionTest {
    @Test
    fun gpsOnlyStartsOnTheFixThenFollowsSmoothly() {
        val fusion = AltitudeFusion()
        val first = fusion.update(FusionInput(0L, 1000.0, 5.0, null))
        assertEquals(1000.0, first.altitudeMslMeters!!, 0.01)
        assertEquals(AltitudeSource.GPS, first.source)
        assertTrue(first.absoluteMsl)
        val second = fusion.update(FusionInput(1_000L, 1020.0, 5.0, null))
        assertEquals(1014.27, second.altitudeMslMeters!!, 0.25)
        assertTrue(second.altitudeMslMeters < 1020.0)
    }

    @Test
    fun barometerWithoutGpsIsNotLabeledMeanSeaLevel() {
        val fusion = AltitudeFusion()
        val pressure = PressureAltitude.hpaFromMeters(3600.0)
        val output = fusion.update(FusionInput(0L, null, null, pressure))
        assertEquals(3600.0, output.altitudeMslMeters!!, 0.05)
        assertEquals(AltitudeSource.PRESSURE_UNCALIBRATED, output.source)
        assertFalse(output.absoluteMsl)
    }

    @Test
    fun firstGpsFixSnapsTheBarometerOffset() {
        val fusion = AltitudeFusion()
        val pressure = PressureAltitude.hpaFromMeters(5000.0)
        val output = fusion.update(FusionInput(0L, 3650.0, 4.0, pressure))
        assertEquals(3650.0, output.altitudeMslMeters!!, 0.05)
        assertEquals(AltitudeSource.FUSED, output.source)
        assertTrue(output.barometerCalibrated)
        assertEquals(-1350.0, output.calibrationOffsetMeters!!, 0.2)
    }

    @Test
    fun barometerClimbIsKeptWhenGpsDoesNotMove() {
        val fusion = AltitudeFusion()
        val base = PressureAltitude.hpaFromMeters(5000.0)
        fusion.update(FusionInput(0L, 5000.0, 4.0, base))
        val climbed = PressureAltitude.hpaFromMeters(5010.0)
        val output = fusion.update(FusionInput(500L, null, null, climbed))
        assertEquals(5009.64, output.altitudeMslMeters!!, 0.2)
        assertEquals(AltitudeSource.FUSED, output.source)
    }

    @Test
    fun gpsBiasCorrectsSlowlyInsideTheGate() {
        val fusion = AltitudeFusion()
        val pressure = PressureAltitude.hpaFromMeters(5000.0)
        fusion.update(FusionInput(0L, 5000.0, 4.0, pressure))
        val output = fusion.update(FusionInput(2_000L, 5020.0, 4.0, pressure))
        assertTrue(output.altitudeMslMeters!! > 5001.5)
        assertTrue(output.altitudeMslMeters < 5006.0)
    }

    @Test
    fun aSingleOutlierDoesNotYankTheReading() {
        val fusion = AltitudeFusion()
        val pressure = PressureAltitude.hpaFromMeters(5000.0)
        fusion.update(FusionInput(0L, 5000.0, 4.0, pressure))
        val output = fusion.update(FusionInput(1_000L, 5080.0, 4.0, pressure))
        assertEquals(5000.0, output.altitudeMslMeters!!, 0.25)
    }

    @Test
    fun aPersistentOutlierReseatsTheOffset() {
        val fusion = AltitudeFusion()
        val pressure = PressureAltitude.hpaFromMeters(5000.0)
        fusion.update(FusionInput(0L, 5000.0, 4.0, pressure))
        fusion.update(FusionInput(1_000L, 5080.0, 4.0, pressure))
        val output = fusion.update(FusionInput(9_000L, 5080.0, 4.0, pressure))
        assertEquals(5080.0, output.altitudeMslMeters!!, 0.3)
    }

    @Test
    fun holdoverFollowsTheBarometerAndWidensAccuracy() {
        val fusion = AltitudeFusion()
        val base = PressureAltitude.hpaFromMeters(5000.0)
        fusion.update(FusionInput(0L, 5000.0, 4.0, base))
        val climbed = PressureAltitude.hpaFromMeters(5010.0)
        val output = fusion.update(FusionInput(10_000L, null, null, climbed))
        assertEquals(AltitudeSource.PRESSURE_HOLDOVER, output.source)
        assertEquals(5010.0, output.altitudeMslMeters!!, 0.2)
        assertEquals(4.75, output.accuracyMeters!!, 0.05)
    }

    @Test
    fun absurdAltitudeIsIgnored() {
        val fusion = AltitudeFusion()
        val output = fusion.update(FusionInput(0L, 20_000.0, 5.0, null))
        assertNull(output.altitudeMslMeters)
        assertEquals(FixQuality.SEARCHING, output.quality)
    }
}
