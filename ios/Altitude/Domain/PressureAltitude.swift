import Foundation

/// International Standard Atmosphere troposphere (valid to about 11 km).
/// Pressure altitude is relative to a standard 1013.25 hPa surface, not to mean sea level.
enum PressureAltitude {
    static let standardPressureHpa = 1013.25

    private static let t0Kelvin = 288.15
    private static let lapseKelvinPerMeter = 0.0065
    private static let exponent = 0.1902632367921655
    private static let scaleMeters = t0Kelvin / lapseKelvinPerMeter

    static func metersFromHpaOrNull(_ pressureHpa: Double) -> Double? {
        guard pressureHpa.isFinite, pressureHpa >= 250, pressureHpa <= 1100 else { return nil }
        let ratio = pressureHpa / standardPressureHpa
        return scaleMeters * (1.0 - pow(ratio, exponent))
    }

    static func hpaFromMeters(_ meters: Double) -> Double {
        let ratio = 1.0 - (meters / scaleMeters)
        return standardPressureHpa * pow(ratio, 1.0 / exponent)
    }
}
