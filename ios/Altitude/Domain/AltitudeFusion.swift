import Foundation

struct FusionConfig {
    var baroNoiseTauSeconds = 0.15
    var gpsTauExcellentSeconds = 0.8
    var gpsTauGoodSeconds = 1.5
    var gpsTauFairSeconds = 3.0
    var gpsTauPoorSeconds = 6.0
    var calibrationTauExcellentSeconds = 12.0
    var calibrationTauGoodSeconds = 25.0
    var calibrationTauFairSeconds = 45.0
    var calibrationTauPoorSeconds = 90.0
    var excellentAccuracyMeters = 6.0
    var goodAccuracyMeters = 12.0
    var fairAccuracyMeters = 25.0
    var maxCalibrationAccuracyMeters = 40.0
    var missingAccuracyMeters = 30.0
    var outlierGateMeters = 25.0
    var outlierSigma = 3.0
    var outlierResetMs: Int64 = 8_000
    var freshGpsMs: Int64 = 5_000
    var holdoverDriftMetersPerSecond = 0.15
    var minAltitudeMeters = -500.0
    var maxAltitudeMeters = 12_000.0
}

/// Complementary filter. The barometer, once its offset is fixed against GPS
/// mean-sea-level altitude, owns short-term motion. GPS owns the slow correction
/// of that offset. Pressure altitude by itself is not mean sea level.
final class AltitudeFusion {
    private let config: FusionConfig
    private var smoothedPressure: Double?
    private var smoothedGps: Double?
    private var calibrationOffset: Double?
    private var lastTimestampMs: Int64?
    private var lastAcceptedGpsTimestampMs: Int64?
    private var lastAcceptedAccuracy: Double?
    private var outlierSinceMs: Int64?
    private var seenSensor = false

    init(config: FusionConfig = FusionConfig()) {
        self.config = config
    }

    func update(_ input: FusionInput) -> FusionOutput {
        if input.gpsMslMeters != nil || input.pressureHpa != nil {
            seenSensor = true
        }
        let gps = sanitizeAltitude(input.gpsMslMeters)
        let accuracy = gps == nil ? nil : sanitizeAccuracy(input.gpsVerticalAccuracyMeters)
        let dt: Double
        if let previous = lastTimestampMs {
            dt = Double(max(0, input.timestampMs - previous)) / 1000.0
        } else {
            dt = 0
        }
        lastTimestampMs = input.timestampMs

        if let rawPressure = input.pressureHpa.flatMap(PressureAltitude.metersFromHpaOrNull) {
            smoothedPressure = approach(smoothedPressure, rawPressure, dt, config.baroNoiseTauSeconds)
        }
        let baro = smoothedPressure

        if let gps, let accuracy, let baro, accuracy <= config.maxCalibrationAccuracyMeters {
            calibrate(input.timestampMs, dt, gps, baro, accuracy)
        } else if let gps, let accuracy, baro == nil {
            let tau = gpsTau(accuracy)
            smoothedGps = approach(smoothedGps, gps, dt, tau)
            markGps(input.timestampMs, accuracy)
            outlierSinceMs = nil
        }

        let offset = calibrationOffset
        let ageMs = lastAcceptedGpsTimestampMs.map { input.timestampMs - $0 }

        if let baro, let offset {
            let age = ageMs ?? Int64.max
            let base = lastAcceptedAccuracy ?? config.missingAccuracyMeters
            let extra: Double
            if age <= config.freshGpsMs {
                extra = 0
            } else {
                extra = config.holdoverDriftMetersPerSecond * Double(max(0, age - config.freshGpsMs)) / 1000.0
            }
            let acc = base + extra
            let source: AltitudeSource = age <= config.freshGpsMs ? .fused : .pressureHoldover
            return publish(baro + offset, acc, source, quality(acc), true, true, offset)
        }

        if let baro {
            return publish(baro, nil, .pressureUncalibrated, .poor, false, false, nil)
        }

        if let gpsOnly = smoothedGps {
            let acc = lastAcceptedAccuracy
            return publish(gpsOnly, acc, .gps, quality(acc), true, false, nil)
        }

        return publish(nil, nil, .none, seenSensor ? .searching : .none, false, false, nil)
    }

