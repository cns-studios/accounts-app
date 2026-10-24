package com.cns.accounts.data

import android.content.res.Resources
import com.cns.accounts.R

/** What the recovery form knows about the typed phrase before auth-v2 checks it. */
data class PhraseCheck(val words: List<String>, val unknown: List<String>) {
    val complete: Boolean get() = unknown.isEmpty() && words.size == RecoveryWords.PhraseLength

    // The typed words are a secret too.
    override fun toString() = "PhraseCheck(${words.size} words, ${unknown.size} unknown)"
}

/**
 * The BIP39 English word list auth-v2 makes recovery phrases from (res/raw/bip39_english.txt),
 * for checking typed words as the user goes. Same rules as the server: any case, punctuation
 * and numbering ignored, and a word's first four letters are enough (they're unique in the list).
 */
class RecoveryWords(private val resources: Resources) {
    private val words: Set<String> by lazy {
        resources.openRawResource(R.raw.bip39_english).bufferedReader().useLines { lines ->
            lines.map(String::trim).filter(String::isNotEmpty).toCollection(LinkedHashSet())
        }
    }
    private val prefixes: Map<String, String> by lazy {
        words.filter { it.length >= 4 }.associateBy { it.take(4) }
    }

    fun normalize(raw: String): String? {
        val word = raw.lowercase().filter { it in 'a'..'z' }
        return when {
            word in words -> word
            word.length == 4 -> prefixes[word]
            else -> null
        }
    }

    fun check(input: String): PhraseCheck {
        val known = ArrayList<String>()
        val unknown = ArrayList<String>()
        input.lowercase().split(NonLetters).filter(String::isNotEmpty).forEach { typed ->
            normalize(typed)?.let(known::add) ?: unknown.add(typed)
        }
        return PhraseCheck(known, unknown)
    }

    companion object {
        const val PhraseLength = 12
        private val NonLetters = Regex("[^a-z]+")
    }
}
