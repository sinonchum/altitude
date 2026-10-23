package app.altitude.domain

enum class LocationFeed {
    SATELLITE,
    NETWORK,
    FUSED,
}

/**
 * Satellite fixes are requested whenever precise location is granted.
 * A fused provider, even when Play Services is present, does not replace them:
 * on many phones the fused client never delivers a GPS fix.
 */
fun activeLocationFeeds(playServicesAvailable: Boolean, precisePermission: Boolean): Set<LocationFeed> {
    return buildSet {
        add(LocationFeed.NETWORK)
        if (precisePermission) add(LocationFeed.SATELLITE)
        if (playServicesAvailable) add(LocationFeed.FUSED)
    }
}
