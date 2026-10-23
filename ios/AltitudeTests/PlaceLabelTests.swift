import XCTest
@testable import Altitude

final class PlaceLabelTests: XCTestCase {
    func testEnglishKeepsTheNominatimNameAheadOfTheGeocoder() {
        var transliterated = false
        let label = choosePlaceLabel(languageTag: "en", osm: .reached("Shigatse"), geocoderName: "Ri Ka Ze Shi") { _ in
            transliterated = true
            return "ri ka ze shi"
        }
        XCTAssertEqual(label, "Shigatse")
        XCTAssertFalse(transliterated)
    }

    func testFrenchAndChineseKeepNominatimFallbackNames() {
        XCTAssertEqual(choosePlaceLabel(languageTag: "fr-FR", osm: .reached("Shigatsé"), geocoderName: "日喀则市") { _ in nil }, "Shigatsé")
        XCTAssertEqual(choosePlaceLabel(languageTag: "zh-CN", osm: .reached("日喀则"), geocoderName: "Shigatse") { _ in "ri ka ze" }, "日喀则")
        XCTAssertEqual(choosePlaceLabel(languageTag: "zh-Hans", osm: .reached("香港"), geocoderName: "Hong Kong") { _ in "xiang gang" }, "香港")
    }

    func testHanInEnglishOrFrenchIsTransliteratedAndJoined() {
        XCTAssertEqual(choosePlaceLabel(languageTag: "en", osm: .reached("日喀则市"), geocoderName: nil) { _ in "ri ka ze shi" }, "Rikazeshi")
        XCTAssertEqual(choosePlaceLabel(languageTag: "fr", osm: .reached("日喀则市"), geocoderName: "Shigatse") { _ in "Ri Ka Ze Shi" }, "Rikazeshi")
    }

    func testMissingTransliteratorKeepsTheHanName() {
        XCTAssertEqual(choosePlaceLabel(languageTag: "en", osm: .reached("日喀则市"), geocoderName: "Ri Ka Ze Shi") { _ in nil }, "日喀则市")
        XCTAssertEqual(choosePlaceLabel(languageTag: "fr", osm: .unreachable, geocoderName: "日喀则市") { _ in "日喀则市" }, "日喀则市")
    }

    func testGeocoderRunsOnlyWhenNominatimIsUnreachable() {
        XCTAssertEqual(choosePlaceLabel(languageTag: "en", osm: .unreachable, geocoderName: "Shigatse") { _ in nil }, "Shigatse")
        XCTAssertEqual(choosePlaceLabel(languageTag: "en", osm: .unreachable, geocoderName: "Ri Ka Ze Shi") { _ in nil }, "Rikazeshi")
        XCTAssertEqual(choosePlaceLabel(languageTag: "fr", osm: .unreachable, geocoderName: "日喀则市") { _ in "ri ka ze shi" }, "Rikazeshi")
        XCTAssertNil(choosePlaceLabel(languageTag: "en", osm: .reached(nil), geocoderName: "Ri Ka Ze Shi") { _ in nil })
        XCTAssertNil(choosePlaceLabel(languageTag: "en", osm: .reached("  "), geocoderName: "Shigatse") { _ in nil })
        XCTAssertNil(choosePlaceLabel(languageTag: "zh", osm: .unreachable, geocoderName: "  ") { _ in nil })
    }

    func testMultiWordOsmNamesStayUntouched() {
        XCTAssertEqual(choosePlaceLabel(languageTag: "en", osm: .reached("Hong Kong"), geocoderName: nil) { _ in nil }, "Hong Kong")
        XCTAssertEqual(choosePlaceLabel(languageTag: "fr", osm: .reached("Everest Base Camp"), geocoderName: nil) { _ in nil }, "Everest Base Camp")
        XCTAssertEqual(choosePlaceLabel(languageTag: "zh-CN", osm: .unreachable, geocoderName: "日喀则市") { _ in "ri ka ze shi" }, "日喀则市")
    }

