package app.altitude.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

class LocaleResourcesTest {
    @Test
    fun translatableKeysMatchAcrossLocales() {
        val catalogs = LOCALES.associateWith { locale -> readCatalog(File("src/main/res/$locale/strings.xml")) }
        val english = catalogs.getValue("values")
        val translated = catalogs.filterKeys { it != "values" }

        assertEquals(english.translatableStrings.keys, translated.values.map { it.translatableStrings.keys }.reduce { left, right -> left.intersect(right) })
        translated.values.forEach { catalog ->
            assertEquals(english.translatableStrings.keys, catalog.translatableStrings.keys)
            assertEquals(english.plurals.keys, catalog.plurals.keys)
        }
        assertEquals(
            catalogs.getValue("values-zh").translatableStrings,
            catalogs.getValue("values-zh-rCN").translatableStrings,
        )
        assertEquals(catalogs.getValue("values-zh").plurals, catalogs.getValue("values-zh-rCN").plurals)

        english.translatableStrings.forEach { (name, value) ->
            assertTrue("$name is blank", value.text.isNotBlank() || name == "caption_placeholder")
            val expected = specifiers(value.text)
            translated.values.forEach { catalog ->
                val actual = specifiers(catalog.translatableStrings.getValue(name).text)
                assertEquals("Format specifiers for $name", expected, actual)
            }
        }
        english.plurals.keys.forEach { name ->
            catalogs.values.forEach { catalog ->
                val plural = catalog.plurals.getValue(name)
                assertTrue("$name in ${catalog.locale} needs quantity other", "other" in plural.quantities)
                plural.items.values.forEach { item ->
                    assertTrue(item.isNotBlank())
                    assertEquals(specifiers(plural.items.getValue("other")), specifiers(item))
                }
            }
        }
    }

    @Test
    fun wordmarkStaysLatinAndUntranslated() {
        val english = readCatalog(File("src/main/res/values/strings.xml"))
        val wordmark = english.strings.getValue("wordmark")
        assertFalse(wordmark.translatable)
        assertEquals("ALTITUDE", wordmark.text)
        listOf("app_name", "language_zh", "language_en", "language_fr").forEach { name ->
            assertFalse(english.strings.getValue(name).translatable)
        }
        LOCALES.filter { it != "values" }.forEach { locale ->
            val catalog = readCatalog(File("src/main/res/$locale/strings.xml"))
            assertFalse(catalog.strings.containsKey("wordmark"))
            assertFalse(catalog.strings.containsKey("app_name"))
        }
    }

    @Test
    fun watermarkPatternsAreLocaleAware() {
        val zone = ZoneId.of("Asia/Shanghai")
        val moment = ZonedDateTime.of(2026, 10, 22, 16, 42, 8, 0, zone)
        val english = readCatalog(File("src/main/res/values/strings.xml"))
        val french = readCatalog(File("src/main/res/values-fr/strings.xml"))
        val chinese = readCatalog(File("src/main/res/values-zh-rCN/strings.xml"))

        assertEquals("2026.10.22  16:42", stamp(english, moment))
        assertEquals("2026.10.22  16:42", stamp(chinese, moment))
        assertEquals("22.10.2026  16:42", stamp(french, moment))
        assertEquals("16:42:08", clock(english, moment))
        assertEquals("16:42:08", clock(french, moment))
        assertTrue(french.translatableStrings.getValue("format_altitude").text.contains('\u202f'))
        assertEquals("O", french.translatableStrings.getValue("hemisphere_w").text)
        assertEquals("西", chinese.translatableStrings.getValue("hemisphere_w").text)
    }

    private fun stamp(catalog: Catalog, moment: ZonedDateTime): String {
        val date = moment.format(DateTimeFormatter.ofPattern(catalog.translatableStrings.getValue("pattern_watermark_date").text))
        val time = moment.format(DateTimeFormatter.ofPattern(catalog.translatableStrings.getValue("pattern_watermark_time").text))
        return catalog.translatableStrings.getValue("format_watermark_stamp").text
            .replace("%1\$s", date)
            .replace("%2\$s", time)
    }

