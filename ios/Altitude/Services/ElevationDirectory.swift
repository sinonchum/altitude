import Foundation

/// Copernicus DEM heights from Open-Meteo. Cached, and spaced at least one second apart. A failed point stays nil.
actor ElevationDirectory {
    private var cache: [String: Double] = [:]
    private var lastRequestStartedMs: Int64 = 0
    private let session: URLSession

    init() {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.timeoutIntervalForRequest = 8
        configuration.timeoutIntervalForResource = 8
        session = URLSession(configuration: configuration)
    }

    func elevations(_ points: [(Double, Double)]) async -> [Double?] {
        if points.isEmpty { return [] }
        let missing = points.enumerated().filter { cache[elevationCacheKey($0.element.0, $0.element.1)] == nil }
        if !missing.isEmpty {
            let fetched = await request(missing.map(\.element))
            if let fetched, fetched.count == missing.count {
                for (slot, item) in missing.enumerated() {
                    guard let meters = fetched[slot] else { continue }
                    cache[elevationCacheKey(item.element.0, item.element.1)] = meters
                }
            }
        }
        return points.map { cache[elevationCacheKey($0.0, $0.1)] }
    }

    private func request(_ points: [(Double, Double)]) async -> [Double?]? {
        await waitForSlot()
        guard let url = URL(string: openMeteoElevationUrl(points.map { (latitude: $0.0, longitude: $0.1) })) else { return nil }
        var request = URLRequest(url: url)
        request.setValue("Altitude/\(AppVersion.short) (https://github.com/sinonchum/altitude)", forHTTPHeaderField: "User-Agent")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        do {
            let (data, response) = try await session.data(for: request)
            guard let http = response as? HTTPURLResponse, (200...299).contains(http.statusCode) else { return nil }
            let body = String(data: data, encoding: .utf8) ?? ""
            return parseOpenMeteoElevations(body)
        } catch is CancellationError {
            return nil
        } catch {
            return nil
        }
    }

    private func waitForSlot() async {
        let now = Int64(ProcessInfo.processInfo.systemUptime * 1000)
        let wait = millisUntilNextPlaceRequest(nowMs: now, previousStartedMs: lastRequestStartedMs)
        if wait > 0 {
            try? await Task.sleep(nanoseconds: UInt64(wait) * 1_000_000)
        }
        lastRequestStartedMs = Int64(ProcessInfo.processInfo.systemUptime * 1000)
    }
}
