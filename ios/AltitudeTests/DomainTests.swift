import XCTest
@testable import Altitude

final class PressureAltitudeTests: XCTestCase {
    func testSeaLevelIsZero() {
        let meters = PressureAltitude.metersFromHpaOrNull(PressureAltitude.standardPressureHpa)
        XCTAssertEqual(meters!, 0, accuracy: 0.001)
    }

    func testRoundTripAcrossTibetElevations() {
        for meters in [0.0, 1000.0, 3650.0, 5200.0, 8849.0] {
            let pressure = PressureAltitude.hpaFromMeters(meters)
            let back = PressureAltitude.metersFromHpaOrNull(pressure)
            XCTAssertEqual(back!, meters, accuracy: 0.05)
        }
    }

    func testRejectsPressureOutsideTheTroposphereModel() {
        XCTAssertNil(PressureAltitude.metersFromHpaOrNull(10))
        XCTAssertNil(PressureAltitude.metersFromHpaOrNull(1200))
        XCTAssertNil(PressureAltitude.metersFromHpaOrNull(.nan))
    }
}

final class Egm96GeoidTests: XCTestCase {
    private lazy var geoid: Egm96Geoid = {
        let data = try! Self.loadGrid()
        return try! Egm96Geoid.decode(data)
    }()

    func testKnownGridNodes() {
        XCTAssertEqual(geoid.undulationMeters(latitudeDegrees: 0, longitudeDegrees: 0), 17.160, accuracy: 0.001)
        XCTAssertEqual(geoid.undulationMeters(latitudeDegrees: 90, longitudeDegrees: 0), 13.610, accuracy: 0.001)
        XCTAssertEqual(geoid.undulationMeters(latitudeDegrees: 40, longitudeDegrees: -105), -17.210, accuracy: 0.001)
    }

    func testLhasaAndLondonAreInThePublishedNeighborhood() {
        XCTAssertEqual(geoid.undulationMeters(latitudeDegrees: 29.65, longitudeDegrees: 91.12), -34.645, accuracy: 0.03)
        XCTAssertEqual(geoid.undulationMeters(latitudeDegrees: 51.48, longitudeDegrees: 0), 45.800, accuracy: 0.03)
    }

    func testLongitudeWrapsAcrossTheAntimeridian() {
        let east = geoid.undulationMeters(latitudeDegrees: 10, longitudeDegrees: 179.9)
        let west = geoid.undulationMeters(latitudeDegrees: 10, longitudeDegrees: -179.9)
        XCTAssertLessThan(abs(east - west), 8)
    }

    func testSyntheticBilinearAndWrap() {
        let centimeters: [Int16] = [0, 1000, 2000, 3000, 0, 1000, 2000, 3000]
        let grid = Egm96Geoid(rows: 2, cols: 4, lat0: 0, lon0: 0, latStep: 10, lonStep: 90, centimeters: centimeters)
        XCTAssertEqual(grid.undulationMeters(latitudeDegrees: 0, longitudeDegrees: 0), 0, accuracy: 0.0001)
        XCTAssertEqual(grid.undulationMeters(latitudeDegrees: 0, longitudeDegrees: 45), 5, accuracy: 0.0001)
        XCTAssertEqual(grid.undulationMeters(latitudeDegrees: 0, longitudeDegrees: 315), 15, accuracy: 0.0001)
        XCTAssertEqual(grid.undulationMeters(latitudeDegrees: 0, longitudeDegrees: -45), 15, accuracy: 0.0001)
    }

    private static func loadGrid() throws -> Data {
        if let url = Bundle.main.url(forResource: "egm96-15", withExtension: "bin") {
            return try Data(contentsOf: url)
        }
        let bundle = Bundle(for: Egm96GeoidTests.self)
        if let url = bundle.url(forResource: "egm96-15", withExtension: "bin") {
            return try Data(contentsOf: url)
        }
        throw GeoidError.truncated
    }
}

