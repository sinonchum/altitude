package app.altitude.domain

import java.util.Locale
import kotlin.math.roundToInt

/**
 * Result of one Nominatim reverse lookup.
 * [Reached] means the service answered, including an answer with no locality.
 * [Unreachable] means the device should use the platform geocoder instead.
 */
sealed class OsmLookup {
    data class Reached(val name: String?) : OsmLookup()
    data object Unreachable : OsmLookup()
}

private val ADDRESS_KEYS = listOf(
    "city",
    "town",
    "village",
    "municipality",
    "hamlet",
    "suburb",
    "county",
    "state",
)

private val LOCALITY_TYPES = setOf(
    "city",
    "town",
    "village",
    "municipality",
    "hamlet",
    "suburb",
    "county",
    "state",
    "administrative",
    "peak",
    "island",
    "water",
    "natural",
)

private val PINYIN_SYLLABLE = Regex(
    "(?i)^(?:zh|ch|sh|b|p|m|f|d|t|n|l|g|k|h|j|q|x|r|z|c|s|y|w)?" +
        "(?:iang|iong|uang|ueng|ian|iao|uai|uan|ang|eng|ing|ong|" +
        "ai|an|ao|ei|en|er|ia|ie|in|iu|ou|ua|ue|ui|un|uo|ve|" +
        "a|e|i|o|u|v|ü|üe)$",
)

fun nominatimAcceptLanguage(languageTag: String): String {
    return languageTag.substringBefore(',').substringBefore('-').substringBefore('_')
        .lowercase(Locale.ROOT)
        .ifBlank { "en" }
}

fun requestsLatinPlaceName(languageTag: String): Boolean {
    val language = nominatimAcceptLanguage(languageTag)
    return language == "en" || language == "fr"
}

fun containsHan(text: String): Boolean {
    return text.any { char ->
        val code = char.code
        code in 0x3400..0x4DBF || code in 0x4E00..0x9FFF || code in 0xF900..0xFAFF
    }
}

/**
 * Short locality from a Nominatim reverse payload.
 * The object's own name wins for a city, town, or similar result.
 * A road or building falls through to the enclosing locality.
 */
fun osmLocalityName(
    name: String?,
    address: Map<String, String>,
    addresstype: String? = null,
    displayName: String? = null,
): String? {
    val objectName = name?.trim()?.takeIf { it.isNotEmpty() }
    val fromAddress = ADDRESS_KEYS.firstNotNullOfOrNull { key ->
        address[key]?.trim()?.takeIf { it.isNotEmpty() }
    }
    val specificPlace = addresstype.isNullOrBlank() || addresstype in LOCALITY_TYPES
    if (specificPlace && objectName != null) return objectName
    if (fromAddress != null) return fromAddress
    if (objectName != null) return objectName
    return displayName?.substringBefore(',')?.trim()?.takeIf { it.isNotEmpty() }
}

/**
 * Picks the label for the app language.
 * A reached Nominatim name is kept, including Nominatim's own language fallback.
 * The geocoder name is used only when Nominatim could not be reached.
 * Han text in English or French is passed through [transliterate]; a null result
 * keeps the original characters.
 */
fun choosePlaceLabel(
    languageTag: String,
    osm: OsmLookup,
    geocoderName: String?,
    transliterate: (String) -> String?,
): String? {
    val fromGeocoder = osm is OsmLookup.Unreachable
    val raw = when (osm) {
        is OsmLookup.Reached -> osm.name
        OsmLookup.Unreachable -> geocoderName
    }?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return presentPlaceName(raw, languageTag, fromGeocoder, transliterate)
}

fun formatTransliteration(raw: String): String {
    return raw.split(Regex("[,;]"))
        .map(::joinSegment)
        .filter { it.isNotEmpty() }
        .joinToString(", ")
}

fun placeCacheKey(latitude: Double, longitude: Double, languageTag: String): String {
    val lat = (latitude * 100.0).roundToInt()
    val lon = (longitude * 100.0).roundToInt()
    return "$lat:$lon:${nominatimAcceptLanguage(languageTag)}"
}

fun formatCoordinateQuery(value: Double): String = String.format(Locale.US, "%.5f", value)

/** Milliseconds to wait so reverse-geocode calls stay at or under one per second. */
fun millisUntilNextPlaceRequest(nowMs: Long, previousStartedMs: Long, intervalMs: Long = 1_000L): Long {
    if (previousStartedMs <= 0L) return 0L
    return (intervalMs - (nowMs - previousStartedMs)).coerceAtLeast(0L)
}

private fun presentPlaceName(
    raw: String,
    languageTag: String,
    fromGeocoder: Boolean,
    transliterate: (String) -> String?,
): String {
    if (!requestsLatinPlaceName(languageTag)) return raw
    if (containsHan(raw)) {
        val transliterated = transliterate(raw)?.trim()?.takeIf { it.isNotEmpty() } ?: return raw
        if (containsHan(transliterated)) return raw
        return formatTransliteration(transliterated)
    }
    if (fromGeocoder && looksLikeSyllableSplitPinyin(raw)) return formatTransliteration(raw)
    return raw
}

private fun looksLikeSyllableSplitPinyin(text: String): Boolean {
    val tokens = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (tokens.size < 2) return false
    return tokens.all(::isPinyinSyllable)
}

private fun joinSegment(segment: String): String {
    val tokens = segment.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (tokens.isEmpty()) return ""
    val words = mutableListOf<String>()
    val syllables = mutableListOf<String>()
    fun flush() {
        if (syllables.isEmpty()) return
        val joined = syllables.joinToString("") { it.lowercase(Locale.ROOT) }
        words += joined.replaceFirstChar { char ->
            if (char.isLowerCase()) char.titlecase(Locale.ROOT) else char.toString()
        }
        syllables.clear()
    }
    for (token in tokens) {
        if (isPinyinSyllable(token)) syllables += token else {
            flush()
            words += token
        }
    }
    flush()
    return words.joinToString(" ")
}

private fun isPinyinSyllable(token: String): Boolean {
    val letters = token.trim('\'', '’', '.')
    if (letters.isEmpty() || letters.any { !it.isLetter() }) return false
    return PINYIN_SYLLABLE.matches(letters)
}