    func testTransliterationJoinsSyllablesIntoWords() {
        XCTAssertEqual(formatTransliteration("Ri Ka Ze Shi"), "Rikazeshi")
        XCTAssertEqual(formatTransliteration("ri ka ze shi"), "Rikazeshi")
        XCTAssertEqual(formatTransliteration("bei jing"), "Beijing")
        XCTAssertEqual(formatTransliteration("la sa"), "Lasa")
        XCTAssertEqual(formatTransliteration("Mount zhu feng"), "Mount Zhufeng")
        XCTAssertEqual(formatTransliteration("ri ka ze shi, bei jing"), "Rikazeshi, Beijing")
        XCTAssertFalse(formatTransliteration("Ri Ka Ze Shi").contains(" "))
    }

    func testNominatimLanguageAndCacheKeyFollowTheAppLanguage() {
        XCTAssertEqual(nominatimAcceptLanguage("en"), "en")
        XCTAssertEqual(nominatimAcceptLanguage("fr-FR"), "fr")
        XCTAssertEqual(nominatimAcceptLanguage("zh-CN"), "zh")
        XCTAssertEqual(formatCoordinateQuery(29.25), "29.25000")
        XCTAssertEqual(formatCoordinateQuery(-88.88), "-88.88000")
        XCTAssertEqual(placeCacheKey(latitude: 29.267, longitude: 88.881, languageTag: "en"), placeCacheKey(latitude: 29.271, longitude: 88.884, languageTag: "en"))
        XCTAssertNotEqual(placeCacheKey(latitude: 29.267, longitude: 88.881, languageTag: "en"), placeCacheKey(latitude: 29.267, longitude: 88.881, languageTag: "fr"))
        XCTAssertTrue(placeCacheKey(latitude: 29.267, longitude: 88.881, languageTag: "zh-CN").hasSuffix(":zh"))
    }

    func testRequestsStayAtMostOnePerSecond() {
        XCTAssertEqual(millisUntilNextPlaceRequest(nowMs: 1_000, previousStartedMs: 0), 0)
        XCTAssertEqual(millisUntilNextPlaceRequest(nowMs: 1_000, previousStartedMs: 1_000), 1_000)
        XCTAssertEqual(millisUntilNextPlaceRequest(nowMs: 1_600, previousStartedMs: 1_000), 400)
        XCTAssertEqual(millisUntilNextPlaceRequest(nowMs: 2_000, previousStartedMs: 1_000), 0)
    }

    func testLocalityPrefersThePlaceOverARoadOrCountry() {
        XCTAssertEqual(
            osmLocalityName(name: "Shigatse", address: ["city": "Shigatse", "state": "Tibet", "country": "China"], addresstype: "city", displayName: "Shigatse, Tibet, China"),
            "Shigatse"
        )
        XCTAssertEqual(
            osmLocalityName(name: "Beijing Road", address: ["city": "Shigatse", "country": "China"], addresstype: "road"),
            "Shigatse"
        )
        XCTAssertEqual(
            osmLocalityName(name: "Mount Everest", address: ["county": "Tingri"], addresstype: "peak"),
            "Mount Everest"
        )
        XCTAssertEqual(osmLocalityName(name: nil, address: ["city": "Lhasa", "country": "China"]), "Lhasa")
        XCTAssertEqual(osmLocalityName(name: nil, address: [:], displayName: "Tibet, China"), "Tibet")
    }

    func testShigatsePayloadsUseTheCityNotTheSuburb() throws {
        XCTAssertEqual(try displayed(Self.shigatseEn, "en"), "Shigatse")
        XCTAssertEqual(try displayed(Self.shigatseFr, "fr"), "Shigatse")
        XCTAssertEqual(try displayed(Self.shigatseZh, "zh"), "日喀则市")
        XCTAssertEqual(try displayed(Self.shigatseZh, "zh-CN"), "日喀则市")
    }