final class MslResolveTests: XCTestCase {
    func testPlatformMslWinsOverTheGeoid() {
        let geoid = ConstantGeoid(value: -40)
        let sample = GnssSample(
            latitude: 29.65,
            longitude: 91.12,
            ellipsoidalAltitudeMeters: 3600,
            platformMslAltitudeMeters: 3656,
            horizontalAccuracyMeters: 4,
            verticalAccuracyMeters: 6,
            timeMs: 1
        )
        XCTAssertEqual(resolveMsl(sample, geoid)!, 3656, accuracy: 0)
        var without = sample
        without.platformMslAltitudeMeters = nil
        XCTAssertEqual(resolveMsl(without, geoid)!, 3640, accuracy: 0)
        XCTAssertNil(resolveMsl(without, nil))
    }
}

private struct ConstantGeoid: GeoidModel {
    var value: Double
    func undulationMeters(latitudeDegrees: Double, longitudeDegrees: Double) -> Double { value }
}

final class FormatTests: XCTestCase {
    func testGroupsAndConvertsFeet() {
        XCTAssertEqual(groupThousands(5210), "5,210")
        XCTAssertEqual(groupThousands(-12), "-12")
        XCTAssertEqual(groupThousands(1_000_000), "1,000,000")
        XCTAssertEqual(formatAltitudeReading(5210, .meters), "5,210 m")
        XCTAssertEqual(formatAltitudeReading(5210, .feet), "17,093 ft")
    }

    func testFormatsCoordinatesAndShanghaiTime() {
        XCTAssertEqual(formatCoordinate(29.652, latitude: true), "29.65200° N")
        XCTAssertEqual(formatCoordinate(-91.1, latitude: false), "91.10000° W")
        let zone = TimeZone(identifier: "Asia/Shanghai")!
        let millis = epochMillis(year: 2026, month: 10, day: 22, hour: 16, minute: 42, second: 8, zone: zone)
        XCTAssertEqual(formatDisplayClock(millis, zone: zone), "16:42:08")
        XCTAssertEqual(formatWatermarkStamp(millis, zone: zone), "2026.10.22  16:42")
    }

    func testWatermarkOmitsAltitudeUntilItIsMeanSeaLevel() {
        let zone = TimeZone(identifier: "Asia/Shanghai")!
        let millis = epochMillis(year: 2026, month: 10, day: 22, hour: 16, minute: 42, second: 0, zone: zone)
        let ready = buildWatermarkContent(
            altitudeMeters: 5210.4,
            absoluteMsl: true,
            unit: .meters,
            latitude: 29.65,
            longitude: 91.12,
            place: "拉萨",
            epochMs: millis,
            zone: zone
        )
        XCTAssertEqual(ready.altitude, "5,210 m")
        XCTAssertEqual(ready.place, "拉萨")
        XCTAssertTrue(ready.coordinates!.contains("29.65000° N"))
        XCTAssertTrue(ready.coordinates!.contains("91.12000° E"))

        let waiting = buildWatermarkContent(
            altitudeMeters: nil,
            absoluteMsl: false,
            unit: .meters,
            latitude: nil,
            longitude: nil,
            place: nil,
            epochMs: millis,
            zone: zone
        )
        XCTAssertEqual(waiting.altitude, "— —")
        XCTAssertNil(waiting.coordinates)
    }

    func testNumbersFollowTheLocale() {
        XCTAssertEqual(formatInteger(5210, Locale(identifier: "en_US")), "5,210")
        XCTAssertEqual(formatInteger(-12, Locale(identifier: "en_US")), "-12")
        XCTAssertEqual(formatInteger(1_000_000, Locale(identifier: "en_US")), "1,000,000")
        XCTAssertEqual(formatDecimal(29.652, fractionDigits: 5, Locale(identifier: "en_US")), "29.65200")

        let frenchGrouped = formatInteger(5210, Locale(identifier: "fr_FR"))
        XCTAssertTrue(frenchGrouped.hasPrefix("5"))
        XCTAssertTrue(frenchGrouped.hasSuffix("210"))
        let separator = frenchGrouped.dropFirst().dropLast(3)
        XCTAssertEqual(separator.count, 1)
        XCTAssertTrue(separator.allSatisfy(isUnicodeSpace))
        XCTAssertFalse(frenchGrouped.contains(","))
        XCTAssertFalse(frenchGrouped.contains("."))

        XCTAssertEqual(formatDecimal(29.652, fractionDigits: 5, Locale(identifier: "fr_FR")), "29,65200")
        XCTAssertEqual(formatInteger(5210, Locale(identifier: "zh_CN")), "5,210")
        XCTAssertEqual(formatDecimal(29.652, fractionDigits: 5, Locale(identifier: "zh_CN")), "29.65200")

        let frenchReading = formatAltitudeReading(5210, .meters, Locale(identifier: "fr_FR"))
        XCTAssertTrue(frenchReading.hasSuffix(" m"))
        let index = frenchReading.index(frenchReading.startIndex, offsetBy: 1)
        XCTAssertTrue(isUnicodeSpace(frenchReading[index]))
    }
}

