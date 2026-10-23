import Foundation

private let metersPerFoot = 0.3048

func metersToFeet(_ meters: Double) -> Double { meters / metersPerFoot }

func groupThousands(_ value: Int64) -> String {
    let sign = value < 0 ? "-" : ""
    let digits = String(abs(value))
    var grouped = ""
    for (index, char) in digits.enumerated() {
        if index > 0 && (digits.count - index) % 3 == 0 {
            grouped.append(",")
        }
        grouped.append(char)
    }
    return sign + grouped
}

func formatInteger(_ value: Int64, _ locale: Locale) -> String {
    let formatter = NumberFormatter()
    formatter.locale = locale
    formatter.numberStyle = .decimal
    formatter.usesGroupingSeparator = true
    formatter.maximumFractionDigits = 0
    formatter.minimumFractionDigits = 0
    formatter.roundingMode = .halfUp
    return formatter.string(from: NSNumber(value: value)) ?? groupThousands(value)
}

func formatDecimal(_ value: Double, fractionDigits: Int, _ locale: Locale) -> String {
    let formatter = NumberFormatter()
    formatter.locale = locale
    formatter.numberStyle = .decimal
    formatter.usesGroupingSeparator = false
    formatter.minimumIntegerDigits = 1
    formatter.minimumFractionDigits = fractionDigits
    formatter.maximumFractionDigits = fractionDigits
    formatter.roundingMode = .halfUp
    return formatter.string(from: NSNumber(value: value)) ?? String(format: "%.\(fractionDigits)f", value)
}

func formatAltitudeNumber(_ meters: Double, _ unit: AltitudeUnit, _ locale: Locale = Locale(identifier: "en_US")) -> String {
    let value = unit == .feet ? metersToFeet(meters) : meters
    return formatInteger(Int64(value.rounded()), locale)
}

func formatAltitudeReading(_ meters: Double, _ unit: AltitudeUnit, _ locale: Locale = Locale(identifier: "en_US")) -> String {
    let suffix = unit == .feet ? "ft" : "m"
    return "\(formatAltitudeNumber(meters, unit, locale)) \(suffix)"
}

func formatAccuracy(_ meters: Double, _ unit: AltitudeUnit) -> String {
    let value = unit == .feet ? metersToFeet(meters) : meters
    let suffix = unit == .feet ? "ft" : "m"
    return "± \(groupThousands(Int64(value.rounded()))) \(suffix)"
}

func formatCoordinate(_ value: Double, latitude: Bool) -> String {
    let hemisphere: String
    if latitude {
        hemisphere = value >= 0 ? "N" : "S"
    } else {
        hemisphere = value >= 0 ? "E" : "W"
    }
    return String(format: "%.5f° %@", locale: Locale(identifier: "en_US_POSIX"), abs(value), hemisphere)
}

func formatCoordinatePair(latitude: Double, longitude: Double) -> String {
    "\(formatCoordinate(latitude, latitude: true))   \(formatCoordinate(longitude, latitude: false))"
}

func formatPattern(_ epochMs: Int64, zone: TimeZone, pattern: String, locale: Locale = Locale(identifier: "en_US_POSIX")) -> String {
    let formatter = DateFormatter()
    formatter.calendar = Calendar(identifier: .gregorian)
    formatter.locale = locale
    formatter.timeZone = zone
    formatter.dateFormat = pattern
    return formatter.string(from: Date(timeIntervalSince1970: Double(epochMs) / 1000))
}

func formatDisplayClock(_ epochMs: Int64, zone: TimeZone = .current) -> String {
    formatPattern(epochMs, zone: zone, pattern: "HH:mm:ss")
}

func formatWatermarkStamp(_ epochMs: Int64, zone: TimeZone = .current) -> String {
    formatPattern(epochMs, zone: zone, pattern: "yyyy.MM.dd") + "  " + formatPattern(epochMs, zone: zone, pattern: "HH:mm")
}

func formatExifDateTime(_ epochMs: Int64, zone: TimeZone = .current) -> String {
    formatPattern(epochMs, zone: zone, pattern: "yyyy:MM:dd HH:mm:ss")
}

func buildWatermarkContent(
    altitudeMeters: Double?,
    absoluteMsl: Bool,
    unit: AltitudeUnit,
    latitude: Double?,
    longitude: Double?,
    place: String?,
    epochMs: Int64,
    zone: TimeZone = .current
) -> WatermarkContent {
    let altitude: String
    if let altitudeMeters, absoluteMsl {
        altitude = formatAltitudeReading(altitudeMeters, unit)
    } else {
        altitude = "— —"
    }
    let coordinates: String?
    if let latitude, let longitude {
        coordinates = formatCoordinatePair(latitude: latitude, longitude: longitude)
    } else {
        coordinates = nil
    }
    let trimmed = place?.trimmingCharacters(in: .whitespacesAndNewlines)
    return WatermarkContent(
        altitude: altitude,
        coordinates: coordinates,
        place: (trimmed?.isEmpty == false) ? trimmed : nil,
        time: formatWatermarkStamp(epochMs, zone: zone)
    )
}

func epochMillis(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int, zone: TimeZone) -> Int64 {
    var components = DateComponents()
    components.calendar = Calendar(identifier: .gregorian)
    components.timeZone = zone
    components.year = year
    components.month = month
    components.day = day
    components.hour = hour
    components.minute = minute
    components.second = second
    let date = components.date ?? Date()
    return Int64((date.timeIntervalSince1970 * 1000).rounded())
}
