import UIKit
import XCTest
@testable import Altitude

final class WatermarkRenderTests: XCTestCase {
    func testPlateJpegMatchesTheMetricAndWritesGps() throws {
        let photo = solid(width: 3000, height: 4000)
        let content = WatermarkContent(
            altitude: "5,210 m",
            coordinates: "29.65218° N   91.11700° E",
            place: "Shigatse",
            time: "2026.10.22  16:42",
            wordmark: "ALTITUDE"
        )
        let rendered = WatermarkRenderer.render(photo: photo, content: content, style: .plateLight, anchor: .bottom)
        let expected = WatermarkMetrics.outputSize(photoWidth: 3000, photoHeight: 4000, style: .plateLight, hasPlace: true)
        XCTAssertEqual(expected, PixelSize(width: 3000, height: 4630))
        XCTAssertEqual(rendered.size.width, CGFloat(expected.width), accuracy: 0.5)
        XCTAssertEqual(rendered.size.height, CGFloat(expected.height), accuracy: 0.5)
        let data = try XCTUnwrap(PhotoJPEG.data(
            image: rendered,
            tags: PhotoJPEG.Tags(latitude: 29.65218, longitude: 91.117, altitudeMeters: 5210, epochMs: 1_761_129_728_000)
        ))
        XCTAssertFalse(data.isEmpty)
        let tags = PhotoJPEG.read(data)
        XCTAssertEqual(tags.software, "Altitude", tags.dump)
        XCTAssertEqual(try XCTUnwrap(tags.latitude), 29.65218, accuracy: 0.0001)
        XCTAssertEqual(try XCTUnwrap(tags.longitude), 91.117, accuracy: 0.0001)
        XCTAssertEqual(try XCTUnwrap(tags.altitude), 5210, accuracy: 0.05)
    }

    func testAltitudeTagIsOmittedWhenTheReadingIsNotSeaLevel() throws {
        let content = WatermarkContent(
            altitude: "— —",
            coordinates: "29.65000° N   91.12000° E",
            place: nil,
            time: nil,
            wordmark: "ALTITUDE"
        )
        XCTAssertEqual(content.altitude, "— —")
        let rendered = WatermarkRenderer.render(photo: solid(width: 80, height: 80), content: content, style: .caption, anchor: .bottom)
        let data = try XCTUnwrap(PhotoJPEG.data(
            image: rendered,
            tags: PhotoJPEG.Tags(latitude: 29.65, longitude: 91.12, altitudeMeters: nil, epochMs: 0)
        ))
        let tags = PhotoJPEG.read(data)
        XCTAssertNil(tags.altitude, tags.dump)
        XCTAssertEqual(tags.software, "Altitude", tags.dump)
        XCTAssertNotNil(tags.latitude)
        XCTAssertNotNil(tags.longitude)
    }

    func testPlaceRequestGateMatchesTheOnePerSecondRule() {
        XCTAssertEqual(millisUntilNextPlaceRequest(nowMs: 1_000, previousStartedMs: 0), 0)
        XCTAssertEqual(millisUntilNextPlaceRequest(nowMs: 1_200, previousStartedMs: 1_000), 800)
    }

    private func solid(width: Int, height: Int) -> UIImage {
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        format.opaque = true
        return UIGraphicsImageRenderer(size: CGSize(width: width, height: height), format: format).image { context in
            UIColor(white: 0.45, alpha: 1).setFill()
            context.fill(CGRect(x: 0, y: 0, width: width, height: height))
        }
    }
}
