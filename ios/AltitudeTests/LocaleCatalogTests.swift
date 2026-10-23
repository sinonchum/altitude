import XCTest
@testable import Altitude

final class LocaleCatalogTests: XCTestCase {
    func testTranslatableKeysMatchAcrossLocales() throws {
        let catalog = try Self.loadCatalog()
        let english = catalog.strings
        let translated = ["zh-Hans", "fr"]
        for (name, entry) in english where entry.translatable && !entry.isPlural {
            XCTAssertFalse(entry.text(for: "en").trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && name != "caption_placeholder", name)
            let expected = specifiers(entry.text(for: "en"))
            for language in translated {
                let actual = specifiers(entry.text(for: language))
                XCTAssertEqual(actual, expected, "Format specifiers for \(name) in \(language)")
            }
        }
        for (name, entry) in english where entry.isPlural {
            for language in ["en", "zh-Hans", "fr"] {
                let plural = entry.plural(for: language)
                XCTAssertTrue(plural.keys.contains("other"), "\(name) in \(language) needs quantity other")
                let other = specifiers(plural["other"] ?? "")
                for item in plural.values {
                    XCTAssertFalse(item.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                    XCTAssertEqual(specifiers(item), other)
                }
            }
            XCTAssertEqual(Set(entry.plural(for: "zh-Hans").keys), ["other"])
            XCTAssertEqual(Set(entry.plural(for: "en").keys), ["one", "other"])
            XCTAssertEqual(Set(entry.plural(for: "fr").keys), ["one", "many", "other"])
        }
    }

    func testWordmarkStaysLatinAndUntranslated() throws {
        let catalog = try Self.loadCatalog()
        let wordmark = try XCTUnwrap(catalog.strings["wordmark"])
        XCTAssertEqual(wordmark.text(for: "en"), "ALTITUDE")
        XCTAssertEqual(wordmark.text(for: "zh-Hans"), "ALTITUDE")
        XCTAssertEqual(wordmark.text(for: "fr"), "ALTITUDE")
        XCTAssertEqual(catalog.strings["app_name"]?.text(for: "zh-Hans"), "Altitude")
        XCTAssertEqual(catalog.strings["language_zh"]?.text(for: "fr"), "中文")
        XCTAssertEqual(catalog.strings["language_en"]?.text(for: "zh-Hans"), "English")
        XCTAssertEqual(catalog.strings["language_fr"]?.text(for: "en"), "Français")
    }

    func testWatermarkPatternsAreLocaleAware() throws {
        let catalog = try Self.loadCatalog()
        let zone = TimeZone(identifier: "Asia/Shanghai")!
        let millis = epochMillis(year: 2026, month: 10, day: 22, hour: 16, minute: 42, second: 8, zone: zone)
        XCTAssertEqual(stamp(catalog, "en", millis, zone), "2026.10.22  16:42")
        XCTAssertEqual(stamp(catalog, "zh-Hans", millis, zone), "2026.10.22  16:42")
        XCTAssertEqual(stamp(catalog, "fr", millis, zone), "22.10.2026  16:42")
        XCTAssertEqual(clock(catalog, "en", millis, zone), "16:42:08")
        XCTAssertEqual(clock(catalog, "fr", millis, zone), "16:42:08")
        XCTAssertTrue(catalog.strings["format_altitude"]!.text(for: "fr").contains("\u{202f}"))
        XCTAssertEqual(catalog.strings["hemisphere_w"]!.text(for: "fr"), "O")
        XCTAssertEqual(catalog.strings["hemisphere_w"]!.text(for: "zh-Hans"), "西")
    }

    func testPluralRuntimeCategories() {
        XCTAssertEqual(pluralCategory(count: 1, language: "en"), "one")
        XCTAssertEqual(pluralCategory(count: 2, language: "en"), "other")
        XCTAssertEqual(pluralCategory(count: 1, language: "zh"), "other")
        XCTAssertEqual(pluralCategory(count: 0, language: "fr"), "one")
        XCTAssertEqual(pluralCategory(count: 3, language: "fr"), "other")
        XCTAssertEqual(pluralCategory(count: 1_000_000, language: "fr"), "many")
    }

    private func stamp(_ catalog: Catalog, _ language: String, _ millis: Int64, _ zone: TimeZone) -> String {
        let entry = catalog.strings
        let datePattern = entry["pattern_watermark_date"]!.text(for: language)
        let timePattern = entry["pattern_watermark_time"]!.text(for: language)
        let date = formatPattern(millis, zone: zone, pattern: datePattern)
        let time = formatPattern(millis, zone: zone, pattern: timePattern)
        return entry["format_watermark_stamp"]!.text(for: language)
            .replacingOccurrences(of: "%1$s", with: date)
            .replacingOccurrences(of: "%2$s", with: time)
    }

    private func clock(_ catalog: Catalog, _ language: String, _ millis: Int64, _ zone: TimeZone) -> String {
        let pattern = catalog.strings["pattern_display_clock"]!.text(for: language)
        return formatPattern(millis, zone: zone, pattern: pattern)
    }

    private func specifiers(_ value: String) -> [String] {
        let regex = try! NSRegularExpression(pattern: "%(?:\\d+\\$)?[sd]")
        let range = NSRange(value.startIndex..., in: value)
        return regex.matches(in: value, range: range).map { match in
            String(value[Range(match.range, in: value)!])
        }
    }

    private static func loadCatalog() throws -> Catalog {
        let bundle = Bundle(for: LocaleCatalogTests.self)
        let url = bundle.url(forResource: "Localizable.catalog", withExtension: "json")
            ?? bundle.url(forResource: "Localizable", withExtension: "xcstrings")
        let resolved = try XCTUnwrap(url)
        let data = try Data(contentsOf: resolved)
        return try JSONDecoder().decode(Catalog.self, from: data)
    }
}

private struct Catalog: Decodable {
    var strings: [String: CatalogEntry]
}

private struct CatalogEntry: Decodable {
    var localizations: [String: CatalogLocalization]

    var translatable: Bool {
        localizations["zh-Hans"] != nil || localizations["fr"] != nil || isPlural
    }

    var isPlural: Bool {
        localizations["en"]?.variations?.plural != nil
    }

    func text(for language: String) -> String {
        localizations[language]?.stringUnit?.value ?? ""
    }

    func plural(for language: String) -> [String: String] {
        let plural = localizations[language]?.variations?.plural ?? [:]
        return plural.mapValues { $0.stringUnit?.value ?? "" }
    }
}

private struct CatalogLocalization: Decodable {
    var stringUnit: CatalogUnit?
    var variations: CatalogVariations?
}

private struct CatalogUnit: Decodable {
    var value: String
}

private struct CatalogVariations: Decodable {
    var plural: [String: CatalogLocalization]?
}
