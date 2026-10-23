import UIKit
import XCTest
@testable import Altitude

final class PhotoImportTests: XCTestCase {
    func testAltitudeAboveSeaLevelKeepsTheMagnitude() {
        XCTAssertEqual(signedGpsAltitudeMeters(3650, altitudeAboveSeaLevel) ?? -1, 3650, accuracy: 0.001)
        XCTAssertEqual(signedGpsAltitudeMeters(3650, nil) ?? -1, 3650, accuracy: 0.001)
        XCTAssertEqual(signedGpsAltitudeMeters(0, nil) ?? -1, 0, accuracy: 0.001)
    }

    func testAltitudeBelowSeaLevelIsNegative() {
        XCTAssertEqual(signedGpsAltitudeMeters(12.5, altitudeBelowSeaLevel) ?? 1, -12.5, accuracy: 0.001)
        XCTAssertEqual(signedGpsAltitudeMeters(0, altitudeBelowSeaLevel) ?? 1, 0, accuracy: 0.001)
    }

    func testMissingAltitudeStaysMissing() {
        XCTAssertNil(signedGpsAltitudeMeters(nil, altitudeBelowSeaLevel))
        XCTAssertNil(signedGpsAltitudeMeters(nil, altitudeAboveSeaLevel))
        XCTAssertNil(signedGpsAltitudeMeters(Double.nan, altitudeAboveSeaLevel))
        XCTAssertNil(signedGpsAltitudeMeters(-1, altitudeAboveSeaLevel))
    }

    func testRationalAltitudeParsesFractions() {
        XCTAssertEqual(rationalToDouble("25/2") ?? -1, 12.5, accuracy: 0.0001)
        XCTAssertEqual(rationalToDouble("3650/1") ?? -1, 3650, accuracy: 0.001)
        XCTAssertNil(rationalToDouble("1/0"))
        XCTAssertNil(rationalToDouble(""))
    }

    func testDateTimeOriginalKeepsTheWallClockAndOffset() {
        let clock = resolveCapturedClock(
            dateTimeOriginal: "2024:06:15 14:30:00",
            dateTimeDigitized: "2024:06:15 06:30:00",
            dateTime: "2020:01:01 00:00:00",
            offsetTimeOriginal: "+08:00",
            offsetTimeDigitized: "-05:00",
            offsetTime: "+01:00",
            gpsDateStamp: "2024:06:15",
            gpsTimeStamp: "06:30:00"
        )
        XCTAssertEqual(clock?.local, WallClock(year: 2024, month: 6, day: 15, hour: 14, minute: 30, second: 0))
        XCTAssertEqual(clock?.offset, ExifOffset.hours(8))
    }

    func testCompactAndNegativeOffsetsParse() {
        XCTAssertEqual(parseExifOffset("+0800"), ExifOffset.hours(8))
        XCTAssertEqual(parseExifOffset("-04:30"), ExifOffset.hoursMinutes(-4, -30))
        XCTAssertEqual(parseExifOffset(" +08 "), ExifOffset.hours(8))
        XCTAssertEqual(parseExifOffset("Z"), ExifOffset.utc)
        XCTAssertNil(parseExifOffset("08:00"))
        XCTAssertNil(parseExifOffset("UTC"))
        XCTAssertNil(parseExifOffset(nil))
    }

    func testMissingOffsetLeavesTheRecordedClockAlone() {
        let clock = resolveCapturedClock(
            dateTimeOriginal: "2024:06:15 14:30:00",
            dateTimeDigitized: nil,
            dateTime: nil,
            offsetTimeOriginal: nil,
            offsetTimeDigitized: nil,
            offsetTime: nil,
            gpsDateStamp: "2024:06:15",
            gpsTimeStamp: "06:30:00"
        )
        XCTAssertEqual(clock?.local, WallClock(year: 2024, month: 6, day: 15, hour: 14, minute: 30, second: 0))
        XCTAssertNil(clock?.offset)
    }

    func testBlankCameraDateIsIgnored() {
        XCTAssertNil(parseExifDateTime("    :  :     :  :  "))
        XCTAssertNil(parseExifDateTime(nil))
        XCTAssertNil(parseExifDateTime(""))
    }

