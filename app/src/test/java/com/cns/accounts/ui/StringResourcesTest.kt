package com.cns.accounts.ui

import com.cns.accounts.data.AppLanguage
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every language has to say the same as English: the same strings, the same format
 * placeholders in them (%1$s, %2$d), and the house rules for text (no em or en dashes).
 * English (brainrotted) is all lower case on top.
 */
class StringResourcesTest {
    private val res = listOf("src/main/res", "app/src/main/res").map(::File).first { it.isDirectory }

    private fun strings(dir: String): Map<String, String> {
        val file = File(res, "$dir/strings.xml")
        assertTrue("$file is missing", file.isFile)
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("string")
        return (0 until nodes.length).map { nodes.item(it) }
            .filter { it.attributes.getNamedItem("translatable")?.nodeValue != "false" }
            .associate { it.attributes.getNamedItem("name").nodeValue to it.textContent }
    }

    private val formats = Regex("%\\d\\$[sd]|%%")
    private fun formatsOf(text: String) = formats.findAll(text).map { it.value }.sorted().toList()

    /** values-<qualifier> of a language tag: "de" -> values-de, the joke language -> values-en-rZZ (see AppLanguage). */
    private fun dirOf(tag: String) = if (tag == AppLanguage.Brainrot) "values-en-rZZ" else "values-$tag"

    private val translated = AppLanguage.Supported.filter { it != "en" }

    @Test
    fun everyLanguageHasAllStrings() {
        val english = strings("values")
        for (tag in translated) {
            val other = strings(dirOf(tag))
            assertEquals("$tag is missing strings", emptyList<String>(), (english.keys - other.keys).sorted())
            assertEquals("$tag has strings English does not", emptyList<String>(), (other.keys - english.keys).sorted())
        }
    }

    @Test
    fun placeholdersMatchEnglish() {
        val english = strings("values")
        for (tag in translated) {
            val wrong = strings(dirOf(tag)).filter { (key, text) -> formatsOf(text) != formatsOf(english[key] ?: "") }.keys
            assertEquals("$tag changes the placeholders of", emptyList<String>(), wrong.sorted())
        }
    }

    @Test
    fun noDashes() {
        for (tag in AppLanguage.Supported) {
            val wrong = strings(if (tag == "en") "values" else dirOf(tag)).filter { (_, text) -> text.any { it == '–' || it == '—' } }.keys
            assertEquals("$tag uses em or en dashes in", emptyList<String>(), wrong.sorted())
        }
    }

    @Test
    fun brainrotIsLowerCase() {
        val loud = strings(dirOf(AppLanguage.Brainrot)).filter { (_, text) -> text.replace(formats, "") != text.replace(formats, "").lowercase() }.keys
        assertEquals("not lower case:", emptyList<String>(), loud.sorted())
    }

    @Test
    fun everyLanguageHasItsPickerName() {
        // The settings screen names each language in itself; a new tag needs a name there.
        val source = File(res, "../java/com/cns/accounts/ui/settings/AppSettingsScreen.kt").readText()
        for (tag in AppLanguage.Supported) assertTrue("no picker name for $tag", "\"$tag\" ->" in source)
    }
}