    private func calibrate(_ timestampMs: Int64, _ dt: Double, _ gps: Double, _ baro: Double, _ accuracy: Double) {
        let desired = gps - baro
        guard let current = calibrationOffset else {
            calibrationOffset = desired
            outlierSinceMs = nil
            markGps(timestampMs, accuracy)
            return
        }
        let innovation = desired - current
        let gate = max(config.outlierGateMeters, accuracy * config.outlierSigma)
        if abs(innovation) <= gate {
            let alpha = alpha(dt, calibrationTau(accuracy))
            calibrationOffset = current + alpha * innovation
            outlierSinceMs = nil
            markGps(timestampMs, accuracy)
            return
        }
        let since = outlierSinceMs ?? timestampMs
        if outlierSinceMs == nil { outlierSinceMs = timestampMs }
        if timestampMs - since >= config.outlierResetMs {
            calibrationOffset = desired
            outlierSinceMs = nil
            markGps(timestampMs, accuracy)
        }
    }

    private func markGps(_ timestampMs: Int64, _ accuracy: Double) {
        lastAcceptedGpsTimestampMs = timestampMs
        lastAcceptedAccuracy = accuracy
    }

    private func sanitizeAltitude(_ value: Double?) -> Double? {
        guard let value, value.isFinite else { return nil }
        if value < config.minAltitudeMeters || value > config.maxAltitudeMeters { return nil }
        return value
    }

    private func sanitizeAccuracy(_ accuracy: Double?) -> Double {
        guard let accuracy, accuracy.isFinite, accuracy > 0 else { return config.missingAccuracyMeters }
        return accuracy
    }

    private func approach(_ previous: Double?, _ sample: Double, _ dtSeconds: Double, _ tauSeconds: Double) -> Double {
        guard let previous else { return sample }
        let alpha = alpha(dtSeconds, tauSeconds)
        return previous + alpha * (sample - previous)
    }

    private func alpha(_ dtSeconds: Double, _ tauSeconds: Double) -> Double {
        if tauSeconds <= 0 { return 1 }
        if dtSeconds <= 0 { return 0 }
        return 1 - exp(-dtSeconds / tauSeconds)
    }

    private func calibrationTau(_ accuracy: Double) -> Double {
        if accuracy <= config.excellentAccuracyMeters { return config.calibrationTauExcellentSeconds }
        if accuracy <= config.goodAccuracyMeters { return config.calibrationTauGoodSeconds }
        if accuracy <= config.fairAccuracyMeters { return config.calibrationTauFairSeconds }
        return config.calibrationTauPoorSeconds
    }

    private func gpsTau(_ accuracy: Double) -> Double {
        if accuracy <= config.excellentAccuracyMeters { return config.gpsTauExcellentSeconds }
        if accuracy <= config.goodAccuracyMeters { return config.gpsTauGoodSeconds }
        if accuracy <= config.fairAccuracyMeters { return config.gpsTauFairSeconds }
        return config.gpsTauPoorSeconds
    }

    private func quality(_ accuracy: Double?) -> FixQuality {
        guard let value = accuracy else { return .poor }
        if value <= config.excellentAccuracyMeters { return .excellent }
        if value <= config.goodAccuracyMeters { return .good }
        if value <= config.fairAccuracyMeters { return .fair }
        return .poor
    }

    private func publish(
        _ altitude: Double?,
        _ accuracy: Double?,
        _ source: AltitudeSource,
        _ quality: FixQuality,
        _ absolute: Bool,
        _ calibrated: Bool,
        _ offset: Double?
    ) -> FusionOutput {
        FusionOutput(
            altitudeMslMeters: altitude,
            accuracyMeters: accuracy,
            source: source,
            quality: quality,
            absoluteMsl: absolute,
            barometerCalibrated: calibrated,
            calibrationOffsetMeters: offset
        )
    }
}
