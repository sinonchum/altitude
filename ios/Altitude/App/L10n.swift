import Foundation

enum AppLanguage: String, CaseIterable, Identifiable {
    case system
    case chinese
    case english
    case french

    var id: String { rawValue }

    var labelKey: String {
        switch self {
        case .system: return "language_system"
        case .chinese: return "language_zh"
        case .english: return "language_en"
        case .french: return "language_fr"
        }
    }

    var formatLocale: Locale {
        switch self {
        case .system: return .autoupdatingCurrent
        case .chinese: return Locale(identifier: "zh_CN")
        case .english: return Locale(identifier: "en_US")
        case .french: return Locale(identifier: "fr_FR")
        }
    }

    var bundleCode: String? {
        switch self {
        case .system: return nil
        case .chinese: return "zh-Hans"
        case .english: return "en"
        case .french: return "fr"
        }
    }

    /// BCP-47 tag passed to Nominatim and to place-name selection.
    var nominatimTag: String {
        switch self {
        case .chinese: return "zh-CN"
        case .english: return "en"
        case .french: return "fr"
        case .system:
            return Locale.preferredLanguages.first ?? "en"
        }
    }
}

func pluralCategory(count: Int, language: String) -> String {
    switch language {
    case "zh":
        return "other"
    case "fr":
        if count == 0 || count == 1 { return "one" }
        if count >= 1_000_000 { return "many" }
        return "other"
    default:
        return count == 1 ? "one" : "other"
    }
}

func androidToSwiftFormat(_ text: String) -> String {
    let pattern = "%(\\d+)\\$s"
    guard let regex = try? NSRegularExpression(pattern: pattern) else { return text.replacingOccurrences(of: "%s", with: "%@") }
    let range = NSRange(text.startIndex..., in: text)
    let replaced = regex.stringByReplacingMatches(in: text, range: range, withTemplate: "%$1$@")
    return replaced.replacingOccurrences(of: "%s", with: "%@")
}

struct Copy {
    var language: AppLanguage

    func text(_ key: String) -> String {
        bundle().localizedString(forKey: key, value: key, table: nil)
    }

    func format(_ key: String, _ args: CVarArg...) -> String {
        let pattern = androidToSwiftFormat(text(key))
        return String(format: pattern, locale: language.formatLocale, arguments: args)
    }

    func plural(_ key: String, _ count: Int) -> String {
        let languageCode = nominatimAcceptLanguage(language.nominatimTag)
        let category = pluralCategory(count: count, language: languageCode)
        let specific = text("\(key)_\(category)")
        let pattern = specific == "\(key)_\(category)" ? text("\(key)_other") : specific
        return String(format: androidToSwiftFormat(pattern), locale: language.formatLocale, count)
    }

    func age(since epochMs: Int64, now: Int64) -> String {
        let minutes = max(0, (now - epochMs) / 60_000)
        if minutes < 1 { return text("age_just_now") }
        if minutes < 60 { return plural("age_minutes", Int(minutes)) }
        if minutes < 60 * 24 { return plural("age_hours", Int(minutes / 60)) }
        return plural("age_days", Int(minutes / (60 * 24)))
    }

    func integer(_ value: Int64) -> String {
        formatInteger(value, language.formatLocale)
    }

    func altitudeNumber(_ meters: Double, _ unit: AltitudeUnit) -> String {
        let value = unit == .feet ? metersToFeet(meters) : meters
        return integer(Int64(value.rounded()))
    }

    func altitudeReading(_ meters: Double, _ unit: AltitudeUnit) -> String {
        format("format_altitude", altitudeNumber(meters, unit), text(unit == .feet ? "unit_feet_symbol" : "unit_meters_symbol"))
    }

    func accuracy(_ meters: Double, _ unit: AltitudeUnit) -> String {
        let value = unit == .feet ? metersToFeet(meters) : meters
        return format("format_accuracy", integer(Int64(value.rounded())), text(unit == .feet ? "unit_feet_symbol" : "unit_meters_symbol"))
    }

    func coordinate(_ value: Double, latitude: Bool) -> String {
        let number = formatDecimal(abs(value), fractionDigits: 5, language.formatLocale)
        let hemisphere: String
        if latitude {
            hemisphere = text(value >= 0 ? "hemisphere_n" : "hemisphere_s")
        } else {
            hemisphere = text(value >= 0 ? "hemisphere_e" : "hemisphere_w")
        }
        return format("format_coordinate", number, hemisphere)
    }

    func coordinatePair(latitude: Double, longitude: Double) -> String {
        format("format_coordinate_pair", coordinate(latitude, latitude: true), coordinate(longitude, latitude: false))
    }

    func displayClock(_ epochMs: Int64, zone: TimeZone = .current) -> String {
        formatPattern(epochMs, zone: zone, pattern: text("pattern_display_clock"), locale: language.formatLocale)
    }

    func watermarkStamp(_ epochMs: Int64, zone: TimeZone = .current) -> String {
        let date = formatPattern(epochMs, zone: zone, pattern: text("pattern_watermark_date"), locale: language.formatLocale)
        let time = formatPattern(epochMs, zone: zone, pattern: text("pattern_watermark_time"), locale: language.formatLocale)
        return format("format_watermark_stamp", date, time)
    }

    func watermark(
        altitudeMeters: Double?,
        absoluteMsl: Bool,
        unit: AltitudeUnit,
        latitude: Double?,
        longitude: Double?,
        place: String?,
        epochMs: Int64
    ) -> WatermarkContent {
        let altitude: String
        if let altitudeMeters, absoluteMsl {
            altitude = altitudeReading(altitudeMeters, unit)
        } else {
            altitude = text("altitude_unavailable")
        }
        let coordinates: String
        if let latitude, let longitude {
            coordinates = coordinatePair(latitude: latitude, longitude: longitude)
        } else {
            coordinates = text("watermark_awaiting_fix")
        }
        let trimmed = place?.trimmingCharacters(in: .whitespacesAndNewlines)
        return WatermarkContent(
            altitude: altitude,
            coordinates: coordinates,
            place: (trimmed?.isEmpty == false) ? trimmed : nil,
            time: watermarkStamp(epochMs),
            wordmark: text("wordmark")
        )
    }

    func sourceLabel(_ source: AltitudeSource) -> String {
        switch source {
        case .gps: return text("source_gps")
        case .fused: return text("source_fused")
        case .pressureHoldover: return text("source_holdover")
        case .pressureUncalibrated: return text("source_pressure")
        case .none: return text("source_none")
        }
    }

    func themeName(_ theme: DisplayTheme) -> String {
        switch theme {
        case .night: return text("theme_night")
        case .void: return text("theme_void")
        case .glacier: return text("theme_glacier")
        case .saffron: return text("theme_saffron")
        case .snow: return text("theme_snow")
        }
    }

    func watermarkName(_ style: WatermarkStyle) -> String {
        switch style {
        case .plateLight: return text("watermark_light")
        case .plateDark: return text("watermark_dark")
        case .mount: return text("watermark_mount")
        case .caption: return text("watermark_caption")
        }
    }

    func anchorName(_ anchor: CaptionAnchor) -> String {
        anchor == .top ? text("anchor_top") : text("anchor_bottom")
    }

    private func bundle() -> Bundle {
        guard let code = language.bundleCode,
              let path = Bundle.main.path(forResource: code, ofType: "lproj"),
              let bundle = Bundle(path: path) else {
            return .main
        }
        return bundle
    }
}

enum AppVersion {
    static var short: String {
        Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "1.0.0"
    }

    static var build: String {
        Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? "1"
    }

    static var line: String { "\(short) (\(build))" }
}
