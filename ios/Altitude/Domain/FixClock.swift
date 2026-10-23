import Foundation

enum FixClock {
    static let fusionWindowMs: Int64 = 12_000
    static let coordinateWindowMs: Int64 = 30_000
    static let allowedSkewMs: Int64 = 2_000

    static func ageMillis(
        elapsedRealtimeMs: Int64,
        wallTimeMs: Int64,
        nowElapsedMs: Int64,
        nowWallMs: Int64
    ) -> Int64? {
        if elapsedRealtimeMs > 0 { return nowElapsedMs - elapsedRealtimeMs }
        if wallTimeMs <= 0 { return nil }
        return nowWallMs - wallTimeMs
    }

    static func accept(_ ageMillis: Int64?, _ windowMs: Int64) -> Bool {
        guard let ageMillis else { return true }
        return ageMillis >= -allowedSkewMs && ageMillis <= windowMs
    }
}

func activeLocationFeeds(playServicesAvailable: Bool, precisePermission: Bool) -> Set<LocationFeed> {
    var feeds: Set<LocationFeed> = [.network]
    if precisePermission { feeds.insert(.satellite) }
    if playServicesAvailable { feeds.insert(.fused) }
    return feeds
}
