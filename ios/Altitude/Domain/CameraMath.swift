import Foundation

enum CameraZoom {
    static let presetRatios: [Float] = [0.5, 1, 2, 5]

    static func clamp(_ ratio: Float, _ minRatio: Float, _ maxRatio: Float) -> Float {
        if !ratio.isFinite { return 1 }
        if !minRatio.isFinite || !maxRatio.isFinite || maxRatio < minRatio { return ratio }
        return min(max(ratio, minRatio), maxRatio)
    }

    static func afterPinch(_ current: Float, _ scale: Float, _ minRatio: Float, _ maxRatio: Float) -> Float {
        if !scale.isFinite || scale <= 0 { return clamp(current, minRatio, maxRatio) }
        return clamp(current * scale, minRatio, maxRatio)
    }

    static func presetsWithin(_ minRatio: Float, _ maxRatio: Float, _ candidates: [Float] = presetRatios) -> [Float] {
        if !minRatio.isFinite || !maxRatio.isFinite || maxRatio < minRatio { return [] }
        return candidates.filter { ratio in
            ratio.isFinite && ratio >= minRatio - 0.001 && ratio <= maxRatio + 0.001
        }
    }

    static func matchesPreset(_ current: Float, _ preset: Float) -> Bool {
        current.isFinite && preset.isFinite && abs(current - preset) < 0.06
    }

    static func formatZoomNumber(_ ratio: Float, _ locale: Locale) -> String {
        if !ratio.isFinite { return "" }
        let tenths = Int((ratio * 10).rounded())
        let sign = tenths < 0 ? "-" : ""
        let absolute = abs(tenths)
        let whole = absolute / 10
        let fraction = absolute % 10
        if fraction == 0 { return sign + String(whole) }
        let separator = decimalSeparator(locale)
        return "\(sign)\(whole)\(separator)\(fraction)"
    }
}

func decimalSeparator(_ locale: Locale) -> String {
    let formatter = NumberFormatter()
    formatter.locale = locale
    return formatter.decimalSeparator ?? "."
}

enum CaptureOrientation {
    static let unknown = -1
    static let hysteresisDegrees = 15

    static func snapDegrees(_ previous: Int, _ rawDegrees: Int, hysteresis: Int = hysteresisDegrees) -> Int {
        if rawDegrees < 0 { return previous < 0 ? 0 : floorMod(previous, 360) }
        let raw = floorMod(rawDegrees, 360)
        if previous < 0 { return nearestQuarter(raw) }
        let held = floorMod(previous, 360)
        if angularDistance(raw, held) >= 45 + hysteresis { return nearestQuarter(raw) }
        return held
    }

    static func surfaceRotation(_ deviceDegrees: Int) -> Int {
        switch floorMod(deviceDegrees, 360) {
        case 0: return 0
        case 90: return 3
        case 180: return 2
        case 270: return 1
        default: return 0
        }
    }

    static func pose(_ deviceDegrees: Int) -> DevicePose {
        switch floorMod(deviceDegrees, 360) {
        case 0: return .portrait
        case 90: return .reverseLandscape
        case 180: return .reversePortrait
        case 270: return .landscape
        default: return .portrait
        }
    }

    static func iconOffsetDegrees(_ deviceDegrees: Int, _ displaySurfaceRotation: Int) -> Int {
        let delta = floorMod(
            clockwiseDegrees(surfaceRotation(deviceDegrees)) - clockwiseDegrees(displaySurfaceRotation),
            360
        )
        return delta > 180 ? -(delta - 360) : -delta
    }

    static func clockwiseDegrees(_ surfaceRotation: Int) -> Int {
        switch surfaceRotation {
        case 0: return 0
        case 1: return 270
        case 2: return 180
        case 3: return 90
        default: return 0
        }
    }

    private static func nearestQuarter(_ raw: Int) -> Int {
        floorMod((raw + 45) / 90 * 90, 360)
    }

    private static func angularDistance(_ a: Int, _ b: Int) -> Int {
        let span = abs(a - b) % 360
        return min(span, 360 - span)
    }
}

enum CameraLenses {
    static func selectable(back: Bool, front: Bool) -> [CameraLens] {
        var lenses: [CameraLens] = []
        if back { lenses.append(.back) }
        if front { lenses.append(.front) }
        return lenses
    }

    static func buttonVisible(back: Bool, front: Bool) -> Bool {
        selectable(back: back, front: front).count > 1
    }

    static func resolve(_ preferred: CameraLens, back: Bool, front: Bool) -> CameraLens {
        let choices = selectable(back: back, front: front)
        if choices.contains(preferred) { return preferred }
        return choices.first ?? .back
    }
}

enum FocusMeteringChoice {
    static let af = 1
    static let ae = 2

    static func select(_ supports: (Int) -> Bool) -> Int? {
        [af | ae, ae, af].first(where: supports)
    }
}

enum Autofocus {
    static let modeAuto = 1
    static let modeContinuousPicture = 4

    static func supported(afModes: [Int]?, minimumFocusDistance: Float?, meteringSupportsAf: Bool) -> Bool {
        let pictureAf = afModes?.contains(where: { $0 == modeAuto || $0 == modeContinuousPicture }) == true
        let travels = minimumFocusDistance != nil && minimumFocusDistance!.isFinite && minimumFocusDistance! > 0
        if afModes != nil && minimumFocusDistance != nil { return pictureAf && travels }
        if afModes != nil && !pictureAf { return false }
        if minimumFocusDistance != nil && !travels { return false }
        return meteringSupportsAf
    }