    func testDigitizedTimeUsesItsOwnOffset() {
        let clock = resolveCapturedClock(
            dateTimeOriginal: nil,
            dateTimeDigitized: "2024:01:02 03:04:05",
            dateTime: "1999:01:01 00:00:00",
            offsetTimeOriginal: "+08:00",
            offsetTimeDigitized: "-04:00",
            offsetTime: "+01:00",
            gpsDateStamp: nil,
            gpsTimeStamp: nil
        )
        XCTAssertEqual(clock?.local, WallClock(year: 2024, month: 1, day: 2, hour: 3, minute: 4, second: 5))
        XCTAssertEqual(clock?.offset, ExifOffset.hours(-4))
    }

    func testGpsTimeAloneIsUtc() {
        let clock = resolveCapturedClock(
            dateTimeOriginal: nil,
            dateTimeDigitized: nil,
            dateTime: nil,
            offsetTimeOriginal: nil,
            offsetTimeDigitized: nil,
            offsetTime: nil,
            gpsDateStamp: "2024:06:15",
            gpsTimeStamp: "6:30:00"
        )
        XCTAssertEqual(clock?.local, WallClock(year: 2024, month: 6, day: 15, hour: 6, minute: 30, second: 0))
        XCTAssertEqual(clock?.offset, ExifOffset.utc)
    }

    func testGpsTimeShiftsIntoTheRecordedOffset() {
        let clock = resolveCapturedClock(
            dateTimeOriginal: nil,
            dateTimeDigitized: nil,
            dateTime: nil,
            offsetTimeOriginal: "+08:00",
            offsetTimeDigitized: nil,
            offsetTime: nil,
            gpsDateStamp: "2024:06:15",
            gpsTimeStamp: "6/1,30/1,0/1"
        )
        XCTAssertEqual(clock?.local, WallClock(year: 2024, month: 6, day: 15, hour: 14, minute: 30, second: 0))
        XCTAssertEqual(clock?.offset, ExifOffset.hours(8))
    }

    func testMissingEveryTimeFieldStaysEmpty() {
        XCTAssertNil(resolveCapturedClock(
            dateTimeOriginal: nil,
            dateTimeDigitized: nil,
            dateTime: nil,
            offsetTimeOriginal: "+08:00",
            offsetTimeDigitized: nil,
            offsetTime: nil,
            gpsDateStamp: "2024:06:15",
            gpsTimeStamp: nil
        ))
    }

    func testOffsetAndFileNameUseTheWallClock() {
        let clock = CapturedClock(
            local: WallClock(year: 2024, month: 6, day: 15, hour: 14, minute: 30, second: 0),
            offset: ExifOffset.hours(8)
        )
        XCTAssertEqual(formatExifOffset(clock.offset!), "+08:00")
        XCTAssertEqual(formatExifOffset(.utc), "+00:00")
        XCTAssertEqual(formatExifOffset(ExifOffset.hoursMinutes(-4, -30)), "-04:30")
        XCTAssertEqual(formatExifWallClock(clock.local), "2024:06:15 14:30:00")
        XCTAssertEqual(importDisplayName(clock: clock, fallbackEpochMs: 0, index: 0), "Altitude_20240615_143000.jpg")
        XCTAssertEqual(importDisplayName(clock: clock, fallbackEpochMs: 0, index: 2), "Altitude_20240615_143000_2.jpg")
    }

    func testOrientationTagsMapToTheTransform() {
        XCTAssertEqual(ExifOrientation.fromTag(0), .normal)
        XCTAssertEqual(ExifOrientation.fromTag(1), .normal)
        XCTAssertEqual(ExifOrientation.fromTag(99), .normal)
        XCTAssertEqual(ExifOrientation.normal.transform(), OrientationTransform(clockwiseDegrees: 0, mirrorHorizontal: false))
        XCTAssertEqual(ExifOrientation.flipHorizontal.transform(), OrientationTransform(clockwiseDegrees: 0, mirrorHorizontal: true))
        XCTAssertEqual(ExifOrientation.rotate180.transform(), OrientationTransform(clockwiseDegrees: 180, mirrorHorizontal: false))
        XCTAssertEqual(ExifOrientation.flipVertical.transform(), OrientationTransform(clockwiseDegrees: 180, mirrorHorizontal: true))
        XCTAssertEqual(ExifOrientation.transpose.transform(), OrientationTransform(clockwiseDegrees: 90, mirrorHorizontal: true))
        XCTAssertEqual(ExifOrientation.rotate90.transform(), OrientationTransform(clockwiseDegrees: 90, mirrorHorizontal: false))
        XCTAssertEqual(ExifOrientation.transverse.transform(), OrientationTransform(clockwiseDegrees: 270, mirrorHorizontal: true))
        XCTAssertEqual(ExifOrientation.rotate270.transform(), OrientationTransform(clockwiseDegrees: 270, mirrorHorizontal: false))
        XCTAssertEqual(ExifOrientation.rotate90.tag, 6)
        XCTAssertEqual(ExifOrientation.rotate270.tag, 8)
    }

