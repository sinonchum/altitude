import Foundation

private let settlementKeys = ["city", "town", "village", "municipality", "hamlet"]
private let adminKeys = ["county", "district", "region", "state"]
private let naturalTypes: Set<String> = ["peak", "island", "water", "natural"]

private let pinyinSyllable: NSRegularExpression = {
    let pattern = "(?i)^(?:zh|ch|sh|b|p|m|f|d|t|n|l|g|k|h|j|q|x|r|z|c|s|y|w)?(?:iang|iong|uang|ueng|ian|iao|uai|uan|ang|eng|ing|ong|ai|an|ao|ei|en|er|ia|ie|in|iu|ou|ua|ue|ui|un|uo|ve|a|e|i|o|u|v|ü|üe)$"
    return try! NSRegularExpression(pattern: pattern)
}()

func nominatimAcceptLanguage(_ languageTag: String) -> String {
    let head = languageTag.split(separator: ",").first.map(String.init) ?? languageTag
    let language = head.split(whereSeparator: { $0 == "-" || $0 == "_" }).first.map(String.init) ?? ""
    let lower = language.lowercased()
    return lower.isEmpty ? "en" : lower
}

func requestsLatinPlaceName(_ languageTag: String) -> Bool {
    let language = nominatimAcceptLanguage(languageTag)
    return language == "en" || language == "fr"
}

func containsHan(_ text: String) -> Bool {
    text.unicodeScalars.contains { scalar in
        let code = scalar.value
        return (0x3400...0x4DBF).contains(code) || (0x4E00...0x9FFF).contains(code) || (0xF900...0xFAFF).contains(code)
    }
}

func osmLocalityName(
    name: String?,
    address: [String: String],
    addresstype: String? = nil,
    displayName: String? = nil,
    languageTag: String = "en"
) -> String? {
    if let addresstype, naturalTypes.contains(addresstype) {
        if let trimmed = name?.trimmingCharacters(in: .whitespacesAndNewlines), !trimmed.isEmpty {
            return trimmed
        }
    }
    let candidates = (settlementKeys + adminKeys).compactMap { key -> String? in
        address[key]?.trimmingCharacters(in: .whitespacesAndNewlines)
    }.filter { !$0.isEmpty }
    guard let chosen = candidates.first else {
        guard let displayName else { return nil }
        let head = displayName.split(separator: ",", maxSplits: 1).first.map(String.init) ?? ""
        let trimmed = head.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }
    if requestsLatinPlaceName(languageTag), containsHan(chosen), let latin = candidates.first(where: isLatinName) {
        return latin
    }
    return chosen
}

func nominatimLocality(_ body: String, _ languageTag: String) throws -> String? {
    guard let data = body.data(using: .utf8) else {
        throw PlaceJSONError.malformed
    }
    let rootAny: Any
    do {
        rootAny = try JSONSerialization.jsonObject(with: data)
    } catch {
        throw PlaceJSONError.malformed
    }
    guard let root = rootAny as? [String: Any] else { return nil }
    if root["error"] != nil { return nil }
    var address: [String: String] = [:]
    if let raw = root["address"] as? [String: Any] {
        for (key, value) in raw {
            if let text = value as? String, !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                address[key] = text
            }
        }
    }
    return osmLocalityName(
        name: root["name"] as? String,
        address: address,
        addresstype: root["addresstype"] as? String,
        displayName: root["display_name"] as? String,
        languageTag: languageTag
    )
}

enum PlaceJSONError: Error {
    case malformed
}

func choosePlaceLabel(
    languageTag: String,
    osm: OsmLookup,
    geocoderName: String?,
    transliterate: (String) -> String?
) -> String? {
    let fromGeocoder: Bool
    let rawSource: String?
    switch osm {
    case .reached(let name):
        fromGeocoder = false
        rawSource = name
    case .unreachable:
        fromGeocoder = true
        rawSource = geocoderName
    }
    guard let raw = rawSource?.trimmingCharacters(in: .whitespacesAndNewlines), !raw.isEmpty else { return nil }
    return presentPlaceName(raw, languageTag, fromGeocoder, transliterate)
}

