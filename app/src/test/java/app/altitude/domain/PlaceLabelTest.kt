package app.altitude.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceLabelTest {
    @Test
    fun englishKeepsTheNominatimNameAheadOfTheGeocoder() {
        var transliterated = false
        val label = choosePlaceLabel(
            languageTag = "en",
            osm = OsmLookup.Reached("Shigatse"),
            geocoderName = "Ri Ka Ze Shi",
        ) {
            transliterated = true
            "ri ka ze shi"
        }
        assertEquals("Shigatse", label)
        assertFalse(transliterated)
    }

    @Test
    fun frenchAndChineseKeepNominatimFallbackNames() {
        assertEquals(
            "Shigatsé",
            choosePlaceLabel("fr-FR", OsmLookup.Reached("Shigatsé"), "日喀则市") { null },
        )
        assertEquals(
            "日喀则",
            choosePlaceLabel("zh-CN", OsmLookup.Reached("日喀则"), "Shigatse") { "ri ka ze" },
        )
        assertEquals(
            "香港",
            choosePlaceLabel("zh-Hans", OsmLookup.Reached("香港"), "Hong Kong") { "xiang gang" },
        )
    }

    @Test
    fun hanInEnglishOrFrenchIsTransliteratedAndJoined() {
        assertEquals(
            "Rikazeshi",
            choosePlaceLabel("en", OsmLookup.Reached("日喀则市"), null) { "ri ka ze shi" },
        )
        assertEquals(
            "Rikazeshi",
            choosePlaceLabel("fr", OsmLookup.Reached("日喀则市"), "Shigatse") { "Ri Ka Ze Shi" },
        )
    }

    @Test
    fun missingTransliteratorKeepsTheHanName() {
        assertEquals(
            "日喀则市",
            choosePlaceLabel("en", OsmLookup.Reached("日喀则市"), "Ri Ka Ze Shi") { null },
        )
        assertEquals(
            "日喀则市",
            choosePlaceLabel("fr", OsmLookup.Unreachable, "日喀则市") { "日喀则市" },
        )
    }

    @Test
    fun geocoderRunsOnlyWhenNominatimIsUnreachable() {
        assertEquals(
            "Shigatse",
            choosePlaceLabel("en", OsmLookup.Unreachable, "Shigatse") { null },
        )
        assertEquals(
            "Rikazeshi",
            choosePlaceLabel("en", OsmLookup.Unreachable, "Ri Ka Ze Shi") { null },
        )
        assertEquals(
            "Rikazeshi",
            choosePlaceLabel("fr", OsmLookup.Unreachable, "日喀则市") { "ri ka ze shi" },
        )
        assertNull(choosePlaceLabel("en", OsmLookup.Reached(null), "Ri Ka Ze Shi") { null })
        assertNull(choosePlaceLabel("en", OsmLookup.Reached("  "), "Shigatse") { null })
        assertNull(choosePlaceLabel("zh", OsmLookup.Unreachable, "  ") { null })
    }

    @Test
    fun multiWordOsmNamesStayUntouched() {
        assertEquals(
            "Hong Kong",
            choosePlaceLabel("en", OsmLookup.Reached("Hong Kong"), null) { null },
        )
        assertEquals(
            "Everest Base Camp",
            choosePlaceLabel("fr", OsmLookup.Reached("Everest Base Camp"), null) { null },
        )
        assertEquals(
            "日喀则市",
            choosePlaceLabel("zh-CN", OsmLookup.Unreachable, "日喀则市") { "ri ka ze shi" },
        )
    }

    @Test
    fun transliterationJoinsSyllablesIntoWords() {
        assertEquals("Rikazeshi", formatTransliteration("Ri Ka Ze Shi"))
        assertEquals("Rikazeshi", formatTransliteration("ri ka ze shi"))
        assertEquals("Beijing", formatTransliteration("bei jing"))
        assertEquals("Lasa", formatTransliteration("la sa"))
        assertEquals("Mount Zhufeng", formatTransliteration("Mount zhu feng"))
        assertEquals("Rikazeshi, Beijing", formatTransliteration("ri ka ze shi, bei jing"))
        assertFalse(formatTransliteration("Ri Ka Ze Shi").contains(" "))
    }

    @Test
    fun nominatimLanguageAndCacheKeyFollowTheAppLanguage() {
        assertEquals("en", nominatimAcceptLanguage("en"))
        assertEquals("fr", nominatimAcceptLanguage("fr-FR"))
        assertEquals("zh", nominatimAcceptLanguage("zh-CN"))
        assertEquals("29.25000", formatCoordinateQuery(29.25))
        assertEquals("-88.88000", formatCoordinateQuery(-88.88))
        assertEquals(placeCacheKey(29.267, 88.881, "en"), placeCacheKey(29.271, 88.884, "en"))
        assertTrue(placeCacheKey(29.267, 88.881, "en") != placeCacheKey(29.267, 88.881, "fr"))
        assertTrue(placeCacheKey(29.267, 88.881, "zh-CN").endsWith(":zh"))
    }

    @Test
    fun requestsStayAtMostOnePerSecond() {
        assertEquals(0L, millisUntilNextPlaceRequest(1_000, 0L))
        assertEquals(1_000L, millisUntilNextPlaceRequest(1_000, 1_000))
        assertEquals(400L, millisUntilNextPlaceRequest(1_600, 1_000))
        assertEquals(0L, millisUntilNextPlaceRequest(2_000, 1_000))
    }

    @Test
    fun localityPrefersThePlaceOverARoadOrCountry() {
        assertEquals(
            "Shigatse",
            osmLocalityName(
                name = "Shigatse",
                address = mapOf("city" to "Shigatse", "state" to "Tibet", "country" to "China"),
                addresstype = "city",
                displayName = "Shigatse, Tibet, China",
            ),
        )
        assertEquals(
            "Shigatse",
            osmLocalityName(
                name = "Beijing Road",
                address = mapOf("city" to "Shigatse", "country" to "China"),
                addresstype = "road",
            ),
        )
        assertEquals(
            "Mount Everest",
            osmLocalityName(
                name = "Mount Everest",
                address = mapOf("county" to "Tingri"),
                addresstype = "peak",
            ),
        )
        assertEquals(
            "Lhasa",
            osmLocalityName(name = null, address = mapOf("city" to "Lhasa", "country" to "China")),
        )
        assertEquals(
            "Tibet",
            osmLocalityName(
                name = null,
                address = emptyMap(),
                displayName = "Tibet, China",
            ),
        )
    }
}
