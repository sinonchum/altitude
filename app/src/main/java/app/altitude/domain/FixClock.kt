package app.altitude.domain

/**
 * Decides whether a location fix is recent enough to show or to feed the filter.
 * Elapsed-realtime age is preferred because wall-clock GPS time is often a second
 * or two away from the phone clock, which used to drop every fix.
 */
object FixClock {
    const val FUSION_WINDOW_MS = 12_000L
    const val COORDINATE_WINDOW_MS = 30_000L
    const val ALLOWED_SKEW_MS = 2_000L

    fun ageMillis(
        elapsedRealtimeMs: Long,
        wallTimeMs: Long,
        nowElapsedMs: Long,
        nowWallMs: Long,
    ): Long? {
        if (elapsedRealtimeMs > 0L) return nowElapsedMs - elapsedRealtimeMs
        if (wallTimeMs <= 0L) return null
        return nowWallMs - wallTimeMs
    }

    fun accept(ageMillis: Long?, windowMs: Long): Boolean {
        if (ageMillis == null) return true
        return ageMillis >= -ALLOWED_SKEW_MS && ageMillis <= windowMs
    }
}
