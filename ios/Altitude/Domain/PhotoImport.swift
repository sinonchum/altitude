import Foundation

/// How many photos one import can frame.
let importSelectionLimit = 12

/// The eight EXIF orientation values. Unknown tags are treated as normal.
enum ExifOrientation: Int, Equatable, CaseIterable {
    case normal = 1
    case flipHorizontal = 2
    case rotate180 = 3
    case flipVertical = 4
    case transpose = 5
    case rotate90 = 6
    case transverse = 7
    case rotate270 = 8

    var tag: Int { rawValue }

    static func fromTag(_ tag: Int) -> ExifOrientation {
        ExifOrientation(rawValue: tag) ?? .normal
    }

    /// Clockwise rotation applied first, then an optional horizontal mirror.
    func transform() -> OrientationTransform {
        switch self {
        case .normal: return OrientationTransform(clockwiseDegrees: 0, mirrorHorizontal: false)
        case .flipHorizontal: return OrientationTransform(clockwiseDegrees: 0, mirrorHorizontal: true)
        case .rotate180: return OrientationTransform(clockwiseDegrees: 180, mirrorHorizontal: false)
        case .flipVertical: return OrientationTransform(clockwiseDegrees: 180, mirrorHorizontal: true)
        case .transpose: return OrientationTransform(clockwiseDegrees: 90, mirrorHorizontal: true)
        case .rotate90: return OrientationTransform(clockwiseDegrees: 90, mirrorHorizontal: false)
        case .transverse: return OrientationTransform(clockwiseDegrees: 270, mirrorHorizontal: true)
        case .rotate270: return OrientationTransform(clockwiseDegrees: 270, mirrorHorizontal: false)
        }
    }
}

struct OrientationTransform: Equatable {
    var clockwiseDegrees: Int
    var mirrorHorizontal: Bool
}

/// Wall clock stored on the photo. Components are the time as recorded, not shifted into the phone's zone.
struct WallClock: Equatable {
    var year: Int
    var month: Int
    var day: Int
    var hour: Int
    var minute: Int
    var second: Int
}

struct ExifOffset: Equatable {
    var totalSeconds: Int

    static let utc = ExifOffset(totalSeconds: 0)

    static func hours(_ hours: Int) -> ExifOffset {
        ExifOffset(totalSeconds: hours * 3600)
    }

    static func hoursMinutes(_ hours: Int, _ minutes: Int) -> ExifOffset {
        ExifOffset(totalSeconds: hours * 3600 + minutes * 60)
    }
}

struct CapturedClock: Equatable {
    var local: WallClock
    var offset: ExifOffset?
}

struct PhotoCapture: Equatable {
    var latitude: Double?
    var longitude: Double?
    var exifAltitudeMeters: Double?
    var clock: CapturedClock?
    var orientation: ExifOrientation

    var hasCoordinates: Bool { latitude != nil && longitude != nil }
}

let altitudeAboveSeaLevel = 0
let altitudeBelowSeaLevel = 1

/// GPSAltitude is an unsigned rational. Reference 1 is below sea level. A missing reference means above sea level.
func signedGpsAltitudeMeters(_ altitudeMeters: Double?, _ altitudeRef: Int?) -> Double? {
    guard let magnitude = altitudeMeters, magnitude.isFinite, magnitude >= 0 else { return nil }
    return altitudeRef == altitudeBelowSeaLevel ? -magnitude : magnitude
}

