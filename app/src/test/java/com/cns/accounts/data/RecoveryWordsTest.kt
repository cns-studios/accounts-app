package com.cns.accounts.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class RecoveryWordsTest {
    // The word list the app ships (Gradle runs unit tests from the module directory).
    private val listFile = File("src/main/res/raw/bip39_english.txt")
    private val words = RecoveryWords { listFile.readLines().asSequence() }

    @Test
    fun shipsTheSameBip39ListAsTheServer() {
        // auth-v2 internal/security/bip39_english.txt has this digest; a different list would
        // make the app reject or mangle valid phrases.
        val digest = MessageDigest.getInstance("SHA-256").digest(listFile.readBytes()).joinToString("") { "%02x".format(it) }
        assertEquals("2f5eed53a4727b4bf8880d8f3f199efc90e58503646d9ff8eff3a2ed3b24dbda", digest)
        assertEquals(2048, listFile.readLines().count { it.isNotBlank() })
    }

    @Test
    fun normalizesCaseAndPunctuation() {
        assertEquals("abandon", words.normalize("abandon"))
        assertEquals("abandon", words.normalize("ABANDON"))
        assertEquals("abandon", words.normalize(" 1.abandon, "))
        assertEquals("zoo", words.normalize("Zoo!"))
    }

    @Test
    fun acceptsTheFirstFourLetters() {
        assertEquals("abandon", words.normalize("aban"))
        assertEquals("zebra", words.normalize("zebr"))
        // Three letters are only a word when the word itself is that short.
        assertEquals("zoo", words.normalize("zoo"))
        assertNull(words.normalize("aba"))
    }

    @Test
    fun rejectsUnknownWords() {
        assertNull(words.normalize("abandonx"))
        assertNull(words.normalize("qqqq"))
        assertNull(words.normalize(""))
        assertNull(words.normalize("1234"))
    }

    @Test
    fun fourLetterPrefixesAreUniqueInTheList() {
        val long = listFile.readLines().map(String::trim).filter { it.length >= 4 }
        assertEquals(long.size, long.map { it.take(4) }.toSet().size)
    }

    @Test
    fun checkSplitsOnAnySeparatorAndCountsUnknownWords() {
        val phrase = "abandon, ability. able\nabout above-absent absorb abstract absurd abuse access accident"
        val check = words.check(phrase)
        assertEquals(12, check.words.size)
        assertTrue(check.unknown.isEmpty())
        assertTrue(check.complete)
        assertEquals("abandon", check.words.first())

        val wrong = words.check("abandon notaword ability")
        assertEquals(listOf("abandon", "ability"), wrong.words)
        assertEquals(listOf("notaword"), wrong.unknown)
        assertFalse(wrong.complete)
    }

    @Test
    fun elevenWordsAreNotComplete() =
        assertFalse(words.check("abandon ".repeat(11)).complete)

    @Test
    fun toStringNeverContainsTheWords() {
        val check = words.check("abandon ability able")
        assertFalse(check.toString().contains("abandon"))
        assertEquals("PhraseCheck(3 words, 0 unknown)", check.toString())
    }
}