    func testExifAltitudeWinsOverALookup() {
        let selected = selectPhotoAltitude(exifAltitudeMeters: 5200, elevationLookupMeters: 100, hasCoordinates: true)
        XCTAssertEqual(selected.provenance, .fromPhoto)
        XCTAssertEqual(selected.meters ?? -1, 5200, accuracy: 0.001)
    }

    func testZeroAndNegativeExifValuesAreReal() {
        let sea = selectPhotoAltitude(exifAltitudeMeters: 0, elevationLookupMeters: 40, hasCoordinates: true)
        let below = selectPhotoAltitude(exifAltitudeMeters: -8, elevationLookupMeters: 40, hasCoordinates: true)
        XCTAssertEqual(sea.provenance, .fromPhoto)
        XCTAssertEqual(sea.meters ?? -1, 0, accuracy: 0.001)
        XCTAssertEqual(below.provenance, .fromPhoto)
        XCTAssertEqual(below.meters ?? 0, -8, accuracy: 0.001)
    }

    func testMissingExifUsesTheLookupWhenThePhotoHasCoordinates() {
        let selected = selectPhotoAltitude(exifAltitudeMeters: nil, elevationLookupMeters: 3648.2, hasCoordinates: true)
        XCTAssertEqual(selected.provenance, .computed)
        XCTAssertEqual(selected.meters ?? 0, 3648.2, accuracy: 0.0001)
    }

    func testFailedLookupDegradesWithoutInventingANumber() {
        let selected = selectPhotoAltitude(exifAltitudeMeters: nil, elevationLookupMeters: nil, hasCoordinates: true)
        XCTAssertEqual(selected.provenance, .missing)
        XCTAssertNil(selected.meters)
        let nonsense = selectPhotoAltitude(exifAltitudeMeters: .nan, elevationLookupMeters: .nan, hasCoordinates: true)
        XCTAssertEqual(nonsense.provenance, .missing)
    }

    func testLookupIsIgnoredWhenThePhotoHasNoCoordinates() {
        let selected = selectPhotoAltitude(exifAltitudeMeters: nil, elevationLookupMeters: 8848, hasCoordinates: false)
        XCTAssertEqual(selected.provenance, .missing)
        XCTAssertNil(selected.meters)
    }

    func testNoticesDistinguishHiddenLocationFromAPhotoThatHasNone() {
        XCTAssertEqual(importNoticeKind(hasCoordinates: false, altitude: nil, locationRedacted: false), .noLocation)
        XCTAssertEqual(importNoticeKind(hasCoordinates: false, altitude: nil, locationRedacted: true), .locationHidden)
        XCTAssertEqual(importNoticeKind(hasCoordinates: true, altitude: nil, locationRedacted: false), .none)
        XCTAssertEqual(
            importNoticeKind(hasCoordinates: true, altitude: SelectedAltitude(meters: 10, provenance: .computed), locationRedacted: false),
            .computedAltitude
        )
        XCTAssertEqual(
            importNoticeKind(hasCoordinates: true, altitude: SelectedAltitude(meters: nil, provenance: .missing), locationRedacted: false),
            .altitudeMissing
        )
        XCTAssertEqual(
            importNoticeKind(hasCoordinates: true, altitude: SelectedAltitude(meters: 10, provenance: .fromPhoto), locationRedacted: true),
            .none
        )
    }