    func testLatinSiblingReplacesHanBeforeTransliteration() {
        let address = ["city": "日喀则市", "region": "Shigatse Prefecture", "state": "Xizang"]
        XCTAssertEqual(osmLocalityName(name: "城南街道", address: address, addresstype: "suburb", languageTag: "en"), "Shigatse Prefecture")
        XCTAssertEqual(osmLocalityName(name: "城南街道", address: address, addresstype: "suburb", languageTag: "fr"), "Shigatse Prefecture")
        XCTAssertEqual(osmLocalityName(name: "城南街道", address: address, addresstype: "suburb", languageTag: "zh"), "日喀则市")
        var transliterated = false
        let english = choosePlaceLabel(
            languageTag: "en",
            osm: .reached(osmLocalityName(name: "城南街道", address: address, addresstype: "suburb", languageTag: "en")),
            geocoderName: "Ri Ka Ze Shi"
        ) { _ in
            transliterated = true
            return "Chengnanjiedao"
        }
        XCTAssertEqual(english, "Shigatse Prefecture")
        XCTAssertFalse(transliterated)
    }

    private func displayed(_ payload: String, _ language: String) throws -> String? {
        var transliterated = false
        let label = choosePlaceLabel(
            languageTag: language,
            osm: .reached(try nominatimLocality(payload, language)),
            geocoderName: "Chengnan Subdistrict"
        ) { _ in
            transliterated = true
            return "Chengnanjiedao"
        }
        XCTAssertFalse(transliterated)
        return label
    }

    private static let shigatseEn = #"{"place_id":241404443,"licence":"Data © OpenStreetMap contributors, ODbL 1.0. http://osm.org/copyright","osm_type":"relation","osm_id":7699977,"lat":"29.2730757","lon":"88.8781083","category":"boundary","type":"administrative","place_rank":16,"importance":0.32221217881594816,"addresstype":"suburb","name":"Chengnan Subdistrict","display_name":"Chengnan Subdistrict, Shigatse, Samzhubzê District, Shigatse Prefecture, Xizang, China","address":{"suburb":"Chengnan Subdistrict","city":"Shigatse","district":"Samzhubzê District","region":"Shigatse Prefecture","state":"Xizang","ISO3166-2-lvl4":"CN-XZ","country":"China","country_code":"cn"},"boundingbox":["29.2243396","29.2788518","88.8717127","88.9107459"]}"#
    private static let shigatseFr = #"{"place_id":241660121,"licence":"Data © OpenStreetMap contributors, ODbL 1.0. http://osm.org/copyright","osm_type":"relation","osm_id":7699977,"lat":"29.2730757","lon":"88.8781083","category":"boundary","type":"administrative","place_rank":16,"importance":0.32221217881594816,"addresstype":"suburb","name":"城南街道","display_name":"城南街道, Shigatse, District de Samdrubtsé, 日喀则市 གཞིས་ཀ་རྩེ་གྲོང་ཁྱེར།, Xizang, Chine","address":{"suburb":"城南街道","city":"Shigatse","district":"District de Samdrubtsé","region":"日喀则市 གཞིས་ཀ་རྩེ་གྲོང་ཁྱེར།","state":"Xizang","ISO3166-2-lvl4":"CN-XZ","country":"Chine","country_code":"cn"},"boundingbox":["29.2243396","29.2788518","88.8717127","88.9107459"]}"#
    private static let shigatseZh = #"{"place_id":241633930,"licence":"Data © OpenStreetMap contributors, ODbL 1.0. http://osm.org/copyright","osm_type":"relation","osm_id":7699977,"lat":"29.2730757","lon":"88.8781083","category":"boundary","type":"administrative","place_rank":16,"importance":0.32221217881594816,"addresstype":"suburb","name":"城南街道","display_name":"城南街道, 日喀则市, 桑珠孜区, 日喀则市, 西藏自治区, 中国","address":{"suburb":"城南街道","city":"日喀则市","district":"桑珠孜区","region":"日喀则市","state":"西藏自治区","ISO3166-2-lvl4":"CN-XZ","country":"中国","country_code":"cn"},"boundingbox":["29.2243396","29.2788518","88.8717127","88.9107459"]}"#
}
