import XCTest
@testable import Altitude

final class CameraCaptureTests: XCTestCase {
    func testOrientationSnapsToQuartersWithHysteresis() {
        XCTAssertEqual(CaptureOrientation.snapDegrees(CaptureOrientation.unknown, 10), 0)
        XCTAssertEqual(CaptureOrientation.snapDegrees(CaptureOrientation.unknown, 80), 90)
        XCTAssertEqual(CaptureOrientation.snapDegrees(0, 59), 0)
        XCTAssertEqual(CaptureOrientation.snapDegrees(0, 60), 90)
        XCTAssertEqual(CaptureOrientation.snapDegrees(90, 31), 90)
        XCTAssertEqual(CaptureOrientation.snapDegrees(90, 30), 0)
        XCTAssertEqual(CaptureOrientation.snapDegrees(0, 350), 0)
        XCTAssertEqual(CaptureOrientation.snapDegrees(0, 300), 270)
        XCTAssertEqual(CaptureOrientation.snapDegrees(90, 170), 180)
        XCTAssertEqual(CaptureOrientation.snapDegrees(90, CaptureOrientation.unknown), 90)
    }

    func testDegreesMapToSurfaceRotation() {
        XCTAssertEqual(CaptureOrientation.surfaceRotation(0), 0)
        XCTAssertEqual(CaptureOrientation.surfaceRotation(90), 3)
        XCTAssertEqual(CaptureOrientation.surfaceRotation(180), 2)
        XCTAssertEqual(CaptureOrientation.surfaceRotation(270), 1)
        XCTAssertEqual(CaptureOrientation.pose(90), .reverseLandscape)
        XCTAssertEqual(CaptureOrientation.pose(270), .landscape)
        XCTAssertEqual(CaptureOrientation.pose(0), .portrait)
        XCTAssertEqual(CaptureOrientation.iconOffsetDegrees(90, 3), 0)
        XCTAssertEqual(CaptureOrientation.iconOffsetDegrees(90, 0), -90)
        XCTAssertEqual(CaptureOrientation.iconOffsetDegrees(270, 0), 90)
    }

    func testUprightBufferSwapsSidesForQuarterTurns() {
        XCTAssertEqual(CaptureFraming.uprightSize(width: 3000, height: 4000, rotationDegrees: 90), PixelSize(width: 4000, height: 3000))
        XCTAssertEqual(CaptureFraming.uprightSize(width: 3000, height: 4000, rotationDegrees: -270), PixelSize(width: 4000, height: 3000))
        XCTAssertEqual(CaptureFraming.uprightSize(width: 3000, height: 4000, rotationDegrees: 180), PixelSize(width: 3000, height: 4000))
        XCTAssertEqual(CaptureFraming.uprightSize(width: 4000, height: 3000, rotationDegrees: 0), PixelSize(width: 4000, height: 3000))
        let upright = CaptureFraming.uprightSize(width: 3000, height: 4000, rotationDegrees: 90)
        let stamped = WatermarkMetrics.outputSize(photoWidth: upright.width, photoHeight: upright.height, style: .plateLight, hasPlace: false)
        XCTAssertEqual(stamped.width, 4000)
        XCTAssertGreaterThan(stamped.height, upright.height)
        let portrait = WatermarkMetrics.outputSize(photoWidth: 3000, photoHeight: 4000, style: .plateLight, hasPlace: false)
        XCTAssertEqual(portrait.height - 4000, stamped.height - 3000)
    }

    func testLensButtonNeedsBothCamerasAndPresetsFollowTheRange() {
        XCTAssertFalse(CameraLenses.buttonVisible(back: true, front: false))
        XCTAssertFalse(CameraLenses.buttonVisible(back: false, front: true))
        XCTAssertTrue(CameraLenses.buttonVisible(back: true, front: true))
        XCTAssertEqual(CameraLenses.resolve(.front, back: true, front: false), .back)
        XCTAssertEqual(CameraLenses.resolve(.front, back: true, front: true), .front)
        XCTAssertEqual(CameraLenses.resolve(.back, back: false, front: false), .back)
        XCTAssertEqual(CameraZoom.presetsWithin(1, 2.4), [1, 2])
        XCTAssertEqual(CameraZoom.presetsWithin(0.5, 8), [0.5, 1, 2, 5])
        XCTAssertEqual(CameraZoom.presetsWithin(0.8, 1.5), [1])
        XCTAssertTrue(CameraZoom.presetsWithin(1.2, 1.8).isEmpty)
    }

    func testFocusFallsBackWhenAutofocusIsMissing() {
        XCTAssertEqual(FocusMeteringChoice.select { _ in true }, FocusMeteringChoice.af | FocusMeteringChoice.ae)
        XCTAssertEqual(FocusMeteringChoice.select { $0 == FocusMeteringChoice.ae }, FocusMeteringChoice.ae)
        XCTAssertEqual(FocusMeteringChoice.select { $0 == FocusMeteringChoice.af }, FocusMeteringChoice.af)
        XCTAssertNil(FocusMeteringChoice.select { _ in false })
    }