func formatTransliteration(_ raw: String) -> String {
    raw.split { $0 == "," || $0 == ";" }
        .map { joinSegment(String($0)) }
        .filter { !$0.isEmpty }
        .joined(separator: ", ")
}

func placeCacheKey(latitude: Double, longitude: Double, languageTag: String) -> String {
    let lat = Int((latitude * 100).rounded())
    let lon = Int((longitude * 100).rounded())
    return "\(lat):\(lon):\(nominatimAcceptLanguage(languageTag))"
}

func formatCoordinateQuery(_ value: Double) -> String {
    String(format: "%.5f", locale: Locale(identifier: "en_US_POSIX"), value)
}

func millisUntilNextPlaceRequest(nowMs: Int64, previousStartedMs: Int64, intervalMs: Int64 = 1_000) -> Int64 {
    if previousStartedMs <= 0 { return 0 }
    return max(0, intervalMs - (nowMs - previousStartedMs))
}

private func presentPlaceName(
    _ raw: String,
    _ languageTag: String,
    _ fromGeocoder: Bool,
    _ transliterate: (String) -> String?
) -> String {
    if !requestsLatinPlaceName(languageTag) { return raw }
    if containsHan(raw) {
        guard let transliterated = transliterate(raw)?.trimmingCharacters(in: .whitespacesAndNewlines),
              !transliterated.isEmpty else { return raw }
        if containsHan(transliterated) { return raw }
        return formatTransliteration(transliterated)
    }
    if fromGeocoder && looksLikeSyllableSplitPinyin(raw) { return formatTransliteration(raw) }
    return raw
}

private func looksLikeSyllableSplitPinyin(_ text: String) -> Bool {
    let tokens = text.trimmingCharacters(in: .whitespacesAndNewlines).split(whereSeparator: { $0.isWhitespace }).map(String.init)
    if tokens.count < 2 { return false }
    return tokens.allSatisfy(isPinyinSyllable)
}

private func joinSegment(_ segment: String) -> String {
    let tokens = segment.trimmingCharacters(in: .whitespacesAndNewlines).split(whereSeparator: { $0.isWhitespace }).map(String.init)
    if tokens.isEmpty { return "" }
    var words: [String] = []
    var syllables: [String] = []
    func flush() {
        if syllables.isEmpty { return }
        let joined = syllables.map { $0.lowercased() }.joined()
        words.append(titlecasedFirst(joined))
        syllables.removeAll()
    }
    for token in tokens {
        if isPinyinSyllable(token) {
            syllables.append(token)
        } else {
            flush()
            words.append(token)
        }
    }
    flush()
    return words.joined(separator: " ")
}

private func titlecasedFirst(_ word: String) -> String {
    guard let first = word.first else { return word }
    return String(first).uppercased() + word.dropFirst()
}

private func isPinyinSyllable(_ token: String) -> Bool {
    let letters = token.trimmingCharacters(in: CharacterSet(charactersIn: "'’."))
    if letters.isEmpty || letters.contains(where: { !$0.isLetter }) { return false }
    let range = NSRange(letters.startIndex..., in: letters)
    return pinyinSyllable.firstMatch(in: letters, range: range) != nil
}

private func isLatinName(_ text: String) -> Bool {
    var letters = 0
    for scalar in text.unicodeScalars {
        guard CharacterSet.letters.contains(scalar) else { continue }
        letters += 1
        let code = scalar.value
        let latin = code <= 0x007F
            || (0x0080...0x00FF).contains(code)
            || (0x0100...0x017F).contains(code)
            || (0x0180...0x024F).contains(code)
            || (0x1E00...0x1EFF).contains(code)
        if !latin { return false }
    }
    return letters > 0
}
