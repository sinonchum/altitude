package app.altitude.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FixClockTest {
    @Test
    fun elapsedRealtimeBeatsAWallClockThatIsAhead() {
        val age = FixClock.ageMillis(
            elapsedRealtimeMs = 10_000L,
            wallTimeMs = 9_999_000L,
            nowElapsedMs = 10_400L,
            nowWallMs = 5_000L,
        )
        assertEquals(400L, age)
        assertTrue(FixClock.accept(age, FixClock.FUSION_WINDOW_MS))
    }

    @Test
    fun smallClockSkewStillCountsAsFresh() {
        val age = FixClock.ageMillis(
            elapsedRealtimeMs = 0L,
            wallTimeMs = 20_000L,
            nowElapsedMs = 0L,
            nowWallMs = 19_000L,
        )
        assertEquals(-1_000L, age)
        assertTrue(FixClock.accept(age, FixClock.FUSION_WINDOW_MS))
    }

    @Test
    fun staleFixIsRejectedAndMissingTimestampIsKept() {
        assertFalse(FixClock.accept(FixClock.FUSION_WINDOW_MS + 1, FixClock.FUSION_WINDOW_MS))
        assertTrue(FixClock.accept(null, FixClock.FUSION_WINDOW_MS))
        assertTrue(
            FixClock.accept(
                FixClock.COORDINATE_WINDOW_MS,
                FixClock.COORDINATE_WINDOW_MS,
            ),
        )
    }
}

class LocationFeedsTest {
    @Test
    fun playServicesDoesNotReplaceSatelliteFixes() {
        val feeds = activeLocationFeeds(playServicesAvailable = true, precisePermission = true)
        assertTrue(LocationFeed.SATELLITE in feeds)
        assertTrue(LocationFeed.FUSED in feeds)
        assertTrue(LocationFeed.NETWORK in feeds)
    }

    @Test
    fun approximatePermissionSkipsSatelliteAndStillUsesNetwork() {
        val feeds = activeLocationFeeds(playServicesAvailable = false, precisePermission = false)
        assertFalse(LocationFeed.SATELLITE in feeds)
        assertFalse(LocationFeed.FUSED in feeds)
        assertTrue(LocationFeed.NETWORK in feeds)
    }
}