func isUnicodeSpace(_ character: Character) -> Bool {
    character.unicodeScalars.allSatisfy { CharacterSet.whitespaces.contains($0) }
}

final class WatermarkMetricsTests: XCTestCase {
    func testPlateExtendsTheStillWithoutChangingItsWidth() {
        XCTAssertEqual(WatermarkMetrics.plateHeightPx(photoWidth: 1000, hasPlace: false), 162)
        XCTAssertEqual(WatermarkMetrics.plateHeightPx(photoWidth: 1000, hasPlace: true), 210)
        let size = WatermarkMetrics.outputSize(photoWidth: 3000, photoHeight: 4000, style: .plateLight, hasPlace: false)
        XCTAssertEqual(size.width, 3000)
        XCTAssertEqual(size.height, 4486)
        let caption = WatermarkMetrics.outputSize(photoWidth: 3000, photoHeight: 4000, style: .caption, hasPlace: false)
        XCTAssertEqual(caption.width, 3000)
        XCTAssertEqual(caption.height, 4000)
    }

    func testMountAddsAPrintMargin() {
        let size = WatermarkMetrics.outputSize(photoWidth: 3000, photoHeight: 4000, style: .mount, hasPlace: false)
        XCTAssertEqual(size.width, 3276)
        XCTAssertEqual(size.height, 4738)
    }

    func testFrameFitsInsideTheBodyAndKeepsThePhotoAspect() {
        let fit = WatermarkMetrics.fitFrame(
            availWidth: 1000,
            availHeight: 1600,
            photoAspect: 0.75,
            style: .plateLight,
            hasPlace: false
        )
        XCTAssertLessThanOrEqual(fit.outerWidth, 1000.5)
        XCTAssertLessThanOrEqual(fit.outerHeight, 1600.5)
        XCTAssertEqual(Double(fit.photoWidth / fit.photoHeight), 0.75, accuracy: 0.001)
        XCTAssertEqual(Double(fit.bottom / fit.photoWidth * 1000), 162, accuracy: 1)
    }

    func testLandscapePlateUsesTheShorterEdge() {
        let size = WatermarkMetrics.outputSize(photoWidth: 4000, photoHeight: 3000, style: .plateLight, hasPlace: false)
        XCTAssertEqual(size.width, 4000)
        XCTAssertEqual(size.height, 3486)
        let portrait = WatermarkMetrics.outputSize(photoWidth: 3000, photoHeight: 4000, style: .plateLight, hasPlace: false)
        XCTAssertEqual(size.height - 3000, portrait.height - 4000)
        let mount = WatermarkMetrics.outputSize(photoWidth: 4000, photoHeight: 3000, style: .mount, hasPlace: false)
        XCTAssertEqual(mount.width, 4276)
        XCTAssertEqual(mount.height, 3738)
        let fit = WatermarkMetrics.fitFrame(
            availWidth: 1600,
            availHeight: 900,
            photoAspect: 4 / 3,
            style: .plateLight,
            hasPlace: false
        )
        XCTAssertLessThanOrEqual(fit.outerWidth, 1600.5)
        XCTAssertLessThanOrEqual(fit.outerHeight, 900.5)
        XCTAssertEqual(Double(fit.photoWidth / fit.photoHeight), 4.0 / 3.0, accuracy: 0.001)
        let shortEdge = min(fit.photoWidth, fit.photoHeight)
        XCTAssertEqual(Double(fit.bottom / shortEdge * 1000), 162, accuracy: 1)
    }
}

