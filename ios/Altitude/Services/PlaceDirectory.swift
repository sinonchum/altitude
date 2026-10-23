import CoreLocation
import Foundation

actor PlaceDirectory {
    private var cache: [String: String] = [:]
    private var lastRequestStartedMs: Int64 = 0
    private let session: URLSession

    init() {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.timeoutIntervalForRequest = 4
        configuration.timeoutIntervalForResource = 4
        session = URLSession(configuration: configuration)
    }

    func resolve(latitude: Double, longitude: Double, language: AppLanguage) async -> String? {
        let tag = language.nominatimTag
        let key = placeCacheKey(latitude: latitude, longitude: longitude, languageTag: tag)
        if let cached = cache[key] { return cached }
        let accept = nominatimAcceptLanguage(tag)
        await waitForSlot()
        let osm = await fetchOsm(latitude: latitude, longitude: longitude, acceptLanguage: accept)
        let geocoderName: String?
        if case .unreachable = osm {
            geocoderName = await appleName(latitude: latitude, longitude: longitude, language: language)
        } else {
            geocoderName = nil
        }
        let label = choosePlaceLabel(languageTag: tag, osm: osm, geocoderName: geocoderName, transliterate: transliterateHan)
        if case .reached = osm, let label, !label.isEmpty {
            cache[key] = label
        }
        return label
    }

    private func waitForSlot() async {
        let now = Int64(ProcessInfo.processInfo.systemUptime * 1000)
        let wait = millisUntilNextPlaceRequest(nowMs: now, previousStartedMs: lastRequestStartedMs)
        if wait > 0 {
            try? await Task.sleep(nanoseconds: UInt64(wait) * 1_000_000)
        }
        lastRequestStartedMs = Int64(ProcessInfo.processInfo.systemUptime * 1000)
    }

    private func fetchOsm(latitude: Double, longitude: Double, acceptLanguage: String) async -> OsmLookup {
        var components = URLComponents(string: "https://nominatim.openstreetmap.org/reverse")
        components?.queryItems = [
            URLQueryItem(name: "format", value: "jsonv2"),
            URLQueryItem(name: "lat", value: formatCoordinateQuery(latitude)),
            URLQueryItem(name: "lon", value: formatCoordinateQuery(longitude)),
            URLQueryItem(name: "zoom", value: "10"),
            URLQueryItem(name: "addressdetails", value: "1"),
            URLQueryItem(name: "accept-language", value: acceptLanguage),
        ]
        guard let url = components?.url else { return .unreachable }
        var request = URLRequest(url: url)
        request.setValue("Altitude/\(AppVersion.short) (https://github.com/sinonchum/altitude)", forHTTPHeaderField: "User-Agent")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue(acceptLanguage, forHTTPHeaderField: "Accept-Language")
        do {
            let (data, response) = try await session.data(for: request)
            guard let http = response as? HTTPURLResponse, (200...299).contains(http.statusCode) else {
                return .unreachable
            }
            let body = String(data: data, encoding: .utf8) ?? ""
            return .reached(try nominatimLocality(body, acceptLanguage))
        } catch is CancellationError {
            return .unreachable
        } catch {
            return .unreachable
        }
    }

    private func appleName(latitude: Double, longitude: Double, language: AppLanguage) async -> String? {
        let geocoder = CLGeocoder()
        let location = CLLocation(latitude: latitude, longitude: longitude)
        let locale = language.formatLocale
        do {
            let marks = try await geocoder.reverseGeocodeLocation(location, preferredLocale: locale)
            guard let mark = marks.first else { return nil }
            return [mark.locality, mark.subAdministrativeArea, mark.administrativeArea, mark.name]
                .compactMap { $0?.trimmingCharacters(in: .whitespacesAndNewlines) }
                .first { !$0.isEmpty }
        } catch {
            return nil
        }
    }
}

func transliterateHan(_ text: String) -> String? {
    let latin = text.applyingTransform(.toLatin, reverse: false)
    let ascii = latin?.applyingTransform(.stripDiacritics, reverse: false)
    let trimmed = ascii?.trimmingCharacters(in: .whitespacesAndNewlines)
    return (trimmed?.isEmpty == false) ? trimmed : nil
}
