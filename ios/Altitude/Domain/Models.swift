import Foundation

enum AltitudeUnit: String, CaseIterable, Codable {
    case meters = "METERS"
    case feet = "FEET"
}

enum DisplayTheme: String, CaseIterable, Codable {
    case night = "NIGHT"
    case void = "VOID"
    case glacier = "GLACIER"
    case saffron = "SAFFRON"
    case snow = "SNOW"

    var lightBars: Bool { self == .snow }
}

enum AppMode: String {
    case display = "DISPLAY"
    case camera = "CAMERA"
}

enum WatermarkStyle: String, CaseIterable, Codable {
    case plateLight = "PLATE_LIGHT"
    case plateDark = "PLATE_DARK"
    case mount = "MOUNT"
    case caption = "CAPTION"
}

enum CaptionAnchor: String, CaseIterable, Codable {
    case bottom = "BOTTOM"
    case top = "TOP"
}

enum AltitudeSource: String {
    case none = "NONE"
    case gps = "GPS"
    case pressureUncalibrated = "PRESSURE_UNCALIBRATED"
    case fused = "FUSED"
    case pressureHoldover = "PRESSURE_HOLDOVER"
}

enum FixQuality: String {
    case none = "NONE"
    case searching = "SEARCHING"
    case poor = "POOR"
    case fair = "FAIR"
    case good = "GOOD"
    case excellent = "EXCELLENT"
}

struct GnssSample: Equatable {
    var latitude: Double
    var longitude: Double
    var ellipsoidalAltitudeMeters: Double?
    var platformMslAltitudeMeters: Double?
    var horizontalAccuracyMeters: Double?
    var verticalAccuracyMeters: Double?
    var timeMs: Int64
    var elapsedRealtimeMs: Int64 = 0
}

struct FusionInput {
    var timestampMs: Int64
    var gpsMslMeters: Double?
    var gpsVerticalAccuracyMeters: Double?
    var pressureHpa: Double?
}

struct FusionOutput: Equatable {
    var altitudeMslMeters: Double?
    var accuracyMeters: Double?
    var source: AltitudeSource
    var quality: FixQuality
    var absoluteMsl: Bool
    var barometerCalibrated: Bool
    var calibrationOffsetMeters: Double?

    static func empty() -> FusionOutput {
        FusionOutput(
            altitudeMslMeters: nil,
            accuracyMeters: nil,
            source: .none,
            quality: .none,
            absoluteMsl: false,
            barometerCalibrated: false,
            calibrationOffsetMeters: nil
        )
    }
}

struct PrefsSnapshot: Equatable {
    var unit: AltitudeUnit
    var theme: DisplayTheme
    var maxBrightness: Bool
    var showCoordinates: Bool
    var showAccuracy: Bool
    var showClock: Bool
    var showPlace: Bool
    var dynamicColor: Bool
    var watermarkStyle: WatermarkStyle
    var captionAnchor: CaptionAnchor
    var onboardingComplete: Bool
    var frontCamera: Bool
    var screenFlash: Bool

    static let `default` = PrefsSnapshot(
        unit: .meters,
        theme: .night,
        maxBrightness: false,
        showCoordinates: true,
        showAccuracy: true,
        showClock: true,
        showPlace: true,
        dynamicColor: false,
        watermarkStyle: .plateLight,
        captionAnchor: .bottom,
        onboardingComplete: false,
        frontCamera: false,
        screenFlash: false
    )
}

struct LastKnown: Equatable, Codable {
    var altitudeMeters: Double
    var timeMs: Int64
    var latitude: Double?
    var longitude: Double?
}

struct WatermarkContent: Equatable {
    var altitude: String
    var coordinates: String?
    var place: String?
    var time: String?
    var wordmark: String = "ALTITUDE"
}

struct PixelSize: Equatable {
    var width: Int
    var height: Int
}

struct FrameFit: Equatable {
    var photoWidth: Float
    var photoHeight: Float
    var side: Float
    var top: Float
    var bottom: Float
    var outerWidth: Float
    var outerHeight: Float
}

enum CameraLens: String {
    case back = "BACK"
    case front = "FRONT"
}

enum DevicePose {
    case portrait
    case landscape
    case reversePortrait
    case reverseLandscape
}

enum LocationFeed {
    case satellite
    case network
    case fused
}

enum OsmLookup {
    case reached(String?)
    case unreachable
}