    func testAutofocusRequiresPictureModeAndFocusTravel() {
        let picture = [Autofocus.modeAuto, Autofocus.modeContinuousPicture]
        XCTAssertTrue(Autofocus.supported(afModes: picture, minimumFocusDistance: 0.1, meteringSupportsAf: false))
        XCTAssertTrue(Autofocus.supported(afModes: [Autofocus.modeContinuousPicture], minimumFocusDistance: 10, meteringSupportsAf: false))
        XCTAssertFalse(Autofocus.supported(afModes: picture, minimumFocusDistance: 0, meteringSupportsAf: true))
        XCTAssertFalse(Autofocus.supported(afModes: [0], minimumFocusDistance: 5, meteringSupportsAf: true))
        XCTAssertFalse(Autofocus.supported(afModes: [3], minimumFocusDistance: 5, meteringSupportsAf: true))
        XCTAssertFalse(Autofocus.supported(afModes: picture, minimumFocusDistance: .nan, meteringSupportsAf: true))
        XCTAssertFalse(Autofocus.supported(afModes: [0], minimumFocusDistance: nil, meteringSupportsAf: true))
        XCTAssertFalse(Autofocus.supported(afModes: nil, minimumFocusDistance: 0, meteringSupportsAf: true))
        XCTAssertTrue(Autofocus.supported(afModes: [Autofocus.modeAuto], minimumFocusDistance: nil, meteringSupportsAf: true))
        XCTAssertFalse(Autofocus.supported(afModes: [Autofocus.modeAuto], minimumFocusDistance: nil, meteringSupportsAf: false))
        XCTAssertTrue(Autofocus.supported(afModes: nil, minimumFocusDistance: nil, meteringSupportsAf: true))
        XCTAssertFalse(Autofocus.supported(afModes: nil, minimumFocusDistance: nil, meteringSupportsAf: false))
    }

    func testTapUsesFocusAndMeteringOnlyWhenTheCameraCanFocus() {
        let everything: (Int) -> Bool = { _ in true }
        let exposureOnly: (Int) -> Bool = { $0 == FocusMeteringChoice.ae }
        XCTAssertEqual(Autofocus.tapFlags(autofocus: true, everything), FocusMeteringChoice.af | FocusMeteringChoice.ae)
        XCTAssertEqual(Autofocus.tapFlags(autofocus: true, exposureOnly), FocusMeteringChoice.ae)
        XCTAssertEqual(Autofocus.tapFlags(autofocus: false, everything), FocusMeteringChoice.ae)
        XCTAssertEqual(Autofocus.tapFlags(autofocus: false, exposureOnly), FocusMeteringChoice.ae)
        XCTAssertNil(Autofocus.tapFlags(autofocus: false) { _ in false })
        XCTAssertNil(Autofocus.tapFlags(autofocus: true) { _ in false })
    }
}

final class CameraStageMetricsTests: XCTestCase {
    func testLandscapePreviewFillsMostOfTheScreen() {
        let style = WatermarkStyle.plateLight
        for (width, height) in [(Float(891), Float(411)), (800, 360), (640, 360)] {
            let fraction = CameraStageMetrics.landscapePreviewFraction(
                screenWidth: width,
                screenHeight: height,
                zoomVisible: true,
                photoAspect: 4 / 3,
                style: style,
                hasPlace: false
            )
            XCTAssertGreaterThanOrEqual(fraction, CameraStageMetrics.minLandscapePreviewFraction, "preview fraction \(fraction) on \(width)x\(height)")
        }
    }

    func testShutterStaysOnThePhysicalBottomEdge() {
        XCTAssertTrue(CameraStageMetrics.shutterOnEnd(1))
        XCTAssertTrue(CameraStageMetrics.shutterOnEnd(0))
        XCTAssertFalse(CameraStageMetrics.shutterOnEnd(CameraStageMetrics.rotation270))
        XCTAssertFalse(CameraStageMetrics.isLandscape(width: 411, height: 891))
        XCTAssertTrue(CameraStageMetrics.isLandscape(width: 891, height: 411))
    }
}

final class CameraZoomTests: XCTestCase {
    func testPinchStaysInsideTheDeviceRangeIncludingUltrawide() {
        XCTAssertEqual(CameraZoom.afterPinch(1, 0.4, 0.5, 8), 0.5, accuracy: 0.001)
        XCTAssertEqual(CameraZoom.afterPinch(1.2, 2, 0.5, 8), 2.4, accuracy: 0.001)
        XCTAssertEqual(CameraZoom.afterPinch(5, 3, 0.5, 8), 8, accuracy: 0.001)
        XCTAssertEqual(CameraZoom.afterPinch(1.2, .nan, 0.5, 8), 1.2, accuracy: 0.001)
    }

    func testPresetsDropRatiosTheLensCannotReach() {
        XCTAssertEqual(CameraZoom.presetsWithin(0.5, 10), [0.5, 1, 2, 5])
        XCTAssertEqual(CameraZoom.presetsWithin(1, 4), [1, 2])
        XCTAssertEqual(CameraZoom.presetsWithin(0.6, 1.2), [1])
        XCTAssertTrue(CameraZoom.presetsWithin(6, 8).isEmpty)
        XCTAssertTrue(CameraZoom.presetsWithin(2, 1).isEmpty)
    }

    func testPresetHighlightAndLabelsFollowTheRatio() {
        XCTAssertTrue(CameraZoom.matchesPreset(1.04, 1))
        XCTAssertFalse(CameraZoom.matchesPreset(2.3, 2))
        XCTAssertEqual(CameraZoom.formatZoomNumber(1, Locale(identifier: "en_US")), "1")
        XCTAssertEqual(CameraZoom.formatZoomNumber(2.26, Locale(identifier: "en_US")), "2.3")
        XCTAssertEqual(CameraZoom.formatZoomNumber(0.5, Locale(identifier: "fr_FR")), "0,5")
        XCTAssertEqual(CameraZoom.formatZoomNumber(2.26, Locale(identifier: "fr_FR")), "2,3")
    }
}