    static func tapFlags(autofocus: Bool, _ supports: (Int) -> Bool) -> Int? {
        if autofocus { return FocusMeteringChoice.select(supports) }
        return supports(FocusMeteringChoice.ae) ? FocusMeteringChoice.ae : nil
    }
}

enum CaptureFraming {
    static func uprightSize(width: Int, height: Int, rotationDegrees: Int) -> PixelSize {
        let quarter = ((rotationDegrees % 360) + 360) % 360
        if quarter == 90 || quarter == 270 { return PixelSize(width: height, height: width) }
        return PixelSize(width: width, height: height)
    }
}

enum WatermarkMetrics {
    static let plateThousandths = 162
    static let plateWithPlaceThousandths = 210
    static let mountSideThousandths = 46
    static let mountTopThousandths = 46
    static let mountBottomThousandths = 200

    static func referenceEdge(photoWidth: Int, photoHeight: Int) -> Int {
        min(photoWidth, photoHeight)
    }

    static func scaledPx(_ reference: Int, _ thousandths: Int) -> Int {
        max(1, Int((Int64(reference) * Int64(thousandths) + 500) / 1000))
    }

    static func plateHeightPx(photoWidth: Int, hasPlace: Bool) -> Int {
        let thousandths = hasPlace ? plateWithPlaceThousandths : plateThousandths
        return scaledPx(photoWidth, thousandths)
    }

    static func outputSize(photoWidth: Int, photoHeight: Int, style: WatermarkStyle, hasPlace: Bool) -> PixelSize {
        let reference = referenceEdge(photoWidth: photoWidth, photoHeight: photoHeight)
        switch style {
        case .caption:
            return PixelSize(width: photoWidth, height: photoHeight)
        case .plateLight, .plateDark:
            return PixelSize(width: photoWidth, height: photoHeight + plateHeightPx(photoWidth: reference, hasPlace: hasPlace))
        case .mount:
            let side = scaledPx(reference, mountSideThousandths)
            let top = scaledPx(reference, mountTopThousandths)
            let bottom = scaledPx(reference, mountBottomThousandths)
            return PixelSize(width: photoWidth + side * 2, height: photoHeight + top + bottom)
        }
    }

    static func fitFrame(
        availWidth: Float,
        availHeight: Float,
        photoAspect: Float,
        style: WatermarkStyle,
        hasPlace: Bool
    ) -> FrameFit {
        precondition(photoAspect > 0)
        precondition(availWidth > 0 && availHeight > 0)
        let shortOverWidth: Float = photoAspect <= 1 ? 1 : 1 / photoAspect
        let sideRatio = (style == .mount ? Float(mountSideThousandths) / 1000 : 0) * shortOverWidth
        let topRatio = (style == .mount ? Float(mountTopThousandths) / 1000 : 0) * shortOverWidth
        let bottomThousandths: Float
        switch style {
        case .mount:
            bottomThousandths = Float(mountBottomThousandths)
        case .plateLight, .plateDark:
            bottomThousandths = Float(hasPlace ? plateWithPlaceThousandths : plateThousandths)
        case .caption:
            bottomThousandths = 0
        }
        let bottomRatio = (bottomThousandths / 1000) * shortOverWidth
        let widthFactor = 1 + 2 * sideRatio
        let heightFactor = (1 / photoAspect) + topRatio + bottomRatio
        let photoW = min(availWidth / widthFactor, availHeight / heightFactor)
        let photoH = photoW / photoAspect
        let side = photoW * sideRatio
        let top = photoW * topRatio
        let bottom = photoW * bottomRatio
        return FrameFit(
            photoWidth: photoW,
            photoHeight: photoH,
            side: side,
            top: top,
            bottom: bottom,
            outerWidth: photoW + side * 2,
            outerHeight: photoH + top + bottom
        )
    }
}

enum CameraStageMetrics {
    static let shutterRail: Float = 96
    static let zoomRail: Float = 64
    static let utilityRail: Float = 120
    static let edgeInset: Float = 4
    static let minLandscapePreviewFraction: Float = 0.70
    static let rotation270 = 3

    static func isLandscape(width: Float, height: Float) -> Bool {
        width > height && height > 0
    }

    static func shutterOnEnd(_ surfaceRotation: Int) -> Bool {
        surfaceRotation != rotation270
    }

    static func landscapePhotoHeight(
        screenWidth: Float,
        screenHeight: Float,
        zoomVisible: Bool,
        photoAspect: Float,
        style: WatermarkStyle,
        hasPlace: Bool
    ) -> Float {
        let zoom: Float = zoomVisible ? zoomRail : 0
        let availW = max(1, screenWidth - shutterRail - utilityRail - zoom - edgeInset * 2)
        let availH = max(1, screenHeight - edgeInset * 2)
        return WatermarkMetrics.fitFrame(
            availWidth: availW,
            availHeight: availH,
            photoAspect: photoAspect,
            style: style,
            hasPlace: hasPlace
        ).photoHeight
    }

    static func landscapePreviewFraction(
        screenWidth: Float,
        screenHeight: Float,
        zoomVisible: Bool,
        photoAspect: Float,
        style: WatermarkStyle,
        hasPlace: Bool
    ) -> Float {
        if screenHeight <= 0 { return 0 }
        return landscapePhotoHeight(
            screenWidth: screenWidth,
            screenHeight: screenHeight,
            zoomVisible: zoomVisible,
            photoAspect: photoAspect,
            style: style,
            hasPlace: hasPlace
        ) / screenHeight
    }
}