final class AltitudeFusionTests: XCTestCase {
    func testGpsOnlyStartsOnTheFixThenFollowsSmoothly() {
        let fusion = AltitudeFusion()
        let first = fusion.update(FusionInput(timestampMs: 0, gpsMslMeters: 1000, gpsVerticalAccuracyMeters: 5, pressureHpa: nil))
        XCTAssertEqual(first.altitudeMslMeters!, 1000, accuracy: 0.01)
        XCTAssertEqual(first.source, .gps)
        XCTAssertTrue(first.absoluteMsl)
        let second = fusion.update(FusionInput(timestampMs: 1_000, gpsMslMeters: 1020, gpsVerticalAccuracyMeters: 5, pressureHpa: nil))
        XCTAssertEqual(second.altitudeMslMeters!, 1014.27, accuracy: 0.25)
        XCTAssertLessThan(second.altitudeMslMeters!, 1020)
    }

    func testBarometerWithoutGpsIsNotLabeledMeanSeaLevel() {
        let fusion = AltitudeFusion()
        let pressure = PressureAltitude.hpaFromMeters(3600)
        let output = fusion.update(FusionInput(timestampMs: 0, gpsMslMeters: nil, gpsVerticalAccuracyMeters: nil, pressureHpa: pressure))
        XCTAssertEqual(output.altitudeMslMeters!, 3600, accuracy: 0.05)
        XCTAssertEqual(output.source, .pressureUncalibrated)
        XCTAssertFalse(output.absoluteMsl)
    }

    func testFirstGpsFixSnapsTheBarometerOffset() {
        let fusion = AltitudeFusion()
        let pressure = PressureAltitude.hpaFromMeters(5000)
        let output = fusion.update(FusionInput(timestampMs: 0, gpsMslMeters: 3650, gpsVerticalAccuracyMeters: 4, pressureHpa: pressure))
        XCTAssertEqual(output.altitudeMslMeters!, 3650, accuracy: 0.05)
        XCTAssertEqual(output.source, .fused)
        XCTAssertTrue(output.barometerCalibrated)
        XCTAssertEqual(output.calibrationOffsetMeters!, -1350, accuracy: 0.2)
    }

    func testBarometerClimbIsKeptWhenGpsDoesNotMove() {
        let fusion = AltitudeFusion()
        let base = PressureAltitude.hpaFromMeters(5000)
        _ = fusion.update(FusionInput(timestampMs: 0, gpsMslMeters: 5000, gpsVerticalAccuracyMeters: 4, pressureHpa: base))
        let climbed = PressureAltitude.hpaFromMeters(5010)
        let output = fusion.update(FusionInput(timestampMs: 500, gpsMslMeters: nil, gpsVerticalAccuracyMeters: nil, pressureHpa: climbed))
        XCTAssertEqual(output.altitudeMslMeters!, 5009.64, accuracy: 0.2)
        XCTAssertEqual(output.source, .fused)
    }

    func testGpsBiasCorrectsSlowlyInsideTheGate() {
        let fusion = AltitudeFusion()
        let pressure = PressureAltitude.hpaFromMeters(5000)
        _ = fusion.update(FusionInput(timestampMs: 0, gpsMslMeters: 5000, gpsVerticalAccuracyMeters: 4, pressureHpa: pressure))
        let output = fusion.update(FusionInput(timestampMs: 2_000, gpsMslMeters: 5020, gpsVerticalAccuracyMeters: 4, pressureHpa: pressure))
        XCTAssertGreaterThan(output.altitudeMslMeters!, 5001.5)
        XCTAssertLessThan(output.altitudeMslMeters!, 5006)
    }

    func testASingleOutlierDoesNotYankTheReading() {
        let fusion = AltitudeFusion()
        let pressure = PressureAltitude.hpaFromMeters(5000)
        _ = fusion.update(FusionInput(timestampMs: 0, gpsMslMeters: 5000, gpsVerticalAccuracyMeters: 4, pressureHpa: pressure))
        let output = fusion.update(FusionInput(timestampMs: 1_000, gpsMslMeters: 5080, gpsVerticalAccuracyMeters: 4, pressureHpa: pressure))
        XCTAssertEqual(output.altitudeMslMeters!, 5000, accuracy: 0.25)
    }

    func testAPersistentOutlierReseatsTheOffset() {
        let fusion = AltitudeFusion()
        let pressure = PressureAltitude.hpaFromMeters(5000)
        _ = fusion.update(FusionInput(timestampMs: 0, gpsMslMeters: 5000, gpsVerticalAccuracyMeters: 4, pressureHpa: pressure))
        _ = fusion.update(FusionInput(timestampMs: 1_000, gpsMslMeters: 5080, gpsVerticalAccuracyMeters: 4, pressureHpa: pressure))
        let output = fusion.update(FusionInput(timestampMs: 9_000, gpsMslMeters: 5080, gpsVerticalAccuracyMeters: 4, pressureHpa: pressure))
        XCTAssertEqual(output.altitudeMslMeters!, 5080, accuracy: 0.3)
    }