    func testOpenMeteoBodyIsParsedAndMalformedBodiesAreEmpty() {
        XCTAssertEqual(numberList(#"{"elevation":[3648.0]}"#), [3648.0])
        XCTAssertEqual(numberList(#"{"elevation":[10.5,-430.0]}"#), [10.5, -430.0])
        XCTAssertEqual(optionalList(#"{"elevation":[null]}"#), [nil] as [Double?])
        XCTAssertNil(parseOpenMeteoElevations(#"{"error":true,"reason":"no"}"#))
        XCTAssertNil(parseOpenMeteoElevations("not json"))
        let url = openMeteoElevationUrl([(latitude: 29.65218, longitude: 91.117)])
        XCTAssertTrue(url.hasPrefix("https://api.open-meteo.com/v1/elevation?"))
        XCTAssertTrue(url.contains("latitude=29.65218"))
        XCTAssertTrue(url.contains("longitude=91.11700"))
        XCTAssertEqual(elevationCacheKey(29.65218, 91.117), "29.6522,91.1170")
    }

    func testStringElevationsAreAccepted() {
        XCTAssertEqual(optionalList(#"{"elevation":["3648.5", null]}"#), [3648.5, nil] as [Double?])
    }

    func testLimitedLibraryKeepsThePickedAssetLocation() {
        let kept = mergePhotoLocation(
            exifLatitude: nil,
            exifLongitude: nil,
            assetLatitude: 29.65218,
            assetLongitude: 91.117,
            assetWasReadable: true
        )
        XCTAssertEqual(kept.latitude ?? 0, 29.65218, accuracy: 0.00001)
        XCTAssertEqual(kept.longitude ?? 0, 91.117, accuracy: 0.00001)
        XCTAssertFalse(kept.redacted)
        XCTAssertEqual(
            importNoticeKind(hasCoordinates: kept.hasCoordinates, altitude: nil, locationRedacted: kept.redacted),
            .none
        )

        let dropped = mergePhotoLocation(
            exifLatitude: nil,
            exifLongitude: nil,
            assetLatitude: nil,
            assetLongitude: nil,
            assetWasReadable: false
        )
        XCTAssertNil(dropped.latitude)
        XCTAssertNil(dropped.longitude)
        XCTAssertTrue(dropped.redacted)
        XCTAssertEqual(
            importNoticeKind(hasCoordinates: dropped.hasCoordinates, altitude: nil, locationRedacted: dropped.redacted),
            .locationHidden
        )
    }

    func testAssetLocationFillsAStrippedFileAndExifWins() {
        let exif = mergePhotoLocation(
            exifLatitude: 29.65,
            exifLongitude: 91.11,
            assetLatitude: 1,
            assetLongitude: 2,
            assetWasReadable: true
        )
        XCTAssertEqual(exif.latitude ?? 0, 29.65, accuracy: 0.0001)
        XCTAssertEqual(exif.longitude ?? 0, 91.11, accuracy: 0.0001)
        XCTAssertFalse(exif.redacted)

        let filled = mergePhotoLocation(
            exifLatitude: nil,
            exifLongitude: nil,
            assetLatitude: 29.65218,
            assetLongitude: 91.117,
            assetWasReadable: true
        )
        XCTAssertEqual(filled.latitude ?? 0, 29.65218, accuracy: 0.00001)
        XCTAssertEqual(filled.longitude ?? 0, 91.117, accuracy: 0.00001)
        XCTAssertFalse(filled.redacted)

        let hidden = mergePhotoLocation(
            exifLatitude: nil,
            exifLongitude: nil,
            assetLatitude: nil,
            assetLongitude: nil,
            assetWasReadable: false
        )
        XCTAssertNil(hidden.latitude)
        XCTAssertTrue(hidden.redacted)

        let absent = mergePhotoLocation(
            exifLatitude: nil,
            exifLongitude: nil,
            assetLatitude: nil,
            assetLongitude: nil,
            assetWasReadable: true
        )
        XCTAssertNil(absent.latitude)
        XCTAssertFalse(absent.redacted)
    }

    func testFullQualityJpegKeepsGpsTimeAndAltitudeRef() throws {
        let photo = solid(width: 40, height: 30, white: 0.4)
        let tags = PhotoJPEG.Tags(
            latitude: 29.65218,
            longitude: 91.117,
            altitudeMeters: -12.5,
            epochMs: 0,
            dateTimeOriginal: "2024:06:15 14:30:00",
            offsetTimeOriginal: "+08:00"
        )
        let data = try XCTUnwrap(PhotoJPEG.data(image: photo, tags: tags))
        let capture = PhotoJPEG.capture(from: data)
        XCTAssertEqual(capture.latitude ?? 0, 29.65218, accuracy: 0.0001)
        XCTAssertEqual(capture.longitude ?? 0, 91.117, accuracy: 0.0001)
        XCTAssertEqual(capture.exifAltitudeMeters ?? 0, -12.5, accuracy: 0.05)
        XCTAssertEqual(capture.clock?.local, WallClock(year: 2024, month: 6, day: 15, hour: 14, minute: 30, second: 0))
        XCTAssertEqual(capture.clock?.offset, ExifOffset.hours(8))
        let page = try XCTUnwrap(PhotoImporter.makePage(
            id: "file",
            data: data,
            assetLatitude: 1,
            assetLongitude: 2,
            assetWasReadable: false
        ))
        XCTAssertEqual(page.capture.latitude ?? 0, 29.65218, accuracy: 0.0001)
        XCTAssertFalse(page.locationRedacted)
        XCTAssertEqual(page.altitude?.provenance, .fromPhoto)
    }

    func testStrippedFileUsesTheAssetIdentifierLocation() throws {
        let photo = solid(width: 24, height: 24, white: 0.2)
        let data = try XCTUnwrap(PhotoJPEG.data(
            image: photo,
            tags: PhotoJPEG.Tags(latitude: nil, longitude: nil, altitudeMeters: nil, epochMs: 0, dateTimeOriginal: "2024:06:15 14:30:00", offsetTimeOriginal: nil)
        ))
        let stripped = PhotoJPEG.capture(from: data)
        XCTAssertNil(stripped.latitude)
        let page = try XCTUnwrap(PhotoImporter.makePage(
            id: "asset",
            data: data,
            assetLatitude: 29.65218,
            assetLongitude: 91.117,
            assetWasReadable: true
        ))
        XCTAssertEqual(page.capture.latitude ?? 0, 29.65218, accuracy: 0.0001)
        XCTAssertEqual(page.capture.longitude ?? 0, 91.117, accuracy: 0.0001)
        XCTAssertFalse(page.locationRedacted)
        XCTAssertNil(page.altitude)
        XCTAssertEqual(page.capture.clock?.local.hour, 14)
        XCTAssertEqual(importSelectionLimit, 12)
        XCTAssertTrue(PhotoImporter.usesSharedPhotoLibrary)
    }

    func testImportWatermarkOmitsMissingValuesAndKeepsTheWallClock() {
        let copy = Copy(language: .english)
        let clock = CapturedClock(
            local: WallClock(year: 2024, month: 6, day: 15, hour: 14, minute: 30, second: 0),
            offset: ExifOffset.hours(8)
        )
        let capture = PhotoCapture(latitude: 29.65218, longitude: 91.117, exifAltitudeMeters: nil, clock: clock, orientation: .normal)
        let computed = copy.importWatermark(
            capture: capture,
            altitude: SelectedAltitude(meters: 3648, provenance: .computed),
            place: "Shigatse",
            showPlace: true,
            unit: .meters
        )
        XCTAssertTrue(computed.altitude.hasPrefix("≈"))
        XCTAssertEqual(computed.time, "2024.06.15  14:30")
        XCTAssertEqual(computed.place, "Shigatse")
        XCTAssertNotNil(computed.coordinates)

        let empty = PhotoCapture(latitude: nil, longitude: nil, exifAltitudeMeters: nil, clock: clock, orientation: .normal)
        let missing = copy.importWatermark(
            capture: empty,
            altitude: SelectedAltitude(meters: nil, provenance: .missing),
            place: "Shigatse",
            showPlace: true,
            unit: .meters
        )
        XCTAssertEqual(missing.altitude, "")
        XCTAssertNil(missing.coordinates)
        XCTAssertFalse(missing.altitude.contains("—"))
        XCTAssertFalse(missing.altitude.contains(copy.text("altitude_unavailable")))
    }

    func testPlateAndMountHonorTheTopAnchor() {
        let photo = solid(width: 200, height: 300, white: 0.45)
        let content = WatermarkContent(altitude: "5,210 m", coordinates: "29.65000° N", place: nil, time: "2024.06.15  14:30", wordmark: "ALTITUDE")
        let top = WatermarkRenderer.render(photo: photo, content: content, style: .plateLight, anchor: .top)
        let bottom = WatermarkRenderer.render(photo: photo, content: content, style: .plateLight, anchor: .bottom)
        XCTAssertGreaterThan(luma(top, x: 10, y: 8), 200)
        XCTAssertLessThan(luma(top, x: 10, y: 48), 180)
        XCTAssertLessThan(luma(bottom, x: 10, y: 8), 180)
        XCTAssertGreaterThan(luma(bottom, x: 10, y: 308), 200)

        let mountTop = WatermarkRenderer.render(photo: photo, content: content, style: .mount, anchor: .top)
        XCTAssertGreaterThan(luma(mountTop, x: 20, y: 8), 200)
        XCTAssertLessThan(luma(mountTop, x: 20, y: 56), 180)
    }

    func testBlankAltitudeIsNotDrawnOnThePlate() {
        let photo = solid(width: 200, height: 300, white: 0.45)
        let blank = WatermarkContent(altitude: "", coordinates: nil, place: nil, time: nil, wordmark: "ALTITUDE")
        let dashed = WatermarkContent(altitude: "— —", coordinates: nil, place: nil, time: nil, wordmark: "ALTITUDE")
        let blankImage = WatermarkRenderer.render(photo: photo, content: blank, style: .plateLight, anchor: .bottom)
        let dashedImage = WatermarkRenderer.render(photo: photo, content: dashed, style: .plateLight, anchor: .bottom)
        let blankDark = darkCount(blankImage, rect: CGRect(x: 140, y: 304, width: 50, height: 24))
        let dashedDark = darkCount(dashedImage, rect: CGRect(x: 140, y: 304, width: 50, height: 24))
        XCTAssertEqual(blankDark, 0)
        XCTAssertGreaterThan(dashedDark, blankDark)
    }

    private func numberList(_ body: String) -> [Double] {
        (parseOpenMeteoElevations(body) ?? []).compactMap { $0 }
    }

    private func optionalList(_ body: String) -> [Double?] {
        parseOpenMeteoElevations(body) ?? []
    }

    private func solid(width: Int, height: Int, white: CGFloat) -> UIImage {
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        format.opaque = true
        return UIGraphicsImageRenderer(size: CGSize(width: width, height: height), format: format).image { context in
            UIColor(white: white, alpha: 1).setFill()
            context.fill(CGRect(x: 0, y: 0, width: width, height: height))
        }
    }

    private func luma(_ image: UIImage, x: Int, y: Int) -> CGFloat {
        let pixel = sample(image, rect: CGRect(x: x, y: y, width: 1, height: 1))
        return (pixel.0 + pixel.1 + pixel.2) / 3
    }

    private func darkCount(_ image: UIImage, rect: CGRect) -> Int {
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        format.opaque = true
        let cropped = UIGraphicsImageRenderer(size: rect.size, format: format).image { _ in
            image.draw(at: CGPoint(x: -rect.minX, y: -rect.minY))
        }
        guard let cg = cropped.cgImage, let data = cg.dataProvider?.data, let pointer = CFDataGetBytePtr(data) else { return 0 }
        let bytesPerPixel = max(cg.bitsPerPixel / 8, 1)
        var dark = 0
        for y in 0..<cg.height {
            for x in 0..<cg.width {
                let offset = y * cg.bytesPerRow + x * bytesPerPixel
                let channels = (0..<min(3, bytesPerPixel)).map { CGFloat(pointer[offset + $0]) }
                let luma = channels.reduce(0, +) / CGFloat(max(channels.count, 1))
                if luma < 80 { dark += 1 }
            }
        }
        return dark
    }

    private func sample(_ image: UIImage, rect: CGRect) -> (CGFloat, CGFloat, CGFloat) {
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        format.opaque = true
        let cropped = UIGraphicsImageRenderer(size: CGSize(width: 1, height: 1), format: format).image { _ in
            image.draw(at: CGPoint(x: -rect.minX, y: -rect.minY))
        }
        guard let cg = cropped.cgImage, let data = cg.dataProvider?.data, let pointer = CFDataGetBytePtr(data) else { return (0, 0, 0) }
        let bytesPerPixel = max(cg.bitsPerPixel / 8, 1)
        let red = CGFloat(pointer[0])
        let green = bytesPerPixel > 1 ? CGFloat(pointer[1]) : red
        let blue = bytesPerPixel > 2 ? CGFloat(pointer[2]) : red
        return (red, green, blue)
    }
}
