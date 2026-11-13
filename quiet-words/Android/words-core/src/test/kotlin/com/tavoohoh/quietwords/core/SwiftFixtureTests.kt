package com.tavoohoh.quietwords.core

import com.tavoohoh.quiet.core.SeededRandom
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The JSON in `fixtures/`, written by Swift (QuietWordsCore/Tests/QuietWordsCoreTests/FixtureWriter.swift).
 * If one of these fails, Swift is right and Kotlin gets fixed.
 */
class SwiftFixtureTests {
    private fun load(name: String): JsonElement {
        val dir = System.getProperty("quiet.fixtures") ?: error("quiet.fixtures not set")
        return Json.parseToJsonElement(File(dir, name).readText())
    }

    private fun JsonElement.str(key: String) = jsonObject.getValue(key).jsonPrimitive.content
    private fun JsonElement.int(key: String) = jsonObject.getValue(key).jsonPrimitive.int
    private fun JsonElement.strings(key: String) = jsonObject.getValue(key).jsonArray.map { it.jsonPrimitive.content }
    private fun JsonElement.ints(key: String) = jsonObject.getValue(key).jsonArray.map { it.jsonPrimitive.int }

    @Test fun basicsMatchSwift() {
        val words = load("words.json")
        for (case in words.jsonObject.getValue("shuffle").jsonArray) {
            val rng = SeededRandom(case.str("seed").toULong(16))
            assertEquals(case.ints("result"), Generator.shuffled((0 until 20).toList(), rng))
        }
        for (case in words.jsonObject.getValue("levels").jsonArray) {
            val key = case.str("key")
            assertEquals(case.str("level"), DailyChallenge.level(key).rawValue, key)
            assertEquals(case.jsonObject.getValue("longHidden").jsonPrimitive.boolean, DailyChallenge.hasLongHidden(key), key)
        }
        for (case in words.jsonObject.getValue("normalize").jsonArray) {
            val language = WordLanguage.fromRaw(case.str("language"))!!
            val out = WordText.normalize(case.str("text"), language)
            assertEquals(case.str("letters"), out, case.str("text"))
            assertEquals(case.jsonObject.getValue("isBoardText").jsonPrimitive.boolean, WordText.isBoardText(out, language), case.str("text"))
        }
    }

    /** Every word of every theme normalizes and counts as in Swift; same hidden words and blocklists. */
    @Test fun themesMatchSwift() {
        val fixture = load("themes.json").jsonObject
        val library = ThemeLibrary.shared
        val themes = fixture.getValue("themes").jsonArray
        assertEquals(themes.map { it.str("id") }, library.themes.map { it.id })
        for (theme in themes) {
            val mine = library.theme(theme.str("id"))!!
            assertEquals(theme.str("symbol"), mine.symbol)
            val languages = theme.jsonObject.getValue("languages").jsonObject
            assertEquals(languages.keys, mine.languages.keys.map { it.rawValue }.toSet())
            for ((code, part) in languages) {
                val words = mine.words(WordLanguage.fromRaw(code)!!)!!
                assertEquals(part.str("name"), words.name)
                assertEquals(part.strings("hidden"), words.hidden, "${mine.id} $code hidden")
                val expected = part.jsonObject.getValue("words").jsonArray.map {
                    listOf(it.str("text"), it.str("letters"), it.str("group"), it.int("count").toString())
                }
                val actual = words.all.map { listOf(it.text, it.letters, it.group.rawValue, it.count.toString()) }
                assertEquals(expected, actual, "${mine.id} $code words")
            }
        }
        val blocklist = fixture.getValue("blocklist").jsonObject
        for (language in WordLanguage.entries) {
            assertEquals(blocklist.getValue(language.rawValue).jsonArray.map { it.jsonPrimitive.content }, library.blocklist.getValue(language))
        }
        assertEquals(fixture.getValue("lockEntries").jsonArray.map { it.jsonPrimitive.content }, library.lock.entries.map { it.from })
    }

    @Test fun dailyChallengesMatchSwift() {
        val days = load("daily.json").jsonArray
        assertTrue(days.size >= 40)
        var compared = 0
        for (day in days) {
            val key = day.str("day")
            val language = WordLanguage.fromRaw(day.str("language"))!!
            val label = "$key $language"
            assertEquals(day.str("level"), DailyChallenge.level(key).rawValue, label)
            val theme = day.jsonObject.getValue("theme")
            assertEquals(if (theme is JsonNull) null else theme.jsonPrimitive.content, DailyChallenge.themeID(key), label)
            assertBoard(day.jsonObject.getValue("board"), DailyChallenge.board(key, language), label)
            compared++
        }
        println("Daily challenges identical to Swift: $compared (day × language)")
    }

    @Test fun freeBoardsMatchSwift() {
        val library = ThemeLibrary.shared
        val boards = load("free.json").jsonArray
        for (case in boards) {
            val language = WordLanguage.fromRaw(case.str("language"))!!
            val level = Level.fromRaw(case.str("level"))!!
            val words = library.theme(case.str("theme"))!!.words(language)!!
            val board = Generator.board(case.str("key"), words.all, words.hidden, language, level, blocklist = library.blocklist.getValue(language))
            assertBoard(case.jsonObject.getValue("board"), board, "${case.str("key")} $level $language")
        }
        println("Free boards identical to Swift: ${boards.size}")
    }

    private fun assertBoard(expected: JsonElement, board: Board?, label: String) {
        if (expected is JsonNull) {
            assertEquals(null, board, label)
            return
        }
        val e: JsonObject = expected.jsonObject
        requireNotNull(board) { "$label: no board in Kotlin" }
        assertEquals(e.int("side"), board.side, label)
        assertEquals(e.str("language"), board.language.rawValue, label)
        val rows = (0 until board.side).map { r -> board.letters.subList(r * board.side, (r + 1) * board.side).joinToString("") }
        assertEquals(e.strings("rows"), rows, "$label rows")
        assertEquals(e.str("hidden"), board.hidden, "$label hidden")
        assertEquals(e.ints("hiddenCells"), board.hiddenCells, "$label hidden cells")
        val placements = e.getValue("placements").jsonArray.map {
            listOf(it.str("text"), it.str("letters"), it.str("group"), it.int("row"), it.int("column"), it.int("dr"), it.int("dc"))
        }
        assertEquals(placements, board.placements.map {
            listOf(it.word.text, it.word.letters, it.word.group.rawValue, it.row, it.column, it.direction.dr, it.direction.dc)
        }, "$label placements")
        assertEquals(e.str("fingerprint"), fingerprint(board), "$label fingerprint")
    }
}
