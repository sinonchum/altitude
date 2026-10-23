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

private val SETTLEMENT_KEYS = listOf(
    "city",
    "town",
    "village",
    "municipality",
    "hamlet",
)

private val ADMIN_KEYS = listOf(
    "county",
    "district",
    "region",
    "state",
)

private val NATURAL_TYPES = setOf(
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
 * Settlement from a Nominatim reverse payload.
 * City, town, village, municipality, then hamlet come before county, district, region, and state.
 * Suburb, neighbourhood, quarter, road, and building names are ignored.
 * The object's own name is used only for a peak, island, water, or other natural feature.
 * In English or French, a Latin name at the same or next level replaces a Han one.
 */
fun osmLocalityName(
    name: String?,
    address: Map<String, String>,
    addresstype: String? = null,
    displayName: String? = null,
    languageTag: String = "en",
): String? {
    if (addresstype in NATURAL_TYPES) {
        name?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    }
    val candidates = (SETTLEMENT_KEYS + ADMIN_KEYS).mapNotNull { key ->
        address[key]?.trim()?.takeIf { it.isNotEmpty() }
    }
    val chosen = candidates.firstOrNull() ?: return displayName
        ?.substringBefore(',')
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
    if (requestsLatinPlaceName(languageTag) && containsHan(chosen)) {
        candidates.firstOrNull(::isLatinName)?.let { return it }
    }
    return chosen
}

/** Locality from a Nominatim reverse JSON body. Malformed JSON throws. */
fun nominatimLocality(body: String, languageTag: String): String? {
    val root = JsonReader(body).parseValue() as? Map<*, *> ?: return null
    if ("error" in root) return null
    val address = (root["address"] as? Map<*, *>)?.entries?.mapNotNull { (key, value) ->
        val field = key as? String ?: return@mapNotNull null
        val text = value as? String ?: return@mapNotNull null
        if (text.isBlank()) null else field to text
    }?.toMap().orEmpty()
    return osmLocalityName(
        name = root["name"] as? String,
        address = address,
        addresstype = root["addresstype"] as? String,
        displayName = root["display_name"] as? String,
        languageTag = languageTag,
    )
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

private fun isLatinName(text: String): Boolean {
    var letters = 0
    for (char in text) {
        if (!char.isLetter()) continue
        letters++
        val block = Character.UnicodeBlock.of(char)
        if (
            block != Character.UnicodeBlock.BASIC_LATIN &&
            block != Character.UnicodeBlock.LATIN_1_SUPPLEMENT &&
            block != Character.UnicodeBlock.LATIN_EXTENDED_A &&
            block != Character.UnicodeBlock.LATIN_EXTENDED_B &&
            block != Character.UnicodeBlock.LATIN_EXTENDED_ADDITIONAL
        ) {
            return false
        }
    }
    return letters > 0
}

internal fun parseJsonValue(text: String): Any? = JsonReader(text).parseValue()

private class JsonReader(private val text: String) {
    private var index = 0

    fun parseValue(): Any? {
        skip()
        if (index >= text.length) throw IllegalArgumentException("Unexpected end of JSON")
        return when (val char = text[index]) {
            '{' -> parseObject()
            '[' -> parseArray()
            '"' -> parseString()
            't' -> literal("true", true)
            'f' -> literal("false", false)
            'n' -> literal("null", null)
            '-', in '0'..'9' -> parseNumber()
            else -> throw IllegalArgumentException("Unexpected '$char'")
        }
    }

    private fun parseObject(): Map<String, Any?> {
        index++
        val values = linkedMapOf<String, Any?>()
        skip()
        if (consume('}')) return values
        while (true) {
            skip()
            val key = parseString()
            skip()
            consumeOrFail(':')
            values[key] = parseValue()
            skip()
            when {
                consume(',') -> continue
                consume('}') -> return values
                else -> throw IllegalArgumentException("Expected ',' or '}'")
            }
        }
    }

    private fun parseArray(): List<Any?> {
        index++
        val values = mutableListOf<Any?>()
        skip()
        if (consume(']')) return values
        while (true) {
            values += parseValue()
            skip()
            when {
                consume(',') -> continue
                consume(']') -> return values
                else -> throw IllegalArgumentException("Expected ',' or ']'")
            }
        }
    }

    private fun parseString(): String {
        consumeOrFail('"')
        val out = StringBuilder()
        while (index < text.length) {
            when (val char = text[index++]) {
                '"' -> return out.toString()
                '\\' -> out.append(escape())
                else -> out.append(char)
            }
        }
        throw IllegalArgumentException("Unclosed string")
    }

    private fun escape(): Char {
        if (index >= text.length) throw IllegalArgumentException("Bad escape")
        return when (val char = text[index++]) {
            '"', '\\', '/' -> char
            'b' -> '\b'
            'f' -> '\u000C'
            'n' -> '\n'
            'r' -> '\r'
            't' -> '\t'
            'u' -> {
                if (index + 4 > text.length) throw IllegalArgumentException("Bad unicode escape")
                val code = text.substring(index, index + 4).toInt(16)
                index += 4
                code.toChar()
            }
            else -> throw IllegalArgumentException("Bad escape")
        }
    }

    private fun parseNumber(): String {
        val start = index
        if (text[index] == '-') index++
        while (index < text.length && text[index].isDigit()) index++
        if (index < text.length && text[index] == '.') {
            index++
            while (index < text.length && text[index].isDigit()) index++
        }
        if (index < text.length && (text[index] == 'e' || text[index] == 'E')) {
            index++
            if (index < text.length && (text[index] == '+' || text[index] == '-')) index++
            while (index < text.length && text[index].isDigit()) index++
        }
        if (start == index) throw IllegalArgumentException("Bad number")
        return text.substring(start, index)
    }

    private fun literal(token: String, value: Any?): Any? {
        if (!text.startsWith(token, index)) throw IllegalArgumentException("Expected $token")
        index += token.length
        return value
    }

    private fun skip() {
        while (index < text.length && text[index].isWhitespace()) index++
    }

    private fun consume(char: Char): Boolean {
        if (index < text.length && text[index] == char) {
            index++
            return true
        }
        return false
    }

    private fun consumeOrFail(char: Char) {
        if (!consume(char)) throw IllegalArgumentException("Expected '$char'")
    }
}
