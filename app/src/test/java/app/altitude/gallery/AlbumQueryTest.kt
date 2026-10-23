package app.altitude.gallery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumQueryTest {
    @Test
    fun modernQueryMatchesTheAltitudeAlbumAndSkipsPendingRows() {
        val (clause, args) = AlbumQuery.selection(29)
        assertTrue(clause.contains("relative_path"))
        assertTrue(clause.contains("is_pending"))
        assertEquals("%Pictures/Altitude%", args.single())
    }

    @Test
    fun legacyQueryMatchesThePublicPicturesPath() {
        val (clause, args) = AlbumQuery.selection(28)
        assertTrue(clause.contains("_data"))
        assertEquals("%/Pictures/Altitude/%", args.single())
    }
}