    func testHoldoverFollowsTheBarometerAndWidensAccuracy() {
        let fusion = AltitudeFusion()
        let base = PressureAltitude.hpaFromMeters(5000)
        _ = fusion.update(FusionInput(timestampMs: 0, gpsMslMeters: 5000, gpsVerticalAccuracyMeters: 4, pressureHpa: base))
        let climbed = PressureAltitude.hpaFromMeters(5010)
        let output = fusion.update(FusionInput(timestampMs: 10_000, gpsMslMeters: nil, gpsVerticalAccuracyMeters: nil, pressureHpa: climbed))
        XCTAssertEqual(output.source, .pressureHoldover)
        XCTAssertEqual(output.altitudeMslMeters!, 5010, accuracy: 0.2)
        XCTAssertEqual(output.accuracyMeters!, 4.75, accuracy: 0.05)
    }

    func testAbsurdAltitudeIsIgnored() {
        let fusion = AltitudeFusion()
        let output = fusion.update(FusionInput(timestampMs: 0, gpsMslMeters: 20_000, gpsVerticalAccuracyMeters: 5, pressureHpa: nil))
        XCTAssertNil(output.altitudeMslMeters)
        XCTAssertEqual(output.quality, .searching)
    }
}

final class FixClockTests: XCTestCase {
    func testElapsedRealtimeBeatsAWallClockThatIsAhead() {
        let age = FixClock.ageMillis(elapsedRealtimeMs: 10_000, wallTimeMs: 9_999_000, nowElapsedMs: 10_400, nowWallMs: 5_000)
        XCTAssertEqual(age, 400)
        XCTAssertTrue(FixClock.accept(age, FixClock.fusionWindowMs))
    }

    func testSmallClockSkewStillCountsAsFresh() {
        let age = FixClock.ageMillis(elapsedRealtimeMs: 0, wallTimeMs: 20_000, nowElapsedMs: 0, nowWallMs: 19_000)
        XCTAssertEqual(age, -1_000)
        XCTAssertTrue(FixClock.accept(age, FixClock.fusionWindowMs))
    }

    func testStaleFixIsRejectedAndMissingTimestampIsKept() {
        XCTAssertFalse(FixClock.accept(FixClock.fusionWindowMs + 1, FixClock.fusionWindowMs))
        XCTAssertTrue(FixClock.accept(nil, FixClock.fusionWindowMs))
        XCTAssertTrue(FixClock.accept(FixClock.coordinateWindowMs, FixClock.coordinateWindowMs))
    }
}

final class LocationFeedsTests: XCTestCase {
    func testPlayServicesDoesNotReplaceSatelliteFixes() {
        let feeds = activeLocationFeeds(playServicesAvailable: true, precisePermission: true)
        XCTAssertTrue(feeds.contains(.satellite))
        XCTAssertTrue(feeds.contains(.fused))
        XCTAssertTrue(feeds.contains(.network))
    }

    func testApproximatePermissionSkipsSatelliteAndStillUsesNetwork() {
        let feeds = activeLocationFeeds(playServicesAvailable: false, precisePermission: false)
        XCTAssertFalse(feeds.contains(.satellite))
        XCTAssertFalse(feeds.contains(.fused))
        XCTAssertTrue(feeds.contains(.network))
    }
}

final class AlbumQueryTests: XCTestCase {
    func testModernQueryMatchesTheAltitudeAlbumAndSkipsPendingRows() {
        let (clause, args) = AlbumQuery.selection(29)
        XCTAssertTrue(clause.contains("relative_path"))
        XCTAssertTrue(clause.contains("is_pending"))
        XCTAssertEqual(args, ["%Pictures/Altitude%"])
    }

    func testLegacyQueryMatchesThePublicPicturesPath() {
        let (clause, args) = AlbumQuery.selection(28)
        XCTAssertTrue(clause.contains("_data"))
        XCTAssertEqual(args, ["%/Pictures/Altitude/%"])
    }
}