func parseExifDateTime(_ raw: String?) -> WallClock? {
    guard let text = cleanExifText(raw), !text.contains(":  :") else { return nil }
    guard let groups = firstGroups(#"^(\d{4}):(\d{2}):(\d{2}) (\d{2}):(\d{2}):(\d{2})$"#, text),
          let year = Int(groups[1]), let month = Int(groups[2]), let day = Int(groups[3]),
          let hour = Int(groups[4]), let minute = Int(groups[5]), let second = Int(groups[6]) else {
        return nil
    }
    let clock = WallClock(year: year, month: month, day: day, hour: hour, minute: minute, second: second)
    return validWallClock(clock) ? clock : nil
}

/// Accepts `±HH:MM`, `±HHMM`, `±HH`, and `Z`. Anything else stays unknown.
func parseExifOffset(_ raw: String?) -> ExifOffset? {
    guard let text = cleanExifText(raw) else { return nil }
    if text.caseInsensitiveCompare("Z") == .orderedSame { return .utc }
    guard let groups = firstGroups(#"^([+-])(\d{2})(?::?(\d{2}))?$"#, text),
          let hours = Int(groups[2]) else { return nil }
    let minutes = groups.count > 3 && !groups[3].isEmpty ? Int(groups[3]) ?? -1 : 0
    if hours > 18 || minutes < 0 || minutes > 59 { return nil }
    let sign = groups[1] == "-" ? -1 : 1
    return ExifOffset(totalSeconds: sign * (hours * 3600 + minutes * 60))
}

/// GPS date and time are UTC. Either half missing means no GPS instant.
func parseGpsUtc(_ dateStamp: String?, _ timeStamp: String?) -> (clock: WallClock, offset: ExifOffset)? {
    guard let date = cleanExifText(dateStamp), let time = cleanExifText(timeStamp) else { return nil }
    guard let dayGroups = firstGroups(#"^(\d{4}):(\d{2}):(\d{2})$"#, date),
          let year = Int(dayGroups[1]), let month = Int(dayGroups[2]), let day = Int(dayGroups[3]),
          let clock = parseGpsClock(time) else { return nil }
    let wall = WallClock(year: year, month: month, day: day, hour: clock.hour, minute: clock.minute, second: clock.second)
    return validWallClock(wall) ? (wall, .utc) : nil
}

/// Prefer DateTimeOriginal, then digitized, then the generic stamp. GPS time is used only when no local stamp exists.
func resolveCapturedClock(
    dateTimeOriginal: String?,
    dateTimeDigitized: String?,
    dateTime: String?,
    offsetTimeOriginal: String?,
    offsetTimeDigitized: String?,
    offsetTime: String?,
    gpsDateStamp: String?,
    gpsTimeStamp: String?
) -> CapturedClock? {
    if let local = parseExifDateTime(dateTimeOriginal) {
        let offset = parseExifOffset(offsetTimeOriginal) ?? parseExifOffset(offsetTime)
        return CapturedClock(local: local, offset: offset)
    }
    if let local = parseExifDateTime(dateTimeDigitized) {
        let offset = parseExifOffset(offsetTimeDigitized) ?? parseExifOffset(offsetTime)
        return CapturedClock(local: local, offset: offset)
    }
    if let local = parseExifDateTime(dateTime) {
        return CapturedClock(local: local, offset: parseExifOffset(offsetTime))
    }
    guard let gps = parseGpsUtc(gpsDateStamp, gpsTimeStamp) else { return nil }
    let offset = parseExifOffset(offsetTimeOriginal)
        ?? parseExifOffset(offsetTimeDigitized)
        ?? parseExifOffset(offsetTime)
        ?? ExifOffset.utc
    return CapturedClock(local: shiftUtc(gps.clock, by: offset), offset: offset)
}

func formatExifWallClock(_ local: WallClock) -> String {
    String(format: "%04d:%02d:%02d %02d:%02d:%02d", locale: Locale(identifier: "en_US_POSIX"), local.year, local.month, local.day, local.hour, local.minute, local.second)
}

func formatExifOffset(_ offset: ExifOffset) -> String {
    let total = offset.totalSeconds
    let sign = total < 0 ? "-" : "+"
    let absolute = abs(total)
    let hours = absolute / 3600
    let minutes = (absolute % 3600) / 60
    return String(format: "%@%02d:%02d", locale: Locale(identifier: "en_US_POSIX"), sign, hours, minutes)
}

func importDisplayName(clock: CapturedClock?, fallbackEpochMs: Int64, index: Int, zone: TimeZone = .current) -> String {
    let stamp: String
    if let clock {
        let local = clock.local
        stamp = String(format: "%04d%02d%02d_%02d%02d%02d", locale: Locale(identifier: "en_US_POSIX"), local.year, local.month, local.day, local.hour, local.minute, local.second)
    } else {
        stamp = formatExifDateTime(fallbackEpochMs, zone: zone)
            .replacingOccurrences(of: ":", with: "")
            .replacingOccurrences(of: " ", with: "_")
    }
    let suffix = index > 0 ? "_\(index)" : ""
    return "Altitude_\(stamp)\(suffix).jpg"
}

func rationalToDouble(_ raw: String) -> Double? {
    let text = raw.trimmingCharacters(in: .whitespacesAndNewlines)
    if text.isEmpty { return nil }
    guard let slash = text.firstIndex(of: "/") else {
        return Double(text).flatMap { $0.isFinite ? $0 : nil }
    }
    guard let numerator = Double(text[..<slash].trimmingCharacters(in: .whitespaces)),
          let denominator = Double(text[text.index(after: slash)...].trimmingCharacters(in: .whitespaces)),
          numerator.isFinite, denominator.isFinite, denominator != 0 else { return nil }
    let value = numerator / denominator
    return value.isFinite ? value : nil
}

func cleanExifText(_ raw: String?) -> String? {
    guard let raw else { return nil }
    let text = raw.replacingOccurrences(of: "\u{0000}", with: "").trimmingCharacters(in: .whitespacesAndNewlines)
    return text.isEmpty ? nil : text
}

func formatWallClockPattern(_ clock: WallClock, pattern: String, locale: Locale) -> String {
    var calendar = Calendar(identifier: .gregorian)
    calendar.timeZone = TimeZone(secondsFromGMT: 0) ?? .gmt
    var components = DateComponents()
    components.calendar = calendar
    components.timeZone = calendar.timeZone
    components.year = clock.year
    components.month = clock.month
    components.day = clock.day
    components.hour = clock.hour
    components.minute = clock.minute
    components.second = clock.second
    let date = calendar.date(from: components) ?? Date(timeIntervalSince1970: 0)
    let millis = Int64((date.timeIntervalSince1970 * 1000).rounded())
    return formatPattern(millis, zone: calendar.timeZone, pattern: pattern, locale: locale)
}

enum AltitudeProvenance: String, Equatable {
    case fromPhoto = "FROM_PHOTO"
    case computed = "COMPUTED"
    case missing = "MISSING"
}

struct SelectedAltitude: Equatable {
    var meters: Double?
    var provenance: AltitudeProvenance
}

/// EXIF altitude wins, including zero and below-sea-level values. A lookup is used only when the photo has coordinates and no altitude of its own.
func selectPhotoAltitude(exifAltitudeMeters: Double?, elevationLookupMeters: Double?, hasCoordinates: Bool) -> SelectedAltitude {
    if let recorded = exifAltitudeMeters, recorded.isFinite {
        return SelectedAltitude(meters: recorded, provenance: .fromPhoto)
    }
    if hasCoordinates, let lookedUp = elevationLookupMeters, lookedUp.isFinite {
        return SelectedAltitude(meters: lookedUp, provenance: .computed)
    }
    return SelectedAltitude(meters: nil, provenance: .missing)
}

enum ImportNoticeKind: Equatable {
    case none
    case noLocation
    case locationHidden
    case computedAltitude
    case altitudeMissing
}

/// A nil altitude means the lookup is still running. That is not a missing value yet.
func importNoticeKind(hasCoordinates: Bool, altitude: SelectedAltitude?, locationRedacted: Bool) -> ImportNoticeKind {
    if !hasCoordinates {
        return locationRedacted ? .locationHidden : .noLocation
    }
    switch altitude?.provenance {
    case .computed: return .computedAltitude
    case .missing: return .altitudeMissing
    case .fromPhoto, nil: return .none
    }
}

func openMeteoElevationUrl(_ points: [(latitude: Double, longitude: Double)]) -> String {
    precondition(!points.isEmpty)
    let latitudes = points.map { formatCoordinateQuery($0.latitude) }.joined(separator: ",")
    let longitudes = points.map { formatCoordinateQuery($0.longitude) }.joined(separator: ",")
    return "https://api.open-meteo.com/v1/elevation?latitude=\(latitudes)&longitude=\(longitudes)"
}

func elevationCacheKey(_ latitude: Double, _ longitude: Double) -> String {
    String(format: "%.4f,%.4f", locale: Locale(identifier: "en_US_POSIX"), latitude, longitude)
}

/// Parses an Open-Meteo elevation body. A null array entry is a missing height. A body without `elevation` is unusable.
func parseOpenMeteoElevations(_ body: String) -> [Double?]? {
    guard let data = body.data(using: .utf8),
          let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
          let values = root["elevation"] as? [Any] else { return nil }
    return values.map(elevationNumber)
}

struct MergedPhotoLocation: Equatable {
    var latitude: Double?
    var longitude: Double?
    var redacted: Bool

    var hasCoordinates: Bool { latitude != nil && longitude != nil }
}

/// EXIF GPS wins. A Photos library asset location fills a file whose GPS was stripped. Neither source invents a coordinate.
func mergePhotoLocation(
    exifLatitude: Double?,
    exifLongitude: Double?,
    assetLatitude: Double?,
    assetLongitude: Double?,
    assetWasReadable: Bool
) -> MergedPhotoLocation {
    if let latitude = finiteNumber(exifLatitude), let longitude = finiteNumber(exifLongitude) {
        return MergedPhotoLocation(latitude: latitude, longitude: longitude, redacted: false)
    }
    if let latitude = finiteNumber(assetLatitude), let longitude = finiteNumber(assetLongitude) {
        return MergedPhotoLocation(latitude: latitude, longitude: longitude, redacted: false)
    }
    if !assetWasReadable {
        return MergedPhotoLocation(latitude: nil, longitude: nil, redacted: true)
    }
    return MergedPhotoLocation(latitude: nil, longitude: nil, redacted: false)
}

private func elevationNumber(_ value: Any) -> Double? {
    if value is NSNull { return nil }
    if let text = value as? String {
        return Double(text).flatMap { $0.isFinite ? $0 : nil }
    }
    if let number = value as? NSNumber {
        let resolved = number.doubleValue
        return resolved.isFinite ? resolved : nil
    }
    if let number = value as? Double, number.isFinite { return number }
    return nil
}

private func finiteNumber(_ value: Double?) -> Double? {
    guard let value, value.isFinite else { return nil }
    return value
}

private func parseGpsClock(_ time: String) -> (hour: Int, minute: Int, second: Int)? {
    if let groups = firstGroups(#"^(\d{1,2}):(\d{1,2}):(\d{1,2}(?:\.\d+)?)$"#, time),
       let hour = Double(groups[1]), let minute = Double(groups[2]), let second = Double(groups[3]) {
        return clockFromParts(hour, minute, second)
    }
    let parts = time.split(separator: ",").map { $0.trimmingCharacters(in: .whitespaces) }
    guard parts.count == 3 else { return nil }
    var numbers: [Double] = []
    for part in parts {
        guard let value = rationalToDouble(part) else { return nil }
        numbers.append(value)
    }
    return clockFromParts(numbers[0], numbers[1], numbers[2])
}

private func clockFromParts(_ hour: Double, _ minute: Double, _ second: Double) -> (hour: Int, minute: Int, second: Int)? {
    guard hour.isFinite, minute.isFinite, second.isFinite else { return nil }
    let wholeHour = Int(hour)
    let wholeMinute = Int(minute)
    let wholeSecond = Int(second)
    guard (0...23).contains(wholeHour), (0...59).contains(wholeMinute), (0...60).contains(wholeSecond) else { return nil }
    return (wholeHour, wholeMinute, min(wholeSecond, 59))
}

private func shiftUtc(_ utc: WallClock, by offset: ExifOffset) -> WallClock {
    var calendar = Calendar(identifier: .gregorian)
    calendar.timeZone = TimeZone(secondsFromGMT: 0) ?? .gmt
    var components = DateComponents()
    components.calendar = calendar
    components.timeZone = calendar.timeZone
    components.year = utc.year
    components.month = utc.month
    components.day = utc.day
    components.hour = utc.hour
    components.minute = utc.minute
    components.second = utc.second
    let date = calendar.date(from: components) ?? Date(timeIntervalSince1970: 0)
    let shifted = date.addingTimeInterval(TimeInterval(offset.totalSeconds))
    let parts = calendar.dateComponents([.year, .month, .day, .hour, .minute, .second], from: shifted)
    return WallClock(
        year: parts.year ?? utc.year,
        month: parts.month ?? utc.month,
        day: parts.day ?? utc.day,
        hour: parts.hour ?? utc.hour,
        minute: parts.minute ?? utc.minute,
        second: parts.second ?? utc.second
    )
}

private func validWallClock(_ clock: WallClock) -> Bool {
    var calendar = Calendar(identifier: .gregorian)
    calendar.timeZone = TimeZone(secondsFromGMT: 0) ?? .gmt
    var components = DateComponents()
    components.calendar = calendar
    components.timeZone = calendar.timeZone
    components.year = clock.year
    components.month = clock.month
    components.day = clock.day
    components.hour = clock.hour
    components.minute = clock.minute
    components.second = clock.second
    guard let date = calendar.date(from: components) else { return false }
    let parts = calendar.dateComponents([.year, .month, .day, .hour, .minute, .second], from: date)
    return parts.year == clock.year && parts.month == clock.month && parts.day == clock.day
        && parts.hour == clock.hour && parts.minute == clock.minute && parts.second == clock.second
}

private func firstGroups(_ pattern: String, _ text: String) -> [String]? {
    guard let regex = try? NSRegularExpression(pattern: pattern) else { return nil }
    let range = NSRange(text.startIndex..., in: text)
    guard let match = regex.firstMatch(in: text, range: range) else { return nil }
    return (0..<match.numberOfRanges).map { index in
        let piece = match.range(at: index)
        guard piece.location != NSNotFound, let swift = Range(piece, in: text) else { return "" }
        return String(text[swift])
    }
}
