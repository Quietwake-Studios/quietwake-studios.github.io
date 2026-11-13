package com.tavoohoh.quietwords.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.Normalizer

/** The language of the words (not of the app). Each one has its own lists, album and daily leaderboard. */
@Serializable
enum class WordLanguage(val rawValue: String) {
    @SerialName("en") EN("en"),
    @SerialName("es") ES("es");

    /** Board letters, in alphabet order. */
    val alphabet: List<Char> get() = if (this == ES) LATIN + 'Ñ' else LATIN

    companion object {
        private val LATIN = ('A'..'Z').toList()
        fun fromRaw(raw: String): WordLanguage? = entries.firstOrNull { it.rawValue == raw }
    }
}

/**
 * Text as it goes on the board: upper case, no accents or diaeresis (Á→A, Ü→U), no spaces,
 * hyphens or apostrophes. Ñ stays in Spanish.
 *
 * Swift walks grapheme clusters (`Character`, compared by canonical equivalence); here the
 * text is NFC-normalized first and walked by code point, which gives the same result for a
 * precomposed or decomposed Ñ (checked against Swift in `fixtures/words.json` and every
 * theme word in `fixtures/themes.json`).
 */
object WordText {
    fun normalize(text: String, language: WordLanguage): String {
        val upper = Normalizer.normalize(text.uppercase(), Normalizer.Form.NFC)
        val out = StringBuilder()
        var i = 0
        while (i < upper.length) {
            val cp = upper.codePointAt(i)
            val next = i + Character.charCount(cp)
            // A Ñ followed by another combining mark is a different Character in Swift.
            if (cp == 'Ñ'.code && language == WordLanguage.ES && (next >= upper.length || !isMark(upper.codePointAt(next)))) {
                out.append('Ñ')
                i = next
                continue
            }
            // The whole cluster: this code point and the marks after it.
            var end = next
            while (end < upper.length && isMark(upper.codePointAt(end))) end += Character.charCount(upper.codePointAt(end))
            val decomposed = Normalizer.normalize(upper.substring(i, end), Normalizer.Form.NFD)
            decomposed.codePoints().forEach { scalar ->
                if (Character.getType(scalar) == Character.NON_SPACING_MARK.toInt()) return@forEach
                if (scalar == ' '.code || scalar == '-'.code || scalar == '\''.code || scalar == '’'.code) return@forEach
                out.appendCodePoint(scalar)
            }
            i = end
        }
        return out.toString()
    }

    /** Only board letters (A–Z, plus Ñ in Spanish). */
    fun isBoardText(text: String, language: WordLanguage): Boolean {
        val nfc = Normalizer.normalize(text, Normalizer.Form.NFC)
        val allowed = language.alphabet
        return nfc.isNotEmpty() && nfc.all { it in allowed }
    }

    private fun isMark(cp: Int): Boolean = when (Character.getType(cp)) {
        Character.NON_SPACING_MARK.toInt(), Character.ENCLOSING_MARK.toInt(), Character.COMBINING_SPACING_MARK.toInt() -> true
        else -> false
    }
}

/** Letters as Swift counts `Character`s: code points that aren't combining marks. */
internal val String.characterCount: Int
    get() {
        var count = 0
        codePoints().forEach { if (Character.getType(it) !in MARK_TYPES) count++ }
        return count
    }

private val MARK_TYPES = setOf(
    Character.NON_SPACING_MARK.toInt(), Character.ENCLOSING_MARK.toInt(), Character.COMBINING_SPACING_MARK.toInt(),
)

/** How well known a word is. */
@Serializable
enum class WordGroup(val rawValue: String) {
    @SerialName("common") COMMON("common"),
    @SerialName("crafted") CRAFTED("crafted"),
    @SerialName("rare") RARE("rare"),
    /** Words of one region or country, at most 20 per theme and language. */
    @SerialName("regional") REGIONAL("regional");

    companion object {
        fun fromRaw(raw: String): WordGroup? = entries.firstOrNull { it.rawValue == raw }
    }
}

/** A word of a theme: as shown in the list ("Estrella de mar") and as it goes on the board ("ESTRELLADEMAR"). */
@Serializable
data class Word(val text: String, val letters: String, val group: WordGroup) {
    constructor(text: String, language: WordLanguage, group: WordGroup) : this(text, WordText.normalize(text, language), group)

    val count: Int get() = letters.characterCount
}
