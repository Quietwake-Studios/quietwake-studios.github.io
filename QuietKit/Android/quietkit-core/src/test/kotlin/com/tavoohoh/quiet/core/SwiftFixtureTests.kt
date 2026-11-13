package com.tavoohoh.quiet.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** `fixtures/core.json`, written by Swift (QuietKit/Tests/QuietCoreTests/FixtureWriter.swift). Swift rules. */
class SwiftFixtureTests {
    private val core: JsonObject by lazy {
        val dir = System.getProperty("quiet.fixtures") ?: error("quiet.fixtures not set")
        Json.parseToJsonElement(File(dir, "core.json").readText()).jsonObject
    }

    private fun JsonObject.str(key: String) = getValue(key).jsonPrimitive.content
    private fun hex(value: ULong) = value.toString(16).padStart(16, '0')

    @Test fun fnv1aMatchesSwift() {
        val cases = core.getValue("fnv1a").jsonArray
        assertTrue(cases.size >= 10)
        for (case in cases.map { it.jsonObject }) {
            assertEquals(case.str("hash"), hex(FNV1a.hash(case.str("input"))), "FNV1a(${case.str("input")})")
        }
    }

    @Test fun splitMix64MatchesSwift() {
        for (case in core.getValue("random").jsonArray.map { it.jsonObject }) {
            val seed = case.str("seed").toULong(16)
            val rng = SeededRandom(seed)
            val next = case.getValue("next").jsonArray.map { it.jsonPrimitive.content }
            assertEquals(next, next.map { hex(rng.next()) }, "seed ${case.str("seed")}")
            for (sample in case.getValue("nextInt").jsonArray.map { it.jsonObject }) {
                val lower = sample.getValue("lower").jsonPrimitive.long
                val upper = sample.getValue("upper").jsonPrimitive.long
                val r = SeededRandom(seed)
                val expected = sample.getValue("values").jsonArray.map { it.jsonPrimitive.long }
                assertEquals(expected, expected.map { r.nextLong(lower, upper) }, "seed $seed in $lower..$upper")
            }
        }
    }

    @Test fun dayKeysMatchSwift() {
        val days = core.getValue("dayKeys").jsonArray.map { it.jsonObject }
        assertTrue(days.size >= 365)
        for (day in days) {
            val key = day.str("key")
            assertEquals(day.getValue("weekday").jsonPrimitive.int, DailyKey.weekday(key), key)
            assertEquals(day.str("seed"), hex(DailyKey.seed(key)), key)
            assertEquals(day.str("plus1"), DailyKey.dayKey(key, 1), key)
            assertEquals(day.str("minus40"), DailyKey.dayKey(key, -40), key)
        }
    }
}