    private fun clock(catalog: Catalog, moment: ZonedDateTime): String {
        return moment.format(DateTimeFormatter.ofPattern(catalog.translatableStrings.getValue("pattern_display_clock").text))
    }

    private fun specifiers(value: String): List<String> {
        return SPECIFIER.findAll(value).map { it.value }.toList()
    }

    private fun readCatalog(file: File): Catalog {
        val builder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        val document = builder.parse(file)
        val strings = linkedMapOf<String, Entry>()
        val plurals = linkedMapOf<String, Plural>()
        val nodes = document.documentElement.childNodes
        for (index in 0 until nodes.length) {
            val node = nodes.item(index)
            if (node !is Element) continue
            val name = node.getAttribute("name")
            val translatable = node.getAttribute("translatable") != "false"
            when (node.tagName) {
                "string" -> strings[name] = Entry(node.textContent, translatable)
                "plurals" -> {
                    val items = linkedMapOf<String, String>()
                    val children = node.childNodes
                    for (childIndex in 0 until children.length) {
                        val child = children.item(childIndex)
                        if (child is Element && child.tagName == "item") {
                            items[child.getAttribute("quantity")] = child.textContent
                        }
                    }
                    plurals[name] = Plural(translatable, items)
                }
            }
        }
        return Catalog(file.parentFile?.name ?: file.name, strings, plurals)
    }

    private data class Entry(val text: String, val translatable: Boolean)

    private data class Plural(val translatable: Boolean, val items: Map<String, String>) {
        val quantities: Set<String> get() = items.keys
    }

    private data class Catalog(
        val locale: String,
        val strings: Map<String, Entry>,
        val plurals: Map<String, Plural>,
    ) {
        val translatableStrings: Map<String, Entry> get() = strings.filterValues { it.translatable }
    }

    private companion object {
        val LOCALES = listOf("values", "values-zh", "values-zh-rCN", "values-fr")
        val SPECIFIER = Regex("%(?:\\d+\\$)?[sd]")
    }
}

class HardcodedCopyGuardTest {
    @Test
    fun mainSourcesDoNotHardcodeUserFacingCopy() {
        val offenders = mutableListOf<String>()
        File("src/main/java").walkTopDown().filter { it.extension == "kt" }.forEach { file ->
            val source = stripComments(file.readText())
            if (CJK.containsMatchIn(source)) {
                offenders += "${file.path} contains CJK text"
            }
            if (file.path.replace('\\', '/').contains("/ui/")) {
                source.lineSequence().forEachIndexed { index, line ->
                    if (TEXT_LITERAL.containsMatchIn(line) || CONTENT_DESCRIPTION.containsMatchIn(line) || ERROR_LITERAL.containsMatchIn(line)) {
                        offenders += "${file.path}:${index + 1}: $line"
                    }
                    if (SENTENCE.containsMatchIn(line)) {
                        offenders += "${file.path}:${index + 1}: $line"
                    }
                }
            }
        }
        File("src/main/res").walkTopDown().filter { it.extension == "xml" && it.name != "strings.xml" }.forEach { file ->
            val text = file.readText()
            if (HARDCODED_XML.containsMatchIn(text)) {
                offenders += "${file.path} has a hardcoded android:text"
            }
        }
        assertTrue(offenders.joinToString("\n"), offenders.isEmpty())
    }

    private fun stripComments(source: String): String {
        return source
            .replace(BLOCK_COMMENT, "")
            .replace(LINE_COMMENT, "")
    }

    private companion object {
        val CJK = Regex("[\\u3400-\\u9FFF]")
        val TEXT_LITERAL = Regex("""\bText\s*\(\s*(?:text\s*=\s*)?"""")
        val CONTENT_DESCRIPTION = Regex("""contentDescription\s*=\s*"""")
        val ERROR_LITERAL = Regex("""\berror\s*\(\s*"""")
        val SENTENCE = Regex(""""[^"\n]*[A-Za-z][^"\n]*\s[^"\n]*"""")
        val HARDCODED_XML = Regex("""android:(?:text|hint|contentDescription)\s*=\s*"[^@"]""")
        val BLOCK_COMMENT = Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL)
        val LINE_COMMENT = Regex("//[^\\n]*")
    }
}
