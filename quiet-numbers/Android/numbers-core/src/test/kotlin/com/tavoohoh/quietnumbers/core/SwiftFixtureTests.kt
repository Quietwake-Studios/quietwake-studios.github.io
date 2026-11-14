package com.tavoohoh.quietnumbers.core

import com.tavoohoh.quiet.core.DailyKey
import com.tavoohoh.quiet.core.SeededRandom
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
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

/**
 * The same boards and the same games as iOS, move by move (fixtures written by the Swift
 * `FixtureWriter`; if one fails, Swift is right and Kotlin is fixed).
 */
class SwiftFixtureTests {
    private fun load(name: String): JsonObject =
        Json.parseToJsonElement(File(System.getProperty("quiet.fixtures"), name).readText()).jsonObject

    private fun ints(e: JsonElement?) = e!!.jsonArray.map { it.jsonPrimitive.int }
    private fun bools(e: JsonElement?) = e!!.jsonArray.map { it.jsonPrimitive.boolean }

    @Test fun dailyGoalsAndBoardsMatchSwift() {
        val fixtures = load("boards.json")
        val days = fixtures["daily"]!!.jsonArray
        assertEquals(60, days.size)
        for (day in days) {
            val o = day.jsonObject
            val key = o["key"]!!.jsonPrimitive.content
            assertEquals(o["goal"]!!.jsonPrimitive.int, DailyChallenge.goal(key), key)
            val random = SeededRandom(DailyKey.seed(key))
            for ((i, stage) in o["stages"]!!.jsonArray.withIndex()) {
                assertEquals(ints(stage), BoardGenerator.makeBoard(random, i + 1).cells.map { it.value }, "$key stage ${i + 1}")
            }
        }
        for (c in fixtures["classic"]!!.jsonArray) {
            val seed = c.jsonObject["seed"]!!.jsonPrimitive.content.toULong(16)
            assertEquals(ints(c.jsonObject["board"]), BoardGenerator.makeBoard(SeededRandom(seed)).cells.map { it.value }, "seed $seed")
        }
        for (p in fixtures["pairable"]!!.jsonArray) {
            assertEquals(p.jsonObject["pairable"]!!.jsonPrimitive.boolean, BoardGenerator.isPairable(ints(p.jsonObject["values"])))
        }
    }

    @Test fun gamesPlayTheSameMoveByMove() {
        val games = load("games.json")["games"]!!.jsonArray
        for (game in games) {
            val g = game.jsonObject
            val name = g["name"]!!.jsonPrimitive.content
            val session = when {
                name == "classic-7" -> GameSession(seed = 7uL)
                name == "classic-99" -> GameSession(seed = 99uL)
                name == "classic-cheap" -> GameSession(seed = 2026uL, scoring = Scoring.CHEAP_HINTS)
                name.startsWith("daily-") -> DailyChallenge.session(name.removePrefix("daily-"))
                else -> GameSession.withValues(listOf(1, 9, 5, 5, 3, 7, 2, 8, 4, 6, 6, 4, 9, 1, 2, 2, 8, 8), seed = 5uL)
            }
            for ((step, element) in g["steps"]!!.jsonArray.withIndex()) {
                val s = element.jsonObject
                val action = s["action"]!!.jsonObject
                val where = "$name step $step $action"
                when {
                    "undo" in action -> assertEquals(action["undo"]!!.jsonPrimitive.boolean, session.undo(), where)
                    "hint" in action -> {
                        val hint = session.useHint()
                        if (action["hint"] is JsonNull) {
                            assertEquals(null, hint, where)
                        } else {
                            assertEquals(ints(action["hint"]), listOf(hint!!.first.first, hint.first.second), where)
                            assertEquals(action["cost"]!!.jsonPrimitive.int, hint.second, where)
                        }
                    }
                    "miss" in action -> session.registerMiss()
                    "plus" in action -> assertEquals(action["plus"]!!.jsonPrimitive.boolean, session.addNumbers(), where)
                    else -> {
                        val (a, b) = ints(action["match"])
                        val outcome = session.match(a, b)!!
                        assertEquals(action["gap"]!!.jsonPrimitive.int, outcome.pair.gap, where)
                        assertEquals(action["route"]!!.jsonPrimitive.content, outcome.pair.route.rawValue, where)
                        assertEquals(action["points"]!!.jsonPrimitive.int, outcome.points, where)
                        assertEquals(ints(action["rows"]), outcome.clearedRows, where)
                        assertEquals(ints(action["digits"]), outcome.clearedDigits, where)
                        assertEquals(action["boardCleared"]!!.jsonPrimitive.boolean, outcome.boardCleared, where)
                        assertEquals(action["reachedGoal"]!!.jsonPrimitive.boolean, outcome.reachedGoal, where)
                    }
                }
                session.addTime(3)
                assertEquals(s["score"]!!.jsonPrimitive.int, session.score, where)
                assertEquals(s["stage"]!!.jsonPrimitive.int, session.stage, where)
                assertEquals(s["plus"]!!.jsonPrimitive.int, session.plusRemaining, where)
                assertEquals(s["hints"]!!.jsonPrimitive.int, session.hintsUsed, where)
                assertEquals(s["streak"]!!.jsonPrimitive.int, session.streak, where)
                assertEquals(s["over"]!!.jsonPrimitive.boolean, session.isOver, where)
                assertEquals(s["completed"]!!.jsonPrimitive.boolean, session.isCompleted, where)
                assertEquals(s["canUndo"]!!.jsonPrimitive.boolean, session.canUndo, where)
                assertEquals(s["seconds"]!!.jsonPrimitive.int, session.elapsedSeconds, where)
                assertEquals(ints(s["values"]), session.board.cells.map { it.value }, where)
                assertEquals(bools(s["cleared"]), session.board.cells.map { it.isCleared }, where)
                assertEquals(bools(s["added"]), session.board.cells.map { it.isAdded }, where)
                val st = session.stats
                assertEquals(ints(s["stats"]), listOf(st.pairs, st.rowsCleared, st.plusUsed, st.longestStreak), where)
                assertEquals(
                    s["validPairs"]!!.jsonArray.map { ints(it) },
                    MatchRules.validPairs(session.board).map { listOf(it.first, it.second, it.gap) }, where,
                )
            }
            // The game ends where Swift's ended (or at the step cap).
            assertEquals(g["steps"]!!.jsonArray.size < 220, session.isOver, name)
            (g["goal"] as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it !is JsonNull }?.let { assertEquals(it.int, session.goal, name) }
        }
    }

    @Suppress("unused") private val keep: JsonArray? = null
}
