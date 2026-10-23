import Foundation

enum PreferenceKey {
    static let unit = "unit"
    static let theme = "theme"
    static let brightness = "max_brightness"
    static let coordinates = "show_coordinates"
    static let accuracy = "show_accuracy"
    static let clock = "show_clock"
    static let place = "show_place"
    static let dynamic = "dynamic_color"
    static let watermark = "watermark"
    static let anchor = "caption_anchor"
    static let onboarding = "onboarding_complete"
    static let lens = "camera_lens"
    static let screenFlash = "screen_flash"
    static let language = "app_language"
    static let lastKnown = "last_known"
}

struct PreferenceStore {
    private let defaults: UserDefaults

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    func load() -> PrefsSnapshot {
        var prefs = PrefsSnapshot.default
        if let raw = defaults.string(forKey: PreferenceKey.unit), let value = AltitudeUnit(rawValue: raw) { prefs.unit = value }
        if let raw = defaults.string(forKey: PreferenceKey.theme), let value = DisplayTheme(rawValue: raw) { prefs.theme = value }
        if defaults.object(forKey: PreferenceKey.brightness) != nil { prefs.maxBrightness = defaults.bool(forKey: PreferenceKey.brightness) }
        if defaults.object(forKey: PreferenceKey.coordinates) != nil { prefs.showCoordinates = defaults.bool(forKey: PreferenceKey.coordinates) }
        if defaults.object(forKey: PreferenceKey.accuracy) != nil { prefs.showAccuracy = defaults.bool(forKey: PreferenceKey.accuracy) }
        if defaults.object(forKey: PreferenceKey.clock) != nil { prefs.showClock = defaults.bool(forKey: PreferenceKey.clock) }
        if defaults.object(forKey: PreferenceKey.place) != nil { prefs.showPlace = defaults.bool(forKey: PreferenceKey.place) }
        if defaults.object(forKey: PreferenceKey.dynamic) != nil { prefs.dynamicColor = defaults.bool(forKey: PreferenceKey.dynamic) }
        if let raw = defaults.string(forKey: PreferenceKey.watermark), let value = WatermarkStyle(rawValue: raw) { prefs.watermarkStyle = value }
        if let raw = defaults.string(forKey: PreferenceKey.anchor), let value = CaptionAnchor(rawValue: raw) { prefs.captionAnchor = value }
        prefs.onboardingComplete = defaults.bool(forKey: PreferenceKey.onboarding)
        prefs.frontCamera = defaults.string(forKey: PreferenceKey.lens) == CameraLens.front.rawValue
        prefs.screenFlash = defaults.bool(forKey: PreferenceKey.screenFlash)
        return prefs
    }

    func save(_ prefs: PrefsSnapshot) {
        defaults.set(prefs.unit.rawValue, forKey: PreferenceKey.unit)
        defaults.set(prefs.theme.rawValue, forKey: PreferenceKey.theme)
        defaults.set(prefs.maxBrightness, forKey: PreferenceKey.brightness)
        defaults.set(prefs.showCoordinates, forKey: PreferenceKey.coordinates)
        defaults.set(prefs.showAccuracy, forKey: PreferenceKey.accuracy)
        defaults.set(prefs.showClock, forKey: PreferenceKey.clock)
        defaults.set(prefs.showPlace, forKey: PreferenceKey.place)
        defaults.set(prefs.dynamicColor, forKey: PreferenceKey.dynamic)
        defaults.set(prefs.watermarkStyle.rawValue, forKey: PreferenceKey.watermark)
        defaults.set(prefs.captionAnchor.rawValue, forKey: PreferenceKey.anchor)
        defaults.set(prefs.onboardingComplete, forKey: PreferenceKey.onboarding)
        defaults.set(prefs.frontCamera ? CameraLens.front.rawValue : CameraLens.back.rawValue, forKey: PreferenceKey.lens)
        defaults.set(prefs.screenFlash, forKey: PreferenceKey.screenFlash)
    }

    func loadLanguage() -> AppLanguage {
        guard let raw = defaults.string(forKey: PreferenceKey.language), let language = AppLanguage(rawValue: raw) else {
            return .system
        }
        return language
    }

    func saveLanguage(_ language: AppLanguage) {
        defaults.set(language.rawValue, forKey: PreferenceKey.language)
    }

    func loadLastKnown() -> LastKnown? {
        guard let data = defaults.data(forKey: PreferenceKey.lastKnown) else { return nil }
        return try? JSONDecoder().decode(LastKnown.self, from: data)
    }

    func saveLastKnown(_ known: LastKnown) {
        if let data = try? JSONEncoder().encode(known) {
            defaults.set(data, forKey: PreferenceKey.lastKnown)
        }
    }
}
